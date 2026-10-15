# Android dependency audit

> **Release configuration update (2026-10-15):** This audit established that Material Components requires API 23. The new Play listing uses `minSdk` 24 because Play automatic protection rejects bundles below API 24; the owner approved this minimum. The dependency compatibility findings remain valid at the higher floor.

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

## Task 2 AndroidX graph

Debug and release runtime classpaths resolve the same 75 artifact coordinates after the AndroidX migration. Three additional BOM coordinates appear in Gradle's dependency report only as version-alignment metadata and are not packaged. The complete resolved artifact list is recorded below; the rationale by group follows it.

| Dependency group | License, minimum SDK, and status | Disposition |
| --- | --- | --- |
| AndroidX AppCompat 1.8.0, Core 1.19.1, Fragment 1.9.1, Preference 1.2.1, ConstraintLayout 2.2.2, CoordinatorLayout 1.3.0, DrawerLayout 1.2.0 and their transitive AndroidX artifacts listed below | Apache-2.0. `:mobile:checkDebugAarMetadata` passes with app `minSdk` 23; no AndroidX AAR requires a higher minimum. The selected direct releases are stable in Google Maven metadata on 2026-10-14. | Replace the legacy Support and Architecture libraries. Keep current direct APIs; old stable transitive components such as Loader 1.0.0, ViewPager 1.0.0, ViewPager2 1.0.0, CardView 1.0.0, and Window 1.0.0 are still requested by upstream component metadata and are not used directly by app code. Do not force newer versions without an upstream compatibility need. |
| `com.google.android.material:material:1.14.0` | Apache-2.0; requires API 23. Current stable version. The upstream project is in maintenance mode and does not plan further feature releases for Views. | Keep the existing Material-based view UI on the latest stable version. This requirement caused the owner-approved `minSdk` increase from 19 to 23. |
| `androidx.databinding:viewbinding:9.4.1` | Apache-2.0; generated binding runtime for the pinned Android Gradle Plugin. | Keep; required by the ViewBinding feature enabled in Task 2. |
| `com.google.code.gson:gson:2.14.0` | Apache-2.0; no matching OSV advisory. | Keep through Task 4 for VAM JSON parsing; remove or retain only if the replacement protocol implementation still needs it. |
| Kotlin stdlib 2.2.10, Coroutines 1.9.0, annotations 23.0.0, JSpecify 1.0.0, Error Prone annotations 2.48.0 and Guava `listenablefuture:1.0` | Apache-2.0 for published metadata; AndroidX and the Android Gradle Plugin bring these transitively. `listenablefuture` is a small compatibility future type and has no active network behavior. | Keep as transitive runtime/compiler support. No direct application code depends on these APIs. |
| `org.simpleframework:simple-xml:2.7.1`; `stax:stax:1.2.0` | Simple XML declares Apache-2.0; StAX's published POM does not declare a license. Neither has an Android manifest minimum. No matching OSV advisory in this scan. | Keep only until Task 5 replaces GPX serialization/parsing; remove both from the final release graph. |

Task 7 replaced Core 1.19.1 with Core 1.18.0: 1.19.1 requires compile SDK 37, which is still Preview, while 1.18.0 is the latest stable release that builds against released SDK 36.1. The final release graph below supersedes the Task 2 dependency list.

The OSV batch scan of all 75 packaged runtime coordinates returned no matching advisories. The selected AndroidX and Material versions do not add an ads, analytics, attribution, or network client SDK.

Resolved debug and release runtime artifact coordinates (BOM alignment metadata omitted):

