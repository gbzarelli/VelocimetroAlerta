# Configuração de Firebase e Strava

Este guia prepara ambientes de desenvolvimento e produção sem incluir IDs de projeto, chaves, tokens ou dados GPX no repositório. As funcionalidades locais continuam disponíveis sem Firebase. Login, sincronização de preferências e Strava só funcionam quando o app e as Cloud Functions estão configurados.

## Firebase Android

1. Crie um projeto Firebase para desenvolvimento e outro para produção.
2. Registre o aplicativo Android com o application ID `br.com.helpdev.velocimetroalerta.v2`.
3. Adicione os fingerprints SHA-1 e SHA-256 de cada certificado debug/release usado. Para a distribuição Play, inclua também o certificado de assinatura do Google Play.
4. Ative Google como provedor em Firebase Authentication e habilite o Firestore.
5. Use os arquivos locais guardados em `/Volumes/gbz-ext/repositories/helpdev/android-keys/velocimetro-alerta-v2/firebase/`: copie `google-services.json` para `mobile/google-services.json` (release) e `google-services-debug.json` para `mobile/src/debug/google-services.json` (debug). Ambos os destinos são ignorados pelo Git. O arquivo de debug usa a chave Android restrita ao certificado debug; o de release usa a chave restrita ao certificado Play.
6. Cadastre o app no Firebase App Check. Use o token debug somente em desenvolvimento; registre o certificado/app com Play Integrity para release.

O Gradle só aplica o plugin Google Services quando encontra `mobile/google-services.json`. Sem esse arquivo, o build continua funcionando, mas o app não inicializa as funções de conta.

## Firebase CLI e Cloud Functions

Instale Node.js 22 e Firebase CLI. Copie `.firebaserc.example` para `.firebaserc` e associe seu projeto de desenvolvimento com `firebase use --add`. O arquivo `.firebaserc` é local e não deve conter um ID privado em commits.

Configure os parâmetros não secretos no arquivo de ambiente da pasta `functions`:

```sh
cp functions/.env.example functions/.env.PROJECT_ID
```

Substitua `PROJECT_ID` pelo ID local do projeto e configure `STRAVA_CLIENT_ID` e `STRAVA_REDIRECT_URI`. Cadastre a URI HTTPS exata na configuração da aplicação Strava. O segredo do cliente fica no Secret Manager:

```sh
firebase functions:secrets:set STRAVA_CLIENT_SECRET --project PROJECT_ID
```

Compile as Functions e publique regras e funções:

```sh
make functions-build
firebase deploy --only firestore:rules,functions --project PROJECT_ID
```

O Firestore e as Cloud Functions usam a região `southamerica-east1` (São Paulo); as funções exigem Firebase Authentication e App Check. O Firestore armazena as preferências permitidas em `users/{uid}/settings/current`; tokens Strava ficam em `users/{uid}/private/stravaConnection`, inacessíveis pelas regras do cliente. Configure a política TTL do Firestore para remover documentos expirados da coleção `stravaOAuthStates` usando o campo `expiresAt`.

Para subir os emuladores Auth, Firestore e Functions:

```sh
make functions-emulate
```

O alvo compila e inicia os emuladores. Para o emulador de Functions, crie `functions/.secret.local` localmente com `STRAVA_CLIENT_SECRET=...`; o arquivo é ignorado pelo Git. O cliente Android desta implementação aponta para o projeto Firebase configurado; para integrar o app aos emuladores, configure endpoints locais em `FirebaseServices` antes de usar esse fluxo em um dispositivo/emulador. Não use projeto ou credenciais de produção para desenvolvimento.

## Strava OAuth e Android App Links

O callback registrado no backend é:

```text
https://helpdev.com.br/apps/velocimetro-alerta/strava/callback
```

Use exatamente a mesma URI no arquivo de ambiente e no cadastro OAuth da aplicação Strava. O fluxo requer o escopo `activity:write`; o app não solicita leitura da biblioteca Strava.

O domínio precisa servir `/.well-known/assetlinks.json` com o package `br.com.helpdev.velocimetroalerta.v2` e os fingerprints SHA-256 dos certificados debug/release pertinentes. O callback HTTPS também deve permanecer válido como fallback web para quando o app não estiver instalado. A implementação e publicação deste arquivo ficam no repositório do site HelpDev; este repositório não contém fingerprints de assinatura.

O `STRAVA_CLIENT_SECRET` e os refresh tokens nunca podem ser incluídos no APK. O app guarda o access token somente em memória durante o envio. O arquivo GPX e as coordenadas seguem diretamente do aparelho para a API de upload do Strava; não passam pelo Firebase.

## Verificações antes do lançamento

- Validar login Google e App Check com projeto Firebase de desenvolvimento.
- Confirmar o callback App Link no Android com certificado instalado e endpoint HTTPS publicado.
- Fazer upload manual e automático com conta de teste Strava, verificando o status até `activity_id`.
- Atualizar a política de privacidade publicada e a declaração Data Safety no Google Play para Google/Firebase, preferências sincronizadas, vínculo Strava e envio de GPX opcional/automático.
- Confirmar custos/cotas das Cloud Functions, Secret Manager e capacidade de atletas da aplicação Strava.
{
  "functions": [
    {
      "source": "functions",
      "codebase": "default",
      "runtime": "nodejs22",
      "ignore": ["node_modules", ".git", "firebase-debug.log", "firebase-debug.*.log"]
    }
  ],
  "firestore": {
    "rules": "firestore.rules"
  },
  "emulators": {
    "auth": { "port": 9099 },
    "functions": { "port": 5001 },
    "firestore": { "port": 8080 },
    "ui": { "enabled": true, "port": 4000 }
  }
}
