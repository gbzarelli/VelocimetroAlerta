# Minhas atividades Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Save completed GPS activities locally, list and inspect them later, view their historical route, and export their canonical GPX on demand.

**Architecture:** Persist each confirmed activity under private app storage as a canonical GPX plus a lightweight Gson summary. The feed and detail consume the summary (including a bounded route preview), while the static map reads the full GPX route; the active-tracking map remains service-backed and independent. The existing main fragment becomes responsible for converting its paused in-memory state into a draft and committing it only after name confirmation.

**Tech Stack:** Kotlin, Java 17, AndroidX/ViewBinding, XML Views, Gson 2.14.0, Google Maps SDK already present, JUnit 4, AndroidX Espresso/instrumentation, Gradle 9.6/AGP 9.4.

**Spec:** `docs/superpowers/specs/2026-10-16-minhas-atividades-design.md`

## Global Constraints

- Keep the current `minSdk 24`, Views/XML, ViewBinding and `colorPrimary` (`#009688`); do not add Room, Compose, cloud sync, import, delete, or edit of a completed activity.
- Store each confirmed activity only under `context.filesDir/activities/<uuid>/activity.gpx` and `summary.json`; never make its raw route public until the user exports it.
- GPX is canonical: export must byte-copy the stored `activity.gpx` to the Storage Access Framework destination, not regenerate a route.
- Summaries include a maximum of 120 ordered preview coordinates, retaining the first and last route point; missing sensor data is represented as absent, never displayed as zero.
- A failed save must retain the paused source session and remove only temporary files; opening historical content must never bind, start, pause, stop, or mutate `SpeedometerService`.
- All new copy must exist in both `values/strings.xml` and `values-pt/strings.xml`; do not commit `.superpowers/` or `docs/superpowers/local-backups/`.

## Review Focus

- **Interrupted storage write:** Task 2 tests that no final directory appears and the paused source remains usable when GPX or JSON write fails.
- **Very long route:** Task 1 tests preview downsampling is capped at 120 while preserving first, last, and ordering; Task 5 loads the unabridged GPX for the map.
- **No sensor samples:** Task 1 tests flags/optional metrics distinguish a missing sensor from a numeric zero; Task 5 hides only unavailable metrics.
- **Invalid historical entry:** Task 2 tests list loading skips a malformed summary or incomplete directory without hiding valid activities.
- **Cancelled/failed export:** Task 5 tests cancel and output-stream failure leave `activity.gpx` unchanged and show an error without affecting the detail screen.

## File Structure

| Path | Responsibility |
| --- | --- |
| `mobile/src/main/java/.../activities/ActivityModels.kt` | Immutable draft, summary, optional sensor values, preview-point model, and draft mapper. |
| `mobile/src/main/java/.../activities/ActivityRepository.kt` | Private file layout, atomic write, listing, summary load, route load and GPX-file lookup. |
| `mobile/src/main/java/.../activities/RoutePreviewView.kt` | Local canvas preview of normalized route coordinates for cards and detail header. |
| `mobile/src/main/java/.../ActivitySaveActivity.kt` | Name confirmation and discard/conclude result contract. |
| `mobile/src/main/java/.../MyActivities.kt`, `ActivityDetailActivity.kt`, `SavedRouteMapActivity.kt` | Feed, detail and static-map navigation. |
| `mobile/src/main/java/.../RouteMapFragment.kt` | Add a static-route input path that reuses its renderer/state without service subscription. |
| `mobile/src/main/java/.../gpx/GpxFileUtils.kt` | GPX reader for persisted tracks; existing writer remains used to create the canonical file. |
| `mobile/src/main/res/layout/` | New save, feed row, detail and static-map layouts; replace the current My Activities placeholder. |
| `mobile/src/main/res/values*/strings.xml`, `AndroidManifest.xml`, `gradle/libs.versions.toml`, `mobile/build.gradle` | Text, declared activities and RecyclerView dependency. |
| `mobile/src/test/java/.../activities/` | Pure models, preview, GPX and repository regression tests. |
| `mobile/src/androidTest/java/.../` | Completion, feed, detail, static map and export behavior. |

### Task 1: Model the saved activity and bounded route preview

