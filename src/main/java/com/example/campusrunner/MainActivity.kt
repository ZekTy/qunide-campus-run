package com.example.campusrunner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.campusrunner.data.LocationCache
import com.example.campusrunner.data.MapProvider
import com.example.campusrunner.data.RoutePoint
import com.example.campusrunner.data.RouteRepository
import com.example.campusrunner.data.SavedRoute
import com.example.campusrunner.data.SpeedPreset
import com.example.campusrunner.data.UserSettings
import com.example.campusrunner.geo.RouteMath
import com.example.campusrunner.nfc.NfcLauncherController
import com.example.campusrunner.service.MockLocationService
import com.example.campusrunner.service.MockPermission
import com.example.campusrunner.ui.CampusMapView
import com.example.campusrunner.ui.MapController
import com.example.campusrunner.ui.MarkerKind
import com.example.campusrunner.ui.theme.CampusRunnerTheme
import com.example.campusrunner.ui.theme.MiuixSkin
import com.example.campusrunner.update.AppUpdateChecker
import com.example.campusrunner.update.AppUpdateResult
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val STARTUP_AGREEMENT_PREFS_NAME = "startup_agreement_prefs"
private const val KEY_INITIAL_LAUNCH_HANDLED = "initial_launch_handled"

private sealed interface UpdateGateState {
    data object Checking : UpdateGateState
    data object UpToDate : UpdateGateState
    data class Required(val result: AppUpdateResult) : UpdateGateState
    data class Failed(val message: String) : UpdateGateState
}

