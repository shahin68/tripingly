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
- iOS `actual`: `UIKitView` hosting `GMSMapView`, a delegate forwarding events, diffing state into `GMSMarker`/`GMSPolyline`. *(Built: the SDK has no Kotlin binding, so Swift (`iosApp/GoogleMapsSdk.swift`) implements the `GoogleMapsSdk`/`GoogleMap` interfaces of `feature:map` and the shared `GoogleSdkMapView` drives it. The key is `MAPS_API_KEY_IOS` in the root `local.properties`; without it, Apple's map (`AppleMapView`) is shown, which takes no taps.)*
- If the repo already has a different abstraction, extend that instead.

## Pins

| Pin | Look | Source |
|---|---|---|
| Trip marker with photo | Larger round pin with the **cover thumbnail** inside plus day-order number | Trip `markers[].coverThumbUrl` |
| Trip marker without photo | Numbered pin in the day color | Trip markers |
| Place with a photo | Round pin (40 dp) with the place's image (`coverThumbUrl`) inside a ring of its pin color; for places with prominence ≥ `photoPinMinProminence` (90) or ≥ `photoPinMinLikes` (3) likes, the first `mapMaxPhotoPins` (100) in pin order (all from `GET /app-config`). The icon shows until the image has loaded | Place shapes' `coverImageId`, `coverThumbUrl` (backend#45) |
| Tripinly place (not in trip) | Medium "unselected" pin with category icon and like count | `in-view` with `isTripinly: true` |
| OSM place | Small dot with category icon, name only at high zoom | `in-view` with `isTripinly: false` |
| Along the way | Like the other place pins, but violet (`AlongTheWayPinColor`) with a white icon, also when the place was already a pin | `alongTheWay` |
| Cluster | Circle with count | `in-view` cluster items |

- Render thumbnails to bitmaps off the main thread (download with the app's image loader, crop circle). Android: `BitmapDescriptor`; iOS: `UIImage` icon (prefer icon images over `iconView` for performance).
- Built (stage 6): `rememberCoverThumbs` loads each cover through Coil's singleton loader, cached by **photo ID + size**, not by the signed URL (it changes about every hour). Android draws the pin with `MarkerComposable` + `TripMarkerIcon(cover)`, iOS paints it with Skia (`drawCoverPin`). A pin is redrawn only when its number, color or cover changes.
- **Dots when zoomed out** (owner, 2026-10-19, like Google Maps): below camera zoom `pinDotBelowZoom` (server, `APP_PIN_DOT_BELOW_ZOOM`, default 14) every place pin, photo pins included, turns into a small dot of its pin color (`PlaceDotSize` 10 dp, white ring), so route lines and stops stay readable; zooming back in grows them into pins. The tapped place, stops and clusters stay as they are. The change is instant, with no animation (owner, 2026-10-19: a fade was tried, a scale is too costly on Android; revisit later): Android gives each pin a hidden dot marker from the start and only shows one and hides the other (making or dropping 150 markers mid-pinch lagged, and changing a marker's icon makes every pin blink; each pin's markers sit under one key by place ID so a pin reload doesn't remake them all), iOS changes the icon (Swift `setMarkers`). A real shrink would redraw every pin icon on every frame, which costs battery and blinks on Android. Android reads the live zoom from the camera; iOS reports it from Swift (`onZoomChange`).

### Map look: where to change it

| What | Value | Where |
|---|---|---|
| Zoom below which place pins are dots | 14 (0–22) | Railway api `APP_PIN_DOT_BELOW_ZOOM` → `pinDotBelowZoom` |
| Dot size | 10 dp | `PlaceDotSize`, `PlacePins.kt` |
| Pin ↔ dot change | instant, no animation | `GoogleMapView.android.kt` (`isDots`), Swift `setMarkers` |
| Along-the-way pin color | violet `0xFF8E24AA` | `AlongTheWayPinColor`, `PlacePins.kt` |
| Place pin sizes | 18 / 30 / 36 dp by prominence (`pinMediumMinProminence` 60, `pinLargeMinProminence` 95 from the server) | `PinSizes`, `PlacePins.kt` |
| Route line | by way of travelling: walk green `0xFF2E7D32`, bike orange `0xFFEF6C00`, car blue `0xFF1E88E5` (also the mode chip's color), 5 dp, arrows, rounded ends, one 1.2 s streak | `TravelMode.lineColor`, `RouteWidth`, `routeLine()`, `MapLine.kt` |
| Direction lines (no route) | slate blue `0xFF3D5A80`, 3 dp, arrows, rounded ends, one 1.2 s streak | `StopPathStyle`, `MAP_LINE_SWEEP_MILLIS`, `StopPath.kt` |
| Room around a fit's points | 48 dp, inside the map's padding | `FitMargin`, `GoogleMapView.kt` |
| Camera move and fit time | 180 ms | `CAMERA_MOVE_MILLIS`, `GoogleMapView.kt` |
| Smallest area a fit shows | about 500 m (0.005°) around one stop | `MIN_SPAN_DEGREES`, `CameraCommand.kt` |
| Zoom on a trip's destination (no stops) | 12, a city | `DESTINATION_ZOOM`, `MapCamera.kt` |
| Pulled-down plan sheet | 72 dp (handle and title row) | `PlanSheetPeek`, `MapPlanSheet.kt` |
| Fit math (opening and fit-day button) | 256 dp world at zoom 0, zoom 2–21 (the map SDKs' own, don't tune) | `fittedCamera`, `GoogleMapView.kt` |

- Use the Google Maps utility libraries for **client-side clustering** of trip markers only if needed. Place clusters come from the server.

## Stop card and photos

- Tapping a stop (pin or chip) centers the camera on it, then opens the **stop card** above the pin, with the same scale-in from its bottom center and the same close-on-any-other-touch as the place card. Info windows are off for stops on both platforms.
- The card shows the cover thumbnail (a stop without a cover shows its place's first image with its credit, asked for once per place per map), the stop's name and its photo count; owners and editors get **Add photos** (system picker: Android Photo Picker, iOS PHPicker, several at once, no storage permission). Picking opens the photo sheet so the uploads are visible. Next to it, the **camera** button opens the in-app camera full screen (live picture, shutter; it asks for the camera permission the first time). A photo uploads the moment it's taken, with no review step, and the sheet opens to show it going up. The sheet's header has the same camera button.
- The camera shows the whole picture (fit, not cropped) so the photo shows what the user saw. The shutter sits under the picture in portrait and beside it in landscape; photos come out upright however the phone is held (Android follows the phone's sensor, iOS uses `AVCaptureDeviceRotationCoordinator`). No zoom or tap-to-focus yet (continuous autofocus). The iOS simulator has no camera and says so.
- Tapping the card opens the **photo sheet** (`StopPhotosRoute`, a bottom sheet with its own view model per stop): the gallery grid (processing and failed photos too for members), uploads with progress, Retry and Remove, and a full-screen viewer (swipe, Make cover, Delete with confirmation for owners and editors). Comments will go into this sheet in stage 7.
- Uploads run from the app scope (`PhotoRepository`): each image is shrunk to `photoMaxEdgePx` and re-encoded as JPEG at `photoJpegQuality` on the phone (this drops EXIF, location included), sent straight to storage through the signed URL with `photoParallelUploads` at a time, then confirmed. Until real time arrives (stage 9) the app asks for the gallery every 3 s, up to 20 times, while photos are processing, then reloads the stop for its cover and count.

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
2. The repository gathers the squares asked for within 30 ms (the map asks for a screen's tiles together) into one request per 16 and shares a square that is still loading. A caller that stops waiting doesn't cancel the request. Loaded squares are kept until newer ones push them out (at most 200), **stale-while-revalidate** (Shahin, 2026-10-10): a square older than `placesRefreshSeconds` from `GET /app-config` (default 300 s) is answered at once from what's kept and reloaded in the background, in the same batched request as other squares. Only when the reloaded places differ is the square reported (`PlaceRepository.changedSquares`) and its pins updated when it is in view (only the changed markers change). A failed reload keeps the old places and is tried again the next time the square is asked for; a square that never loaded isn't kept.
3. When the camera rests, the squares in view load (kept ones answer at once); once it has rested 300 ms, the ring of squares around the view loads too (only the ones not loaded yet), so a pan finds its places ready (`MapViewModel.onCameraMove`).
4. Zoomed out (< 14): Tripinly places and server clusters, plus OSM places with a Wikidata entry from zoom 10 while hot spots are few. Zoomed in (≥ 14): OSM pins too. The server's picks are fixed on the map, so panning keeps the same pins (backend #25).
   - Decided 2026-10-13 (Shahin, "Markers"): pins are **native map markers** that keep their size on screen while the camera zooms (tiles stretched between whole zoom levels). `MapViewModel` builds the pin list (`placePins`) when the camera rests, from the loaded squares in view: places inside the view that aren't stops, liked places first (most liked first), then by prominence, at most `mapMaxPins` from `GET /app-config` (default 150). A square that changes or a stop added or deleted rebuilds the list. The map diffs markers by place id, so a pin that stays keeps its marker; new pins fade in (250 ms). Each pin look and cluster count is drawn once into a bitmap and reused; a photo pin's look is its image id, loaded through Coil by that id (`rememberPinPhotos`). Decided 2026-10-17 (C/D in `pins-and-saving-decisions.md`): photo pins, capped per screen.
   - A tap on a pin or cluster is the marker's own tap (`OnPlacePinClick`, `OnClusterClick`); a tap on the bare map adds a stop as before. The tapped place is a 44 dp marker in place of its pin.
   - **Android:** maps-compose `Marker`s. **iOS** (Google Maps SDK): `GMSMarker`s through `GoogleMapsSdk.setMarkers`, like the stops.
   - **Stays once shown** (backend, 2026-10-13): the server puts well-known places (prominence ≥ 60) first in every square, by prominence, so a place shown at one zoom is still shown closer in.
   - **Bottom of the trip map (Shahin, 2026-10-22):** the day tabs are fixed at the bottom, over the navigation bar. Tapping a tab opens that day without moving the camera; a small fit-day button above the location button frames the open day (Shahin, 2026-10-22). Everything else of the day's plan (the stops card, the A→B route card) is in a non-modal sheet that rests on the tabs: a drag anywhere on it pulls it up to show all of it or down to leave its title row (72 dp), and a tap on its handle does the same; the credits and the two buttons ride on its top. The sheet slides up when the day has a plan and slides down when it has none. Pulling the sheet never moves the camera, but the fit-day button frames the day in the map the sheet leaves free at that moment: pulled up, the stops fit above it; pulled down, it's the same fit as on opening (Shahin, 2026-10-22). A trip's map opens already fitted to the open day: the view model hands the area over as `CameraState.openingBounds` and `MapScreen` works out the starting camera with `fittedCamera` (the SDKs' own Web Mercator fit) from the map's size and its padding, so the map never starts somewhere and then fits.
   - **Google's logo** stays above the bottom controls: the map gets the header's height as top padding and, as bottom padding, the day tabs (with the navigation bar) and the credits line, each measured once with `onSizeChanged` when its size changes, plus the pulled-down sheet (`PlanSheetPeek`) while there is a plan. The buttons and a pulled-up sheet float over the map and don't count, so the logo, centering and every fit stay in the part of the map nothing covers. Fitting the open day's stops is worked out by `fittedCamera` (the SDKs' own Web Mercator fit) inside that padding less the pulled-up part of the sheet, with a 48 dp margin and at least about 500 m shown around a single stop; no measured heights reach the view model. The bounds reported to the view model are the whole view's, not the padded area's.
   - Pins show our own category icon (Material icons for now, Shahin's designs later): café, restaurant, bar, attraction, museum, historic, park, nature, landmark. Every pin look is drawn once (`drawPlacePin`); markers reuse it.
   - Pins are sized by how well known the place is (`prominence`, 0–100, Wikimedia page views and OSM tags; Shahin, 2026-10-13): 36 dp from `pinLargeMinProminence` (default 95), 30 dp from `pinMediumMinProminence` (default 60), else 18 dp; both thresholds come from `GET /app-config` (`placePinSize`). Hot spots (liked on public trips) use the Tripinly color and also grow with their likes, 24 dp, 30 dp from 5 likes, 36 dp from 20 (`hotSpotSize`), taking the bigger of the two sizes. The tapped place is 44 dp. A photo inside the pin comes with place-photos PR 5 (planned).
   - Planned as its own stage after stage 5 (Shahin, 2026-10-10): an importance score so famous places show when zoomed out, one marker per city when zoomed far out, illustrated markers.
5. Category filter chips (cafés, restaurants, attractions, museums, parks…) change the `categories` parameter.
6. Tapping a place → its pin grows, the camera glides to center it at the same zoom (400 ms), and only once it rests there does a card (`PlaceCard`) open just above the pin, scaling in from the card's bottom center so it seems to come out of the pin, with nothing dimmed. A place already centered opens its card at once; if the camera comes to rest elsewhere (the user dragged), the place closes. The view model decides this (the place card's state is only there once the camera rests on it), so the card never moves with the map. A rotation keeps the camera and the open card. A touch anywhere outside the card (the map, another pin, the search bar, the day card) closes it at once, scaling it back into the pin, and still does what it touched: another pin opens, the map moves. Only a tap on the bare map that closes a card adds no stop. The card shows name, category and likes from the pin at once, opening hours and website from `GET /places/{id}`, "Add to day N" for owners and editors. Adding goes through the optimistic marker queue as `POST /days/{id}/markers` with `placeId` (instant, like a tap), not `POST /places/{id}/add-to-trip`. Above the name, the card shows the place's images (`images[]` from `GET /places/{id}`, trip photos first, then Wikimedia Commons), swiped through and cropped to the card, with a grey box while they load and the category icon when there are none (Shahin, 2026-10-11; his art will replace the icons). Each Commons image shows its credit under it ("© author · licence · Wikimedia Commons", the title first for licences before 4.0, "author · Public domain", plus "· cropped" where it's cropped); the author and "Wikimedia Commons" open the file page, the licence its text. A tapped image opens full screen, uncropped, with its credit and a Report button (a trip photo as `photo`, a Commons image as `place_image`). Tapping a cluster zooms in two levels on it.
7. A square that fails to load is asked for again the next time the camera rests over it.

## Search

- Debounce 300 ms, from the first letter (one letter returns Photon prefix matches only), cancel in-flight requests. Send the map center as `lat/lng` for ranking.
- Results: our places (have `id`) first, then addresses/cities (`source: "photon"`, no `id`).
- *(Built: search icon in the map header; picking a result moves the camera at a zoom by its type, country 5 … street or place 17.)* Picking one of our places opens its card; picking an address opens a card with "Add to day N" (`name` + `location` + `osmType`/`osmId`); a city or larger area only moves the camera.
- A **long press** on the map creates a custom pin: user names it → `POST /days/{id}/markers` with name + location. *(Built on Android and on iOS with Google Maps.)*

## Routes and places along the way

- Trip screen: toggle "Show route" for the selected day → `GET /days/{id}/route?mode=walking&categories=…`. Draw the decoded polyline (encoded polyline, precision 5; decode in shared code). Show total time/distance and per-leg times between pins.
- A→B: from the place card or search, "Route from here / to here" → `GET /routes?from=…&to=…&mode=…`.
- Mode selector: walking (default), cycling, driving. Public transport (tram, U-Bahn, bus) is being decided (owner, 2026-10-19): openrouteservice has none on its hosted service.
- **Along the way**: show `alongTheWay` pins and a horizontal list under the map ("in 12 min · 80 m off route"), ordered along the route. Each item has "Add to trip".
- Refetch the day route after marker add/move/delete/reorder (including realtime events).
- **Routes are Tripinly Pro** (`routes` in `GET /me` entitlements; the server answers 403 `PREMIUM_REQUIRED` otherwise). Without Pro, the Route button and "Route from here" show a "comes with Tripinly Pro" note (the paywall later).
- **One line style** (product owner, 2026-10-18): the map shows the day's direction lines or a real route, never a server straight-line estimate on top. `ROUTING_UNAVAILABLE` or `degraded: true` → no route drawn, the direction lines stay, and a small "Routes aren't available right now" note.

*(Built in stage 8: the Route button on the stops card turns the day's route on; leg times sit between the stop chips; Walk/Bike/Car chips; the places along the way are a row in the same card and pins on the map. "Route from here" / "Route to here" on place cards, the A→B card replaces the stops card until closed. Routes are kept in memory by stops and mode, so the same stops are routed once; a day is routed after its own stop changes reached the server. The MapKit fallback on iOS draws no route.)*

*Direction lines: while no route shows, the open day's stops are joined by straight slate-blue lines (`StopPathColor`, quieter than the route's bright blue) with a flat arrow halfway along each stretch pointing to the next stop (`StopPath.kt`). When the line appears or changes, a light streak runs along it once (1.2 s, like a progress bar); nothing animates after that, because a lasting animation redraws the map every frame and heats the phone (owner, 2026-10-18). Drawn on the device with no routing call. Straight since 2026-10-18: the owner tried the curve and the coral and didn't like them.*

*Every map line is one `MapLine` (points, arrows, `MapLineStyle` color and width) drawn by one piece of code per platform (`MapLineView` on Android, Swift `setLine`/`setLineSweep` on iOS): rounded ends (Android `RoundCap`; iOS draws a dot as wide as the line on each end, since `GMSPolyline` has square ends), flat arrows, and the one-time streak. Real routes (owner, 2026-10-19) look the same as the direction lines but take their way of travelling's color, and the mode chips on the route card use the same color. A route starts and ends exactly on the first and last stop (joined to the street the route starts on), has an arrow halfway between each two stops, and is cut into about 200 even steps so the streak glides at one speed on long straight roads. The color comes from the mode the shown route was asked for, so a line still loading a new mode keeps its old color until the new one arrives. A new line kind only needs a new style.*

## Best route

The Best order button sits on the stops card whether or not the route shows (owner, 2026-10-19), for owners and editors with 3–25 stops; it uses the selected travel mode (walking by default).
`POST /days/{id}/optimize` → preview the new order (a list in a dialog) → "Apply" saves it as a normal reorder (`PUT /days/{id}/marker-order`, optimistic like every stop change) rather than `?apply=true`, so it shows at once.
Free for everyone, by real travel times (product owner, 2026-10-18).
- `mode: "travel_time"`: show "N min shorter" from `savedMinutes`.
- `mode: "straight_line"` (`degraded`): travel times weren't available; say it's ordered by distance.
