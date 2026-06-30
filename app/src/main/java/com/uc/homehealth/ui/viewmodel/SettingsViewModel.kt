package com.uc.homehealth.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uc.homehealth.data.AuthPreferences
import com.uc.homehealth.data.GlanceConfig
import com.uc.homehealth.data.HaTtsEngine
import com.uc.homehealth.data.HaTtsVoice
import com.uc.homehealth.data.HomeRepository
import com.uc.homehealth.data.NetworkLocator
import com.uc.homehealth.data.TtsDefaults
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
    val go2rtcUrl: String = "",
    val homeSsids: List<String> = emptyList(),
    val currentSsid: String? = null,
    val onHomeWifi: Boolean = false,
    val locationServicesEnabled: Boolean = true,
    val isLoggedIn: Boolean = false,
    val connectionStatus: WsConnectionStatus = WsConnectionStatus.DISCONNECTED,
    /** HA's own reason for the last auth rejection (from auth_invalid), if any. */
    val authErrorDetail: String? = null,
    val enteredUrl: String = "",
    val showTokenInput: Boolean = false,
    val enteredToken: String = "",
    val demoFromOnboarding: Boolean = false,
) {
    val isConnected: Boolean get() = connectionStatus == WsConnectionStatus.READY
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authPreferences: AuthPreferences,
    private val userPreferences: UserPreferences,
    private val networkLocator: NetworkLocator,
    private val repo: HomeRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Same reasoning as OnboardingViewModel: the user often leaves to a browser to copy their
    // long-lived access token, so the half-filled re-connect form (URL/token + whether the
    // token field is expanded) is backed by SavedStateHandle to survive a background
    // process-death recreation instead of resetting to blank.
    private val _enteredUrl = savedStateHandle.getStateFlow(KEY_URL, "")
    private val _showTokenInput = savedStateHandle.getStateFlow(KEY_SHOW_TOKEN, false)
    private val _enteredToken = savedStateHandle.getStateFlow(KEY_TOKEN, "")
    private val _glance = MutableStateFlow(GlanceConfig())

    init {
        viewModelScope.launch {
            _glance.value = userPreferences.config.first()
        }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            authPreferences.authState,
            // combine() caps at 5 sources — status and its auth-error detail travel as a pair.
            combine(repo.connectionStatus(), repo.authError()) { s, e -> s to e },
            _glance,
            userPreferences.demoFromOnboarding,
            networkLocator.currentSsid,
        ) { auth, (wsStatus, authError), glance, demoFlag, ssid ->
            ServerSlice(auth, wsStatus, authError, glance, demoFlag, ssid)
        },
        _enteredUrl,
        _showTokenInput,
        _enteredToken,
    ) { server, enteredUrl, showToken, enteredToken ->
        val auth = server.auth
        val activeUrl = auth.activeUrl(server.currentSsid)
        SettingsUiState(
            userName = server.glance.userName,
            haUrl = activeUrl,
            localUrl = auth.localUrl,
            remoteUrl = auth.remoteUrl,
            go2rtcUrl = auth.go2rtcUrl,
            homeSsids = auth.homeSsids,
            currentSsid = server.currentSsid,
            onHomeWifi = auth.isHomeSsid(server.currentSsid),
            locationServicesEnabled = networkLocator.isLocationEnabled(),
            isLoggedIn = auth.accessToken.isNotEmpty(),
            connectionStatus = server.wsStatus,
            authErrorDetail = server.authError,
            enteredUrl = enteredUrl,
            showTokenInput = showToken,
            enteredToken = enteredToken,
            demoFromOnboarding = server.demoFromOnboarding,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private data class ServerSlice(
        val auth: com.uc.homehealth.data.AuthState,
        val wsStatus: WsConnectionStatus,
        val authError: String?,
        val glance: GlanceConfig,
        val demoFromOnboarding: Boolean,
        val currentSsid: String?,
    )

    fun onUrlChange(url: String) { savedStateHandle[KEY_URL] = url }
    fun onTokenChange(token: String) { savedStateHandle[KEY_TOKEN] = token }
    fun toggleTokenInput() { savedStateHandle[KEY_SHOW_TOKEN] = !_showTokenInput.value }

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

    fun saveNetworkRouting(localUrl: String, remoteUrl: String, homeSsids: List<String>, go2rtcUrl: String) {
        viewModelScope.launch {
            authPreferences.saveUrls(
                localUrl = localUrl.trimEnd('/'),
                remoteUrl = remoteUrl.trimEnd('/'),
                homeSsids = homeSsids.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                go2rtcUrl = go2rtcUrl.trim().trimEnd('/'),
            )
        }
    }

    // ── Voice & TTS settings ─────────────────────────────────────────────────
    // Installed engines, loaded on demand when the Voice subpage opens (one-shot WS call).
    // null = not fetched yet (page shows a skeleton), [] = fetched and none installed —
    // collapsing the two made the page claim "No TTS engines found" while still loading.
    private val _ttsEngines = MutableStateFlow<List<HaTtsEngine>?>(null)
    val ttsEngines: StateFlow<List<HaTtsEngine>?> = _ttsEngines

    val ttsDefaults: StateFlow<TtsDefaults> = userPreferences.ttsDefaults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TtsDefaults())

    fun loadTtsEngines() {
        viewModelScope.launch { _ttsEngines.value = repo.getTtsEngines() }
    }

    suspend fun loadTtsVoices(engineId: String, language: String): List<HaTtsVoice> =
        repo.getTtsVoices(engineId, language)

    /** Persist the chosen engine + language. Clears the saved voice (only valid per-engine). */
    fun setTtsEngine(engineId: String, language: String) {
        viewModelScope.launch { userPreferences.setTtsEngine(engineId, language) }
    }
    fun setTtsLanguage(language: String) {
        viewModelScope.launch { userPreferences.setTtsLanguage(language) }
    }
    fun setTtsVoice(voiceId: String) {
        viewModelScope.launch { userPreferences.setTtsVoice(voiceId) }
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
            savedStateHandle[KEY_SHOW_TOKEN] = false
            savedStateHandle[KEY_TOKEN] = ""
            savedStateHandle[KEY_URL] = ""
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

    /**
     * Wipe all activity history stored on this device (the Activity tab's Room feed
     * and the demo list). Does not touch Home Assistant. Backs the "Manage Data" page.
     */
    fun clearActivityData() {
        viewModelScope.launch { repo.clearNotifications() }
    }

    private companion object {
        const val KEY_URL = "settings_entered_url"
        const val KEY_SHOW_TOKEN = "settings_show_token"
        const val KEY_TOKEN = "settings_entered_token"
    }
}