class MainActivity : ComponentActivity() {
    private lateinit var repository: RouteRepository
    private lateinit var userSettings: UserSettings
    private lateinit var nfcLauncher: NfcLauncherController

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshState()
    }

    private var hasLocationPermissionState = mutableStateOf(false)
    private var canMockState = mutableStateOf(false)
    private var routesState = mutableStateOf<List<SavedRoute>>(emptyList())
    private var mapProviderState = mutableStateOf(MapProvider.AUTO)
    private var serviceRunningState = mutableStateOf(false)
    private var servicePausedState = mutableStateOf(false)
    private var nfcActivatedState = mutableStateOf(false)
    private var nfcStatusState = mutableStateOf("")
    private var nfcLinkState = mutableStateOf("")
    private var startupAgreementVisibleState = mutableStateOf(false)
    private var updateGateState = mutableStateOf<UpdateGateState>(UpdateGateState.Checking)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = RouteRepository(this)
        userSettings = UserSettings(this)
        val showStartupAgreement = shouldShowStartupAgreement(savedInstanceState)
        startupAgreementVisibleState.value = showStartupAgreement
        nfcLauncher = NfcLauncherController(this, ::refreshNfcState)
        routesState.value = repository.getRoutes()
        mapProviderState.value = userSettings.mapProvider
        refreshState()
        nfcLauncher.onCreate()
        checkForUpdates()

        setContent {
            CampusRunnerTheme {
                CampusRunnerApp(
                    hasLocationPermission = hasLocationPermissionState.value,
                    canMockLocation = canMockState.value,
                    routes = routesState.value,
                    mapProvider = mapProviderState.value,
                    isServiceRunning = serviceRunningState.value,
                    isServicePaused = servicePausedState.value,
                    isNfcActivated = nfcActivatedState.value,
                    nfcStatusText = nfcStatusState.value,
                    nfcLink = nfcLinkState.value,
                    onRequestPermissions = ::requestRuntimePermissions,
                    onOpenDeveloperOptions = ::openDeveloperOptions,
                    onRefreshState = ::refreshState,
                    onVerifyNfc = nfcLauncher::showActivationDialog,
                    onOpenAlipayNfc = nfcLauncher::openAlipayLink,
                    onShowNfcLink = nfcLauncher::showCurrentLinkDialog,
                    onSaveNfcLink = nfcLauncher::saveLink,
                    onProviderChange = { provider ->
                        userSettings.mapProvider = provider
                        mapProviderState.value = provider
                    },
                    onStartPoint = ::startPoint,
                    onStartRoute = ::startRoute,
                    onPause = ::pauseMocking,
                    onResumeMock = ::resumeMocking,
                    onStop = ::stopMocking,
                    onDeleteRoute = ::deleteRoute,
                    onSaveRoute = ::saveRoute,
                    onLocateMe = ::lastKnownRoutePoint
                )
                when (val updateState = updateGateState.value) {
                    UpdateGateState.Checking -> UpdateCheckingDialog()
                    is UpdateGateState.Failed -> UpdateCheckFailedDialog(
                        message = updateState.message,
                        onRetry = ::checkForUpdates,
                        onExit = { finish() }
                    )

                    is UpdateGateState.Required -> ForceUpdateDialog(
                        latestVersion = updateState.result.latestVersion,
                        message = updateState.result.message,
                        onOpenRelease = { openExternalUrl(updateState.result.releaseUrl) },
                        onRetry = ::checkForUpdates
                    )

                    UpdateGateState.UpToDate -> if (startupAgreementVisibleState.value) {
                        StartupAgreementDialog(
                            onAccept = { startupAgreementVisibleState.value = false },
                            onExit = { finish() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
        nfcLauncher.onResume()
    }

    override fun onPause() {
        nfcLauncher.onPause()
        super.onPause()
    }

    private fun shouldShowStartupAgreement(savedInstanceState: Bundle?): Boolean {
        if (savedInstanceState != null) return false

        val prefs = getSharedPreferences(STARTUP_AGREEMENT_PREFS_NAME, Context.MODE_PRIVATE)
        val initialLaunchHandled = prefs.getBoolean(KEY_INITIAL_LAUNCH_HANDLED, false)
        val isFreshInstallFirstLaunch = !initialLaunchHandled &&
                !NfcLauncherController.hasExistingInstallState(this)

        if (!initialLaunchHandled) {
            prefs.edit().putBoolean(KEY_INITIAL_LAUNCH_HANDLED, true).apply()
        }

        return !isFreshInstallFirstLaunch
    }

    private fun checkForUpdates() {
        updateGateState.value = UpdateGateState.Checking
        Thread {
            try {
                val result = AppUpdateChecker.checkLatest()
                runOnUiThread {
                    updateGateState.value = if (result.updateRequired) {
                        UpdateGateState.Required(result)
                    } else {
                        UpdateGateState.UpToDate
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    updateGateState.value = UpdateGateState.Failed(
                        e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                    )
                }
            }
        }.start()
    }

    private fun openExternalUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(this, "无法打开更新页面", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestRuntimePermissions() {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun refreshState() {
        hasLocationPermissionState.value = hasFineLocationPermission()
        canMockState.value = hasLocationPermissionState.value && MockPermission.canUseMockLocation(this)
        serviceRunningState.value = MockLocationService.isRunning
        servicePausedState.value = MockLocationService.isPaused
        routesState.value = repository.getRoutes()
        refreshNfcState()
    }

    private fun refreshNfcState() {
        nfcActivatedState.value = nfcLauncher.isActivated
        nfcStatusState.value = nfcLauncher.nfcStatusText
        nfcLinkState.value = nfcLauncher.currentLink
    }

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun openDeveloperOptions() {
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }.onFailure {
            Toast.makeText(this, "无法打开开发者选项", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startPoint(latText: String, lngText: String) {
        val lat = latText.toDoubleOrNull()
        val lng = lngText.toDoubleOrNull()
        if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
            Toast.makeText(this, "请输入有效经纬度", Toast.LENGTH_SHORT).show()
            return
        }
        startServiceSafely(MockLocationService.pointIntent(this, lat, lng))
    }

    private fun startRoute(route: SavedRoute, speedText: String) {
        if (route.points.size < 2) {
            Toast.makeText(this, "路线至少需要 2 个点", Toast.LENGTH_SHORT).show()
            return
        }
        val speed = speedText.toDoubleOrNull()
        if (speed == null || speed <= 0.0) {
            Toast.makeText(this, "速度必须大于 0", Toast.LENGTH_SHORT).show()
            return
        }
        startServiceSafely(
            MockLocationService.routeIntent(
                context = this,
                route = route,
                speedMps = speed,
                closeLoop = route.closeLoop,
                loopCount = route.loopCount
            )
        )
    }

    private fun pauseMocking() {
        startService(MockLocationService.pauseIntent(this))
        servicePausedState.value = true
    }

    private fun resumeMocking() {
        startService(MockLocationService.resumeIntent(this))
        servicePausedState.value = false
    }

    private fun stopMocking() {
        startService(MockLocationService.stopIntent(this))
        serviceRunningState.value = false
        servicePausedState.value = false
    }

    private fun startServiceSafely(intent: Intent) {
        if (!nfcLauncher.ensureActivated()) {
            return
        }
        if (!hasLocationPermissionState.value) {
            requestRuntimePermissions()
            return
        }
        if (!canMockState.value) {
            Toast.makeText(this, "请先在开发者选项中选择本应用为模拟位置应用", Toast.LENGTH_LONG).show()
            openDeveloperOptions()
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            serviceRunningState.value = true
            servicePausedState.value = false
        } catch (e: SecurityException) {
            Toast.makeText(this, "没有模拟位置权限，请重新选择模拟位置应用", Toast.LENGTH_LONG).show()
            openDeveloperOptions()
        }
    }

    private fun saveRoute(
        name: String,
        points: List<RoutePoint>,
        closeLoop: Boolean,
        loopCount: Int
    ): SavedRoute? {
        if (name.isBlank()) {
            Toast.makeText(this, "请输入路线名称", Toast.LENGTH_SHORT).show()
            return null
        }
        if (points.size < 2) {
            Toast.makeText(this, "路线至少需要 2 个点", Toast.LENGTH_SHORT).show()
            return null
        }
        val route = repository.saveRoute(name, points, closeLoop, loopCount)
        routesState.value = repository.getRoutes()
        Toast.makeText(this, "路线已保存", Toast.LENGTH_SHORT).show()
        return route
    }

    private fun deleteRoute(route: SavedRoute) {
        repository.deleteRoute(route.id)
        routesState.value = repository.getRoutes()
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownRoutePoint(): RoutePoint? {
        if (!hasFineLocationPermission()) {
            requestRuntimePermissions()
            return null
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providerNames = runCatching { locationManager.allProviders }.getOrDefault(
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        )
        val location = providerNames
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .filter { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
            .maxByOrNull(Location::getTime)

        if (location != null) {
            val point = RoutePoint(location.latitude, location.longitude)
            LocationCache.save(this, point)
            return point
        }

        val cachedPoint = LocationCache.read(this)
        if (cachedPoint != null) {
            Toast.makeText(this, "使用上次记录的位置定位地图", Toast.LENGTH_SHORT).show()
            return cachedPoint
        }

        Toast.makeText(this, "暂时没有当前位置，请先打开系统定位或稍后再试", Toast.LENGTH_SHORT).show()
        return null
    }
}

private enum class Screen {
    HOME,
    POINT_PICKER,
    ROUTE_EDITOR
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun CampusRunnerApp(
    hasLocationPermission: Boolean,
    canMockLocation: Boolean,
    routes: List<SavedRoute>,
    mapProvider: MapProvider,
    isServiceRunning: Boolean,
    isServicePaused: Boolean,
    isNfcActivated: Boolean,
    nfcStatusText: String,
    nfcLink: String,
    onRequestPermissions: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onRefreshState: () -> Unit,
    onVerifyNfc: () -> Unit,
    onOpenAlipayNfc: () -> Unit,
    onShowNfcLink: () -> Unit,
    onSaveNfcLink: (String) -> Unit,
    onProviderChange: (MapProvider) -> Unit,
    onStartPoint: (String, String) -> Unit,
    onStartRoute: (SavedRoute, String) -> Unit,
    onPause: () -> Unit,
    onResumeMock: () -> Unit,
    onStop: () -> Unit,
    onDeleteRoute: (SavedRoute) -> Unit,
    onSaveRoute: (String, List<RoutePoint>, Boolean, Int) -> SavedRoute?,
    onLocateMe: () -> RoutePoint?
) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var editingRoute by remember { mutableStateOf<SavedRoute?>(null) }
    var speedText by remember { mutableStateOf(formatNumber(SpeedPreset.FAST_PACE.speedMps)) }
    var closeLoop by remember { mutableStateOf(false) }
    var loopCountText by remember { mutableStateOf("1") }
    var pointLatInput by remember { mutableStateOf("39.904200") }
    var pointLngInput by remember { mutableStateOf("116.407400") }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1200L)
            onRefreshState()
        }
    }

    BackHandler(enabled = screen != Screen.HOME) {
        screen = Screen.HOME
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            if (targetState == Screen.HOME) {
                slideInHorizontally { -it / 4 } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
            } else {
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it / 4 } + fadeOut()
            }
        },
        label = "screen_transition"
    ) { target ->
        when (target) {
            Screen.HOME -> HomeScreen(
                hasLocationPermission = hasLocationPermission,
                canMockLocation = canMockLocation,
                routes = routes,
                mapProvider = mapProvider,
                isServiceRunning = isServiceRunning,
                isServicePaused = isServicePaused,
                isNfcActivated = isNfcActivated,
                nfcStatusText = nfcStatusText,
                nfcLink = nfcLink,
                speedText = speedText,
                pointLatInput = pointLatInput,
                pointLngInput = pointLngInput,
                onPointLatChange = { pointLatInput = it },
                onPointLngChange = { pointLngInput = it },
                onSpeedTextChange = { speedText = it },
                onRequestPermissions = onRequestPermissions,
                onOpenDeveloperOptions = onOpenDeveloperOptions,
                onVerifyNfc = onVerifyNfc,
                onOpenAlipayNfc = onOpenAlipayNfc,
                onShowNfcLink = onShowNfcLink,
                onSaveNfcLink = onSaveNfcLink,
                onProviderChange = onProviderChange,
                onOpenPointPicker = { screen = Screen.POINT_PICKER },
                onOpenRouteEditor = {
                    editingRoute = null
                    closeLoop = false
                    loopCountText = "1"
                    screen = Screen.ROUTE_EDITOR
                },
                onEditRoute = {
                    editingRoute = it
                    closeLoop = it.closeLoop
                    loopCountText = it.loopCount.toString()
                    screen = Screen.ROUTE_EDITOR
                },
                onStartPoint = { onStartPoint(pointLatInput, pointLngInput) },
                onStartRoute = onStartRoute,
                onPause = onPause,
                onResumeMock = onResumeMock,
                onStop = onStop,
                onDeleteRoute = onDeleteRoute
            )

            Screen.POINT_PICKER -> MapPointPickerScreen(
                mapProvider = mapProvider,
                startPoint = pointLatInput.toDoubleOrNull()?.let { lat ->
                    pointLngInput.toDoubleOrNull()?.let { lng -> RoutePoint(lat, lng) }
                },
                onBack = { screen = Screen.HOME },
                onLocateMe = onLocateMe,
                onPointPicked = { point ->
                    pointLatInput = String.format(Locale.US, "%.6f", point.latWgs84)
                    pointLngInput = String.format(Locale.US, "%.6f", point.lngWgs84)
                    screen = Screen.HOME
                }
            )

            Screen.ROUTE_EDITOR -> RouteEditorScreen(
                mapProvider = mapProvider,
                initialRoute = editingRoute,
                closeLoop = closeLoop,
                loopCountText = loopCountText,
                onCloseLoopChange = { closeLoop = it },
                onLoopCountTextChange = { loopCountText = it },
                onBack = { screen = Screen.HOME },
                onLocateMe = onLocateMe,
                onSaveRoute = { name, points ->
                    val loopCount = loopCountText.toIntOrNull() ?: 1
                    val saved = onSaveRoute(name, points, closeLoop, loopCount)
                    if (saved != null) {
                        editingRoute = saved
                        screen = Screen.HOME
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    hasLocationPermission: Boolean,
    canMockLocation: Boolean,
    routes: List<SavedRoute>,
    mapProvider: MapProvider,
    isServiceRunning: Boolean,
    isServicePaused: Boolean,
    isNfcActivated: Boolean,
    nfcStatusText: String,
    nfcLink: String,
    speedText: String,
    pointLatInput: String,
    pointLngInput: String,
    onPointLatChange: (String) -> Unit,
    onPointLngChange: (String) -> Unit,
    onSpeedTextChange: (String) -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onVerifyNfc: () -> Unit,
    onOpenAlipayNfc: () -> Unit,
    onShowNfcLink: () -> Unit,
    onSaveNfcLink: (String) -> Unit,
    onProviderChange: (MapProvider) -> Unit,
    onOpenPointPicker: () -> Unit,
    onOpenRouteEditor: () -> Unit,
    onEditRoute: (SavedRoute) -> Unit,
    onStartPoint: () -> Unit,
    onStartRoute: (SavedRoute, String) -> Unit,
    onPause: () -> Unit,
    onResumeMock: () -> Unit,
    onStop: () -> Unit,
    onDeleteRoute: (SavedRoute) -> Unit
) {
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }
    var overviewExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("RouteVerge", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    PermissionDots(
                        hasLocationPermission = hasLocationPermission,
                        canMockLocation = canMockLocation,
                        onClick = { overviewExpanded = !overviewExpanded },
                        onLongPress = { showStatusDialog = true }
                    )
                    Box {
                        TextButton(
                            onClick = { providerMenuExpanded = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MiuixSkin.Primary)
                        ) {
                            Text(mapProvider.label)
                        }
                        DropdownMenu(
                            expanded = providerMenuExpanded,
                            onDismissRequest = { providerMenuExpanded = false }
                        ) {
                            MapProvider.entries.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.label) },
                                    onClick = {
                                        providerMenuExpanded = false
                                        onProviderChange(provider)
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
        ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AnimatedVisibility(
                    visible = overviewExpanded,
                    enter = expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + scaleIn(
                        initialScale = 0.94f,
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f),
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(),
                    exit = shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + scaleOut(
                        targetScale = 0.94f,
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                    ) + fadeOut()
                ) {
                    HomeOverviewPanel(
                        hasLocationPermission = hasLocationPermission,
                        canMockLocation = canMockLocation,
                        isServiceRunning = isServiceRunning,
                        isServicePaused = isServicePaused,
                        isNfcActivated = isNfcActivated,
                        routesCount = routes.size,
                        mapProvider = mapProvider,
                        onShowStatus = { showStatusDialog = true }
                    )
                }
            }

            item {
                PointSimulationCard(
                    latInput = pointLatInput,
                    lngInput = pointLngInput,
                    isServiceRunning = isServiceRunning,
                    onLatChange = onPointLatChange,
                    onLngChange = onPointLngChange,
                    onOpenPointPicker = onOpenPointPicker,
                    onStartPoint = onStartPoint,
                    onStop = onStop
                )
            }

            item {
                RouteSimulationCard(
                    speedText = speedText,
                    routes = routes,
                    isServiceRunning = isServiceRunning,
                    isServicePaused = isServicePaused,
                    onSpeedTextChange = onSpeedTextChange,
                    onOpenRouteEditor = onOpenRouteEditor,
                    onEditRoute = onEditRoute,
                    onStartRoute = onStartRoute,
                    onPause = onPause,
                    onResumeMock = onResumeMock,
                    onStop = onStop,
                    onDeleteRoute = onDeleteRoute
                )
            }

            item {
                NfcLauncherCard(
                    isActivated = isNfcActivated,
                    statusText = nfcStatusText,
                    currentLink = nfcLink,
                    onVerify = onVerifyNfc,
                    onOpenAlipay = onOpenAlipayNfc,
                    onShowCurrentLink = onShowNfcLink,
                    onSaveLink = onSaveNfcLink
                )
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CreditLine()
                }
            }
        }
    }

    if (showStatusDialog) {
        AlertDialog(
            onDismissRequest = { showStatusDialog = false },
            title = { Text("运行状态") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusRow("定位权限", if (hasLocationPermission) "已授权" else "未授权", hasLocationPermission)
                    StatusRow("模拟位置应用", if (canMockLocation) "已选择本应用" else "未选择", canMockLocation)
                }
            },
            confirmButton = {
                TextButton(onClick = { showStatusDialog = false }) {
                    Text("知道了")
                }
            },
            dismissButton = {
                if (!hasLocationPermission) {
                    TextButton(onClick = onRequestPermissions) {
                        Text("授权定位")
                    }
                } else if (!canMockLocation) {
                    TextButton(onClick = onOpenDeveloperOptions) {
                        Text("开发者选项")
                    }
                }
            }
        )
    }
}

@Composable
private fun HomeOverviewPanel(
    hasLocationPermission: Boolean,
    canMockLocation: Boolean,
    isServiceRunning: Boolean,
    isServicePaused: Boolean,
    isNfcActivated: Boolean,
    routesCount: Int,
    mapProvider: MapProvider,
    onShowStatus: () -> Unit
) {
    val readyForMock = hasLocationPermission && canMockLocation
    val runLabel = when {
        isServicePaused -> "模拟暂停"
        isServiceRunning -> "模拟运行中"
        else -> "待命"
    }
    val runTone = when {
        isServicePaused -> MiuixSkin.Warning
        isServiceRunning -> MiuixSkin.Success
        else -> MiuixSkin.Primary
    }

    MiuixCard(containerColor = MiuixSkin.SurfaceElevated) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "控制台",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "先确认状态，再启动模拟",
                        color = MiuixSkin.TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Surface(
                    color = runTone.copy(alpha = 0.13f),
                    contentColor = runTone,
                    shape = MiuixSkin.PillShape,
                    modifier = Modifier.clickable(onClick = onShowStatus)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(runTone, CircleShape)
                        )
                        Text(runLabel, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OverviewMetric(
                    label = "权限",
                    value = if (readyForMock) "就绪" else "待设置",
                    ok = readyForMock,
                    modifier = Modifier.weight(1f)
                )
                OverviewMetric(
                    label = "授权",
                    value = if (isNfcActivated) "已验证" else "未验证",
                    ok = isNfcActivated,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OverviewMetric(
                    label = "路线",
                    value = "${routesCount} 条",
                    ok = routesCount > 0,
                    modifier = Modifier.weight(1f)
                )
                OverviewMetric(
                    label = "地图",
                    value = mapProvider.label,
                    ok = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun OverviewMetric(
    label: String,
    value: String,
    ok: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MiuixSkin.SurfaceContainer,
        shape = MiuixSkin.FieldShape,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(if (ok) MiuixSkin.Success else MiuixSkin.Warning, CircleShape)
                )
                Text(label, color = MiuixSkin.TextMuted, style = MaterialTheme.typography.labelMedium)
            }
            Text(
                value,
                color = MiuixSkin.Text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun UpdateCheckingDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("正在检查更新") },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 3.dp)
                Text("正在确认 GitHub 最新版本，请稍候。", color = MiuixSkin.TextMuted)
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun UpdateCheckFailedDialog(
    message: String,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("无法检查更新") },
        text = {
            Text(
                text = "需要确认当前版本是否为最新版后才能进入。\n\n$message",
                color = MiuixSkin.TextMuted
            )
        },
        confirmButton = {
            Button(onClick = onRetry) {
                Text("重新检查")
            }
        },
        dismissButton = {
            TextButton(onClick = onExit) {
                Text("退出")
            }
        }
    )
}

@Composable
private fun ForceUpdateDialog(
    latestVersion: String,
    message: String,
    onOpenRelease: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("发现新版本") },
        text = {
            Text(
                text = buildString {
                    append("当前版本不是最新版，请更新到 v")
                    append(latestVersion)
                    append(" 后继续使用。")
                    if (message.isNotBlank()) {
                        append("\n\n")
                        append(message)
                    }
                },
                color = MiuixSkin.TextMuted
            )
        },
        confirmButton = {
            Button(onClick = onOpenRelease) {
                Text("前往 GitHub")
            }
        },
        dismissButton = {
            TextButton(onClick = onRetry) {
                Text("我已更新，重新检查")
            }
        }
    )
}

@Composable
private fun StartupAgreementDialog(
    onAccept: () -> Unit,
    onExit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("软件使用协议与免责声明") },
        text = {
            val scrollState = rememberScrollState()
            Text(
                text = NfcLauncherController.SOFTWARE_AGREEMENT,
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState),
                color = MiuixSkin.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight
            )
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("确认并进入")
            }
        },
        dismissButton = {
            TextButton(onClick = onExit) {
                Text("退出")
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PermissionDots(
    hasLocationPermission: Boolean,
    canMockLocation: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .pointerInput(hasLocationPermission, canMockLocation, onClick, onLongPress) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(hasLocationPermission)
        StatusDot(canMockLocation)
    }
}

@Composable
private fun StatusDot(ok: Boolean) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(if (ok) MiuixSkin.Success else MiuixSkin.Warning, CircleShape)
    )
}

@Composable
private fun CreditLine() {
    val uriHandler = LocalUriHandler.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("vibe coding by ", style = MaterialTheme.typography.labelMedium, color = MiuixSkin.TextMuted)
        Text(
            "Inklazy",
            style = MaterialTheme.typography.labelMedium,
            color = MiuixSkin.Primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { uriHandler.openUri("https://t.me/Inklazy") }
        )
        Text(" & Codex", style = MaterialTheme.typography.labelMedium, color = MiuixSkin.TextMuted)
    }
}

@Composable
private fun Modifier.fluidPressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "fluid_press_scale")
    return this.scale(scale)
}