**Files:**
- Create: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/activities/ActivityModels.kt`
- Create: `mobile/src/test/java/br/com/helpdev/velocimetroalerta/activities/ActivityDraftFactoryTest.kt`

**Interfaces:**
- Produces: `data class ActivityPreviewPoint(val latitude: Double, val longitude: Double)`.
- Produces: `data class ActivitySummary(...)`, including IDs, saved/start times, all approved aggregate metrics, nullable sensor metrics/availability flags, and `routePreview: List<ActivityPreviewPoint>`.
- Produces: `data class ActivityDraft(val summary: ActivitySummary, val gpx: Gpx)` and `object ActivityDraftFactory { fun from(gpx: Gpx, alert: ObSpeedometerAlert, suggestedName: String, id: String = UUID.randomUUID().toString()): ActivityDraft }`.
- Produces: `object RoutePreviewSampler { fun sample(points: List<TrkPt>, maximumPoints: Int = 120): List<ActivityPreviewPoint> }`.

- [ ] **Step 1: Write failing `ActivityDraftFactoryTest` cases for a complete snapshot, absent sensors, total duration, and preview sampling**

Assert that factory output copies `ObSpeedometerAlert` aggregates, uses `moving + paused` as total duration, derives availability from actual samples, produces a suggested name/ID, and that a 400-point route produces at most 120 ordered coordinates retaining endpoints.

- [ ] **Step 2: Run the focused model test to verify it fails**

Run: `./gradlew :mobile:testDebugUnitTest --tests br.com.helpdev.velocimetroalerta.activities.ActivityDraftFactoryTest`

Expected: FAIL because activity persistence models do not exist.

- [ ] **Step 3: Implement immutable activity models, deep GPX snapshotting, and deterministic preview sampling**

`ActivityDraftFactory.from` must duplicate the incoming GPX/points and point extensions before returning so a later service mutation cannot alter a confirmed draft. Sample coordinates evenly, preserve original order, and always retain first/last when points exist.

- [ ] **Step 4: Re-run the focused model test**

Run: `./gradlew :mobile:testDebugUnitTest --tests br.com.helpdev.velocimetroalerta.activities.ActivityDraftFactoryTest`

Expected: PASS.

- [ ] **Step 5: Commit the model boundary**

```bash
git add mobile/src/main/java/br/com/helpdev/velocimetroalerta/activities/ActivityModels.kt mobile/src/test/java/br/com/helpdev/velocimetroalerta/activities/ActivityDraftFactoryTest.kt
git commit -m "feat: model saved activity snapshots"
```

### Task 2: Persist canonical GPX and summaries atomically

**Files:**
- Create: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/activities/ActivityRepository.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/gpx/GpxFileUtils.kt`
- Create: `mobile/src/test/java/br/com/helpdev/velocimetroalerta/activities/ActivityRepositoryTest.kt`
- Modify: `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/gpx/GpxFileUtilsTest.kt`

**Interfaces:**
- Consumes: `ActivityDraft`, `ActivitySummary`, `ActivityPreviewPoint` from Task 1.
- Produces: `class ActivityRepository(filesDir: File, gson: Gson = Gson(), gpxFileUtils: GpxFileUtils = GpxFileUtils())` with `fun save(draft: ActivityDraft, confirmedName: String): ActivitySummary`, `fun list(): List<ActivitySummary>`, `fun loadSummary(id: String): ActivitySummary?`, `fun loadRoute(id: String): List<RoutePoint>?`, and `fun gpxFile(id: String): File?`.
- Produces: `fun GpxFileUtils.readGpx(input: InputStream): Gpx` for the writer-compatible GPX 1.1 subset, including point extensions.

- [ ] **Step 1: Write failing repository and GPX-reader tests**

Use a temporary directory to assert final two-file layout, newest-first list ordering, round-trip route coordinates/extensions, missing sensor representation, malformed-entry skipping, and cleanup when a test double fails while writing GPX or JSON. Extend Android GPX instrumentation to prove the reader accepts the writer's output.

- [ ] **Step 2: Run focused repository and GPX tests to verify they fail**

Run: `./gradlew :mobile:testDebugUnitTest --tests br.com.helpdev.velocimetroalerta.activities.ActivityRepositoryTest :mobile:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=br.com.helpdev.velocimetroalerta.gpx.GpxFileUtilsTest`

Expected: repository tests fail until storage exists; instrumentation fails until `readGpx` exists.

- [ ] **Step 3: Implement repository atomic promotion and streaming GPX reader**

