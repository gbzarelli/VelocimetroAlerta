# Live Route Map Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show an active activity's accepted GPS trace and current position on a live map while recording continues, with safe return navigation to the speed screen.

**Architecture:** Add a provider-neutral `ActiveRoute` publisher alongside the existing GPX recording in `SpeedometerService`; it supplies an immutable initial snapshot and ordered point additions to a short-lived map observer. `RouteMapActivity` binds only to an existing service, and `RouteMapFragment` renders a single Google Maps polyline/marker on the main thread without owning GPS or ride state.

**Tech Stack:** Kotlin, AndroidX/ViewBinding, AGP 9.4.1, API 24+, Maps SDK for Android, Maps Secrets Gradle Plugin, JUnit, AndroidX instrumentation tests.

**Spec:** `docs/superpowers/specs/2026-10-15-live-route-map-design.md`

## Global Constraints

- Implement against `master` at `bc81747` or a descendant: its package is `br.com.helpdev.velocimetroalerta.v2`, `minSdk` is 24, and `targetSdk` is 36.
- Use the existing `SpeedometerService` as the sole GPS/activity owner. Do not request more location permissions, enable Google Maps My Location, create another `LocationListener`, or change the 15 m GPX acceptance threshold.
- Add a map point only after the existing valid-location branch has appended its GPX point. A paused, stale, null, or inaccurate position must not alter the map trace.
- The map binds with flags `0` and must never start, pause, stop, or recreate `SpeedometerService`. It must safely handle a service that disappeared between tapping `Mapa` and binding.
- Keep Maps API keys outside version control, logs, screenshots, tests, and issue text. Restrict production use to `br.com.helpdev.velocimetroalerta.v2` plus its release certificate SHA-1.
- Add `INTERNET` only for Maps tiles. Do not add `ACCESS_BACKGROUND_LOCATION`, backend uploads, directions, or cloud route storage.
- Before shipping, update Play Data Safety and the privacy policy for the Maps SDK's documented collection of request metadata, crash metrics, IP address, Maps SDK identifier, and map interactions where applicable.

## Review Focus

- Returning from the map while a ride is active must preserve the service's running/paused state, chronometer, notification, and GPX point count; Task 4 owns this instrumentation test.
- A subscriber must receive one consistent initial snapshot and exactly one ordered notification per later accepted point, even when subscribing while the service appends; Task 2 owns the unit tests.
- The screen must retain the last route during manual/automatic pause and GPS degradation, but show a usable empty state before the first valid point; Tasks 2 and 3 own these cases.
- Late service callbacks must not update a destroyed map/fragment or retain it after rotation/navigation; Task 3 owns lifecycle tests.
- An invalid/missing API key, no Google Play services, or no network must fail only the map display and never interrupt tracking; Tasks 3 and 5 own this validation.

## File structure

| File | Responsibility |
| --- | --- |
| `gps/RoutePoint.kt` | Immutable coordinate, snapshot, and observer contract with no Maps types. |
| `gps/ActiveRoute.kt` | Thread-safe current-route state, revisions, subscription, and defensive snapshots. |
| `gps/SpeedometerService.kt` | Resets/appends the active route exactly with GPX point acceptance. |
| `RouteMapActivity.kt` | Edge-to-edge map host, explicit existing-service binding, Up/Back navigation. |
| `RouteMapFragment.kt` | Service subscription and UI lifecycle ownership. |
| `RouteMapRenderer.kt` | Maps-specific polyline/marker and camera adapter behind a testable interface. |
| `activity_route_map.xml`, `fragment_route_map.xml` | Toolbar/map/empty-error/recenter UI. |
| `fragment_main.xml`, `MainActivityFragment.kt` | Active-ride `Mapa` entry control. |
| `AndroidManifest.xml`, Gradle catalog/module config | SDK, API key metadata, activity, and network configuration. |
| Unit/instrumentation test files below | Route ordering and user-flow regression coverage. |

### Task 1: Configure Maps without exposing keys

