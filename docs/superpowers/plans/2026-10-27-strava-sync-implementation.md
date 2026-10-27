# Login Google, preferências na nuvem e envio manual ou automático ao Strava — plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Manter o uso local como convidado e oferecer login Google, preferências por UID e envio de atividades ao Strava manualmente ou, por escolha, automaticamente para novas atividades salvas.

**Architecture:** Kotlin/XML mantém GPX, resumo, tipo e estado de envio no aparelho. Firebase Auth identifica a conta; Firestore sincroniza preferências, inclusive o toggle automático; Cloud Functions e Secret Manager protegem OAuth e tokens do Strava. Um coordenador local inicia trabalho único por conta e atividade: pela ação manual dos detalhes ou após salvar uma atividade futura quando a preferência automática está ligada.

**Tech Stack:** Android/Kotlin/XML (`minSdk 24`, `targetSdk 36`), Credential Manager/Google ID, Firebase Authentication/Firestore/Functions, TypeScript Cloud Functions, Secret Manager, WorkManager, Gson e API Strava. Conferir versões suportadas no começo da implementação.

**Spec:** `docs/superpowers/specs/2026-10-27-strava-individual-export-design.md`

## Baseline do worktree

- Base verificada em `522173e` (`feat: use bottom banners for activity screens`); execução isolada na branch `gz/strava-individual-sync`.
- `settings.gradle` inclui somente `:mobile`; `mobile/build.gradle` e `gradle/libs.versions.toml` ainda não incluem Firebase, Credential Manager ou WorkManager, e o repositório não tem configuração `functions/`.
- `ActivitySaveActivity` retorna nome e ação; `MainActivityFragment.saveActivity()` chama `ActivityRepository.save()` e abre `ActivityDetailActivity` após o salvamento.
- `ActivitySummary` não tem tipo. `ActivityRepository` salva `summary.json` e `activity.gpx` juntos em `files/activities/<id>/`; `ActivityDetailActivity` oferece somente **Exportar GPX**.
- O drawer usa `activity_main_drawer.xml` e `nav_header_main.xml`; preferências atuais usam `PreferenceManager.getDefaultSharedPreferences` e existem traduções em `values/strings.xml` e `values-pt/strings.xml`.
- A issue #11 originalmente propõe envio de atividades já salvas ao conectar. As decisões posteriores deste plano substituem esse comportamento: envio manual de uma atividade por vez e automático somente para novos salvamentos após opt-in, sem backfill.

## Estado da execução — 2026-10-27

- Implementados o login Google/Firebase, perfis locais por conta, sincronização de preferências permitidas, tela Conta e conexões, conexão OAuth Strava no servidor, classificação de atividade, envio individual/automático via WorkManager, estado local do envio e ações de recuperação nos detalhes.
- Adicionados regras do Firestore, configuração e documentação para Firebase/Strava, aviso de dados no README e compilação de Functions na CI.
- `make build` e `npm ci && npm run build` passaram. O Android foi compilado sem `mobile/google-services.json`; a compilação das Functions foi executada com Node 26, embora o runtime de projeto/CI esteja configurado para Node 22.
- Testes automatizados e fluxos reais de OAuth/upload não foram executados nesta rodada. As credenciais/configuração Firebase não estão presentes.
- Permanecem como gates externos: publicar App Links `assetlinks.json` e fallback de callback no site HelpDev, atualizar a política de privacidade no repositório do site e a declaração Data Safety no Play Console, configurar projetos Firebase/App Check e cadastrar o app/credenciais Strava.

## Global Constraints

