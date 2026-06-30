package com.uc.homehealth.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.google.gson.Gson
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("auth") }
        )

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        // Force IPv4-only resolution — prevents a 15s stall when Android tries IPv6 first
        // on a local IPv4 address (192.168.x.x) and the dual-stack handshake hangs.
        .dns { hostname -> Dns.SYSTEM.lookup(hostname).filter { it is Inet4Address }.ifEmpty { Dns.SYSTEM.lookup(hostname) } }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        // 15s WS ping (was 30s). Remote HA usually sits behind a reverse proxy /
        // Cloudflare whose idle-connection timeout can be short; a tighter ping keeps
        // the socket warm and detects a dead link faster so our reconnect loop fires
        // sooner. OkHttp also treats a missed pong within this window as a failure.
        .pingInterval(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false) // we have our own reconnect loop in HaWebSocketClient
        .build()

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()
}
