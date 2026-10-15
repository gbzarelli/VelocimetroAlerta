# Android Modernization and Play Store Republication Design

> **Superseded for package identity and republication:** The owner approved a new listing at `br.com.helpdev.velocimetroalerta.v2`; follow [`2026-10-15-new-package-play-republication-design.md`](2026-10-15-new-package-play-republication-design.md) and [`2026-10-15-new-package-android-play-republication.md`](../plans/2026-10-15-new-package-android-play-republication.md). This document remains historical; its Android modernization requirements continue through the active Android and new-package plans.

## Goal

Restore and republish Velocímetro Alerta as a functional Android application compatible with current Android releases and Google Play requirements, while preserving the existing package identity and avoiding unverified signing-key changes.

## Agreed scope

- The application remains free and without advertising. Repository inspection found no ad SDK, ad unit, or advertising integration, and the AdMob account has no app registration matching `br.com.helpdev.velocimetroalerta`. Do not create an AdMob app or unit, add an advertising SDK, or declare Advertising ID use unless the product decision changes.
- Modernize the Android build for stable Android 16 / API 36, the minimum currently required for Play submissions; do not target Android 17/API 37 while its SDK is still Preview.
- Set the minimum SDK to API 23 / Android 6.0. The owner approved this after the current stable Material Components release required API 23; this drops support for Android 5.x and earlier.
- Preserve `applicationId` `br.com.helpdev.velocimetroalerta`. The next release must use a `versionCode` greater than 12; after auditing Play's visible release history, source is set to `versionCode` 13 / `versionName` 4.2.
- Audit existing signing material and Play App Signing state before selecting a signing key. Do not generate or substitute a signing key blindly.
- Resolve the Play Console policy, app-content, package-verification, and release-track requirements before production rollout.

## Current state and constraints

- The repository is clean on `master` at the start of this design. The app module is `:mobile`; the `:wear` module is not included by `settings.gradle`.
- `mobile/build.gradle` declares Android Gradle Plugin 3.1.3, Kotlin Gradle Plugin 1.2.30, compile/target SDK 27, minimum SDK 19, and version code 12. It uses old support libraries and a hard-coded local signing configuration.
- The Gradle wrapper scripts exist, but the wrapper JAR/properties needed to run them are absent. The repository ignores `/gradle/`. JCenter and old Kotlin Android extensions are part of the legacy build setup.
- The source uses GPS tracking and a foreground service, BLE/legacy Bluetooth, spoken speed alerts, and GPX file export. The manifest and implementation use permissions and APIs that require review for current Android releases.
- The local Android SDK has released platforms through API 36.1 and preview platforms through API 37.2. ADB is available; no system Gradle executable is installed.
- There are no existing mobile source test directories. Hardware behavior (GPS and Bluetooth heart-rate equipment) therefore needs physical-device validation in addition to automated checks.
- The Play Console lists the app as removed by Google. Its policy email cites missing or inaccurate target-audience information. The app-content page currently lists nine declarations needing attention: Data safety, Financial features, Health apps, Privacy policy, Sign-in details, Target audience and content, Advertising ID, Government apps, and Location permissions.
- The Play Console account's Android developer verification page shows this package in Draft with zero registered keys. The app's Play App Signing/integrity page did not load during inspection, so enrollment and certificate fingerprints remain unverified.
- The Play release area shows production inactive and initial setup/release tasks incomplete. Any mandatory testing or review requirements presented by the Console must be included in release readiness.

## Architecture and migration approach

### Build and dependency layer

Restore a checked-in Gradle wrapper and establish a supported, pinned Gradle/Android Gradle Plugin/Kotlin/JDK combination. Migrate the module from legacy support libraries to AndroidX, replace deprecated Kotlin synthetic view access with a maintained binding approach, and remove JCenter. Audit every direct and transitive library for current maintenance, Android API compatibility, and security advisories; replace dependencies only where needed to achieve a stable API 36+ build and preserve app behavior.

Keep signing secrets out of tracked Gradle files. The existing keystore remains external under `../android-keys`; commands and reports must not print keystore passwords or private key material. Only public certificate fingerprints and non-secret signing metadata may be used in comparison notes.

### Android platform behavior

Update the manifest and runtime flows to handle:

