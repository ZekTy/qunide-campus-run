package com.example.campusrunner.data

import android.content.Context

class UserSettings(context: Context) {
    private val prefs = context.getSharedPreferences("campus_runner_settings", Context.MODE_PRIVATE)

    var mapProvider: MapProvider
        get() = runCatching {
            MapProvider.valueOf(prefs.getString(KEY_MAP_PROVIDER, MapProvider.AUTO.name) ?: MapProvider.AUTO.name)
        }.getOrDefault(MapProvider.AUTO)
        set(value) {
            prefs.edit().putString(KEY_MAP_PROVIDER, value.name).apply()
        }

    private companion object {
        const val KEY_MAP_PROVIDER = "map_provider"
    }
}
