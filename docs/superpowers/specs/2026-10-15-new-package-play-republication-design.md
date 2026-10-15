# Velocímetro Alerta v2: New Play Listing Design

## Status

Approved by the owner on 2026-10-15 and used to create the new Google Play listing. This document supersedes the previous preserve-package decision for Play republication. The release is submitted and currently **In review**; the app is not yet public. The execution record and remaining gates are maintained in `docs/superpowers/plans/2026-10-15-new-package-android-play-republication.md`.

## Goal

Publish the modernized Velocímetro Alerta as a new Google Play app after confirming that the original self-managed signing key for package `br.com.helpdev.velocimetroalerta` cannot be recovered. Keep the existing brand and core functionality while establishing a valid, maintainable package and signing identity.

## Approved product and identity decisions

- Public app title: `Velocímetro Alerta`.
- New package / Gradle `applicationId`: `br.com.helpdev.velocimetroalerta.v2`, subject to Google's package-availability check when creating the listing. Package names are permanent once registered; if this exact identifier is unavailable, stop and ask the owner to choose another one.
- Keep the source `namespace` as `br.com.helpdev.velocimetroalerta`. Kotlin/Java packages, imports, generated `R` references, and resource paths remain stable; only package-facing integrations and links that must resolve to the installed application need to be updated.
- New listing is free and has no ads. The source/dependency audit found no ad SDK, ad unit, Advertising ID permission, or AdMob app registration for the original package. Do not create an AdMob app/unit or declare ad use.
- Keep `versionName` 4.2 and increase `versionCode` for each replacement. The initial code-1 AAB was rejected because its `minSdk` was below Play automatic protection's API 24 floor; code 2 was validated in the internal-test draft, and production was submitted with code 3. The current build configuration is code 3, `minSdk` 24, and target SDK 36.
- Enroll the new listing in Play App Signing. Generate a distinct upload keystore for this identity at `/Volumes/gbz-ext/repositories/helpdev/android-keys/velocimetro_alerta_v2_upload.jks`, with its password in the neighboring hidden `.velocimetro_alerta_v2_upload.pass` file, following the existing `android-keys` naming pattern. Set both files to owner-only permissions and never stage or commit them. Let Google manage the app-signing key for Play distribution. Do not reuse the old local JKS automatically.
- Remove the mismatched local keystore `../android-keys/velocimetro_alerta.jks` from the `android-keys` folder as requested by the owner. Preserve every other key file and pre-existing change in that repository; do not rewrite its Git history.
- Preserve the old Play Console listing as a record. It is currently marked **App removed** and is not publicly available. Do not delete it; no deletion is needed to create the new package. Only unpublish it if a later Console check shows that it is publicly available.
- The owner has approved the audience as teenagers (13–17) and adults, registering public signing fingerprints with Android developer verification for the prior package, and retaining the current ad-free model. For the new package, the public certificate requested by Google's current verification flow will be registered as a required release step; no private key material will be uploaded.

## User and data impact

The new package is a separate Android application identity. Existing users will not receive an in-place update from the old package, and Android will keep the two apps' private local data separate. GPX files already exported by a user remain user-managed files, but app settings and unexported local activity data do not transfer automatically. The new Play listing needs its own install URL and package-verification record.

The same public title may be used for the new listing. Because its functionality is similar to the removed listing, prepare a current store description and media that clearly identify the release as the supported successor. Google Play retains review discretion under its repetitive-content and policy rules; the historical listing is already removed, which avoids two publicly available listings during the transition, but approval is not guaranteed.

## Android project changes required by the new identity

- Change `mobile/build.gradle` `applicationId` to `br.com.helpdev.velocimetroalerta.v2`; keep `namespace` and source package declarations unchanged.
- Update package-specific destinations and links. In particular, change `mobile/src/main/res/xml/configs.xml`'s `android:targetPackage` for `ConfigHRZActivity`, and update the Play Store package link in `README.md` after confirming the new package.
- Keep app-internal service action values stable unless inspection finds an external consumer that requires a package-specific value. They are string actions on an explicit service intent and are not themselves the installed package identity.
- Do not include the inactive `:wear` module as part of this release. It is not included in `settings.gradle`; any future Wear OS publication is a separate product decision.
- Use `minSdk` 24 to retain Play automatic protection, which the Console requires for this listing. Keep target SDK 36 and the released compile API 36.1.
- The submitted production AAB reports package `br.com.helpdev.velocimetroalerta.v2`, `versionCode` 3, `minSdk` 24, and target SDK 36, and contains no ads SDK or Advertising ID permission.

## Signing and Android developer verification

The Play Console reported that the original app is not enrolled in Play App Signing. The v12 APK downloaded from Play is signed with public certificate SHA-256 `fca535607e82e0e55775b4bfd8f578954993acc3096c966e10c9a9258b0095b6`; the available local `../android-keys/velocimetro_alerta.jks` has a different certificate, `25a97269bf75ff4b39718ab768530875a9a3ed3ddeab3dd13b03c521d002e938`. The original private key was not found in the inspected keys directory. Google guidance for an unenrolled app with a lost signing key is to publish a new app with a different package; the Console's dual-release option does not restore the old package signing identity.