Write `activity.gpx` and `summary.json` inside `<id>.tmp`, validate both, then atomically rename the directory to `<id>`; remove only `<id>.tmp` on failure. Set the track name from the trimmed confirmed name before GPX write. List only complete directories with a valid summary and log/skip corrupt ones. Parse GPX using Android `XmlPullParser`; reject a route with no valid coordinates.

- [ ] **Step 4: Re-run repository and GPX tests**

Run: `./gradlew :mobile:testDebugUnitTest --tests br.com.helpdev.velocimetroalerta.activities.ActivityRepositoryTest :mobile:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=br.com.helpdev.velocimetroalerta.gpx.GpxFileUtilsTest`

Expected: PASS, including malformed/failed-write cases.

- [ ] **Step 5: Commit private persistence**

```bash
git add mobile/src/main/java/br/com/helpdev/velocimetroalerta/activities/ActivityRepository.kt mobile/src/main/java/br/com/helpdev/velocimetroalerta/gpx/GpxFileUtils.kt mobile/src/test/java/br/com/helpdev/velocimetroalerta/activities/ActivityRepositoryTest.kt mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/gpx/GpxFileUtilsTest.kt
git commit -m "feat: persist activities as GPX and summary"
```

### Task 3: Replace direct export with name confirmation and safe commit

**Files:**
- Create: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/ActivitySaveActivity.kt`
- Create: `mobile/src/main/res/layout/activity_save_activity.xml`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/MainActivityFragment.kt`
- Modify: `mobile/src/main/AndroidManifest.xml`
- Modify: `mobile/src/main/res/values/strings.xml`
- Modify: `mobile/src/main/res/values-pt/strings.xml`
- Create: `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/ActivitySaveFlowTest.kt`

**Interfaces:**
- Consumes: `ActivityDraft` and `ActivityRepository.save` from Tasks 1–2.
- Produces: `ActivitySaveActivity.newIntent(context: Context, suggestedName: String): Intent`; result extras `EXTRA_ACTION` (`"confirm" | "discard"`) and `EXTRA_NAME`.
- Produces: `MainActivityFragment` handling the result by `save(draft, name)`, `clear()` only on success/discard, and starting `ActivityDetailActivity` with `EXTRA_ACTIVITY_ID` on success.

- [ ] **Step 1: Write failing instrumentation coverage for confirm, blank name, discard, and repository failure**

Seed a paused, valid route through the existing service test hooks. Assert Save opens the name screen rather than a document picker; assert a blank trimmed name cannot confirm; assert discard clears without repository files; assert confirmed save opens detail and failure leaves the Save/Refresh controls and service state intact.

- [ ] **Step 2: Run the focused save-flow test to verify it fails**

Run: `python3 tools/run-hermetic-android-tests.py --sdk 24,36 --test ActivitySaveFlowTest`

Expected: FAIL because saving currently launches `CreateDocument`.

- [ ] **Step 3: Implement the confirmation Activity and replace `pendingExport` flow**

Keep only the current valid-session guard in `MainActivityFragment`; create an `ActivityDraft` once valid. `ActivitySaveActivity` owns validation and returns an action/name, never writes files. On confirmation, run repository work on the fragment’s background executor, present non-cancelable progress, clear the service only after success, then open the saved detail. On error leave the draft/session intact and offer retry/discard.

- [ ] **Step 4: Re-run the focused save-flow test**

Run: `python3 tools/run-hermetic-android-tests.py --sdk 24,36 --test ActivitySaveFlowTest`

Expected: PASS on both APIs; no document-picker intent is observed before confirmation.

- [ ] **Step 5: Commit the save/discard flow**

```bash
git add mobile/src/main/java/br/com/helpdev/velocimetroalerta/ActivitySaveActivity.kt mobile/src/main/java/br/com/helpdev/velocimetroalerta/MainActivityFragment.kt mobile/src/main/res/layout/activity_save_activity.xml mobile/src/main/AndroidManifest.xml mobile/src/main/res/values/strings.xml mobile/src/main/res/values-pt/strings.xml mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/ActivitySaveFlowTest.kt
git commit -m "feat: confirm activity before saving"
```