- Login Google é obrigatório para conectar o Strava ou sincronizar preferências; gravação, biblioteca local, configurações locais e exportação GPX continuam disponíveis como convidado.
- `Sincronizar automático` é opcional, tem padrão desligado e abrange atividades elegíveis salvas depois da ativação; o toggle fica logo abaixo da linha do Strava na tela de conta, visível e desativado até conectar o Strava, e não faz backfill.
- O botão manual nos detalhes continua disponível para atividades já salvas e usa o mesmo trabalho único UID + `activityId` que o modo automático.
- Durante upload manual ou automático, a tela de detalhes permanece aberta e o botão Strava é substituído por uma barra animada “Sincronizando com Strava…”; **Exportar GPX** continua disponível.
- Em resultado incerto, a tela de detalhes substitui a ação Strava por um aviso e os botões **Já aparece no Strava** e **Tentar novamente**; tocar em **Já aparece no Strava** muda o botão para **Presença confirmada manualmente** na mesma tela, sem redirecionar para uma tela de conclusão ou verificação.
- Após confirmação final da API, o próprio botão dos detalhes mostra **Atividade já sincronizada**; não há tela separada de conclusão.
- O tipo é obrigatório em novas atividades; veículo/carro e qualquer tipo sem mapeamento Strava ficam apenas no aparelho.
- GPX e estado de envio não vão para Firebase. Android envia o GPX local diretamente ao Strava usando token curto obtido para a UID autenticada.
- O `sport_type` é enviado no mesmo POST do GPX, conforme a referência atual da API Strava; não há atualização posterior do tipo.
- `stravaAutoSyncEnabled` é booleano por UID, padrão `false`; preferências Bluetooth, consentimentos/permissões e `keep_alive` permanecem locais.
- Firebase Auth, Firestore e callable Functions exigem Firebase App Check em release; usar provedor debug somente em desenvolvimento/emulador.
- Fluxos de upload não repetem às cegas um POST ambíguo; enviar `sport_type` junto com o GPX e só indicar sucesso após o Strava retornar `activity_id`.
- `client_secret` e `refresh_token` nunca ficam no APK, repositório, respostas do app ou logs; strings novas devem existir em inglês e português.
- A callable autenticada pode devolver access token Strava ao próprio UID para upload direto; o app o mantém somente em memória e nunca o grava ou registra em log.
- Resumos antigos sem tipo continuam legíveis. O tipo só é solicitado antes do envio individual de uma atividade antiga.

## Review Focus

- Salvamento atômico, descarte e tipo sem correspondência: só enfileirar depois de salvar localmente com sucesso; testar falha no save, descarte e `Vehicle` sem upload.
- Corrida entre botão manual e gatilho automático para a mesma atividade: compartilhar chave única UID + `activityId`; testar enqueue simultâneo sem duplicar upload.
- Toggle desligado com trabalho pendente ou em andamento: cancelar trabalho automático ainda não iniciado, concluir o upload que já começou e não agendar os próximos; testar cada estado.
- Logout, troca de UID, token revogado e preferência de outra conta: nunca usar credencial ou opção automática de um UID em outra conta; testar isolamento e reconciliação.
- Resumo legado sem tipo ou atividade excluída com trabalho pendente: solicitar classificação antes do envio e cancelar o trabalho enfileirado ao excluir; preservar GPX e não marcar sucesso incorretamente.

---

### Task 1: Preparar Firebase, regras e chamadas seguras do backend

**Files:**
- Create: `firebase.json`, `.firebaserc.example`, `firestore.rules`
- Create: `functions/package.json`, `functions/package-lock.json`, `functions/tsconfig.json`, `functions/src/index.ts`, `functions/src/stravaOAuth.ts`, `functions/src/stravaTokenStore.ts`
- Create: `functions/test/settings.rules.test.ts`, `functions/test/stravaOAuth.test.ts`
- Modify: `.gitignore`, `.github/workflows/android.yml`, `Makefile`

**Interfaces:**
- Exporta `beginStravaConnect()`, `getStravaConnectionState()`, `getStravaAccessToken()` e `disconnectStrava()`; identidade vem exclusivamente de Firebase Auth, nunca de UID enviada no payload.
- Callable Functions e acesso Firestore aplicam Auth, Rules e App Check em produção; ambiente de teste usa emuladores e provedor debug.
- Callback HTTPS consome `state` aleatório, associado à UID e de uso único; troca o código, grava access/refresh tokens no servidor e redireciona via Android App Link sem credenciais.
- Firestore permite ao cliente somente `users/{uid}/settings/current`; token Strava fica em documento/coleção privada, negado a qualquer cliente.
- `getStravaAccessToken()` retorna somente access token vigente ao UID autenticado; client secret e refresh token nunca saem do servidor. GPX e coordenadas não passam pelas Functions.

