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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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

// --- UI: THEME CONFIG ---
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
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
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

// --- SCREEN: LOGIN ---
@Composable
fun LoginScreen(vm: MainViewModel) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    
    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            ElevatedCard(
                modifier = Modifier.widthIn(max = 480.dp).padding(24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OshSuLogo(modifier = Modifier.width(200.dp).height(80.dp))
                    Spacer(Modifier.height(32.dp))
                    Text("Welcome Back", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(32.dp))
                    
                    OutlinedTextField(
                        value = email, 
                        onValueChange = { email = it }, 
                        label = { Text("Email") }, 
                        modifier = Modifier.fillMaxWidth(), 
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Email, null) }
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pass, 
                        onValueChange = { pass = it }, 
                        label = { Text("Password") }, 
                        modifier = Modifier.fillMaxWidth(), 
                        singleLine = true, 
                        visualTransformation = PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Default.Lock, null) }
                    )
                    
                    if (vm.errorMsg != null) { 
                        Spacer(Modifier.height(16.dp))
                        Text(vm.errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) 
                    }
                    
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = { vm.login(email, pass) }, 
                        modifier = Modifier.fillMaxWidth().height(48.dp), 
                        enabled = !vm.isLoading
                    ) { 
                        if (vm.isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary) 
                        else Text("Sign In") 
                    }
                }
            }
        }
    }
}

// --- UI: MAIN SCAFFOLD & BOTTOM NAV ---
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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    val items = listOf(
                        Triple("Home", Icons.Outlined.Home, Icons.Filled.Home),
                        Triple("Schedule", Icons.Outlined.DateRange, Icons.Filled.DateRange),
                        Triple("Grades", Icons.Outlined.Description, Icons.Filled.Description),
                        Triple("Profile", Icons.Outlined.Person, Icons.Filled.Person)
                    )
                    
                    items.forEachIndexed { index, (label, outlined, filled) ->
                        NavigationBarItem(
                            icon = { Icon(if (vm.currentTab == index) filled else outlined, contentDescription = null) },
                            label = { Text(label) },
                            selected = vm.currentTab == index,
                            onClick = { vm.currentTab = index }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                 when(vm.currentTab) {
                    0 -> HomeScreen(vm)
                    1 -> ScheduleScreen(vm)
                    2 -> GradesScreen(vm)
                    3 -> ProfileScreen(vm)
                }
            }
        }

        // Full Screen Overlays
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
        
        // Bottom Sheet Popup
        if (vm.selectedClass != null) {
            ModalBottomSheet(
                onDismissRequest = { vm.selectedClass = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                vm.selectedClass?.let { ClassDetailsSheet(vm, it) }
            }
        }
    }
}

