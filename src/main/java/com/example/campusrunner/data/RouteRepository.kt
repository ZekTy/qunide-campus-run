package com.example.campusrunner.data

import android.content.Context
import java.util.UUID

class RouteRepository(context: Context) {
    private val prefs = context.getSharedPreferences("campus_runner_routes", Context.MODE_PRIVATE)

    fun getRoutes(): List<SavedRoute> {
        return RouteJson.decode(prefs.getString(KEY_ROUTES, "[]") ?: "[]")
    }

    fun saveRoute(
        name: String,
        points: List<RoutePoint>,
        closeLoop: Boolean,
        loopCount: Int
    ): SavedRoute {
        val trimmedName = name.trim()
        val routes = getRoutes().toMutableList()
        routes.removeAll { it.name == trimmedName }
        val savedRoute = SavedRoute(
            id = UUID.randomUUID().toString(),
            name = trimmedName,
            points = points,
            closeLoop = closeLoop,
            loopCount = loopCount.coerceAtLeast(1)
        )
        routes.add(0, savedRoute)
        saveRoutes(routes)
        return savedRoute
    }

    fun deleteRoute(routeId: String) {
        saveRoutes(getRoutes().filterNot { it.id == routeId })
    }

    private fun saveRoutes(routes: List<SavedRoute>) {
        prefs.edit().putString(KEY_ROUTES, RouteJson.encode(routes)).apply()
    }

    private companion object {
        const val KEY_ROUTES = "routes_json"
    }
}
