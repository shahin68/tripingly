---
name: premium-and-paywall
description: Use for Tripinly premium features in the app — reading entitlements, showing premium vs free behaviour (best route by real travel time), upsell hints, and the paywall/purchase flow once the billing provider is decided.
---

# Premium and paywall

## Now (billing not decided)

- Entitlements come from `GET /me` (for example `best_route_realtime`). Store them in the session state; refresh on app foreground and after purchase.
- Best route: always call `POST /days/{id}/optimize`; the server returns `mode`. For `straight_line`, show the result plus a small hint: "Premium plans this with real walking times". For `travel_time`, show "N min shorter".
- Never unlock by a local flag. Debug builds may have a developer menu that *displays* entitlement state, but not one that fakes it.
- Route lines and places along the way are free: no premium UI there.

## Later (after the user decides — see open question 1)

If RevenueCat is chosen:
- Use RevenueCat's KMP SDK; configure with the Tripinly user ID as the app user ID after sign-in; log out on sign-out.
- Paywall shows offerings (monthly/yearly) from RevenueCat, localized prices from the store, restore purchases, terms and privacy links (store requirement).
- After a purchase, refresh `GET /me` (the backend learns about it by webhook; poll for up to ~10 s).
- Apple requires a "Restore purchases" button; Google Play requires correct subscription management links.

Ask the user before implementing any purchase code.