- Location permission grants/denials and foreground-location service declarations and start restrictions.
- Bluetooth scan/connect permissions on Android 12 and later, while retaining any needed legacy permission declarations for older supported versions.
- Notification permission and notification behavior on Android 13 and later.
- Immutable `PendingIntent` flags where required.
- Edge-to-edge insets and system-bar overlap for API 35/36 behavior, plus modern back navigation.
- GPX export through the Storage Access Framework or another API-compatible user-selected destination instead of relying on broad external-storage permissions.

Retain user-visible GPS tracking and alert behavior. A permission denial must leave the app in a recoverable state and explain which feature is unavailable; it must not crash or silently run a location service without its required access.

### Signing and Play identity

Inspect the keystore metadata in `../android-keys` without exposing secrets. Read Play App Signing and upload-certificate state from the Console and compare public certificate fingerprints. If Play App Signing is enabled, preserve Google's app-signing key; use the existing registered upload key if it matches, or follow Google's upload-key reset process if necessary. If Play App Signing is not enabled, only the original app-signing key can update the existing package; stop and consult the owner if that key cannot be verified. Register the appropriate public key for the package's Android developer verification record.

### Play policy and release

Resolve the historical target-audience issue first. Rebuild each pending declaration from the actual app behavior, current dependency manifest, and privacy policy rather than guessing. Declare no Advertising ID use while the app remains free of ads and ad SDKs. Review Health apps and Location permissions carefully because the app tracks location and can connect to heart-rate equipment; do not present it as a medical product without evidence.

Keep the existing package ID and Play listing. Produce a signed Android App Bundle with a version code above 12. Validate through the available internal/closed testing route and Play pre-launch report, then roll out to production only after the Console shows policy and release readiness. Do not create Google Ads campaigns or incur advertising spend as part of this work.

## Validation and acceptance criteria

### Build and static checks

- A clean checkout can run the checked-in wrapper to assemble and test the app without a machine-specific Gradle installation.
- The app module builds with compile SDK 36 or higher and target SDK 36 or higher, with minSdk kept at 19 unless the owner approves a dependency-driven increase.
- Dependency and manifest review confirms there is no ad SDK or Advertising ID permission unless the user later changes the no-ads decision.
- The release AAB preserves the existing application ID, uses a version code above 12, and is signed by the key accepted for this Play listing.

### Functional checks

- GPS speed, average/maximum speed, spoken alerts, pause/resume, and continued tracking while the activity is backgrounded work on supported API levels.
- Permission grant, denial, revocation, and app-return paths work for location, Bluetooth, and notifications.
- BLE heart-rate equipment can connect, report values, and recover from disconnection on a physical device.
- GPX export produces a readable file at a user-selected destination and remains usable after process restart or sharing.
- No crashes or blocked navigation occur under edge-to-edge layouts, system bars, modern back navigation, or app upgrades.

### Play release checks

- The removal policy issue and all applicable app-content declarations are resolved.
- Package developer verification has a registered public key and the Console reports no unresolved package identity issue.
- Play accepts the AAB on the intended test track; automated pre-launch review has no release-blocking crash or policy issue.
- Production rollout uses the current Play signing arrangement and an owner-reviewed release note and staged rollout setting.

Automated checks cannot prove GPS reception or BLE interoperability across hardware. Final acceptance therefore requires at least one real Android device with GPS and a BLE heart-rate sensor, plus Play Console test-track validation.

## Decisions requiring owner input during execution

- The app's intended target age groups and whether children are part of the intended audience; this cannot be inferred safely from implementation alone and is central to the historical policy removal.
- Data Safety, Health apps, and Location declarations where implementation behavior or an external service's handling is ambiguous.
- Privacy-policy URL/ownership if no valid policy is available in the repository or current Play listing.
- Any minimum-SDK increase required by selected maintained dependencies.
- Any signing-key mismatch that cannot be resolved through the existing Play App Signing upload-key reset flow.
- The staged production rollout percentage and timing, if the Console does not provide an already-approved release setting.

## External platform facts

- Google Play's current submission requirement is target API 36 or higher for app updates; verify the policy again at upload time: <https://support.google.com/googleplay/android-developer/answer/11926878?hl=en>.
- Android 16 targeting changes include enforced edge-to-edge behavior; verify applicable platform migration guidance during implementation: <https://developer.android.com/about/versions/16/behavior-changes-16>.
- Play App Signing separates the upload key from Google's app-signing key and supports an upload-key reset; the actual app enrollment state must be confirmed in this app's Console: <https://support.google.com/googleplay/android-developer/answer/9842756?hl=en>.
