---
name: premium-and-paywall
description: Use for Tripinly premium features in the app — reading entitlements, showing premium vs free behaviour (routes along streets), the paywall, purchases and restore through RevenueCat.
---

# Premium and paywall

## Entitlements

- Entitlements come from `GET /me` (today `routes`); `Account.hasPro` is true with any of them (Tripinly Pro unlocks all). Never unlock by a local flag; the server enforces with 403 `PREMIUM_REQUIRED`.
- Routes (day route, A→B, places along the way) need `routes`; without it the Route button and "Route from here" open the paywall (`MapViewModel` sends `Event.OpenPaywall`).
- Best order is free for everyone: always call `POST /days/{id}/optimize`; `travel_time` → "N min shorter", `straight_line` (degraded) → "ordered by distance".
- Dev accounts on staging (`DEV_ACCOUNTS_PRO`) and the fake-API build have Pro without buying.

## Purchases (RevenueCat, built 2026-10-20)

- `StorePurchases` (`core:data/billing`) is the store: Android `RevenueCatPurchases` in `androidApp` (RevenueCat Android SDK), iOS `IosStorePurchases` over the Swift `NativePurchases` (`iosApp/NativePurchases.swift`, `RevenueCat` Swift package). Bound only when `revenuecat.androidKey` / `revenuecat.iosKey` is set in `local.properties`; otherwise `ProRepository.isAvailable` is false and the paywall says purchases aren't available.
- `ProRepository` logs the store in with our user id (`start()` from `MainNavigation`, and again before every purchase), so the backend's webhook knows whose purchase it is; sign-out logs it out (`LocalDataCleaner`).
- After a purchase or a restore that found Pro, it re-reads `GET /me` up to 5 times, 2 s apart: `Active` once the server says Pro, else `Confirming`. Restore that finds nothing is `NothingToRestore`; a closed sheet is `Cancelled` (no message).
- The paywall (`feature:paywall`) shows the current offering's monthly, yearly and lifetime packages (RevenueCat package types `$rc_monthly`, `$rc_annual`, `$rc_lifetime`) with the store's formatted prices; yearly is preselected. It always has Restore purchases, the renewal fine print and Terms/Privacy links (store requirements); a Pro account sees it active with Manage subscription (RevenueCat's management URL).
- Errors: `DataError.Purchase` (`Pending` is a note, `NotAllowed`, `NoInternet`, `Store` with the store's own message, `Unavailable`).
- Testing: RevenueCat's Test Store key works on both platforms without the Play/App Store accounts; real store keys and products come with the release checklist.

## Adding a premium feature

Confirm with Shahin it's premium, gate it on the entitlement from `GET /me`, open the paywall from the gated tap, and add the benefit line to the paywall in every language.
