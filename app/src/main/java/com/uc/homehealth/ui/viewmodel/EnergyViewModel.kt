@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.uc.homehealth.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uc.homehealth.data.AutoEnergy
import com.uc.homehealth.data.CloudGrid
import com.uc.homehealth.data.EnergyConfig
import com.uc.homehealth.data.HaEntitySummary
import com.uc.homehealth.data.HaEntityValue
import com.uc.homehealth.data.HomeCoords
import com.uc.homehealth.data.HomeRepository
import com.uc.homehealth.data.SolarForecast
import com.uc.homehealth.data.SolarForecastRepository
import com.uc.homehealth.data.UserPreferences
import com.uc.homehealth.data.WsConnectionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EnergyUiState(
    val config: EnergyConfig = EnergyConfig(),
    val solar: HaEntityValue? = null,
    val batterySoc: HaEntityValue? = null,
    val batteryPower: HaEntityValue? = null,
    val grid: HaEntityValue? = null,
    val home: HomeCoords? = null,
    val forecast: SolarForecast? = null,
    val productionHistory: List<Float> = emptyList(),
    val cloudGrid: CloudGrid? = null,
    val allEntities: List<HaEntitySummary> = emptyList(),
    // Defaults to CONNECTING so the first composed frame reads as "loading", never as a
    // false "set up your dashboard" flash before the flows emit.
    val connection: WsConnectionStatus = WsConnectionStatus.CONNECTING,
    // True when values are flowing from the HA Energy dashboard's own wiring rather than
    // manually picked sensors — surfaced in the setup sheet.
    val autoWired: Boolean = false,
) {
    /** True once any live energy value is available (always true in demo mode). */
    val hasData: Boolean get() = solar != null || batterySoc != null || batteryPower != null || grid != null

    /**
     * True while the WebSocket is re-establishing (app resumed from background, transient
     * drop). Distinguishes "data wiped, coming right back" from "nothing configured" so the
     * screen never flashes the setup empty-state during a reconnect. Demo mode reports READY.
     */
    val isReconnecting: Boolean
        get() = connection == WsConnectionStatus.CONNECTING ||
            connection == WsConnectionStatus.DISCONNECTED ||
            connection == WsConnectionStatus.ERROR
}

// Live energy values folded into one holder so the root combine stays within its 5-arg arity.
private data class LiveEnergy(
    val config: EnergyConfig,
    val solar: HaEntityValue?,
    val batterySoc: HaEntityValue?,
    val batteryPower: HaEntityValue?,
    val grid: HaEntityValue?,
    val productionHistory: List<Float>,
    val autoWired: Boolean,
)

@HiltViewModel
class EnergyViewModel @Inject constructor(
    private val repo: HomeRepository,
    private val userPreferences: UserPreferences,
    private val solarForecastRepo: SolarForecastRepository,
) : ViewModel() {

    // Re-subscribe the per-chip flows whenever the configured entity ids change. Each role
    // resolves manual pick → Energy-dashboard auto-wiring (Helios's single source of truth).
    private val liveEnergy: Flow<LiveEnergy> = userPreferences.energyConfig.flatMapLatest { cfg ->
        combine(
            repo.getSolarProduction(cfg.solarProductionId),
            repo.getBatterySoc(cfg.batterySocId),
            repo.getBatteryPower(cfg.batteryPowerId),
            repo.getGridPower(cfg.gridPowerId),
            combine(repo.getEnergyHistory(cfg.solarProductionId), repo.getAutoEnergy()) { h, a -> h to a },
        ) { solar, soc, batP, grid, (history, auto) ->
            LiveEnergy(
                config = cfg,
                solar = solar ?: auto?.solar,
                batterySoc = soc ?: auto?.batterySoc,
                batteryPower = batP ?: auto?.batteryPower,
                grid = grid ?: auto?.grid,
                productionHistory = history,
                autoWired = auto != null,
            )
        }
    }

    // Open-Meteo forecast for the resolved home location. Cached in the repository, so this
    // refetches only when the coordinates change (not on every UI recomposition).
    private val forecast: Flow<SolarForecast?> = repo.getHomeCoords().flatMapLatest { home ->
        if (home == null) flowOf(null)
        else flow { emit(solarForecastRepo.forecast(home.latitude, home.longitude)) }
    }

    // Weather-mode cloud field, fetched lazily when the user enters weather mode. Retries
    // on every entry until a grid lands — a one-shot guard would mean a single failed
    // fetch left weather mode cloudless forever.
    private val _cloudGrid = MutableStateFlow<CloudGrid?>(null)
    private var cloudFetchInFlight = false

    fun ensureCloudGrid() {
        if (cloudFetchInFlight || _cloudGrid.value != null) return
        cloudFetchInFlight = true
        viewModelScope.launch {
            try {
                val home = repo.getHomeCoords().firstOrNull { it != null } ?: return@launch
                _cloudGrid.value = solarForecastRepo.cloudGrid(home.latitude, home.longitude)
            } finally {
                cloudFetchInFlight = false
            }
        }
    }

    // The entity-setup sheet is rendered at the top level of the nav host (above the bottom
    // nav, like the room sheet), so its visibility lives here rather than in the screen.
    private val _showSetup = MutableStateFlow(false)
    val showSetup: StateFlow<Boolean> = _showSetup.asStateFlow()
    fun openSetup() { _showSetup.value = true }
    fun dismissSetup() { _showSetup.value = false }

    // Tapping the home pin opens the sun-&-sky detail sheet (Helios's home dashboard).
    private val _showHomeDetail = MutableStateFlow(false)
    val showHomeDetail: StateFlow<Boolean> = _showHomeDetail.asStateFlow()
    fun openHomeDetail() { _showHomeDetail.value = true }
    fun dismissHomeDetail() { _showHomeDetail.value = false }

    val uiState: StateFlow<EnergyUiState> = combine(
        liveEnergy,
        repo.getHomeCoords(),
        repo.getAllEntities(),
        forecast,
        // Folded pair keeps the combine within its 5-arg arity.
        combine(_cloudGrid, repo.connectionStatus()) { clouds, conn -> clouds to conn },
    ) { live, home, entities, fc, (clouds, conn) ->
        EnergyUiState(
            config = live.config,
            solar = live.solar,
            batterySoc = live.batterySoc,
            batteryPower = live.batteryPower,
            grid = live.grid,
            home = home,
            forecast = fc,
            productionHistory = live.productionHistory,
            cloudGrid = clouds,
            allEntities = entities,
            connection = conn,
            autoWired = live.autoWired,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EnergyUiState(),
    )

    fun setSolarEntity(entityId: String) = update { userPreferences.setEnergySolarId(entityId) }
    fun setBatterySocEntity(entityId: String) = update { userPreferences.setEnergyBatterySocId(entityId) }
    fun setBatteryPowerEntity(entityId: String) = update { userPreferences.setEnergyBatteryPowerId(entityId) }
    fun setGridEntity(entityId: String) = update { userPreferences.setEnergyGridId(entityId) }
    fun setPvPeakKwp(kwp: Float) = update { userPreferences.setEnergyPvPeakKwp(kwp) }

    private inline fun update(crossinline block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
