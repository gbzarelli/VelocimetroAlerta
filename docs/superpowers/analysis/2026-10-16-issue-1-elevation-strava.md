# Issue #1 — análise de elevação com a atividade TDL na Canastra

Data: 16/10/2026. Base: `master` em `22b77937065c102fc9f0dc6f83ad523031e92850`.

## Escopo e fontes

- [Issue #1](https://github.com/gbzarelli/VelocimetroAlerta/issues/1).
- [Atividade 19463623301 no Strava](https://www.strava.com/activities/19463623301), consultada no Chrome autenticado do usuário: 63,69 km, 4:03:18 de movimento, 5:26:22 decorridos e **2.174 m de ganho de elevação**.
- Exportação GPX dessa atividade, baixada em 16/10/2026 e guardada **somente** em `tools/gpx/TDL_na_Canastra.gpx` desta worktree; o diretório é ignorado pelo Git. O arquivo original também permanece em `~/Downloads`. Não publicar, adicionar ao Git ou anexar o GPX sem autorização explícita.
- Código local: `GpxMath.kt`, `SpeedometerService.kt`, `Gps.kt`, `TrkSeg.java`, `TrkPt.java`, `ActivityModels.kt`, `MainActivityFragment.kt` e testes correspondentes.
- [Strava: Elevation on Strava FAQs](https://support.strava.com/en-us/articles/15402093-elevation-on-strava-faqs), [Strava: Elevation](https://support.strava.com/en-us/articles/15401909-elevation) e [Android `Location`](https://developer.android.com/reference/android/location/Location).

Reproduzir os agregados, sem expor coordenadas ou horários: `python3 tools/analyze_elevation_gpx.py tools/gpx/TDL_na_Canastra.gpx --reference-gain 2174`.

## O que o GPX permite afirmar

O GPX exportado contém **14.560 pontos**, todos com `<ele>` e `<time>`, em um único segmento. A distância entre pontos por Haversine soma **64,002 km**, 0,312 km (0,49%) acima dos 63,69 km apresentados no Strava. Isso já mostra que a página aplica processamento diferente do somatório ingênuo do arquivo. A altitude do arquivo começa em 681,2 m, termina em 682,3 m e varia entre 675,3 e 1.280,0 m; a diferença líquida é só +1,1 m. O somatório de todas as subidas entre pontos consecutivos, **sem filtro**, é 2.322,5 m; o de descidas, 2.321,4 m.

O GPX **não contém** precisão horizontal, precisão vertical, pressão, identificação do dispositivo ou dados sobre correção de elevação. `TrkPt.accuracy` é preenchido a partir de `Location.getAccuracy()` durante a gravação pelo app, mas é descartado na exportação GPX. Ao ler um GPX externo com o modelo atual, a precisão fica no valor padrão `0`, e `GpxMath` soma praticamente cada incremento. Assim, os cenários de 3 a 15 m abaixo são **suposições de precisão horizontal constante**, não medições dessa pedalada nem replay fiel no aparelho.

O Strava diz que atividades sem altímetro barométrico reconhecido podem ter elevação corrigida por uma base externa e suavizada; o valor de 2.174 m é uma **estimativa processada da plataforma**, não verdade de terreno. A página identifica o gravador como “Strava App” e não oferece “Corrigir elevação” no menu observado, o que é compatível com essa correção, mas não comprova a origem de cada `<ele>` exportado. A comparação entre 2.174 m e os totais abaixo mede concordância entre métodos diferentes; não mede erro absoluto do app. Altitude absoluta de `Location.getAltitude()` usa o elipsoide WGS84, enquanto outras fontes podem usar referência do nível médio do mar, reforçando que altitude absoluta e ganho acumulado são comparações distintas.

## Reprodução do algoritmo atual

`GpxMath.calculateGain(start, points)` calcula uma **média global de precisão horizontal** a cada execução e a usa como limiar vertical. A função não conserva estado entre chamadas. `SpeedometerService` dispara o cálculo a cada distância **estritamente maior** que 1 km desde a última execução, antes de adicionar a localização atual ao GPX; uma pausa chama cálculo integral e substitui os acumulados. A tabela reproduz a função com precisão constante e um fluxo **sequencial idealizado** de blocos de 1 km. Concorrência, pausas automáticas, rejeição de pontos por precisão e a frequência real de `Location` não são reproduzidas.

| Precisão horizontal assumida | Ganho/perda, track completo | Ganho/perda, blocos de 1 km | Diferença do ganho em blocos vs. completo |
|---:|---:|---:|---:|
| 0 m (padrão de um GPX sem precisão) | 2.322,5 / 2.321,4 m | — | — |
| 3 m | 2.137,7 / 2.137,5 m | 2.073,6 / 2.073,7 m | −64,1 m |
| 5 m | 2.007,9 / 1.994,9 m | 1.941,8 / 1.938,0 m | −66,1 m |
| 8 m | 1.849,3 / 1.848,1 m | 1.726,2 / 1.795,4 m | −123,1 m |
| 10 m | 1.793,9 / 1.804,8 m | 1.650,7 / 1.725,7 m | −143,2 m |
| 12 m | 1.761,2 / 1.746,0 m | 1.566,8 / 1.663,8 m | −194,4 m |
| 15 m | 1.668,7 / 1.615,1 m | 1.499,4 / 1.573,8 m | −169,3 m |

No cenário hipotético de 5 m, o cálculo integral fica **166,1 m (7,6%) abaixo** do Strava; o de blocos, **232,2 m (10,7%) abaixo**. Com precisão não informada (`0`), o cálculo integral fica **148,5 m (6,8%) acima**. Escolher um limiar apenas para atingir 2.174 m nesta rota seria ajuste excessivo a uma referência processada. O dado mais robusto é a **não equivalência entre blocos e cálculo integral sobre os mesmos pontos**.

### Causas confirmadas no código

1. `Location.getAccuracy()` é precisão **horizontal**; o Android expõe `hasVerticalAccuracy()`/`getVerticalAccuracyMeters()` desde a API 26. Nem a média de todo o percurso nem um valor horizontal instantâneo representam incerteza vertical.
2. A cada bloco, `calculateGain` reinicia `climb` e o ponto âncora. O serviço preserva só o último índice. Mudanças abaixo do limiar que atravessam a fronteira de 1 km deixam de ser acumuladas. Na pausa, o cálculo começa no índice zero e altera retroativamente o valor exibido.
3. O gatilho de 1 km atrasa a atualização por cerca de **3 min 49 s** na velocidade média de 15,7 km/h mostrada pelo Strava; em subidas lentas pode demorar mais. O cálculo é chamado **antes** da inserção do ponto corrente, acrescentando uma amostra de atraso. A gravação de uma atividade ativa usa `ActivityDraftFactory.from(service.gpx, alert, ...)` sem finalizar explicitamente a altimetria.
4. Uma oscilação isolada `100 → 106 → 100` com limiar 5 m gera **6 m de ganho e 6 m de perda**. Há teste para perfil simples e lista vazia, mas não para esse caso, subida lenta, blocos, pausa ou variação de qualidade.
5. A flag `tmpCalculatingAltimetry` é ativada **dentro da thread**, depois da checagem. Duas chamadas podem iniciar threads; a lista `ArrayList` de `TrkPt` pode ser modificada durante a leitura; acumulados e callbacks também são acessados por threads diferentes.
6. `Gps.getLocation()` é amostrado por um loop de 1 s. Enquanto `views <= 2`, o mesmo `Location` pode ser processado novamente. Os pontos duplicados têm distância zero, mas aumentam o track e o custo do cálculo, que percorre todos os pontos para recalcular a média da precisão. `accuracy <= 15` aceita valor zero/ausente como se fosse excelente.
7. A exibição de altitude usa a medição bruta do GPS, enquanto ganho/perda usam outro caminho. Não há estado explícito para altitude indisponível, precisão vertical ausente ou estimativa temporariamente congelada. O texto de ajuda promete recálculo na pausa, que deixaria de ser válido com processamento incremental.

### Experimento exploratório, não proposta calibrada

O script inclui uma **sonda** com mediana causal dos últimos 5 valores e máquina persistente de excursão/reversão. Sobre o GPX já processado pelo Strava, produziu ganho/perda de 2.139,4 / 2.139,5 m com limiar 5 m, e 1.948,0 / 1.947,9 m com limiar 10 m. O primeiro total fica 34,6 m (1,6%) abaixo da página. Essa proximidade **não valida** o limiar de 5 m: a rota é uma amostra só, não contém a precisão vertical original e já pode estar suavizada/corrigida. A mediana de 5 pontos ignora uma única espícula `100,100,100,100,100,106,100,...`, mas introduz atraso de cerca de duas amostras em subida contínua. O valor para parada/finalização precisa de regra própria para a cauda do filtro.

## Recomendações prioritárias

1. **Separar qualidade horizontal de vertical.** Conservar a regra horizontal para aceitar posição/distância conforme a política atual; para elevação exigir `hasAltitude()`, número finito e amostra nova. Usar precisão vertical quando disponível, sem converter a horizontal em vertical. A política sem precisão vertical (API 24–25 ou provedor incompleto) deve ser explícita, conservadora e calibrada em mais de uma rota.
2. **Processar cada fix novo em série.** Um estimador puro e incremental deve possuir seu filtro, mínimo/máximo, direção, ganho e perda; chamadas em blocos e replay contínuo devem produzir o mesmo resultado. `pause` congela; `resume` mantém a referência sem conectar saltos; `stop/save` finaliza e publica um snapshot atômico. Nenhuma leitura da lista GPX em mutação.
3. **Filtrar sem esconder subidas graduais.** Usar uma pequena janela causal robusta para espículas e uma histerese de reversão para não contar zigue-zague vertical curto. A escolha de janela/limiar deve minimizar ganho espúrio e atraso em testes sintéticos e replays independentes; 10 m sem barômetro é uma referência externa, não uma meta de equivalência com o Strava.
4. **Exibir procedência e frescor.** Ganho/perda confirmados atualizam no mesmo ciclo de fix aceito (meta: até 5 s depois de ultrapassar o limiar sob fix por segundo). Altitude atual pode usar a mesma amostra filtrada, com estado “aguardando GPS”/“sinal impreciso” quando necessário. Não exibir `0 m` como medição ausente nem saltar os totais ao pausar.
5. **Validar sem circularidade.** Primeiro testes de propriedades (invariância por blocos, monotonicidade, conservação aproximada de ganho−perda, repetição e pausas). Depois replays de pelo menos um percurso plano e outro montanhoso **com dados brutos e precisão vertical**, além desta rota. Comparar Strava só como referência secundária. Instrumentar contagens de amostras aceitas/rejeitadas e latência, sem registrar coordenadas nem altitudes brutas em logs de produção.

## Limites deste trabalho

Não foi implementada mudança no app nem executado replay de 5 h no emulador. O GPX exportado não informa a precisão dos fixes nem se cada altitude é do sensor original ou da correção posterior. Também não há medição independente de terreno ou arquivo original de sensores; portanto **não se pode afirmar** que algum dos totais reproduz a medição real do aparelho. `make test` passou na base antes das mudanças desta análise; os testes do script e a inspeção final são registrados no fechamento da worktree.
