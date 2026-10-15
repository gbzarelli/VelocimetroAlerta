# Velocímetro Alerta v2 Android and Play Republication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete Android modernization and publish the ad-free Velocímetro Alerta as a new Google Play app with a new package and signing identity.

**Architecture:** Keep the existing Android source namespace and app implementation, change the installed package to `br.com.helpdev.velocimetroalerta.v2`, and validate the application before creating a new Play listing. Use a new local upload key with Play App Signing for distribution; keep the removed original listing as a historical record.

**Tech Stack:** Kotlin, Android Gradle Plugin 9.4.1, Gradle 9.6.0, JDK 17+, Android API 36 / compile API 36.1, AndroidX, Android Test, Google Play Console, Play App Signing, `keytool`, `jarsigner`, `bundletool`.

**Spec:** `docs/superpowers/specs/2026-10-15-new-package-play-republication-design.md`

## Global Constraints

- Set `applicationId` to `br.com.helpdev.velocimetroalerta.v2`; keep `namespace` and Kotlin/Java source packages at `br.com.helpdev.velocimetroalerta`.
- Keep increasing `versionCode` for every replacement bundle. The initial AAB used code 1 and failed Console validation; code 2 was validated for the internal-test draft; production was submitted with code 3 / versionName 4.2. The active build configuration is code 3.
- Use released API 36.1 to compile, target API 36, and set `minSdk` 24 to satisfy Play automatic protection.
- Keep the app free and without ads; add no ads SDK, AdMob unit, Advertising ID permission, or Google Ads campaign.
- Store the new upload keystore and its `.pass` sidecar in `/Volumes/gbz-ext/repositories/helpdev/android-keys/` using that repository's existing naming pattern; set both to owner-only permissions, never stage or commit them, and never print their contents. Use Play App Signing for distribution.
- Remove only `/Volumes/gbz-ext/repositories/helpdev/android-keys/velocimetro_alerta.jks` from the local `android-keys` worktree; preserve its other pre-existing changes and files.
- Do not delete or edit the old Play listing unless the Console later shows it is available and requires reversible unpublishing; it is currently marked **App removed**.
- Submit only after declarations, current store assets, and the location-permission video are ready. The user-provided unlisted YouTube demonstration was added before the production submission.
- Current Play status (2026-10-15): production submission 1 for versionCode 3 is **In review**. The app is not yet public; Play review and public-install verification remain pending. Managed publishing is off.
- Do not claim upgrade compatibility or local-data migration from the old package; the new package is a separate Android app identity.

## Review Focus

- A release built with namespace `br.com.helpdev.velocimetroalerta` must install under application ID `br.com.helpdev.velocimetroalerta.v2`; verify the merged manifest and uploaded AAB.
- The heart-rate-zone preference must open `ConfigHRZActivity` in the installed `.v2` package; cover the navigation path in an instrumentation test.
- Every permission-flow test must establish and restore its own location, notification, and Bluetooth state; cover the complete test suite on API 35 and API 36.
- No ad SDK or Advertising ID permission may appear in debug or release dependency/manifest reports; inspect both release and debug runtime graphs.
- The new Play listing must not imply that old installs can update or transfer private data; inspect the listing copy and test side-by-side install behavior where the old APK is available.

---

### Task 1: Reconcile the previous plans with the approved new package

**Files:**
- Modify: `docs/superpowers/plans/2026-10-14-android-modernization.md`
- Modify: `docs/superpowers/plans/2026-10-14-play-console-republication.md`
- Modify: `docs/superpowers/specs/2026-10-14-android-play-republication-design.md`
- Read: `docs/superpowers/specs/2026-10-15-new-package-play-republication-design.md`
- Read: this plan

**Interfaces:**
- Produces: one current implementation route where the Android plan owns functional modernization and this plan owns the new package, signing, Play Console, and publication steps.
- Consumes: owner-approved package, signing, audience, and ad-free decisions in the spec.

