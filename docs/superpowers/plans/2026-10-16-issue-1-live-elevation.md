# Issue #1 Live Elevation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Calcular e exibir ganho/perda de elevação durante o tracking com baixa latência, resistência a ruído e totais independentes de pausas de cálculo ou fronteiras de 1 km.

**Architecture:** Um estimador Kotlin puro mantém filtro causal e estado de excursão/histerese. O serviço converte cada fix Android novo, alimenta o estimador em série e publica um snapshot coerente para UI e gravação. O GPX continua com altitude bruta; os totais salvos vêm do estimador.

**Tech Stack:** Kotlin/Java Android (minSdk 24, targetSdk 36), JUnit JVM, testes instrumentados API 24/36, Python 3 para análise GPX offline.

**Spec:** [desenho de elevação ao vivo](../specs/2026-10-16-elevation-live-design.md); [análise da atividade](../analysis/2026-10-16-issue-1-elevation-strava.md).

## Global Constraints

- Não adicionar nem publicar `tools/gpx/**/*.gpx`; a faixa pode revelar localização privada. As três exportações da issue #1 estão somente na worktree em `tools/gpx/issue-1/<id>.gpx`.
- `Location.getAccuracy()` é só horizontal; `getVerticalAccuracyMeters()`/`hasVerticalAccuracy()` apenas em API 26+.
- Manter a política atual de não contar altimetria durante pausa, inclusive pausas automáticas, até decisão explícita de produto em contrário.
- GPX exportado mantém `<ele>` bruto e o esquema atual. Resumos históricos não são recalculados.
- Os limiares da spec são **iniciais**: o gate de calibração decide os valores antes da troca do cálculo em produção. Comparar com Strava não é critério de igualdade.
- Fazer uma tarefa por vez: teste que falha, implementação mínima, teste que passa, revisão e commit. Executar `make test` e `make test-e2e` após integração; `make verify` no candidato final.

## Review Focus

1. **Fix duplicado pelo loop de 1 s:** dois polls do mesmo `elapsedRealtimeNanos` não acrescentam dois pontos nem dois eventos ao estimador — teste instrumentado da Task 3.
2. **Precisão vertical ausente em API 24:** não ler métodos de API 26; fallback de 10 m sem tratar precisão horizontal de 5 m como vertical — teste JVM de conversão na Task 2 e instrumentado API 24 na Task 5.
3. **Pausa e salvamento concorrentes:** GPX e resumo se referem ao mesmo ponto final e o ganho não dá salto após pausa — teste instrumentado da Task 4.
4. **Subida dividida em blocos:** resultado ponto a ponto e por blocos é igual dentro de 0,01 m — teste de propriedades da Task 1.
5. **GPS ruim depois de bom:** a UI preserva o último total confirmado e marca sinal fraco, sem apresentar altitude 0 m — teste da Task 4.

## Mapa de arquivos e responsabilidades

| Arquivo | Responsabilidade |
|---|---|
| `mobile/src/main/java/br/com/helpdev/velocimetroalerta/gps/ElevationEstimator.kt` (novo) | `ElevationSample`, `ElevationEstimate`, configuração, filtro causal, histerese, pausa, retomada e finalização; sem Android ou GPX. |
| `mobile/src/main/java/br/com/helpdev/velocimetroalerta/gps/AndroidElevationSample.kt` (novo) | Converter `Location` e flags da plataforma, preservando precisão vertical opcional e tempo monotônico. |
| `mobile/src/main/java/br/com/helpdev/velocimetroalerta/gps/SpeedometerService.kt` | Deduplicar fixes, alimentar estimador em série, publicar snapshot e retirar cálculo assíncrono de 1 km. |
| `mobile/src/main/java/br/com/helpdev/velocimetroalerta/gps/ObSpeedometerAlert.kt` | Estado opcional de altitude/qualidade e cópia imutável para consumidores. |
| `mobile/src/main/java/br/com/helpdev/velocimetroalerta/activities/ActivityModels.kt` | Capturar resumo e GPX do mesmo snapshot final. |
| `mobile/src/main/java/br/com/helpdev/velocimetroalerta/MainActivityFragment.kt`, `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-pt/strings.xml` | Estados de altitude/qualidade e explicação atualizada. |
| `mobile/src/test/java/br/com/helpdev/velocimetroalerta/gps/ElevationEstimatorTest.kt` (novo) | Sequências sintéticas e invariantes do algoritmo. |
| `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/SpeedometerServiceElevationTest.kt` (novo) | Fluxo Android e snapshot de gravação; reutilizar infraestrutura dos testes instrumentados. |
| `tools/analyze_elevation_gpx.py`, `tools/elevation_validation/` e seus testes Python | Benchmark offline agregado das três rotas; evoluir para execução do estimador Kotlin real, mantendo GPX fora do Git. |

