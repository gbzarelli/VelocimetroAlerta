# Project-specific agent guidance

- Read the root `README.md` for Android setup, GPX simulator usage, and the complete Makefile command list (`make help`).
- Prefer the Makefile for routine project workflows: `make build`, `make run`, `make test`, `make test-e2e`, and `make verify`.
- `make test-e2e` starts or waits for the API 24 and API 36 emulator AVDs documented in the Makefile.
- GPX files under `tools/gpx/` are local test data and ignored by Git because tracks may reveal private location history. Do not stage or share them unless the user explicitly requests it.
- Keep app changes in the existing Android/Kotlin and XML conventions. Run focused tests first, then the relevant Makefile test target.
