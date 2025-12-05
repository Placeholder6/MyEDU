package kg.oshsu.myedu

import android.Manifest
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kg.oshsu.myedu.ui.components.ExpressiveShapesBackground
import kg.oshsu.myedu.ui.screens.*

class MainActivity : ComponentActivity() {
    
    private val vm by viewModels<MainViewModel>()

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

@Composable
fun MyEduTheme(themePreference: String, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themePreference) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }

    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else { if (useDarkTheme) darkColorScheme() else lightColorScheme() }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDarkTheme
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppContent(vm: MainViewModel) {
    // 1. Root Container to hold persistent background
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        
        // 2. Persistent Background Layer
        // Only show if in Login or Onboarding to avoid visual noise in the main app
        if (vm.appState == "LOGIN" || vm.appState == "ONBOARDING") {
            ExpressiveShapesBackground(maxWidth, maxHeight)
        }

        // 3. Shared Transition Layout
        SharedTransitionLayout {
            AnimatedContent(
                targetState = vm.appState, 
                label = "Root",
                transitionSpec = {
                    fadeIn(animationSpec = tween(600)) togetherWith fadeOut(animationSpec = tween(600))
                }
            ) { state ->
                when (state) {
                    // Pass Shared scopes
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

    BackHandler(enabled = vm.selectedClass != null || vm.showTranscriptScreen || vm.showReferenceScreen) { 
        when {
            vm.selectedClass != null -> vm.selectedClass = null
            vm.showTranscriptScreen -> vm.showTranscriptScreen = false
            vm.showReferenceScreen -> vm.showReferenceScreen = false
        }
    }

    val navItems = listOf(
        NavItem("Home", Icons.Filled.Home, Icons.Outlined.Home, 0),
        NavItem("Schedule", Icons.Filled.DateRange, Icons.Outlined.DateRange, 1),
        NavItem("Grades", Icons.Filled.Description, Icons.Outlined.Description, 2),
        NavItem("Profile", Icons.Filled.Person, Icons.Outlined.Person, 3)
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