@Composable
private fun FluidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .fluidPressScale(interactionSource),
        enabled = enabled,
        interactionSource = interactionSource,
        shape = MiuixSkin.ActionShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 5.dp,
            disabledElevation = 0.dp
        ),
        content = content
    )
}

@Composable
private fun FluidOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .fluidPressScale(interactionSource),
        enabled = enabled,
        interactionSource = interactionSource,
        shape = MiuixSkin.ActionShape,
        border = BorderStroke(
            1.dp,
            if (enabled) MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        content = content
    )
}

@Composable
private fun FluidCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MiuixSkin.SurfaceFloating,
    contentColor: Color = MiuixSkin.Text,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        color = if (enabled) containerColor else MiuixSkin.DisabledContainer,
        contentColor = if (enabled) contentColor else MiuixSkin.TextDisabled,
        shape = CircleShape,
        tonalElevation = 4.dp,
        shadowElevation = 1.dp,
        modifier = modifier
            .fluidPressScale(interactionSource)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun MiuixCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = MiuixSkin.CardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        content()
    }
}

@Composable
private fun MiuixTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = textStyle,
        shape = MiuixSkin.FieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MiuixSkin.Primary,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            focusedContainerColor = MiuixSkin.SurfaceContainer,
            unfocusedContainerColor = MiuixSkin.SurfaceContainer,
            disabledContainerColor = MiuixSkin.DisabledContainer,
            focusedLabelColor = MiuixSkin.Primary,
            unfocusedLabelColor = MiuixSkin.TextMuted,
            disabledLabelColor = MiuixSkin.TextDisabled,
            focusedTextColor = MiuixSkin.Text,
            unfocusedTextColor = MiuixSkin.Text,
            disabledTextColor = MiuixSkin.TextDisabled,
            cursorColor = MiuixSkin.Primary
        ),
        modifier = modifier
    )
}