- [x] Add a superseded notice to the old preserve-package spec and Play republication plan that links to the new spec and this plan; retain the old documents as history and do not execute their signing steps.
- [x] Update the Android modernization plan's package constraint and release expectation to use `.v2` / version code 1 while retaining namespace `br.com.helpdev.velocimetroalerta`.
- [x] Read the prior spec and both plans end to end; search active instructions that preserve the original application ID or require version code 13 and correct any remaining conflicts.
- [x] Run `git diff --check`; search the active Android plan for `Preserve.*applicationId|versionCode 13|preserves the package ID` and inspect the first eight lines of both superseded documents. Expected: no stale release-identity assertions remain in the active Android plan, and both old documents link to their successors.

Expected: an engineer can follow the Android functional plan and this publication plan without conflicting package, signing, or version requirements.

### Task 2: Make the Android regression suite independent of emulator state

**Files:**
- Create: `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/test/RuntimePermissionStateRule.kt`
- Create: `tools/run-hermetic-android-tests.py`
- Modify: `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/TrackingPermissionFlowTest.kt`
- Modify: `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/BluetoothPermissionFlowTest.kt`
- Modify: `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/MainActivityBackNavigationTest.kt`
- Modify: `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/PrivacyPolicyNavigationTest.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/MainActivity.kt`
- Modify as needed: remaining instrumentation tests that share permission, service, preference, or Espresso Intents state

**Interfaces:**
- Produces: `RuntimePermissionStateRule` checks each test's API-applicable annotated permission state and stops `SpeedometerService` and clears the location-disclosure preference before and after each test. The host runner snapshots/restores the emulator's original runtime permission grants and sets each test's annotated state before launching that method in a fresh instrumentation process.
- Consumes: current API-aware permission behavior from `RuntimePermissionPolicy` and the existing instrumentation test suite.

- [x] Implement `RuntimePermissionStateRule` so it verifies only permissions applicable to the emulator API and matches the test's declared grant/denial state; stop the service and reset the location-disclosure preference in setup/teardown.
- [x] Implement `tools/run-hermetic-android-tests.py` to run each instrumentation method in a separate `am instrument` process, configure its permission state from `@RuntimePermissionState`, snapshot and restore original grants and permission flags on the host, and save per-test output under `mobile/build/hermetic-android-tests/`.
- [x] Write/update tests so `locationDeniedDoesNotStartTracking` and `locationDisclosureCanBeCancelledBeforePermissionPrompt` start with coarse/fine location denied, while `preciseGrantStartsLocationForegroundService`, `notificationDeniedDoesNotCrashTracking`, and `backDuringRideRequiresConfirmation` explicitly establish their required grants.
- [x] Mark `notificationDeniedDoesNotCrashTracking` as API 33+ because notification runtime permission does not exist on earlier supported APIs.
- [x] Run each affected test alone on API 35 using the host runner; expected: the runner configures permission state automatically and the JUnit rule confirms the state before test execution.
- [x] Apply the permission fixture to `deniedBluetoothPermissionLeavesGpsAvailable`, granting both location permissions and notifications while denying `BLUETOOTH_CONNECT`; restore the prior heart-rate preference and stop the service in teardown.
- [x] Scope Espresso Intents setup/release to each test and reset drawer, activity, service, and disclosure state so the privacy-link and active-ride Back assertions do not depend on test order.
- [x] Run `python3 tools/run-hermetic-android-tests.py --sdk 35,36`; expected: every discovered instrumentation case passes individually on API 35 and API 36, with original emulator permission grants and flags restored after each method.
- [x] Run JVM tests and `./gradlew :mobile:assembleDebug :mobile:bundleRelease :mobile:compileDebugAndroidTestKotlin`; expected: all commands pass and the release bundle remains unsigned until Task 4 configures the new upload key.

Expected: all automated tests pass as a suite on the available API 35 and 36 emulators, with host-managed per-test permission setup and restoration and no manual emulator preconditioning.

