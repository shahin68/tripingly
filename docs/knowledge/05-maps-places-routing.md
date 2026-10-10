# Tripinly — Maps, Places and Routing (client view)

## The stack

| Part | What the client uses |
|---|---|
| Drawing the map | **Google Maps SDK**: Android via the existing integration (maps-compose if already used), iOS via Google Maps SDK for iOS wrapped in `UIKitView` behind a shared interface |
| Places on the map | Tripinly backend `GET /places/tiles` (our places + OpenStreetMap POIs, by map square) |
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

## Stop names

Built 2026-10-10 (Shahin asked 2026-10-08, chose "Number + name"):

- A chip shows the stop's number badge and its name; the pin's title is the name. Numbers come from the stop's position in the day, so they follow deletes and reorders.
- A stop added from a place (pin card, search result of ours) is created with `placeId` and named after the place.
- A tap on the map sends only `location`. The server names it (our public place within 25 m, else the Photon address, else the coordinates, backend#28) and the app takes the name from the create answer. Until it answers, the chip shows "Stop N". A name the user already gave (long press, or a rename while it was sending) is never replaced.
- An address picked in search (house, street, Photon place) opens a card with its address and "Add to day N", which sends its name, location and OSM ids. Cities and larger areas only move the map.
- Stops saved before this keep their old "Stop #N" name until renamed.

## Loading places while browsing

Decided 2026-10-10 (Shahin, "Tiles + prefetch"): places load and draw like Google Maps' own, without a request after the map stops or a hitch when pins appear.

1. Places load by **map square**, not by view: square `level/x/y` is `360 / 2^level` degrees a side on a grid fixed on the map (`PlaceSquare`), at `level = zoom − 2` (about four map tiles a side). `GET /places/tiles?tiles=…&zoom=&limit=200` answers up to 16 squares at once, each like in-view for its area. *(Built: `PlaceRepository.placesIn`.)*
2. The repository gathers the squares asked for within 30 ms (the map asks for a screen's tiles together) into one request per 16 and shares a square that is still loading. A caller that stops waiting doesn't cancel the request. Loaded squares are kept until newer ones push them out (at most 200), **stale-while-revalidate** (Shahin, 2026-10-10): a square older than `placesRefreshSeconds` from `GET /app-config` (default 300 s) is answered at once from what's kept and reloaded in the background, in the same batched request as other squares. Only when the reloaded places differ is the square reported (`PlaceRepository.changedSquares`) and redrawn when it is in view: Android drops all its tiles 300 ms later (Google's `TileOverlay` can't redraw one tile) and paints them again from the kept squares; iOS reloads the squares in view. A failed reload keeps the old places and is tried again the next time the square is asked for; a square that never loaded isn't kept.
3. Once the camera has rested 300 ms, the ring of squares around the view loads too (only the ones not loaded yet), so a pan finds its places ready (`MapViewModel.onCameraMove`).
4. Zoomed out (< 14): Tripinly places and server clusters, plus OSM places with a Wikidata entry from zoom 10 while hot spots are few. Zoomed in (≥ 14): OSM pins too. The server's picks are fixed on the map, so panning keeps the same pins (backend #25).
   - **Android:** pins are painted into map tiles (`PlaceTileProvider`, a Google Maps `TileOverlay`) on the map's own background threads. Each tile draws the squares under it at the tile's zoom, plus pins reaching in from the next squares, and the map keeps the tiles and fades them in. However many pins there are, none costs the main thread a frame. Stops hide their place's pin: when the stops' places change, the tiles are drawn again.
   - A tap is matched in `MapViewModel` against the places of the loaded squares at the camera's zoom (`floor(zoom)`): the nearest pin or cluster within 24 points opens; else the tap adds a stop as before. The tapped place is a real marker (44 dp) on top of its tile pin.
   - **iOS** (MapKit, until the iOS map decision): annotations for the squares in view, loaded 300 ms after the region rests.
   - Pins show our own category icon (Material icons for now, Shahin's designs later): café, restaurant, bar, attraction, museum, historic, park, nature, landmark. Every pin look is drawn once (`drawPlacePin`); tiles copy them.
   - Hot spots (liked on public trips) use the Tripinly color and grow with their likes: 24 dp, 30 dp from 5 likes, 36 dp from 20 (`hotSpotSize`). OSM pins are 22 dp, the tapped place 44 dp.
   - Planned as its own stage after stage 5 (Shahin, 2026-10-10): an importance score so famous places show when zoomed out, one marker per city when zoomed far out, illustrated markers.
5. Category filter chips (cafés, restaurants, attractions, museums, parks…) change the `categories` parameter.
6. Tapping a place → its pin grows, the camera glides to center it at the same zoom, and a card (`PlaceCard`) floats just above it, with nothing dimmed. A touch anywhere outside the card only closes it; it doesn't reach the map. The card shows name, category and likes from the pin at once, opening hours and website from `GET /places/{id}`, "Add to day N" for owners and editors. Adding goes through the optimistic marker queue as `POST /days/{id}/markers` with `placeId` (instant, like a tap), not `POST /places/{id}/add-to-trip`. Photos come with stage 6. Tapping a cluster zooms in two levels on it.
7. A square that fails to load is asked for again later (the map retries a tile it got no answer for).

## Search

- Debounce 300 ms, from the first letter (one letter returns Photon prefix matches only), cancel in-flight requests. Send the map center as `lat/lng` for ranking.
- Results: our places (have `id`) first, then addresses/cities (`source: "photon"`, no `id`).
- *(Built: search icon in the map header; picking a result moves the camera at a zoom by its type, country 5 … street or place 17.)* Picking one of our places opens its card; picking an address opens a card with "Add to day N" (`name` + `location` + `osmType`/`osmId`); a city or larger area only moves the camera.
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
