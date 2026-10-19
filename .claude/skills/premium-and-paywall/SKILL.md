---
name: premium-and-paywall
description: Use for Tripinly premium features in the app — reading entitlements, showing premium vs free behaviour (routes along streets), upsell hints, and the paywall/purchase flow once the billing provider is decided.
---

# Premium and paywall

## Now (billing not decided)

- Entitlements come from `GET /me` (today `routes`). Store them in the session state; refresh on app foreground and after purchase.
- Routes (day route, A→B, places along the way) need `routes`; without it the Route button and "Route from here" show a "comes with Tripinly Pro" note (the paywall later). The server enforces it with 403 `PREMIUM_REQUIRED`.
- Best order is free for everyone: always call `POST /days/{id}/optimize`; `travel_time` → "N min shorter", `straight_line` (degraded) → "ordered by distance".
- Never unlock by a local flag. Debug builds may have a developer menu that *displays* entitlement state, but not one that fakes it.
- Dev accounts on staging (`DEV_ACCOUNTS_PRO`) and the fake-API build have Pro until the paywall exists.

## Later (after the user decides — see open question 1)

If RevenueCat is chosen:
- Use RevenueCat's KMP SDK; configure with the Tripinly user ID as the app user ID after sign-in; log out on sign-out.
- Paywall shows offerings (monthly/yearly) from RevenueCat, localized prices from the store, restore purchases, terms and privacy links (store requirement).
- After a purchase, refresh `GET /me` (the backend learns about it by webhook; poll for up to ~10 s).
- Apple requires a "Restore purchases" button; Google Play requires correct subscription management links.

Ask the user before implementing any purchase code.
