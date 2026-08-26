package io.github.landwarderer.futon.mihon.compat

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.network.NetworkHelper
import io.github.landwarderer.futon.core.network.webview.WebViewExecutor
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import javax.inject.Singleton

@Singleton
class MihonInjektBridge(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val cookieJar: CookieJar,
    private val webViewExecutor: WebViewExecutor? = null,
) {

    private val application: Application
        get() = context.applicationContext as Application

    @Volatile
    private var initialized = false

    /**
     * This must be called before loading any Mihon extensions.
     *
     * Thread-safe, can be called multiple times.
     */
    @Synchronized
    fun initialize() {
        if (initialized) return

        try {
            val networkHelper = MihonNetworkHelper(httpClient, cookieJar, webViewExecutor)
            Log.d(
                "MihonInjektBridge",
                "Creating MihonNetworkHelper with webViewExecutorPresent=${webViewExecutor != null}",
            )

            Injekt.importModule(object : InjektModule {
                override fun InjektRegistrar.registerInjectables() {
                    addSingleton(application)
                    addSingletonFactory<Context> { context.applicationContext }

                    addSingletonFactory<NetworkHelper> { networkHelper }
                    addSingletonFactory<OkHttpClient> { httpClient }
                    addSingletonFactory<CookieJar> { cookieJar }

                    addSingletonFactory<SharedPreferences> {
                        PreferenceManager.getDefaultSharedPreferences(context)
                    }

                    val json = Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    }
                    addSingletonFactory<Json> { json }
                    addSingletonFactory<StringFormat> { json }
                    addSingletonFactory<SerialFormat> { json }

                    // Current extension ecosystems may resolve ProtoBuf directly from Injekt.
                    // Futon already ships kotlinx-serialization-protobuf, so expose the same host
                    // service Kototoro provides instead of forcing extensions to construct their own.
                    addSingletonFactory<ProtoBuf> { ProtoBuf }
                }
            })

            initialized = true
            Log.d("MihonInjektBridge", "Injekt initialized with app dependencies")
        } catch (e: Throwable) {
            Log.e("MihonInjektBridge", "CRITICAL: Failed to initialize Injekt bridge", e)
            // Keep Futon usable even when the optional extension runtime cannot initialize.
        }
    }

    fun isInitialized(): Boolean = initialized
}