```text
androidx.activity:activity-ktx:1.8.1
androidx.activity:activity:1.8.1
androidx.annotation:annotation-experimental:1.4.1
androidx.annotation:annotation-jvm:1.10.0
androidx.annotation:annotation:1.10.0
androidx.appcompat:appcompat-resources:1.8.0
androidx.appcompat:appcompat:1.8.0
androidx.arch.core:core-common:2.2.0
androidx.arch.core:core-runtime:2.2.0
androidx.cardview:cardview:1.0.0
androidx.collection:collection-jvm:1.6.0
androidx.collection:collection:1.6.0
androidx.concurrent:concurrent-futures:1.1.0
androidx.constraintlayout:constraintlayout-core:1.1.2
androidx.constraintlayout:constraintlayout:2.2.2
androidx.coordinatorlayout:coordinatorlayout:1.3.0
androidx.core:core-ktx:1.19.1
androidx.core:core-viewtree:1.0.0
androidx.core:core:1.19.1
androidx.cursoradapter:cursoradapter:1.0.0
androidx.customview:customview:1.2.0
androidx.databinding:viewbinding:9.4.1
androidx.drawerlayout:drawerlayout:1.2.0
androidx.dynamicanimation:dynamicanimation:1.1.0
androidx.emoji2:emoji2:1.3.0
androidx.fragment:fragment-ktx:1.9.1
androidx.fragment:fragment:1.9.1
androidx.graphics:graphics-shapes-android:1.0.1
androidx.graphics:graphics-shapes:1.0.1
androidx.interpolator:interpolator:1.0.0
androidx.lifecycle:lifecycle-common-jvm:2.10.0
androidx.lifecycle:lifecycle-common:2.10.0
androidx.lifecycle:lifecycle-livedata-core-ktx:2.10.0
androidx.lifecycle:lifecycle-livedata-core:2.10.0
androidx.lifecycle:lifecycle-runtime-android:2.10.0
androidx.lifecycle:lifecycle-runtime-ktx-android:2.10.0
androidx.lifecycle:lifecycle-runtime:2.10.0
androidx.lifecycle:lifecycle-viewmodel-android:2.10.0
androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0
androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.10.0
androidx.lifecycle:lifecycle-viewmodel-savedstate:2.10.0
androidx.lifecycle:lifecycle-viewmodel:2.10.0
androidx.loader:loader:1.0.0
androidx.preference:preference-ktx:1.2.1
androidx.preference:preference:1.2.1
androidx.profileinstaller:profileinstaller:1.4.0
androidx.recyclerview:recyclerview:1.2.1
androidx.resourceinspection:resourceinspection-annotation:1.0.1
androidx.savedstate:savedstate-android:1.4.0
androidx.savedstate:savedstate:1.4.0
androidx.slidingpanelayout:slidingpanelayout:1.2.0
androidx.startup:startup-runtime:1.1.1
androidx.tracing:tracing-android:2.0.0
androidx.tracing:tracing:2.0.0
androidx.transition:transition:1.5.0
androidx.vectordrawable:vectordrawable-animated:1.1.0
androidx.vectordrawable:vectordrawable:1.1.0
androidx.versionedparcelable:versionedparcelable:1.1.1
androidx.viewpager2:viewpager2:1.0.0
androidx.viewpager:viewpager:1.0.0
androidx.window:window:1.0.0
com.google.android.material:material:1.14.0
com.google.code.gson:gson:2.14.0
com.google.errorprone:error_prone_annotations:2.48.0
com.google.guava:listenablefuture:1.0
org.jetbrains.kotlin:kotlin-stdlib:2.2.10
org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0
org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0
org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0
org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.3
org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3
org.jetbrains:annotations:23.0.0
org.jspecify:jspecify:1.0.0
org.simpleframework:simple-xml:2.7.1
stax:stax:1.2.0
```

## App network and advertising inventory

Source and the resolved runtime graph contain no ad, analytics, attribution, or IP networking SDK. The manifest does not request `INTERNET`, `AD_ID`, or `ACCESS_LOCAL_NETWORK`. The app uses Bluetooth socket APIs for the VAM sensor; this is not local IP network access. No SDK in this graph initiates network traffic on behalf of the app. Recheck the final merged manifest and graph in Task 7 after AndroidX migration.

## Audit sources and method

- Gradle reports: `./gradlew :mobile:dependencies --configuration debugRuntimeClasspath` and `./gradlew :mobile:dependencies --configuration releaseRuntimeClasspath`.
- AAR minimums: inspected `AndroidManifest.xml` in the resolved Android AARs; `:mobile:checkDebugAarMetadata` passed at API 37.
- Licenses: published Maven POM license declarations; Gson's published POM declares Apache-2.0. The Simple XML transitive `stax:stax` POM does not declare a license.
- Security: OSV query batch for every resolved group, artifact, and version.
- Android Support to AndroidX migration: <https://developer.android.com/jetpack/androidx/migrate>.

## Final release graph

## Final release graph (2026-10-14)

