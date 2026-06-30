@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.uc.homehealth.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoAwareHomeRepository @Inject constructor(
    private val authPreferences: AuthPreferences,
    private val fake: FakeHomeRepository,
    private val real: HaHomeRepository,
) : HomeRepository {

    private val isDemo: Flow<Boolean> = authPreferences.authState
        .map { it.accessToken.isEmpty() }
        .distinctUntilChanged()

    private fun <T> route(block: (HomeRepository) -> Flow<T>): Flow<T> =
        isDemo.flatMapLatest { demo -> block(if (demo) fake else real) }

    private suspend fun authenticated() =
        authPreferences.authState.first().accessToken.isNotEmpty()

    override fun getRooms() = route { it.getRooms() }
    override fun getAllRooms() = route { it.getAllRooms() }
    override fun getScenes() = route { it.getScenes() }
    override fun getAllScenes() = route { it.getAllScenes() }
    override fun getFavorites() = route { it.getFavorites() }
    override fun getAllEntities() = route { it.getAllEntities() }
    override fun getAutomations() = route { it.getAutomations() }
    override fun getNotifications() = route { it.getNotifications() }
    override fun getLightsForRoom(areaId: String) = route { it.getLightsForRoom(areaId) }
    override fun getLight(entityId: String) = route { it.getLight(entityId) }
    override fun getClimateForRoom(areaId: String) = route { it.getClimateForRoom(areaId) }
    override fun getClimate(entityId: String) = route { it.getClimate(entityId) }
    override fun getTempHistory(areaId: String) = route { it.getTempHistory(areaId) }
    override fun getEntityHistory(entityId: String) = route { it.getEntityHistory(entityId) }
    override fun getEntityState(entityId: String) = route { it.getEntityState(entityId) }
    override fun getPersonLocation(entityId: String) = route { it.getPersonLocation(entityId) }
    override fun getTrackedFlights() = route { it.getTrackedFlights() }
    override fun isFlightRadar24Available() = route { it.isFlightRadar24Available() }
    override fun getMediaPlayer(entityId: String) = route { it.getMediaPlayer(entityId) }
    override fun getCameraSnapshotUrl(entityId: String) = route { it.getCameraSnapshotUrl(entityId) }
    override fun connectionStatus() = route { it.connectionStatus() }
    override fun authError() = route { it.authError() }
    override fun getUpdates() = route { it.getUpdates() }
    override fun getSolarProduction(entityId: String) = route { it.getSolarProduction(entityId) }
    override fun getBatterySoc(entityId: String) = route { it.getBatterySoc(entityId) }
    override fun getBatteryPower(entityId: String) = route { it.getBatteryPower(entityId) }
    override fun getGridPower(entityId: String) = route { it.getGridPower(entityId) }
    override fun getEnergyHistory(entityId: String) = route { it.getEnergyHistory(entityId) }
    override fun getHomeCoords() = route { it.getHomeCoords() }
    override fun getAutoEnergy() = route { it.getAutoEnergy() }
    override fun getPulse() = route { it.getPulse() }

    override suspend fun deleteNotification(id: Long) {
        if (authenticated()) real.deleteNotification(id) else fake.deleteNotification(id)
    }
    override suspend fun clearNotifications() {
        // Clear the on-device Room store (real) regardless of auth AND the demo list
        // (fake), so "delete all activity data" honestly wipes everything on device.
        real.clearNotifications()
        fake.clearNotifications()
    }

    override suspend fun toggleLight(entityId: String, isOn: Boolean) {
        if (authenticated()) real.toggleLight(entityId, isOn)
    }
    override suspend fun setLightBrightness(entityId: String, brightness: Int) {
        if (authenticated()) real.setLightBrightness(entityId, brightness)
    }
    override suspend fun setLightColor(entityId: String, r: Int, g: Int, b: Int) {
        if (authenticated()) real.setLightColor(entityId, r, g, b)
    }
    override suspend fun setLightColorTemp(entityId: String, kelvin: Int) {
        if (authenticated()) real.setLightColorTemp(entityId, kelvin)
    }
    override suspend fun setClimateTemperature(entityId: String, temperature: Float) {
        if (authenticated()) real.setClimateTemperature(entityId, temperature)
    }
    override suspend fun setClimateHvacMode(entityId: String, mode: String) {
        if (authenticated()) real.setClimateHvacMode(entityId, mode)
    }
    override suspend fun setClimateFanMode(entityId: String, fanMode: String) {
        if (authenticated()) real.setClimateFanMode(entityId, fanMode)
    }
    override suspend fun addTrackedFlight(query: String) {
        if (authenticated()) real.addTrackedFlight(query) else fake.addTrackedFlight(query)
    }
    override suspend fun removeTrackedFlight(query: String) {
        if (authenticated()) real.removeTrackedFlight(query) else fake.removeTrackedFlight(query)
    }
    override suspend fun runScene(sceneId: String) {
        if (authenticated()) real.runScene(sceneId)
    }
    override suspend fun toggleEntity(entityId: String) {
        if (authenticated()) real.toggleEntity(entityId)
    }
    override suspend fun pressEntity(entityId: String) {
        if (authenticated()) real.pressEntity(entityId)
    }
    override suspend fun suggestedGlanceEntityIds(): List<String> =
        if (authenticated()) real.suggestedGlanceEntityIds() else fake.suggestedGlanceEntityIds()
    override suspend fun mediaPlayPause(entityId: String) {
        if (authenticated()) real.mediaPlayPause(entityId)
    }
    override suspend fun mediaSkipNext(entityId: String) {
        if (authenticated()) real.mediaSkipNext(entityId)
    }
    override suspend fun mediaSkipPrev(entityId: String) {
        if (authenticated()) real.mediaSkipPrev(entityId)
    }
    override suspend fun mediaSetVolume(entityId: String, volume: Float) {
        if (authenticated()) real.mediaSetVolume(entityId, volume)
    }
    override suspend fun mediaSetShuffle(entityId: String, on: Boolean) {
        if (authenticated()) real.mediaSetShuffle(entityId, on)
    }
    override suspend fun mediaSetRepeat(entityId: String, mode: MediaRepeatMode) {
        if (authenticated()) real.mediaSetRepeat(entityId, mode)
    }
    override suspend fun mediaSeek(entityId: String, progress: Float) {
        if (authenticated()) real.mediaSeek(entityId, progress)
    }
    override suspend fun mediaTurnOff(entityId: String) {
        if (authenticated()) real.mediaTurnOff(entityId)
    }

    override suspend fun searchMusicAssistant(
        entityId: String,
        query: String,
        mediaType: MaMediaType?,
        libraryOnly: Boolean,
    ): MaSearchResults =
        if (authenticated()) real.searchMusicAssistant(entityId, query, mediaType, libraryOnly)
        else fake.searchMusicAssistant(entityId, query, mediaType, libraryOnly)

    override suspend fun playMusicAssistantMedia(entityId: String, item: MaSearchItem, mode: MaEnqueueMode) {
        if (authenticated()) real.playMusicAssistantMedia(entityId, item, mode)
    }

    override fun getMusicAssistantPlayers() = route { it.getMusicAssistantPlayers() }

    override suspend fun getMaQueue(entityId: String): MaQueue? =
        if (authenticated()) real.getMaQueue(entityId) else fake.getMaQueue(entityId)

    override suspend fun getMaLibrary(
        entityId: String,
        mediaType: MaMediaType,
        favoritesOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<MaSearchItem> =
        if (authenticated()) real.getMaLibrary(entityId, mediaType, favoritesOnly, limit, offset)
        else fake.getMaLibrary(entityId, mediaType, favoritesOnly, limit, offset)

    override suspend fun transferMaQueue(fromEntityId: String, toEntityId: String) {
        if (authenticated()) real.transferMaQueue(fromEntityId, toEntityId)
    }

    override fun getTtsTarget(entityId: String) = route { it.getTtsTarget(entityId) }
    override suspend fun getTtsEngines(): List<HaTtsEngine> =
        if (authenticated()) real.getTtsEngines() else fake.getTtsEngines()
    override suspend fun getTtsVoices(engineId: String, language: String): List<HaTtsVoice> =
        if (authenticated()) real.getTtsVoices(engineId, language) else fake.getTtsVoices(engineId, language)
    override suspend fun sendTts(
        mediaPlayerEntityId: String,
        message: String,
        announce: Boolean,
        engineId: String?,
        voiceId: String?,
        language: String?,
    ) {
        if (authenticated()) real.sendTts(mediaPlayerEntityId, message, announce, engineId, voiceId, language)
        else fake.sendTts(mediaPlayerEntityId, message, announce, engineId, voiceId, language)
    }
    override suspend fun installUpdate(entityId: String, backup: Boolean) {
        if (authenticated()) real.installUpdate(entityId, backup) else fake.installUpdate(entityId, backup)
    }
    override suspend fun skipUpdate(entityId: String) {
        if (authenticated()) real.skipUpdate(entityId) else fake.skipUpdate(entityId)
    }
    override suspend fun clearSkippedUpdate(entityId: String) {
        if (authenticated()) real.clearSkippedUpdate(entityId) else fake.clearSkippedUpdate(entityId)
    }

    override suspend fun getCameraStreamUrl(entityId: String): String? =
        if (authenticated()) real.getCameraStreamUrl(entityId) else null

    override suspend fun startCameraWebRtc(entityId: String, offerSdp: String, onSignal: (WebRtcSignal) -> Unit): Int? =
        if (authenticated()) real.startCameraWebRtc(entityId, offerSdp, onSignal) else null
    override fun sendCameraWebRtcCandidate(entityId: String, sessionId: String, candidate: WebRtcIceCandidate) =
        real.sendCameraWebRtcCandidate(entityId, sessionId, candidate)
    override fun stopCameraWebRtc(subscriptionId: Int) = real.stopCameraWebRtc(subscriptionId)
    override suspend fun getCameraWebRtcConfig(entityId: String): List<WebRtcIceServer>? =
        if (authenticated()) real.getCameraWebRtcConfig(entityId) else null

    override fun reconnectNow() = real.reconnectNow()
}
