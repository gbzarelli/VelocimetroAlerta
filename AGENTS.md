# Project-specific agent guidance

- Read the root `README.md` for Android setup, GPX simulator usage, and the complete Makefile command list (`make help`).
- Prefer the Makefile for routine project workflows: `make build`, `make run`, `make test`, `make test-e2e`, and `make verify`.
- `make test-e2e` starts or waits for the API 24 and API 36 emulator AVDs documented in the Makefile.
- GPX files under `tools/gpx/` are local test data and ignored by Git because tracks may reveal private location history. Do not stage or share them unless the user explicitly requests it.
- Keep app changes in the existing Android/Kotlin and XML conventions. Run focused tests first, then the relevant Makefile test target.

## Firebase and signing configuration

- The Firebase Android client configuration for this app is stored locally in the sibling `android-keys` repository at `/Volumes/gbz-ext/repositories/helpdev/android-keys/velocimetro-alerta-v2/firebase/google-services.json`.
- Copy that file to `mobile/google-services.json` in the active checkout before running Firebase-backed app flows. The app-level file is gitignored and must not be committed.
- The upload keystore is `/Volumes/gbz-ext/repositories/helpdev/android-keys/velocimetro_alerta_v2_upload.jks`. Public Google Play signing certificates and their fingerprints are documented in `/Volumes/gbz-ext/repositories/helpdev/android-keys/velocimetro-alerta-v2/google-play-certificates/README.md`.
- Never print, copy into the app repository, or commit keystore passwords, private keys, Strava secrets, or Firebase local configuration. Store the Strava client secret in Firebase Secret Manager.
