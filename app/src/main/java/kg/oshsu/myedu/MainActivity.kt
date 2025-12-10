package kg.oshsu.myedu

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kg.oshsu.myedu.ui.components.ExpressiveShapesBackground
import kg.oshsu.myedu.ui.screens.*

class MainActivity : ComponentActivity() {
    
    private val vm by viewModels<MainViewModel>()

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("myedu_offline_cache", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_language", "en") ?: "en"
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm.initSession(applicationContext)

        splashScreen.setKeepOnScreenCondition {
            vm.appState == "STARTUP"
        }

        setContent { 
            LaunchedEffect(vm.fullSchedule, vm.timeMap, vm.notificationsEnabled) {
                if (vm.fullSchedule.isNotEmpty() && vm.timeMap.isNotEmpty() && vm.notificationsEnabled) {
                    ScheduleAlarmManager(applicationContext).scheduleNotifications(vm.fullSchedule, vm.timeMap)
                }
            }

            MyEduTheme(themePreference = vm.appTheme) { AppContent(vm) } 
        }
    }
}

// ... [MyEduTheme and rememberAnimatedColorScheme remain the same] ...
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
    } else { if (useDarkTheme) darkColorScheme() else lightColorScheme() }
    
    val animatedScheme = rememberAnimatedColorScheme(targetScheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDarkTheme
        }
    }
    MaterialTheme(colorScheme = animatedScheme, content = content)
}

@Composable
fun rememberAnimatedColorScheme(targetColorScheme: ColorScheme): ColorScheme {
    val animationSpec = tween<Color>(durationMillis = 600)

    val primary by animateColorAsState(targetColorScheme.primary, animationSpec, label = "primary")
    val onPrimary by animateColorAsState(targetColorScheme.onPrimary, animationSpec, label = "onPrimary")
    val primaryContainer by animateColorAsState(targetColorScheme.primaryContainer, animationSpec, label = "primaryContainer")
    val onPrimaryContainer by animateColorAsState(targetColorScheme.onPrimaryContainer, animationSpec, label = "onPrimaryContainer")
    val secondary by animateColorAsState(targetColorScheme.secondary, animationSpec, label = "secondary")
    val onSecondary by animateColorAsState(targetColorScheme.onSecondary, animationSpec, label = "onSecondary")
    val secondaryContainer by animateColorAsState(targetColorScheme.secondaryContainer, animationSpec, label = "secondaryContainer")
    val onSecondaryContainer by animateColorAsState(targetColorScheme.onSecondaryContainer, animationSpec, label = "onSecondaryContainer")
    val tertiary by animateColorAsState(targetColorScheme.tertiary, animationSpec, label = "tertiary")
    val onTertiary by animateColorAsState(targetColorScheme.onTertiary, animationSpec, label = "onTertiary")
    val tertiaryContainer by animateColorAsState(targetColorScheme.tertiaryContainer, animationSpec, label = "tertiaryContainer")
    val onTertiaryContainer by animateColorAsState(targetColorScheme.onTertiaryContainer, animationSpec, label = "onTertiaryContainer")
    val background by animateColorAsState(targetColorScheme.background, animationSpec, label = "background")
    val onBackground by animateColorAsState(targetColorScheme.onBackground, animationSpec, label = "onBackground")
    val surface by animateColorAsState(targetColorScheme.surface, animationSpec, label = "surface")
    val onSurface by animateColorAsState(targetColorScheme.onSurface, animationSpec, label = "onSurface")
    val surfaceVariant by animateColorAsState(targetColorScheme.surfaceVariant, animationSpec, label = "surfaceVariant")
    val onSurfaceVariant by animateColorAsState(targetColorScheme.onSurfaceVariant, animationSpec, label = "onSurfaceVariant")
    val outline by animateColorAsState(targetColorScheme.outline, animationSpec, label = "outline")
    val outlineVariant by animateColorAsState(targetColorScheme.outlineVariant, animationSpec, label = "outlineVariant")
    val error by animateColorAsState(targetColorScheme.error, animationSpec, label = "error")
    val onError by animateColorAsState(targetColorScheme.onError, animationSpec, label = "onError")
    val errorContainer by animateColorAsState(targetColorScheme.errorContainer, animationSpec, label = "errorContainer")
    val onErrorContainer by animateColorAsState(targetColorScheme.onErrorContainer, animationSpec, label = "onErrorContainer")
    val surfaceContainerLowest by animateColorAsState(targetColorScheme.surfaceContainerLowest, animationSpec, label = "sCL")
    val surfaceContainerLow by animateColorAsState(targetColorScheme.surfaceContainerLow, animationSpec, label = "sCLow")
    val surfaceContainer by animateColorAsState(targetColorScheme.surfaceContainer, animationSpec, label = "sC")
    val surfaceContainerHigh by animateColorAsState(targetColorScheme.surfaceContainerHigh, animationSpec, label = "sCH")
    val surfaceContainerHighest by animateColorAsState(targetColorScheme.surfaceContainerHighest, animationSpec, label = "sCHH")

    return targetColorScheme.copy(
        primary = primary, onPrimary = onPrimary, primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = secondary, onSecondary = onSecondary, secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary, onTertiary = onTertiary, tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
        background = background, onBackground = onBackground,
        surface = surface, onSurface = onSurface, surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
        outline = outline, outlineVariant = outlineVariant,
        error = error, onError = onError, errorContainer = errorContainer, onErrorContainer = onErrorContainer,
        surfaceContainerLowest = surfaceContainerLowest, surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer, surfaceContainerHigh = surfaceContainerHigh, surfaceContainerHighest = surfaceContainerHighest
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
                    fadeIn(animationSpec = tween(600)) togetherWith fadeOut(animationSpec = tween(600))
                }
            ) { state ->
                when (state) {
                    "LOGIN" -> LoginScreen(vm, this@SharedTransitionLayout, this@AnimatedContent)
                    "ONBOARDING" -> OnboardingScreen(vm, this@SharedTransitionLayout, this@AnimatedContent)
                    "APP" -> MainAppStructure(vm)
                    else -> Box(Modifier.fillMaxSize()) 
                }
            }
        }
    }
}

