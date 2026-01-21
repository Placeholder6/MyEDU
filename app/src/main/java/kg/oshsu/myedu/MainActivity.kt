package kg.oshsu.myedu

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.ExperimentalTransitionApi
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kg.oshsu.myedu.ui.components.ExpressiveShapesBackground
import kg.oshsu.myedu.ui.screens.ClassDetailsSheet
import kg.oshsu.myedu.ui.screens.DictionaryScreen
import kg.oshsu.myedu.ui.screens.EditProfileScreen
import kg.oshsu.myedu.ui.screens.GradesScreen
import kg.oshsu.myedu.ui.screens.HomeScreen
import kg.oshsu.myedu.ui.screens.LoginScreen
import kg.oshsu.myedu.ui.screens.OnboardingScreen
import kg.oshsu.myedu.ui.screens.PersonalInfoScreen
import kg.oshsu.myedu.ui.screens.ProfileScreen
import kg.oshsu.myedu.ui.screens.ReferenceView
import kg.oshsu.myedu.ui.screens.ScheduleScreen
import kg.oshsu.myedu.ui.screens.SettingsScreen
import kg.oshsu.myedu.ui.screens.TranscriptView
import kg.oshsu.myedu.ui.screens.WebDocumentScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.min

class MainActivity : ComponentActivity() {

    private val vm by viewModels<MainViewModel>()

    // --- INSTALL RECEIVER ---
    private val installReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)

                if (id == vm.downloadId) {
                    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val query = DownloadManager.Query().setFilterById(id)
                    var cursor: Cursor? = null

                    try {
                        cursor = downloadManager.query(query)
                        if (cursor.moveToFirst()) {
                            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val status = cursor.getInt(statusIndex)

                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                                val uriString = cursor.getString(uriIndex)

                                if (uriString != null) {
                                    val localFile = File(Uri.parse(uriString).path!!)
                                    val contentUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        localFile
                                    )
                                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(
                                            contentUri,
                                            "application/vnd.android.package-archive"
                                        )
                                        flags =
                                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                                    }
                                    context.startActivity(installIntent)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        cursor?.close()
                    }
                }
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("myedu_offline_cache", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_language", "en") ?: "en"
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        registerReceiver(
            installReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_EXPORTED
        )
        setupBackgroundWork()
        enableEdgeToEdge()
        vm.initSession(applicationContext)
        splashScreen.setKeepOnScreenCondition { vm.appState == "STARTUP" }

        setContent {
            LaunchedEffect(vm.fullSchedule, vm.timeMap, vm.notificationsEnabled) {
                if (vm.fullSchedule.isNotEmpty() && vm.timeMap.isNotEmpty() && vm.notificationsEnabled) {
                    ScheduleAlarmManager(applicationContext).scheduleNotifications(
                        vm.fullSchedule,
                        vm.timeMap
                    )
                }
            }
            MyEduTheme(themePreference = vm.appTheme) { AppContent(vm) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(installReceiver)
    }

    private fun setupBackgroundWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val syncRequest = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(4, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "MyEduGradeSync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}

// ... (MyEduTheme and rememberAnimatedColorScheme unchanged)
@Composable
fun MyEduTheme(themePreference: String, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themePreference) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }

    val context = LocalContext.current
    val targetScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (useDarkTheme) darkColorScheme() else lightColorScheme()
    }

    val animatedScheme = rememberAnimatedColorScheme(targetScheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            androidx.core.view.WindowCompat.getInsetsController(
                window,
                view
            ).isAppearanceLightStatusBars = !useDarkTheme
        }
    }
    MaterialTheme(colorScheme = animatedScheme, content = content)
}