@Composable
private fun PointSimulationCard(
    latInput: String,
    lngInput: String,
    isServiceRunning: Boolean,
    onLatChange: (String) -> Unit,
    onLngChange: (String) -> Unit,
    onOpenPointPicker: () -> Unit,
    onStartPoint: () -> Unit,
    onStop: () -> Unit
) {
    MiuixCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("定点模拟", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiuixTextField(
                    value = latInput,
                    onValueChange = onLatChange,
                    label = "纬度 WGS-84",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                MiuixTextField(
                    value = lngInput,
                    onValueChange = onLngChange,
                    label = "经度 WGS-84",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FluidOutlinedButton(onClick = onOpenPointPicker, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("地图选点")
                }
                FluidButton(onClick = onStartPoint, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("开始定点")
                }
            }
            FluidOutlinedButton(
                onClick = onStop,
                enabled = isServiceRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("停止模拟")
            }
        }
    }
}

@Composable
private fun NfcLauncherCard(
    isActivated: Boolean,
    statusText: String,
    currentLink: String,
    onVerify: () -> Unit,
    onOpenAlipay: () -> Unit,
    onShowCurrentLink: () -> Unit,
    onSaveLink: (String) -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }

    MiuixCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("支付宝 NFC 跳转", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (isActivated) statusText else "设备未验证",
                        color = if (isActivated) MiuixSkin.TextMuted else MiuixSkin.Warning,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                StatusDot(isActivated)
            }

            Text(
                currentLink,
                color = MiuixSkin.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onShowCurrentLink)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                FluidButton(onClick = onOpenAlipay, modifier = Modifier.weight(1f), enabled = isActivated) {
                    Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("模拟跳转")
                }
                FluidOutlinedButton(onClick = { showEditDialog = true }, modifier = Modifier.weight(1f), enabled = isActivated) {
                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("更新链接")
                }
            }

            if (!isActivated) {
                FluidOutlinedButton(onClick = onVerify, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("验证设备")
                }
            }
        }
    }

    if (showEditDialog) {
        var linkText by remember(currentLink) { mutableStateOf(currentLink) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("更新 NFC 链接") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiuixTextField(
                        value = linkText,
                        onValueChange = { linkText = it },
                        label = "支付宝 NFC 链接",
                        minLines = 3,
                        maxLines = 5
                    )
                    Text("留空保存会恢复默认链接；扫描到新的 NFC 链接时仍会弹窗确认保存。", color = MiuixSkin.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showEditDialog = false
                    onSaveLink(linkText)
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun RouteSimulationCard(
    speedText: String,
    routes: List<SavedRoute>,
    isServiceRunning: Boolean,
    isServicePaused: Boolean,
    onSpeedTextChange: (String) -> Unit,
    onOpenRouteEditor: () -> Unit,
    onEditRoute: (SavedRoute) -> Unit,
    onStartRoute: (SavedRoute, String) -> Unit,
    onPause: () -> Unit,
    onResumeMock: () -> Unit,
    onStop: () -> Unit,
    onDeleteRoute: (SavedRoute) -> Unit
) {
    MiuixCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("路线模拟", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                FluidButton(onClick = onOpenRouteEditor) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("新建路线")
                }
            }

            SpeedSelector(speedText = speedText, onSpeedTextChange = onSpeedTextChange)

            AnimatedVisibility(isServiceRunning) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    FluidButton(
                        onClick = if (isServicePaused) onResumeMock else onPause,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (isServicePaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isServicePaused) "继续" else "暂停")
                    }
                    FluidOutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("停止")
                    }
                }
            }

            if (routes.isEmpty()) {
                EmptyRoutesCard(onOpenRouteEditor)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    routes.forEach { route ->
                        RouteCard(
                            route = route,
                            onStart = { onStartRoute(route, speedText) },
                            onEdit = { onEditRoute(route) },
                            onDelete = { onDeleteRoute(route) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedSelector(
    speedText: String,
    onSpeedTextChange: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        val currentSpeed = speedText.toDoubleOrNull()
        SpeedPreset.entries.forEach { preset ->
            SpeedPresetButton(
                label = preset.label,
                speedMps = preset.speedMps,
                selected = currentSpeed?.let { kotlin.math.abs(it - preset.speedMps) < 0.001 } == true,
                onClick = { onSpeedTextChange(formatNumber(preset.speedMps)) },
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
            )
        }
        MiuixTextField(
            value = speedText,
            onValueChange = onSpeedTextChange,
            label = "m/s",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 17.sp),
            modifier = Modifier
                .weight(0.82f)
                .height(64.dp)
        )
    }
}

@Composable
private fun SpeedPresetButton(
    label: String,
    speedMps: Double,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        color = if (selected) MiuixSkin.PrimarySoft else MiuixSkin.SurfaceContainer,
        contentColor = if (selected) MiuixSkin.Primary else MiuixSkin.Text,
        shape = MiuixSkin.FieldShape,
        border = BorderStroke(1.dp, if (selected) MiuixSkin.Primary.copy(alpha = 0.35f) else Color.Transparent),
        modifier = modifier
            .fluidPressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${formatNumber(speedMps)}m/s",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(ok)
        Spacer(Modifier.width(8.dp))
        Text(label, modifier = Modifier.weight(1f), color = MiuixSkin.TextMuted)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyRoutesCard(onOpenEditor: () -> Unit) {
    MiuixCard(containerColor = MiuixSkin.SurfaceContainer) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = MiuixSkin.Primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text("还没有保存路线")
            Spacer(Modifier.height(12.dp))
            FluidButton(onClick = onOpenEditor) {
                Text("去地图选点")
            }
        }
    }
}

@Composable
private fun RouteCard(
    route: SavedRoute,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val distance = RouteMath.totalDistanceMeters(route.points, closeLoop = route.closeLoop)
    val loopText = if (route.closeLoop) "首尾相连 · ${route.loopCount} 次" else "往返"

    MiuixCard(containerColor = MiuixSkin.SurfaceContainer) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(route.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${route.points.size} 个点 · ${formatDistance(distance)} · $loopText", color = MiuixSkin.TextMuted)
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "删除", tint = MiuixSkin.Danger)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FluidButton(onClick = onStart, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("模拟")
                }
                FluidOutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Text("编辑")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除路线") },
            text = { Text("确认删除“${route.name}”？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text("删除", color = MiuixSkin.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun MapPointPickerScreen(
    mapProvider: MapProvider,
    startPoint: RoutePoint?,
    onBack: () -> Unit,
    onLocateMe: () -> RoutePoint?,
    onPointPicked: (RoutePoint) -> Unit
) {
    MapSelectionScreen(
        title = "定点选点",
        mapProvider = mapProvider,
        initialPoint = startPoint,
        points = emptyList(),
        pointsVersion = 0,
        closeLoopPreview = false,
        drawMode = false,
        onBack = onBack,
        onLocateMe = onLocateMe,
        onUndo = null,
        onDrawPoint = null,
        overlayContent = {},
        previewPoints = emptyList(),
        bottomContent = { controller ->
            FluidButton(
                onClick = { controller.cameraTarget()?.let(onPointPicked) },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("使用当前位置")
            }
        }
    )
}

@Composable
private fun RouteEditorScreen(
    mapProvider: MapProvider,
    initialRoute: SavedRoute?,
    closeLoop: Boolean,
    loopCountText: String,
    onCloseLoopChange: (Boolean) -> Unit,
    onLoopCountTextChange: (String) -> Unit,
    onBack: () -> Unit,
    onLocateMe: () -> RoutePoint?,
    onSaveRoute: (String, List<RoutePoint>) -> Unit
) {
    val points = remember(initialRoute?.id) {
        mutableStateListOf<RoutePoint>().apply {
            addAll(initialRoute?.points.orEmpty())
        }
    }
    var pointsVersion by remember(initialRoute?.id) { mutableStateOf(0) }
    var name by remember(initialRoute?.id) { mutableStateOf(initialRoute?.name ?: "") }
    var showSaveDialog by remember { mutableStateOf(false) }
    var drawMode by remember { mutableStateOf(false) }
    var templateEnabled by remember(initialRoute?.id) { mutableStateOf(false) }
    var templateCenter by remember(initialRoute?.id) { mutableStateOf<RoutePoint?>(null) }
    var templateLength by remember(initialRoute?.id) { mutableStateOf(160.0) }
    var templateWidth by remember(initialRoute?.id) { mutableStateOf(75.0) }
    var templateRotation by remember(initialRoute?.id) { mutableStateOf(0.0) }

    MapSelectionScreen(
        title = "绘制路线",
        mapProvider = mapProvider,
        initialPoint = initialRoute?.points?.firstOrNull(),
        points = points,
        pointsVersion = pointsVersion,
        closeLoopPreview = closeLoop,
        drawMode = drawMode,
        onBack = onBack,
        onLocateMe = onLocateMe,
        onUndo = {
            val removeCount = points.size.coerceAtMost(8)
            repeat(removeCount) { points.removeLast() }
            pointsVersion++
        },
        onDrawPoint = { point ->
            val last = points.lastOrNull()
            if (last == null || RouteMath.distanceMeters(last, point) >= 4.0) {
                points.add(point)
                pointsVersion++
            }
        },
        overlayContent = { controller ->
            val center = templateCenter
            if (templateEnabled && center != null) {
                TrackTemplateControls(
                    onRotateLeft = { templateRotation += 5.0 },
                    onRotateRight = { templateRotation -= 5.0 },
                    onScaleUp = {
                        templateLength = (templateLength * 1.08).coerceAtMost(400.0)
                        templateWidth = (templateWidth * 1.08).coerceAtMost(160.0)
                    },
                    onScaleDown = {
                        templateLength = (templateLength * 0.92).coerceAtLeast(40.0)
                        templateWidth = (templateWidth * 0.92).coerceAtLeast(20.0)
                    },
                    onMoveToCenter = { templateCenter = controller.cameraTarget() ?: templateCenter },
                    onClose = { templateEnabled = false }
                )
            }
        },
        bottomContent = { controller ->
            val templatePoints = templateCenter?.let { center ->
                buildTrackOval(center, templateLength, templateWidth, templateRotation)
            }.orEmpty()
            RouteEditorCompactBar(
                pointCount = points.size,
                distanceText = formatDistance(RouteMath.totalDistanceMeters(points, closeLoop)),
                drawMode = drawMode,
                closeLoop = closeLoop,
                loopCountText = loopCountText,
                templateEnabled = templateEnabled,
                canSave = points.size >= 2,
                onToggleDraw = { drawMode = !drawMode },
                onToggleCloseLoop = { onCloseLoopChange(!closeLoop) },
                onLoopCountTextChange = onLoopCountTextChange,
                onToggleTemplate = {
                    val center = controller.cameraTarget() ?: initialRoute?.points?.firstOrNull() ?: RoutePoint(39.9042, 116.4074)
                    templateCenter = center
                    templateEnabled = !templateEnabled
                },
                onApplyTemplate = {
                    if (templatePoints.size >= 2) {
                        points.clear()
                        points.addAll(templatePoints)
                        onCloseLoopChange(true)
                        templateEnabled = false
                        drawMode = false
                        pointsVersion++
                    }
                },
                onUndo = {
                    val removeCount = points.size.coerceAtMost(8)
                    repeat(removeCount) { points.removeLast() }
                    pointsVersion++
                },
                onClear = {
                    points.clear()
                    pointsVersion++
                },
                onSave = { showSaveDialog = true }
            )
        },
        previewPoints = if (templateEnabled) {
            templateCenter?.let { buildTrackOval(it, templateLength, templateWidth, templateRotation) }.orEmpty()
        } else {
            emptyList()
        }
    )

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存路线") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiuixTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "路线名称",
                        singleLine = true
                    )
                    Text(if (closeLoop) "模式：首尾相连循环 ${loopCountText.toIntOrNull() ?: 1} 次" else "模式：非闭环往返", color = MiuixSkin.TextMuted)
                    Text("同名路线会被覆盖。", color = MiuixSkin.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSaveDialog = false
                    onSaveRoute(name, points.toList())
                }) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun BoxScope.TrackTemplateControls(
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onScaleUp: () -> Unit,
    onScaleDown: () -> Unit,
    onMoveToCenter: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = MiuixSkin.SurfaceFloating,
        shape = MiuixSkin.FloatingShape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .padding(top = 74.dp, end = 12.dp)
            .align(Alignment.TopEnd)
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FluidCircleButton(onClick = onRotateLeft, modifier = Modifier.size(38.dp)) {
                Icon(Icons.AutoMirrored.Rounded.RotateLeft, contentDescription = "向左旋转", tint = MiuixSkin.Text, modifier = Modifier.size(18.dp))
            }
            FluidCircleButton(onClick = onRotateRight, modifier = Modifier.size(38.dp)) {
                Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = "向右旋转", tint = MiuixSkin.Text, modifier = Modifier.size(18.dp))
            }
            FluidCircleButton(onClick = onScaleDown, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.Remove, contentDescription = "缩小", tint = MiuixSkin.Text, modifier = Modifier.size(18.dp))
            }
            FluidCircleButton(onClick = onScaleUp, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = "放大", tint = MiuixSkin.Text, modifier = Modifier.size(18.dp))
            }
            FluidCircleButton(onClick = onMoveToCenter, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.MyLocation, contentDescription = "移动到准星", tint = MiuixSkin.Primary, modifier = Modifier.size(18.dp))
            }
            FluidCircleButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "关闭模板", tint = MiuixSkin.Danger, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun RouteEditorCompactBar(
    pointCount: Int,
    distanceText: String,
    drawMode: Boolean,
    closeLoop: Boolean,
    loopCountText: String,
    templateEnabled: Boolean,
    canSave: Boolean,
    onToggleDraw: () -> Unit,
    onToggleCloseLoop: () -> Unit,
    onLoopCountTextChange: (String) -> Unit,
    onToggleTemplate: () -> Unit,
    onApplyTemplate: () -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit
) {
    Surface(
        color = MiuixSkin.SurfaceFloating,
        shape = MiuixSkin.CardShape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$pointCount 点 · $distanceText", fontWeight = FontWeight.SemiBold)
                    Text(if (drawMode) "正在绘制：手指拖过地图生成曲线" else "关闭绘制后可正常拖动地图", color = MiuixSkin.TextMuted, style = MaterialTheme.typography.labelSmall)
                }
                MiuixTextField(
                    value = loopCountText,
                    onValueChange = onLoopCountTextChange,
                    label = "圈数",
                    enabled = closeLoop,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(82.dp).height(56.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SpeedyToolButton(
                    text = if (drawMode) "绘制中" else "画线",
                    selected = drawMode,
                    onClick = onToggleDraw,
                    modifier = Modifier.weight(1f)
                )
                SpeedyToolButton(
                    text = if (closeLoop) "闭环" else "往返",
                    selected = closeLoop,
                    onClick = onToggleCloseLoop,
                    modifier = Modifier.weight(1f)
                )
                SpeedyToolButton(
                    text = if (templateEnabled) "贴合中" else "跑道",
                    selected = templateEnabled,
                    onClick = onToggleTemplate,
                    modifier = Modifier.weight(1f)
                )
                FluidOutlinedButton(
                    onClick = onApplyTemplate,
                    enabled = templateEnabled,
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Text("添加")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FluidOutlinedButton(onClick = onUndo, enabled = pointCount > 0, modifier = Modifier.weight(1f).height(44.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("撤销")
                }
                FluidOutlinedButton(onClick = onClear, enabled = pointCount > 0, modifier = Modifier.weight(1f).height(44.dp)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("清空")
                }
                FluidButton(onClick = onSave, enabled = canSave, modifier = Modifier.weight(1.25f).height(44.dp)) {
                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("保存")
                }
            }
        }
    }
}

@Composable
private fun SpeedyToolButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        color = if (selected) MiuixSkin.PrimarySoft else MiuixSkin.SurfaceContainer,
        contentColor = if (selected) MiuixSkin.Primary else MiuixSkin.Text,
        shape = MiuixSkin.FieldShape,
        border = BorderStroke(1.dp, if (selected) MiuixSkin.Primary.copy(alpha = 0.35f) else Color.Transparent),
        modifier = modifier
            .height(44.dp)
            .fluidPressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun MapSelectionScreen(
    title: String,
    mapProvider: MapProvider,
    initialPoint: RoutePoint?,
    points: List<RoutePoint>,
    pointsVersion: Int,
    closeLoopPreview: Boolean,
    drawMode: Boolean,
    onBack: () -> Unit,
    onLocateMe: () -> RoutePoint?,
    onUndo: (() -> Unit)?,
    onDrawPoint: ((RoutePoint) -> Unit)?,
    overlayContent: @Composable BoxScope.(MapController) -> Unit,
    previewPoints: List<RoutePoint>,
    bottomContent: @Composable (MapController) -> Unit
) {
    var mapController by remember { mutableStateOf<MapController?>(null) }
    var satelliteEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(mapController, pointsVersion, closeLoopPreview, previewPoints) {
        val controller = mapController ?: return@LaunchedEffect
        renderRoute(controller, points, closeLoopPreview, previewPoints)
    }

    LaunchedEffect(mapController, satelliteEnabled) {
        mapController?.setSatelliteEnabled(satelliteEnabled)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CampusMapView(
            provider = mapProvider,
            modifier = Modifier.fillMaxSize()
        ) { controller ->
            mapController = controller
            controller.disableUiControls()
            controller.setSatelliteEnabled(satelliteEnabled)
            controller.moveCamera(initialPoint ?: RoutePoint(39.9042, 116.4074), 16f)
            renderRoute(controller, points, closeLoopPreview, previewPoints)
        }

        if (drawMode && onDrawPoint != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(mapController, drawMode) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                mapController
                                    ?.screenPointToRoutePoint(offset.x.roundToInt(), offset.y.roundToInt())
                                    ?.let(onDrawPoint)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                mapController
                                    ?.screenPointToRoutePoint(change.position.x.roundToInt(), change.position.y.roundToInt())
                                    ?.let(onDrawPoint)
                            }
                        )
                    }
            )
        } else if (onDrawPoint == null) {
            Icon(
                Icons.Rounded.AddLocationAlt,
                contentDescription = null,
                tint = MiuixSkin.Primary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
                    .padding(bottom = 14.dp)
            )
        } else {
            MapCenterCrosshair()
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FluidCircleButton(
                onClick = onBack,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Surface(
                color = MiuixSkin.SurfaceFloating,
                shape = MiuixSkin.FloatingShape,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (points.isEmpty()) title else "$title · ${points.size} 点 · ${formatDistance(RouteMath.totalDistanceMeters(points, closeLoopPreview))}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimatedVisibility(onUndo != null && points.isNotEmpty()) {
                    FluidCircleButton(
                        onClick = { onUndo?.invoke() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "撤销")
                    }
                }
                FluidCircleButton(
                    onClick = { satelliteEnabled = !satelliteEnabled },
                    containerColor = if (satelliteEnabled) MiuixSkin.Primary else MiuixSkin.SurfaceFloating,
                    contentColor = if (satelliteEnabled) Color.White else MiuixSkin.Primary,
                    modifier = Modifier.size(44.dp)
                ) {
                    Text(if (satelliteEnabled) "2D" else "卫星", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                FluidCircleButton(
                    onClick = {
                        val current = onLocateMe()
                        if (current != null) {
                            mapController?.animateCamera(current, 17f)
                        }
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Rounded.MyLocation, contentDescription = "定位到当前位置", tint = MiuixSkin.Primary)
                }
            }
        }

        mapController?.let { controller ->
            overlayContent(controller)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, MiuixSkin.Background.copy(alpha = 0.96f))))
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val controller = mapController
            if (controller != null) {
                bottomContent(controller)
            }
        }
    }
}

