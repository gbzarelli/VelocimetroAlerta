# Play Console Republication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the existing Velocímetro Alerta Play listing and publish a validated update under its existing package and verified signing identity.

**Architecture:** Use the existing Play listing and account. First resolve signing/package verification and policy declarations from evidence; then upload the Android modernization plan's AAB to the required test track, satisfy Play review gates, and submit a staged production release.

**Tech Stack:** Google Play Console, Android developer verification, Play App Signing, Android App Bundle, `keytool`, `jarsigner`, `bundletool`; AdMob/Google Ads remain read-only/out of scope unless the user changes the no-ads decision.

**Spec:** `docs/superpowers/specs/2026-10-14-android-play-republication-design.md`

## Global Constraints

- Preserve package `br.com.helpdev.velocimetroalerta` and the existing Play listing if the original private signing key can be recovered. If it cannot, stop and ask the owner before choosing a new package or signing strategy.
- Keep the app free and without ads; do not create AdMob units, add an ads SDK, declare Advertising ID use, or start a Google Ads campaign.
- Use the released Android 16 target API 36 and compileSdk 36.1 from the Android modernization plan; Android 17/API 37 remains Preview on 2026-10-14 and is only a forward-compatibility test target. Recheck Play policy at submission time.
- Use a `versionCode` greater than 12 and greater than every version code already uploaded to this Play app.
- Never reveal keystore passwords or private key material; compare only public signing-certificate fingerprints.
- Do not change the app-signing key until Play App Signing enrollment and the current app/upload certificates are confirmed.
- Complete all declarations accurately; ask the owner where intended audience, privacy practices, or health-data handling cannot be proven from the source and listing.
- Do not submit a production rollout until the test-track bundle, policy status, signing identity, and owner-reviewed rollout settings are ready.

## Confirmed Console and certificate evidence (2026-10-14)

- Play Console lists production release 4.1 / versionCode 12; the latest uploaded artifact is `12.apk`; the current latest-artifacts view shows APKs 8–12. The next source release is set to versionCode 13 / versionName 4.2.
- Play Console's **Protected with Play** page says “Releases not signed by Play.” The App signing page is an unenrolled setup form. Its advanced options are “Use existing app signing key from another repository” and “Use a new app signing key (this requires ongoing dual releases).” No signing option was selected or saved.
- The version-12 `Distribution APK` downloaded from Play reports signer SHA-256 `fca535607e82e0e55775b4bfd8f578954993acc3096c966e10c9a9258b0095b6`. The local `../android-keys/velocimetro_alerta.jks` certificate SHA-256 is `25a97269bf75ff4b39718ab768530875a9a3ed3ddeab3dd13b03c521d002e938`. The inspected `android-keys` repository has no second Velocímetro keystore or the `fca...` private key. Since this app is not enrolled in Play App Signing, do not use `25a9...` to sign an update for this package.
- Android developer verification now lists both package fingerprints as **Verified**: Play-distribution `fca535...95b6` and local `25a972...e938`. This resolves package-key registration only; it does not make the two private signing keys interchangeable.
- The user confirmed the audience is teens aged 13–17 and adults, not children under 13. This has been declared in Play Console.
- The public Portuguese privacy policy at `https://helpdev.com.br/apps/velocimetro-alerta/privacidade/` returns HTTP 200 and its canonical URL is saved in Play Console. Website PR #13 is merged and CI passed, but the current FTP page still lacks `#english`; the owner must upload the merged static export before the app's English link is released.
- Data Safety is saved in the Publishing overview as “no data collection” and “no data shared.” The resolved release graph and merged manifest have no internet permission, advertising/analytics SDK, or Advertising ID; GPX export is explicitly initiated by the user and handled by the selected Android destination. The Console now shows **one** declaration needing attention: Location permissions. Its declaration still needs the user's location-use video link.
- The app is still removed and no review has been submitted. The locally built version-13 AAB is unsigned because the matching original private signing key is unavailable.
- The store-listing editor initially loaded its assets asynchronously; after loading it showed one app icon, one feature graphic, and eight phone screenshots. Existing metadata is populated.

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