@Composable
fun rememberAnimatedColorScheme(targetColorScheme: ColorScheme): ColorScheme {
    val animationSpec = tween<Color>(durationMillis = 600)
    val primary by animateColorAsState(targetColorScheme.primary, animationSpec, label = "primary")
    val onPrimary by animateColorAsState(
        targetColorScheme.onPrimary,
        animationSpec,
        label = "onPrimary"
    )
    val primaryContainer by animateColorAsState(
        targetColorScheme.primaryContainer,
        animationSpec,
        label = "primaryContainer"
    )
    val onPrimaryContainer by animateColorAsState(
        targetColorScheme.onPrimaryContainer,
        animationSpec,
        label = "onPrimaryContainer"
    )
    val secondary by animateColorAsState(
        targetColorScheme.secondary,
        animationSpec,
        label = "secondary"
    )
    val onSecondary by animateColorAsState(
        targetColorScheme.onSecondary,
        animationSpec,
        label = "onSecondary"
    )
    val secondaryContainer by animateColorAsState(
        targetColorScheme.secondaryContainer,
        animationSpec,
        label = "secondaryContainer"
    )
    val onSecondaryContainer by animateColorAsState(
        targetColorScheme.onSecondaryContainer,
        animationSpec,
        label = "onSecondaryContainer"
    )
    val tertiary by animateColorAsState(targetColorScheme.tertiary, animationSpec, label = "tertiary")
    val onTertiary by animateColorAsState(
        targetColorScheme.onTertiary,
        animationSpec,
        label = "onTertiary"
    )
    val tertiaryContainer by animateColorAsState(
        targetColorScheme.tertiaryContainer,
        animationSpec,
        label = "tertiaryContainer"
    )
    val onTertiaryContainer by animateColorAsState(
        targetColorScheme.onTertiaryContainer,
        animationSpec,
        label = "onTertiaryContainer"
    )
    val background by animateColorAsState(
        targetColorScheme.background,
        animationSpec,
        label = "background"
    )
    val onBackground by animateColorAsState(
        targetColorScheme.onBackground,
        animationSpec,
        label = "onBackground"
    )
    val surface by animateColorAsState(targetColorScheme.surface, animationSpec, label = "surface")
    val onSurface by animateColorAsState(
        targetColorScheme.onSurface,
        animationSpec,
        label = "onSurface"
    )
    val surfaceVariant by animateColorAsState(
        targetColorScheme.surfaceVariant,
        animationSpec,
        label = "surfaceVariant"
    )
    val onSurfaceVariant by animateColorAsState(
        targetColorScheme.onSurfaceVariant,
        animationSpec,
        label = "onSurfaceVariant"
    )
    val outline by animateColorAsState(targetColorScheme.outline, animationSpec, label = "outline")
    val outlineVariant by animateColorAsState(
        targetColorScheme.outlineVariant,
        animationSpec,
        label = "outlineVariant"
    )
    val error by animateColorAsState(targetColorScheme.error, animationSpec, label = "error")
    val onError by animateColorAsState(targetColorScheme.onError, animationSpec, label = "onError")
    val errorContainer by animateColorAsState(
        targetColorScheme.errorContainer,
        animationSpec,
        label = "errorContainer"
    )
    val onErrorContainer by animateColorAsState(
        targetColorScheme.onErrorContainer,
        animationSpec,
        label = "onErrorContainer"
    )
    val surfaceContainerLowest by animateColorAsState(
        targetColorScheme.surfaceContainerLowest,
        animationSpec,
        label = "sCL"
    )
    val surfaceContainerLow by animateColorAsState(
        targetColorScheme.surfaceContainerLow,
        animationSpec,
        label = "sCLow"
    )
    val surfaceContainer by animateColorAsState(
        targetColorScheme.surfaceContainer,
        animationSpec,
        label = "sC"
    )
    val surfaceContainerHigh by animateColorAsState(
        targetColorScheme.surfaceContainerHigh,
        animationSpec,
        label = "sCH"
    )
    val surfaceContainerHighest by animateColorAsState(
        targetColorScheme.surfaceContainerHighest,
        animationSpec,
        label = "sCHH"
    )

    return targetColorScheme.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        outlineVariant = outlineVariant,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppContent(vm: MainViewModel) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (vm.appState == "LOGIN" || vm.appState == "ONBOARDING") {
            ExpressiveShapesBackground(maxWidth, maxHeight)
        }

        SharedTransitionLayout {
            AnimatedContent(
                targetState = vm.appState,
                label = "Root",
                transitionSpec = {
                    fadeIn(animationSpec = tween(600)) togetherWith fadeOut(
                        animationSpec = tween(600)
                    )
                }
            ) { state ->
                when (state) {
                    "LOGIN" -> LoginScreen(vm, this@SharedTransitionLayout, this@AnimatedContent)
                    "ONBOARDING" -> OnboardingScreen(
                        vm,
                        this@SharedTransitionLayout,
                        this@AnimatedContent
                    )
                    // Updated call to pass the shared scopes
                    "APP" -> MainAppStructure(vm, this@SharedTransitionLayout, this@AnimatedContent)
                    else -> Box(Modifier.fillMaxSize())
                }
            }
        }

        // --- UPDATE DIALOG ---
        val context = LocalContext.current
        if (vm.updateAvailableRelease != null) {
            val release = vm.updateAvailableRelease!!
            AlertDialog(
                onDismissRequest = { vm.updateAvailableRelease = null },
                title = { Text(stringResource(R.string.update_available_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.update_available_msg,
                            release.tagName,
                            release.body
                        )
                    )
                },
                confirmButton = {
                    Button(onClick = { vm.downloadUpdate(context) }) {
                        Text(
                            stringResource(R.string.update_btn_download)
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { vm.updateAvailableRelease = null }) {
                        Text(
                            stringResource(R.string.update_btn_later)
                        )
                    }
                }
            )
        }
    }
}