---

### Task 1: Contrato e estimador incremental puro

**Files:** criar `gps/ElevationEstimator.kt` e `gps/ElevationEstimatorTest.kt` nos diretórios acima.

**Interfaces:**

- Produz `data class ElevationSample(val altitudeMeters: Double?, val verticalAccuracyMeters: Double?, val fixElapsedRealtimeNanos: Long)`.
- Produz `enum class ElevationQuality { WAITING, ACTIVE, STALE, LOW_CONFIDENCE }` e `data class ElevationEstimate(val filteredAltitudeMeters: Double?, val gainMeters: Double, val lossMeters: Double, val quality: ElevationQuality, val acceptedSamples: Long)`.
- Produz `class ElevationEstimator(config: ElevationConfig = ElevationConfig())` com `accept(sample): ElevationEstimate`, `pause(): ElevationEstimate`, `resume(): ElevationEstimate`, `finish(): ElevationEstimate`, `snapshot(): ElevationEstimate`, `reset(): Unit`.
- `ElevationConfig` fixa janela mediana 5, fallback 10 m, limiar de precisão vertical 25 m, fórmula local `clamp(1.5 × medianaVertical, 5 m, 20 m)` e lacuna 30 s. O filtro e o limiar pertencem ao segmento ativo, não ao GPX completo.

- [ ] **Step 1: Escrever testes que falham** para espícula isolada (`[100]×5,106,[100]×5 → 0/0`), subida gradual de 12 m com platô final (`12/0`), retorno ao início (`12/12`), pausa sem salto, repetição/tempo decrescente, vertical ausente/ruim, lacuna >30 s, monotonicidade e processamento em blocos vs. contínuo (`≤0,01 m` de diferença). Simular fix de 1 s entre pontos.
- [ ] **Step 2: Rodar** `./gradlew :mobile:testDebugUnitTest --tests '*ElevationEstimatorTest'`; confirmar falha pelos símbolos/comportamentos novos, sem falhas de infraestrutura.
- [ ] **Step 3: Implementar** buffer circular causal, mediana, vale/pico/direção, contadores e snapshot. Uma reversão curta não debita total; `pause/resume` inicia novo segmento sem zerar totais. Invalidade/qualidade congelam o total. A complexidade por fix permanece constante com janela fixa.
- [ ] **Step 4: Rodar o mesmo teste** até passar e revisar casos de fim de filtro: a cauda e `finish()` devem ser idempotentes, sem alterar o total após a última amostra.
- [ ] **Step 5: Commit** `feat: add incremental elevation estimator` com apenas estimador e testes.

### Task 2: Conversão de `Location` e política de qualidade

**Files:** criar `gps/AndroidElevationSample.kt`; testar em `gps/AndroidElevationSampleTest.kt` (JVM/Robolectric se já disponível) ou em `SpeedometerServiceElevationTest.kt` instrumentado. Ajustar a infraestrutura de teste somente se necessário.

**Interfaces:**

- Consome `ElevationSample` da Task 1.
- Produz `fun Location.toElevationSample(): ElevationSample`, retornando `altitudeMeters = null` se `!hasAltitude()` ou não finito; `verticalAccuracyMeters = null` em API <26, `!hasVerticalAccuracy()` ou valor não finito; tempo em `elapsedRealtimeNanos`.
- A precisão horizontal continua disponível separadamente para a aceitação de posição do serviço; esta função não a converte em vertical.

