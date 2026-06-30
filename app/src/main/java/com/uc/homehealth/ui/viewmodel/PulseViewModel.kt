package com.uc.homehealth.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uc.homehealth.data.AuthPreferences
import com.uc.homehealth.data.HomeRepository
import com.uc.homehealth.data.PulseReport
import com.uc.homehealth.data.WsConnectionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class PulseUiState(
    val report: PulseReport? = null,
    val connectionStatus: WsConnectionStatus = WsConnectionStatus.DISCONNECTED,
    // Server URL for the "address these in HA" hand-off button (blank in demo mode).
    val haUrl: String = "",
) {
    // True until a meaningful report exists: real mode pre-state-load analyzes an empty
    // map (sampleSize 0) which would read as a fake perfect score — gate it behind a
    // loading surface instead.
    val isLoading: Boolean get() = report == null || report.sampleSize == 0
}

@HiltViewModel
class PulseViewModel @Inject constructor(
    repo: HomeRepository,
    authPreferences: AuthPreferences,
) : ViewModel() {

    val uiState: StateFlow<PulseUiState> = combine(
        repo.getPulse(),
        repo.connectionStatus(),
        authPreferences.authState,
    ) { report, status, auth ->
        PulseUiState(
            report = report,
            connectionStatus = status,
            haUrl = if (auth.accessToken.isNotBlank()) auth.haUrl else "",
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PulseUiState(),
    )
}
