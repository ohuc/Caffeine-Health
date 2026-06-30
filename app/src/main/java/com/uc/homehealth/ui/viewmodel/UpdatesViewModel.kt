package com.uc.homehealth.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uc.homehealth.data.HaUpdate
import com.uc.homehealth.data.HomeRepository
import com.uc.homehealth.updates.UpdateInstallService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdatesUiState(
    val updates: List<HaUpdate> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    private val repo: HomeRepository,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    val uiState: StateFlow<UpdatesUiState> = repo.getUpdates()
        .map { UpdatesUiState(updates = it, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdatesUiState())

    // Installs run through a foreground service so they keep going (and stay visible) even
    // if the user closes the app. The service dispatches every command and stops itself
    // once all of them finish. The actual install runs on the HA server regardless.
    fun install(entityId: String, backup: Boolean) {
        val supportsBackup = uiState.value.updates.firstOrNull { it.entityId == entityId }?.supportsBackup == true
        UpdateInstallService.start(
            appContext,
            ids = listOf(entityId),
            backupIds = if (backup && supportsBackup) setOf(entityId) else emptySet(),
        )
    }

    /** Install every available (non-skipped, not-already-installing) update. */
    fun updateAll(backup: Boolean) {
        val targets = uiState.value.updates.filter { !it.isSkipped && !it.inProgress }
        if (targets.isEmpty()) return
        UpdateInstallService.start(
            appContext,
            ids = targets.map { it.entityId },
            backupIds = targets.filter { backup && it.supportsBackup }.map { it.entityId }.toSet(),
        )
    }

    fun skip(entityId: String) {
        viewModelScope.launch { repo.skipUpdate(entityId) }
    }

    fun clearSkipped(entityId: String) {
        viewModelScope.launch { repo.clearSkippedUpdate(entityId) }
    }
}
