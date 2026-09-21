package com.falcon.tripingly.feature.home.presentation.screen

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.util.DateUtils
import com.falcon.tripingly.core.util.ShareManager
import com.falcon.tripingly.feature.home.domain.model.Trip
import com.falcon.tripingly.feature.home.domain.usecase.CreateTripUseCase
import com.falcon.tripingly.feature.home.domain.usecase.DeleteTripUseCase
import com.falcon.tripingly.feature.home.domain.usecase.GetAllTripsUseCase
import com.falcon.tripingly.feature.home.domain.usecase.UpdateTripDatesUseCase
import com.falcon.tripingly.feature.home.domain.usecase.UpdateTripNameUseCase
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
    private val updateTripNameUseCase: UpdateTripNameUseCase,
    private val updateTripDatesUseCase: UpdateTripDatesUseCase,
    private val shareManager: ShareManager
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
            is Action.OnRenameTrip -> {
                val trip = _uiState.value.trips.find { it.id == action.tripId }
                _uiState.update { it.copy(renamingTrip = trip) }
            }
            is Action.OnDismissRenameDialog -> _uiState.update { it.copy(renamingTrip = null) }
            is Action.OnConfirmRenameTrip -> renameTrip(action.tripId, action.newName)
            
            is Action.OnRescheduleTrip -> {
                val trip = _uiState.value.trips.find { it.id == action.tripId }
                _uiState.update { it.copy(reschedulingTrip = trip) }
            }
            is Action.OnDismissRescheduleDialog -> _uiState.update { it.copy(reschedulingTrip = null) }
            is Action.OnConfirmRescheduleTrip -> rescheduleTrip(action.tripId, action.startDate, action.endDate)
            
            is Action.OnShareTrip -> {
                val trip = _uiState.value.trips.find { it.id == action.tripId }
                trip?.let {
                    val dates = "${DateUtils.formatFormal(it.startDate)} - ${DateUtils.formatFormal(it.endDate)}"
                    shareManager.shareTrip(it.name, dates)
                }
            }
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

    private fun renameTrip(tripId: String, newName: String) {
        viewModelScope.launch {
            updateTripNameUseCase(tripId, newName)
            _uiState.update { it.copy(renamingTrip = null) }
        }
    }

    private fun rescheduleTrip(tripId: String, startDate: LocalDate, endDate: LocalDate) {
        viewModelScope.launch {
            updateTripDatesUseCase(tripId, startDate, endDate)
            _uiState.update { it.copy(reschedulingTrip = null) }
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
        val isCreateDialogVisible: Boolean = false,
        val renamingTrip: Trip? = null,
        val reschedulingTrip: Trip? = null
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
        data class OnRenameTrip(val tripId: String) : Action
        data object OnDismissRenameDialog : Action
        data class OnConfirmRenameTrip(val tripId: String, val newName: String) : Action
        
        data class OnRescheduleTrip(val tripId: String) : Action
        data object OnDismissRescheduleDialog : Action
        data class OnConfirmRescheduleTrip(val tripId: String, val startDate: LocalDate, val endDate: LocalDate) : Action
        
        data class OnShareTrip(val tripId: String) : Action
    }
}
