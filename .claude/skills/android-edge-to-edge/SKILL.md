---
name: android-edge-to-edge
description: >-
  Use this skill when configuring, implementing, or debugging edge-to-edge display,
  window insets handling, status and navigation bar styling, or IME keyboard padding
  in Android and Compose Multiplatform.
---

# Android Edge-to-Edge & Window Insets Guide

This skill governs edge-to-edge display and insets management in `tripingly` across Android versions, especially Android 15+ (API 35+ / API 36) where edge-to-edge is strictly enforced by the system.

---

## 1. Activity Setup (`MainActivity.kt`)

Call `enableEdgeToEdge()` inside `onCreate` before `setContent`:

```kotlin
package com.falcon.tripingly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before setContent
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            App()
        }
    }
}
```

> [!IMPORTANT]
> **Android 15+ (API 35+) Enforcement**:
> In Android 15+, apps targeting SDK 35+ are forced edge-to-edge by default. Legacy flags like `window.setDecorFitsSystemWindows(true)` or `window.statusBarColor` are ignored or deprecated. All insets must be handled via Compose `WindowInsets`.

---

## 2. Compose WindowInsets Foundations

In Compose Multiplatform, handle insets declaratively:

| Inset Type | Compose API | Purpose |
| :--- | :--- | :--- |
| **System Bars** | `WindowInsets.systemBars` | Status bar + Navigation bar areas |
| **Safe Drawing** | `WindowInsets.safeDrawing` | System bars + display cutouts / notches |
| **Safe Gestures** | `WindowInsets.safeGestures` | Waterfall edges and gesture exclusion areas |
| **Keyboard / IME** | `WindowInsets.ime` | Dynamic soft keyboard height |
| **Status Bar only** | `WindowInsets.statusBars` | Top status bar only |
| **Nav Bar only** | `WindowInsets.navigationBars` | Bottom navigation bar only |

### Common Modifier Modifiers
```kotlin
// Consume insets as padding
Modifier.windowInsetsPadding(WindowInsets.safeDrawing)

// Or specific bar insets:
Modifier.windowInsetsPadding(WindowInsets.statusBars)
Modifier.windowInsetsPadding(WindowInsets.navigationBars)

// Keyboard avoiding:
Modifier.imePadding()
```

---

## 3. Screen Inset Patterns

### Pattern A: Standard Content Screen (Scaffold Default)
When content should stay strictly within safe areas:

```kotlin
@Composable
fun StandardScreen(content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding) // Consumes safe drawing insets
        ) {
            // Content is protected from cutouts and system bars
        }
    }
}
```

### Pattern B: Immersive Fullscreen (e.g. Maps & Media)
For maps, media, or splash backgrounds where the visual layer extends behind system bars, but interactive controls respect safe insets:

```kotlin
@Composable
fun ImmersiveMapScreen(
    state: MapUiState,
    onAction: (MapUiAction) -> Unit
) {
    // 1. Scaffold configured with ZERO insets so background spans full display
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Map or media draws edge-to-edge beneath transparent system bars
            GoogleMapView(
                modifier = Modifier.fillMaxSize(),
                // ...
            )

            // Top Floating Search Bar respects status bar & safe cutouts
            TopSurfingBar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(16.dp)
            )

            // Bottom controls respect navigation bar and gesture insets
            FloatingActionButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp)
            )
        }
    }
}
```

---

## 4. Keyboard / IME Handling
To ensure text fields and bottom action bars automatically slide above the soft keyboard:

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .imePadding() // Automatically offsets when keyboard opens
) {
    // Scrollable content
    LazyColumn(modifier = Modifier.weight(1f)) { ... }
    
    // Bottom input or button bar
    TextField(...)
}
```

---

## 5. System Bar Icon Contrast (Light vs. Dark Theme)

Ensure status bar icons (clock, battery, Wi-Fi) and navigation bar icons remain readable:

```kotlin
val isDarkTheme = isSystemInDarkTheme()

DisposableEffect(isDarkTheme) {
    enableEdgeToEdge(
        statusBarStyle = if (isDarkTheme) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        },
        navigationBarStyle = if (isDarkTheme) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
    )
    onDispose {}
}
```

---

## 6. Edge-to-Edge Rules Checklist
1. **Always call `enableEdgeToEdge()`**: In `MainActivity.onCreate()` before `setContent`.
2. **Never hardcode status bar heights**: Never use magic numbers like `24.dp` or `48.dp`. Always use `WindowInsets.statusBars` or `WindowInsets.safeDrawing`.
3. **Avoid double padding**: If `Scaffold` already applies `innerPadding`, do not add another `windowInsetsPadding(WindowInsets.safeDrawing)` on child composables.
4. **Immersive screens must set `contentWindowInsets = WindowInsets(0, 0, 0, 0)`**: Apply insets selectively to floating controls and headers.
5. **Always add `imePadding()` to input screens**: Prevents soft keyboard from obscuring buttons and text inputs.
