package com.uc.homehealth.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uc.homehealth.data.AuthPreferences
import com.uc.homehealth.data.NetworkLocator
import com.uc.homehealth.data.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val name: String = "",
    val enteredUrl: String = "",          // remote URL (required)
    val enteredLocalUrl: String = "",     // local URL (optional)
    val enteredSsidInput: String = "",    // typing buffer for the add-SSID field
    val enteredSsids: List<String> = emptyList(),  // committed home SSIDs
    val enteredToken: String = "",
    val onboardingComplete: Boolean = false,
    val demoFromOnboarding: Boolean = false,
    val authenticated: Boolean = false,
    val loaded: Boolean = false,
    val locationServicesEnabled: Boolean = true,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authPreferences: AuthPreferences,
    private val userPreferences: UserPreferences,
    private val networkLocator: NetworkLocator,
) : ViewModel() {

    private val _name = MutableStateFlow("")
    private val _enteredUrl = MutableStateFlow("")
    private val _enteredLocalUrl = MutableStateFlow("")
    private val _enteredSsidInput = MutableStateFlow("")
    private val _enteredSsids = MutableStateFlow<List<String>>(emptyList())
    private val _enteredToken = MutableStateFlow("")

    val name: StateFlow<String> = _name.asStateFlow()

    val uiState: StateFlow<OnboardingUiState> = combine(
        combine(userPreferences.onboardingComplete, userPreferences.demoFromOnboarding, authPreferences.authState) { c, d, a -> Triple(c, d, a) },
        _name,
        combine(_enteredUrl, _enteredLocalUrl, _enteredSsidInput, _enteredSsids, _enteredToken) { u, l, sIn, sList, t ->
            FormFields(u, l, sIn, sList, t)
        },
    ) { core, name, fields ->
        OnboardingUiState(
            name = name,
            enteredUrl = fields.url,
            enteredLocalUrl = fields.localUrl,
            enteredSsidInput = fields.ssidInput,
            enteredSsids = fields.ssids,
            enteredToken = fields.token,
            onboardingComplete = core.first,
            demoFromOnboarding = core.second,
            authenticated = core.third.accessToken.isNotEmpty(),
            loaded = true,
            locationServicesEnabled = networkLocator.isLocationEnabled(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OnboardingUiState())

    private data class FormFields(
        val url: String,
        val localUrl: String,
        val ssidInput: String,
        val ssids: List<String>,
        val token: String,
    )

    fun onNameChange(value: String) { _name.value = value }
    fun onUrlChange(value: String) { _enteredUrl.value = value }
    fun onLocalUrlChange(value: String) { _enteredLocalUrl.value = value }
    fun onSsidInputChange(value: String) { _enteredSsidInput.value = value }
    fun onTokenChange(value: String) { _enteredToken.value = value }

    /** Move the current typed value into the SSID list. No-op for blanks / duplicates. */
    fun addTypedSsid() {
        val v = _enteredSsidInput.value.trim()
        if (v.isEmpty()) return
        if (v in _enteredSsids.value) {
            _enteredSsidInput.value = ""
            return
        }
        _enteredSsids.value = _enteredSsids.value + v
        _enteredSsidInput.value = ""
    }

    fun removeSsid(ssid: String) {
        _enteredSsids.value = _enteredSsids.value.filterNot { it == ssid }
    }

    /** Returns true if the location permission is currently granted. */
    fun hasLocationPermission(): Boolean = networkLocator.hasLocationPermission()

    /** True iff the OS-level Location toggle is on (needed for SSID reads). */
    fun isLocationServicesEnabled(): Boolean = networkLocator.isLocationEnabled()

    /** After the user grants the permission, re-read and append to the list. */
    fun detectSsid() {
        networkLocator.refresh()
        val ssid = networkLocator.currentSsid.value
        if (!ssid.isNullOrBlank() && ssid !in _enteredSsids.value) {
            _enteredSsids.value = _enteredSsids.value + ssid
        }
    }

    fun finishWithConnect() {
        val remote = _enteredUrl.value.trimEnd('/')
        val local = _enteredLocalUrl.value.trimEnd('/')
        // Sweep up any text still in the typing buffer that the user forgot to commit.
        val pending = _enteredSsidInput.value.trim()
        val ssids = (_enteredSsids.value + listOfNotNull(pending.takeIf { it.isNotEmpty() }))
            .distinct()
        val token = _enteredToken.value.trim()
        val nm = _name.value.trim()
        viewModelScope.launch {
            if (nm.isNotEmpty()) userPreferences.setUserName(nm)
            if (remote.isNotEmpty() && token.isNotEmpty()) {
                authPreferences.saveAuth(local, remote, ssids, token, "", Int.MAX_VALUE)
            }
            userPreferences.setDemoFromOnboarding(false)
            userPreferences.setOnboardingComplete(true)
        }
    }

    fun finishWithDemo() {
        val nm = _name.value.trim()
        viewModelScope.launch {
            if (nm.isNotEmpty()) userPreferences.setUserName(nm)
            userPreferences.setDemoFromOnboarding(true)
            userPreferences.setOnboardingComplete(true)
        }
    }

    fun clearDemoFromOnboarding() {
        viewModelScope.launch { userPreferences.setDemoFromOnboarding(false) }
    }
}
