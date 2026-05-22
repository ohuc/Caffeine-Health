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
    override fun getClimateForRoom(areaId: String) = route { it.getClimateForRoom(areaId) }
    override fun getTempHistory(areaId: String) = route { it.getTempHistory(areaId) }
    override fun getEntityHistory(entityId: String) = route { it.getEntityHistory(entityId) }
    override fun getEntityState(entityId: String) = route { it.getEntityState(entityId) }
    override fun getTrackedFlights() = route { it.getTrackedFlights() }
    override fun isFlightRadar24Available() = route { it.isFlightRadar24Available() }
    override fun getMediaPlayer(entityId: String) = route { it.getMediaPlayer(entityId) }
    override fun getCameraSnapshotUrl(entityId: String) = route { it.getCameraSnapshotUrl(entityId) }
    override fun connectionStatus() = route { it.connectionStatus() }

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
    override suspend fun getCameraStreamUrl(entityId: String): String? =
        if (authenticated()) real.getCameraStreamUrl(entityId) else null

    override fun reconnectNow() = real.reconnectNow()
}
