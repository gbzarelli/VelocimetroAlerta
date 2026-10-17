![Issues](https://img.shields.io/github/issues/gbzarelli/VelocimetroAlerta.svg) ![Forks](https://img.shields.io/github/forks/gbzarelli/VelocimetroAlerta.svg) ![Stars](https://img.shields.io/github/stars/gbzarelli/VelocimetroAlerta.svg) ![Release Version](https://img.shields.io/github/release/gbzarelli/VelocimetroAlerta.svg)

# Speedometer Alert - Velocimetro Alerta

<img src=imagens/Screenshot_20180929-100657.png width=170> . <img src=imagens/Screenshot_20180930-171106.png width=170> . <img src=imagens/Screenshot_20180930-180614.png width=170>

<img src=imagens/Screenshot_20180929-100722.png width=170> . <img src=imagens/Screenshot_20180929-100727.png width=170> . <img src=imagens/Screenshot_20180929-100735.png width=170>


[en] The Speedometer Alert is an application that indicates precisely through GPS positions the current speed, average speed, maximum speed, elapsed time, altimetry, ALTIMETRY GAIN/LOSS IN REAL TIME and distance traveled of a started activity, is able to predict a GPS interruption or stop activity if you do not have speed and stop activity automatically, thus not losing important information such as time and average speed.
Its most important function is the ALERT that can be configured to SPEAK from time to time or kilometers in kilometers the information you want with the interval you want, so if you have doing some activity that you can not be looking at the device (such as cycling ) he will tell you the information you want.
You can extract the GPX file from your activity and upload it to applications known as Strava.
We have an independent design developed on Arduino to monitor cadence, temperature and humidity. See: https://github.com/helpdeveloper/VelocimetroAlertaBLModule

[pt-br] O Velocímetro Alerta é um aplicativo que indica precisamente através de posições GPS a velocidade atual, velocidade média, velocidade máxima, tempo decorrido, altimetria, GANHO DE ALTIMETRIA EM TEMPO REAL (calculado a cada quilômetro) e distância percorrida de uma atividade iniciada, é capaz de prever uma interrupção de GPS ou parada de atividade caso não tenha velocidade e parar a atividade automaticamente, assim não perdendo informações importantes como tempo e velocidade média.
Sua função mais importante é o ALERTA que pode ser configurado para FALAR de tempos em tempos ou quilometros em quilometros a informação que desejar com o intervalo que desejar, assim se tiver fazendo alguma atividade que não seja possível estar olhando para o dispositivo (como o ciclismo) ele falara para você as informações que desejar.
Com o Velocímetro Alerta é possível se conectar com sua cinta cardíaca bluetooth para monitorar suas zonas, e também configurar suas zonas de acordo com seu perfil.
É possível extrair o arquivo GPX de sua atividade e subir para aplicativos conhecidos como o Strava.
Temos um projeto independente desenvolvido no Arduino para monitorar a cadência, temperatura e humidade. Veja em: https://github.com/helpdeveloper/VelocimetroAlertaBLModule

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