- [ ] **Step 1: Escrever testes que falham** para altitude ausente, altitude válida, precisão vertical presente/ausente, valor inválido e execução API 24 sem chamada a método de API 26. Verificar campo nulo, não `0.0` fictício.
- [ ] **Step 2: Rodar teste focado** (`:mobile:testDebugUnitTest --tests '*AndroidElevationSampleTest'` ou teste instrumentado correspondente) e observar falha esperada.
- [ ] **Step 3: Implementar** verificação explícita de versão/flags e conversão, sem mudar o filtro de distância existente.
- [ ] **Step 4: Rodar teste focado** e `make test`; confirmar ambos passam.
- [ ] **Step 5: Commit** `feat: capture vertical GPS quality for elevation`.

### Task 3: Integrar estimador ao serviço e remover recálculo por quilômetro

**Files:** modificar `gps/SpeedometerService.kt` e, se necessário, `gps/Gps.kt`/`gpx/objects/TrkSeg.java`; criar/atualizar `SpeedometerServiceElevationTest.kt`.

**Interfaces:**

- Consome `ElevationEstimator.accept/pause/resume/finish/snapshot` e `Location.toElevationSample()`.
- Produz um caminho serial de fix aceito: validar status/frescor → distância e GPX → estimador → `ObSpeedometerAlert` → callback/notification. Definir um identificador monotônico de último fix para que polls repetidos apenas atualizem o status de sinal.

- [ ] **Step 1: Escrever teste instrumentado que falha**: dois polls do mesmo fix geram um ponto GPX e uma alimentação de elevação; nova posição produz novo total no mesmo ciclo; fronteira de 1 km não altera o estimador; pausa congela; retomada e parada não causam recálculo retroativo.
- [ ] **Step 2: Rodar teste focado** no emulador disponível, registrando a falha esperada, sem iniciar `make test-e2e` inteiro ainda.
- [ ] **Step 3: Mover a alimentação** para depois da aceitação do fix, manter tudo em uma única fila/thread proprietária e retirar `calculateAltimetryAsync`, `calculateAltimetry`, os três campos `tmp...Altimetry` e a constante de 1 km. Sincronizar transições de pausa/parada com essa fila; o `stop()` deve publicar `finish()` antes do snapshot para salvar.
- [ ] **Step 4: Rodar teste focado**, depois `make test`. Medir custo por fix numa rota longa e verificar que não cresce com o número de pontos.
- [ ] **Step 5: Commit** `feat: calculate live elevation per accepted fix`.

### Task 4: Publicação coerente, salvamento e texto de UI

**Files:** modificar `gps/ObSpeedometerAlert.kt`, `activities/ActivityModels.kt`, `MainActivityFragment.kt`, `values/strings.xml`, `values-pt/strings.xml`; atualizar testes de ActivityDraft e adicionar testes instrumentados de tela/salvamento.

**Interfaces:**

- Consome `ElevationEstimate` da Task 1. O serviço oferece um snapshot imutável de atividade contendo `ObSpeedometerAlert` copiado e GPX até o mesmo último fix; `ActivityDraftFactory` recebe esse snapshot ou objetos capturados sob a mesma trava serial.
- A UI formata altitude opcional e `ElevationQuality`, preserva os últimos totais confirmados em sinal ruim e não apresenta `0 m` para ausência de altitude.

- [ ] **Step 1: Escrever testes que falham**: estado `WAITING/STALE/LOW_CONFIDENCE` não mostra altitude falsa, ganho/perda não pulam após pausa, save contém a mesma última amostra no GPX e no resumo; atividades antigas continuam legíveis.
- [ ] **Step 2: Rodar os testes focados** (JVM ActivityDraft e instrumentado de tela) e confirmar falhas ligadas ao comportamento novo.
- [ ] **Step 3: Implementar** snapshot coerente, formatação/estado de tela e atualização da ajuda EN/PT. Manter a altitude bruta no GPX e evitar alteração de schema sem teste de migração.
- [ ] **Step 4: Rodar testes focados**, `make test` e revisão visual manual com GPS ausente, preciso e pausado.
- [ ] **Step 5: Commit** `feat: show and persist consistent elevation estimates`.

### Task 5: Calibração, comparação e gate de liberação

**Files:** evoluir `tools/elevation_validation/compare.py`, `tools/elevation_validation/test_compare.py`, `gps/ElevationEstimatorTest.kt` e a documentação de análise, sem versionar tracks. Criar um pequeno runner de replay que invoque **o estimador Kotlin real** a partir do GPX local; o comparador Python atual só executa a lógica anterior e a sonda exploratória.