- [ ] **Step 1: Escrever testes de regras e OAuth** com os casos `sameUidCanReadAndWriteAllowedSettings`, `otherUidCannotReadOrWriteSettings`, `clientCannotReadStravaTokens`, `rejectsUnknownSettingKeys`, `rejectsInvalidIntervalMode`, `rejectsUnauthenticatedConnect`, `rejectsExpiredOrReusedState` e `callbackDoesNotExposeRefreshToken`.
- [ ] **Step 2: Executar os testes Firebase focados** pelo Emulator Suite; confirmar falha inicial para autorização ausente, acesso cruzado e reuso do state.
- [ ] **Step 3: Implementar Firestore Rules e o armazenamento de `users/{uid}/settings/current`**, com esquema que aceite somente chaves/tipos definidos na Task 2.
- [ ] **Step 4: Implementar Functions de OAuth/token**; guardar `STRAVA_CLIENT_SECRET` no Secret Manager, validar `activity:write`, serializar a renovação do refresh token e aplicar enforcement de App Check em produção.
- [ ] **Step 5: Adicionar scripts de teste do Firebase e um target `make test-firebase`**, usando somente emuladores e configuração de desenvolvimento; não exigir credenciais de produção.
- [ ] **Step 6: Rodar testes de Functions/regras e `make build`**; verificar que builds de PR não precisam de arquivo ou segredo Firebase de produção.
- [ ] **Step 7: Commitar a infraestrutura e contratos do backend** com testes focados aprovados.

### Task 2: Login Google e preferências por conta