**Files:**

- Modify: `.gitignore`
- Modify: `gradle/libs.versions.toml`
- Modify: `mobile/build.gradle`
- Modify: `mobile/src/main/AndroidManifest.xml`
- Create: `local.defaults.properties`
- Create: `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/RouteMapSmokeTest.kt`
- Modify: `README.md`

**Interfaces:**

- Produces the version-catalog aliases `libs.play.services.maps` and `libs.plugins.maps.secrets`.
- Produces manifest metadata named `com.google.android.geo.API_KEY` whose value is `${MAPS_API_KEY}` and an internal `RouteMapActivity` declaration.

- [ ] Write `RouteMapSmokeTest.mapActivityIsDeclaredAndLaunchesWithoutLocationPermission`; launch `RouteMapActivity` with no tracking permissions and assert its non-map chrome/empty-state root renders. The initial run must fail because the activity does not exist.
- [ ] Pin `com.google.android.gms:play-services-maps` and `com.google.android.libraries.mapsplatform.secrets-gradle-plugin` in `gradle/libs.versions.toml` using versions verified against the official setup documentation at implementation time; apply the plugin with the existing version-catalog plugin convention and add the Maps library to `mobile/build.gradle`.
- [ ] Ignore `secrets.properties`; add tracked `local.defaults.properties` with `MAPS_API_KEY=DEFAULT_API_KEY`, then configure the plugin to use `secrets.properties` locally and the harmless default in CI. Do not use `local.properties` for the key.
- [ ] Add only `android.permission.INTERNET`, the recommended Maps API metadata, and non-exported `RouteMapActivity` to `AndroidManifest.xml`.
- [ ] Add setup instructions for enabling Maps SDK for Android, creating distinct restricted debug/release keys, and the data-safety follow-up to `README.md`; include neither a key nor a certificate fingerprint.
- [ ] Run `./gradlew :mobile:assembleDebug :mobile:connectedDebugAndroidTest --tests '*RouteMapSmokeTest'` on the existing API-35-or-newer Google APIs emulator; expected: the activity launches and the app requests no extra runtime permission.
- [ ] Commit as `build: configure Maps SDK for active routes`.

### Task 2: Add an ordered, provider-neutral active-route publisher

**Files:**

- Create: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/gps/RoutePoint.kt`
- Create: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/gps/ActiveRoute.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/gps/SpeedometerService.kt`
- Create: `mobile/src/test/java/br/com/helpdev/velocimetroalerta/gps/ActiveRouteTest.kt`

**Interfaces:**

- Produces `data class RoutePoint(val latitude: Double, val longitude: Double)` and `data class RouteSnapshot(val revision: Long, val points: List<RoutePoint>)`.
- Produces `fun interface RouteObserver { fun onPointAdded(revision: Long, point: RoutePoint) }`.
- Produces `fun reset()`, `fun append(point: RoutePoint)`, `fun subscribe(observer: RouteObserver): RouteSnapshot`, and `fun unsubscribe(observer: RouteObserver)` on `ActiveRoute`.

- [ ] Write `ActiveRouteTest.snapshotContainsPreexistingPointsInOrder`, `subscribeReturnsImmutableSnapshotAndReceivesOnlyLaterPoints`, `notificationsIncreaseRevisionOncePerAppend`, `unsubscribeIsIdempotent`, and `resetClearsTheNextSnapshot`. Use a background append/subscription test to assert that a renderer can discard a snapshot/revision older than an already received update.
- [ ] Run `./gradlew :mobile:testDebugUnitTest --tests '*ActiveRouteTest'`; expected: failure because the route types and publisher do not exist.
- [ ] Implement `ActiveRoute` with a private lock, monotonically increasing revision, and a copy of observers captured under the lock then notified outside it. Return `points.toList()` in every snapshot; never expose mutable state.
- [ ] Add one `ActiveRoute` field to `SpeedometerService`. Reset it inside the existing `reset()` that creates a new GPX; after each successful `gpx.trk.trkseg.addTrkPt(...)`, append the same `location.latitude`/`location.longitude`. Do not append from notification/UI callbacks or from any pause/invalid-location path.
- [ ] Expose thin service methods `subscribeRoute(observer): RouteSnapshot` and `unsubscribeRoute(observer)` that delegate to the active route. Do not modify or repurpose `CallbackGpsThread`.
- [ ] Run `./gradlew :mobile:testDebugUnitTest --tests '*ActiveRouteTest'` and `./gradlew :mobile:testDebugUnitTest`.
- [ ] Commit as `feat: publish active GPS route updates`.

