package com.uc.homehealth.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uc.homehealth.data.HomeRepository
import com.uc.homehealth.data.HaNotification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActivityUiState(
    val notifications: List<HaNotification> = emptyList(),
    val activeFilter: String = "All",
)

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val repo: HomeRepository,
) : ViewModel() {

    val uiState: StateFlow<ActivityUiState> = repo.getNotifications()
        .map { ActivityUiState(notifications = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ActivityUiState(),
        )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    /** Swipe-to-delete: remove one event from the on-device feed. */
    fun delete(id: Long) {
        viewModelScope.launch { repo.deleteNotification(id) }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        repo.reconnectNow()
        viewModelScope.launch {
            delay(700)
            _isRefreshing.value = false
        }
    }
}
