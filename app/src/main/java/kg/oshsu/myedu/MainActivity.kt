package kg.oshsu.myedu

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import kg.oshsu.myedu.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// --- UI COMPONENT: LOGO ---
@Composable
fun OshSuLogo(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    val context = LocalContext.current
    val url = "file:///android_asset/logo-dark4.svg"
    val imageLoader = remember { ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build() }
    AsyncImage(
        model = url,
        imageLoader = imageLoader,
        contentDescription = "OshSU Logo",
        modifier = modifier,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(tint)
    )
}

// --- ACTIVITY: MAIN ENTRY POINT ---
class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

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

// --- UI: THEME CONFIG (UPDATED) ---
@Composable
fun MyEduTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Use our custom palette
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = PrimaryDark, onPrimary = OnPrimaryDark, primaryContainer = PrimaryContainerDark, onPrimaryContainer = OnPrimaryContainerDark,
            secondary = SecondaryDark, onSecondary = OnSecondaryDark, secondaryContainer = SecondaryContainerDark, onSecondaryContainer = SecondaryContainerDark,
            tertiary = TertiaryDark, surface = SurfaceDark, surfaceVariant = SurfaceVariantDark, onSurfaceVariant = OnSurfaceVariantDark, outline = OutlineDark
        )
    } else {
        lightColorScheme(
            primary = PrimaryLight, onPrimary = OnPrimaryLight, primaryContainer = PrimaryContainerLight, onPrimaryContainer = OnPrimaryContainerLight,
            secondary = SecondaryLight, onSecondary = OnSecondaryLight, secondaryContainer = SecondaryContainerLight, onSecondaryContainer = OnSecondaryContainerLight,
            tertiary = TertiaryLight, surface = SurfaceLight, surfaceVariant = SurfaceVariantLight, onSurfaceVariant = OnSurfaceVariantLight, outline = OutlineLight
        )
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, shapes = Shapes, content = content)
}

// --- UI: NAVIGATION HOST ---
@Composable
fun AppContent(vm: MainViewModel) {
    AnimatedContent(targetState = vm.appState, label = "Root") { state ->
        when (state) {
            "LOGIN" -> LoginScreen(vm)
            "APP" -> MainAppStructure(vm)
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

// --- SCREEN: LOGIN (UPDATED) ---
@Composable
fun LoginScreen(vm: MainViewModel) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    
    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { p ->
        Box(modifier = Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 500.dp).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OshSuLogo(modifier = Modifier.width(200.dp).height(80.dp))
                Spacer(Modifier.height(16.dp))
                Text("Welcome Back", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(48.dp))
                
                OutlinedTextField(
                    value = email, onValueChange = { email = it }, 
                    label = { Text("Email") }, 
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it }, 
                    label = { Text("Password") }, 
                    modifier = Modifier.fillMaxWidth(), 
                    visualTransformation = PasswordVisualTransformation(),
                    shape = MaterialTheme.shapes.small
                )
                
                if (vm.errorMsg != null) { 
                    Spacer(Modifier.height(16.dp))
                    Text(vm.errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) 
                }
                
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = { vm.login(email, pass) }, 
                    modifier = Modifier.fillMaxWidth().height(56.dp), 
                    enabled = !vm.isLoading,
                    shape = RoundedCornerShape(100.dp) // Stadium
                ) { 
                    if (vm.isLoading) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary) else Text("Sign In", fontSize = 18.sp) 
                }
            }
        }
    }
}