### Task 3: Implement a lifecycle-safe live-map screen

**Files:**

- Create: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/RouteMapActivity.kt`
- Create: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/RouteMapFragment.kt`
- Create: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/RouteMapRenderer.kt`
- Create: `mobile/src/main/res/layout/activity_route_map.xml`
- Create: `mobile/src/main/res/layout/fragment_route_map.xml`
- Modify: `mobile/src/main/res/values/strings.xml`
- Modify: `mobile/src/main/res/values-pt/strings.xml`
- Create: `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/RouteMapRenderingTest.kt`

**Interfaces:**

- Consumes: `SpeedometerService.subscribeRoute(observer)` and `unsubscribeRoute(observer)` from Task 2.
- Produces `RouteMapActivity` and a fragment that exposes no GPS/location-permission API and owns its observer only while its view is alive.

- [ ] Write `RouteMapRenderingTest.emptyRouteShowsWaitingState`, `initialSnapshotDrawsOnePolylineAndFinalMarker`, `laterPointUpdatesTheExistingPolylineAndMarker`, `staleRevisionIsIgnored`, `panStopsFollowAndRecenterRestoresIt`, and `backFinishesOnlyRouteMapActivity`. Use a fake `RouteMapRenderer` to test rendering/state transitions without a Maps key.
- [ ] Run the narrow test class; expected: failure because the activity, fragment, renderer adapter, and resources are absent.
- [ ] Implement `RouteMapActivity` with ViewBinding, `EdgeToEdge.enable`, system-bar insets, an `AppCompat` toolbar, and Up/Back handlers that only call `finish()`. Its `ServiceConnection` calls `bindService(serviceIntent, connection, 0)`; if binding fails or the bound service is no longer running, show an unavailable/final-route state rather than starting the service.
- [ ] Implement `RouteMapFragment` with a `SupportMapFragment`, binding/map-ready gates, and main-thread rendering. Subscribe exactly once after both gates are ready; apply the returned snapshot and render only callbacks with revisions greater than the last rendered revision. Unsubscribe before unbinding/clearing the binding in `onStop` or `onDestroyView`.
- [ ] Implement `RouteMapRenderer` around one `GoogleMap`, one `Polyline`, and one current-position `Marker`. Render an empty route as a waiting message; append/update rather than recreate the polyline; do not invoke `isMyLocationEnabled`.
- [ ] Frame the initial non-empty route, follow point additions by default, stop following on user camera movement, and wire the accessible `Recentrar` control to focus the final point and resume following. Leave the last route visible on pause, invalid GPS, service disconnection, or activity stop.
- [ ] Run `./gradlew :mobile:connectedDebugAndroidTest --tests '*RouteMapRenderingTest'` on a Google APIs emulator, then rotate/recreate the map screen and confirm destroyed views receive no update.
- [ ] Commit as `feat: render active route in real time`.

### Task 4: Add speed-screen entry and preserve the ride through navigation

**Files:**

- Modify: `mobile/src/main/res/layout/fragment_main.xml`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/MainActivityFragment.kt`
- Modify: `mobile/src/main/res/values/strings.xml`
- Modify: `mobile/src/main/res/values-pt/strings.xml`
- Create: `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/RouteMapNavigationTest.kt`

**Interfaces:**

- Consumes `RouteMapActivity` from Task 3 and the existing `SpeedometerService.isRunning`/`isPause` properties.
- Produces a labelled `btMap` control that launches only the map activity and never calls a service state method.