### Task 3: Set the new application ID and verify package-facing integrations

**Files:**
- Modify: `mobile/build.gradle`
- Modify: `mobile/src/main/res/xml/configs.xml`
- Modify: `README.md`
- Modify: `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/ConfigScreensSmokeTest.kt`
- Read: `mobile/src/main/AndroidManifest.xml`
- Read: `settings.gradle`

**Interfaces:**
- Produces: release `applicationId = br.com.helpdev.velocimetroalerta.v2`, with monotonically increasing version codes and `versionName = 4.2`; source namespace and package imports remain unchanged. The active production artifact is code 3.
- Consumes: passing functional test baseline from Task 2.

- [x] Extend `ConfigScreensSmokeTest.settingsAndHeartRateScreensRender` to assert `targetContext.packageName == "br.com.helpdev.velocimetroalerta.v2"`, then click the heart-rate zones preference and assert `layout_max_hr` is displayed; run the test before the package change and confirm it fails on the old application ID.
- [x] Change only `applicationId` in `mobile/build.gradle` to `br.com.helpdev.velocimetroalerta.v2`; set `versionCode` to 1 and keep `versionName` 4.2 and `namespace` unchanged.
- [x] Set `android:targetPackage` in `mobile/src/main/res/xml/configs.xml` to `br.com.helpdev.velocimetroalerta.v2`; keep `android:targetClass` on the existing source namespace class.
- [x] Update the README Google Play URL to `https://play.google.com/store/apps/details?id=br.com.helpdev.velocimetroalerta.v2`; search the project for old store URLs and update only user-facing links that should point to the new listing.
- [x] Run the focused configuration instrumentation test, `./gradlew :mobile:processDebugManifest :mobile:processReleaseManifest`, and `git diff --check`; expected: components still resolve and the release manifest reports the new package with the old namespace-backed classes.
- [x] Re-run the debug and release runtime dependency reports and inspect merged manifests; expected: no ad SDK, AdMob unit, or `com.google.android.gms.permission.AD_ID` appears in either variant.

Expected: debug and release variants use the new install identity while all internal class and resource references continue to resolve.

### Task 4: Create the new upload identity and remove the unusable old keystore

**Files and records:**
- Delete: `/Volumes/gbz-ext/repositories/helpdev/android-keys/velocimetro_alerta.jks`
- Create: `/Volumes/gbz-ext/repositories/helpdev/android-keys/velocimetro_alerta_v2_upload.jks`
- Create with owner-only permissions: `/Volumes/gbz-ext/repositories/helpdev/android-keys/.velocimetro_alerta_v2_upload.pass`
- Modify: `mobile/build.gradle` only if release signing requires a safe environment-variable adjustment
- Record non-secret metadata in: `docs/superpowers/plans/2026-10-15-new-package-android-play-republication.md`

**Interfaces:**
- Produces: a new RSA upload key with alias `velocimetro_alerta_v2_upload`, a public SHA-256 fingerprint, and a signed release build configuration using `VEL_ALERT_UPLOAD_STORE_FILE`, `VEL_ALERT_UPLOAD_STORE_PASSWORD`, `VEL_ALERT_UPLOAD_KEY_ALIAS`, and `VEL_ALERT_UPLOAD_KEY_PASSWORD`.
- Consumes: new package identity from Task 3 and the local signing safeguards already in `mobile/build.gradle`.

**Local signing record:** RSA-4096 upload certificate SHA-256 `1568b3ddae50bcc8e5d2f27b78a5d60329102b975adf7545c3041dfb7f7d1016`; private key and passphrase are stored only in the external `android-keys` worktree.

