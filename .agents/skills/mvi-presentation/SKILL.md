---
name: mvi-presentation
description: >-
  Use this skill when building or refactoring UI and Presentation layer components in Compose Multiplatform.
  Covers MVVM + MVI pattern with Action interfaces, ViewModel-owned StateFlow, Composable stability,
  and proper side-effect handling.
---

# MVI Presentation Layer in Compose Multiplatform

How screens are built in `tripingly`. The short version lives in `docs/knowledge/07-architecture.md`; this skill has the templates.

---

## 1. MVI Flow

```
[ XScreen (stateless) ] ──( XAction )──> [ XViewModel ]
          ▲                                    │
          ├──────────( XUiState )──────────────┤
[ XRoute ] ◄──────────( XEffect, one-shot )────┘
```

1. **State:** one immutable `XUiState` per screen, exposed as read-only `StateFlow<XUiState>`. Anything that can be state is state: a dialog shown, a field error, a loading flag.
2. **Action:** one sealed `XAction` describing what the user did (`TitleChanged`, `SaveClicked`), sent to one `onAction(action)`. The ViewModel decides what it means.
3. **Effect:** only true one-shots (navigate, open the share sheet, request a permission) go through a `Channel` as `XEffect`.

---

## 2. Contracts

Put each in its own file next to the screen (`TripDetailUiState.kt`, `TripDetailAction.kt`, `TripDetailEffect.kt`).

```kotlin
package com.falcon.tripingly.feature.trips.presentation.detail

import com.falcon.tripingly.core.ui.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class TripDetailUiState(
    val title: String = "",
    val days: ImmutableList<DayUi> = persistentListOf(),
    val isLoading: Boolean = true,
    val isRenameDialogVisible: Boolean = false,
    val titleError: UiText? = null,
    val error: UiText? = null,
)

sealed interface TripDetailAction {
    data object RenameClicked : TripDetailAction
    data class TitleChanged(val title: String) : TripDetailAction
    data object SaveClicked : TripDetailAction
    data object ErrorDismissed : TripDetailAction
    data class DayClicked(val dayId: String) : TripDetailAction
}

sealed interface TripDetailEffect {
    data class OpenDay(val dayId: String) : TripDetailEffect
}
```

