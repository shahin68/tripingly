---
name: ui-component
description: Use when adding or changing a reusable Tripinly design-system component in core:designsystem — tokens, slot APIs, stateless parameters, previews, strings and screenshot tests.
---

# Design-system component

Lives in `core/designsystem/src/commonMain/kotlin/com/falcon/tripingly/core/designsystem/component/`. Read `styling-and-theming` for tokens.

## When something belongs here

- Used, or clearly going to be used, by two or more features (buttons, cards, chips, empty and error states, search bar, avatar, photo thumbnail, map pin chrome).
- Feature-specific composables stay in `feature/<name>/presentation/component/`.

## API rules

- **Stateless.** Values in, events out: `TripCard(title: String, dateRange: String, coverUrl: String?, onClick: () -> Unit, modifier: Modifier = Modifier)`. No ViewModels, no feature `XAction`, no repositories, no domain models from a single feature.
- `modifier: Modifier = Modifier` is the first optional parameter and is applied to the root.
- Prefer **slots** for variable content: `leading: (@Composable () -> Unit)? = null`, `content: @Composable RowScope.() -> Unit`.
- Only theme tokens: `MaterialTheme.colorScheme`, `MaterialTheme.typography`, `MaterialTheme.shapes`, `MaterialTheme.spacing`. No hex colors, raw `dp` spacing or font sizes inside components.
- Text shown by the component itself (e.g. "Dismiss") comes from `core:designsystem` string resources; text the caller provides is a `String` parameter already resolved by the caller.
- Stable parameters only (primitives, `ImmutableList`, stable models); hoist any internal UI state with a `rememberXState()` holder when callers need control.
- Accessibility: `contentDescription` for icons that carry meaning, minimum 48dp touch targets, `Role` on clickable custom shapes.
- Name by what it is (`TripCard`, `EmptyState`), prefixed with `Tripinly` only where it would clash with Material (`TripinlyButton`).

## Previews and tests

- Previews at the bottom of the file, in `TripinglyTheme`, light and dark, and every visual state (enabled, disabled, loading, error, long text).
- Screenshot test per component once Roborazzi is added to the project (planned; confirm the dependency first).
- If a component has logic (formatting, state holder), unit-test it in `commonTest`.

## Checklist

1. Tokens only, no literals.
2. Stateless, slots where content varies, modifier first.
3. Strings in `core/designsystem/.../composeResources` (+ de, hu).
4. Previews for all states, light and dark.
5. Replace the duplicated feature-level versions in the same PR only if they are in scope; otherwise note them.
