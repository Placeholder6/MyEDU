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
import kg.oshsu.myedu.ui.screens.*

class MainActivity : ComponentActivity() {
    
    private val vm by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Install Splash Screen (Must be first)
        // This handles the transition from the Splash Theme to the App Theme.
        val splashScreen = installSplashScreen()
        
        super.onCreate(savedInstanceState)
        
        // 2. Enable Edge-to-Edge (Mandatory for Android 15+)
        enableEdgeToEdge()
        
        // 3. Init Data
        vm.initSession(applicationContext)

        // 4. Keep Splash Screen until App State isn't STARTUP
        // This prevents the "white flash" by holding the splash screen 
        // until we know if the user is logged in or not.
        splashScreen.setKeepOnScreenCondition {
            vm.appState == "STARTUP"
        }

        setContent { 
            // Alarm Manager (Background Logic)
            LaunchedEffect(vm.fullSchedule, vm.timeMap) {
                if (vm.fullSchedule.isNotEmpty() && vm.timeMap.isNotEmpty()) {
                    ScheduleAlarmManager(applicationContext).scheduleNotifications(vm.fullSchedule, vm.timeMap)
                }
            }

            MyEduTheme { AppContent(vm) } 
        }
    }
}

@Composable
fun MyEduTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else { if (darkTheme) darkColorScheme() else lightColorScheme() }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
fun AppContent(vm: MainViewModel) {
    AnimatedContent(
        targetState = vm.appState, 
        label = "Root",
        transitionSpec = {
            // Smooth crossfade to match native splash exit
            fadeIn(animationSpec = tween(600)) togetherWith fadeOut(animationSpec = tween(600))
        }
    ) { state ->
        when (state) {
            "LOGIN" -> LoginScreen(vm)
            "APP" -> MainAppStructure(vm)
            else -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) 
        }
    }
}

data class NavItem(
    val label: String, 
    val selectedIcon: ImageVector, 
    val unselectedIcon: ImageVector,
    val index: Int
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainAppStructure(vm: MainViewModel) {
    // --- PERMISSION REQUEST (Only runs in APP state) ---
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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                CompositionLocalProvider(LocalRippleConfiguration provides null) {
                    ShortNavigationBar {
                        navItems.forEach { item ->
                            val isSelected = vm.currentTab == item.index
                            
                            ShortNavigationBarItem(
                                selected = isSelected,
                                onClick = { vm.currentTab = item.index },
                                icon = {
                                    Crossfade(
                                        targetState = isSelected, 
                                        label = "IconFade",
                                        animationSpec = tween(durationMillis = 200) 
                                    ) { selected ->
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    }
                                },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                AnimatedContent(
                    targetState = vm.currentTab,
                    label = "TabTransition",
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith 
                        fadeOut(animationSpec = tween(300))
                    }
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

        AnimatedVisibility(
            visible = vm.showTranscriptScreen, 
            enter = slideInHorizontally { it }, 
            exit = slideOutHorizontally { it },
            modifier = Modifier.fillMaxSize() 
        ) { 
            TranscriptView(vm) { vm.showTranscriptScreen = false } 
        }

        AnimatedVisibility(
            visible = vm.showReferenceScreen, 
            enter = slideInHorizontally { it }, 
            exit = slideOutHorizontally { it },
            modifier = Modifier.fillMaxSize()
        ) { 
            ReferenceView(vm) { vm.showReferenceScreen = false } 
        }
        
        if (vm.selectedClass != null) {
            ModalBottomSheet(
                onDismissRequest = { vm.selectedClass = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                vm.selectedClass?.let { ClassDetailsSheet(vm, it) }
            }
        }
    }
}

@Composable
fun NotificationPermissionRequest() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )
    
    // LaunchedEffect ensures this runs once when the composable enters the composition
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
