package com.uc.homehealth.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uc.homehealth.data.AuthPreferences
import com.uc.homehealth.data.GlanceConfig
import com.uc.homehealth.data.HomeRepository
import com.uc.homehealth.data.NetworkLocator
import com.uc.homehealth.data.UserPreferences
import com.uc.homehealth.data.WsConnectionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val userName: String = "",
    val serverName: String = "Riverside",
    val haVersion: String = "v2026.4",
    val areaCount: Int = 6,
    val deviceCount: Int = 25,
    val haUrl: String = "",          // active URL (what's being used right now)
    val localUrl: String = "",
    val remoteUrl: String = "",
    val homeSsids: List<String> = emptyList(),
    val currentSsid: String? = null,
    val onHomeWifi: Boolean = false,
    val locationServicesEnabled: Boolean = true,
    val isLoggedIn: Boolean = false,
    val connectionStatus: WsConnectionStatus = WsConnectionStatus.DISCONNECTED,
    val enteredUrl: String = "",
    val showTokenInput: Boolean = false,
    val enteredToken: String = "",
    val glance: GlanceConfig = GlanceConfig(),
    val demoFromOnboarding: Boolean = false,
    val showGlanceSheet: Boolean = false,
) {
    val isConnected: Boolean get() = connectionStatus == WsConnectionStatus.READY
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authPreferences: AuthPreferences,
    private val userPreferences: UserPreferences,
    private val networkLocator: NetworkLocator,
    private val repo: HomeRepository,
) : ViewModel() {

    private val _enteredUrl = MutableStateFlow("")
    private val _showTokenInput = MutableStateFlow(false)
    private val _enteredToken = MutableStateFlow("")
    private val _showGlanceSheet = MutableStateFlow(false)
    private val _glance = MutableStateFlow(GlanceConfig())

    init {
        viewModelScope.launch {
            _glance.value = userPreferences.config.first()
        }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            authPreferences.authState,
            repo.connectionStatus(),
            _glance,
            userPreferences.demoFromOnboarding,
            networkLocator.currentSsid,
        ) { auth, wsStatus, glance, demoFlag, ssid ->
            ServerSlice(auth, wsStatus, glance, demoFlag, ssid)
        },
        _enteredUrl,
        _showTokenInput,
        _enteredToken,
        _showGlanceSheet,
    ) { server, enteredUrl, showToken, enteredToken, showGlance ->
        val auth = server.auth
        val activeUrl = auth.activeUrl(server.currentSsid)
        SettingsUiState(
            userName = server.glance.userName,
            haUrl = activeUrl,
            localUrl = auth.localUrl,
            remoteUrl = auth.remoteUrl,
            homeSsids = auth.homeSsids,
            currentSsid = server.currentSsid,
            onHomeWifi = auth.isHomeSsid(server.currentSsid),
            locationServicesEnabled = networkLocator.isLocationEnabled(),
            isLoggedIn = auth.accessToken.isNotEmpty(),
            connectionStatus = server.wsStatus,
            enteredUrl = enteredUrl,
            showTokenInput = showToken,
            enteredToken = enteredToken,
            glance = server.glance,
            demoFromOnboarding = server.demoFromOnboarding,
            showGlanceSheet = showGlance,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private data class ServerSlice(
        val auth: com.uc.homehealth.data.AuthState,
        val wsStatus: WsConnectionStatus,
        val glance: GlanceConfig,
        val demoFromOnboarding: Boolean,
        val currentSsid: String?,
    )

    fun onUrlChange(url: String) { _enteredUrl.value = url }
    fun onTokenChange(token: String) { _enteredToken.value = token }
    fun toggleTokenInput() { _showTokenInput.value = !_showTokenInput.value }

    fun showGlanceSheet() { _showGlanceSheet.value = true }
    fun hideGlanceSheet() { _showGlanceSheet.value = false }

    fun connectWithToken(haUrl: String, token: String) {
        viewModelScope.launch {
            // Treat the entered URL as the remote URL; preserve any existing local URL / SSIDs.
            val existing = authPreferences.authState.first()
            authPreferences.saveAuth(
                localUrl = existing.localUrl,
                remoteUrl = haUrl.trimEnd('/'),
                homeSsids = existing.homeSsids,
                accessToken = token.trim(),
                refreshToken = "",
                expiresIn = Int.MAX_VALUE,
            )
        }
    }

    fun saveNetworkRouting(localUrl: String, remoteUrl: String, homeSsids: List<String>) {
        viewModelScope.launch {
            authPreferences.saveUrls(
                localUrl = localUrl.trimEnd('/'),
                remoteUrl = remoteUrl.trimEnd('/'),
                homeSsids = homeSsids.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            )
        }
    }

    fun hasLocationPermission(): Boolean = networkLocator.hasLocationPermission()
    fun refreshSsid() { networkLocator.refresh() }

    /** Convenience: refresh and return the current SSID (if any). */
    fun currentSsid(): String? {
        networkLocator.refresh()
        return networkLocator.currentSsid.value
    }

    fun disconnect() {
        viewModelScope.launch {
            authPreferences.clearAuth()
            _showTokenInput.value = false
            _enteredToken.value = ""
            _enteredUrl.value = ""
        }
    }

    /**
     * Flip the onboarding-complete flag back to false so the root nav swaps to
     * OnboardingScreen on the next frame. Auth + preferences are left intact —
     * the user can either re-enter values or finish onboarding with the
     * existing config (and the demo path is still available).
     */
    fun redoOnboarding() {
        viewModelScope.launch { userPreferences.setOnboardingComplete(false) }
    }

    fun setUserName(name: String) {
        _glance.value = _glance.value.copy(userName = name)
        viewModelScope.launch { userPreferences.setUserName(name) }
    }

    fun setOutsideTempEntity(id: String) {
        _glance.value = _glance.value.copy(entityOutsideTemp = id)
        viewModelScope.launch { userPreferences.setOutsideTempEntity(id) }
    }

    fun setInsideTempEntity(id: String) {
        _glance.value = _glance.value.copy(entityInsideTemp = id)
        viewModelScope.launch { userPreferences.setInsideTempEntity(id) }
    }

    fun setDoorbellEntity(id: String) {
        _glance.value = _glance.value.copy(entityDoorbell = id)
        viewModelScope.launch { userPreferences.setDoorbellEntity(id) }
    }

    fun setLightsOnEntity(id: String) {
        _glance.value = _glance.value.copy(entityLightsOn = id)
        viewModelScope.launch { userPreferences.setLightsOnEntity(id) }
    }

    fun setAqiEntity(id: String) {
        _glance.value = _glance.value.copy(entityAqi = id)
        viewModelScope.launch { userPreferences.setAqiEntity(id) }
    }

    fun setGlanceTemplate(value: String) {
        _glance.value = _glance.value.copy(template = value)
        viewModelScope.launch { userPreferences.setTemplate(value) }
    }
}