- [x] Confirm the prior audit records the old file's public SHA-256 as `25a97269bf75ff4b39718ab768530875a9a3ed3ddeab3dd13b03c521d002e938`, then remove only `android-keys/velocimetro_alerta.jks`; keep all pre-existing changes and other files in that repository untouched.
- [x] Generate the new upload keystore at the approved `android-keys` path, following the neighboring `.jks` and hidden `.pass` naming convention. Set both files to mode `600`; keep both untracked and unstaged, and never print the password or add either file to the app repository.
- [x] Export only the new public certificate and record its SHA-256 fingerprint; verify the key alias and certificate using `keytool` without exposing passwords.
- [x] Configure the four `VEL_ALERT_UPLOAD_*` values locally and build `./gradlew :mobile:bundleRelease`; expected for the initial artifact: package `.v2`, version code 1, target 36, and minSdk 23. The initial artifact was signed and verified but later rejected by Play's API 24 automatic-protection requirement.
- [x] Verify the AAB with `bundletool validate`, `jarsigner -verify`, and `keytool -printcert -jarfile`; expected: the AAB signature matches the new upload certificate and no signing secret appears in command output or tracked files.
- **Replacement AAB validation (2026-10-15):** `mobile-release.aab` SHA-256 `3a90876b2c82a4c0420a9da3346be9ea2fcb4977c8747e586319a3b2df2d7a02`; package `.v2`, versionCode 2, versionName 4.2, minSdk 24, targetSdk 36; bundletool validation and JAR signature verification pass, and the embedded upload certificate matches SHA-256 `1568b3ddae50bcc8e5d2f27b78a5d60329102b975adf7545c3041dfb7f7d1016`.
- [x] Check both Git worktrees; expected: the app repository contains no keystore/password files, while the keys repository preserves all pre-existing user changes and shows only the requested tracked keystore deletion plus the new credential files if Git does not ignore them. Never stage or commit any keys-repository changes.

Expected: the `.v2` release can be signed for upload, and the unusable `velocimetro_alerta.jks` working-tree file is removed without altering other key records.

### Task 5: Create the new Play listing and complete its metadata and declarations

**Files and records:**
- Read: old listing in Play Console, package `br.com.helpdev.velocimetroalerta`
- Create: new Play Console listing `Velocímetro Alerta`
- Use: `https://helpdev.com.br/apps/velocimetro-alerta/privacidade/`
- Record: the new app ID, package, signing certificates, declarations, and remaining review requirements in this plan

**Interfaces:**
- Produces: a free, Portuguese (Brazil) listing for package `br.com.helpdev.velocimetroalerta.v2`, with current metadata, privacy URL, audience/data/health/location declarations, and Play App Signing enabled.
- Consumes: the verified package and signed AAB from Tasks 3–4, and the actual behavior/dependency evidence from the Android plan.

- [x] Recheck the old listing remains **App removed** and preserve its current state; do not submit its four unsent changes or delete it.
- [x] Create the new app entry using title `Velocímetro Alerta`, Portuguese (Brazil), app (not game), free pricing, and the confirmed owner contact email; use the modernized descriptions based on the old listing's feature set and HTTPS website.
- [x] Use any package-availability check offered by the Console before registering the new package. If the Console binds a package only when accepting the first AAB, treat that upload as the permanent identity registration and use only the fully validated `.v2` bundle; stop if the package is already claimed or the required signing choices differ from the approved design.

