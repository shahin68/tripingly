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

1. On **camera idle**, wait 300 ms (debounce), cancel the previous request, then call `GET /places/in-view?bbox=…&zoom=…&categories=…`. The bbox is sent as plain decimals (the server rejects exponents). *(Built: `MapViewModel.loadPlaces`.)*
2. The repository keeps the last answer with the area the server answered for (it widens the box to quarter tiles at the zoom level), so a small pan or zoom inside it asks nothing for 60 s (the server's cache time). No longer cache (decided 2026-10-09).
3. Zoomed out (< 14): Tripinly places and server clusters, plus OSM places with a Wikidata entry from zoom 10 while hot spots are few. Zoomed in (≥ 14): OSM dots too. The server's picks are fixed on the map, so panning keeps the same dots (backend #25).
   - Pins still in view stay when the next answer leaves them out, unless the map zoomed out (`MapViewModel.loadPlaces`). On Android, new pins fade in and leaving ones fade out (250 ms) with one shared fade per answer, however many pins it brings. iOS doesn't fade yet; that waits for the iOS map decision.
   - Pins are Google Maps markers (decided 2026-10-10) showing our own category icon (Material icons for now, Shahin's designs later): café, restaurant, bar, attraction, museum, historic, park, nature, landmark. Pins that look the same share one bitmap, drawn the first time it's needed (`drawPlacePin`), so a new pin costs no drawing.
   - Hot spots (liked on public trips) use the Tripinly color and grow with their likes: 24 dp, 30 dp from 5 likes, 36 dp from 20 (`hotSpotSize`). OSM pins are 22 dp, the tapped place 44 dp.
   - Planned as its own stage after stage 5 (Shahin, 2026-10-10): an importance score so famous places show when zoomed out, one marker per city when zoomed far out, illustrated markers.
4. Category filter chips (cafés, restaurants, attractions, museums, parks…) change the `categories` parameter.
5. Tapping a place → its pin grows, the camera glides to center it at the same zoom, and a card (`PlaceCard`) floats just above it, with nothing dimmed. A touch anywhere outside the card only closes it; it doesn't reach the map. The card shows name, category and likes from the pin at once, opening hours and website from `GET /places/{id}`, "Add to day N" for owners and editors. Adding goes through the optimistic marker queue as `POST /days/{id}/markers` with `placeId` (instant, like a tap), not `POST /places/{id}/add-to-trip`. Photos come with stage 6. Tapping a cluster zooms in two levels on it.
6. Handle `BBOX_TOO_LARGE` by showing nothing new (the user zoomed out too far).

## Search

- Debounce 300 ms, from the first letter (one letter returns Photon prefix matches only), cancel in-flight requests. Send the map center as `lat/lng` for ranking.
- Results: our places (have `id`) first, then addresses/cities (`source: "photon"`, no `id`).
- *(Built: search icon in the map header; picking a result moves the camera at a zoom by its type, country 5 … street or place 17.)* Planned with stop names: picking a place with `id` → move the camera, open the place card. Picking an address/city → move the camera; "Add to trip" sends `name` + `location` (+ `osmType`/`osmId`) to `POST /days/{id}/markers`.
- A **long press** on the map creates a custom pin: user names it → `POST /days/{id}/markers` with name + location. *(Built on Android; iOS has no map gestures yet.)*

## Routes and places along the way

- Trip screen: toggle "Show route" for the selected day → `GET /days/{id}/route?mode=walking&categories=…`. Draw the decoded polyline (encoded polyline, precision 5; decode in shared code). Show total time/distance and per-leg times between pins.
- A→B: from the place card or search, "Route from here / to here" → `GET /routes?from=…&to=…&mode=…`.
- Mode selector: walking (default), cycling, driving. No public transport.
- **Along the way**: show `alongTheWay` pins and a horizontal list under the map ("in 12 min · 80 m off route"), ordered along the route. Each item has "Add to trip".
- Refetch the day route after marker add/move/delete/reorder (including realtime events).
- `ROUTING_UNAVAILABLE` → show straight lines between markers with a small "Route unavailable" note. A `degraded: true` flag means the same.

## Best route

`POST /days/{id}/optimize` → preview the new order (animate the pins or show a list) → "Apply" calls `?apply=true`.
- `mode: "straight_line"`: free result; show a hint that premium uses real travel times (see `premium-and-paywall`).
- `mode: "travel_time"`: show "N min shorter" from `savedMinutes`.
