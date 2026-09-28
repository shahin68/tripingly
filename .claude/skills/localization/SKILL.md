---
name: localization
description: Use when adding or changing any user-facing text, supporting a new language, or formatting dates, times, distances, durations and counts in the Tripinly app.
---

# Localization

- All user-facing text lives in the project's string resources (Compose Multiplatform resources `composeResources/values*/strings.xml` if that's what the repo uses; otherwise follow the existing system). No literals in UI code.
- Every new key is added for **all** supported languages in the same change (default: English, German, Hungarian). If you can't translate confidently, add the English text and list the keys in your report as "needs translation".
- Plurals use plural resources ("1 like" / "12 likes").
- Server texts (error `message`, push) come localized: send `Accept-Language` with the app language on every request and in device registration.
- Place names: the API returns the localized name when available; don't translate place names yourself.
- Formatting with the user's locale: dates (`14 May` / `14. Mai` / `május 14.`), times (24h in DE/HU), distances (metric: m under 1 km, then km with one decimal), durations ("12 min", "1 h 20 min").
- Layouts must handle long German words and Hungarian text: no fixed-width buttons, allow two lines.
- Language change in the app (if supported) → re-register the device and refetch visible data.
