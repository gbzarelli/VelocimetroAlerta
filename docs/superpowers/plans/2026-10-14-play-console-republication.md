# Play Console Republication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the existing Velocímetro Alerta Play listing and publish a validated update under its existing package and verified signing identity.

**Architecture:** Use the existing Play listing and account. First resolve signing/package verification and policy declarations from evidence; then upload the Android modernization plan's AAB to the required test track, satisfy Play review gates, and submit a staged production release.

**Tech Stack:** Google Play Console, Android developer verification, Play App Signing, Android App Bundle, `keytool`, `jarsigner`, `bundletool`; AdMob/Google Ads remain read-only/out of scope unless the user changes the no-ads decision.

**Spec:** `docs/superpowers/specs/2026-10-14-android-play-republication-design.md`

## Global Constraints

- Preserve package `br.com.helpdev.velocimetroalerta` and the existing Play listing.
- Keep the app free and without ads; do not create AdMob units, add an ads SDK, declare Advertising ID use, or start a Google Ads campaign.
- Use the Android modernization plan's API 37 Android 17 release; Play's current update floor is API 36+, so recheck policy at submission time.
- Use a `versionCode` greater than 12 and greater than every version code already uploaded to this Play app.
- Never reveal keystore passwords or private key material; compare only public signing-certificate fingerprints.
- Do not change the app-signing key until Play App Signing enrollment and the current app/upload certificates are confirmed.
- Complete all declarations accurately; ask the owner where intended audience, privacy practices, or health-data handling cannot be proven from the source and listing.
- Do not submit a production rollout until the test-track bundle, policy status, signing identity, and owner-reviewed rollout settings are ready.

## Review Focus

- Intended audience and whether children are part of it cannot be inferred from code; collect the owner's answer before submitting Target audience and Families declarations.
- Data Safety depends on off-device transmission by the app and every SDK; inspect permissions, network calls, manifests, and resolved dependencies before answering.
- GPS continues during an explicitly started ride when the screen is off; accurately classify that foreground-service use and do not claim background access is absent without reviewing the final implementation.
- The saved JKS may be the upload key or the original app-signing key; compare fingerprints and stop if Play enrollment or certificate ownership is unclear.
- Play may require additional closed-test participants/duration and a rollout percentage; verify current Console gates and ask the owner for decisions that need people or release timing.

---

### Task 1: Verify Play App Signing and local keystore identity

**Files and records:**
- Read: `../android-keys/velocimetro_alerta.jks` metadata only
- Read: Play Console **App integrity / Play App Signing** for `br.com.helpdev.velocimetroalerta`
- Read: account **Android developer verification** package record
- Keep temporary certificates outside the repository, for example `/tmp/velocimetro-alerta-cert.pem`

**Interfaces:**
- Produces: written non-secret record of Play App Signing enrollment, app-signing certificate SHA-256, upload-certificate SHA-256, local-keystore SHA-256, and required package-verification key.
- Consumes: existing package identity and signing safeguards from the spec.

- [ ] Open the app's Play App Signing/App integrity page and record whether Play App Signing is enrolled and the SHA-256 fingerprints for the app-signing and upload certificates.
- [ ] Inspect local keystore metadata using `keytool -list -v -keystore ../android-keys/velocimetro_alerta.jks`; enter passwords only at the interactive prompt and keep command logs free of secret values.
- [ ] Compare the local certificate fingerprint with the Play upload certificate; separately compare the Console app-signing fingerprint with the account's package verification key list.
- [ ] If Play App Signing is active and the local key is not the registered upload key, create a new upload key without overwriting the old file, export only its public PEM certificate, request an upload-key reset in Play Console, and wait for approval before using it.
- [ ] If Play App Signing is not active and the local key does not match the original app-signing certificate, stop before generating or uploading a bundle and ask the owner to resolve the original key.
- [ ] Register the SHA-256 fingerprint of the certificate that signs APKs delivered by Play (the Play app-signing certificate), as indicated by the verification page; add the upload certificate only if the Console explicitly requests it. Confirm package status is registered rather than Draft.

Expected: signing state is unambiguous, local release signing can use the accepted upload key, and the package verification record shows a registered eligible key. If the Play signing page remains unavailable, stop and ask for account-owner assistance instead of guessing.

