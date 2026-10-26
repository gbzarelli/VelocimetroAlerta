![Issues](https://img.shields.io/github/issues/gbzarelli/VelocimetroAlerta.svg) ![Forks](https://img.shields.io/github/forks/gbzarelli/VelocimetroAlerta.svg) ![Stars](https://img.shields.io/github/stars/gbzarelli/VelocimetroAlerta.svg) ![Release Version](https://img.shields.io/github/release/gbzarelli/VelocimetroAlerta.svg)

# Speedometer Alert - Velocimetro Alerta

<img src=imagens/Screenshot_20180929-100657.png width=170> . <img src=imagens/Screenshot_20180930-171106.png width=170> . <img src=imagens/Screenshot_20180930-180614.png width=170>

<img src=imagens/Screenshot_20180929-100722.png width=170> . <img src=imagens/Screenshot_20180929-100727.png width=170> . <img src=imagens/Screenshot_20180929-100735.png width=170>


## Product overview

### English

**Hear your speed updates and keep your eyes on the road.** Speedometer Alert is a GPS speedometer for cycling and other activities where watching a phone is inconvenient. During an activity, it can speak your selected updates at an interval based on minutes or kilometers. Choose current, average, or maximum speed, distance, and elapsed time; vibration is optional.

The app also shows live GPS speed, distance, elapsed time, and an estimate of elevation gain and loss. Automatic pause helps handle stops and low-speed sections. Save activities on your device, review routes on a map, edit an activity name, delete saved activities, or export a GPX file for use with services such as Strava.

Compatible Bluetooth sensors can provide heart-rate and cadence data. An optional Arduino-based Vel Alert module can add cadence, temperature, and humidity readings; see [VelocimetroAlertaBLModule](https://github.com/helpdeveloper/VelocimetroAlertaBLModule).

The app displays banner ads at the bottom of My activities, saved activity details, and Settings.

### Português (Brasil)

**Ouça os avisos de velocidade e mantenha os olhos no caminho.** O Velocímetro Alerta é um velocímetro por GPS para ciclismo e outras atividades em que não é prático olhar para o celular. Durante a atividade, ele fala as informações escolhidas em intervalos por minutos ou quilômetros. Selecione velocidade atual, média ou máxima, distância e tempo decorrido; a vibração é opcional.

O app também mostra velocidade pelo GPS, distância, tempo decorrido e uma estimativa de ganho e perda de elevação. A pausa automática ajuda a lidar com paradas e trechos de baixa velocidade. Salve as atividades no dispositivo, consulte os percursos no mapa, edite o nome, exclua atividades salvas ou exporte um arquivo GPX para usar em serviços como o Strava.

Sensores Bluetooth compatíveis podem fornecer dados de frequência cardíaca e cadência. Um módulo Vel Alert opcional, baseado em Arduino, pode adicionar cadência, temperatura e umidade; veja o projeto [VelocimetroAlertaBLModule](https://github.com/helpdeveloper/VelocimetroAlertaBLModule).

O app exibe anúncios em banner na parte inferior de Minhas atividades, dos detalhes de atividades salvas e de Configurações.

## Installation

Use the Play Store: 

[Speedometer Alert](https://play.google.com/store/apps/details?id=br.com.helpdev.velocimetroalerta.v2)

## Maps SDK setup (development)

The live activity map uses the Google Maps SDK for Android. Enable **Maps SDK for Android** in a Google Cloud project and configure billing for that project. Create a development API key restricted to the debug application ID and debug signing certificate, then put it in the ignored root-level `secrets.properties` file:

```properties
MAPS_API_KEY=your-development-key
```

The tracked `local.defaults.properties` contains only a placeholder so builds can run without a key; map tiles require a valid key. Use a separate release key restricted to application ID `br.com.helpdev.velocimetroalerta.v2` and the release signing certificate SHA-1. Never commit keys. Review the Maps SDK's current Google Play Data Safety disclosure and update the app's Data Safety declaration and privacy policy before releasing a build with Maps.

## Local Android development

Run `make help` for the available commands. Common workflows:

```sh
make build             # assemble the debug APK
make run               # open/wait for emulator-5564, install, and launch the app
make test              # GPX simulator tests and JVM unit tests
make test-elevation    # elevation estimator and pause policy tests
make test-elevation-replay # replay the three local Strava GPX files
make test-e2e          # full instrumentation suite on API 24 and API 36
make verify            # unit tests plus emulator instrumentation
```

GitHub Actions runs `make test` and replays the three tracked elevation GPX fixtures on every pull request. It also runs the hermetic instrumentation suite on API 24 and API 36 emulators.

### Simulate a GPX route

The simulator injects track coordinates and altitude through the Android Emulator GPS console. Put a GPX file at `tools/gpx/Pedalada_vespertina.gpx` (or set `GPX=/path/to/ride.gpx`). GPX files in `tools/gpx/` are ignored by Git because they can contain private location history; the local `Pedalada_vespertina.gpx` is copied from `~/Downloads` when available.

```sh
make gpx-realtime   # recorded timestamps; repeats until Ctrl+C
make gpx-fast       # 12x faster by default; repeats until Ctrl+C
make gpx-fast GPX_FAST_FACTOR=20
```

Both targets build, install, and open the app on `emulator-5564` before sending locations. Change `DEVICE`, `PORT`, or `AVD` to use another emulator. During accelerated playback, the app observes the corresponding faster apparent speed. Start or pause tracking in the app as usual; stop the simulator with Ctrl+C.

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

Please make sure to update tests as appropriate.

## License
[Apache-2.0](https://choosealicense.com/licenses/apache-2.0/)
