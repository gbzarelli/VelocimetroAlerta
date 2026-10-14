# Android Modernization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Modernize `:mobile` for current Android and Play requirements while preserving GPS, speed alerts, BLE sensor, and GPX workflows.

**Architecture:** Restore a reproducible Gradle build, migrate the existing view-based app to AndroidX and ViewBinding, and isolate API-level permission decisions in a small policy helper. Keep foreground location tracking behind an explicit user action and stream GPX output through the Storage Access Framework.

**Tech Stack:** Kotlin, Android Gradle Plugin 9.4.1, Gradle 9.6.0, JDK 17 or newer, Android API 37 (Android 17), AGP built-in Kotlin, AndroidX, Material Components, Android `XmlSerializer`/`XmlPullParser`, JUnit, AndroidX Test.

**Spec:** `docs/superpowers/specs/2026-10-14-android-play-republication-design.md`

## Global Constraints

- Preserve `applicationId` `br.com.helpdev.velocimetroalerta`.
- Compile and target API 37, the latest stable Android release at plan creation; recheck the Play minimum (currently API 36+) before upload and raise the target if that minimum advances.
- Set `minSdk` to 23, approved by the owner after the current stable Material Components requirement was confirmed; this drops support below Android 6.0.
- Keep the app free and without ads; add no ad SDK, ad unit, AdMob configuration, or Advertising ID permission.
- Do not track location before the user starts tracking with required permission; do not add `ACCESS_BACKGROUND_LOCATION` unless the reviewed behavior and Play policy require it.
- Keep keystores, passwords, and private key material outside tracked files and command output.
- Use Gradle 9.6.0 with AGP 9.4.1 and a JDK 17+ runtime; AGP 9.4.1 is the latest stable artifact available, supports API 37, and requires Gradle 9.6/JDK 17. See [AGP 9.4 release notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes) and [built-in Kotlin migration](https://developer.android.com/build/migrate-to-built-in-kotlin).

## Review Focus

- A user who denies or later revokes precise location must be able to use non-tracking screens without a crash, and tracking must not start; cover in Task 3 permission tests.
- A user who grants approximate but not precise location must see a clear explanation that accurate speed tracking needs precise location; cover in Task 3 instrumentation/manual checks.
- A user who denies Bluetooth access or turns Bluetooth off must retain GPS tracking and receive a recoverable sensor status; cover in Task 4 permission checks and device validation.
- Canceling GPX document creation or exporting two activities with similar timestamps must not lose track data or overwrite an earlier export; cover in Task 5 serializer and SAF checks.
- The oldest supported API and API 37 must display controls correctly around system bars and handle Back while tracking; cover in Task 6 UI checks and Task 7 device matrix.
- API 37 ignores orientation and resizability restrictions on large-screen devices (`sw >= 600dp`); keep tracking state and controls usable across rotation, resize, and fold/unfold. Cover in Task 6 and the API 37 device matrix.
- A closed classic Bluetooth RFCOMM socket returns `-1` from `InputStream.read()` on API 37; treat it as disconnect, close the session, and recover without interrupting GPS. Cover in Task 4 unit and physical-device checks.
- Background speech alerts on API 37 must run under the app's user-initiated location foreground service; verify screen-off behavior on API 37 in Tasks 3 and 7.

---

### Task 1: Restore the pinned Gradle build

**Files:**
- Modify: `build.gradle`
- Modify: `settings.gradle`
- Modify: `gradle.properties`
- Modify: `.gitignore`
- Modify: `mobile/build.gradle`
- Create: `gradle/libs.versions.toml`
- Create: `docs/android-dependency-audit.md`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradle/wrapper/gradle-wrapper.jar`
- Regenerate: `gradlew`, `gradlew.bat`

**Interfaces:**
- Produces: checked-in Gradle Wrapper 9.6.0; Android plugin 9.4.1; a version catalog for pinned plugins and dependencies; module configuration with compile/target API 37 and conditional `minSdk = 19`.

- [x] Replace the incomplete wrapper with the official Gradle 9.6.0 wrapper scripts, JAR, and properties; stop ignoring the wrapper directory while retaining ignores for local build outputs and signing files.
- [x] Move plugin resolution to `pluginManagement` with Google and Maven Central; remove JCenter and the legacy root `buildscript` Kotlin/AGP classpaths.
- [x] Apply `com.android.application` from the version catalog at 9.4.1; use AGP built-in Kotlin instead of `kotlin-android` and remove the direct `kotlin-stdlib-jre7` dependency.
- [x] Set `compileSdk` and `targetSdk` to API 37 for Android 17, installing the Android 17 SDK platform; AGP 9.4 uses SDK Build Tools 36.0.0 by default, so omit a hard-coded `buildToolsVersion`. Google Play's update floor is currently API 36+, so recheck its policy immediately before upload and use a higher supported API if required. Keep `minSdk = 19` pending the dependency audit; enable AndroidX and ViewBinding.
- [x] Remove the tracked `playStore` signing block and credential-like values from `mobile/build.gradle`. Configure release signing only from local ignored properties or environment variables; leave local release builds unsigned when those values are absent.
- [x] Replace the legacy root `clean` task declaration with Gradle 9-compatible task registration or remove it if the base plugin already provides the required task.
- [x] Run `./gradlew :mobile:dependencies --configuration debugRuntimeClasspath` and `./gradlew :mobile:dependencies --configuration releaseRuntimeClasspath`; record each direct/transitive library's resolved version, license, minSdk constraint, maintenance status, known advisories, local-network behavior, and keep/upgrade/replace decision in `docs/android-dependency-audit.md`.
- [x] Run `./gradlew --version` and `./gradlew help`.
- [x] Commit the wrapper and build-configuration changes as `build: modernize Android Gradle toolchain`.

Run: `./gradlew --version` and `./gradlew help`
Expected: Gradle 9.6.0 starts on JDK 17 or newer, and project configuration completes without resolving JCenter or requiring the missing local keystore.

### Task 2: Migrate support libraries and synthetic view access

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `mobile/build.gradle`
- Modify: `mobile/src/main/AndroidManifest.xml`
- Modify: Kotlin sources under `mobile/src/main/java/br/com/helpdev/velocimetroalerta/` that import `android.support.*`, `android.preference.*`, or `kotlinx.android.synthetic.*`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/ConfigActivityFragment.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/dialogs/DialogHR.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/dialogs/DialogPrefVelAlertBLModule.kt`
- Modify: `mobile/src/main/res/xml/configs.xml`
- Modify: support widget declarations in `mobile/src/main/res/layout/` (`activity_main.xml`, `app_bar_main.xml`, `activity_config.xml`, `activity_config_hrz.xml`, `activity_my_activities.xml`, and referenced content/dialog layouts)
- Create: `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/MainActivitySmokeTest.kt`
- Create: `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/ConfigScreensSmokeTest.kt`

**Interfaces:**
- Consumes: Gradle 9.6.0, AGP 9.4.1, and the version catalog from Task 1.
- Produces: AndroidX `AppCompatActivity`, Fragment, Preference (`PreferenceFragmentCompat`), Core, ConstraintLayout, Material Components, generated ViewBinding classes for existing layouts, and valid explicit exported settings for Android 12+ components.

- [x] Pin current stable AndroidX and Material Components dependencies in `gradle/libs.versions.toml`, set `minSdk = 23` for Material Components 1.14.0, and remove `com.android.support:*` and `com.android.support.constraint:*` dependencies.
- [x] Resolve the already reproduced `:mobile:processDebugManifest` failure by setting `android:exported="true"` on launcher `MainActivity`, changing the heart-rate zones preference in `configs.xml` to target `ConfigHRZActivity` explicitly, setting `android:exported="false"` on that internal activity, and marking internal services `false`; rerun `./gradlew :mobile:processDebugManifest`.
- [x] Update `docs/android-dependency-audit.md` with the selected AndroidX versions and any minimum-SDK or license constraints; record all 75 resolved runtime artifacts and their group rationale.
- [x] Replace every legacy Support Library Kotlin import and layout class with its AndroidX or Material Components equivalent. Preserve the platform parent-activity metadata key `android.support.PARENT_ACTIVITY`; AndroidX `NavUtils` still defines and reads this key.
- [x] Migrate `ConfigActivityFragment` and its custom dialog preferences from platform `android.preference` to AndroidX Preference; remove the stray `-` text node in `configs.xml` while preserving all preference keys and defaults.
- [x] Enable and use ViewBinding in `MainActivityFragment`, `DialogNumberPicker`, and `ConfigHRZActivity`; remove synthetic imports from those files and the unused import in `SpeedometerService`; clear fragment binding in `onDestroyView`.
- [x] Set the speedometer notification `PendingIntent` to immutable; the API 35 main-screen smoke test exposed the Android 12+ runtime crash during service creation.
- [x] Add AndroidX Test runner/rules and the two smoke tests. `MainActivitySmokeTest` launches the main screen with its required test permissions granted and asserts the speed display and start control are visible. `ConfigScreensSmokeTest` opens settings and heart-rate configuration and checks both toolbars and the heart-rate content root.
- [x] Run `rg -n 'android\.support|android\.preference|kotlinx\.android\.synthetic' mobile/src/main/java mobile/src/main/res`; expected: no matches.
- [x] Run `./gradlew :mobile:assembleDebug :mobile:connectedDebugAndroidTest` on the available API 35 emulator. The SDK Manager listed no API 37 system image on 2026-10-14; API 37 compile/platform and manifest validation still pass.
- [x] Commit the AndroidX and ViewBinding migration as `refactor: migrate app UI to AndroidX` (`08d4f3d`).

Expected: debug APK assembles and both smoke tests pass without Support Library or synthetic-view references on the available modern emulator; API 37 remains the compile and target platform.

### Task 3: Make location permission and foreground tracking API-aware

**Files:**
- Modify: `mobile/src/main/AndroidManifest.xml`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/MainActivity.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/MainActivityFragment.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/gps/Gps.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/gps/SpeedometerService.kt`
- Create: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/permissions/RuntimePermissionPolicy.kt`
- Create: `mobile/src/test/java/br/com/helpdev/velocimetroalerta/permissions/RuntimePermissionPolicyTest.kt`
- Create: `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/TrackingPermissionFlowTest.kt`

**Interfaces:**
- Produces: `RuntimePermissionPolicy.permissionsFor(feature: RuntimePermissionPolicy.Feature, sdkInt: Int): Set<String>`; nested `Feature` values are `LOCATION`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, and `POST_NOTIFICATIONS`.
- Produces: `Gps.init(context)` configures `LocationManager` without subscribing; `Gps.startTracking()` subscribes only after permission is granted; `Gps.stopTracking()` removes updates; `Gps.close()` stops and releases tracking.

- [x] Write `RuntimePermissionPolicyTest` with exact expectations: location is empty at API 22 and `{ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION}` at API 23; Bluetooth scan is `{ACCESS_FINE_LOCATION}` at API 30 and `{BLUETOOTH_SCAN}` at API 31; connect is `{BLUETOOTH_CONNECT}` at API 31; notifications are empty at API 32 and `{POST_NOTIFICATIONS}` at API 33.
- [x] Run `./gradlew :mobile:testDebugUnitTest --tests '*RuntimePermissionPolicyTest'` and confirm it fails because the policy type has not been created.
- [x] Write the four `TrackingPermissionFlowTest` cases for denied, precise, approximate-only, and notification-denied access. Validate location revocation from the host with `adb`, because Android restarts the app process after precise-location downgrade; the instrumentation runner cannot survive revoking its target process.
- [x] Implement the policy helper and update the manifest with coarse/fine location, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`, and API-31 Bluetooth permissions; cap legacy Bluetooth permissions at API 30 and mark scan `neverForLocation` only if code review confirms scan results are not used to infer location.
- [x] Set `android:foregroundServiceType="location"` on `SpeedometerService`; keep background-location permission out unless the implementation audit proves it is necessary.
- [x] Change `MainActivity` so app launch binds the service without prompting for storage/location or subscribing to GPS. Register permission requests with Activity Result APIs and request precise location only when the user taps Start.
- [x] Change `Gps.init` to initialize without updates. Start location updates only after precise permission is granted and tracking begins; stop updates on track stop and service destruction.
- [x] Promote `SpeedometerService` to a location foreground service synchronously from the explicit Start action before processing location; handle `SecurityException` by stopping foreground state and returning a visible recoverable error.
- [x] Make notification denial non-fatal to GPS tracking while explaining that notification visibility is limited; location denial or approximate-only access must prevent precise speed tracking and offer a retry path.
- [x] Make the tracking permission scenarios pass on API 35: denied location, approximate-only, notification denial, and precise tracking with emulator GPS fix; stopping clears the fix and foreground state. Host `dumpsys` also confirmed an ongoing location foreground notification.
- [ ] On API 37, start tracking while the activity is visible, turn the screen off, and verify location updates and spoken speed alerts continue under the location foreground service; stop tracking and verify both cease. API 35 testing confirmed the foreground service remains active with the screen off; spoken output was not exercised, and the API 37 emulator image is unavailable in the installed SDK.
- [ ] Run `./gradlew :mobile:testDebugUnitTest :mobile:connectedDebugAndroidTest` on API 23, API 30, API 31, API 33, API 36, and API 37 emulators where available. Only API 35 is installed; Task 7 retains the final device-matrix check.
- [ ] Commit the location and Bluetooth permission/service changes together as `fix: modernize location and Bluetooth permissions` because Tasks 3 and 4 share the foreground service, permission policy, and main screen callbacks.

Expected: policy unit cases pass; no GPS update subscription or foreground location service starts before an explicit permitted Start action.

### Task 4: Migrate Bluetooth runtime permissions and connection flows

**Files:**
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/bluetooth/Bluetooth.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/dialogs/DialogHR.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/dialogs/DialogPrefVelAlertBLModule.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/ConfigActivityFragment.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/ConfigHRZActivity.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/sensors/HMService.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/sensors/HMGatt.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/sensors/VAMService.kt`
- Create: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/sensors/VamFrameReader.kt`
- Create: `mobile/src/test/java/br/com/helpdev/velocimetroalerta/sensors/VamFrameReaderTest.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/gps/SpeedometerService.kt`
- Modify: `mobile/src/test/java/br/com/helpdev/velocimetroalerta/permissions/RuntimePermissionPolicyTest.kt`
- Create: `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/BluetoothPermissionFlowTest.kt`

**Interfaces:**
- Consumes: `RuntimePermissionPolicy.permissionsFor` from Task 3.
- Produces: user-initiated scan/connect flows that request only the Bluetooth capability needed for the selected sensor, handle RFCOMM EOF/disconnects, and leave GPS tracking usable if Bluetooth is unavailable.

- [x] Extend permission-policy tests with the expected API 30 and API 31 scan/connect permission sets; leave denial behavior to the instrumentation test below.
- [x] Write `BluetoothPermissionFlowTest.deniedBluetoothPermissionLeavesGpsAvailable` and run it on API 35; its denial scenario verifies GPS remains active and the sensor denial status is visible.
- [x] Request scan permission before discovery and connect permission before reading bonded devices or starting GATT; do not request Bluetooth permissions on app launch.
- [x] Replace programmatic `BluetoothAdapter.enable()`/`disable()` calls with the system enable request flow; use `BluetoothManager.adapter` and guard every privileged Bluetooth operation with the matching permission.
- [x] Update dynamic receiver registration for Android 13+ receiver flags and close/unregister receivers and GATT connections on lifecycle termination.
- [x] Add user-visible sensor states for unsupported hardware, permission denial, Bluetooth disabled, connection failure, and reconnection; keep speedometer GPS controls operational in all sensor failure cases.
- [x] Add `VamFrameReaderTest.closedSocketReturnsDisconnected` and `partialReadsProduceOneCrlfFrame`; make an EOF (`read() == -1`) return a disconnect result instead of writing a negative byte count or spinning.
- [x] Update `VAMService` to close the RFCOMM socket and clear/reset sensor state on EOF, then retry connection with bounded delay while GPS remains active.
- [x] Run unit tests and the Bluetooth permission instrumentation scenario on the available API 35 emulator; a connected suite excluding the separately configured location-permission matrix also passed 3/3. API 30/31/33/36/37 emulator images are not installed.
- [ ] Make `BluetoothPermissionFlowTest` pass on API 31/36/37, then perform physical sensor validation below. API 35 passed; API 31/36/37 images and physical BLE/RFCOMM sensors are unavailable in this environment.
- [ ] On physical hardware, pair and connect a BLE heart-rate sensor and the classic RFCOMM VAM module, receive readings, revoke permission, disable Bluetooth, drop each connection during a read, and verify recovery without interrupting GPS tracking.
- [ ] Commit the location and Bluetooth permission/service changes as `fix: modernize location and Bluetooth permissions`.

Expected: API 30 uses legacy permission behavior; API 31+ prompts for the specific scan/connect permissions; BLE or RFCOMM sensor denial/disconnect does not crash or stop GPS, and RFCOMM EOF causes a clean reconnect path.

### Task 5: Replace legacy GPX serialization and export through SAF

**Files:**
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/gpx/GpxFileUtils.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/gpx/objects/Gpx.java`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/gpx/objects/MetaData.java`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/gpx/objects/Trk.java`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/gpx/objects/TrkSeg.java`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/gpx/objects/TrkPt.java`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/gpx/objects/Extensions.java`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/gpx/objects/TrackPointExtension.java`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/sensors/VAMService.kt`
- Create: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/sensors/TrackPointExtensionJsonParser.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/MainActivityFragment.kt`
- Modify: `mobile/src/main/AndroidManifest.xml`
- Modify: `mobile/build.gradle`
- Create: `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/gpx/GpxFileUtilsTest.kt`
- Create: `mobile/src/test/java/br/com/helpdev/velocimetroalerta/sensors/TrackPointExtensionJsonParserTest.kt`

**Interfaces:**
- Produces: `GpxFileUtils.writeGpx(gpx: Gpx, output: OutputStream)` serializes GPX 1.1 without taking ownership of the caller's stream and throws `IllegalArgumentException` when the track has no points.
- Produces: `TrackPointExtensionJsonParser.parse(json: String): TrackPointExtension?` for the JSON conversion used by `VAMService`.
- Produces: `MainActivityFragment` saves with `ActivityResultContracts.CreateDocument("application/gpx+xml")` and shares the resulting `content://` URI with read permission.

- [ ] Write `GpxFileUtilsTest` with a track containing two points and heart-rate/cadence extensions; parse the result and assert GPX 1.1 namespace, creator, timestamps, coordinates, elevation, and extension values. Add `writeGpxRejectsEmptyTrack`, which asserts `IllegalArgumentException` for a GPX with no points.
- [ ] Run the GPX instrumentation test and confirm it fails because the OutputStream writer and empty-track validation have not been implemented.
- [ ] Replace Simple XML annotations/`Persister` with Android `XmlSerializer`; emit correctly namespaced GPX 1.1 and Garmin track-point extensions. Remove the unused altitude debug writer and obsolete file-only serialization methods after confirming no callers remain; retain the empty-track behavior by asserting that the user flow does not launch export without a valid track.
- [ ] Remove `org.simpleframework:simple-xml`; upgrade Gson from 2.8.2 to a current stable version pinned in `gradle/libs.versions.toml`.
- [ ] Write `TrackPointExtensionJsonParserTest.parsesSensorValuesAndMissingOptionalFields` for `{"cad":"82","atemp":"21","rhu":null,"hr":"144"}`; assert getters return cadence `82`, temperature `21`, humidity `null`, and heart rate `144`, then run it and confirm the new parser API is missing.
- [ ] Implement `TrackPointExtensionJsonParser.parse(json: String): TrackPointExtension?` with the pinned Gson dependency, inject/use it in `VAMService`, and keep absent JSON properties null.
- [ ] Run `./gradlew :mobile:testDebugUnitTest --tests '*TrackPointExtensionJsonParserTest'`.
- [ ] Replace `WRITE_EXTERNAL_STORAGE`/`READ_EXTERNAL_STORAGE` checks in `MainActivityFragment.save()` with `CreateDocument`; validate that the GPX has points before launching the picker, write in a background executor via `ContentResolver.openOutputStream`, use a collision-resistant timestamp filename, and report chooser cancellation without error.
- [ ] Share the saved document URI using `Intent.EXTRA_STREAM`, `ClipData`, and `FLAG_GRANT_READ_URI_PERMISSION`; remove `file://` sharing and broad external-storage manifest permissions.
- [ ] Run GPX instrumentation tests and `./gradlew :mobile:testDebugUnitTest :mobile:connectedDebugAndroidTest` on API 30, API 36, and API 37.
- [ ] Manually export twice, cancel the picker, reopen the exported file in a GPX viewer, and share it to another app.
- [ ] Verify the two exports have distinct document names and the first export remains readable after the second is saved.
- [ ] Commit the GPX serialization/export migration as `feat: export GPX through the document picker`.

Expected: saved GPX round-trips with all existing track and sensor data; no external-storage permission is requested; canceling the picker leaves the activity intact.

### Task 6: Update Back navigation and edge-to-edge layouts

**Files:**
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/MainActivity.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/MainActivityFragment.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/ConfigActivity.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/ConfigHRZActivity.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/MyActivities.kt`
- Modify: `mobile/src/main/res/layout/activity_main.xml`
- Modify: `mobile/src/main/res/layout/app_bar_main.xml`
- Modify: `mobile/src/main/res/layout/fragment_main.xml`
- Modify: `mobile/src/main/res/values/styles.xml`
- Modify: `mobile/src/main/res/values-v21/styles.xml`
- Modify: `mobile/src/main/res/values/dimens.xml`
- Create: `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/MainActivityBackNavigationTest.kt`

**Interfaces:**
- Produces: `MainActivity` uses AndroidX `OnBackPressedDispatcher`; navigation drawer closes first, then an active ride prompts before exit, and an inactive screen follows normal Back behavior.

- [ ] Write `MainActivityBackNavigationTest.backClosesDrawerBeforeActivity` and `backDuringRideRequiresConfirmation`; run them on API 37 and confirm the edge-to-edge/back requirements are not yet covered or fail due to the legacy callback.
- [ ] Replace `onBackPressed()` with `OnBackPressedCallback`; replace `startActivityForResult`/`onActivityResult` for configuration and activity navigation with registered Activity Result launchers.
- [ ] Enable edge-to-edge with `WindowCompat.setDecorFitsSystemWindows(window, false)` and apply status/navigation bar insets to the activity and fragment roots so the toolbar, speed readouts, and controls remain tappable.
- [ ] Support large-screen rotation and resizing: remove reliance on the manifest's portrait-only restriction for large screens, use responsive layouts for `sw >= 600dp`, and preserve active ride state across configuration changes. Retain the phone layout where it remains appropriate.
- [ ] Run Back-flow tests and capture screenshots on API 30/API 36/API 37 phones and API 37 tablet/foldable configurations; inspect system-bar overlap, touch targets, drawer behavior, resize, rotate, and fold/unfold while tracking.
- [ ] Commit edge-to-edge and Back navigation changes as `fix: support edge-to-edge and predictive Back`.

Expected: drawer, confirmation, and normal Back behaviors match existing intent; no interactive content is covered by system bars on API 35/36/37 or in large-screen configurations.

### Task 7: Complete regression coverage and produce a release-ready AAB

**Files:**
- Create: `mobile/src/test/java/br/com/helpdev/velocimetroalerta/gpx/GpxMathTest.kt`
- Create: `mobile/src/test/java/br/com/helpdev/velocimetroalerta/gps/ObSpeedometerAlertTest.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/gpx/GpxMath.kt`
- Modify: `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/TrackingPermissionFlowTest.kt`
- Modify: `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/MainActivitySmokeTest.kt`
- Modify: `mobile/build.gradle`
- Modify: `README.md` only if build/test or SDK support instructions have changed

**Interfaces:**
- Consumes: modernized UI, permission, service, Bluetooth, GPX, and toolchain behavior from Tasks 1–6.
- Produces: a validated release bundle build; final version code and Play signing configuration are applied in the Play republication plan after checking the Console's highest uploaded code.

- [ ] Add `GpxMathTest.calculateGainCountsChangesAtLeastMeanAccuracy` using elevations 100, 102, 105, 103 with accuracy 2 for each point and assert gain/loss `(5.0, 2.0)`; add `calculateGainReturnsZeroForEmptyTrack` and assert `(0.0, 0.0)` after adding the empty-input guard.
- [ ] Add `ObSpeedometerAlertTest.cadenceAndBpmAveragesExcludePausedSamples`: cadence 20, paused 40, then 30 yields average 25 and max 40; heart rate 100, paused 200, then 120 yields average 110 and max 200. Add `addTimePausedAccumulatesMilliseconds` and assert inputs 1000.0 and 250.0 yield `timePaused == 1250L`.
- [ ] Run `./gradlew :mobile:testDebugUnitTest --tests '*GpxMathTest.calculateGainReturnsZeroForEmptyTrack'`; confirm it fails on the current empty-list indexing behavior, then add the empty-input guard in `GpxMath.calculateGain` and rerun it to pass.
- [ ] Run `./gradlew :mobile:testDebugUnitTest` and `./gradlew :mobile:connectedDebugAndroidTest` on API 23, API 30, API 31, API 33, API 34, API 35, API 36, and API 37; include API 19 if it remains the approved minimum. Include API 37 large-screen rotation/resizing and screen-off speech-alert scenarios.
- [ ] On physical hardware, run a GPS route with screen off, spoken speed alerts, pause/resume, heart-rate sensor reconnect, and GPX export/import/share; record device/API and outcome for each scenario.
- [ ] Run `./gradlew :mobile:assembleDebug` and `./gradlew :mobile:bundleRelease` with no signing credentials present; expected: debug APK and unsigned release AAB build without hidden local-keystore assumptions.
- [ ] Inspect the resolved dependency report and merged manifest; verify no JCenter, Simple XML, advertising SDK, `AD_ID`, broad storage permission, or `ACCESS_LOCAL_NETWORK` remains unless the audited app/dependency behavior requires local-network access and the Android 17 permission has been correctly implemented.
- [ ] Update `docs/android-dependency-audit.md` from the final release dependency graph and confirm every shipped dependency has a documented disposition.
- [ ] Commit the regression suite and final build configuration as `test: cover Android modernization release flows`.

Expected: unit and instrumentation suites pass; the physical-device checklist passes; release bundle metadata preserves package ID and targets API 37.

## Platform references

- AGP 9.4 supports API 37 with Gradle 9.6, Build Tools 36.0.0, and JDK 17: <https://developer.android.com/build/releases/agp-9-4-0-release-notes>.
- Android 17 SDK setup and API 37 platform installation: <https://developer.android.com/about/versions/17/setup-sdk>.
- Android 17 behavior changes for target API 37, including background audio, large-screen orientation, and Bluetooth socket EOF: <https://developer.android.com/about/versions/17/behavior-changes-17>.
- Android 17 migration guide: <https://developer.android.com/about/versions/17/migration>.
- Current Play target API policy (currently API 36+): <https://support.google.com/googleplay/android-developer/answer/11926878?hl=en>.
- Foreground service launch and location restrictions: <https://developer.android.com/develop/background-work/services/fgs/launch>.
- Bluetooth permission matrix: <https://developer.android.com/develop/connectivity/bluetooth/bt-permissions>.
- Scoped-storage migration guidance: <https://developer.android.com/about/versions/11/privacy/storage>.
