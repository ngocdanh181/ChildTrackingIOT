package com.example.childtrackingappiot.utils

import android.content.Context
import android.content.SharedPreferences

class PrefsManager private constructor(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "ChildTrackingPrefs"
        private const val KEY_TOKEN = "token"
        private const val KEY_HTTP_SERVER = "http_server"
        private const val KEY_WS_SERVER = "ws_server"
        
        @Volatile
        private var instance: PrefsManager? = null

        fun getInstance(context: Context): PrefsManager {
            return instance ?: synchronized(this) {
                instance ?: PrefsManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun saveServerConfig(httpServer: String, wsServer: String) {
        prefs.edit()
            .putString(KEY_HTTP_SERVER, httpServer)
            .putString(KEY_WS_SERVER, wsServer)
            .apply()
    }

    fun getHttpServer(): String? = prefs.getString(KEY_HTTP_SERVER, null)
    fun getWsServer(): String? = prefs.getString(KEY_WS_SERVER, null)

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }
} 