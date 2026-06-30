package com.uc.homehealth.data

// Music Assistant search support. MA media players (registry platform
// "music_assistant") accept the integration's own `music_assistant.search` /
// `music_assistant.play_media` services, which is what the media card's search
// sheet drives — no separate MA connection or page needed (the HA integration
// proxies everything; same approach as the community mass-search-card).

/** The media kinds `music_assistant.search` can return / filter on. */
enum class MaMediaType(val haValue: String, val label: String) {
    ARTIST("artist", "Artists"),
    ALBUM("album", "Albums"),
    TRACK("track", "Tracks"),
    PLAYLIST("playlist", "Playlists"),
    RADIO("radio", "Radio"),
}

/** How a picked item lands on the player's queue (`play_media`'s `enqueue`). */
enum class MaEnqueueMode(val haValue: String) {
    PLAY("play"),   // play now, keep the rest of the queue
    ADD("add"),     // append to the end of the queue
}

// One search hit, flattened for the results list. `uri` is MA's canonical media id
// (e.g. "spotify://track/…" or "library://album/…") and is what play_media consumes.
data class MaSearchItem(
    val uri: String,
    val name: String,
    val mediaType: MaMediaType,
    val subtitle: String,     // joined artist names / album — "" when MA sends none
    val imageUrl: String?,    // absolute http(s) artwork URL when available
)

// Snapshot of a player's active queue (`music_assistant.get_queue`). HA's service
// only exposes the current/next items plus counters — the full item list lives in
// MA's own API, which this app deliberately doesn't connect to.
data class MaQueue(
    val currentTitle: String?,
    val currentArtist: String?,
    val nextTitle: String?,
    val nextArtist: String?,
    val itemCount: Int,
    val currentIndex: Int?,
)

data class MaSearchResults(
    val artists: List<MaSearchItem> = emptyList(),
    val albums: List<MaSearchItem> = emptyList(),
    val tracks: List<MaSearchItem> = emptyList(),
    val playlists: List<MaSearchItem> = emptyList(),
    val radio: List<MaSearchItem> = emptyList(),
) {
    val isEmpty: Boolean
        get() = artists.isEmpty() && albums.isEmpty() && tracks.isEmpty() &&
            playlists.isEmpty() && radio.isEmpty()

    /** Sections in display order, skipping empty ones. */
    fun sections(): List<Pair<MaMediaType, List<MaSearchItem>>> = listOf(
        MaMediaType.TRACK to tracks,
        MaMediaType.ARTIST to artists,
        MaMediaType.ALBUM to albums,
        MaMediaType.PLAYLIST to playlists,
        MaMediaType.RADIO to radio,
    ).filter { it.second.isNotEmpty() }
}