- [x] Resolve the signing-page setup state: Play confirms releases are not signed by Play and the app has not enrolled in Play App Signing; record the original-key and new-key/dual-release options without selecting either.
- [x] Inspect local keystore metadata using `keytool -list -v -keystore ../android-keys/velocimetro_alerta.jks`; enter passwords only at the interactive prompt and keep command logs free of secret values.
- [x] Compare public fingerprints: local JKS `25a97269...d002e938` does not match the version-12 Play signer `fca53560...8b0095b6`; confirm developer-verification status for both.
- [ ] If Play App Signing is active and the local key is not the registered upload key, create a new upload key without overwriting the old file, export only its public PEM certificate, request an upload-key reset in Play Console, and wait for approval before using it.
- [x] Because Play App Signing is not active and the local key does not match the original signer, stop before signing/uploading a release; the `fca...` private key was not found in `../android-keys`.
- [x] Register and verify both fingerprints in Android developer verification, with the user's approval. This does not resolve the app-update signature mismatch.

Expected: signing state is unambiguous and the package verification record shows a registered eligible key. The account is verified, but the release remains blocked because the original `fca...` private key is not in the inspected keys repository. Google says existing apps that self-manage signing must transfer a copy of the original key to enroll in Play App Signing; an app-signing key that remains self-managed cannot be reset if lost. Do not select the “new app signing key / ongoing dual releases” option until the owner chooses that migration path and its compatibility consequences. See [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en-en) and [Publishing issues](https://support.google.com/googleplay/android-developer/answer/9061737?hl=en).

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

- [ ] Read the historical removal notice and current Policy status issue to capture whether the Console requires a policy appeal or a new compliant release; the app still shows the red “App removed” banner, while age declarations are now corrected.
- [x] Recheck Android developer verification: both `fca...` and `25a9...` are now Verified for this package.
- [x] Trace data flow through app code, merged manifest, and resolved release dependencies. No app or SDK network transmission is present; GPX files are exported only after a user action to a destination selected in Android.
- [x] Complete Target audience and content for ages 13–17 and adults; children under 13 are not intended.
- [x] Review and save applicable Data Safety, Financial features, Health apps, Privacy policy, Sign-in details, Target audience, Advertising ID, and Government apps declarations; only Location permissions remains in “Need attention.”
- [x] Complete Data Safety from source and dependency evidence. The preview says no data collection and no sharing with third parties; change is saved but not sent for review.
- [x] Complete Financial features and Government apps based on app functionality; declare no sign-in based on the absence of authenticated content.
- [x] Complete Health apps based on the fitness and heart-rate features without medical diagnosis or treatment claims.
- [x] Verify the public HTTPS privacy policy returns 200, save the canonical trailing-slash URL in Play Console, and add an in-app navigation link. The Portuguese page is live; English translation PR #13 requires merge and manual FTP deployment.
- [x] Declare no ads and no Advertising ID; confirm the merged manifest and dependency graph contain no ad SDK or `com.google.android.gms.permission.AD_ID`.
- [ ] Complete Location permissions using the final foreground-service behavior. The user has no video yet and will send its link later; do not send the declaration for review until it is supplied and saved.
- [x] Save completed declarations. Console shows one remaining attention item, Location permissions. No review was submitted.

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
- [ ] Install the test release from Play on the supported minimum API 23 and released API 36 (plus API 37 Preview only if available); exercise permission grant/denial, GPS tracking with screen off and speech alerts, notification, BLE heart-rate and classic RFCOMM sensor connections including disconnection recovery, GPX export/share, large-screen rotation/resizing where available, and upgrade from the prior installed package if available.
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

- Android modernization targets released API 36 (Android 16) and compiles against API 36.1; Google Play requires target API 36 or higher for updates as of Aug 31, 2026. Android 17/API 37 is Preview on 2026-10-14: <https://support.google.com/googleplay/android-developer/answer/11926878?hl=en>.
- Android 17 changes relevant to large-screen orientation, background speech, and Bluetooth socket disconnects: <https://developer.android.com/about/versions/17/behavior-changes-17>.
- Play App Signing separates Google's app-signing key from the upload key: <https://support.google.com/googleplay/android-developer/answer/9842756?hl=en>.
- Package verification requires registering the package name and public signing-certificate fingerprint: <https://developer.android.com/developer-verification/guides/google-play-console>.
- Data Safety disclosures include data transmitted by SDKs and third-party libraries: <https://support.google.com/googleplay/android-developer/answer/10787469?hl=en>.
- Location access through a foreground service must be tied to a user-initiated core feature and accurately declared: <https://support.google.com/googleplay/android-developer/answer/16558241?hl=en>.
