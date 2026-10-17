# Validação offline da issue #1

Esta pasta contém o manifesto e o comparador das três atividades. Os arquivos privados ficam em `tools/gpx/issue-1/<id>.gpx`, ignorados pelo Git. O comparador só imprime agregados, sem coordenadas ou horários.

```sh
python3 -m tools.elevation_validation.compare --format markdown
python3 -m tools.elevation_validation.compare --format json
python3 -m unittest tools.test_analyze_elevation_gpx tools.elevation_validation.test_compare
```

O manifesto `activities.json` registra números vistos nas páginas do Strava em 16/10/2026. O script usa o perfil `<ele>` exportado, mede distância por Haversine, reproduz `GpxMath.calculateGain` com uma precisão horizontal **constante presumida** (3, 5, 8, 10, 12 ou 15 m) e simula o recálculo sequencial de 1 km do serviço. A sonda de mediana/histerese é uma hipótese exploratória, não código do app. As saídas não são uma medição de erro absoluto: o GPX não traz precisão por ponto nem pausas, e o ganho do Strava é processado pela plataforma.

Ao implementar o novo estimador em Kotlin, acrescentar um replay que execute **a mesma implementação usada pelo app**, com fixtures sintéticas versionáveis e estes três GPX locais como comparação secundária. O gate de acurácia ainda exige gravações brutas independentes, incluindo percurso plano ruidoso e montanhoso com precisão vertical, além de teste em API 24/36.