// --- UI: MAIN SCAFFOLD & NAV (EXPRESSIVE OVERHAUL) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppStructure(vm: MainViewModel) {
    BackHandler(enabled = vm.selectedClass != null || vm.showTranscriptScreen || vm.showReferenceScreen) { 
        when {
            vm.selectedClass != null -> vm.selectedClass = null
            vm.showTranscriptScreen -> vm.showTranscriptScreen = false
            vm.showReferenceScreen -> vm.showReferenceScreen = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // CONTENT LAYER
        Box(Modifier.fillMaxSize().padding(bottom = 0.dp)) {
             when(vm.currentTab) {
                0 -> HomeScreen(vm)
                1 -> ScheduleScreen(vm)
                2 -> GradesScreen(vm)
                3 -> ProfileScreen(vm)
            }
        }

        // FLOATING NAV BAR LAYER (Bottom Center)
        Box(Modifier.align(Alignment.BottomCenter)) {
            ExpressiveNavBar(
                items = listOf(
                    "Home" to Icons.Rounded.Home,
                    "Schedule" to Icons.Rounded.CalendarMonth,
                    "Grades" to Icons.Rounded.School,
                    "Profile" to Icons.Rounded.Person
                ),
                selectedItem = vm.currentTab,
                onItemSelected = { vm.currentTab = it }
            )
        }

        // OVERLAYS
        AnimatedVisibility(
            visible = vm.showTranscriptScreen, 
            enter = slideInHorizontally { it }, 
            exit = slideOutHorizontally { it },
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
        ) { 
            TranscriptView(vm) { vm.showTranscriptScreen = false } 
        }

        AnimatedVisibility(
            visible = vm.showReferenceScreen, 
            enter = slideInHorizontally { it }, 
            exit = slideOutHorizontally { it },
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
        ) { 
            ReferenceView(vm) { vm.showReferenceScreen = false } 
        }
        
        if (vm.selectedClass != null) {
            ModalBottomSheet(
                onDismissRequest = { vm.selectedClass = null },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                vm.selectedClass?.let { ClassDetailsSheet(vm, it) }
            }
        }
    }
}

