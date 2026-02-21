package com.aurora.modifypositioning.ui.movement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurora.modifypositioning.data.MapPreferencesStore
import com.aurora.modifypositioning.domain.MockController
import com.aurora.modifypositioning.model.MovementMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MovementViewModel(
    private val mapPreferencesStore: MapPreferencesStore,
    private val controller: MockController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovementUiState())
    val uiState: StateFlow<MovementUiState> = _uiState.asStateFlow()

    init {
        observeController()
        loadConfig()
    }

    fun setMovementMode(mode: MovementMode) {
        viewModelScope.launch {
            mapPreferencesStore.setMovementMode(mode)
            _uiState.update { it.copy(mode = mode) }
            controller.onMovementModeChanged(mode)
        }
    }

    private fun loadConfig() {
        viewModelScope.launch {
            val mode = mapPreferencesStore.getMovementMode()
            val config = mapPreferencesStore.getRandomWalkConfig()
            _uiState.update {
                it.copy(
                    mode = mode,
                    config = config,
                )
            }
        }
    }

    private fun observeController() {
        viewModelScope.launch {
            controller.target.collectLatest { target ->
                _uiState.update { it.copy(currentTarget = target) }
            }
        }
        viewModelScope.launch {
            controller.movementCenter.collectLatest { center ->
                _uiState.update { it.copy(centerTarget = center) }
            }
        }
        viewModelScope.launch {
            controller.movementMode.collectLatest { mode ->
                _uiState.update { it.copy(mode = mode) }
            }
        }
        viewModelScope.launch {
            controller.movementState.collectLatest { state ->
                _uiState.update { it.copy(movementState = state) }
            }
        }
        viewModelScope.launch {
            controller.movementTrace.collectLatest { trace ->
                _uiState.update { it.copy(tracePoints = trace) }
            }
        }
        viewModelScope.launch {
            controller.movementCurrentSpeedMps.collectLatest { speed ->
                _uiState.update { it.copy(currentSpeedMps = speed) }
            }
        }
        viewModelScope.launch {
            controller.movementDistanceFromCenterMeters.collectLatest { distance ->
                _uiState.update { it.copy(distanceFromCenterMeters = distance) }
            }
        }
    }
}

class MovementViewModelFactory(
    private val mapPreferencesStore: MapPreferencesStore,
    private val controller: MockController,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MovementViewModel::class.java)) {
            return MovementViewModel(
                mapPreferencesStore = mapPreferencesStore,
                controller = controller,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.simpleName}")
    }
}