**Interfaces:** consome parâmetros e saídas das Tasks 1–4; produz relatório agregado por rota (`ganho`, `perda`, erro em trechos conhecidos, ruído plano, latência p50/p95, descartes, tempo por fix), sem coordenadas.

- [x] **Step 1a: Organizar e comparar três exportações Strava autorizadas** (`19463623301`, `20049386131`, `20338280241`) em `tools/gpx/issue-1/` ignorado, com manifesto, script e testes agregados em `tools/elevation_validation/`. O replay atual assume precisão horizontal constante e não modela pausas ou qualidade real.
- [ ] **Step 1b: Coletar ou obter consentidamente** pelo menos duas rotas independentes: uma plana ruidosa e uma montanhosa com dados brutos/precisão vertical e eventos de pausa; manter arquivos em `tools/gpx/` ignorado. Os três casos Strava são sensibilidade secundária, não gabarito de acurácia.
- [ ] **Step 2: Executar o estimador Kotlin real** em replay ponto a ponto e em blocos; confirmar invariância `≤0,01 m` e comparar com a implementação anterior sob hipóteses explicitadas. Ajustar janela/limiares somente se testes de ruído, subida e latência justificarem; registrar números e versão de parâmetros.
- [ ] **Step 3: Testar API 24 e 36** com `make test-e2e` e replay controlado; confirmar fallback sem precisão vertical, pausas, tela desligada, notification e resumo salvo. Medir atraso p95 `≤5 s` após confirmação sob 1 fix/s e ausência de ganho espúrio relevante na rota plana (meta provisória: `<10 m por hora`, sujeita à qualidade do material de referência).
- [ ] **Step 4: Rodar `make verify`** e revisar que `git status --short` não lista GPX, coordenadas ou logs brutos. Atualizar a análise com resultados observados e limitações.
- [ ] **Step 5: Commit** `test: validate elevation estimator on replay` e preparar review da branch. Só propor merge depois de revisar o gate com o usuário.

## Self-review do plano

- **Cobertura da spec:** entrada/qualidade (Tasks 1–2), estado e serialização (Tasks 1 e 3), UI/salvamento (Task 4), calibração/rollout (Task 5).
- **Consistência de tipos:** `ElevationSample` → `ElevationEstimator` → `ElevationEstimate` → serviço/`ObSpeedometerAlert` → resumo/UI; nenhum consumidor depende de `TrkPt.accuracy` como precisão vertical.
- **Risco material pendente:** os três GPX desta análise não substituem capturas brutas com qualidade vertical e pausas em aparelho. O gate da Task 5 exige esses dados e replay do Kotlin real antes de afirmar melhoria de acurácia ou publicar o cálculo.

## Estado da implementação inicial (16/10/2026)

Implementados nesta branch: estimador incremental Kotlin e testes puros; conversor Android compatível com API 24 e precisão vertical API 26+; deduplicação monotônica; controle de debounce da pausa automática; integração serial no serviço; snapshots coerentes de atividade; estado de altitude ausente na UI/resumo; replay da implementação Kotlin para as três trilhas privadas. A pausa automática retém fixes por até 3 s, reaplicando-os se o movimento voltar e descartando-os se a pausa for confirmada; fixes lentos continuam válidos enquanto a atividade permanece ativa.

Validações executadas: `make test` e `make test-elevation-replay` passaram. Os testes Android de conversão passaram em API 24/36. Após reiniciar o emulador API 36 (que havia parado de iniciar instrumentation depois de rodadas repetidas), `make test-e2e` passou **78/78** métodos aplicáveis nos dois emuladores. O runner também foi corrigido para imprimir o SDK escolhido sem fazer uma chamada ADB redundante depois de cada método.

Pendente antes de declarar calibração/rollout: rotas independentes plana e montanhosa com dados brutos/precisão vertical e eventos reais de pausa; replay dos cenários de pausa a partir de dados com marcações confiáveis; benchmark de tempo por fix e atraso p95; confirmação manual no aparelho de pausa, retomada, sinal ruim, tela desligada e salvamento. Não anexar GPX privados à issue nem incluí-los no Git.