Official guidance: [Play App Signing and upload-key reset](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en) · [Register packages in Play Console](https://developer.android.com/developer-verification/guides/google-play-console).

### Task 2: Resolve the historical removal and app-content declarations

**Files and records:**
- Read: `mobile/src/main/AndroidManifest.xml` and final merged release manifest
- Read: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/` and resolved Gradle dependency report
- Read: `docs/android-dependency-audit.md`
- Read/update: Play Console **Policy status**, **App content**, and **Privacy policy** fields
- Read: current public store listing and any privacy-policy URL already linked there
- Modify if no in-app policy link exists: `mobile/src/main/res/xml/configs.xml`, localized `mobile/src/main/res/values/strings.xml` and `mobile/src/main/res/values-pt/strings.xml`, and `mobile/src/main/java/br/com/helpdev/velocimetroalerta/ConfigActivityFragment.kt`

**Interfaces:**
- Produces: all applicable app-content declarations saved and no unresolved policy issue blocking the listing.
- Consumes: final no-ads SDK inventory and permission/data flow from the Android modernization plan.

- [ ] Open the historical policy email and current Policy status issue; record the exact remediation text and whether the Console requires a policy appeal or a new compliant release.
- [ ] Recheck Android developer verification immediately: the package record was Draft with zero keys during inspection, and Google's September 30, 2026 package-registration date has passed. Record the Console's current status and any grace/remediation instructions before starting a release.
- [ ] Trace each data type from app code through all resolved SDKs to determine whether it is transmitted off device, stored locally, shared, or used only for the user-requested ride. Create a short evidence table for the Data Safety answer.
- [ ] Ask the owner the intended age groups and whether the app is directed to children before completing Target audience and content; if no answer is available, save that form as a draft and do not guess.
- [ ] Review all nine pending declarations: Data safety, Financial features, Health apps, Privacy policy, Sign-in details, Target audience and content, Advertising ID, Government apps, and Location permissions.
- [ ] Complete Data Safety from the source/dependency evidence; complete Financial features and Government apps from actual app functionality; declare no sign-in only after confirming no authenticated content exists.
- [ ] Complete Health apps based on fitness and heart-rate features, without claiming medical diagnosis or treatment. Ask the owner if the data's handling, health claims, or purpose remains ambiguous.
- [ ] Confirm an active, public, non-geofenced, non-editable privacy-policy URL (not a PDF). If none is present in the repository/listing or it does not describe location, GPX, heart-rate data, local/off-device processing, and deletion/retention, ask the owner for the correct policy before submitting. Add the same verified URL to Play Console and an in-app settings link if no working link exists; open that link once in the installed app to verify it reaches the intended policy.
- [ ] Declare no ads and no Advertising ID while the app remains free of ad code and ad SDKs; verify the merged manifest has no `com.google.android.gms.permission.AD_ID`.
- [ ] Complete Location permissions using the final foreground-service behavior: tracking is user initiated, remains visible through an ongoing notification, and stops when the user stops the ride. Do not declare or request `ACCESS_BACKGROUND_LOCATION` unless the final code and Console policy require it.
- [ ] Save each declaration and confirm the Console status changes from attention required to complete; read any new policy response before proceeding.

Expected: every applicable form is complete and consistent with the final app and its privacy policy; no declaration is submitted with a guessed audience or data flow. Google requires Data Safety and Health apps declarations for apps on test tracks as well as production; see [Data Safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en) and [Health apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291?hl=en).

### Task 3: Confirm listing, pricing, version, and test-track requirements

**Files and records:**
- Read/update: Play Console **Store listing**, **Pricing and distribution**, **App content**, and **Testing** pages
- Modify: `mobile/build.gradle` (release `versionCode`/`versionName` only after reading Console state)

**Interfaces:**
- Produces: confirmed free pricing, listing/privacy-policy consistency, a selected available test track, and an unused version code.
- Consumes: unchanged Play listing/package and all applicable declarations from Task 2.

- [ ] Confirm the current Play listing remains free; make no conversion to paid and no monetization changes.
- [ ] Check every uploaded, draft, rejected, and testing release for the highest version code. Set the next release `versionCode` to `max(13, highest Play versionCode + 1)` and `versionName` to `4.2` unless the current listing already shows a later version.
- [ ] Review the store title, short/full description, screenshots, category, contact details, and privacy-policy link for accuracy; update only fields made stale by the modernized app or required by policy.
- [ ] Inspect the incomplete setup and closed-testing checklist currently shown by the Console; record exact tester-count, duration, and review requirements instead of assuming a generic gate.
- [ ] If a closed test requires participants, invite only owner-approved testers and ask the owner to supply/approve any missing participant list; verify the required active days before continuing.
- [ ] Confirm an internal or closed testing track is available and that its country/device targeting includes the intended validation devices.

Expected: price remains free, version code is unused and greater than all existing uploads, listing declarations match the app, and test-track gates are documented.

### Task 4: Build, sign, and validate the test-track bundle

**Files and records:**
- Read: `docs/superpowers/plans/2026-10-14-android-modernization.md`
- Create outside source control: `mobile/build/outputs/bundle/release/mobile-release.aab`
- Read/update: Play Console **Internal testing** or the currently required testing track

**Interfaces:**
- Consumes: passing implementation and physical-device checks from the Android modernization plan, the verified upload key from Task 1, completed declarations from Task 2, and the version code from Task 3.
- Produces: a Play-accepted test release with pre-launch results and no release-blocking issue.

- [ ] Build `:mobile:bundleRelease` using the verified upload key configured through ignored local properties/environment; do not pass passwords on the command line or commit signing material.
- [ ] Run `bundletool validate --bundle=mobile/build/outputs/bundle/release/mobile-release.aab` and `jarsigner -verify -verbose -certs mobile/build/outputs/bundle/release/mobile-release.aab`; use `keytool -printcert -jarfile` to compare the AAB upload signature with the expected upload certificate.
- [ ] Confirm AAB package ID, version code, target SDK, min SDK, permissions, and lack of ad SDK/Advertising ID using `bundletool dump manifest` or Android SDK inspection tools.
- [ ] Upload the AAB to the Console-selected test track, complete the release notes in Portuguese, and start the Play review/pre-launch report.
- [ ] Install the test release from Play on one supported older API and API 37; exercise permission grant/denial, GPS tracking with screen off and speech alerts, notification, BLE heart-rate and classic RFCOMM sensor connections including disconnection recovery, GPX export/share, large-screen rotation/resizing where available, and upgrade from the prior installed package if available.
- [ ] Review pre-launch results and policy feedback. Fix any release-blocking issue in the Android modernization plan, increase `versionCode`, rebuild, and re-upload to the same test track.

Expected: Play accepts the signed AAB, the test release installs from Play, pre-launch review has no blocking crash/policy issue, and tested flows match the Android plan's acceptance criteria.

### Task 5: Submit the production release and verify availability

**Files and records:**
- Read/update: Play Console **Production** release, rollout configuration, and release notes
- Use: exact AAB approved in Task 4; do not rebuild after test approval unless a new version code and test cycle are used

**Interfaces:**
- Consumes: resolved policy/app-content state, registered package key, verified signing arrangement, and successful Play test release.
- Produces: reviewed production release under the original free listing and package.

- [ ] Before selecting Submit for review, show the owner the final version, release notes, Play pre-launch summary, signing identity, and rollout percentage; ask for the rollout percentage/timing if not already selected.
- [ ] Submit the approved release to Production using the same signed AAB that passed testing; preserve the existing free pricing and do not enable ads or external ad campaigns.
- [ ] Monitor review status and respond to any new policy feedback using the evidence gathered in Tasks 1–2; if Play requires a changed bundle, repeat Task 4 with a higher version code.
- [ ] After approval, confirm the Play listing reports the app available, the new version code is live, the package remains `br.com.helpdev.velocimetroalerta`, and the listing remains free and ad-free.
- [ ] Install/update from the public Play listing on a supported device and verify launch, GPS tracking, notification, sensor settings, and GPX export once more.

Expected: production is approved and available from the existing listing; the rollout follows the owner-reviewed percentage and the live metadata agrees with the released bundle.

## External platform references

- Android modernization targets API 37 (Android 17); Play update submissions currently require API 36 or higher, so verify at upload time: <https://support.google.com/googleplay/android-developer/answer/11926878?hl=en>.
- Android 17 changes relevant to large-screen orientation, background speech, and Bluetooth socket disconnects: <https://developer.android.com/about/versions/17/behavior-changes-17>.
- Play App Signing separates Google's app-signing key from the upload key: <https://support.google.com/googleplay/android-developer/answer/9842756?hl=en>.
- Package verification requires registering the package name and public signing-certificate fingerprint: <https://developer.android.com/developer-verification/guides/google-play-console>.
- Data Safety disclosures include data transmitted by SDKs and third-party libraries: <https://support.google.com/googleplay/android-developer/answer/10787469?hl=en>.
- Location access through a foreground service must be tied to a user-initiated core feature and accurately declared: <https://support.google.com/googleplay/android-developer/answer/16558241?hl=en>.