**Files:**
- Create: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/auth/GoogleAuthRepository.kt`, `auth/AccountSession.kt`
- Create: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/settings/SyncableSettings.kt`, `settings/SettingsSyncRepository.kt`, `settings/LocalProfileStore.kt`
- Create: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/AccountConnectionsActivity.kt`, `mobile/src/main/res/layout/activity_account_connections.xml`
- Create: `mobile/src/test/java/br/com/helpdev/velocimetroalerta/auth/GoogleAuthRepositoryTest.kt`, `mobile/src/test/java/br/com/helpdev/velocimetroalerta/settings/SyncableSettingsTest.kt`, `mobile/src/test/java/br/com/helpdev/velocimetroalerta/settings/SettingsSyncRepositoryTest.kt`
- Modify: `VelocimetroApplication.kt`, `MainActivity.kt`, `activity_main_drawer.xml`, `nav_header_main.xml`, `AndroidManifest.xml`, `gradle/libs.versions.toml`, `mobile/build.gradle`, `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-pt/strings.xml`

**Interfaces:**
- `AccountSession` expõe `uid: String?` e estado de login; `GoogleAuthRepository.signIn()` usa Credential Manager + Firebase Auth e `signOut()` encerra a sessão.
- `SyncableSettings` serializa a allowlist de tipos: `key_informar_tempo`, `key_informar_distancia`, `key_informar_vatual`, `key_informar_vmedia`, `key_informar_vmax`, `pref_vibrar` (Boolean); `key_intervalo`, `key_intervalo_valor` (String); `key_pause_automatico` (Boolean); `pref_hr_max_value`, `pref_hr_rest_value`, `pref_hr_z1_value` até `pref_hr_z5_value` (Int); `stravaAutoSyncEnabled` (Boolean, padrão false).
- `key_intervalo` é normalizada no documento remoto para `distance` ou `time`; converter os valores locais em inglês (`kilometers`, `minutes`) e português (`quilômetro`, `minutos`) para evitar conflito entre idiomas.
- `pref_module_vel_alert*`, `pref_hr_sensor*`, flags de consentimento/permissão e `keep_alive` ficam locais e não podem aparecer no payload remoto.
- `SettingsSyncRepository.observeCurrentAccount()` e `setStravaAutoSyncEnabled(enabled)` usam somente a UID da sessão Firebase atual; gravam por campo em `users/{uid}/settings/current` e aplicam snapshots sem ciclo de regravação. `LocalProfileStore` separa o perfil convidado do cache por UID.
- `AccountConnectionsActivity` apresenta login/nome Google, ação **Sair**, conexão Strava e preferências. No header do drawer, subtítulo mostra `helpdev.com.br` como convidado ou o nome Google conectado; botão muda de **Entrar com Google** para **Conta e conexões**.

- [ ] **Step 1: Escrever testes para a allowlist e migração** em `SyncableSettingsTest`: `intervalModeUsesStableRemoteValues`, `legacyEnglishAndPortugueseIntervalValuesRoundTrip`, defaults/tipos portáveis e exclusão de sensor, consentimento, permissão e `keep_alive`.
- [ ] **Step 2: Escrever testes de sessão/sincronização** `cancelledGoogleSignInKeepsGuestMode`, `firstLoginPublishesLocalSettingsWhenRemoteIsEmpty`, `existingRemoteSettingsRequireExplicitChoice`, `logoutRestoresGuestProfile`, `settingsCacheIsIsolatedByUid`, `offlineChangesSyncWhenOnline` e `remoteSnapshotDoesNotEchoWrite`.
- [ ] **Step 3: Executar os testes unitários focados** e confirmar a falha antes de implementar os repositórios.
- [ ] **Step 4: Implementar Credential Manager/Firebase Auth, App Check e os adaptadores de perfil**; usar Play Integrity em release, provedor debug somente no build de desenvolvimento/emulador e manter `SharedPreferences` como projeção local imediata para telas e serviço GPS.
- [ ] **Step 5: Implementar reconciliação de preferências**; para primeiro login sem documento publicar o perfil local; com documento existente, oferecer **Restaurar da nuvem** (padrão destacado) ou **Usar as deste aparelho**. Aplicação que altera rastreamento/áudio espera a atividade pausar ou terminar.
- [ ] **Step 6: Criar `AccountConnectionsActivity` e integrar a linha de conta ao header**; convidado vê helpdev.com.br e **Entrar com Google**; conta conectada vê o nome Google e **Conta e conexões**. Logout repõe o perfil convidado sem apagar GPX, resumos nem caches de outras UID.
- [ ] **Step 7: Rodar testes focados, `make test-unit` e `make build`**; verificar login cancelado e sessão de convidado continuam permitindo gravar, navegar e exportar GPX.
- [ ] **Step 8: Commitar conta Google, perfis locais e preferências em nuvem** com regras Firebase da Task 1.

### Task 3: Persistir tipo da atividade no salvamento local

**Files:**
- Create: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/activities/ActivityType.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/activities/ActivityModels.kt`, `activities/ActivityRepository.kt`, `ActivitySaveActivity.kt`, `MainActivityFragment.kt`
- Modify: `mobile/src/main/res/layout/activity_save_activity.xml`, `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-pt/strings.xml`
- Create: `mobile/src/test/java/br/com/helpdev/velocimetroalerta/activities/ActivityTypeTest.kt`
- Modify: `mobile/src/test/java/br/com/helpdev/velocimetroalerta/activities/ActivityRepositoryTest.kt`, `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/ActivitySaveFlowTest.kt`

**Interfaces:**
- `ActivityType` usa chaves persistidas estáveis e expõe `stravaSportType: String?`; valores e modalidades são os da tabela no spec. **Veículo/carro** tem `null` e nunca é enviado.
- `ActivitySummary.activityType: ActivityType? = null` preserva leitura de `summary.json` legado; o campo é obrigatório ao gravar uma atividade nova. O seletor inicia em **Bike**, como no mockup, e permite alterar antes de salvar.
- `ActivityRepository.save(draft, confirmedName, activityType)` inclui tipo no resumo salvo sem mudar bytes/conteúdo do GPX; `updateActivityType(id, type)` permite classificar uma atividade legada antes do primeiro envio.

