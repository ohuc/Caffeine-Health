package com.uc.homehealth.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uc.homehealth.data.AuthPreferences
import com.uc.homehealth.data.HaAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HaAuthViewModel @Inject constructor(
    private val authManager: HaAuthManager,
    private val authPreferences: AuthPreferences,
) : ViewModel() {

    private val codeVerifier = authManager.generateCodeVerifier()

    private val _authComplete = MutableStateFlow(false)
    val authComplete: StateFlow<Boolean> = _authComplete.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun buildAuthUrl(haUrl: String): String = authManager.buildAuthUrl(haUrl, codeVerifier)

    fun exchangeCode(haUrl: String, code: String) {
        viewModelScope.launch {
            try {
                val tokens = authManager.exchangeCode(haUrl, code, codeVerifier)
                authPreferences.saveAuth(haUrl, tokens.accessToken, tokens.refreshToken, tokens.expiresIn)
                _authComplete.value = true
            } catch (e: Exception) {
                _error.value = "Connection failed: ${e.message}"
            }
        }
    }
}
