# Velocímetro Alerta 4.5 Release Design

## Goal

Make spoken activity alerts the clear product promise, fix GPS precision feedback, request location on first launch without starting tracking, add the requested ads and saved-activity management, and prepare version 4.5 for the active Play listing.

## Decisions

- Keep package `br.com.helpdev.velocimetroalerta.v2`; release as `versionCode = 6`, `versionName = 4.5`.
- Treat no GPS fix as neutral, accuracy from 0 through 15 metres as acceptable, and accuracy above 15 metres as poor. Keep acceptable precision black and poor precision red.
- Request coarse and precise location once on the first app launch. This permission flow never starts the tracking service. If permission is denied, the Play action remains able to request it again through the existing tracking flow.
- Show a Google Mobile Ads interstitial when the user opens My Activities and an anchored adaptive banner at the bottom of Settings. Do not show ads on the live tracking screen.
- Use UMP consent before requesting ads, expose privacy options when UMP requires it, cap ad content at the teen rating, and use Google's test ad unit IDs in debug builds. Release builds use the AdMob units created for the active `.v2` listing.
- Renaming a saved activity updates both its summary and GPX track name. Deleting requires an in-app confirmation and removes the activity directory.
- Rewrite the English and Brazilian Portuguese store descriptions and README introduction so spoken alerts by time or distance are the first feature users see.
- Update the linked privacy-policy source and Play declarations to describe the ads SDK. The currently live policy says the app has no ads; publishing its legal-policy revision is a separate public action and needs owner confirmation.

## Current evidence and constraints

- Play Console shows the active `.v2` listing in Production at 100% rollout, last released October 17, 2026. The project builds version 4.4 / code 5.
- AdMob has no Velocímetro Alerta app yet. The account requires review for new store-linked apps, and may limit ad serving until review finishes.
- The existing privacy policy is bilingual but explicitly says there are no ads or advertising ID.
- Keep Android minSdk 24 and targetSdk 36.

## Acceptance

- Focused unit and instrumentation tests cover GPS states, initial permission behavior, activity rename/delete, and ad placement/lifecycle.
- `make test`, `make test-e2e`, debug build, and signed release bundle validation pass.
- The Play listing and data declarations match the shipped SDK and features.
- Version 4.5 is submitted to the existing `.v2` production listing after required public policy approval and Console checks.