@Composable
private fun BoxScope.MapCenterCrosshair() {
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .size(34.dp)
            .padding(bottom = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .width(28.dp)
                .height(3.dp)
                .shadow(2.dp, RoundedCornerShape(2.dp))
                .background(Color.White, RoundedCornerShape(2.dp))
        )
        Box(
            Modifier
                .width(3.dp)
                .height(28.dp)
                .shadow(2.dp, RoundedCornerShape(2.dp))
                .background(Color.White, RoundedCornerShape(2.dp))
        )
    }
}

private fun renderRoute(
    controller: MapController,
    points: List<RoutePoint>,
    closeLoopPreview: Boolean = false,
    previewPoints: List<RoutePoint> = emptyList()
) {
    controller.clear()
    if (points.size >= 2) {
        val visiblePoints = if (closeLoopPreview) points + points.first() else points
        controller.addPolyline(visiblePoints, android.graphics.Color.parseColor(MiuixSkin.PrimaryHex), 8f)
    }
    points.forEachIndexed { index, point ->
        val kind = when (index) {
            0 -> MarkerKind.START
            points.lastIndex -> MarkerKind.END
            else -> MarkerKind.NORMAL
        }
        controller.addMarker(point, "${index + 1}", kind)
    }
    if (previewPoints.size >= 2) {
        controller.addPolyline(previewPoints + previewPoints.first(), android.graphics.Color.parseColor(MiuixSkin.SuccessHex), 12f)
    }
}