// --- SCREEN: HOME (BENTO GRID LAYOUT) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    val user = vm.userData
    val profile = vm.profileData
    var showNewsSheet by remember { mutableStateOf(false) }
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = remember(currentHour) { if(currentHour in 4..11) "Good\nMorning" else if(currentHour in 12..16) "Good\nAfternoon" else "Good\nEvening" }
    
    // Bento Grid Layout
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        verticalItemSpacing = 12.dp,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 100.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Header (Span 2)
        item(span = StaggeredGridItemSpan.FullLine) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp), 
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(greeting, style = MaterialTheme.typography.displayMedium, lineHeight = 40.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(user?.name ?: "Student", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                }
                Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable { showNewsSheet = true }, contentAlignment = Alignment.Center) {
                    if (vm.newsList.isNotEmpty()) BadgedBox(badge = { Badge { Text("${vm.newsList.size}") } }) { Icon(Icons.Rounded.Notifications, null) }
                    else Icon(Icons.Rounded.Notifications, null)
                }
            }
        }

        // 2. Main Action / Hero (Span 2) - Next Class or Status
        item(span = StaggeredGridItemSpan.FullLine) {
            val nextClass = vm.todayClasses.firstOrNull()
            if (nextClass != null) {
                ExpressiveCard(
                    onClick = { vm.selectedClass = nextClass },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.height(180.dp)
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.align(Alignment.TopStart)) {
                            Badge(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White) { 
                                Text("UP NEXT", modifier = Modifier.padding(4.dp)) 
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(nextClass.subject?.get() ?: "Class", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text(vm.getTimeString(nextClass.id_lesson), style = MaterialTheme.typography.titleMedium)
                        }
                        Icon(Icons.Rounded.ArrowOutward, null, modifier = Modifier.align(Alignment.BottomEnd).size(32.dp))
                    }
                }
            } else {
                ExpressiveCard(onClick = {}, containerColor = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.height(120.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Free Day!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("No classes scheduled for today.", style = MaterialTheme.typography.bodyMedium)
                        }
                        Icon(Icons.Rounded.Weekend, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha=0.5f))
                    }
                }
            }
        }

        // 3. Stats (Span 1 each)
        item {
            ExpressiveCard(onClick = {}, containerColor = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.height(160.dp)) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Icon(Icons.Rounded.Timeline, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Column {
                        Text("Semester", style = MaterialTheme.typography.labelMedium)
                        Text("${profile?.active_semester ?: 1}", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            ExpressiveCard(onClick = {}, containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.height(160.dp)) {
                 Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Icon(Icons.Rounded.Groups, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column {
                        val group = vm.determinedGroup?.toString() ?: profile?.studentMovement?.avn_group_name ?: "-"
                        Text("Group", style = MaterialTheme.typography.labelMedium)
                        Text(group, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Today's List (Span 2)
        if (vm.todayClasses.isNotEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Text("Rest of Today", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))
            }
            items(vm.todayClasses.drop(1)) { item ->
                ExpressiveCard(onClick = { vm.selectedClass = item }, modifier = Modifier.fillMaxWidth().height(100.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.subject?.get() ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(vm.getTimeString(item.id_lesson), style = MaterialTheme.typography.bodySmall)
                        }
                        ContainerBadge(text = item.room?.name_en ?: "?")
                    }
                }
            }
        }
    }
    
    if (showNewsSheet) { 
        ModalBottomSheet(onDismissRequest = { showNewsSheet = false }) { 
            LazyColumn(contentPadding = PaddingValues(24.dp)) { 
                item { Text("Announcements", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(16.dp)) }
                items(vm.newsList) { news -> 
                    Card(Modifier.padding(bottom=12.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) { 
                        Column(Modifier.padding(16.dp)) { 
                            Text(news.title?:"", fontWeight=FontWeight.Bold, style = MaterialTheme.typography.titleMedium) 
                            Spacer(Modifier.height(4.dp))
                            Text(news.message?:"", style = MaterialTheme.typography.bodyMedium) 
                        } 
                    } 
                } 
            } 
        } 
    }
}

// --- SCREEN: SCHEDULE (PAGER) ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScheduleScreen(vm: MainViewModel) {
    val tabs = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val scope = rememberCoroutineScope()
    val initialPage = remember { val c = Calendar.getInstance(); val d = c.get(Calendar.DAY_OF_WEEK); if (d == Calendar.SUNDAY) 0 else (d - 2).coerceIn(0, 5) }
    val pagerState = rememberPagerState(initialPage = initialPage) { tabs.size }

    Column(Modifier.fillMaxSize().padding(top = 48.dp)) {
        // Tab Header
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent,
            edgePadding = 16.dp,
            divider = {},
            indicator = { tabPositions -> 
                if (pagerState.currentPage < tabPositions.size) {
                    Box(Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]).height(4.dp).padding(horizontal = 12.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                }
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { 
                        Text(
                            title, 
                            style = if(pagerState.currentPage==index) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                            fontWeight = if(pagerState.currentPage==index) FontWeight.Bold else FontWeight.Normal
                        ) 
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { pageIndex ->
            val dayClasses = vm.fullSchedule.filter { it.day == pageIndex }
            if (dayClasses.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Weekend, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.surfaceVariant)
                        Text("No classes", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp, bottom = 100.dp)) {
                    items(dayClasses) { item ->
                         ExpressiveCard(
                             onClick = { vm.selectedClass = item },
                             modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth()
                         ) {
                             Row(verticalAlignment = Alignment.CenterVertically) {
                                 // Time Column
                                 Column(Modifier.width(60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                     val time = vm.getTimeString(item.id_lesson).split("-").firstOrNull() ?: ""
                                     Text(time, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                     Text("Pair ${item.id_lesson}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                 }
                                 // Divider
                                 Box(Modifier.height(40.dp).width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                 Spacer(Modifier.width(16.dp))
                                 // Content
                                 Column(Modifier.weight(1f)) {
                                     Text(item.subject?.get() ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                     Text(item.subject_type?.get() ?: "Lesson", style = MaterialTheme.typography.bodySmall)
                                 }
                                 // Room Badge
                                 ContainerBadge(text = item.room?.name_en ?: "?", color = MaterialTheme.colorScheme.secondaryContainer)
                             }
                         }
                    }
                }
            }
        }
    }
}

// --- SCREEN: GRADES ---
@Composable
fun GradesScreen(vm: MainViewModel) {
    val session = vm.sessionData
    val activeSemId = vm.profileData?.active_semester

    LazyColumn(contentPadding = PaddingValues(top = 48.dp, bottom = 100.dp, start = 16.dp, end = 16.dp)) {
        item { 
            Text("Academic\nPerformance", style = MaterialTheme.typography.displayMedium, lineHeight = 44.sp)
            Spacer(Modifier.height(24.dp))
        }

        if (session.isEmpty()) {
            item { Text("No grades available.", color = MaterialTheme.colorScheme.outline) }
        } else {
            val currentSem = session.find { it.semester?.id == activeSemId } ?: session.lastOrNull()
            items(currentSem?.subjects ?: emptyList()) { sub ->
                val total = sub.marklist?.total?.toInt() ?: 0
                val passed = total >= 50
                
                ExpressiveCard(
                    onClick = {},
                    modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth(),
                    containerColor = if (passed) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.errorContainer.copy(alpha=0.3f)
                ) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(sub.subject?.get() ?: "Subject", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(
                                "$total", 
                                style = MaterialTheme.typography.headlineMedium, 
                                fontWeight = FontWeight.Black,
                                color = if (passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            GradePill("Mod 1", sub.marklist?.point1)
                            GradePill("Mod 2", sub.marklist?.point2)
                            GradePill("Exam", sub.marklist?.finally)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GradePill(label: String, score: Double?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text("${score?.toInt() ?: "-"}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

// --- SCREEN: PROFILE ---
@Composable
fun ProfileScreen(vm: MainViewModel) {
    val user = vm.userData
    val profile = vm.profileData
    val pay = vm.payStatus

    LazyColumn(contentPadding = PaddingValues(top = 0.dp, bottom = 100.dp), modifier = Modifier.fillMaxSize()) {
        // Large Header
        item {
            Box(Modifier.fillMaxWidth().height(280.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(120.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)) {
                        AsyncImage(model = profile?.avatar, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("${user?.name} ${user?.last_name}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(user?.email ?: "", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            Column(Modifier.padding(16.dp)) {
                // Payment Card
                if (pay != null) {
                    ExpressiveCard(onClick = {}, containerColor = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Tuition Status", style = MaterialTheme.typography.labelMedium)
                                val debt = pay.getDebt()
                                if (debt <= 0) Text("Fully Paid", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                else Text("-${debt.toInt()} KGS", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                            CircularProgressIndicator(
                                progress = { ((pay.paid_summa ?: 0.0) / (pay.need_summa ?: 1.0)).toFloat() },
                                modifier = Modifier.size(50.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Actions
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { vm.showReferenceScreen = true }, 
                        modifier = Modifier.weight(1f).height(56.dp), 
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) { Text("Reference PDF") }
                    
                    Button(
                        onClick = { vm.fetchTranscript() }, 
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = MaterialTheme.shapes.medium
                    ) { 
                        if (vm.isTranscriptLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White) else Text("Transcript") 
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                Text("Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                
                ProfileRow(Icons.Rounded.School, "Faculty", profile?.studentMovement?.faculty?.name_en ?: "-")
                ProfileRow(Icons.Rounded.Book, "Speciality", profile?.studentMovement?.speciality?.name_en ?: "-")
                
                Spacer(Modifier.height(32.dp))
                OutlinedButton(
                    onClick = { vm.logout() }, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Log Out") }
            }
        }
    }
}

@Composable
fun ProfileRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// --- HELPER: BADGE ---
@Composable
fun ContainerBadge(text: String, color: Color = MaterialTheme.colorScheme.surface) {
    Surface(color = color, shape = CircleShape) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

// --- DETAILS SHEET (MODERNIZED) ---
@Composable
fun ClassDetailsSheet(vm: MainViewModel, item: ScheduleItem) {
    Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
        Text(item.subject?.get() ?: "Subject", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AccessTime, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(vm.getTimeString(item.id_lesson), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(24.dp))
        
        // Info Grid
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoBox("Type", item.subject_type?.get() ?: "Lesson", Modifier.weight(1f))
            InfoBox("Room", item.room?.name_en ?: "-", Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        InfoBox("Teacher", item.teacher?.get() ?: "Unknown", Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        InfoBox("Building", item.classroom?.building?.getName() ?: "Main Campus", Modifier.fillMaxWidth())
        
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun InfoBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f), MaterialTheme.shapes.medium).padding(16.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

// --- TRANSCRIPT & REFERENCE VIEW WRAPPERS ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptView(vm: MainViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = { TopAppBar(title = { Text("Transcript") }, navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, null) } }) }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize()) {
            if(vm.isPdfGenerating) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.PictureAsPdf, null, modifier = Modifier.size(64.dp).padding(bottom = 16.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Generate Official Transcript", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { vm.generateTranscriptPdf(context, "en") }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Download English PDF") }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { vm.generateTranscriptPdf(context, "ru") }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Download Russian PDF") }
                
                if (vm.pdfStatusMessage != null) {
                    Spacer(Modifier.height(24.dp))
                    Text(vm.pdfStatusMessage!!, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceView(vm: MainViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = { TopAppBar(title = { Text("Reference") }, navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, null) } }) }
    ) { p ->
        Box(Modifier.padding(p).fillMaxSize()) {
            if(vm.isPdfGenerating) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Description, null, modifier = Modifier.size(64.dp).padding(bottom = 16.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Student Reference (Form 8)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { vm.generateReferencePdf(context, "en") }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Download English PDF") }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { vm.generateReferencePdf(context, "ru") }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Download Russian PDF") }
                 if (vm.pdfStatusMessage != null) {
                    Spacer(Modifier.height(24.dp))
                    Text(vm.pdfStatusMessage!!, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}