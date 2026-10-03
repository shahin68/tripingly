---
name: resource-management
description: Use this skill when managing string resources and localization in Compose Multiplatform. Ensures all UI strings are extracted to XML files and never hard-coded.
---

# String Resource Management Skill

This skill defines the standard procedure for extracting, naming, and using string resources in the Tripingly project to facilitate future localization and maintain code hygiene.

## Core Rules

1.  **NO HARD-CODED STRINGS**: Every user-facing string MUST be extracted to a string resource file.
2.  **Location**: Each module owns its strings in `<module>/src/commonMain/composeResources/values/strings.xml` (e.g. `feature/trips/...`, `core/designsystem/...`), with `values-de/` and `values-hu/` next to it for the other launch languages. A module never uses another module's `Res`; a string two modules need either lives in `core:designsystem` behind a component, or is duplicated.
3.  **Naming Convention**: Use `snake_case` with semantic prefixing to group related strings.
    - Format: `<feature>_<component>_<description>`
    - Examples: `home_trip_list_empty`, `map_header_title`, `common_dialog_cancel`.
4.  **No Redundant Comments**: Do not add comments explaining that a string was extracted.
5.  **Multiplatform Usage**: Use `stringResource(Res.string.key_name)` for access in Composables.

## Extraction Workflow

### 1. Identify Hard-coded Strings
Scan Composable files and ViewModels for string literals used in the UI.

### 2. Define in `strings.xml`
Add the string to the owning module's `composeResources/values/strings.xml` (and the `de`/`hu` files) following the naming convention.

```xml
<resources>
    <string name="feature_component_description">Actual String Value</string>
</resources>
```

### 3. Replace in Code
Replace the literal with the generated resource accessor. Ensure the necessary imports are present:
`import com.falcon.tripingly.feature.trips.generated.resources.Res`
`import com.falcon.tripingly.feature.trips.generated.resources.*`

The `Res` package is `<module namespace>.generated.resources`, set by the `tripinly.kmp.compose` convention plugin, and the class is internal to the module.

ViewModels don't resolve strings with `getString`; they put a `UiText` (resource + args, or the server's localized message) in state and the composable resolves it.

## Formatting & Plurals
- Use `%s`, `%d` for placeholders.
- If plurals are needed, define them in a `<plurals>` tag within the same file.

## Verification
Always run a Gradle build after extraction to ensure the `Res` object is generated and accessible.