For the new package, use a newly generated upload key and enroll in Play App Signing. Store the `.jks` and hidden `.pass` files in the separate `android-keys` repository according to the owner's instruction, with mode `600`; never stage or commit either file. Complete package verification with the public certificate required by the Console for the new package, checking which certificate(s) the current flow requests after Play App Signing enrollment. Never upload a private key or keystore to Android developer verification. Record fingerprints only; keep passwords out of source code and logs.

## Play listing and declarations

- Create a new app entry with the title `Velocímetro Alerta`, package `br.com.helpdev.velocimetroalerta.v2`, free pricing, Portuguese (Brazil) as the primary store language, and the existing owner contact details after checking they remain current.
- Reuse the live privacy page `https://helpdev.com.br/apps/velocimetro-alerta/privacidade/` and confirm its HTTPS response and Portuguese/English anchors before saving it on the new entry.
- Declarations for the new package were completed from the final manifest, dependencies, and behavior: audience 13+ (including 13–17 and adults), no ads, no Advertising ID, Data Safety, Health apps, and Location permissions. The owner-provided unlisted video is linked in the Location declaration: `https://www.youtube.com/shorts/Se3rfMIysfY`.
- The old entry has four unsent changes (audience, privacy policy, Data Safety, and Health declaration) and a remaining Location declaration. Do not submit old-entry changes as a substitute for completing the new entry.
- Refresh store assets before review. The old listing has no icon, feature graphic, or phone screenshots in the Console, and the repository screenshots are from 2018. Use current screenshots matching the modernized app; do not invent product UI or claim unsupported features.
- Current Console records show the old app as removed. Keep that record intact while the new listing is reviewed. If the new listing is approved, no additional old-app deletion action is needed. If Play later requires the old entry to be unpublished, use the reversible unpublish action rather than attempting permanent deletion.

## Release sequence and gates

1. Android modernization and release fixes are implemented. Build, unit tests, hermetic permission tests, and emulator coverage are recorded in the implementation plan; physical GPS/Bluetooth/screen-off speech validation remains a follow-up.
2. The package was available and registered permanently as `br.com.helpdev.velocimetroalerta.v2`.
3. A distinct upload key was generated and stored outside the app repository; Play App Signing and package verification are configured.
4. The listing and policy, privacy, audience, Data Safety, Health, and Location declarations are saved, including the owner-provided location video.
5. Current icon, feature graphic, screenshots, and Portuguese release notes are saved in the listing.
6. Code 2 was validated in the internal-test draft. It was not rolled out because no tester roster was provided; Play install and pre-launch results remain unavailable.
7. Production code 3 was submitted on 2026-10-15 after preview validation. The submission is currently **In review**; verify public availability only after approval.

## Acceptance criteria

- The new listing uses `br.com.helpdev.velocimetroalerta.v2`, the public title `Velocímetro Alerta`, free pricing, and no ads.
- The old package's listing remains preserved and is not represented as updatable by the new package.
- A new upload key is used only for uploads, Play App Signing manages the distribution key, and the new package's Android developer verification is complete with the Console-requested public certificate.
- The new release AAB has the expected package, SDK levels, version code, permissions, and no ads SDK or Advertising ID permission.
- All applicable new-listing declarations and media are complete; the location video is supplied and accepted before requesting review.
- A Play test release and pre-launch report remain pending because the code-2 internal-test draft was not rolled out. Local emulator results are not a substitute for Play-distributed validation; physical GPS/Bluetooth/screen-off speech scenarios also remain pending.
- The production listing is **In review** and not yet publicly available. After approval, verify its store URL and install behavior. The removed old listing remains intact unless Google requires reversible unpublishing.

## Known constraints and risks

- The exact `.v2` package may already be claimed; package availability must be confirmed before creating the permanent listing.
- Users cannot update from the old package and local app-private data cannot be migrated through a new package.
- Play can request additional evidence or reject substantially similar content despite the new-package path being Google's documented route for a lost key.
- Production review is pending. Google may request additional policy evidence or a replacement bundle.
- A prior aggregate API 35 instrumentation run had 7 of 18 failures, mostly due permission-state setup; the focused regressions passed individually. Later hermetic matrix results and currently available emulator APIs are documented in the implementation plan.

## External platform references

- Google's lost-key guidance for an app not enrolled in Play App Signing: <https://support.google.com/googleplay/android-developer/answer/9061737?hl=en>.
- Creating an app and the permanent package-name constraint: <https://support.google.com/googleplay/android-developer/answer/9859152?hl=en>.
- Play App Signing and upload keys: <https://support.google.com/googleplay/android-developer/answer/9842756?hl=en>.
- Google Play repetitive-content policy: <https://support.google.com/googleplay/android-developer/answer/9899034?hl=en>.
- Unpublishing an app: <https://support.google.com/googleplay/android-developer/answer/9859350?hl=en>.
- Android developer verification for Play Console: <https://developer.android.com/developer-verification/guides/google-play-console>.