The final debug and release runtime reports resolve the same 81 graph coordinates. This includes 78 Android/JVM artifacts and three BOM nodes used only for version alignment; the BOMs are not packaged. The complete artifact coordinate list is below. The release AAB builds unsigned with package `br.com.helpdev.velocimetroalerta`, `minSdk 23`, `targetSdk 36`, version code 13, and version name 4.2. Its release manifest passed AAR metadata checks against released compile SDK 36.1.

| Final dependency group | License, minimum SDK, maintenance and security | Disposition |
| --- | --- | --- |
| AndroidX, Material Components, and generated ViewBinding artifacts | Published Apache-2.0 metadata. Resolved AAR metadata is compatible with the owner-approved `minSdk 23`; `:mobile:checkReleaseAarMetadata` passes with compile SDK 36.1. Direct versions are stable Google Maven releases on 2026-10-14. The OSV batch returned no matching advisories for the final graph. | Keep. The older transitive Loader, ViewPager, ViewPager2, CardView, and Window versions are requested by upstream component metadata and are not used directly; do not force upgrades without compatibility need. |
| `com.google.code.gson:gson:2.14.0`, Kotlin stdlib 2.2.10, Coroutines 1.9.0, Kotlin serialization 1.7.3, JetBrains annotations 23.0.0, JSpecify 1.0.0, Error Prone annotations 2.48.0, and Guava `listenablefuture:1.0` | Published Apache-2.0 metadata. Any Android AARs in this group are compatible with the approved `minSdk 23`; Java archives have no Android manifest minimum. No matching OSV advisories in the final scan. | Keep: Gson parses the VAM sensor payload, Kotlin is supplied by the Android Gradle Plugin, and the rest arrive transitively. |
| Kotlin, Coroutines, and Serialization BOM coordinates | POM-only version alignment metadata, not packaged artifacts. | Keep as resolution metadata only. |
| Advertising, analytics, attribution, IP networking, Simple XML, and StAX | None found in the final resolved release graph. The merged release manifest declares no `INTERNET`, `AD_ID`, `ACCESS_LOCAL_NETWORK`, `READ_EXTERNAL_STORAGE`, or `WRITE_EXTERNAL_STORAGE`; the GPX flow uses the Storage Access Framework. | No ad units or SDKs are needed. Do not declare Advertising ID use while the product remains ad-free. |

The OSV batch queried 78 resolved non-BOM Maven coordinates on 2026-10-14 and returned zero matching advisories.

Resolved non-BOM release runtime coordinates:

