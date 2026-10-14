# Android dependency audit

## Task 1 baseline graph

Resolved `:mobile:debugRuntimeClasspath` and `:mobile:releaseRuntimeClasspath` with Gradle 9.6.0 and AGP 9.4.1 after restoring the wrapper. Both configurations resolve the same 27 unique coordinates. `minSdk` remains 19: every resolved Android AAR that declares a minimum requires API 14 or less, and the Java archives have no Android manifest minimum. `:mobile:checkDebugAarMetadata` passed with compile API 37.

The list below records every coordinate in that graph. Android Support and Architecture components are grouped by version because their POMs all declare Apache-2.0 and their Android AAR manifests have the same minimum. `stax:stax` does not declare a license in its POM and is only pulled by Simple XML; the Simple XML parser will be removed in Task 5.

| Resolved coordinates | License and Android minimum | Maintenance, security, and disposition |
| --- | --- | --- |
| `com.android.support:animated-vector-drawable:27.1.1`; `com.android.support:appcompat-v7:27.1.1`; `com.android.support:design:27.1.1`; `com.android.support:recyclerview-v7:27.1.1`; `com.android.support:support-annotations:27.1.1`; `com.android.support:support-compat:27.1.1`; `com.android.support:support-core-ui:27.1.1`; `com.android.support:support-core-utils:27.1.1`; `com.android.support:support-fragment:27.1.1`; `com.android.support:support-media-compat:27.1.1`; `com.android.support:support-v4:27.1.1`; `com.android.support:support-vector-drawable:27.1.1`; `com.android.support:transition:27.1.1` | Apache-2.0; AAR minimum API 14 | Legacy Support Library line is superseded by AndroidX and no longer receives feature updates. No matching OSV advisories in this scan. Replace with maintained AndroidX/Material equivalents in Task 2. |
| `com.android.support.constraint:constraint-layout:1.1.3`; `com.android.support.constraint:constraint-layout-solver:1.1.3` | Apache-2.0; layout AAR minimum API 9; solver is a Java archive | Old Support coordinate. No matching OSV advisories. Replace with AndroidX ConstraintLayout in Task 2. |
| `android.arch.core:common:1.1.0`; `android.arch.core:runtime:1.1.0`; `android.arch.lifecycle:common:1.1.0`; `android.arch.lifecycle:livedata-core:1.1.0`; `android.arch.lifecycle:runtime:1.1.0`; `android.arch.lifecycle:viewmodel:1.1.0` | Apache-2.0; AAR minimum API 14 where an AAR is present; common libraries are Java archives | Legacy Architecture Components superseded by AndroidX Lifecycle. No matching OSV advisories. Replace through the AndroidX migration in Task 2. |
| `org.jetbrains.kotlin:kotlin-stdlib:2.2.10`; `org.jetbrains:annotations:13.0`; `com.google.errorprone:error_prone_annotations:2.48.0` | Apache-2.0; Java archives with no Android manifest minimum | Kotlin stdlib is added by AGP built-in Kotlin; annotations and Error Prone annotations arrive transitively. No matching OSV advisories. Keep unless the final graph changes their versions or origin. |
| `com.google.code.gson:gson:2.14.0` | Apache-2.0; Java archive with no Android manifest minimum | Kept for the VAM sensor JSON until Task 5. The original 2.8.2 resolved in source was affected by [GHSA-4jrv-ppp4-jm57](https://osv.dev/vulnerability/GHSA-4jrv-ppp4-jm57), fixed in 2.8.9. Upgraded to 2.14.0, the latest stable version in Maven Central metadata at audit time; no matching OSV advisory for 2.14.0. |
| `org.simpleframework:simple-xml:2.7.1` | Apache-2.0; Java archive with no Android manifest minimum | Legacy serializer is used by GPX code. No matching OSV advisory. Replace with Android `XmlSerializer`/`XmlPullParser` in Task 5. |
| `stax:stax:1.2.0` | License not declared in its published POM; Java archive with no Android manifest minimum | Transitive only through Simple XML; no matching OSV advisory. Remove with Simple XML in Task 5 rather than ship a transitive dependency with unclear license metadata. |

`OSV` queried all 27 resolved coordinates after the Gson update; it returned no matching advisories. The earlier source version Gson 2.8.2 did match the advisory above, so it is not retained.

## App network and advertising inventory

Source and the resolved runtime graph contain no ad, analytics, attribution, or IP networking SDK. The manifest does not request `INTERNET`, `AD_ID`, or `ACCESS_LOCAL_NETWORK`. The app uses Bluetooth socket APIs for the VAM sensor; this is not local IP network access. No SDK in this graph initiates network traffic on behalf of the app. Recheck the final merged manifest and graph in Task 7 after AndroidX migration.

## Audit sources and method

- Gradle reports: `./gradlew :mobile:dependencies --configuration debugRuntimeClasspath` and `./gradlew :mobile:dependencies --configuration releaseRuntimeClasspath`.
- AAR minimums: inspected `AndroidManifest.xml` in the resolved Android AARs; `:mobile:checkDebugAarMetadata` passed at API 37.
- Licenses: published Maven POM license declarations; Gson's published POM declares Apache-2.0. The Simple XML transitive `stax:stax` POM does not declare a license.
- Security: OSV query batch for every resolved group, artifact, and version.
- Android Support to AndroidX migration: <https://developer.android.com/jetpack/androidx/migrate>.

## Final release graph

Update this document after Tasks 2 and 5 with the final debug and release graphs. Record each coordinate, resolved version, license, Android minimum, maintenance status, advisory result, local-network behavior, and keep/upgrade/replace decision. Acceptance requires no JCenter, Simple XML/StAX, advertising SDK, `AD_ID`, or unexplained `ACCESS_LOCAL_NETWORK` permission in the release graph or merged manifest.