- [ ] **Step 1: Adicionar testes de mapeamento** `mapsBikeAndSupportedTypesToStravaSports`, `vehicleHasNoStravaSport` e `stableKeysAreIndependentOfLocalizedLabels` em `ActivityTypeTest`.
- [ ] **Step 2: Adicionar testes de compatibilidade/persistência** `savePersistsActivityType`, `loadsLegacySummaryWithoutActivityType`, `updateActivityTypePreservesGpxBytes` e `rejectsBlankActivityName` em `ActivityRepositoryTest`; rodar `make test-unit` e confirmar as falhas esperadas.
- [ ] **Step 3: Implementar enum e persistência**; anexar o tipo em `ActivityRepository.save` (não em `ActivityDraftFactory`) e gravar a alteração de uma atividade legada com escrita temporária/atômica.
- [ ] **Step 4: Adicionar seletor requerido a `ActivitySaveActivity`**; retornar nome e chave estável do tipo. Atualizar `MainActivityFragment.saveActivity()` para passar ambos ao repositório e preservar o estado escolhido se o salvamento local precisar ser tentado novamente.
- [ ] **Step 5: Atualizar `ActivitySaveFlowTest`** para afirmar Bike inicial, retorno da categoria alterada e descarte sem persistência; confirmar que atividade salva ainda abre seus detalhes.
- [ ] **Step 6: Rodar `make test-unit`, os testes Android focados de salvamento e `make build`**; confirmar que GPX continua legível e os resumos antigos continuam listáveis.
- [ ] **Step 7: Commitar classificação e salvamento local de tipo** antes de conectar o botão Strava.

### Task 4: Conexão Strava e configuração em Conta e conexões

**Files:**
- Create: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/strava/StravaConnectionRepository.kt`, `StravaConnectionState.kt`
- Create: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/strava/StravaOAuthCallbackActivity.kt`, `mobile/src/test/java/br/com/helpdev/velocimetroalerta/strava/StravaConnectionRepositoryTest.kt`
- Create: `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/AccountConnectionsTest.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/AccountConnectionsActivity.kt`, `mobile/src/main/res/layout/activity_account_connections.xml`, `mobile/src/main/AndroidManifest.xml`, `functions/src/stravaOAuth.ts`, `firebase.json`
- Create: `hosting/.well-known/assetlinks.json.example` and callback deployment instructions; inject actual signing fingerprints through environment-specific deployment configuration

**Interfaces:**
- `StravaConnectionState` distingue desconectado, conectando, conectado e reconexão necessária.
- `StravaConnectionRepository.connect()` exige sessão Firebase atual e inicia `beginStravaConnect()`; depois do App Link, consulta o backend e atualiza a conta, sem credenciais no link.
- Callback usa domínio HTTPS configurado para Hosting/Functions, `state` de uso único e somente escopo `activity:write`; conectar não lê a biblioteca nem inicia upload.
- `AccountConnectionsActivity` mostra conectar/desconectar e o toggle da `SettingsSyncRepository`; toggle fica imediatamente abaixo da linha Strava, visível, desligado e desativado sem vínculo.

- [ ] **Step 1: Adicionar testes de estado** `guestMustAuthenticateBeforeConnect`, `cancelledLoginDoesNotStartOAuth`, `oauthCallbackRefreshesConnectionState`, `deniedScopeLeavesAccountDisconnected` e `disconnectRevokesServerLink`.
- [ ] **Step 2: Adicionar teste Espresso** para ação do drawer abrir Conta e conexões, toggle aparecer abaixo de Strava, estar desligado e indisponível quando desconectado; rodar testes focados antes da implementação.
- [ ] **Step 3: Implementar repositório e callback HTTPS**; validar assinatura/expiração/reuso do state, escopo exato `activity:write`, troca/rotação do token no servidor e associação ao UID que iniciou OAuth.
- [ ] **Step 4: Registrar App Link HTTPS verificado no manifest** e tratar sucesso, cancelamento, state inválido e troca de conta sem iniciar upload automaticamente; reconexão tampouco varre atividades.
- [ ] **Step 5: Estender Conta e conexões** com estados Strava, desconexão/revogação e toggle; sincronização automática continua `false` até opt-in e o texto avisa que somente novas atividades serão enviadas.
- [ ] **Step 6: Rodar testes de repositório/UI e `make build`**; fazer OAuth manual em Firebase/Strava de desenvolvimento com API 24 e 36 depois de configurar domínio, fingerprints e redirect URI.
- [ ] **Step 7: Commitar conexão OAuth e tela de conta** depois de confirmar que o link não contém tokens e callback rejeita state antigo.

