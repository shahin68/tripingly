---
name: mvi-presentation
description: >-
  Use this skill when building or refactoring UI and Presentation layer components in Compose Multiplatform.
  Covers MVVM + MVI pattern with Action interfaces, ViewModel-owned StateFlow, Composable stability,
  and proper side-effect handling.
---

# MVI Presentation Layer in Compose Multiplatform

This guide defines the engineering design system, state, and interaction patterns for the Presentation layer of `tripingly`.

---

## 1. MVI Flow Mechanics

The application layer strictly employs unidirectional data stream structures:

```
[ Composable UI ] ──────( Dispatches UiAction )──────> [ ViewModel ]
       ▲                                                     │
       ├──────────────( Observes Immutable UiState )─────────┤
       └──────────────( Safely Collects One-shot UiEvent )───┘
```

1. **State**: An absolute, immutable representation of the visible UI layout, exposed solely as a read-only `StateFlow<UiState>`.
2. **Action**: Sealed interactions representing explicit user intentions dispatched to the ViewModel.
3. **Event**: Individual, non-persistent side-effects (e.g., specific camera movements, alerts, platform notifications) pushed over buffered `Channels` and safely consumed as a lifecycle-aware `Flow`.

---

## 2. Structural Definition Contracts

```kotlin
package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.runtime.Immutable
import com.falcon.tripingly.feature.map.domain.model.Coordinates

// 1. Fully Immutable State Envelope
@Immutable
data class MapUiState(
    val currentLocation: Coordinates? = null,
    val cameraTarget: Coordinates = Coordinates.Paris,
    val isLoadingLocation: Boolean = false,
    val errorMessage: String? = null
)

// 2. Focused Intent Actions
sealed interface MapAction {
    data object CenterOnUserLocation : MapAction
    data class OnPermissionResult(val isGranted: Boolean) : MapAction
    data object DismissError : MapAction
}

// 3. One-Shot Side-Effect Channels
sealed interface MapEvent {
    data class AnimateCamera(val coordinates: Coordinates, val zoom: Float) : MapEvent
    data class ShowSnackbar(val message: String) : MapEvent
}
```

---

## 3. ViewModel State Control Guidelines

ViewModels accept abstractions using constructor injection, manage underlying streams, and expose clean, high-level hooks for incoming actions:

```kotlin
package com.falcon.tripingly.feature.map.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.coroutines.CoroutineDispatchers
import com.falcon.tripingly.feature.map.domain.usecase.GetCurrentLocationUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MapViewModel(
    private val getCurrentLocationUseCase: GetCurrentLocationUseCase,
    private val dispatchers: CoroutineDispatchers
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _events = Channel<MapEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: MapAction) {
        when (action) {
            is MapAction.CenterOnUserLocation -> fetchUserLocation()
            is MapAction.OnPermissionResult -> handlePermission(action.isGranted)
            is MapAction.DismissError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun fetchUserLocation() {
        viewModelScope.launch(dispatchers.main) {
            _uiState.update { it.copy(isLoadingLocation = true) }
            // Fetch logic execution here...
        }
    }

    private fun handlePermission(isGranted: Boolean) {
        // Handle changes in state mapping...
    }
}
```

---

## 4. Modern Compose Stability & Composition Lifecycles

### Rule 1: Clean Separation of Stateful and Stateless Composables
Always separate screen declarations into a **Route (Stateful)** and a **Screen Content (Stateless)** Composable:
- **Route**: Manages framework bindings, resolves the ViewModel using modern multiplatform dependency tools (`koinViewModel()`), handles event lifecycle collections, and forwards state parameters downwards.
- **Content**: A stateless, declarative function that accepts the state layout object and lambda action callbacks `(action: MapAction) -> Unit`. This design makes it highly testable and previewable.

### Rule 2: Lifecycle-Aware Side-Effect Flow Consumption
Observe one-off event flows safely using standard Jetpack Compose multiplatform lifecycle-aware tools:

```kotlin
@Composable
fun MapRoute(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Process short-lived lifecycle events cleanly
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is MapEvent.AnimateCamera -> { /* Trigger Camera Animation */ }
                is MapEvent.ShowSnackbar -> { /* Display temporary snackbar */ }
            }
        }
    }

    MapScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier
    )
}
```

### Rule 3: Parameter Stability & Modifier Compliance
- Annotate state data structures with `@Immutable` or `@Stable`.
- Avoid passing raw, un-remembered lambdas or mutable structures directly inside the Composable hierarchy to prevent performance regression.
- Pass the raw method references where possible: `onAction = viewModel::onAction`.
- **First Optional Parameter Rule**: Every Composable view configuration signature **must** accept a `modifier: Modifier = Modifier` as its very first optional parameter.
