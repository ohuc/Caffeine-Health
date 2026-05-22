package com.uc.homehealth.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the current Wi-Fi SSID and tracks Wi-Fi reachability.
 *
 * Mirrors the official Home Assistant companion app's `WifiHelperImpl`
 * (https://github.com/home-assistant/android `common/.../WifiHelperImpl.kt`):
 *
 * 1. **SSID is read via the deprecated `WifiManager.connectionInfo.ssid`.**
 *    The HA team explicitly chose this path with the comment
 *    *"Deprecated but callback doesn't provide SSID info instantly"*.
 *    On Android 12+ the modern `NetworkCapabilities.transportInfo` path only
 *    yields a non-redacted `WifiInfo` when the callback is registered with a
 *    `TRANSPORT_WIFI`-filtered `NetworkRequest`, **and** the value is delivered
 *    *asynchronously* via `onCapabilitiesChanged`. For a synchronous "what SSID
 *    am I on right now?" read (i.e. button click handlers), the deprecated
 *    API is the only reliable option even on API 33+.
 *
 * 2. **Wi-Fi-as-active-transport** is checked synchronously via
 *    `activeNetwork.getNetworkCapabilities().hasTransport(TRANSPORT_WIFI)` —
 *    same pattern HA companion uses in `isUsingWifi()`.
 *
 * 3. **Reactive updates** come from a `TRANSPORT_WIFI`-filtered network
 *    callback plus a default-network callback (for "Wi-Fi lost, switched to
 *    cellular" transitions). Each callback just calls `refresh()` which
 *    re-reads via the synchronous path above.
 *
 * Required runtime permission: `ACCESS_FINE_LOCATION`. Required system
 * setting: Location Services ON. Without either, Android redacts the SSID
 * to `WifiManager.UNKNOWN_SSID` (`<unknown ssid>`).
 */
@Singleton
class NetworkLocator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.getSystemService(WifiManager::class.java)
    private val lm = context.getSystemService(LocationManager::class.java)

    private val _currentSsid = MutableStateFlow<String?>(null)
    val currentSsid: StateFlow<String?> = _currentSsid.asStateFlow()

    init {
        // Default-network callback so we react when Wi-Fi drops out entirely
        // (device falls back to cellular). Without this we'd never clear the
        // SSID when the user walks away from the home network.
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "default-network onAvailable → refresh")
                    refresh()
                }
                override fun onLost(network: Network) {
                    Log.d(TAG, "default-network onLost → refresh")
                    refresh()
                }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    refresh()
                }
            })
        }.onFailure { Log.w(TAG, "registerDefaultNetworkCallback failed", it) }

        // Wi-Fi-filtered callback so we react to SSID changes even when Wi-Fi
        // is not the default network (e.g. dual-data devices). Using a filtered
        // request is also the modern blessed path — it's what makes
        // `transportInfo.ssid` come through unredacted on Android 12+, though
        // we don't actually need that since we use the deprecated getter.
        runCatching {
            val req = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            cm.registerNetworkCallback(req, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "wifi-network onAvailable → refresh")
                    refresh()
                }
                override fun onLost(network: Network) {
                    Log.d(TAG, "wifi-network onLost → refresh")
                    refresh()
                }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    refresh()
                }
            })
        }.onFailure { Log.w(TAG, "registerNetworkCallback(WIFI) failed", it) }

        refresh()
    }

    fun hasLocationPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    /** Is the OS-level Location toggle on? Required for SSID reads on Android 8+. */
    fun isLocationEnabled(): Boolean =
        runCatching { lm?.isLocationEnabled == true }.getOrDefault(false)

    /** True iff the active data network is Wi-Fi. */
    fun isUsingWifi(): Boolean = runCatching {
        cm.activeNetwork
            ?.let { cm.getNetworkCapabilities(it) }
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }.getOrDefault(false)

    /**
     * Re-read the SSID synchronously. Caller pattern is:
     * `networkLocator.refresh(); networkLocator.currentSsid.value`.
     * After this returns, `currentSsid.value` reflects the latest read.
     */
    @Suppress("DEPRECATION")
    fun refresh() {
        val perm = hasLocationPermission()
        val locOn = isLocationEnabled()
        val onWifi = isUsingWifi()

        if (!perm) {
            Log.i(TAG, "refresh: ssid=null reason=ACCESS_FINE_LOCATION not granted")
            _currentSsid.value = null
            return
        }
        if (!locOn) {
            // Try anyway — some OEMs return a real SSID. But warn loudly.
            Log.w(TAG, "refresh: Location Services OFF — SSID will likely be UNKNOWN_SSID")
        }
        if (!onWifi) {
            Log.i(TAG, "refresh: ssid=null reason=Wi-Fi not the active data network")
            _currentSsid.value = null
            return
        }

        // HA companion path — deprecated, but the only API that returns the
        // SSID synchronously on demand. Permission + location toggle + active
        // Wi-Fi transport are all confirmed above.
        val raw = wifi?.connectionInfo?.ssid
        val cleaned = raw
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID }
        Log.i(TAG, "refresh: perm=$perm locOn=$locOn onWifi=$onWifi raw=$raw → ssid=$cleaned")
        _currentSsid.value = cleaned
    }

    private companion object { const val TAG = "HomeHealth_Net" }
}
