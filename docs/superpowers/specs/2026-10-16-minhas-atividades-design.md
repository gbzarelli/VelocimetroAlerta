# Minhas atividades — design

**Status:** aprovado para planejamento em 16 de outubro de 2026

## Objetivo

Transformar o encerramento de uma atividade em um fluxo de salvamento local: a pessoa nomeia e confirma ou descarta a sessão; as atividades concluídas ficam no feed **Minhas atividades**, com detalhe, rota estática expansível e exportação GPX posterior.

## Contexto e limites

- O app Android atual é Views/XML com Activities, Fragments e ViewBinding; a área **Minhas atividades** existe, mas é somente um placeholder.
- O rastreamento em `SpeedometerService` já forma um `Gpx` e mantém `ObSpeedometerAlert`, que contém métricas agregadas e de pausa.
- `GpxFileUtils` já escreve GPX 1.1 e extensões de ponto para frequência cardíaca, cadência, temperatura e umidade. Não há leitor GPX nem persistência de atividades.
- O mapa ao vivo recém-adicionado usa `RouteMapRenderer`/`RouteMapState`; ele não pode depender do histórico nem o histórico pode se ligar ao serviço de GPS.
- Esta entrega não inclui edição ou exclusão de atividades concluídas, importação de GPX, sincronização em nuvem, filtros, busca ou migração de dados antigos.
- Não introduzir Room, Compose ou novo SDK. Manter a paleta `colorPrimary` verde-petróleo e os componentes visuais atuais.

## Experiência aprovada

### Salvar uma atividade

1. O botão de salvar permanece disponível apenas para sessão pausada com pelo menos um minuto e pontos GPX.
2. Em vez de abrir o seletor de documentos, ele abre uma tela de conclusão com o nome sugerido `Atividade dd MMM, HH:mm`, derivado do início do rastreio e no locale do aparelho.
3. A pessoa pode substituir o nome. O campo não pode ficar em branco após `trim()`.
4. **Concluir atividade** persiste a atividade, encerra/limpa o serviço somente após a persistência integral e abre o detalhe daquela atividade.
5. **Descartar** não cria arquivos, encerra/limpa a sessão pausada e volta ao painel principal.
6. Erro de gravação preserva a sessão pausada e mostra uma mensagem com possibilidade de tentar novamente ou descartar; nunca abre o detalhe nem limpa o serviço nesse caso.

### Feed

`MyActivities` passa a listar atividades da mais recente à mais antiga e agrupá-las pelo mês/ano local do início. Cada cartão é inteiro clicável e contém:

- esboço da rota no topo, com a linha em verde-petróleo;
- nome e dia/mês;
- distância, tempo em movimento e velocidade média.

O cartão segue o wireframe aprovado: fundo branco, cantos arredondados, sombra discreta, tipografia escura e valores secundários em azul acinzentado. A rota é uma prévia estática, não um mapa carregado por rede. Sem atividades, a tela explica que atividades concluídas aparecerão ali.

### Detalhe e mapa

O detalhe mostra, nesta ordem:

1. toolbar com retorno ao feed;
2. esboço clicável da rota no topo;
3. nome e data/hora de início;
4. distância, tempo em movimento e velocidade média;
5. resumo: tempo total, tempo pausado, velocidade máxima, distância em pausa, ganho e perda de elevação;
6. sensores e GPS quando disponíveis: cadência média/máxima, frequência cardíaca média/máxima, temperatura, umidade, altitude final e precisão GPS;
7. única ação persistente: **Exportar GPX**.

Tocar no esboço abre o mapa histórico em tela cheia. Ele usa os mesmos controles de zoom/recentralização do mapa atual e renderiza uma rota imutável; não se conecta, inicia, pausa ou encerra `SpeedometerService`. A exportação abre o Storage Access Framework e copia o GPX armazenado; cancelar o seletor não modifica a atividade.

## Persistência

Cada atividade é privada ao app e fica em `filesDir/activities/<uuid>/`:

```text
activity.gpx
summary.json
```

`activity.gpx` é o registro canônico, contendo nome, tempo de metadados, track e extensões por ponto. Ele é escrito uma única vez da cópia imutável do `Gpx` da sessão no momento da confirmação. Exportar GPX copia este arquivo, sem reserialização.

`summary.json` contém, no mínimo:

- `id`, `name`, `startedAtEpochMillis`, `savedAtEpochMillis`;
- `distanceKm`, `distancePausedKm`, `movingDurationMillis`, `pausedDurationMillis`, `totalDurationMillis`;
- `averageSpeedKmh`, `maxSpeedKmh`;
- `finalAltitudeMeters`, `gpsAccuracyMeters`, `elevationGainMeters`, `elevationLossMeters`;
- `averageCadenceRpm`, `maxCadenceRpm`, `averageHeartRateBpm`, `maxHeartRateBpm`, `temperatureCelsius`, `humidityPercent`;
- `hasCadenceData`, `hasHeartRateData`, `hasTemperatureData`, `hasHumidityData`.
- `routePreview`: até 120 pares latitude/longitude, amostrados uniformemente do percurso e sempre incluindo o primeiro e o último ponto.

Valores indisponíveis não são apresentados como zero: os quatro sinalizadores distinguem ausência de sensor de uma medida real. `totalDurationMillis` é `movingDurationMillis + pausedDurationMillis`.

O repositório grava primeiro em um diretório temporário no mesmo pai, fecha os dois arquivos e renomeia/promove o diretório apenas quando ambos existem e são legíveis. Se qualquer etapa falhar, remove somente o temporário. Ao listar, ignora entradas incompletas ou ilegíveis e registra o erro, preservando as demais atividades.

## Componentes e fronteiras

- `ActivityRepository`: cria, lista e lê atividades; é a única camada que conhece `filesDir` e o layout de arquivos.
- `ActivityDraft`: instantâneo imutável da sessão pausada e de suas métricas antes do formulário de nome.
- `ActivitySummary`: representação serializável do `summary.json`, usada pelo feed e detalhe, incluindo a geometria resumida que desenha o esboço sem ler todos os GPX.
- `RoutePreviewView`: view local, sem SDK de mapas ou rede, que normaliza `routePreview` no seu retângulo e desenha a linha verde-petróleo no cartão e no topo do detalhe.
- Leitor em `GpxFileUtils`: converte o track privado salvo para `List<RoutePoint>` para o mapa histórico, preservando a ordem dos pontos. O escritor existente continua responsável por GPX 1.1.
- `MyActivities`: carrega resumos fora da thread principal e abre `ActivityDetailActivity` com um `EXTRA_ACTIVITY_ID`.
- `ActivityDetailActivity`: carrega um ID existente, mostra o resumo e inicia o mapa histórico com os pontos persistidos.
- O mapa histórico reutiliza `RouteMapRenderer` e `RouteMapState` com uma fonte estática. `RouteMapActivity` atual continua a ser a tela de rota ao vivo e mantém sua ligação opcional ao serviço.
- `MainActivityFragment`: cria o rascunho e navega para a conclusão; deixa de registrar `CreateDocument` e de manter `pendingExport` para o fluxo principal.

## Falhas e comportamento de borda

- Sessão sem pontos ou menor que um minuto segue o bloqueio existente e nunca cria rascunho.
- Nome em branco mantém o formulário aberto e informa o campo obrigatório.
- Atividade com sensor ausente oculta a métrica correspondente; todas as métricas de GPS e resumo válidas continuam visíveis.
- GPX sem pontos não abre um mapa vazio; o detalhe informa rota indisponível e permanece exportável somente se o arquivo canônico ainda for válido.
- Se o destino escolhido para exportação não abrir stream ou falhar durante a cópia, o arquivo interno permanece intacto e o erro é informado.
- Voltar do detalhe retorna ao feed; voltar do mapa retorna ao detalhe; nenhum desses caminhos altera o histórico ou a sessão ativa.

## Verificação

- Testes JVM para conversão de rascunho em resumo, campos opcionais, ordenação decrescente, layout de arquivos, gravação atômica e limpeza de temporários.
- Testes JVM para escrita/leitura de GPX, ordem de pontos e extensões que o mapa/exportação devem preservar.
- Testes instrumentados para concluir e descartar, apresentar feed/estado vazio, abrir detalhe/mapa estático, exportar/cancelar e preservar a sessão quando a persistência falha.
- Regressões para o mapa ao vivo: abrir mapa histórico não pode iniciar ou encerrar o serviço nem alterar seus pontos/estado.
- Comandos de validação alvo: `./gradlew :mobile:testDebugUnitTest`, `./gradlew :mobile:assembleDebug` e os testes instrumentados novos pelo runner hermético já usado pelo repositório.

## Critérios de aceite

1. Salvar uma sessão válida nunca abre diretamente o seletor de GPX; pede nome sugerido e permite concluir ou descartar.
2. Uma conclusão bem-sucedida aparece imediatamente no feed e abre seu detalhe.
3. O feed e o detalhe persistem após fechar/reabrir o aplicativo.
4. O detalhe apresenta somente dados efetivamente coletados e permite abrir o mapa estático no toque do esboço.
5. Exportar resulta em um GPX equivalente ao registro interno, com percurso e extensões de ponto preservados.
6. Uma falha de persistência ou exportação não perde a sessão nem corrompe atividades já concluídas.