data class NavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val index: Int
)

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class,
    ExperimentalTransitionApi::class
)
@Composable
fun MainAppStructure(
    vm: MainViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope
) {
    NotificationPermissionRequest()

    // 1. Navigation State (Main Content)
    val transitionState = remember { SeekableTransitionState(vm.currentScreen) }
    
    // 2. Overlay States (Settings & Dictionary)
    val settingsState = remember { SeekableTransitionState(vm.showSettingsScreen) }
    val dictState = remember { SeekableTransitionState(vm.showDictionaryScreen) }

    // 3. Sync Logic (ViewModel -> TransitionState)
    LaunchedEffect(vm.currentScreen) {
        if (transitionState.targetState != vm.currentScreen) {
            transitionState.animateTo(vm.currentScreen)
        }
    }
    LaunchedEffect(vm.showSettingsScreen) {
        if (settingsState.targetState != vm.showSettingsScreen) {
            settingsState.animateTo(vm.showSettingsScreen)
        }
    }
    LaunchedEffect(vm.showDictionaryScreen) {
        if (dictState.targetState != vm.showDictionaryScreen) {
            dictState.animateTo(vm.showDictionaryScreen)
        }
    }
    
    // Track swipe edge for all predictive gestures
    var backSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_RIGHT) }

    // 4. PREDICTIVE BACK HANDLERS (Priority Ordered)
    
    // Priority 1: Bottom Sheet (Class Details) - Handled internally in ModalBottomSheet
    
    // Priority 2: Dictionary Overlay
    if (vm.selectedClass == null && vm.showDictionaryScreen) {
        PredictiveBackHandler { progress ->
            try {
                // Seek to Closed (false)
                dictState.seekTo(0f, targetState = false)
                progress.collect { backEvent ->
                    if (backEvent.progress < 0.1f) backSwipeEdge = backEvent.swipeEdge
                    dictState.seekTo(backEvent.progress, targetState = false)
                }
                dictState.animateTo(false)
                vm.showDictionaryScreen = false
            } catch (e: CancellationException) {
                withContext(NonCancellable) { dictState.animateTo(dictState.currentState) }
            }
        }
    }
    // Priority 3: Settings Overlay
    else if (vm.selectedClass == null && vm.showSettingsScreen) {
        PredictiveBackHandler { progress ->
            try {
                // Seek to Closed (false)
                settingsState.seekTo(0f, targetState = false)
                progress.collect { backEvent ->
                    if (backEvent.progress < 0.1f) backSwipeEdge = backEvent.swipeEdge
                    settingsState.seekTo(backEvent.progress, targetState = false)
                }
                settingsState.animateTo(false)
                vm.showSettingsScreen = false
            } catch (e: CancellationException) {
                withContext(NonCancellable) { settingsState.animateTo(settingsState.currentState) }
            }
        }
    }
    // Priority 4: Documents (Transcript/Reference/EditProfile/PersonalInfo -> Profile)
    else if (vm.selectedClass == null && (vm.currentScreen == AppScreen.REFERENCE || vm.currentScreen == AppScreen.TRANSCRIPT || vm.currentScreen == AppScreen.EDIT_PROFILE || vm.currentScreen == AppScreen.PERSONAL_INFO)) {
        PredictiveBackHandler { progress ->
            try {
                progress.collect { backEvent ->
                    if (backEvent.progress < 0.1f) backSwipeEdge = backEvent.swipeEdge
                    transitionState.seekTo(backEvent.progress, targetState = AppScreen.PROFILE)
                }
                transitionState.animateTo(AppScreen.PROFILE)
                vm.currentScreen = AppScreen.PROFILE
            } catch (e: CancellationException) {
                withContext(NonCancellable) { transitionState.animateTo(transitionState.currentState) }
            }
        }
    }
    // Priority 5: Web View Back -> Return Screen
    else if (vm.selectedClass == null && vm.currentScreen == AppScreen.WEB_VIEW) {
        BackHandler { vm.currentScreen = vm.returnScreen }
    }
    // Priority 6: Fallback Standard Back
    else if (vm.selectedClass == null && vm.currentScreen != AppScreen.HOME) {
        BackHandler { vm.currentScreen = AppScreen.HOME }
    }

    val navItems = listOf(
        NavItem(stringResource(R.string.nav_home), Icons.Filled.Home, Icons.Outlined.Home, 0),
        NavItem(stringResource(R.string.nav_schedule), Icons.Filled.DateRange, Icons.Outlined.DateRange, 1),
        NavItem(stringResource(R.string.nav_grades), Icons.Filled.Description, Icons.Outlined.Description, 2),
        NavItem(stringResource(R.string.nav_profile), Icons.Filled.Person, Icons.Outlined.Person, 3)
    )

    // REMOVED: SharedTransitionLayout {} wrapper. We now use the scope passed from AppContent.
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        val transition = rememberTransition(transitionState)
        val settingsTransition = rememberTransition(settingsState)
        val dictTransition = rememberTransition(dictState)

        // --- Background Scaling Logic ---
        val settingScale by settingsTransition.animateFloat(label = "settingsScale") { if (it) 0.95f else 1f }
        val dictScale by dictTransition.animateFloat(label = "dictScale") { if (it) 0.95f else 1f }
        val mainContentScale = min(settingScale, dictScale)

        // --- MAIN APP CONTENT ---
        Box(Modifier.fillMaxSize().graphicsLayer {
            scaleX = mainContentScale
            scaleY = mainContentScale
        }) {
            transition.AnimatedContent(
                transitionSpec = {
                    // DEFINE LIST OF SCREENS THAT OPEN FROM PROFILE
                    val subScreens = listOf(AppScreen.TRANSCRIPT, AppScreen.REFERENCE, AppScreen.EDIT_PROFILE, AppScreen.PERSONAL_INFO)
                    
                    val isBack = (initialState in subScreens) && targetState == AppScreen.PROFILE
                    val isOpen = (targetState in subScreens) && initialState == AppScreen.PROFILE

                    if (isBack) {
                        val slideDirection = if (backSwipeEdge == BackEventCompat.EDGE_RIGHT) -1 else 1
                        val exit = slideOutHorizontally(animationSpec = tween(400)) { width -> (width * 0.05f * slideDirection).toInt() } + 
                                scaleOut(targetScale = 0.8f, animationSpec = tween(400)) + 
                                fadeOut(animationSpec = tween(400))
                        val enter = fadeIn(animationSpec = tween(400)) + 
                                    scaleIn(initialScale = 0.9f, animationSpec = tween(400))
                        (enter togetherWith exit).apply { targetContentZIndex = -1f }
                    } else if (isOpen) {
                        val enter = slideInHorizontally(animationSpec = tween(400)) { it } + 
                                    scaleIn(initialScale = 0.85f, animationSpec = tween(400)) + 
                                    fadeIn(animationSpec = tween(400))
                        val exit = fadeOut(animationSpec = tween(400)) + 
                                scaleOut(targetScale = 0.95f, animationSpec = tween(400))
                        enter togetherWith exit
                    } else {
                        fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                    }
                }
            ) { targetScreen ->
                if (targetScreen in listOf(AppScreen.HOME, AppScreen.SCHEDULE, AppScreen.GRADES, AppScreen.PROFILE)) {
                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                navItems.forEach { item ->
                                    val isSelected = vm.currentTab == item.index
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = { vm.currentTab = item.index },
                                        icon = { Icon(if (isSelected) item.selectedIcon else item.unselectedIcon, item.label) },
                                        label = { Text(item.label) }
                                    )
                                }
                            }
                        }
                    ) { padding ->
                        // FIX: Only apply bottom padding (for Nav Bar), let children handle Top Padding (Status Bar)
                        Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
                            when (targetScreen) {
                                AppScreen.HOME -> HomeScreen(vm)
                                AppScreen.SCHEDULE -> ScheduleScreen(vm)
                                AppScreen.GRADES -> GradesScreen(vm)
                                // Passes the outer sharedTransitionScope
                                AppScreen.PROFILE -> ProfileScreen(vm, sharedTransitionScope, this@AnimatedContent)
                                else -> {}
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize()) {
                        when (targetScreen) {
                            // Passes the outer sharedTransitionScope
                            AppScreen.TRANSCRIPT -> TranscriptView(vm, { vm.currentScreen = AppScreen.PROFILE }, sharedTransitionScope, this@AnimatedContent)
                            // Passes the outer sharedTransitionScope
                            AppScreen.REFERENCE -> ReferenceView(vm, { vm.currentScreen = AppScreen.PROFILE }, sharedTransitionScope, this@AnimatedContent)
                            // Passes the outer sharedTransitionScope
                            AppScreen.EDIT_PROFILE -> EditProfileScreen(vm, { vm.currentScreen = AppScreen.PROFILE }, sharedTransitionScope, this@AnimatedContent)
                            
                            // ADDED PERSONAL INFO SCREEN
                            AppScreen.PERSONAL_INFO -> PersonalInfoScreen(vm, { vm.currentScreen = AppScreen.PROFILE }, sharedTransitionScope, this@AnimatedContent)
                            
                            // ADDED WEB VIEW
                            AppScreen.WEB_VIEW -> WebDocumentScreen(
                                url = vm.webUrl, 
                                title = vm.webTitle,
                                fileName = vm.webFileName,
                                authToken = vm.prefsManager?.getToken(),
                                themeMode = vm.appTheme,
                                onClose = { vm.currentScreen = vm.returnScreen }
                            )

                            else -> {}
                        }
                    }
                }
            }
        }

        // --- SETTINGS OVERLAY ---
        settingsTransition.AnimatedVisibility(
            visible = { it },
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(animationSpec = tween(400)) { width -> 
                val direction = if (backSwipeEdge == BackEventCompat.EDGE_RIGHT) -1 else 1
                (width * 0.05f * direction).toInt() 
            } + scaleOut(targetScale = 0.8f, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400)),
            modifier = Modifier.fillMaxSize()
        ) {
            SettingsScreen(vm) { vm.showSettingsScreen = false }
        }

        // --- DICTIONARY OVERLAY ---
        dictTransition.AnimatedVisibility(
            visible = { it },
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(animationSpec = tween(400)) { width -> 
                    val direction = if (backSwipeEdge == BackEventCompat.EDGE_RIGHT) -1 else 1
                (width * 0.05f * direction).toInt() 
            } + scaleOut(targetScale = 0.8f, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400)),
            modifier = Modifier.fillMaxSize()
        ) {
            DictionaryScreen(vm) { vm.showDictionaryScreen = false }
        }

        // --- BOTTOM SHEET (Class Details) ---
        if (vm.selectedClass != null) {
            ModalBottomSheet(
                onDismissRequest = { vm.selectedClass = null },
                containerColor = Color.Transparent, 
                dragHandle = null 
            ) {
                
                val swipeProgress = remember { Animatable(0f) }
                
                PredictiveBackHandler { progress ->
                    try {
                        progress.collect { backEvent ->
                            swipeProgress.snapTo(backEvent.progress)
                        }
                        // Commit: Animate to full exit state THEN set null
                        swipeProgress.animateTo(1f)
                        vm.selectedClass = null
                    } catch (e: CancellationException) {
                        // Cancel: Animate back to open state
                        swipeProgress.animateTo(0f)
                    }
                }
                
                // This SURFACE now acts as the real bottom sheet container
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val progress = swipeProgress.value
                            val scale = 1f - (progress * 0.1f)
                            scaleX = scale
                            scaleY = scale
                            
                            // FIX: Move down by full height + 20% buffer to ensure it clears screen even if scrolled
                            translationY = progress * (size.height * 1.2f)
                            
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        },
                    shape = BottomSheetDefaults.ExpandedShape,
                    color = BottomSheetDefaults.ContainerColor
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Re-add standard Drag Handle inside our animated surface
                        BottomSheetDefaults.DragHandle()
                        
                        // The actual content
                        vm.selectedClass?.let { ClassDetailsSheet(vm, it) }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationPermissionRequest() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { })
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