```text
androidx.activity:activity-ktx:1.8.1
androidx.activity:activity:1.8.1
androidx.annotation:annotation-experimental:1.4.1
androidx.annotation:annotation-jvm:1.10.0
androidx.annotation:annotation:1.10.0
androidx.appcompat:appcompat-resources:1.8.0
androidx.appcompat:appcompat:1.8.0
androidx.arch.core:core-common:2.2.0
androidx.arch.core:core-runtime:2.2.0
androidx.cardview:cardview:1.0.0
androidx.collection:collection-jvm:1.6.0
androidx.collection:collection:1.6.0
androidx.concurrent:concurrent-futures:1.1.0
androidx.constraintlayout:constraintlayout-core:1.1.2
androidx.constraintlayout:constraintlayout:2.2.2
androidx.coordinatorlayout:coordinatorlayout:1.3.0
androidx.core:core-ktx:1.18.0
androidx.core:core-viewtree:1.0.0
androidx.core:core:1.18.0
androidx.cursoradapter:cursoradapter:1.0.0
androidx.customview:customview:1.2.0
androidx.databinding:viewbinding:9.4.1
androidx.drawerlayout:drawerlayout:1.2.0
androidx.dynamicanimation:dynamicanimation:1.1.0
androidx.emoji2:emoji2-views-helper:1.3.0
androidx.emoji2:emoji2:1.3.0
androidx.fragment:fragment-ktx:1.9.1
androidx.fragment:fragment:1.9.1
androidx.graphics:graphics-shapes-android:1.0.1
androidx.graphics:graphics-shapes:1.0.1
androidx.interpolator:interpolator:1.0.0
androidx.lifecycle:lifecycle-common-jvm:2.10.0
androidx.lifecycle:lifecycle-common:2.10.0
androidx.lifecycle:lifecycle-livedata-core-ktx:2.10.0
androidx.lifecycle:lifecycle-livedata-core:2.10.0
androidx.lifecycle:lifecycle-livedata:2.10.0
androidx.lifecycle:lifecycle-process:2.10.0
androidx.lifecycle:lifecycle-runtime-android:2.10.0
androidx.lifecycle:lifecycle-runtime-ktx-android:2.10.0
androidx.lifecycle:lifecycle-runtime-ktx:2.10.0
androidx.lifecycle:lifecycle-runtime:2.10.0
androidx.lifecycle:lifecycle-viewmodel-android:2.10.0
androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0
androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.10.0
androidx.lifecycle:lifecycle-viewmodel-savedstate:2.10.0
androidx.lifecycle:lifecycle-viewmodel:2.10.0
androidx.loader:loader:1.0.0
androidx.preference:preference-ktx:1.2.1
androidx.preference:preference:1.2.1
androidx.profileinstaller:profileinstaller:1.4.0
androidx.recyclerview:recyclerview:1.2.1
androidx.resourceinspection:resourceinspection-annotation:1.0.1
androidx.savedstate:savedstate-android:1.4.0
androidx.savedstate:savedstate-ktx:1.4.0
androidx.savedstate:savedstate:1.4.0
androidx.slidingpanelayout:slidingpanelayout:1.2.0
androidx.startup:startup-runtime:1.1.1
androidx.tracing:tracing-android:2.0.0
androidx.tracing:tracing:2.0.0
androidx.transition:transition:1.5.0
androidx.vectordrawable:vectordrawable-animated:1.1.0
androidx.vectordrawable:vectordrawable:1.1.0
androidx.versionedparcelable:versionedparcelable:1.1.1
androidx.viewpager2:viewpager2:1.0.0
androidx.viewpager:viewpager:1.0.0
androidx.window:window:1.0.0
com.google.android.material:material:1.14.0
com.google.code.gson:gson:2.14.0
com.google.errorprone:error_prone_annotations:2.48.0
com.google.guava:listenablefuture:1.0
org.jetbrains.kotlin:kotlin-stdlib:2.2.10
org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0
org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0
org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0
org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.3
org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3
org.jetbrains:annotations:23.0.0
org.jspecify:jspecify:1.0.0
```

The three version-alignment nodes omitted from that artifact list are `org.jetbrains.kotlin:kotlin-bom:1.8.22`, `org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.9.0`, and `org.jetbrains.kotlinx:kotlinx-serialization-bom:1.7.3`.

## Maps SDK addition (2026-10-15)

The graph above is the pre-map release baseline. The active debug and release graphs now also include
`com.google.android.gms:play-services-maps:20.0.0` and its Google Play services dependencies; the current app
manifest adds `INTERNET` solely so the Maps SDK can load map content. Confirm the resolved runtime graph with
`./gradlew :mobile:dependencies --configuration debugRuntimeClasspath` and inspect the merged manifest with
`./gradlew :mobile:processDebugMainManifest` (repeat with `Release` for a release artifact).

The Maps SDK 20.x Android compatibility floor is API 23; this app's configured `minSdk` is 24, so the dependency
does not raise the app's existing floor. Maps SDK for Android is a Google Maps Platform service governed by its
Google Maps Platform terms and policies; it is not an Apache-licensed library that can be treated as a standalone,
offline map renderer. The API key must remain outside version control and be restricted by package name and signing
certificate. Google Cloud billing and quota controls must be configured for the production project; review current
usage and pricing before release.

The SDK's published Play Data Safety guidance states that it automatically collects request metadata (including
device metadata, SDK version/build, result count, and an internal usage-attribution identifier), SDK crash metrics
and stack traces, IP address, and a Maps SDK-specific pseudonymous identifier. Map interaction events such as pan
and zoom may also be collected when the app uses the Maps camera APIs, as this implementation does. The app owner
must represent the complete app/SDK behavior in Play Data Safety and the public privacy policy. This is SDK-to-Google
technical collection, separate from the app's local activity recording and from any Help DEV server collection.

Rationale: a native Google map is required to display the live route over geographic map content; the chosen SDK
provides this without adding a second location owner or route backend. The map remains optional while recording, and
the activity can continue locally if the map cannot load. Do not describe the app as having no network SDK or as
having no third-party technical collection while this dependency is shipped.