- User-facing text in state is `UiText` (a string resource with args, or the server's localized `message`), never a raw `String` built from an exception or hardcoded.
- Lists are `ImmutableList`; derived values (formatted dates, counts) are computed before they reach the UI, in presentation mappers.
- No `@Immutable` needed on these when all fields are stable; domain models from `core:model` are marked stable in `compose-stability.conf`.

---

## 3. ViewModel

```kotlin
internal class TripDetailViewModel(
    private val key: TripDetailKey,
    private val tripRepository: TripRepository,
) : ViewModel() {

    private val localState = MutableStateFlow(LocalState())

    val uiState: StateFlow<TripDetailUiState> =
        combine(tripRepository.observeTrip(key.tripId), localState) { trip, local ->
            reduce(trip, local)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailUiState())

    private val effects = Channel<TripDetailEffect>(Channel.BUFFERED)
    val effectFlow: Flow<TripDetailEffect> = effects.receiveAsFlow()

    fun onAction(action: TripDetailAction) {
        when (action) {
            TripDetailAction.RenameClicked -> localState.update { it.copy(isRenameDialogVisible = true) }
            is TripDetailAction.TitleChanged -> localState.update { it.copy(draftTitle = action.title, titleError = null) }
            TripDetailAction.SaveClicked -> save()
            TripDetailAction.ErrorDismissed -> localState.update { it.copy(error = null) }
            is TripDetailAction.DayClicked -> viewModelScope.launch { effects.send(TripDetailEffect.OpenDay(action.dayId)) }
        }
    }

    private fun save() = viewModelScope.launch {
        when (val result = tripRepository.renameTrip(key.tripId, localState.value.draftTitle)) {
            is AppResult.Success -> localState.update { it.copy(isRenameDialogVisible = false) }
            is AppResult.Error -> localState.update { it.copy(error = result.error.toUiText()) }
        }
    }
}
```

Rules:
- Repository flows become state with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`; local UI state lives in a private `MutableStateFlow` combined in. Never expose a `MutableStateFlow`.
- Non-trivial transitions are pure `reduce(...)` functions, tested without coroutines.
- **No dispatchers in ViewModels.** `viewModelScope` already runs on `Dispatchers.Main.immediate`; blocking work is moved off the main thread inside the data layer with injected dispatchers. Never `launch(dispatchers.main)`.
- ViewModels never hold platform objects (Context, UIViewController) or composable state.
- Arguments come from the typed navigation key (`TripDetailKey`), injected with `parametersOf(key)`.

---

## 4. Route, Screen and Effects

```kotlin
@Composable
fun TripDetailRoute(
    key: TripDetailKey,
    onOpenDay: (String) -> Unit,
    viewModel: TripDetailViewModel = koinViewModel { parametersOf(key) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.effectFlow) { effect ->
        when (effect) {
            is TripDetailEffect.OpenDay -> onOpenDay(effect.dayId)
        }
    }

    TripDetailScreen(state = state, onAction = viewModel::onAction)
}
```

`ObserveAsEvents` (in `core:ui`) collects on `Dispatchers.Main.immediate` while the lifecycle is at least STARTED, so effects are not lost on rotation or while backgrounded:

```kotlin
@Composable
fun <T> ObserveAsEvents(flow: Flow<T>, key1: Any? = null, onEvent: (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(flow, lifecycleOwner.lifecycle, key1) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.Main.immediate) { flow.collect { currentOnEvent(it) } }
        }
    }
}
```

---

## 5. Passing State Down (property drilling)

- The `XRoute` is the only composable that knows the ViewModel. It is never passed further down.
- `XScreen(state, onAction, modifier)` is stateless. Feature sections inside it may take a sub-state and `onAction`.
- **Design-system components** (`core:designsystem`) take plain values and specific lambdas (`onClick`, `onValueChange`), never a feature's `XAction`, so they stay reusable and previewable.
- Drilling through more than about three levels is a smell. Fix it with:
  - slot APIs (pass `content: @Composable () -> Unit` instead of the data the child needs),
  - a small `remember`ed UI state holder for UI-only state (map camera, sheet, pager), or
  - a narrower sub-state type for the section.
- `CompositionLocal` is only for ambient values (theme, spacing, image loader), never for data or callbacks.
- Stable parameters: immutable models, method references (`viewModel::onAction`), remembered lambdas.
- Every composable takes `modifier: Modifier = Modifier` as its first optional parameter and applies it to its root; external padding comes from the caller.
- Split files when a screen grows past about 300 lines: sections go to `component/`.

---

## 6. Previews

- Previews sit at the bottom of the file, wrapped in `TripinglyTheme`.
- Drive them with a `PreviewParameterProvider` of sample states that covers **content, loading, empty and error**:

```kotlin
internal class TripDetailStateProvider : PreviewParameterProvider<TripDetailUiState> {
    override val values = sequenceOf(
        tripDetailPreviewState,
        TripDetailUiState(isLoading = true),
        tripDetailPreviewState.copy(days = persistentListOf()),
        tripDetailPreviewState.copy(error = UiText.Raw("Something went wrong")),
    )
}

@Preview
@Composable
private fun TripDetailScreenPreview(
    @PreviewParameter(TripDetailStateProvider::class) state: TripDetailUiState,
) {
    TripinglyTheme { TripDetailScreen(state = state, onAction = {}) }
}
```

- Sample data lives in an `internal` `<Screen>PreviewData.kt` file in the same package.

---

## 7. Tests

Every action and every error the screen handles has a ViewModel test with fakes and Turbine (see `kmp-testing-framework`): `saveClicked_withBlankTitle_showsTitleError`, `saveClicked_onNetworkError_showsError`, `dayClicked_sendsOpenDayEffect`.
