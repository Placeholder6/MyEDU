package kg.oshsu.myedu

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kg.oshsu.myedu.ui.screens.*

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent { 
            val vm: MainViewModel = viewModel()
            val context = LocalContext.current
            
            LaunchedEffect(Unit) { vm.initSession(context) }
            LaunchedEffect(vm.fullSchedule, vm.timeMap) {
                if (vm.fullSchedule.isNotEmpty() && vm.timeMap.isNotEmpty()) {
                    ScheduleAlarmManager(context).scheduleNotifications(vm.fullSchedule, vm.timeMap)
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
    // UPDATED: Smooth Fade-In for App Content
    AnimatedContent(
        targetState = vm.appState, 
        label = "Root",
        transitionSpec = {
            if (targetState == "APP") {
                fadeIn(animationSpec = tween(1000)) togetherWith fadeOut(animationSpec = tween(1000))
            } else {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            }
        }
    ) { state ->
        when (state) {
            "LOGIN" -> LoginScreen(vm)
            "APP" -> MainAppStructure(vm)
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