private fun formatDistance(distance: Double): String {
    return if (distance >= 1000) {
        String.format(Locale.US, "%.2f km", distance / 1000.0)
    } else {
        String.format(Locale.US, "%.0f m", distance)
    }
}

private fun formatNumber(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }
}

private fun buildTrackOval(
    center: RoutePoint,
    lengthMeters: Double,
    widthMeters: Double,
    rotationDegrees: Double,
    samples: Int = 80
): List<RoutePoint> {
    val safeWidth = widthMeters.coerceIn(20.0, 160.0)
    val safeLength = lengthMeters.coerceAtLeast(safeWidth + 10.0).coerceAtMost(400.0)
    val radius = safeWidth / 2.0
    val halfStraight = (safeLength - safeWidth) / 2.0
    val rotation = Math.toRadians(rotationDegrees)

    return List(samples) { index ->
        val t = 2.0 * PI * index / samples
        val baseX: Double
        val baseY: Double
        if (cos(t) >= 0.0) {
            baseX = halfStraight + radius * cos(t)
            baseY = radius * sin(t)
        } else {
            baseX = -halfStraight + radius * cos(t)
            baseY = radius * sin(t)
        }
        val eastMeters = baseX * cos(rotation) - baseY * sin(rotation)
        val northMeters = baseX * sin(rotation) + baseY * cos(rotation)
        offsetMeters(center, eastMeters, northMeters)
    }
}

private fun offsetMeters(origin: RoutePoint, eastMeters: Double, northMeters: Double): RoutePoint {
    val earthRadiusMeters = 6378137.0
    val dLat = northMeters / earthRadiusMeters
    val dLng = eastMeters / (earthRadiusMeters * cos(Math.toRadians(origin.latWgs84)).coerceAtLeast(0.000001))
    return RoutePoint(
        latWgs84 = origin.latWgs84 + Math.toDegrees(dLat),
        lngWgs84 = origin.lngWgs84 + Math.toDegrees(dLng)
    )
}
