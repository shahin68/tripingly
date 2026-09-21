package com.falcon.tripingly.feature.home.presentation.screen

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.feature.home.domain.model.Trip
import com.falcon.tripingly.feature.home.domain.usecase.CreateTripUseCase
import com.falcon.tripingly.feature.home.domain.usecase.DeleteTripUseCase
import com.falcon.tripingly.feature.home.domain.usecase.GetAllTripsUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

class HomeViewModel(
    private val getAllTripsUseCase: GetAllTripsUseCase,
    private val createTripUseCase: CreateTripUseCase,
    private val deleteTripUseCase: DeleteTripUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(State())
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    private val _events = Channel<Event>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        observeTrips()
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.OnTabSelected -> _uiState.update { it.copy(selectedTab = action.tab) }
            is Action.OnSearchQueryChanged -> _uiState.update { it.copy(searchQuery = action.query) }
            is Action.OnAddTripClick -> _uiState.update { it.copy(isCreateDialogVisible = true) }
            is Action.OnDismissCreateDialog -> _uiState.update { it.copy(isCreateDialogVisible = false) }
            is Action.OnConfirmCreateTrip -> createTrip(action.name, action.startDate, action.endDate)
            is Action.OnTripClick -> sendEvent(Event.NavigateToMap(action.tripId))
            is Action.OnDeleteTrip -> deleteTrip(action.tripId)
        }
    }

    private fun observeTrips() {
        viewModelScope.launch {
            getAllTripsUseCase().collect { trips ->
                _uiState.update { it.copy(trips = trips) }
            }
        }
    }

    private fun createTrip(name: String, startDate: LocalDate, endDate: LocalDate) {
        viewModelScope.launch {
            createTripUseCase(name, startDate, endDate)
            _uiState.update { it.copy(isCreateDialogVisible = false) }
        }
    }

    private fun deleteTrip(tripId: String) {
        viewModelScope.launch {
            deleteTripUseCase(tripId)
        }
    }

    private fun sendEvent(event: Event) {
        viewModelScope.launch {
            _events.send(event)
        }
    }

    @Immutable
    data class State(
        val selectedTab: Tab = Tab.MyTrips,
        val searchQuery: String = "",
        val trips: List<Trip> = emptyList(),
        val isCreateDialogVisible: Boolean = false
    )

    enum class Tab { MyTrips, Social }

    sealed interface Event {
        data class NavigateToMap(val tripId: String) : Event
    }

    sealed interface Action {
        data class OnTabSelected(val tab: Tab) : Action
        data class OnSearchQueryChanged(val query: String) : Action
        data object OnAddTripClick : Action
        data object OnDismissCreateDialog : Action
        data class OnConfirmCreateTrip(val name: String, val startDate: LocalDate, val endDate: LocalDate) : Action
        data class OnTripClick(val tripId: String) : Action
        data class OnDeleteTrip(val tripId: String) : Action
    }
}