data class NavItem(val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector, val index: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppStructure(vm: MainViewModel) {
    NotificationPermissionRequest()

    BackHandler(enabled = vm.selectedClass != null || vm.showTranscriptScreen || vm.showReferenceScreen || vm.showSettingsScreen || vm.showDictionaryScreen) { 
        when {
            vm.selectedClass != null -> vm.selectedClass = null
            vm.showTranscriptScreen -> vm.showTranscriptScreen = false
            vm.showReferenceScreen -> vm.showReferenceScreen = false
            vm.showDictionaryScreen -> vm.showDictionaryScreen = false // Close Dict
            vm.showSettingsScreen -> vm.showSettingsScreen = false 
        }
    }

    val navItems = listOf(
        NavItem(stringResource(R.string.nav_home), Icons.Filled.Home, Icons.Outlined.Home, 0),
        NavItem(stringResource(R.string.nav_schedule), Icons.Filled.DateRange, Icons.Outlined.DateRange, 1),
        NavItem(stringResource(R.string.nav_grades), Icons.Filled.Description, Icons.Outlined.Description, 2),
        NavItem(stringResource(R.string.nav_profile), Icons.Filled.Person, Icons.Outlined.Person, 3)
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
            Box(Modifier.padding(padding)) {
                AnimatedContent(
                    targetState = vm.currentTab,
                    label = "TabTransition",
                    transitionSpec = { fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300)) }
                ) { targetTab ->
                    when(targetTab) {
                        0 -> HomeScreen(vm)
                        1 -> ScheduleScreen(vm)
                        2 -> GradesScreen(vm)
                        3 -> ProfileScreen(vm)
                    }
                }
            }
        }
        
        AnimatedVisibility(visible = vm.showTranscriptScreen, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }, modifier = Modifier.fillMaxSize()) { TranscriptView(vm) { vm.showTranscriptScreen = false } }
        AnimatedVisibility(visible = vm.showReferenceScreen, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }, modifier = Modifier.fillMaxSize()) { ReferenceView(vm) { vm.showReferenceScreen = false } }
        
        AnimatedVisibility(
            visible = vm.showSettingsScreen, 
            enter = slideInHorizontally(initialOffsetX = { it }), 
            exit = slideOutHorizontally(targetOffsetX = { it }), 
            modifier = Modifier.fillMaxSize()
        ) { 
            SettingsScreen(vm) { vm.showSettingsScreen = false } 
        }

        // --- NEW: Dictionary Screen Overlay ---
        AnimatedVisibility(
            visible = vm.showDictionaryScreen, 
            enter = slideInHorizontally(initialOffsetX = { it }), 
            exit = slideOutHorizontally(targetOffsetX = { it }), 
            modifier = Modifier.fillMaxSize()
        ) { 
            DictionaryScreen(vm) { vm.showDictionaryScreen = false } 
        }

        if (vm.selectedClass != null) {
            ModalBottomSheet(onDismissRequest = { vm.selectedClass = null }) { vm.selectedClass?.let { ClassDetailsSheet(vm, it) } }
        }
    }
}

@Composable
fun NotificationPermissionRequest() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission(), onResult = { })
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}