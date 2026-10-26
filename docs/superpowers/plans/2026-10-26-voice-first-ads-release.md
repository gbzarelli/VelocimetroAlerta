# Velocímetro Alerta 4.5 Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to complete this plan task by task. Steps use checkbox syntax.

**Goal:** Ship the requested GPS, permission, ads, activity-management, and store-copy updates in Velocímetro Alerta 4.5.

**Architecture:** Keep the existing Android/Kotlin/XML structure. Add a small GPS-precision policy, a separate first-launch permission callback, repository operations for rename/delete, and an AdMob/UMP manager that gates screen ads. Update Play and privacy copy to match actual behavior.

**Tech Stack:** Kotlin, Android SDK 36, Google Mobile Ads SDK 25.5.0, UMP SDK 4.0.0, XML, Gradle.

**Spec:** `docs/superpowers/specs/2026-10-26-voice-first-ads-release-design.md`

## Global Constraints

- Package remains `br.com.helpdev.velocimetroalerta.v2`; release is versionCode 6 / versionName 4.5.
- Keep minSdk 24 and targetSdk 36.
- No ads on the live tracking screen.
- Debug builds use Google test ad units; release builds use the units created in AdMob for the `.v2` Play listing.
- Do not publish the live privacy-policy page until the owner approves that public legal notice.

## Review Focus

- No fix or an invalid GPS accuracy must stay neutral; exactly 15m remains acceptable.
- Denying the first-launch location prompt must not start tracking and must still allow a later Play retry.
- Invalid IDs and malformed saved activity directories must never rename or delete another activity.
- Consent unavailable or ads offline must not block Settings or tracking.
- Release manifest and store declarations must match the final AdMob SDK and IDs.

---

### Task 1: GPS precision

**Files:** Create `mobile/src/main/java/br/com/helpdev/velocimetroalerta/gps/GpsPrecisionStatus.kt`; test `mobile/src/test/java/br/com/helpdev/velocimetroalerta/gps/GpsPrecisionStatusTest.kt`; update service and main fragment.

- [x] Write tests for unknown, 0m, 15m, above 15m, negative, and NaN accuracy.
- [x] Run `./gradlew :mobile:testDebugUnitTest --tests '*GpsPrecisionStatusTest'`; expected: the new classification API is missing.
- [x] Implement the policy and render UNKNOWN neutrally, GOOD black, and POOR red; remove the stale TextView tag state.
- [x] Run the focused test and `./gradlew :mobile:testDebugUnitTest`; expected: both pass.

### Task 2: First-launch location prompt

**Files:** update `MainActivity.kt`, permission policy/tests, and `TrackingPermissionFlowTest.kt`.

- [x] Add an instrumentation test proving the first launch requests location but does not start tracking; prove denial leaves Play retry available.
- [x] Run the focused instrumentation test on API 24; expected: the current app does not request permission until Play is tapped.
- [x] Add a one-time launch permission launcher with no service-start callback; keep the existing Play flow as the only path that starts tracking.
- [x] Run the focused permission instrumentation tests on API 24 and API 36; expected: launch prompt, retry, and existing notification/Bluetooth behavior pass.

### Task 3: Saved activity rename and delete

**Files:** update `activities/ActivityRepository.kt`, repository tests, `ActivityDetailActivity.kt`, detail menu/layout strings, and `ActivityDetailTest.kt`.

- [x] Add repository tests for rename, GPX track-name synchronization, delete, missing IDs, and traversal-like IDs.
- [x] Run the focused repository tests; expected: rename/delete operations are missing.
- [x] Implement safe repository operations and detail-screen edit/delete actions; require confirmation before deletion.
- [x] Run repository and detail instrumentation tests on API 24; expected: names persist and deletion returns to the refreshed activity list.

### Task 4: AdMob and consent

**Files:** update version catalog, `mobile/build.gradle`, manifest and Settings layout/activity; create the application consent/ads manager and debug ad ID resources; update My Activities interstitial handling.

- [x] Add tests for ad-unit selection and test that Settings exposes the banner container and consent options entry point.
- [x] Run focused tests; expected: the new ad policy/container is not present.
- [x] Integrate GMA 25.5.0 and UMP 4.0.0, use the registered app ID and production unit IDs in release, test IDs in debug, and cap content at rating T.
- [x] Show the interstitial only when My Activities is entered; load/destroy the Settings adaptive banner with the activity lifecycle.
- [x] Run focused tests, `make build`, and inspect merged debug/release manifests; expected: debug uses test units and both variants include the app ID/required SDK permissions.

### Task 5: Listing, privacy copy, and release

**Files:** update `README.md`, app version, site privacy-policy source at `helpdev.com.br`, Play Console listing and ads/data declarations.

- [x] Rewrite README English/Portuguese product copy around spoken alerts; verify the copy accurately describes time/distance intervals and existing activity features.
- [x] Rewrite English and pt-BR Play descriptions and release notes; declare ads and update Data Safety to match the final SDK.
- [x] Update the local privacy-policy source in both languages, run the site's production build, and leave the live policy unpublished pending owner approval.
- [x] Set version 4.5 / code 6; run `make test`, `make test-e2e`, build the signed AAB, and verify package, version, target SDK, merged permissions, and signature.
- [ ] After the required privacy-policy publication approval, complete Play Console validation and submit the production release; verify the Console shows it submitted for review or rolled out.
