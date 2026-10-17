# GPX locais da issue #1

Esta pasta reúne, somente nesta worktree, as exportações privadas `19463623301.gpx`, `20049386131.gpx` e `20338280241.gpx`. Os arquivos `.gpx` são ignorados pelo Git porque contêm localização e horários.

O manifesto público, o comparador e os testes estão em [`../../elevation_validation/`](../../elevation_validation/README.md). Da raiz do projeto, execute `make compare-elevation` para ver apenas números agregados e `make test-elevation` para validar o comparador sem precisar das exportações.