- **Console evidence (2026-10-15):** The old package remains marked **App removed**. The new package `br.com.helpdev.velocimetroalerta.v2` is registered as `Velocímetro Alerta`, pt-BR, free. Developer policy, export-law, Play App Signing, and IARC terms were accepted with the owner's authorization. The owner contact is `contato@helpdev.com.br`, and the privacy page is `https://helpdev.com.br/apps/velocimetro-alerta/privacidade/`. Audience (13–17 and adults), no ads, Data Safety, Health, and foreground-location declarations are saved. The unlisted background-location demonstration is linked in the location declaration: `https://www.youtube.com/shorts/Se3rfMIysfY`. The icon, feature graphic, two phone screenshots, and `Don't label assets` AI declaration are saved in the default listing. Play App Signing and automatic protection are on. The initial code-1 / minSdk-23 AAB was rejected; code 2 / minSdk 24 was validated for the internal-test draft. The production track now contains release 4.2 / versionCode 3, target API 36, minSdk 24, Brazil only.
- [x] Enroll the new app in Play App Signing using Google's generated app-signing key and the new upload certificate; verify the Console shows releases are signed by Google Play and automatic protection is on.
- [x] Check Android developer verification for `br.com.helpdev.velocimetroalerta.v2`: current Google guidance says new apps created in Play Console are automatically registered to the developer account; the `.v2` Play listing was created after that flow launched and no certificate-registration prompt appeared. No certificate fingerprint was manually submitted for this new package. Check the developer account dashboard again for account-level verification alerts before production.
- [x] Set the new privacy URL and complete target audience (13–17 and adults, no under-13 audience), ads (none), Advertising ID (not used), Data Safety, Health apps, and Location permissions from the final app behavior and resolved dependencies.
- [x] Check current closed-testing eligibility: the old listing in this same developer account dates to 2020. Google's 12-testers/14-days requirement applies to personal accounts created after 2023-11-13, so it does not apply to this established account. Keep internal/closed testing available for QA; the internal release remains draft until a tester roster is supplied and rollout is deliberately started.
- [x] Add the owner-provided unlisted Location permissions video to the declaration before submitting production; do not substitute another URL.

Expected: the new listing is internally consistent and every applicable declaration and location evidence is saved before submission.

### Task 6: Prepare store assets and validate the Play test release

**Files and artifacts:**
- Create/review: `imagens/play-store-v2/app-icon-512.png` (use the existing launcher icon if it meets Play requirements)
- Create: `imagens/play-store-v2/feature-graphic-1024x500.png`
- Create: `imagens/play-store-v2/phone-01.png` and `phone-02.png` (add up to six more current phone screenshots if useful)
- Create: `mobile/build/outputs/bundle/release/mobile-release.aab`
- Use: Play Console internal testing or the first track required by its checklist

**Interfaces:**
- Consumes: final app behavior from Task 2, package/signing identity from Tasks 3–4, and draft listing/declarations from Task 5.
- Produces: a Play-accepted test release installed from the Console, with pre-launch checks reviewed and current store assets attached.

- [x] Capture current screenshots from the built app on a supported emulator; create and review the feature graphic and phone screenshots in `imagens/play-store-v2/`. Confirmed in the listing review that the icon, feature graphic, and both phone screenshots are attached. The icon matches the launcher artwork present since the 2016 project initialization; the phone images are emulator captures and the feature graphic reuses the app artwork, so the AI asset declaration is `Don't label assets`.
- [x] Historical gate: the Location permissions video was unavailable at this checkpoint, so review and production submission were blocked. The owner later supplied the video; it was added to the Location declaration before the production submission (see Task 7).
- [x] Upload the signed `versionCode 1` AAB to the internal-test draft; Console validation rejected it because automatic protection requires `minSdk` 24. Preserve automatic protection and replace it with a signed `versionCode 2` AAB using `minSdk` 24.
- [x] Upload the replacement `versionCode 2` AAB to the existing internal-test draft and confirm Console validation succeeds; save as draft only. After reloading the Console, the artifact row shows `2 (4.2)`, API levels `24+`, target SDK `36`, and no minSdk validation error. The release name and notes are saved, and no review or rollout action was submitted.
- [ ] Run the app installed through Play on API 24 and API 36; validate permission grant/denial, GPS tracking with the screen off, spoken alerts, notifications, BLE heart-rate connection and recovery, classic RFCOMM module connection and recovery, and GPX save/share/reopen.
- [ ] Install the old version-12 APK beside the new Play test build where available; verify Android treats them as separate apps and do not expect old private settings or unexported activity data to migrate.
- [ ] Review Play pre-launch results and fix all blocking crash, compatibility, declaration, or policy findings; upload a higher version code to the same test track when a fix changes the bundle.
- [x] Run `./gradlew :mobile:testDebugUnitTest --rerun-tasks` and `python3 tools/test_run_hermetic_android_tests.py`; the JVM build succeeds and all 7 host-runner cases pass.
- [x] Run `python3 tools/run-hermetic-android-tests.py --sdk 24,35,36`; 68/68 applicable executions pass: 14 on API 24, 18 on API 35, and 36 across two API 36 emulators. Four API-inapplicable cases are skipped by their declared SDK minimums.
- [x] Fresh local rerun on 2026-10-15 with the emulators then online (`--sdk 24,36`): API 24 passed 14/14 and one API 36 emulator passed 18/18. On the second API 36 emulator, 5/18 passed and 13 failed before app assertions with Espresso `RootViewWithoutFocusException` (`has-window-focus=false`). API 35 was unavailable for this rerun; the earlier 68/68 result remains historical evidence, and the latest focus failures are an environment limitation to recheck on a focused emulator/CI.

