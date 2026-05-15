package com.example.campusrunner.data

import org.json.JSONArray
import org.json.JSONObject

object RouteJson {
    fun encode(routes: List<SavedRoute>): String {
        val array = JSONArray()
        routes.forEach { route ->
            array.put(JSONObject().apply {
                put("id", route.id)
                put("name", route.name)
                put("closeLoop", route.closeLoop)
                put("loopCount", route.loopCount)
                put("points", JSONArray().apply {
                    route.points.forEach { point ->
                        put(JSONObject().apply {
                            put("latWgs84", point.latWgs84)
                            put("lngWgs84", point.lngWgs84)
                        })
                    }
                })
            })
        }
        return array.toString()
    }

    fun decode(json: String): List<SavedRoute> {
        return try {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val route = array.getJSONObject(i)
                    val pointsArray = route.getJSONArray("points")
                    val points = buildList {
                        for (j in 0 until pointsArray.length()) {
                            val point = pointsArray.getJSONObject(j)
                            add(
                                RoutePoint(
                                    point.getDouble("latWgs84"),
                                    point.getDouble("lngWgs84")
                                )
                            )
                        }
                    }
                    add(
                        SavedRoute(
                            id = route.getString("id"),
                            name = route.getString("name"),
                            points = points,
                            speedMps = route.optDouble("speedMps", 2.6),
                            closeLoop = route.optBoolean("closeLoop", false),
                            loopCount = route.optInt("loopCount", 1).coerceAtLeast(1)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
