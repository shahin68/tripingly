---
name: mvi-presentation
description: >-
  Use this skill when building or refactoring UI and Presentation layer components in Compose Multiplatform.
  Covers MVVM + MVI pattern with Action interfaces, ViewModel-owned StateFlow, Composable stability,
  and proper side-effect handling.
---

# MVI Presentation Layer in Compose Multiplatform

This guide defines the Presentation layer architecture for `tripingly`.

---

## 1. Core Pattern Overview

```
[ Composable UI ] ────( Action Interface )────> [ ViewModel ]
       ▲                                               │
       ├──────────────( Immutable StateFlow )──────────┤
       └──────────────( One-time UiEvent Flow )────────┘
```

1. **State**: Immutable snapshot of the UI, exposed as `StateFlow<UiState>` owned exclusively by the `ViewModel`.
2. **Action**: High-level user intentions sent to the ViewModel via a sealed interface (`UiAction`).
3. **Event**: One-shot side-effects (navigation, snackbar, haptic feedback) emitted via an unbuffered or buffered `Channel` and exposed as a `Flow<UiEvent>`.

---

## 2. Defining State, Action, and Event

```kotlin
package com.falcon.tripingly.feature.trips.presentation

import androidx.compose.runtime.Immutable
import com.falcon.tripingly.domain.model.Trip

// 1. Immutable UI State
@Immutable
data class TripsUiState(
    val isLoading: Boolean = false,
    val trips: List<TripUiModel> = emptyList(),
    val errorMessage: String? = null
)

@Immutable
data class TripUiModel(
    val id: String,
    val title: String,
    val destination: String,
    val formattedDates: String
)

// 2. High-level User Actions (Intents)
sealed interface TripsAction {
    data object Refresh : TripsAction
    data class OnTripClicked(val tripId: String) : TripsAction
    data class OnDeleteTrip(val tripId: String) : TripsAction
    data object OnCreateTripClicked : TripsAction
    data object DismissError : TripsAction
}

// 3. One-Time Side Effects
sealed interface TripsEvent {
    data class NavigateToDetails(val tripId: String) : TripsEvent
    data object NavigateToCreate : TripsEvent
    data class ShowToast(val message: String) : TripsEvent
}
```

---

## 3. ViewModel State Ownership & Action Handling

```kotlin
package com.falcon.tripingly.feature.trips.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.domain.usecase.GetTripsStreamUseCase
import com.falcon.tripingly.domain.usecase.SyncTripsUseCase
import com.falcon.tripingly.core.domain.result.AppResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TripsViewModel(
    private val getTripsStreamUseCase: GetTripsStreamUseCase,
    private val syncTripsUseCase: SyncTripsUseCase
) : ViewModel() {

    // ViewModel owns and protects the mutable state
    private val _uiState = MutableStateFlow(TripsUiState(isLoading = true))
    val uiState: StateFlow<TripsUiState> = _uiState.asStateFlow()

    // Dedicated Channel for one-off side effects (not lost on recomposition)
    private val _events = Channel<TripsEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        observeTrips()
        onAction(TripsAction.Refresh)
    }

    // High-level MVI dispatch function
    fun onAction(action: TripsAction) {
        when (action) {
            is TripsAction.Refresh -> refreshTrips()
            is TripsAction.OnTripClicked -> navigateToDetails(action.tripId)
            is TripsAction.OnDeleteTrip -> deleteTrip(action.tripId)
            is TripsAction.OnCreateTripClicked -> sendEvent(TripsEvent.NavigateToCreate)
            is TripsAction.DismissError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun observeTrips() {
        viewModelScope.launch {
            getTripsStreamUseCase().collect { trips ->
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        trips = trips.map { it.toUiModel() }
                    )
                }
            }
        }
    }

    private fun refreshTrips() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = syncTripsUseCase()) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
                is AppResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.error.asUiText()
                        )
                    }
                }
            }
        }
    }

    private fun navigateToDetails(tripId: String) {
        sendEvent(TripsEvent.NavigateToDetails(tripId))
    }

    private fun deleteTrip(tripId: String) {
        // execute delete
    }

    private fun sendEvent(event: TripsEvent) {
        viewModelScope.launch {
            _events.send(event)
        }
    }
}
```

---

## 4. Composable Stability & Side-Effect Rules

### Rule 1: Separation of Stateful and Stateless Composables
Split each screen into a **Route (Stateful)** and **Content (Stateless)** Composable:
- **Route**: Injects ViewModel, collects state with lifecycle awareness, handles side-effect events, and delegates rendering to Content.
- **Content**: Pure function receiving state and lambda callbacks `(action: (TripsAction) -> Unit)`. Extremely previewable and testable.

### Rule 2: Proper Side-Effect Consumption
Consume ViewModel events using `LaunchedEffect` keyed to the event flow:

```kotlin
@Composable
fun TripsRoute(
    viewModel: TripsViewModel,
    onNavigateToDetails: (String) -> Unit,
    onNavigateToCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Observe side effects safely
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is TripsEvent.NavigateToDetails -> onNavigateToDetails(event.tripId)
                is TripsEvent.NavigateToCreate -> onNavigateToCreate()
                is TripsEvent.ShowToast -> {
                    // Trigger snackbar / toast
                }
            }
        }
    }

    TripsScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier
    )
}
```

### Rule 3: Composable Parameter Stability
- Mark data classes holding state with `@Immutable` or `@Stable`.
- Avoid passing raw unstable collections or lambdas that capture changing variables without `remember`.
- Pass high-level action dispatcher: `onAction: (TripsAction) -> Unit`.
- Always provide a `Modifier` parameter that defaults to `Modifier` as the first optional parameter.