### Task 5: Coordenador de upload manual e automático

**Files:**
- Create: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/strava/StravaSyncStore.kt`, `StravaUploadClient.kt`, `StravaUploadWorker.kt`, `StravaUploadScheduler.kt`, `StravaUploadCoordinator.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/MainActivityFragment.kt`, `ActivityDetailActivity.kt`, `mobile/src/main/res/layout/activity_activity_detail.xml`, localized strings
- Create: `StravaSyncStoreTest.kt`, `StravaUploadCoordinatorTest.kt`, `StravaUploadWorkerTest.kt` under `mobile/src/test/java/br/com/helpdev/velocimetroalerta/strava/`
- Modify: `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/ActivityDetailTest.kt`; add WorkManager test dependencies in `gradle/libs.versions.toml` and `mobile/build.gradle`

**Interfaces:**
- `StravaUploadCoordinator.enqueueManual(activityId)` enfileira apenas a atividade aberta; login/OAuth preserva esse ID até retorno ou cancelamento.
- O ID da ação manual pendente fica salvo por UID durante login/OAuth e é consumido ou apagado ao retomar/cancelar; recriar o processo não pode redirecionar o envio a outra atividade.
- `StravaUploadCoordinator.onActivitySaved(summary)` enfileira somente após `ActivityRepository.save()` retornar sucesso e se UID, Strava e `stravaAutoSyncEnabled` estiverem ativos e o tipo tiver modalidade.
- Ambos usam trabalho único `strava-upload-$uid-$activityId`; `KEEP` impede disputa entre clique manual e auto. Status durável fica separado de `summary.json`, indexado por UID + ID, em arquivo atômico app-private.
- Estados de upload: `not_uploaded`, `queued`, `uploading`, `processing`, `synced`, `manually_confirmed`, `retryable_error` e `needs_action`.
- `StravaSyncStore` persiste `uid`, `activityId`, origem (`manual`/`automatic`), status, `uploadId` e `stravaActivityId`; também fornece `savePendingManualAction(uid, activityId)`, `consumePendingManualAction(uid)` e `clearPendingManualAction(uid)` para sobreviver ao login/OAuth e à recriação do processo.
- Worker valida que a sessão Auth atual continua sendo a UID do trabalho antes de solicitar token; UID diferente pausa/encerra o trabalho sem reutilizar a conta atual.
- O cliente Android lê o GPX local e envia diretamente à API Strava. Access token vive só em memória. `external_id` estável auxilia reconciliação, mas não é tratado como garantia de idempotência.

- [ ] **Step 1: Escrever testes do store/coordenador** `manualEnqueuesOnlyRequestedActivity`, `autoEnqueuesOnlyAfterSuccessfulSaveAndOptIn`, `neverBackfillsExistingActivities`, `vehicleIsNeverEnqueued`, `legacyActivityRequiresTypeBeforeEnqueue`, `manualActionSurvivesProcessRecreationForSameUid`, `manualAndAutoShareUniqueWork`, `uidSwitchDoesNotReuseWork`, `turningAutoSyncOffCancelsQueuedAutoWork`, `turningAutoSyncOffKeepsRunningUpload` e `deletingActivityCancelsQueuedUpload`.
- [ ] **Step 2: Escrever testes do worker/client fake** `uploadRequestIncludesNameAndStableExternalId`, `uploadSuccessWaitsForActivityId`, `pollIntervalIsAtLeastOneSecond`, `ambiguousPostRequiresAction`, `http401RequiresReconnect` e `http429HonorsRetryAfter`; rodar testes focados e confirmar falha inicial.
- [ ] **Step 3: Implementar `StravaSyncStore`** com escrita atômica app-private para estados por UID/atividade; manter `summary.json` e GPX imutáveis quando o status muda.
- [ ] **Step 4: Implementar scheduler/coordinator com WorkManager**; trabalho manual carrega apenas o activityId pedido; gatilho automático ocorre após persistência local bem-sucedida e usa a mesma chave única.
- [ ] **Step 5: Implementar cliente/worker Strava**; pedir access token autenticado em memória, enviar GPX, nome, `sport_type`, `data_type=gpx` e `external_id` estável apenas ao Strava; acompanhar `GET /uploads/{id}` sem polling abaixo de 1 segundo e só marcar sucesso após obter `activity_id`.
- [ ] **Step 6: Implementar recuperação e erros**; só repetir uploads quando é certo que o POST não chegou; POST sem resposta e sem `upload_id` muda para `needs_action` e espera decisão manual; desconexão/revogação pede login/conexão novamente.
- [ ] **Step 7: Integrar e testar estados nos detalhes**; `uploadProgressKeepsGpxAvailable`, `syncedUploadDisablesSendButton` e `manualConfirmationStaysOnDetailsAndDisablesSendButton` confirmam o indicador inline, CTA sincronizada e confirmação manual sem navegação/tela “Concluído”.
- [ ] **Step 8: Integrar confirmação incerta nos mesmos detalhes**; **Já aparece no Strava** muda a CTA para **Presença confirmada manualmente**; **Tentar novamente** avisa sobre duplicata e só então agenda outra tentativa. Não criar fluxo/tela separada de verificação ou conclusão.
- [ ] **Step 9: Ligar `onActivitySaved` após `ActivityRepository.save()`**; testar que descarte, erro de salvamento, toggle desligado, Vehicle e conexão/login não disparam upload; atividades antigas pedem tipo se legado e continuam manuais.
- [ ] **Step 10: Rodar testes unitários focados, `make test-unit`, `make build` e `make test-e2e` em API 24/36**; em conta de desenvolvimento testar um envio manual e um novo salvamento após opt-in, verificando ausência de duplicata.
- [ ] **Step 11: Commitar envio individual, estado e gatilho automático** depois de confirmar os critérios de aceite dos estados.

### Task 6: Privacidade, operação e preparação para lançamento

**Files:**
- Modify: `README.md`, `mobile/src/main/res/values/strings.xml`, `mobile/src/main/res/values-pt/strings.xml`, `.github/workflows/android.yml`, `Makefile`
- Create: `docs/strava-firebase-setup.md`, `functions/README.md` with emulator/deployment commands and examples, without project IDs, secrets, GPX or coordinates
- External release dependency: policy source is served from `helpdev.com.br` via `privacy_policy_url`; update its owning repository and the Play Console Data Safety declaration before public release, tracked separately from this Android worktree

- [ ] **Step 1: Documentar setup de dev/teste e produção** para Firebase Auth, Firestore, Functions, Secret Manager, OAuth Strava, domínio HTTPS/App Links, fingerprints e configuração CI; exemplos não contêm segredos/IDs de produção.
- [ ] **Step 2: Atualizar disclosure e strings EN/PT**; login Google é opcional para uso local, GPX vai direto do aparelho ao Strava, auto é opt-in para futuras atividades e nunca faz backfill.
- [ ] **Step 3: Criar checklist de privacidade e telemetria**; logs não podem incluir GPX, coordenadas, access/refresh tokens, client secret, OAuth code ou state.
- [ ] **Step 4: Registrar a atualização da política no repositório proprietário do site e Data Safety no Play Console** como gate de release; não alterar outro repositório como parte desta implementação Android sem escopo confirmado.
- [ ] **Step 5: Atualizar CI/Makefile** para executar `make test-firebase` e manter builds sem secrets de produção; `make verify` inclui testes Android existentes e emuladores Firebase documentados.
- [ ] **Step 6: Rodar `make test`, `make test-firebase`, `make build`, `make test-e2e` e `make verify`** antes de release; executar OAuth/upload real somente em projeto e conta Strava de desenvolvimento.
- [ ] **Step 7: Aprovar publicação interna** após política/Data Safety, custos/cotas de Functions e capacidade/revisão de atletas Strava estarem confirmados.
- [ ] **Step 8: Commitar documentação, avisos e CI**; deixar IDs e material de assinatura em configuração local/segredos do CI.

## Critérios de aceite de ponta a ponta

- [ ] Convidado consegue salvar, consultar e exportar GPX sem Google; sem login não começa OAuth Strava nem sincronização de preferências.
- [ ] Login Google restaura preferências da conta; trocar de UID não pode reutilizar conexão Strava nem opção automática de outra conta.
- [ ] Novas atividades exigem tipo; resumos antigos continuam legíveis e pedem um tipo antes do primeiro envio manual.
- [ ] Envio manual transmite apenas a atividade cujo detalhe foi acionado; login ou conexão, por si só, nunca inicia upload.
- [ ] Depois da confirmação final do Strava, os detalhes mostram “strava · Atividade já sincronizada” em botão inativo, sem abrir uma tela de conclusão. Ao tocar em “Já aparece no Strava”, o botão muda para “Presença confirmada manualmente” nos próprios detalhes e evita novo envio acidental.
- [ ] Durante upload manual ou automático, a tela 2 permanece aberta e exibe “Sincronizando com Strava…” no lugar do botão Strava; “Exportar GPX” continua disponível.
- [ ] Em um resultado incerto, a tela 2 mostra aviso, confirmação manual ou tentativa novamente, com o alerta de possível duplicata; não aparece outra tela.
- [ ] O toggle automático fica logo abaixo da linha do Strava em Conta e conexões, começa desligado e sincroniza por UID Google; sem Strava, fica visível e desativado. Ligá-lo envia somente atividades elegíveis salvas depois; não faz backfill.
- [ ] Com modo automático ativo, atividade elegível só entra na fila após persistência local e detalhes mostram estado do envio. Descarte/falha no salvamento, veículo e tipo sem mapeamento não entram na fila.
- [ ] Desligar o modo impede novas filas automáticas, cancela as ainda não iniciadas e permite terminar upload já iniciado; envio manual de atividades antigas continua disponível.
- [ ] Unicidade do WorkManager impede upload manual/automático duplicado para a mesma UID + `activityId`; POST ambíguo exige revisão em vez de retry cego.
- [ ] Regras Firestore isolam preferências por UID e negam tokens Strava aos clientes; GPX nunca passa pelo Firebase.
- [ ] Testes unitários/emuladores focados, `make test`, `make build`, `make test-e2e` e `make verify` passam antes do release; OAuth/upload reais usam conta de teste.

## Dependências e riscos de execução

- Requer projetos Firebase de desenvolvimento/produção, aplicativo Strava aprovado, domínio HTTPS para callback, Android App Links verificados e fingerprints de assinatura release. A revogação usa o endpoint Strava OAuth de revoke com o refresh token, `token_type_hint=refresh_token` e autenticação Basic do aplicativo.
- Cloud Functions/Secret Manager podem exigir faturamento Firebase; capacidade/revisão de atletas Strava precisa ser confirmada antes da publicação ampla.
- Atividades e histórico de envio ficam no aparelho. Toggle e preferências sincronizam pela conta, mas arquivos GPX não migram entre dispositivos.
- Envio automático só se aplica a atividades salvas após o opt-in. Ativar a opção nunca envia silenciosamente a biblioteca local existente.
- A política de privacidade reside fora deste repositório e o Data Safety é cadastrado no Play Console; ambos são gates de release, não pré-requisitos para iniciar a implementação local.
