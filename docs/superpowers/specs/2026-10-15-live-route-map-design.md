# Live Route Map Design

## Goal

Allow the person recording an activity to open a map, see the accepted route and current accepted location update in real time, and return to the speed screen without stopping, pausing, restarting, or duplicating GPS tracking.

## Current baseline

This design applies to `master` at `bc81747`. The app is already AndroidX/ViewBinding based and ships as `br.com.helpdev.velocimetroalerta.v2` with `minSdk 24`, `targetSdk 36`, and a user-initiated location foreground service. `SpeedometerService` owns the active GPS subscription and records only positions whose existing accuracy rule is at most 15 m into GPX. `MainActivity` unbinds when the map opens, but the started foreground service continues running.

The current minimum API satisfies Maps SDK for Android's API-23 floor, so the map feature does not require a new minimum-SDK decision.

## Scope

- On the speed screen, show an accessible `Mapa` action only while `SpeedometerService.isRunning` is true, including manual and automatic pauses. It opens a new `RouteMapActivity`.
- The map renders every already accepted point of the current activity as one blue polyline and displays one marker at the final accepted point. It appends new accepted points as they are recorded.
- The first non-empty route frames the route/current point. The map follows subsequent points until the person pans the map; a visible `Recentrar` control restores following.
- With no accepted point, show a waiting state. With invalid GPS or paused tracking, retain the existing route and add no point. If tracking stops while the map is open, retain the last drawn route without starting/restarting the service.
- Toolbar Up and Android Back close only `RouteMapActivity` and return to the speed screen. The existing main-screen Back confirmation remains the only flow that stops a ride.
- This iteration excludes turn-by-turn navigation, route matching, offline tiles, historical-activity maps, remote sharing, cloud route storage, and any new location/background permissions.

## Provider and configuration

Use Maps SDK for Android with `SupportMapFragment`. Add only `INTERNET`, the Maps dependency, and Google Maps API metadata. The app must not enable Google Map's My Location layer: the marker comes solely from the activity's already accepted GPX point.

Create a Google Cloud project with Maps SDK for Android enabled and an appropriately configured billing account. Keep `MAPS_API_KEY` in ignored `secrets.properties`, make it available through the Maps Secrets Gradle Plugin, and commit a non-secret default only for local/test builds. Use separate debug and release keys; restrict the release key to application ID `br.com.helpdev.velocimetroalerta.v2` and its release certificate SHA-1. No key value may enter source control, build logs, issue text, screenshots, or test fixtures.

## Route delivery architecture

`SpeedometerService` remains the only source of GPS data and activity state. Do not route map updates through its existing `CallbackGpsThread`, which has one UI consumer and is cleared when `MainActivity` pauses.

Introduce a small provider-neutral active-route component in the `gps` package:

- `RoutePoint(latitude, longitude)` is an immutable coordinate with no Maps dependency.
- `RouteSnapshot(revision, points)` is an immutable initial state.
- `RouteObserver.onPointAdded(revision, point)` receives only new accepted points.
- `ActiveRoute.subscribe(observer)` atomically registers an observer and returns a snapshot; `unsubscribe(observer)` is idempotent; `append(point)` increments the revision and notifies a copy of observers after leaving its lock.

On each successful GPX point append, the service appends the same latitude/longitude to `ActiveRoute`. It resets `ActiveRoute` together with the GPX document for a new activity. The map screen binds to the already started service with flags `0`, not `BIND_AUTO_CREATE`, so a stale click cannot recreate tracking. It subscribes once after both service and map are ready, posts callbacks to the main thread, ignores revisions at or below the last rendered revision, and unsubscribes before unbinding.

`RouteMapFragment` owns the provider rendering details: a single `Polyline`, a single final-point `Marker`, the latest revision, and follow-mode state. Its map-specific types never enter the service or GPX model.

## Privacy and release impact

The Maps SDK automatically collects technical request metadata, crash metrics, IP address, and a pseudonymous Maps SDK identifier; pan/zoom interaction data can also be collected. Before a release containing this feature, update the app's Google Play Data Safety disclosure from the current no-data-collection position using Google's then-current Maps SDK disclosure guidance. Review the privacy-policy wording as well. This disclosure work is mandatory even though the app does not upload its GPX route to an app backend.

## Acceptance criteria

- Opening, panning, recentering, returning from, rotating, or receiving an initialization error in the map never calls `start`, `pause`, or `stop` on `SpeedometerService`; elapsed time, alert state, foreground notification, and GPX recording remain intact.
- A map opened after N accepted points shows exactly N ordered points; each later accepted point extends the same polyline once and updates the one current marker.
- Null/low-quality locations and manual/automatic pauses create no map point. A map opened before the first accepted point is clear and recoverable.
- The map does not register a location listener, request a runtime permission, turn on `isMyLocationEnabled`, or expose a mutable GPX collection.
- Unit tests prove snapshot/subscription ordering and immutability. Instrumentation tests prove main-to-map-to-main navigation preserves the running/paused state and works with the app's existing edge-to-edge/back behavior. Physical-device validation verifies a route using a restricted non-production key.

## References

- [Maps SDK for Android setup](https://developers.google.com/maps/documentation/android-sdk/config)
- [Maps Secrets Gradle Plugin](https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin)
- [Maps SDK for Android Play Data Safety disclosure](https://developers.google.com/maps/documentation/android-sdk/play-data-disclosure)
