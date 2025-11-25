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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.decode.SvgDecoder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// --- CONSTANTS FOR EXPRESSIVE DESIGN ---
val ExpressiveContainerShape = RoundedCornerShape(24.dp)
val ExpressiveButtonShape = RoundedCornerShape(50) // Stadium/Pill
val ExpressiveCardShape = RoundedCornerShape(20.dp)

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

// --- UI: THEME CONFIG (EXPRESSIVE) ---
@Composable
fun MyEduTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Use dynamic color if available for that M3 feel
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else { 
        if (darkTheme) darkColorScheme() else lightColorScheme() 
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
    
    // Override shapes for Expressive look
    val shapes = Shapes(
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp)
    )

    MaterialTheme(
        colorScheme = colorScheme, 
        shapes = shapes,
        typography = MaterialTheme.typography.copy(
            displayMedium = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
        ),
        content = content
    )
}

// --- UI: NAVIGATION HOST ---
@Composable
fun AppContent(vm: MainViewModel) {
    AnimatedContent(
        targetState = vm.appState, 
        label = "Root",
        transitionSpec = {
            fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) togetherWith 
            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow))
        }
    ) { state ->
        when (state) {
            "LOGIN" -> LoginScreen(vm)
            "APP" -> MainAppStructure(vm)
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

// --- SCREEN: LOGIN (HERO LAYOUT) ---
@Composable
fun LoginScreen(vm: MainViewModel) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    Scaffold(containerColor = MaterialTheme.colorScheme.primaryContainer) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Top Section: Hero Branding
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.4f)
                    .padding(top = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OshSuLogo(
                    modifier = Modifier.width(180.dp).height(80.dp), 
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "MyEDU", 
                    style = MaterialTheme.typography.displayMedium, 
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Bottom Section: Input Surface
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.65f)
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Welcome Back", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(32.dp))
                    
                    OutlinedTextField(
                        value = email, 
                        onValueChange = { email = it }, 
                        label = { Text("Email") }, 
                        modifier = Modifier.fillMaxWidth(), 
                        singleLine = true,
                        shape = ExpressiveButtonShape
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pass, 
                        onValueChange = { pass = it }, 
                        label = { Text("Password") }, 
                        modifier = Modifier.fillMaxWidth(), 
                        singleLine = true, 
                        visualTransformation = PasswordVisualTransformation(),
                        shape = ExpressiveButtonShape
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
                        shape = ExpressiveButtonShape
                    ) { 
                        if (vm.isLoading) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp)) 
                        else Text("Sign In", style = MaterialTheme.typography.titleMedium) 
                    }
                }
            }
        }
    }
}

