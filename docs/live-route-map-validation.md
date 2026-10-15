# Live route map validation (2026-10-15)

## Build and automated coverage

- App build: debug variant; package `br.com.helpdev.velocimetroalerta.v2`.
- Emulator used for end-to-end instrumentation: Google Play emulator, API 36 (`emulator-5558`). An API 24 emulator is also available (`emulator-5562`).
- Maps key kind: tracked `DEFAULT_API_KEY` placeholder only. No usable debug key was configured. No key, certificate fingerprint, or credential material is recorded here.
- GPX input: user-provided `Pedalada_vespertina.gpx` from Downloads, read in place through `ROUTE_GPX_PATH`; 4,248 track points. The GPX file and coordinates were not copied into the repository or logs.
- JVM unit tests and debug build: `ROUTE_GPX_PATH=/Users/gbzarelli/Downloads/Pedalada_vespertina.gpx ./gradlew :mobile:assembleDebug :mobile:testDebugUnitTest --rerun-tasks` — passed (43 tasks executed), including route ordering, snapshot/revision, rendering-state, and GPX-to-renderer validation.
- API 24 and API 36 map lifecycle instrumentation: map opens without starting tracking, Back finishes only the map, and recreation does not create or stop tracking — 4/4 passed.
- API 24 and API 36 navigation instrumentation: map button visibility, running/paused navigation, service identity, route count, and chronometer preservation — 8/8 passed.
- Hermetic instrumentation invocations used `python3 tools/run-hermetic-android-tests.py --sdk 24,36 --device emulator-5562 --device emulator-5558 --test RouteMapScreenSmokeTest#mapOpensWithoutStartingTrackingAndBackFinishesOnlyMapActivity --test RouteMapScreenSmokeTest#recreatingMapDoesNotCreateOrStopTracking` (4/4 passed) and the same API/device selectors with the four `RouteMapNavigationTest` methods (8/8 passed).
- GPX rendering check: all 4,248 source points were parsed read-only and fed in order into the provider-neutral map state/fake renderer; endpoint/marker matched the final GPX point. This verifies route data flow and ordering, not Google basemap tiles.

## Device/manual outcomes and limits

- The emulator's Google Maps SDK rejected the placeholder key as unauthorized. Therefore map tiles, visual alignment over the basemap, pan/recenter with a valid Google renderer, and actual GPS movement while watching tiles were not verified.
- On API 24, Android reports a bound-but-not-started `SpeedometerService` record (`started=false`, `pid=0`) while the map requests a bind with flags `0`. The instrumentation check now distinguishes this record from an actually started service/process; the API 24 and API 36 smoke tests pass with that assertion.
- The tests verify the recording service remains active when opening/closing the map. No physical API 24+ device was available; walking/riding, automatic pause from real GPS, and physical-device rotation were not exercised.
- Network-offline behavior and the timed map-load error presentation were not directly exercised. They remain release checks. The map is designed to bind to the existing service only, and map rendering is not the GPS or GPX owner.
- No debug/release Maps key was supplied. Before a visual device test, configure a distinct restricted debug key (package plus debug signing certificate) outside version control; production must use the release certificate restriction. Never include either key or certificate fingerprint in validation artifacts.

## Dependency, privacy, and release disclosures

- Maps SDK `play-services-maps:20.0.0` is added; its Android API floor is 23 and the app's `minSdk` is 24. `INTERNET` is declared to load map content. Billing, quotas, current terms, and key restrictions must be confirmed in the production Google Cloud project.
- Google's current Maps SDK Data Safety guidance documents automatically collected request/device metadata, SDK crash metrics/stack traces, IP address, a Maps SDK pseudonymous identifier, and possible pan/zoom interaction collection when camera APIs are used. See [Google Maps SDK Data Safety guidance](https://developers.google.com/maps/documentation/android-sdk/play-data-disclosure).
- The public privacy-policy source at `helpdeveloper/helpdev.com.br` did not yet describe this SDK collection. A proposed PT/EN source update was prepared, but GitHub branch creation was denied by the connected integration (HTTP 403). The source and public policy therefore remain unchanged; request repository write access or apply the policy update through the site's normal review/deployment process.
- Play Console was not edited. The existing versionCode 3 production submission was in review before the Maps SDK was added. Before a map-enabled release: update Data Safety, publish the policy revision, increase `versionCode`, and submit a new bundle. Do not mark the disclosure complete until verified in the Console and on the public policy URL.

## Required release follow-up

1. Obtain/configure a restricted debug key and repeat the route/map smoke test with working tiles and network disabled.
2. Exercise a physical API 24+ device through empty route, multiple points, follow, pan/recenter, pause/resume, GPS degradation, rotation, Up, and Back while confirming the foreground notification and GPX capture remain intact.
3. Obtain access to the public policy repository, merge/deploy the bilingual Maps disclosure, and verify the canonical URL.
4. Update and save Play Data Safety for the map-enabled release, then build/test a higher-versionCode bundle before submission.
