package com.uc.homehealth.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uc.homehealth.data.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the "Additional Card Settings" page + the bottom-nav-bar editor. */
@HiltViewModel
class CardSettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
) : ViewModel() {

    val roomWarningsEnabled: StateFlow<Boolean> = userPreferences.roomWarningsEnabled.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        true,
    )

    val smartGlanceEnabled: StateFlow<Boolean> = userPreferences.smartGlanceEnabled.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        true,
    )

    // Ordered bottom-nav tab keys. Null until the first DataStore emission — the nav host
    // gates on it so a cold launch never flashes the default tab set (or the wrong root
    // tab) before the user's saved arrangement arrives.
    val navTabKeys: StateFlow<List<String>?> = userPreferences.navTabKeys
        .map<List<String>, List<String>?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setNavTabKeys(keys: List<String>) {
        viewModelScope.launch { userPreferences.setNavTabKeys(keys) }
    }

    fun setRoomWarningsEnabled(value: Boolean) {
        viewModelScope.launch { userPreferences.setRoomWarningsEnabled(value) }
    }

    fun setSmartGlanceEnabled(value: Boolean) {
        viewModelScope.launch { userPreferences.setSmartGlanceEnabled(value) }
    }
}
