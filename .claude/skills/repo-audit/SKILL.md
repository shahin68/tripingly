---
name: repo-audit
description: Use at the start of work on the Tripinly KMP repo (first session, or when 06-current-state.md is empty or outdated) to map the existing code, stack and screens before making changes.
---

# Repo audit

Goal: understand the existing app well enough to extend it without breaking its patterns, and record that in `docs/knowledge/06-current-state.md`. **Don't change code during the audit** (except to get it building, and only after asking).

## Steps

1. **Build files:** read `settings.gradle(.kts)`, root and module `build.gradle(.kts)`, `gradle/libs.versions.toml`. Record modules, targets, Kotlin/Compose/AGP versions, every library and version.
2. **iOS side:** find the iOS app project (`iosApp/`), how the shared framework is integrated (CocoaPods, SPM, direct framework), iOS-only dependencies (Google Maps iOS SDK? Firebase?), minimum iOS version.
3. **Source sets:** what's in `commonMain`, `androidMain`, `iosMain`. Note `expect/actual` declarations.
4. **Architecture:** how screens are built (Compose Multiplatform everywhere, or SwiftUI on iOS?), navigation library, DI, state holders/view models, repositories, how data flows.
5. **Screens:** list every screen and feature (Home/My Trips, Social, trip creation, map…), where it lives, what data it uses (hardcoded, local DB, fake repo), and what's missing compared to `01-product-brief.md`.
6. **Maps:** how Google Maps is shown on Android **and** on iOS (iOS may be missing or a placeholder), how markers are drawn, whether there's a shared map abstraction, whether a map style is used.
7. **Models:** compare existing domain models with `03-api-contract.md` (IDs, names, dates, coordinates, nested days/markers). Note needed mappings.
8. **Config and secrets:** where API keys live (`local.properties`, `Info.plist`, `google-services.json`, `GoogleService-Info.plist`), what's committed, what's missing.
9. **Build and run** (CI runs these on every PR; the Claude cloud sandbox can't reach Google Maven) Android (`./gradlew :<app>:assembleDebug`) and, if possible, the iOS framework/app. Record commands and failures.
10. **Tests:** existing tests and how to run them.
11. **Boundaries:** check the module graph against `07-architecture.md` (no feature → feature dependency, domain packages free of Room/Ktor/Compose imports, DTOs and entities not leaking above the data layer, `internal` by default). Note every violation in Gaps and risks.

## Output

Fill every section of `06-current-state.md`: the screens table with the backend endpoints each screen will need, and a short **Gaps and risks** list (for example "iOS map not implemented", "models use Int IDs, API uses UUID strings", "no networking layer").

Then give the user a summary with the 3–5 most important findings and a proposed next step (usually the networking foundation from the build order). Ask before large changes such as adding a DI framework or navigation library.