### Task 4: Build the activity feed with local route sketches

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `mobile/build.gradle`
- Create: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/activities/RoutePreviewView.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/MyActivities.kt`
- Modify: `mobile/src/main/res/layout/content_my_activities.xml`
- Create: `mobile/src/main/res/layout/item_activity_summary.xml`
- Create: `mobile/src/main/res/drawable/activity_card_background.xml`
- Create: `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/MyActivitiesTest.kt`

**Interfaces:**
- Consumes: `ActivityRepository.list()` and `ActivitySummary.routePreview` from Tasks 1–2.
- Produces: `RoutePreviewView.submit(points: List<ActivityPreviewPoint>)` and a `RecyclerView` adapter whose click launches `ActivityDetailActivity.EXTRA_ACTIVITY_ID`.
- Produces: `MyActivities` states `loading`, `empty`, and `content`, sorted/grouped by the local start date.

- [ ] **Step 1: Write failing feed tests and preview geometry tests**

Assert an empty repository renders the explanatory state; seed summaries across two months and assert newest-first sections/card labels; click a card and assert its ID is passed to the detail Activity. Unit-test preview normalization for vertical, horizontal, and repeated-coordinate routes so drawing remains inside bounds.

- [ ] **Step 2: Run focused feed tests to verify they fail**

Run: `./gradlew :mobile:testDebugUnitTest --tests br.com.helpdev.velocimetroalerta.activities.RoutePreviewViewTest && python3 tools/run-hermetic-android-tests.py --sdk 24,36 --test MyActivitiesTest`

Expected: FAIL because `MyActivities` is still a development placeholder.

- [ ] **Step 3: Implement the list, card and route-preview view**

Add the AndroidX RecyclerView catalog/module dependency. Draw preview polylines locally in `RoutePreviewView`, with normalized bounds/padding, `colorPrimary` stroke and no map/network dependency. Load repository work off the main thread; show only month headers and cards approved in the visual design. Keep all measurements locale-aware and use `ActivitySummary` data only.

- [ ] **Step 4: Re-run focused feed tests**

Run: `./gradlew :mobile:testDebugUnitTest --tests br.com.helpdev.velocimetroalerta.activities.RoutePreviewViewTest && python3 tools/run-hermetic-android-tests.py --sdk 24,36 --test MyActivitiesTest`

Expected: PASS with empty and multi-month content states.

- [ ] **Step 5: Commit feed UI**

```bash
git add gradle/libs.versions.toml mobile/build.gradle mobile/src/main/java/br/com/helpdev/velocimetroalerta/activities/RoutePreviewView.kt mobile/src/main/java/br/com/helpdev/velocimetroalerta/MyActivities.kt mobile/src/main/res/layout/content_my_activities.xml mobile/src/main/res/layout/item_activity_summary.xml mobile/src/main/res/drawable/activity_card_background.xml mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/MyActivitiesTest.kt mobile/src/test/java/br/com/helpdev/velocimetroalerta/activities/RoutePreviewViewTest.kt
git commit -m "feat: list saved activities"
```

### Task 5: Add detail, historical map and deferred GPX export

**Files:**
- Create: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/ActivityDetailActivity.kt`
- Create: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/SavedRouteMapActivity.kt`
- Modify: `mobile/src/main/java/br/com/helpdev/velocimetroalerta/RouteMapFragment.kt`
- Create: `mobile/src/main/res/layout/activity_activity_detail.xml`
- Create: `mobile/src/main/res/layout/activity_saved_route_map.xml`
- Modify: `mobile/src/main/AndroidManifest.xml`
- Modify: `mobile/src/main/res/values/strings.xml`
- Modify: `mobile/src/main/res/values-pt/strings.xml`
- Create: `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/ActivityDetailTest.kt`
- Modify: `mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/RouteMapNavigationTest.kt`

**Interfaces:**
- Consumes: `ActivityRepository.loadSummary`, `loadRoute`, and `gpxFile` from Task 2; `RoutePreviewView` from Task 4; `RouteMapState`/`GoogleRouteMapRenderer` already present.
- Produces: `ActivityDetailActivity.EXTRA_ACTIVITY_ID`, `SavedRouteMapActivity.EXTRA_ACTIVITY_ID`, and a `RouteMapFragment.showStaticRoute(points: List<RoutePoint>)` input that never subscribes to `SpeedometerService`.
- Produces: detail GPX export via `ActivityResultContracts.CreateDocument("application/gpx+xml")` and stream copy from the repository’s canonical file.

- [ ] **Step 1: Write failing detail/map/export tests**

Seed an activity with and without sensor data. Assert all applicable metric values render, unavailable sensor rows are hidden, preview click opens `SavedRouteMapActivity`, static map receives ordered points, and Back returns to detail. Assert export cancellation and forced destination-stream failure preserve the internal GPX. Extend live-map regression to assert opening a saved route never changes a currently running/paused service.

- [ ] **Step 2: Run focused detail and map tests to verify they fail**

Run: `python3 tools/run-hermetic-android-tests.py --sdk 24,36 --test ActivityDetailTest --test RouteMapNavigationTest`

Expected: FAIL because no detail/static-map activity or deferred export exists.

- [ ] **Step 3: Implement detail rendering, static map source and copy-based export**

Load summary asynchronously by required ID; finish with a user-visible unavailable state for missing/corrupt data. Bind the top `RoutePreviewView` to the stored preview and make it the only map navigation affordance. Reuse `RouteMapFragment` renderer/state for `showStaticRoute`, but isolate that mode from service binding/subscription. Render rows conditionally for each optional sensor. Export on a background executor using `inputStream.copyTo(outputStream)` with a unique default `VEL_ALERTA_<started-at>_<id>.gpx` filename.

- [ ] **Step 4: Re-run focused detail and map tests**

Run: `python3 tools/run-hermetic-android-tests.py --sdk 24,36 --test ActivityDetailTest --test RouteMapNavigationTest`

Expected: PASS; live ride state/point count is unchanged by historical map navigation.

- [ ] **Step 5: Commit historical detail and export**

```bash
git add mobile/src/main/java/br/com/helpdev/velocimetroalerta/ActivityDetailActivity.kt mobile/src/main/java/br/com/helpdev/velocimetroalerta/SavedRouteMapActivity.kt mobile/src/main/java/br/com/helpdev/velocimetroalerta/RouteMapFragment.kt mobile/src/main/res/layout/activity_activity_detail.xml mobile/src/main/res/layout/activity_saved_route_map.xml mobile/src/main/AndroidManifest.xml mobile/src/main/res/values/strings.xml mobile/src/main/res/values-pt/strings.xml mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/ActivityDetailTest.kt mobile/src/androidTest/java/br/com/helpdev/velocimetroalerta/RouteMapNavigationTest.kt
git commit -m "feat: show saved activity details"
```

### Task 6: Run full regression and document visual/device checks

**Files:**
- Create: `docs/minhas-atividades-validation.md`

**Interfaces:**
- Consumes: all feature and regression tests from Tasks 1–5.
- Produces: reproducible validation evidence and a concise manual-check list for device-only route, sensor and document-provider behavior.

- [ ] **Step 1: Add a validation checklist before broad execution**

Document the required manual checks: name suggestion/edit/discard, persistence through app restart, long GPX preview/map, each optional sensor state, cancelled/failed export, and live-vs-historical map separation.

- [ ] **Step 2: Run JVM and debug build regression**

Run: `./gradlew :mobile:assembleDebug :mobile:testDebugUnitTest --rerun-tasks`

Expected: PASS with all persistence, GPX, preview and map-state unit tests.

- [ ] **Step 3: Run hermetic instrumentation regression**

Run: `python3 tools/run-hermetic-android-tests.py --sdk 24,36`

Expected: all API-applicable cases pass independently, including the new activity flow; any unavailable emulator is recorded as an environment limitation rather than masked.

- [ ] **Step 4: Check staged changes and update validation evidence with actual results**

Run: `git diff --check` and record exact command outcomes, device APIs, skipped cases, and manual checks still required in `docs/minhas-atividades-validation.md`.

- [ ] **Step 5: Commit verification evidence**

```bash
git add docs/minhas-atividades-validation.md
git commit -m "docs: validate minhas atividades"
```

## Plan Self-Review

- **Spec coverage:** Tasks 1–2 cover private GPX/summary persistence; Task 3 covers naming, confirm/discard and failure retention; Task 4 covers approved feed design; Task 5 covers detail, static map and deferred export; Task 6 covers full acceptance evidence.
- **Step scan:** Each implementation task begins from a focused failing test, defines exact produced interfaces, then verifies and commits independently.
- **Type consistency:** `ActivityDraft`/`ActivitySummary` originate in Task 1 and are consumed only through `ActivityRepository` in Tasks 2–5; navigation always passes a string `EXTRA_ACTIVITY_ID`; all static maps consume `List<RoutePoint>`.
- **Review Focus:** The five likely failure classes are explicitly attached to the task tests that own their behavior.
- **Proportion:** The plan describes interfaces, files and checks rather than reimplementing the feature; the spec retains behavior and user-experience decisions.
