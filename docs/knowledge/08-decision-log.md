# Tripinly Client — Decision Log

Append-only. Newest at the bottom.

| Date | Decision | Why | By |
|---|---|---|---|
| 2026-09-24 | Backend: NestJS REST (`/v1`, JSON, OpenAPI) + Socket.IO, built by a separate agent; client consumes the contract | Product owner | Product owner |
| 2026-09-24 | Sign-in only with Google and Apple; JWT access + rotating refresh tokens | Product requirement | Product owner |
| 2026-09-24 | Minimum age 16; versioned consent at onboarding | EU/GDPR | Product owner |
| 2026-09-24 | "Add to my trips" = one button creating an independent editable copy, no photos/comments | Product requirement (design prototype shows a bookmark; brief wins) | Product owner |
| 2026-09-24 | Multiple photos per marker, one user-chosen cover shown inside the map pin, swipeable gallery | Product requirement (design shows one photo; brief wins) | Product owner |
| 2026-09-24 | Find people by username handle and invite links; no follow system in v1 | Keep v1 small | Product owner |
| 2026-09-24 | Report and block in v1 | Store requirements | Product owner |
| 2026-09-27 | Keep **Google Maps SDK** for drawing the map (Android + iOS); hide Google POIs; no Google Places/Directions/Geocoding | Familiar and stable; MapLibre Compose not stable yet; Google data can't be stored and costs money | Product owner + agent |
| 2026-09-27 | All places, search and routes come from the backend (OpenStreetMap data, Photon, openrouteservice); show OSM attribution | Free and storable | Product owner + agent |
| 2026-09-27 | Map browsing shows Tripinly places prominently and OSM places as dots when zoomed in | Map never empty | Product owner |
| 2026-09-27 | Routes show places along the way (attractions, cafés, restaurants) | Product requirement | Product owner |
| 2026-09-27 | Route lines and along-the-way are free; best route by real travel time is premium, straight-line best route is free | Product requirement | Product owner |
