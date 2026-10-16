# Minhas atividades — validação

## Validação automatizada

Executado em 2026-10-16, no branch `feat/minhas-atividades`:

- `./gradlew :mobile:assembleDebug :mobile:testDebugUnitTest --rerun-tasks` — sucesso; APK debug montado. 48 testes JVM reportados, 47 executados e aprovados, 1 ignorado por configuração existente.
- `python3 tools/run-hermetic-android-tests.py --sdk 24,36 --device emulator-5554 --device emulator-5564` — sucesso; 66/66 testes instrumentados passaram nos emuladores API 24 e API 36. Casos incompatíveis com o nível de API são ignorados pelo runner.
- Os testes cobrem confirmação/descartar do nome sugerido, feed vazio e com atividade, detalhe e métricas opcionais, mapa histórico sem iniciar o rastreamento, exportação GPX byte a byte e cancelamento, além das regressões existentes de GPS, permissões, mapa ao vivo e leitura/escrita GPX.
- `git diff --check` — sucesso.

## Checklist de conferência manual

Os fluxos automatizados acima foram exercitados nos emuladores. Para uma conferência visual em aparelho e integrações de terceiros, ainda vale verificar:

- Confirmar que o nome sugerido respeita idioma e formato de data do aparelho; editar, concluir e descartar uma atividade.
- Fechar e reabrir o app para verificar a atividade persistida no feed.
- Conferir o esboço e a rota completa com uma atividade longa, incluindo zoom e enquadramento do mapa.
- Conferir a apresentação de atividades com e sem amostras de cada sensor opcional.
- Escolher diferentes destinos do seletor de documentos, incluindo cancelar e um destino sem permissão de escrita.
- Durante uma gravação ativa e pausada, abrir uma atividade histórica e confirmar que o rastreamento atual não muda.