- [ ] Write `RouteMapNavigationTest.mapButtonIsHiddenBeforeStarting`, `mapButtonIsVisibleDuringRunningAndPausedRide`, `openingAndReturningPreservesRunningRide`, and `openingAndReturningPreservesPausedRide`. Assert service identity/state, elapsed chronometer base, and accepted-point count are unchanged across the map activity result.
- [ ] Run the test class; expected: failure because `btMap` and route-map navigation do not exist.
- [ ] Add an accessible `btMap` control with `contentDescription`/localized label to `fragment_main.xml`, positioned so it has a 48 dp touch target and does not overlap Start/Pause, Save, or Refresh.
- [ ] In `MainActivityFragment`, set its visibility from the current service state after connection, `setGpsPause`, `setBaseChronometer`, refresh/clear, and callback updates. On click, only execute `startActivity(Intent(requireContext(), RouteMapActivity::class.java))`; it must not call `requestTrackingStart`, `start`, `pause`, `stop`, `clear`, or mutate the chronometer.
- [ ] Confirm the map's Back returns to the running `MainActivity`; then confirm MainActivity's Back still displays its existing confirmation instead of stopping implicitly.
- [ ] Run `./gradlew :mobile:connectedDebugAndroidTest --tests '*RouteMapNavigationTest'` and `python3 tools/run-hermetic-android-tests.py --help` followed by the repository's documented instrumentation command.
- [ ] Commit as `feat: open live route map from active activity`.

### Task 5: Complete Maps-specific release validation and disclosures

**Files:**

- Modify: `docs/android-dependency-audit.md`
- Modify: `docs/superpowers/plans/2026-10-15-new-package-android-play-republication.md` or its release checklist successor
- Modify: the public privacy-policy source/repository identified by the release plan, if its Maps disclosure is incomplete
- Create: `docs/live-route-map-validation.md`

**Interfaces:**

- Consumes: Tasks 1-4 and a non-production restricted key supplied outside the repository.
- Produces: physical-device evidence and updated release/disclosure records, not secret material.

- [ ] Add the Maps SDK version, license, `minSdk`, data-collection implications, and keep rationale to `docs/android-dependency-audit.md`.
- [ ] Review Google's current Maps SDK Data Safety table and update Play Console/Data Safety plus the public privacy policy to describe the SDK's documented technical data collection and applicable map interactions. Record only the completed-form evidence/URL, never credentials.
- [ ] On a physical API-24-or-newer device with Google Play services, start a route, open the map before the first point and after multiple points, then walk/ride until new accepted points extend the line. Verify current marker, follow, pan/recenter, manual pause, automatic pause, invalid GPS, rotation, toolbar Up, and Android Back.
- [ ] Repeat with network unavailable and an invalid debug key. Confirm the map reports a recoverable display error while foreground tracking, GPX capture, notification, and return navigation continue.
- [ ] Run `./gradlew :mobile:assembleDebug :mobile:testDebugUnitTest :mobile:connectedDebugAndroidTest`; execute the existing hermetic instrumentation runner on the available emulator/device matrix and add the map cases to its documented invocation if required.
- [ ] Record device/API/build/key-kind (never value), route outcomes, and disclosure completion in `docs/live-route-map-validation.md`.
- [ ] Commit as `test: validate live route map and disclosures`.

## Plan self-review

- **Spec coverage:** Tasks 1-5 cover the existing modernized build, secure Maps configuration, service-owned points, live rendering, speed/map navigation, no-point/pause/failure states, privacy impact, automated tests, and physical validation.
- **Type consistency:** Task 2 defines the exact snapshot/observer API used in Task 3; Tasks 3 and 4 both consume `RouteMapActivity`; Maps types remain outside `gps`.
- **Scope:** No backend, location-sharing, offline map, route-matching, directions, or historical-map work is implied.
- **Review focus:** Each stated failure mode is pinned to a named Task 2-5 check.