// --- SCREEN: HOME TAB ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    val user = vm.userData
    val profile = vm.profileData
    var showNewsSheet by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = remember(currentHour) { if(currentHour in 4..11) "Good Morning" else if(currentHour in 12..16) "Good Afternoon" else "Good Evening" }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { 
                    Column {
                        Text(greeting, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                        Text(user?.name ?: "Student", style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    IconButton(onClick = { showNewsSheet = true }) {
                        if (vm.newsList.isNotEmpty()) {
                            BadgedBox(badge = { Badge { Text("${vm.newsList.size}") } }) {
                                Icon(Icons.Outlined.Notifications, "News")
                            }
                        } else {
                            Icon(Icons.Outlined.Notifications, "News")
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) { 
                    InfoCard(
                        icon = Icons.Outlined.CalendarToday, 
                        label = "Semester", 
                        value = profile?.active_semester?.toString() ?: "-",
                        modifier = Modifier.weight(1f)
                    )
                    InfoCard(
                        icon = Icons.Outlined.Groups, 
                        label = "Group", 
                        value = vm.determinedGroup?.toString() ?: profile?.studentMovement?.avn_group_name ?: "-",
                        modifier = Modifier.weight(1f)
                    ) 
                }
            }
            
            item {
                Text(
                    text = "${vm.todayDayName}'s Schedule",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (vm.todayClasses.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Weekend, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.height(16.dp))
                            Text("No classes today!", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(vm.todayClasses) { item ->
                    ClassListItem(item, vm.getTimeString(item.id_lesson)) { vm.selectedClass = item }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    
    if (showNewsSheet) {
        ModalBottomSheet(onDismissRequest = { showNewsSheet = false }) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text("Announcements", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                LazyColumn {
                    items(vm.newsList) { news ->
                        ListItem(
                            headlineContent = { Text(news.title ?: "") },
                            supportingContent = { Text(news.message ?: "", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(Icons.Default.Article, null) },
                            modifier = Modifier.clickable { /* Expand news if needed */ }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// --- SCREEN: SCHEDULE TAB ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(vm: MainViewModel) {
    val tabs = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val pagerState = rememberPagerState(initialPage = Calendar.getInstance().get(Calendar.DAY_OF_WEEK).let { if (it == Calendar.SUNDAY) 0 else (it - 2).coerceIn(0, 5) }) { tabs.size }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Schedule") })
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) }
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
                val dayClasses = vm.fullSchedule.filter { it.day == pageIndex }
                if (dayClasses.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No classes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp)) {
                        items(dayClasses) { item ->
                            ClassListItem(item, vm.getTimeString(item.id_lesson)) { vm.selectedClass = item }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

// --- HELPER COMPONENTS ---

@Composable
fun ClassListItem(item: ScheduleItem, timeString: String, onClick: () -> Unit) {
    val startTime = timeString.split("-").firstOrNull()?.trim() ?: ""
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // Flat list style
    ) {
        ListItem(
            headlineContent = { Text(item.subject?.get() ?: "Subject", fontWeight = FontWeight.SemiBold) },
            supportingContent = { 
                val room = item.room?.name_en ?: "Room ?"
                val type = item.subject_type?.get() ?: "Lesson"
                Text("$room • $type")
            },
            leadingContent = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp)) {
                    Text(startTime, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Pair ${item.id_lesson}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            },
            trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
fun InfoCard(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// --- SCREEN: GRADES TAB ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(vm: MainViewModel) {
    val session = vm.sessionData
    val activeSemId = vm.profileData?.active_semester

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Grades") }) }) { padding ->
        if (vm.isGradesLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                if (session.isEmpty()) item { Text("No grades available.") }
                else {
                    val currentSem = session.find { it.semester?.id == activeSemId } ?: session.lastOrNull()
                    currentSem?.let { sem ->
                        item { 
                            Text(
                                sem.semester?.name_en ?: "Current Semester", 
                                style = MaterialTheme.typography.titleMedium, 
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) 
                        }
                        items(sem.subjects ?: emptyList()) { sub ->
                            OutlinedCard(modifier = Modifier.padding(bottom = 8.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(sub.subject?.get() ?: "Subject", style = MaterialTheme.typography.titleMedium)
                                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        GradeItem("M1", sub.marklist?.point1)
                                        GradeItem("M2", sub.marklist?.point2)
                                        GradeItem("Exam", sub.marklist?.finally)
                                        GradeItem("Total", sub.marklist?.total, isTotal = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GradeItem(label: String, score: Double?, isTotal: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        val scoreInt = score?.toInt() ?: 0
        val color = if (isTotal) {
            if (scoreInt >= 50) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
        } else MaterialTheme.colorScheme.onSurface
        Text("$scoreInt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

// --- SCREEN: PROFILE TAB ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(vm: MainViewModel) {
    val user = vm.userData
    val profile = vm.profileData
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                actions = { IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, null) } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            // Header
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.size(120.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                    if (profile?.avatar != null) {
                        AsyncImage(model = profile.avatar, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Text(
                            text = (user?.name?.take(1) ?: "S").uppercase(),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("${user?.last_name} ${user?.name}", style = MaterialTheme.typography.headlineSmall)
                Text(user?.email ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            }

            // Documents Section
            Text("Documents", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
            ListItem(
                headlineContent = { Text("Transcript") },
                leadingContent = { Icon(Icons.Outlined.School, null) },
                trailingContent = { Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.clickable { vm.fetchTranscript() }
            )
            ListItem(
                headlineContent = { Text("Reference Letter") },
                leadingContent = { Icon(Icons.Outlined.Description, null) },
                trailingContent = { Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.clickable { vm.showReferenceScreen = true }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Action
            ListItem(
                headlineContent = { Text("Log Out", color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { vm.logout() }
            )
        }
    }
    
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Settings") },
            text = {
                OutlinedTextField(
                    value = vm.dictionaryUrl, 
                    onValueChange = { vm.dictionaryUrl = it }, 
                    label = { Text("Dictionary URL") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done") } }
        )
    }
}

// --- SHEET: CLASS DETAILS ---
@Composable
fun ClassDetailsSheet(vm: MainViewModel, item: ScheduleItem) {
    val timeString = vm.getTimeString(item.id_lesson)
    Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
        Text(item.subject?.get() ?: "Subject", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AccessTime, null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(8.dp))
            Text(timeString, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(24.dp))
        
        DetailItem(Icons.Outlined.Person, "Teacher", item.teacher?.get() ?: "Unknown")
        DetailItem(Icons.Outlined.MeetingRoom, "Room", item.room?.name_en ?: "Unknown")
        DetailItem(Icons.Outlined.Business, "Building", item.classroom?.building?.getName() ?: "-")
        
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun DetailItem(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// --- SCREEN: REFERENCE VIEW ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceView(vm: MainViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val user = vm.userData
    val profile = vm.profileData
    val mov = profile?.studentMovement
    val activeSemester = profile?.active_semester ?: 1
    val course = (activeSemester + 1) / 2
    
    val facultyName = mov?.faculty?.let { it.name_en ?: it.name_ru } 
        ?: mov?.speciality?.faculty?.let { it.name_en ?: it.name_ru } 
        ?: "-"

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Reference") }, 
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, null) } }
            ) 
        },
        bottomBar = {
            if (!vm.isPdfGenerating) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { vm.generateReferencePdf(context, "ru") }, modifier = Modifier.weight(1f)) { Text("PDF (RU)") }
                    Button(onClick = { vm.generateReferencePdf(context, "en") }, modifier = Modifier.weight(1f)) { Text("PDF (EN)") }
                }
            } else {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp)) {
                    OshSuLogo(modifier = Modifier.width(150.dp).height(60.dp).align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(16.dp))
                    Text("CERTIFICATE OF STUDY", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(24.dp))
                    
                    RefDetailRow("Name", "${user?.last_name} ${user?.name}")
                    RefDetailRow("Faculty", facultyName)
                    RefDetailRow("Speciality", mov?.speciality?.name_en ?: "-")
                    RefDetailRow("Year", "$course ($activeSemester Semester)")
                    
                    Spacer(Modifier.height(24.dp))
                    Text("Active Student • ${SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date())}", color = Color(0xFF4CAF50))
                }
            }
            if (vm.pdfStatusMessage != null) {
                Spacer(Modifier.height(16.dp))
                Text(vm.pdfStatusMessage!!, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
fun RefDetailRow(label: String, value: String) {
    Column(Modifier.padding(bottom = 16.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

// --- SCREEN: TRANSCRIPT VIEW ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptView(vm: MainViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Transcript") }, 
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, null) } }
            ) 
        },
        bottomBar = {
            if (vm.transcriptData.isNotEmpty() && !vm.isPdfGenerating) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { vm.generateTranscriptPdf(context, "ru") }, modifier = Modifier.weight(1f)) { Text("PDF (RU)") }
                    Button(onClick = { vm.generateTranscriptPdf(context, "en") }, modifier = Modifier.weight(1f)) { Text("PDF (EN)") }
                }
            } else if (vm.isPdfGenerating) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (vm.isTranscriptLoading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    vm.transcriptData.forEach { yearData ->
                        item { 
                            Text(yearData.eduYear ?: "Year", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) 
                        }
                        yearData.semesters?.forEach { sem ->
                            item { 
                                Text(sem.semesterName ?: "Semester", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp)) 
                            }
                            items(sem.subjects ?: emptyList()) { sub ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                                ) {
                                    ListItem(
                                        headlineContent = { Text(sub.subjectName ?: "Subject") },
                                        supportingContent = { Text("Credits: ${sub.credit?.toInt() ?: 0} • ${sub.examRule?.alphabetic ?: "-"}") },
                                        trailingContent = { 
                                            Text(
                                                "${sub.markList?.total?.toInt() ?: 0}", 
                                                style = MaterialTheme.typography.titleLarge, 
                                                fontWeight = FontWeight.Bold,
                                                color = if ((sub.markList?.total ?: 0.0) >= 50) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                                            ) 
                                        }
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
            if (vm.pdfStatusMessage != null) {
                Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(), shadowElevation = 4.dp, shape = RoundedCornerShape(8.dp)) {
                    Text(vm.pdfStatusMessage!!, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