// --- UI: MAIN SCAFFOLD & BOTTOM NAV (EXPRESSIVE) ---
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
                // M3 NavigationBar with Expressive state
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 8.dp
                ) {
                    val items = listOf(
                        Triple("Home", Icons.Filled.Home, Icons.Outlined.Home),
                        Triple("Schedule", Icons.Filled.DateRange, Icons.Outlined.DateRange),
                        Triple("Grades", Icons.Filled.Description, Icons.Outlined.Description),
                        Triple("Profile", Icons.Filled.Person, Icons.Outlined.Person)
                    )
                    
                    items.forEachIndexed { index, (label, filledIcon, outlinedIcon) ->
                        val isSelected = vm.currentTab == index
                        NavigationBarItem(
                            icon = { 
                                Icon(if (isSelected) filledIcon else outlinedIcon, contentDescription = label) 
                            },
                            label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            selected = isSelected,
                            onClick = { vm.currentTab = index },
                            // Explicit colors for high contrast active state
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
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
        
        // Bottom Sheet
        if (vm.selectedClass != null) {
            ModalBottomSheet(
                onDismissRequest = { vm.selectedClass = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                vm.selectedClass?.let { ClassDetailsSheet(vm, it) }
            }
        }
    }
}

// --- SCREEN: HOME (FEED LAYOUT) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    val user = vm.userData; val profile = vm.profileData
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { 
                    Column {
                        Text(
                            text = "Hello, ${user?.name?.split(" ")?.firstOrNull() ?: "Student"}",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        BadgedBox(badge = { if (vm.newsList.isNotEmpty()) Badge { Text("${vm.newsList.size}") } }) { 
                            Icon(Icons.Outlined.Notifications, "News") 
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Cards Row
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeroStatCard(
                        modifier = Modifier.weight(1f),
                        title = "Semester",
                        value = profile?.active_semester?.toString() ?: "-",
                        subtitle = vm.determinedStream?.let { "Stream $it" },
                        color = MaterialTheme.colorScheme.primaryContainer,
                        onColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    HeroStatCard(
                        modifier = Modifier.weight(1f),
                        title = "Group",
                        value = vm.determinedGroup?.toString() ?: profile?.studentMovement?.avn_group_name ?: "-",
                        subtitle = if (vm.determinedGroup != null) profile?.studentMovement?.avn_group_name else null,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        onColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            
            // Today's Classes Header
            item {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Today's Schedule", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.currentTab = 1 }) { Text("See All") }
                }
            }

            if (vm.todayClasses.isEmpty()) {
                item {
                    EmptyStateCard("No classes today", Icons.Outlined.Weekend)
                }
            } else {
                items(vm.todayClasses) { item ->
                    ClassItem(item, vm.getTimeString(item.id_lesson)) { vm.selectedClass = item }
                }
            }
            
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// --- COMPONENT: HERO STAT CARD ---
@Composable
fun HeroStatCard(
    modifier: Modifier,
    title: String,
    value: String,
    subtitle: String?,
    color: Color,
    onColor: Color
) {
    Card(
        modifier = modifier.height(140.dp),
        shape = ExpressiveCardShape,
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = onColor.copy(alpha = 0.7f))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = onColor
                )
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = onColor.copy(alpha = 0.8f))
                }
            }
        }
    }
}

// --- COMPONENT: EXPRESSIVE CLASS ITEM ---
@Composable
fun ClassItem(item: ScheduleItem, timeString: String, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveCardShape, // Larger corner radius
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time Strip
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.width(48.dp).padding(end = 12.dp)
            ) {
                Text(
                    text = timeString.split("-").firstOrNull()?.trim() ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${item.id_lesson}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.fillMaxHeight().width(1.dp))
            
            // Content
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    text = item.subject?.get() ?: "Subject",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Room, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = item.room?.name_en ?: "Unknown Room",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = item.subject_type?.get() ?: "Lesson",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

// --- SCREEN: SCHEDULE (TIMELINE PAGER) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(vm: MainViewModel) {
    val tabs = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val pagerState = rememberPagerState(initialPage = Calendar.getInstance().get(Calendar.DAY_OF_WEEK).let { if (it == 1) 0 else (it - 2).coerceIn(0, 5) }) { 6 }
    val scope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                CenterAlignedTopAppBar(
                    title = { Text("Schedule", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                )
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surface,
                    edgePadding = 16.dp,
                    indicator = { positions ->
                        if (pagerState.currentPage < positions.size) {
                            // Expressive Pill Indicator
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(positions[pagerState.currentPage]),
                                height = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        val selected = pagerState.currentPage == index
                        Tab(
                            selected = selected,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { 
                                Text(
                                    title, 
                                    color = if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if(selected) FontWeight.Bold else FontWeight.Normal
                                ) 
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        HorizontalPager(state = pagerState, modifier = Modifier.padding(padding)) { page ->
            val classes = vm.fullSchedule.filter { it.day == page }
            if (classes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyStateCard("No classes", Icons.Outlined.EventBusy)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(classes) { item ->
                        ClassItem(item, vm.getTimeString(item.id_lesson)) { vm.selectedClass = item }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(msg: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        Icon(icon, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(msg, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// --- SCREEN: PROFILE (EXPRESSIVE) ---
@Composable
fun ProfileScreen(vm: MainViewModel) {
    val profile = vm.profileData
    val user = vm.userData
    
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        // Expressive Avatar
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
        ) {
            AsyncImage(
                model = profile?.avatar, 
                contentDescription = null, 
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Spacer(Modifier.height(24.dp))
        Text(
            "${user?.last_name} ${user?.name}", 
            style = MaterialTheme.typography.headlineMedium, 
            textAlign = TextAlign.Center
        )
        Text(
            user?.email ?: "", 
            style = MaterialTheme.typography.bodyLarge, 
            color = MaterialTheme.colorScheme.secondary
        )
        
        Spacer(Modifier.height(32.dp))
        
        // Contract Card
        vm.payStatus?.let { pay ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ExpressiveCardShape,
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(Modifier.padding(24.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Tuition", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Icon(Icons.Outlined.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { (pay.paid_summa?.toFloat() ?: 0f) / (pay.need_summa?.toFloat() ?: 1f) },
                        modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Paid", style = MaterialTheme.typography.labelMedium)
                            Text("${pay.paid_summa?.toInt()} s", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total", style = MaterialTheme.typography.labelMedium)
                            Text("${pay.need_summa?.toInt()} s", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // Settings List
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = ExpressiveCardShape
        ) {
            ListItem(
                headlineContent = { Text("Transcript") },
                leadingContent = { Icon(Icons.Outlined.Description, null) },
                modifier = Modifier.clickable { vm.fetchTranscript() }
            )
            HorizontalDivider(Modifier.padding(start = 56.dp))
            ListItem(
                headlineContent = { Text("Reference Paper") },
                leadingContent = { Icon(Icons.Outlined.Assignment, null) },
                modifier = Modifier.clickable { vm.showReferenceScreen = true }
            )
            HorizontalDivider(Modifier.padding(start = 56.dp))
            ListItem(
                headlineContent = { Text("Log Out") },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = MaterialTheme.colorScheme.error) },
                headlineColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.clickable { vm.logout() }
            )
        }
        
        Spacer(Modifier.height(100.dp))
    }
}

// --- SCREEN: GRADES (STANDARD LIST) ---
@Composable
fun GradesScreen(vm: MainViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
             Text("Current Grades", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 16.dp))
        }
        val currentSem = vm.sessionData.find { it.semester?.id == vm.profileData?.active_semester }
        if (currentSem != null) {
             items(currentSem.subjects ?: emptyList()) { sub ->
                 OutlinedCard(
                     modifier = Modifier.fillMaxWidth(),
                     shape = ExpressiveCardShape
                 ) {
                     ListItem(
                         headlineContent = { Text(sub.subject?.get() ?: "") },
                         supportingContent = {
                             val m1 = sub.marklist?.point1 ?: 0.0
                             val m2 = sub.marklist?.point2 ?: 0.0
                             val fin = sub.marklist?.finally ?: 0.0
                             val total = sub.marklist?.total ?: 0.0
                             Text("M1: ${m1.toInt()}  |  M2: ${m2.toInt()}  |  Exam: ${fin.toInt()}")
                         },
                         trailingContent = {
                             Surface(
                                 color = if ((sub.marklist?.total ?: 0.0) >= 50) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                 shape = RoundedCornerShape(8.dp)
                             ) {
                                 Text(
                                     "${sub.marklist?.total?.toInt()}",
                                     modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                     fontWeight = FontWeight.Bold,
                                     style = MaterialTheme.typography.titleMedium
                                 )
                             }
                         }
                     )
                 }
             }
        } else {
            item { Text("No grade data available") }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// --- OVERLAYS & SHEETS REMAIN SIMILAR BUT WITH UPDATED SHAPES ---
// (TranscriptView and ReferenceView follow similar structure but use the Expressive shapes defined in MyEduTheme)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptView(vm: MainViewModel, onClose: () -> Unit) {
    Scaffold(
        topBar = { 
            TopAppBar(title = { Text("Transcript") }, navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) 
        }
    ) { padding ->
        // Reuse existing logic with new Card styles
        LazyColumn(Modifier.padding(padding).padding(16.dp)) {
             // ... (Implementation same as previous but using ExpressiveCardShape for items)
             items(vm.transcriptData) { year ->
                 Text(year.eduYear ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))
                 year.semesters?.forEach { sem ->
                     sem.subjects?.forEach { sub ->
                         OutlinedCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = ExpressiveCardShape) {
                             ListItem(
                                 headlineContent = { Text(sub.subjectName ?: "") },
                                 trailingContent = { Text("${sub.markList?.total?.toInt()}", fontWeight = FontWeight.Bold) }
                             )
                         }
                     }
                 }
             }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceView(vm: MainViewModel, onClose: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Reference") }, navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
             OutlinedCard(Modifier.fillMaxWidth(), shape = ExpressiveCardShape) {
                 Column(Modifier.padding(24.dp)) {
                     OshSuLogo(Modifier.height(60.dp).align(Alignment.CenterHorizontally))
                     Spacer(Modifier.height(16.dp))
                     Text("Certificate of Study", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                     HorizontalDivider(Modifier.padding(vertical = 24.dp))
                     Text("Certifies that ${vm.userData?.last_name} ${vm.userData?.name} is a student.", style = MaterialTheme.typography.bodyLarge)
                     // ... details
                 }
             }
             Spacer(Modifier.height(16.dp))
             Button(onClick = { vm.generateReferencePdf(LocalContext.current, "ru") }, modifier = Modifier.fillMaxWidth(), shape = ExpressiveButtonShape) {
                 Text("Download PDF")
             }
        }
    }
}

@Composable
fun ClassDetailsSheet(vm: MainViewModel, item: ScheduleItem) {
    Column(Modifier.padding(24.dp).fillMaxWidth()) {
        Text(item.subject?.get() ?: "", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row {
            SuggestionChip(onClick = {}, label = { Text(item.subject_type?.get() ?: "") })
            Spacer(Modifier.width(8.dp))
            SuggestionChip(onClick = {}, label = { Text(item.room?.name_en ?: "") })
        }
        Spacer(Modifier.height(24.dp))
        // ... Details
        Text("Teacher: ${item.teacher?.get()}", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(48.dp))
    }
}