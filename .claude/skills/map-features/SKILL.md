---
name: map-features
description: Use for any Tripinly map work on Android or iOS — Google Maps setup and style, hiding Google POIs, trip marker pins with photo thumbnails, in-view places from the backend, clustering, search, custom pins, route lines, places along the way, best route preview and OSM attribution.
---

# Map features

Read `docs/knowledge/05-maps-places-routing.md` first. Check `06-current-state.md` for how the map is currently integrated on each platform and extend that.

## Checklist for any map change

- [ ] Works on **Android and iOS** (iOS uses Google Maps SDK for iOS inside `UIKitView`).
- [ ] Map style hides Google POIs (`featureType: "poi"`, `visibility: "off"`; keep `poi.park` geometry if it looks better, but no labels/icons).
- [ ] No Google Places/Geocoding/Directions calls; no `onPoiClick` usage; no Google data sent to the backend.
- [ ] "© OpenStreetMap contributors" visible when our places/routes are shown; "© openrouteservice.org" when a route is shown; Google logo not covered (use map padding for bottom sheets).
- [ ] State lives in shared code (`MapState`); platform code only renders and forwards events.
- [ ] Works in **landscape** and survives a rotation: the camera (target and zoom) and selections stay as they were, nothing refits or reopens, and cards and overlays fit the short height (see `07-architecture.md`, Landscape).

## Implementation notes

**Opening a trip:** `MapViewModel` restores the open day (by id, first day if it's gone), Route on (only with Pro) and the travel mode from `TripMapViewRepository` (DataStore), then frames the day with `MapCamera.showOpenDay`, the same call the fit-day button makes (a day tab doesn't move the camera; Shahin, 2026-10-22): the day's stops, else the whole trip's, else the destination. The camera position isn't saved. A new map opens already fitted to that area (`openingBounds` worked out by `fittedCamera` in `MapScreen`); never start a map somewhere and fit it after. It saves the view 500 ms after it last changed. Keep anything new that a person sets on the map and expects back in that same record.

**Camera idle → places by square:** load the squares in view (`PlaceRepository.placesIn`, kept squares answer at once) and prefetch the ring around them 300 ms later; build the pin list in `MapViewModel` (`placePins`: in view, not a stop, liked first then by prominence, capped by `mapMaxPins` from app-config, sized by `placePinSize`). Keep previous pins until the new ones arrive (no flicker). Pins are native markers so they keep their size while the camera zooms.

**Diffing pins:** keep a map `id → native marker`; add/remove/update only what changed. Never clear and redraw all markers on each update.

**Thumbnail pins:** load the cover image with the image loader at the target pixel size, crop to a circle, draw border and order number, cache the result by (url, size, number, selected). Do this off the main thread. Placeholder pin until loaded.

**Z-order:** trip markers > selected place > Tripinly places > along-the-way > OSM dots.

**Clusters:** server clusters (`cluster: true`) → tap zooms in by 2 levels centered on it.

**Long press:** custom pin → sheet asking for a name and the target day → `POST /days/{id}/markers` with name + location.

**Routes:** decode the encoded polyline (precision 5) in shared code; draw every line as a `MapLine` (`MapLine.kt`: points, arrows, `MapLineStyle`) through the one line drawer per platform, so routes and direction lines share rounded ends, arrows and the one-time streak; a route is 5 dp in its travel mode's color (`TravelMode.lineColor`, also on the mode chips) and starts and ends on the first and last stop; a new kind of line only needs a style; fit the camera to the route with padding for sheets. Leg times appear as small labels between pins or in the day list.

**Along the way:** violet pins (`AlongTheWayPinColor`) + horizontal list under the map, ordered by `positionAlongRoute`; list item tap ↔ pin selection stay in sync.

**Zoomed out:** below `pinDotBelowZoom` (from `GET /app-config`) place pins turn into dots and back at once (no animation for now, owner's call); never animate pin icons frame by frame. Every map look value and where it lives is in the "Map look: where to change it" table of `docs/knowledge/05-maps-places-routing.md`; add new ones there.

**Best route:** preview the proposed order (reordered list + numbered pins) before applying; "Apply" → `?apply=true`; show `savedMinutes` (free for everyone); a straight-line result says travel times weren't available. Routes are Pro; the map shows the app's straight direction lines with arrows or a real route, never both.

**Performance:** at most a few hundred native markers; rely on the server's limits and clustering. Pause in-view loading while the user drags.

## Tests (commonTest)

Polyline decoding, bbox rounding and cache key, pin diffing logic, MapState reducer for camera idle / selection / route toggle.
