package com.uc.homehealth.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uc.homehealth.data.HaMedia
import com.uc.homehealth.data.HomeRepository
import com.uc.homehealth.data.MaEnqueueMode
import com.uc.homehealth.data.MaMediaType
import com.uc.homehealth.data.MaQueue
import com.uc.homehealth.data.MaSearchItem
import com.uc.homehealth.data.MaSearchResults
import com.uc.homehealth.data.MediaRepeatMode
import com.uc.homehealth.data.WsConnectionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// The single Music page: Music Assistant players, the selected player's now-playing
// state, its queue snapshot, and pass-throughs for library/search/playback. Everything
// rides the HA integration (WS) — there is deliberately NO direct MA-server connection.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repo: HomeRepository,
) : ViewModel() {

    val players: StateFlow<List<HaMedia>> = repo.getMusicAssistantPlayers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // True until the connection has actually delivered data: an empty player list
    // mid-connect must read as "loading", never as "no MA players" (the fake-empty-state
    // rule from docs/material3-expressive.md). Demo mode reports READY immediately.
    val playersLoading: StateFlow<Boolean> = combine(repo.connectionStatus(), players) { status, list ->
        list.isEmpty() && status != WsConnectionStatus.READY
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    // Explicit pick wins; otherwise prefer whatever is playing, then the first player.
    private val _pickedPlayerId = MutableStateFlow<String?>(null)
    val selectedPlayerId: StateFlow<String?> = combine(_pickedPlayerId, players) { picked, list ->
        picked?.takeIf { id -> list.any { it.entityId == id } }
            ?: list.firstOrNull { it.isPlaying }?.entityId
            ?: list.firstOrNull()?.entityId
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectPlayer(entityId: String) { _pickedPlayerId.value = entityId }

    // Live hero state for the selected player (same shape the room media card uses).
    val selectedMedia: StateFlow<HaMedia?> = selectedPlayerId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repo.getMediaPlayer(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Queue snapshot, re-fetched when the player or its track changes (HA has no live
    // queue subscription — the now-playing title is the refresh signal).
    val queue: StateFlow<MaQueue?> = selectedMedia
        .map { media -> media?.entityId to media?.title }
        .distinctUntilChanged()
        .mapLatest { (entityId, _) ->
            if (entityId == null) null else repo.getMaQueue(entityId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ── Playback controls (selected player) ──────────────────────────────────
    private fun selected(): String? = selectedPlayerId.value

    fun playPause() { selected()?.let { id -> viewModelScope.launch { repo.mediaPlayPause(id) } } }
    fun skipNext() { selected()?.let { id -> viewModelScope.launch { repo.mediaSkipNext(id) } } }
    fun skipPrev() { selected()?.let { id -> viewModelScope.launch { repo.mediaSkipPrev(id) } } }
    fun setVolume(volume: Float) { selected()?.let { id -> viewModelScope.launch { repo.mediaSetVolume(id, volume) } } }
    fun setShuffle(on: Boolean) { selected()?.let { id -> viewModelScope.launch { repo.mediaSetShuffle(id, on) } } }
    fun setRepeat(mode: MediaRepeatMode) { selected()?.let { id -> viewModelScope.launch { repo.mediaSetRepeat(id, mode) } } }
    fun seek(progress: Float) { selected()?.let { id -> viewModelScope.launch { repo.mediaSeek(id, progress) } } }

    // ── Library / search / queue actions ─────────────────────────────────────
    // Suspend pass-throughs: the page owns paging/debounce state, like the sheets do.
    suspend fun loadLibrary(
        mediaType: MaMediaType,
        favoritesOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<MaSearchItem> {
        val id = selected() ?: return emptyList()
        return repo.getMaLibrary(id, mediaType, favoritesOnly, limit, offset)
    }

    suspend fun search(query: String, mediaType: MaMediaType?, libraryOnly: Boolean): MaSearchResults {
        val id = selected() ?: return MaSearchResults()
        return repo.searchMusicAssistant(id, query, mediaType, libraryOnly)
    }

    fun playItem(item: MaSearchItem, mode: MaEnqueueMode) {
        val id = selected() ?: return
        viewModelScope.launch { repo.playMusicAssistantMedia(id, item, mode) }
    }

    /** Move the active queue to [toEntityId] and follow it with the selection. */
    fun transferQueue(toEntityId: String) {
        val from = selected() ?: return
        if (from == toEntityId) return
        viewModelScope.launch {
            repo.transferMaQueue(from, toEntityId)
            _pickedPlayerId.value = toEntityId
        }
    }
}
