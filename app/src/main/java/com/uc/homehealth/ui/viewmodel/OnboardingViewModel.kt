package com.uc.homehealth.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uc.homehealth.data.AuthPreferences
import com.uc.homehealth.data.NetworkLocator
import com.uc.homehealth.data.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // The Connect step asks the user to fetch a Home Assistant long-lived access token from
    // their browser, then come back and paste it. While they're away the OS routinely kills
    // this (memory-heavy) app — or "Don't keep activities" destroys it outright — and on return
    // the activity + this ViewModel are recreated. Backing the in-progress form with
    // SavedStateHandle (persisted into the saved-instance Bundle) restores what they typed
    // instead of dropping them onto a blank form ("starts fresh"). SSIDs are stored as a
    // newline-joined string — the app's standard list encoding and Bundle-safe.
    private val _name = savedStateHandle.getStateFlow(KEY_NAME, "")
    private val _enteredUrl = savedStateHandle.getStateFlow(KEY_URL, "")
    private val _enteredLocalUrl = savedStateHandle.getStateFlow(KEY_LOCAL_URL, "")
    private val _enteredSsidInput = savedStateHandle.getStateFlow(KEY_SSID_INPUT, "")
    private val _enteredSsidsRaw = savedStateHandle.getStateFlow(KEY_SSIDS, "")
    private val _enteredToken = savedStateHandle.getStateFlow(KEY_TOKEN, "")

    val name: StateFlow<String> = _name

    val uiState: StateFlow<OnboardingUiState> = combine(
        combine(userPreferences.onboardingComplete, userPreferences.demoFromOnboarding, authPreferences.authState) { c, d, a -> Triple(c, d, a) },
        _name,
        combine(_enteredUrl, _enteredLocalUrl, _enteredSsidInput, _enteredSsidsRaw, _enteredToken) { u, l, sIn, sRaw, t ->
            FormFields(u, l, sIn, splitSsids(sRaw), t)
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

    fun onNameChange(value: String) { savedStateHandle[KEY_NAME] = value }
    fun onUrlChange(value: String) { savedStateHandle[KEY_URL] = value }
    fun onLocalUrlChange(value: String) { savedStateHandle[KEY_LOCAL_URL] = value }
    fun onSsidInputChange(value: String) { savedStateHandle[KEY_SSID_INPUT] = value }
    fun onTokenChange(value: String) { savedStateHandle[KEY_TOKEN] = value }

    /** Move the current typed value into the SSID list. No-op for blanks / duplicates. */
    fun addTypedSsid() {
        val v = _enteredSsidInput.value.trim()
        if (v.isEmpty()) return
        val current = splitSsids(_enteredSsidsRaw.value)
        if (v in current) {
            savedStateHandle[KEY_SSID_INPUT] = ""
            return
        }
        setSsids(current + v)
        savedStateHandle[KEY_SSID_INPUT] = ""
    }

    fun removeSsid(ssid: String) {
        setSsids(splitSsids(_enteredSsidsRaw.value).filterNot { it == ssid })
    }

    /** Returns true if the location permission is currently granted. */
    fun hasLocationPermission(): Boolean = networkLocator.hasLocationPermission()

    /** True iff the OS-level Location toggle is on (needed for SSID reads). */
    fun isLocationServicesEnabled(): Boolean = networkLocator.isLocationEnabled()

    /** After the user grants the permission, re-read and append to the list. */
    fun detectSsid() {
        networkLocator.refresh()
        val ssid = networkLocator.currentSsid.value
        val current = splitSsids(_enteredSsidsRaw.value)
        if (!ssid.isNullOrBlank() && ssid !in current) {
            setSsids(current + ssid)
        }
    }

    private fun setSsids(list: List<String>) {
        savedStateHandle[KEY_SSIDS] = list.joinToString("\n")
    }

    private fun splitSsids(raw: String): List<String> =
        raw.split('\n').filter { it.isNotBlank() }

    fun finishWithConnect() {
        val remote = _enteredUrl.value.trimEnd('/')
        val local = _enteredLocalUrl.value.trimEnd('/')
        // Sweep up any text still in the typing buffer that the user forgot to commit.
        val pending = _enteredSsidInput.value.trim()
        val ssids = (splitSsids(_enteredSsidsRaw.value) + listOfNotNull(pending.takeIf { it.isNotEmpty() }))
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

    private companion object {
        const val KEY_NAME = "onb_name"
        const val KEY_URL = "onb_url"
        const val KEY_LOCAL_URL = "onb_local_url"
        const val KEY_SSID_INPUT = "onb_ssid_input"
        const val KEY_SSIDS = "onb_ssids"
        const val KEY_TOKEN = "onb_token"
    }
}
