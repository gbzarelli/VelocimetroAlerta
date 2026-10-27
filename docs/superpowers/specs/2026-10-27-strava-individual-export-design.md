# Login Google, preferências na nuvem e envio manual ou automático ao Strava — desenho

## Intenção

Usar a issue [#11](https://github.com/gbzarelli/VelocimetroAlerta/issues/11) como referência para login Google/Firebase e sincronização de preferências, mantendo escolha explícita sobre os envios ao Strava. Por padrão, a pessoa abre uma atividade salva e toca em **Enviar ao Strava**; só aquela atividade é enviada. Como opção, depois de conectar o Strava, pode ativar **Sincronizar automático** em **Conta e conexões**. Nesse modo, cada atividade elegível salva dali em diante é enviada após o salvamento, sem tocar no botão individual.

O login Google é obrigatório para configurar ou usar a conexão Strava, alterar **Sincronizar automático** e sincronizar preferências na nuvem. O restante do app continua disponível localmente como convidado. Se a pessoa tocar em **Enviar ao Strava** sem login Google, o app solicita login e retoma a ação escolhida; depois solicita autorização Strava, se ainda não conectado, e retoma o envio da mesma atividade.

O tipo da atividade é selecionado ao salvá-la, persistido com o resumo e mapeado para um tipo aceito pelo Strava quando houver correspondência.

## Estado atual confirmado

- `ActivitySaveActivity` recolhe o nome; `MainActivityFragment.saveActivity()` salva a atividade e abre `ActivityDetailActivity`.
- `ActivityRepository` mantém `activity.gpx` e `summary.json` em `files/activities/<id>/`; `ActivitySummary` ainda não tem campo de tipo.
- `ActivityDetailActivity` já carrega o resumo pelo ID e oferece **Exportar GPX**.
- As configurações atuais ficam em `SharedPreferences`. O código atual não tem Firebase Auth, Firestore, Cloud Functions nem integração Strava.
- A issue #11 propõe Google/Firebase e sincronização automática ampla. Este desenho mantém login e preferências e oferece automático somente como opção, para atividades futuras; a biblioteca existente não é enviada em lote.

## Objetivos

- Exigir autenticação Google/Firebase antes de conectar o Strava ou sincronizar preferências na nuvem, sem exigir conta para gravar, consultar ou exportar GPX localmente.
- Sincronizar na nuvem apenas as preferências portáveis acordadas na issue.
- Pedir um tipo ao salvar uma nova atividade e guardar esse tipo no resumo.
- Adicionar **Enviar ao Strava** à tela de detalhes, preservando **Exportar GPX**.
- Durante um upload manual ou automático, manter a tela de detalhes aberta e substituir o botão Strava por uma barra animada **Sincronizando com Strava…**; manter **Exportar GPX** disponível.
- Mostrar o status de envio nos detalhes: depois da confirmação do Strava, desativar o botão e identificá-lo como **strava · Atividade já sincronizada** para evitar duplicatas; uma confirmação manual após resposta incerta deve aparecer como estado distinto.
- Se o resultado do upload for incerto, manter os detalhes abertos e mostrar ali o aviso **Não foi possível confirmar**, com **Já aparece no Strava** e **Tentar novamente**; ao tocar em **Já aparece no Strava**, mudar o botão na própria tela para **Presença confirmada manualmente**, sem navegar para uma tela de conclusão ou verificação.
- Adicionar em **Conta e conexões** um toggle **Sincronizar automático** logo abaixo da linha de conexão do Strava, desligado por padrão; se o Strava não estiver conectado, ele permanece visível, desativado e explica que é preciso conectar a conta.
- Com o toggle ligado, enviar automaticamente apenas atividades novas salvas depois da ativação, quando o Strava estiver conectado e o tipo tiver modalidade correspondente; manter envio manual de atividades existentes.
- Concluir login Google, OAuth Strava e upload de forma encadeada para a atividade cujo botão iniciou o fluxo, mesmo com recriação do processo Android.
- Manter GPX e resumo no aparelho; transmitir o GPX diretamente do aparelho ao Strava.
- Exibir estados de envio, processamento e falha, sem reportar sucesso antes da confirmação de processamento do Strava.

## Fora de escopo

- Envio de atividades ao conectar ou ao entrar com Google, backfill de atividades anteriores à ativação automática, importação/leitura do Strava, backup de GPX ou histórico de atividades entre aparelhos.
- Envio automático quando a opção estiver desligada, a conexão Strava estiver ausente/revogada ou o tipo da atividade não tiver modalidade equivalente.
- Login Google obrigatório para gravação, biblioteca local, exportação GPX ou uso geral do app.
- Converter categorias locais sem correspondência para uma modalidade Strava semanticamente diferente.

## Abordagens consideradas

1. **Sincronizar tudo ao conectar e fazer backfill.** Automatiza toda a biblioteca atual, mas envia atividades antigas sem escolha por item; fica fora do escopo.
2. **Somente envio manual por atividade.** Mantém controle individual total, mas exige repetir o toque para cada nova atividade.
3. **Envio manual com opção automática futura por conta. (Escolhida)** O modo padrão continua individual; a pessoa pode ativar/desativar o envio automático das próximas atividades em Conta e conexões. Atividades já existentes não entram em lote. Firebase mantém preferências da conta e backend OAuth mínimo protege o segredo/tokens, enquanto o GPX segue direto do Android ao Strava.

## Fluxos propostos

### Login e preferências

1. Sem sessão, o app abre como convidado. Gravação, biblioteca, configurações locais e exportação GPX continuam disponíveis.
2. Login/cadastro usa Credential Manager, Google ID token e Firebase Authentication. Uma conta Google existente e uma conta nova seguem o mesmo fluxo; o Firebase UID é a identidade dos dados remotos.
3. Preferências portáveis da issue: áudio, intervalo, vibração, pausa automática e zonas de frequência cardíaca. Nomes/endereços de sensores Bluetooth, permissões/consentimentos e `keep_alive` permanecem locais.
4. No primeiro login, se não há documento remoto, publicar as preferências portáveis deste aparelho. Se já existe perfil remoto, oferecer **Restaurar da nuvem** (opção inicial destacada) ou **Usar as deste aparelho** antes de substituir dados. Depois, sincronizar mudanças por campo.
5. Ao sair, encerrar a sessão remota e restaurar o perfil local de convidado. Não apagar dados locais nem preferências da conta. Não aplicar ajustes remotos que afetem uma gravação enquanto uma atividade estiver em andamento; adiar até pausar ou finalizar.

### Tipo e envio de atividade

1. A tela de salvamento pede nome e tipo obrigatório antes de concluir. Descartar continua sem criar atividade.
2. Tipos iniciais propostos:

   | Tipo no app | `sport_type` Strava | Envio |
   | --- | --- | --- |
   | Bike | `Ride` | Sim |
   | eBike | `EBikeRide` | Sim |
   | Mountain bike | `MountainBikeRide` | Sim |
   | Gravel | `GravelRide` | Sim |
   | eMountain bike | `EMountainBikeRide` | Sim |
   | Bike indoor/virtual | `VirtualRide` | Sim |
   | Corrida | `Run` | Sim |
   | Caminhada | `Walk` | Sim |
   | Trilha | `Hike` | Sim |
   | Veículo/carro | Sem correspondência | Local somente |

   A lista pode crescer quando a API aceitar uma modalidade equivalente. Veículo/carro não deve ser enviado como `Ride` ou `Workout` por aproximação.
3. Em atividades existentes sem tipo, ao tocar em **Enviar ao Strava**, pedir uma modalidade suportada e persistir a escolha confirmada. Para tipo local somente, explicar por que não pode ser enviado.
4. O envio manual começa na atividade aberta. Se não houver sessão Google, autenticar e retomar o `activityId`; se não houver vínculo Strava, abrir OAuth e, ao voltar, enviar esse mesmo `activityId`. Se qualquer etapa for cancelada ou falhar, permanecer nos detalhes e não enviar outra atividade.
5. Em **Conta e conexões**, exibir o toggle **Sincronizar automático** logo abaixo da linha de conexão do Strava. O padrão é desligado; sem Strava conectado, o controle fica visível, desativado e informa que é preciso conectar a conta. A preferência fica salva por UID Google e sincroniza entre aparelhos, mas somente GPX local deste aparelho pode ser enviado.
6. Ao ativar o toggle, não examinar nem enfileirar a biblioteca já salva. Cada salvamento futuro bem-sucedido agenda uma atividade elegível depois de `ActivityRepository.save()`; atividade descartada, não salva, de tipo local-only ou sem conexão não é enviada.
7. Ao desligar, não agendar novas atividades automáticas e cancelar trabalhos automáticos ainda não iniciados; deixar um upload já iniciado terminar para evitar um resultado remoto ambíguo. O botão manual de atividades existentes continua disponível. Conectar ou reconectar o Strava não reativa envios por si só.

### Arquitetura de integração

```text
Credential Manager → Firebase Auth (Google UID)
                          ├─ Firestore: users/{uid}/settings/current
                          └─ Cloud Functions autenticadas
                               ├─ iniciar OAuth / receber callback
                               ├─ renovar e revogar tokens Strava
                               └─ fornecer access token curto ao mesmo UID

ActivityRepository (GPX local) → trabalho único para UID + activityId
                                      ↓ HTTPS multipart
                               Strava POST /uploads → GET /uploads/{id}
                                                        ↓ activity_id
                                             PUT /activities/{id} (sport_type)
```

- `beginStravaConnect` exige Firebase Auth e cria `state` aleatório, de uso único, associado à UID e com prazo curto. O callback HTTPS público valida/consome o `state`, valida o escopo e troca o `code` no servidor. Redireciona ao Android via App Link verificado, sem incluir tokens no link. A associação Strava fica atrelada à UID Google autenticada.
- O segredo Strava fica no Secret Manager; refresh/access tokens ficam em armazenamento servidor protegido e inacessível a clientes. Renovação de refresh token rotativo deve ser serializada. Functions validam a Firebase Auth UID em cada chamada e não aceitam UID fornecida no corpo como identidade.
- Para enviar, uma callable autenticada fornece token curto ao mesmo usuário. O app guarda-o somente em memória e envia diretamente ao Strava o GPX da atividade selecionada, com nome, `data_type=gpx` e `external_id` estável baseado no ID local. O endpoint `POST /uploads` não oferece `sport_type`; depois que o upload termina e retorna `activity_id`, o app chama `PUT /activities/{id}` com o `sport_type` escolhido. Ambos os pedidos usam o token curto. O backend não recebe GPX nem coordenadas.
- O upload Strava é assíncrono. Após `POST /uploads`, acompanhar `GET /uploads/{id}` sem polling mais frequente que uma vez por segundo e só mostrar sincronizado quando houver `activity_id` final.
- Nos detalhes, marcar o botão como **Atividade já sincronizada** somente após obter `activity_id` e aplicar o tipo no Strava. Uma confirmação manual de presença deve usar rótulo próprio, sem sugerir que a API verificou o envio; ambos os estados impedem um novo envio acidental.
- Após uma confirmação manual, permanecer nos detalhes e mostrar **Presença confirmada manualmente** no botão; após confirmação da API, permanecer nos detalhes e mostrar **Atividade já sincronizada**. Não há tela separada de conclusão.
- Guardar estado de upload **localmente**, separado de `summary.json`, com chave UID + activityId: `not_uploaded`, `queued`, `uploading`, `processing`, `applying_type`, `synced`, `manually_confirmed`, `retryable_error`, `needs_action`. Se o arquivo chegar ao Strava mas a atualização de `sport_type` falhar, oferecer **Tentar definir tipo novamente**; essa ação repete somente `PUT /activities/{id}`, nunca o envio do GPX. Trabalho único por par impede envio manual e automático duplicados. No modo manual, só agendar o `activityId` escolhido; no automático, só o `activityId` recém-salvo. Conexão ou login não examina a biblioteca.
- Persistir `stravaAutoSyncEnabled` como preferência booleana por UID, padrão `false`, no documento de preferências e no cache local usado pelo agendador. Mudanças de conta devem cancelar/reconciliar somente trabalhos do UID correspondente; não usar uma preferência de outra sessão para decidir um envio.
- Erros de rede antes de obter `upload_id` são ambíguos: nos detalhes da atividade, substituir o botão Strava por **Ação necessária** e pedir que a pessoa confira o Strava. Ela pode marcar **Já aparece no Strava** (registrar confirmação manual, sem ID verificado) ou **Tentar novamente**, com aviso de que pode criar duplicata caso o primeiro pedido tenha sido aceito. Não afirmar que `external_id` garante idempotência nem repetir automaticamente. Tratar 401/revogação pedindo reconexão e 429 respeitando os limites do Strava.
- Desconectar revoga a autorização no Strava e remove credenciais do servidor. Mantém preferências e atividades locais.

### Segurança e escopo de dados

- Firestore contém somente as preferências portáveis por UID. Regras negam acesso cruzado e rejeitam campos/tipos não permitidos; coleção de tokens é negada a clientes.
- Envio manual transmite somente o GPX da atividade selecionada; envio automático transmite somente GPX de atividade nova salva após o opt-in. Nenhum percurso vai para Firebase. Login ou conexão não importa nem envia arquivos.
- Atualizar política de privacidade e declaração Data Safety antes do lançamento para explicar login, preferências em nuvem, vínculo de conta Strava e envio manual/automático do percurso e timestamps.

## Critérios de aceitação

- Como convidado, é possível gravar, consultar, configurar localmente e exportar GPX; nenhum fluxo Strava nem sincronização de preferências começa sem autenticação Google.
- Ao tentar conectar Strava ou sincronizar preferências, login Google é exigido; cancelamento mantém uso local intacto.
- Login com Google cria/retoma a UID Firebase; as preferências portáveis restauram/sincronizam conforme a escolha, sem enviar dados Bluetooth, consentimentos ou `keep_alive`.
- Uma atividade nova exige tipo. Nome, percurso, métricas e exportação GPX continuam funcionando. Atividades antigas pedem tipo suportado antes do primeiro envio.
- Ao tocar **Enviar ao Strava**, fluxo de login e conexão, quando necessários, retoma apenas a atividade selecionada; nenhuma outra atividade é enfileirada.
- O toggle **Sincronizar automático** fica logo abaixo da linha do Strava na tela de conta, começa desligado e fica desativado sem Strava conectado. Ligá-lo agenda apenas salvamentos elegíveis seguintes; não faz backfill. Desligá-lo impede novas filas automáticas, cancela as ainda não iniciadas e deixa uploads já iniciados terminar.
- Com modo automático ligado, salvar uma atividade elegível inicia o envio depois da persistência local e mostra o estado de envio nos detalhes; tipo local-only, cancelamento ou falha de salvamento nunca envia GPX. Com modo desligado, salvar não envia.
- Durante os estados `uploading`, `processing` e `applying_type`, a tela de detalhes continua aberta; o mesmo indicador animado substitui o botão Strava nos envios manuais e automáticos, enquanto **Exportar GPX** permanece disponível.
- Conectar/reconectar Strava ou entrar com Google não inicia upload. Atividades antigas continuam podendo ser enviadas individualmente.
- Sucesso verificado só é indicado depois que o Strava retornar `activity_id` e aceitar a atualização do `sport_type`; falha na atualização permite retentar somente essa etapa. Confirmação manual após POST ambíguo é identificada como tal, sem alegar verificação pela API. Reabrir a tela ou reiniciar o app não cria novo envio automaticamente.
- Tipo local-only não é enviado. Falhas, cancelamentos e desconexão preservam GPX e atividade locais.
- Desconectar revoga credenciais do servidor sem apagar atividades locais ou preferências sincronizadas.
- Testes cobrem mapeamento de tipos, leitura de `summary.json` legado, preservação do `activityId` pendente, OAuth e `state`, isolamento por UID, sincronização/merge de preferências incluindo `stravaAutoSyncEnabled`, default desligado, criação/cancelamento das filas automáticas, ausência de backfill, estados assíncronos, falha entre upload e alteração de tipo, confirmação/reenvio em resultado ambíguo e prevenção de envio duplicado.

## Decisões e dependências

- Login Google é obrigatório para Strava e preferências de nuvem; não é requisito para as funcionalidades locais.
- Sincronização automática é opcional, desligada por padrão, vinculada à conta Google e cobre somente atividades elegíveis salvas depois de ativada. Atividades existentes continuam manuais.
- O estado de envio e GPX permanecem no aparelho. Reinstalação ou troca de dispositivo não restaura atividades nem histórico de upload, embora login Google recupere preferências e vínculo Strava.
- O escopo OAuth Strava necessário é `activity:write`; não solicitar leitura de atividades.
- Implantação precisa de projetos Firebase de desenvolvimento/produção, app Strava, domínio HTTPS de callback, associação App Links e credenciais de assinatura. São parâmetros de implantação, sem IDs ou segredos no código.
- Cloud Functions/Secret Manager podem exigir faturamento e dependem de cotas/capacidade do app Strava. Confirmar disponibilidade e limites antes do lançamento público.
- Validar versões atuais de Firebase, Credential Manager e API Strava durante implementação. Seguir Kotlin/XML, strings EN/PT, minSdk 24 e comandos `make` documentados.

## Referências verificadas

- Issue de referência: https://github.com/gbzarelli/VelocimetroAlerta/issues/11
- Firebase Google Sign-In Android: https://firebase.google.com/docs/auth/android/google-signin
- Firestore Rules por UID: https://firebase.google.com/docs/firestore/security/rules-conditions
- Callable Functions e autenticação: https://firebase.google.com/docs/functions/callable
- Segredos para Cloud Functions: https://firebase.google.com/docs/functions/config-env
- Strava OAuth: https://developers.strava.com/docs/authentication/
- Upload GPX e processamento assíncrono: https://developers.strava.com/docs/uploads/
- Referência API para upload e alteração de `sport_type`: https://developers.strava.com/docs/reference/#api-Uploads-createUpload e https://developers.strava.com/docs/reference/#api-Activities-updateActivityById
- Strava limites/capacidade: https://developers.strava.com/docs/rate-limits/
- Segredos compartilhados em apps nativos: https://www.rfc-editor.org/info/rfc8252