**Play test status (2026-10-15):** The code-2 internal-test draft remains unrolled because no tester roster was provided. Play installation and pre-launch checks remain pending; local emulator results are not a Play-installed test or pre-launch report. The owner later authorized submitting code 3 directly to Production; that submission is in review.

Expected: the tested bundle installs from Play and passes the exercised GPS, permissions, notification, sensor, speech, and GPX flows; store assets match the released app.

### Task 7: Submit production and verify the new listing

**Files and records:**
- Use: exact signed AAB accepted on the test track
- Update: new Play Console Production track and release notes
- Preserve: old removed listing

**Interfaces:**
- Consumes: approved test release, completed declarations including the location video, current store media, and verified signing/package records.
- Produces: an approved public Play listing under `br.com.helpdev.velocimetroalerta.v2`.

- [x] Review the Production release preview: no blocking errors remained; the only warning was the optional missing deobfuscation file. Play submission 1 was sent for review on 2026-10-15 with versionCode 3, versionName 4.2, minSdk 24, target SDK 36, Brazil only, and the approved release notes.
- [x] Use the owner's approved rollout scope (Brazil) and ad-free, free listing; do not create ad campaigns or enable monetization.
- [x] Submit production changes after adding the location video and completing the declarations. The Console shows submission status **In review**; managed publishing is off.
- [ ] Review Google Play's decision and address any returned policy or technical findings.
- [ ] Monitor review feedback and respond only using verified source/policy evidence; if a new bundle is required, return to Task 6 with an increased version code.
- [ ] After approval, verify the public URL contains `id=br.com.helpdev.velocimetroalerta.v2`, the listing is free and ad-free, and a fresh Play install can start GPS tracking, show notifications, configure sensors, and export GPX.
- [ ] Confirm the old listing remains preserved as **App removed**; do not attempt deletion unless Google exposes a supported action and requires it.

Expected: after Google's review completes, the new listing is public and functional, and the historic removed listing remains intact. Current status: **In review**, not yet public.

## External platform references

- Lost signing key for an app not enrolled in Play App Signing: <https://support.google.com/googleplay/android-developer/answer/9061737?hl=en>.
- Create and configure a Play app; package names are permanent: <https://support.google.com/googleplay/android-developer/answer/9859152?hl=en>.
- Play App Signing and upload keys: <https://support.google.com/googleplay/android-developer/answer/9842756?hl=en>.
- Repetitive content policy: <https://support.google.com/googleplay/android-developer/answer/9899034?hl=en>.
- Data Safety declarations: <https://support.google.com/googleplay/android-developer/answer/10787469?hl=en>.
- Location permissions declaration: <https://support.google.com/googleplay/android-developer/answer/16558241?hl=en>.
- Android developer verification for Play Console: <https://developer.android.com/developer-verification/guides/google-play-console>.
