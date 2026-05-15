package com.example.campusrunner.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.campusrunner.MainActivity
import com.example.campusrunner.data.LocationCache
import com.example.campusrunner.data.PlaybackMode
import com.example.campusrunner.data.RoutePoint
import com.example.campusrunner.data.SavedRoute
import com.example.campusrunner.data.SimulatedLocation
import com.example.campusrunner.geo.RouteMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MockLocationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var locationManager: LocationManager
    private var job: Job? = null
    private var activeProviders: List<String> = emptyList()
    private var startedAtMillis: Long = 0L
    private var pausedAtMillis: Long = 0L
    private var totalPausedMillis: Long = 0L
    private var lastPushedLocation: SimulatedLocation? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_POINT -> startPoint(
                lat = intent.getDoubleExtra(EXTRA_LAT, 0.0),
                lng = intent.getDoubleExtra(EXTRA_LNG, 0.0)
            )

            ACTION_START_ROUTE -> startRoute(
                routeJson = intent.getStringExtra(EXTRA_ROUTE_JSON).orEmpty(),
                speedMps = intent.getDoubleExtra(EXTRA_SPEED_MPS, 0.0),
                closeLoop = intent.getBooleanExtra(EXTRA_CLOSE_LOOP, false),
                loopCount = intent.getIntExtra(EXTRA_LOOP_COUNT, 1).coerceAtLeast(1)
            )

            ACTION_PAUSE -> pauseMocking()
            ACTION_RESUME -> resumeMocking()
            ACTION_STOP -> stopMocking()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopMocking(stopService = false)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPoint(lat: Double, lng: Double) {
        startMockLoop(
            notificationText = "定点模拟中：${formatCoord(lat)}, ${formatCoord(lng)}",
            locationProvider = {
                SimulatedLocation(
                    latWgs84 = lat,
                    lngWgs84 = lng,
                    speedMps = 0f,
                    bearing = 0f,
                    accuracyMeters = 5f,
                    altitudeMeters = 10.0
                )
            }
        )
    }

    private fun startRoute(routeJson: String, speedMps: Double, closeLoop: Boolean, loopCount: Int) {
        val route = decodeRoute(routeJson) ?: return
        if (speedMps <= 0.0) {
            stopSelf()
            return
        }
        val playbackMode = if (closeLoop) PlaybackMode.LOOP else PlaybackMode.OUT_AND_BACK
        val maxElapsedMillis = if (closeLoop) {
            val loopDistance = RouteMath.totalDistanceMeters(route.points, closeLoop = true)
            ((loopDistance * loopCount / speedMps) * 1000.0).toLong()
        } else {
            Long.MAX_VALUE
        }

        startMockLoop(
            notificationText = "路线模拟中：${route.name} $speedMps m/s",
            locationProvider = {
                val elapsedMillis = activeElapsedMillis()
                if (elapsedMillis >= maxElapsedMillis) {
                    scope.launch { stopMocking() }
                }
                RouteMath.interpolateRoute(
                    points = route.points,
                    elapsedMillis = elapsedMillis,
                    speedMps = speedMps,
                    playbackMode = playbackMode
                )
            }
        )
    }

    private fun startMockLoop(notificationText: String, locationProvider: () -> SimulatedLocation) {
        if (!hasFineLocationPermission()) {
            stopSelf()
            return
        }

        job?.cancel()
        job = null
        removeActiveProviders()

        val readyProviders = PROVIDERS.filter { setupProvider(it) }
        if (readyProviders.isEmpty()) {
            isRunning = false
            isPaused = false
            removeActiveProviders()
            stopSelf()
            return
        }
        activeProviders = readyProviders

        startedAtMillis = System.currentTimeMillis()
        pausedAtMillis = 0L
        totalPausedMillis = 0L
        lastPushedLocation = locationProvider()
        isRunning = true
        isPaused = false
        startForeground(NOTIFICATION_ID, buildNotification(notificationText))
        activeProviders.forEach { provider ->
            pushLocation(provider, lastPushedLocation ?: locationProvider())
        }

        job = scope.launch {
            while (isActive) {
                val loc = if (isPaused) {
                    lastPushedLocation ?: locationProvider()
                } else {
                    locationProvider().also { lastPushedLocation = it }
                }
                val pushed = activeProviders.count { provider -> pushLocation(provider, loc) }
                if (pushed == 0) {
                    Log.w(TAG, "No mock providers accepted location; stopping mock loop")
                    stopMocking()
                    return@launch
                }
                delay(1000L)
            }
        }
    }

    private fun pauseMocking() {
        if (!isRunning || isPaused) return
        pausedAtMillis = System.currentTimeMillis()
        isPaused = true
    }

    private fun resumeMocking() {
        if (!isRunning || !isPaused) return
        totalPausedMillis += System.currentTimeMillis() - pausedAtMillis
        pausedAtMillis = 0L
        isPaused = false
    }

    private fun stopMocking(stopService: Boolean = true) {
        job?.cancel()
        job = null
        isRunning = false
        isPaused = false
        pausedAtMillis = 0L
        totalPausedMillis = 0L
        lastPushedLocation = null
        removeActiveProviders()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (stopService) {
            stopSelf()
        }
    }

    private fun activeElapsedMillis(): Long {
        val currentPause = if (isPaused && pausedAtMillis > 0L) {
            System.currentTimeMillis() - pausedAtMillis
        } else {
            0L
        }
        return System.currentTimeMillis() - startedAtMillis - totalPausedMillis - currentPause
    }

    private fun hasFineLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun setupProvider(provider: String): Boolean {
        return runCatching {
            removeProvider(provider)
            MockPermission.addProvider(locationManager, provider)
            locationManager.setTestProviderEnabled(provider, true)
        }.onFailure {
            Log.w(TAG, "Failed to set up mock provider $provider", it)
        }.isSuccess
    }

    private fun removeActiveProviders() {
        val providers = activeProviders.ifEmpty { PROVIDERS }
        providers.forEach(::removeProvider)
        activeProviders = emptyList()
    }

    private fun removeProvider(provider: String) {
        runCatching { locationManager.setTestProviderEnabled(provider, false) }
        runCatching { locationManager.removeTestProvider(provider) }
    }

    private fun pushLocation(provider: String, simulated: SimulatedLocation): Boolean {
        LocationCache.save(this, RoutePoint(simulated.latWgs84, simulated.lngWgs84))
        val location = Location(provider).apply {
            latitude = simulated.latWgs84
            longitude = simulated.lngWgs84
            accuracy = simulated.accuracyMeters
            altitude = simulated.altitudeMeters
            speed = simulated.speedMps
            bearing = simulated.bearing
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            extras = Bundle().apply {
                putInt("satellites", 8)
                putInt("satellitesvalue", 8)
            }
        }
        return runCatching {
            locationManager.setTestProviderLocation(provider, location)
        }.onFailure {
            Log.w(TAG, "Failed to push mock location to $provider", it)
        }.isSuccess
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            1,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("去你的校园跑")
            .setContentText(text)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "停止", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "模拟定位服务", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun decodeRoute(routeJson: String): SavedRoute? {
        return runCatching {
            val obj = JSONObject(routeJson)
            val pointsArray = obj.getJSONArray("points")
            val points = buildList {
                for (i in 0 until pointsArray.length()) {
                    val p = pointsArray.getJSONObject(i)
                    add(RoutePoint(p.getDouble("latWgs84"), p.getDouble("lngWgs84")))
                }
            }
            SavedRoute(
                id = obj.getString("id"),
                name = obj.getString("name"),
                points = points,
                closeLoop = obj.optBoolean("closeLoop", false),
                loopCount = obj.optInt("loopCount", 1).coerceAtLeast(1)
            )
        }.getOrNull()
    }

    private fun formatCoord(value: Double): String = String.format("%.6f", value)

    companion object {
        const val ACTION_START_POINT = "com.example.campusrunner.START_POINT"
        const val ACTION_START_ROUTE = "com.example.campusrunner.START_ROUTE"
        const val ACTION_PAUSE = "com.example.campusrunner.PAUSE"
        const val ACTION_RESUME = "com.example.campusrunner.RESUME"
        const val ACTION_STOP = "com.example.campusrunner.STOP"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        const val EXTRA_ROUTE_JSON = "route_json"
        const val EXTRA_SPEED_MPS = "speed_mps"
        const val EXTRA_CLOSE_LOOP = "close_loop"
        const val EXTRA_LOOP_COUNT = "loop_count"

        private const val CHANNEL_ID = "mock_location"
        private const val NOTIFICATION_ID = 2201
        private const val TAG = "MockLocationService"
        private val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var isPaused: Boolean = false
            private set

        fun pointIntent(context: Context, lat: Double, lng: Double): Intent {
            return Intent(context, MockLocationService::class.java).apply {
                action = ACTION_START_POINT
                putExtra(EXTRA_LAT, lat)
                putExtra(EXTRA_LNG, lng)
            }
        }

        fun routeIntent(
            context: Context,
            route: SavedRoute,
            speedMps: Double,
            closeLoop: Boolean,
            loopCount: Int
        ): Intent {
            return Intent(context, MockLocationService::class.java).apply {
                action = ACTION_START_ROUTE
                putExtra(EXTRA_SPEED_MPS, speedMps)
                putExtra(EXTRA_CLOSE_LOOP, closeLoop)
                putExtra(EXTRA_LOOP_COUNT, loopCount.coerceAtLeast(1))
                putExtra(EXTRA_ROUTE_JSON, JSONObject().apply {
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
                }.toString())
            }
        }

        fun pauseIntent(context: Context): Intent {
            return Intent(context, MockLocationService::class.java).apply {
                action = ACTION_PAUSE
            }
        }

        fun resumeIntent(context: Context): Intent {
            return Intent(context, MockLocationService::class.java).apply {
                action = ACTION_RESUME
            }
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, MockLocationService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }
}
