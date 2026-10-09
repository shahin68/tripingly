# Tripinly — Maps, Places and Routing (client view)

## The stack

| Part | What the client uses |
|---|---|
| Drawing the map | **Google Maps SDK**: Android via the existing integration (maps-compose if already used), iOS via Google Maps SDK for iOS wrapped in `UIKitView` behind a shared interface |
| Places on the map | Tripinly backend `GET /places/in-view` (our places + OpenStreetMap POIs) |
| Search box | Backend `GET /places/search` |
| Routes and travel times | Backend `GET /days/{id}/route`, `GET /routes` |
| Best route | Backend `POST /days/{id}/optimize` |

**Not used:** Google Places, Geocoding, Directions or Routes APIs. They cost money per call, and Google's terms forbid storing their place data. The Maps SDK itself is free on mobile.

## Legal rules

1. **Hide Google POIs** with a map style JSON (`poi` → `visibility: off`, keep road and area labels). Otherwise users see Google's cafés and try to tap places we can't use.
2. Never read Google POI data (`onPoiClick` etc.) and never send Google place data to the backend.
3. Keep the Google logo and legal text visible (SDK default; don't cover it with UI; use map padding).
4. Show **"© OpenStreetMap contributors"** on every map screen that shows our places or routes (small text over the map, tappable to openstreetmap.org/copyright), and in Settings → About/Legal. For routes, also "© openrouteservice.org". The API responses contain an `attribution` string; show it.

## Shared map abstraction

Keep map UI code platform-specific but its **state shared**:

```kotlin
// commonMain
data class MapState(
    val camera: CameraState,
    val tripMarkers: List<TripPin>,        // markers of the open trip (numbered, with cover thumbnail)
    val places: List<PlacePin>,            // in-view places (Tripinly or OSM)
    val clusters: List<ClusterPin>,
    val routes: List<RouteLine>,           // decoded polylines
    val alongTheWay: List<PlacePin>,
    val selected: Selection?
)

@Composable expect fun TripinlyMap(state: MapState, onEvent: (MapEvent) -> Unit, modifier: Modifier)
// MapEvent: CameraIdle(bounds, zoom), PinClick(id), MapClick(latLng), MapLongClick(latLng), ClusterClick(...)
```

- Android `actual`: Google Maps (maps-compose or MapView, whichever the repo uses).
- iOS `actual`: `UIKitView` hosting `GMSMapView`, a delegate forwarding events, diffing state into `GMSMarker`/`GMSPolyline`.
- If the repo already has a different abstraction, extend that instead.

## Pins

| Pin | Look | Source |
|---|---|---|
| Trip marker with photo | Larger round pin with the **cover thumbnail** inside plus day-order number | Trip `markers[].coverThumbUrl` |
| Trip marker without photo | Numbered pin in the day color | Trip markers |
| Tripinly place (not in trip) | Medium "unselected" pin with category icon and like count | `in-view` with `isTripinly: true` |
| OSM place | Small dot with category icon, name only at high zoom | `in-view` with `isTripinly: false` |
| Along the way | Like OSM or Tripinly pins, with a subtle highlight | `alongTheWay` |
| Cluster | Circle with count | `in-view` cluster items |

- Render thumbnails to bitmaps off the main thread (download with the app's image loader, crop circle, cache per URL + size). Android: `BitmapDescriptor`; iOS: `UIImage` icon (prefer icon images over `iconView` for performance).
- Use the Google Maps utility libraries for **client-side clustering** of trip markers only if needed. Place clusters come from the server.

## Stop names (planned for stage 5)

Shahin wants every stop to show its place name, not only "Stop #N" (asked 2026-10-08).

- Chips and pin titles already show **"Stop #N" from the stop's position** in the day, so numbers follow deletes and reorders. The marker's stored `name` is a placeholder ("Stop #N" at creation) until this lands.
- Stage 5: a stop added from a place (in-view pin, search result with `id`) is created with `placeId`, and the server fills `name` with the place name. A stop added by tapping empty map gets a name from a backend lookup of the nearest OSM place or a Photon reverse lookup (a backend endpoint; never Google, never public Nominatim), and falls back to "Stop #N" when nothing is near.
- Display: "Stop #N" plus the place name (chip second line or "Stop #N · Name", pin info window title "Name", snippet "Stop #N"). The design is Shahin's call when stage 5 starts.

## Loading places while browsing

1. On **camera idle**, wait 300 ms (debounce), cancel the previous request, then call `GET /places/in-view?bbox=…&zoom=…&categories=…`.
2. Cache responses per rounded bbox + zoom + categories for a few minutes, so panning back is instant.
3. Zoomed out (< 14): only Tripinly places and server clusters. Zoomed in (≥ 14): OSM dots too.
4. Category filter chips (cafés, restaurants, attractions, museums, parks…) change the `categories` parameter.
5. Tapping a place → place sheet (`GET /places/{id}`): name, category, likes, public photos, "Add to trip" (choose trip and day) → `POST /places/{id}/add-to-trip`.
6. Handle `BBOX_TOO_LARGE` by showing nothing new (the user zoomed out too far).

## Search

- Debounce 300 ms, from the first letter (one letter returns Photon prefix matches only), cancel in-flight requests. Send the map center as `lat/lng` for ranking.
- Results: our places (have `id`) first, then addresses/cities (`source: "photon"`, no `id`).
- Picking a place with `id` → move the camera, open the place sheet. Picking an address/city → move the camera; "Add to trip" sends `name` + `location` (+ `osmType`/`osmId`) to `POST /days/{id}/markers`.
- A **long press** on the map creates a custom pin: user names it → `POST /days/{id}/markers` with name + location.

## Routes and places along the way

- Trip screen: toggle "Show route" for the selected day → `GET /days/{id}/route?mode=walking&categories=…`. Draw the decoded polyline (encoded polyline, precision 5; decode in shared code). Show total time/distance and per-leg times between pins.
- A→B: from the place sheet or search, "Route from here / to here" → `GET /routes?from=…&to=…&mode=…`.
- Mode selector: walking (default), cycling, driving. No public transport.
- **Along the way**: show `alongTheWay` pins and a horizontal list under the map ("in 12 min · 80 m off route"), ordered along the route. Each item has "Add to trip".
- Refetch the day route after marker add/move/delete/reorder (including realtime events).
- `ROUTING_UNAVAILABLE` → show straight lines between markers with a small "Route unavailable" note. A `degraded: true` flag means the same.

## Best route

`POST /days/{id}/optimize` → preview the new order (animate the pins or show a list) → "Apply" calls `?apply=true`.
- `mode: "straight_line"`: free result; show a hint that premium uses real travel times (see `premium-and-paywall`).
- `mode: "travel_time"`: show "N min shorter" from `savedMinutes`.
