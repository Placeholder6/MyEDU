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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material.icons.Icons
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
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.decode.SvgDecoder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// --- UI COMPONENT: LOGO ---
@Composable
fun OshSuLogo(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary // Default to Monet Primary Color
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
        colorFilter = ColorFilter.tint(tint) // Apply the dynamic tint
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
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.fillMaxSize().widthIn(max = 600.dp).padding(24.dp).systemBarsPadding(), 
                verticalArrangement = Arrangement.Center, 
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OshSuLogo(modifier = Modifier.width(260.dp).height(100.dp))
                Spacer(Modifier.height(48.dp))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                if (vm.errorMsg != null) { Spacer(Modifier.height(16.dp)); Text(vm.errorMsg!!, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(32.dp))
                Button(onClick = { vm.login(email, pass) }, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = !vm.isLoading) { if (vm.isLoading) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary) else Text("Sign In") }
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

    // ROOT BOX: Enables Z-index layering.
    Box(modifier = Modifier.fillMaxSize()) {
        
        // LAYER 1: The Main Scaffold (Home, Schedule, etc.)
        // This is always rendered, so it stays visible in the background during animations.
        Scaffold(
            bottomBar = {
                // NavigationBar is always rendered here to prevent layout jumps.
                // It will be covered by the full-screen overlays below.
                NavigationBar {
                    NavigationBarItem(icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") }, selected = vm.currentTab == 0, onClick = { vm.currentTab = 0 })
                    NavigationBarItem(icon = { Icon(Icons.Default.DateRange, null) }, label = { Text("Schedule") }, selected = vm.currentTab == 1, onClick = { vm.currentTab = 1 })
                    NavigationBarItem(icon = { Icon(Icons.Default.Description, null) }, label = { Text("Grades") }, selected = vm.currentTab == 2, onClick = { vm.currentTab = 2 })
                    NavigationBarItem(icon = { Icon(Icons.Default.Person, null) }, label = { Text("Profile") }, selected = vm.currentTab == 3, onClick = { vm.currentTab = 3 })
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                 // Content is always rendered.
                 when(vm.currentTab) {
                    0 -> HomeScreen(vm)
                    1 -> ScheduleScreen(vm)
                    2 -> GradesScreen(vm)
                    3 -> ProfileScreen(vm)
                }
            }
        }

        // LAYER 2: Full Screen Overlays (Transcript / Reference)
        // Placing these AFTER the Scaffold in the Box ensures they draw ON TOP.
        // Because they fill max size, they will cover the Scaffold and the Bottom Bar.
        
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
        
        // LAYER 3: Bottom Sheet Popup (Class Details)
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

// --- SCREEN: REFERENCE VIEW ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceView(vm: MainViewModel, onClose: () -> Unit) {
    val context = LocalContext.current; val user = vm.userData; val profile = vm.profileData; val mov = profile?.studentMovement
    val activeSemester = profile?.active_semester ?: 1; val course = (activeSemester + 1) / 2
    
    // Logic: Correct Faculty Name Extraction
    val facultyName = mov?.faculty?.let { it.name_en ?: it.name_ru } 
        ?: mov?.speciality?.faculty?.let { it.name_en ?: it.name_ru } 
        ?: "-"
    
    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Reference (Form 8)") }, 
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, null) } }
            ) 
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Column(Modifier.padding(16.dp)) {
                    if (vm.isPdfGenerating) {
                         Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                             CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                             Spacer(Modifier.width(16.dp))
                             Text("Generating PDF...", color = MaterialTheme.colorScheme.primary)
                         }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(onClick = { vm.generateReferencePdf(context, "ru") }, modifier = Modifier.weight(1f)) { 
                                Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("PDF (RU)") 
                            }
                            Button(onClick = { vm.generateReferencePdf(context, "en") }, modifier = Modifier.weight(1f)) { 
                                Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("PDF (EN)") 
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.fillMaxSize().widthIn(max = 840.dp).verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(24.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { OshSuLogo(modifier = Modifier.width(180.dp).height(60.dp)); Spacer(Modifier.height(16.dp)); Text("CERTIFICATE OF STUDY", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
                        Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(24.dp))
                        Text("This is to certify that", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline); Text("${user?.last_name} ${user?.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(24.dp))
                        RefDetailRow("Student ID", "${user?.id}"); 
                        RefDetailRow("Faculty", facultyName); 
                        RefDetailRow("Speciality", mov?.speciality?.name_en ?: "-"); 
                        RefDetailRow("Year of Study", "$course ($activeSemester Semester)"); 
                        RefDetailRow("Education Form", mov?.edu_form?.name_en ?: "-"); 
                        RefDetailRow("Payment", if (mov?.id_payment_form == 2) "Contract" else "Budget")
                        Spacer(Modifier.height(32.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Verified, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Active Student • ${SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date())}", style = MaterialTheme.typography.labelMedium, color = Color(0xFF4CAF50)) }
                    }
                }
                
                if (vm.pdfStatusMessage != null) { Spacer(Modifier.height(16.dp)); Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface)) { Text(vm.pdfStatusMessage!!, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(16.dp)) } }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun RefDetailRow(label: String, value: String) { Column(Modifier.padding(bottom = 16.dp)) { Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary); Text(value, style = MaterialTheme.typography.bodyLarge) } }

// --- SCREEN: TRANSCRIPT VIEW ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptView(vm: MainViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Full Transcript") }, 
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, null) } }
            ) 
        },
        bottomBar = {
            if (vm.transcriptData.isNotEmpty()) {
                Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Column(Modifier.padding(16.dp)) {
                         if (vm.isPdfGenerating) {
                             Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                 CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                 Spacer(Modifier.width(16.dp))
                                 Text("Generating PDF...", color = MaterialTheme.colorScheme.primary)
                             }
                         } else {
                             Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Button(onClick = { vm.generateTranscriptPdf(context, "ru") }, modifier = Modifier.weight(1f)) { 
                                    Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("PDF (RU)") 
                                }
                                Button(onClick = { vm.generateTranscriptPdf(context, "en") }, modifier = Modifier.weight(1f)) { 
                                    Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("PDF (EN)") 
                                }
                            }
                         }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (vm.isTranscriptLoading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else if (vm.transcriptData.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No transcript data.") }
            else {
                LazyColumn(Modifier.widthIn(max = 840.dp).padding(horizontal = 16.dp)) {
                    vm.transcriptData.forEach { yearData ->
                        item { Spacer(Modifier.height(16.dp)); Text(yearData.eduYear ?: "Unknown Year", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                        yearData.semesters?.forEach { sem ->
                            item { Spacer(Modifier.height(12.dp)); Text(sem.semesterName ?: "Semester", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary); Spacer(Modifier.height(8.dp)) }
                            items(sem.subjects ?: emptyList()) { sub ->
                                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) { Text(sub.subjectName ?: "Subject", fontWeight = FontWeight.SemiBold); Text("Code: ${sub.code ?: "-"} • Credits: ${sub.credit?.toInt() ?: 0}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
                                        Column(horizontalAlignment = Alignment.End) { val total = sub.markList?.total?.toInt() ?: 0; Text("$total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (total >= 50) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error); Text(sub.examRule?.alphabetic ?: "-", style = MaterialTheme.typography.bodyMedium) }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
            if (vm.pdfStatusMessage != null) Card(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface)) { Text(vm.pdfStatusMessage!!, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(16.dp)) }
        }
    }
}

// --- SCREEN: PROFILE TAB ---
@Composable
fun ProfileScreen(vm: MainViewModel) {
    val user = vm.userData; val profile = vm.profileData; val pay = vm.payStatus
    val fullName = "${user?.last_name ?: ""} ${user?.name ?: ""}".trim().ifEmpty { "Student" }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Logic: Correct Faculty Name Extraction for Profile
    val facultyName = profile?.studentMovement?.faculty?.let { it.name_en ?: it.name_ru } 
        ?: profile?.studentMovement?.speciality?.faculty?.let { it.name_en ?: it.name_ru } 
        ?: "-"

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().widthIn(max = 840.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            // Settings Button
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                 IconButton(onClick = { showSettingsDialog = true }) { Icon(Icons.Default.Settings, "Settings") }
            }
            // Profile Image
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(128.dp).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)), CircleShape).padding(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background)) { AsyncImage(model = profile?.avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape)) }
            Spacer(Modifier.height(16.dp)); Text(fullName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(user?.email ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            
            // Payment Status Section
            Spacer(Modifier.height(24.dp))
            if (pay != null) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Tuition Contract", fontWeight = FontWeight.Bold); Icon(Icons.Outlined.Payments, null, tint = MaterialTheme.colorScheme.primary) }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text("Paid", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline); Text("${pay.paid_summa?.toInt() ?: 0} KGS", style = MaterialTheme.typography.titleMedium, color = Color(0xFF4CAF50)) }; Column(horizontalAlignment = Alignment.End) { Text("Total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline); Text("${pay.need_summa?.toInt() ?: 0} KGS", style = MaterialTheme.typography.titleMedium) } }
                        val debt = pay.getDebt(); if (debt > 0) { Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp)); Text("Remaining: ${debt.toInt()} KGS", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            
            // Document Buttons
            InfoSection("Documents")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { vm.showReferenceScreen = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Icon(Icons.Default.Description, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Reference") }
                Button(onClick = { vm.fetchTranscript() }, modifier = Modifier.weight(1f), enabled = !vm.isTranscriptLoading) { if (vm.isTranscriptLoading) CircularProgressIndicator(Modifier.size(18.dp), color=MaterialTheme.colorScheme.onPrimary, strokeWidth=2.dp) else Icon(Icons.Default.School, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Transcript") }
            }

            Spacer(Modifier.height(24.dp)); InfoSection("Personal Details")
            DetailCard(Icons.Outlined.School, "Faculty", facultyName)
            DetailCard(Icons.Outlined.Book, "Speciality", profile?.studentMovement?.speciality?.name_en ?: "-")
            Spacer(Modifier.height(32.dp)); Button(onClick = { vm.logout() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer), modifier = Modifier.fillMaxWidth()) { Text("Log Out") }; Spacer(Modifier.height(80.dp))
        }
    }

    // Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Settings") },
            text = {
                Column {
                    Text("Dictionary URL")
                    OutlinedTextField(
                        value = vm.dictionaryUrl, 
                        onValueChange = { vm.dictionaryUrl = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showSettingsDialog = false }) { Text("Done") } }
        )
    }
}

// --- SCREEN: HOME TAB ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    val user = vm.userData; val profile = vm.profileData; var showNewsSheet by remember { mutableStateOf(false) }
    val currentHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greetingText = remember(currentHour) { if(currentHour in 4..11) "Good Morning," else if(currentHour in 12..16) "Good Afternoon," else "Good Evening," }
    
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().widthIn(max = 840.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) { Text(greetingText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary); Text(text = user?.name ?: "Student", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OshSuLogo(modifier = Modifier.width(100.dp).height(40.dp)); Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.size(40.dp).clickable { showNewsSheet = true }, contentAlignment = Alignment.Center) { if (vm.newsList.isNotEmpty()) { BadgedBox(badge = { Badge { Text("${vm.newsList.size}") } }) { Icon(Icons.Outlined.Notifications, contentDescription = "Announcements", tint = MaterialTheme.colorScheme.primary) } } else { Icon(Icons.Outlined.Notifications, contentDescription = "Announcements", tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { 
                StatCard(
                    icon = Icons.Outlined.CalendarToday, 
                    label = "Semester", 
                    value = profile?.active_semester?.toString() ?: "-", 
                    secondaryValue = vm.determinedStream?.let { "Stream $it" },
                    bg = MaterialTheme.colorScheme.primaryContainer, 
                    modifier = Modifier.weight(1f)
                )
                
                val groupNum = vm.determinedGroup?.toString()
                val groupName = profile?.studentMovement?.avn_group_name
                StatCard(
                    icon = Icons.Outlined.Groups, 
                    label = "Group", 
                    value = groupNum ?: groupName ?: "-", 
                    secondaryValue = if (groupNum != null) groupName else null, 
                    bg = MaterialTheme.colorScheme.secondaryContainer, 
                    modifier = Modifier.weight(1f)
                ) 
            }
            Spacer(Modifier.height(32.dp)); Text("${vm.todayDayName}'s Classes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(16.dp))
            if (vm.todayClasses.isEmpty()) { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Weekend, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(16.dp)); Text("No classes today!", style = MaterialTheme.typography.bodyLarge) } } } else { vm.todayClasses.forEach { item -> ClassItem(item, vm.getTimeString(item.id_lesson)) { vm.selectedClass = item } } }
            Spacer(Modifier.height(80.dp))
        }
    }    
    if (showNewsSheet) { ModalBottomSheet(onDismissRequest = { showNewsSheet = false }) { Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) { Text("Announcements", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); LazyColumn { items(vm.newsList) { news -> Card(Modifier.padding(top=8.dp).fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(news.title?:"", fontWeight=FontWeight.Bold); Text(news.message?:"") } } } } } } }
}

// --- SCREEN: SCHEDULE TAB ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScheduleScreen(vm: MainViewModel) {
    val tabs = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val scope = rememberCoroutineScope()
    val initialPage = remember { val cal = Calendar.getInstance(); val dow = cal.get(Calendar.DAY_OF_WEEK); if (dow == Calendar.SUNDAY) 0 else (dow - 2).coerceIn(0, 5) }
    val pagerState = rememberPagerState(initialPage = initialPage) { tabs.size }
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { OshSuLogo(modifier = Modifier.width(100.dp).height(40.dp)) }) }) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = pagerState.currentPage, containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary, indicator = { tabPositions -> if (pagerState.currentPage < tabPositions.size) { TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]), color = MaterialTheme.colorScheme.primary) } }) { tabs.forEachIndexed { index, title -> Tab(selected = pagerState.currentPage == index, onClick = { scope.launch { pagerState.animateScrollToPage(index) } }, text = { Text(title) }) } }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
                val dayClasses = vm.fullSchedule.filter { it.day == pageIndex }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { if (dayClasses.isEmpty()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.Weekend, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.surfaceVariant); Spacer(Modifier.height(16.dp)); Text("No classes", color = Color.Gray) } } } else { LazyColumn(modifier = Modifier.fillMaxSize().widthIn(max = 840.dp), contentPadding = PaddingValues(16.dp)) { items(dayClasses) { item -> ClassItem(item, vm.getTimeString(item.id_lesson)) { vm.selectedClass = item } }; item { Spacer(Modifier.height(80.dp)) } } } }
            }
        }
    }
}

// --- SHEET: CLASS DETAILS ---
@Composable
fun ClassDetailsSheet(vm: MainViewModel, item: ScheduleItem) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val groupLabel = if (item.subject_type?.get() == "Lecture") "Stream" else "Group"
    val groupValue = item.stream?.numeric?.toString() ?: "?"
    
    // FETCH TIME HERE
    val timeString = vm.getTimeString(item.id_lesson)

    // GRADES LOGIC
    val activeSemester = vm.profileData?.active_semester
    val session = vm.sessionData
    val currentSemSession = session.find { it.semester?.id == activeSemester } ?: session.lastOrNull()
    val subjectGrades = currentSemSession?.subjects?.find { 
        it.subject?.get() == item.subject?.get() 
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxWidth().widthIn(max = 840.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
            // Header
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { 
                Column(Modifier.padding(24.dp)) { 
                    Text(item.subject?.get() ?: "Subject", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(4.dp))
                    
                    // ADDED TIME ROW
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.8f), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(timeString, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.9f))
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { 
                        AssistChip(onClick = {}, label = { Text(item.subject_type?.get() ?: "Lesson") })
                        if (item.stream?.numeric != null) { 
                            AssistChip(onClick = {}, label = { Text("$groupLabel $groupValue") }, colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface)) 
                        } 
                    } 
                } 
            }
            
            // Grades Section
            Spacer(Modifier.height(24.dp))
            Text("Current Performance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                if (subjectGrades != null) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            ScoreColumn("M1", subjectGrades.marklist?.point1)
                            ScoreColumn("M2", subjectGrades.marklist?.point2)
                            ScoreColumn("Exam", subjectGrades.marklist?.finally)
                            ScoreColumn("Total", subjectGrades.marklist?.total, true)
                        }
                    }
                } else {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(16.dp))
                        Text("No grades available for this subject yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            // Teacher Section
            Spacer(Modifier.height(24.dp))
            Text("Teacher", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) { 
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { 
                    Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(16.dp))
                    Text(item.teacher?.get() ?: "Unknown", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { clipboardManager.setText(AnnotatedString(item.teacher?.get() ?: "")); Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show() }) { 
                        Icon(Icons.Default.ContentCopy, "Copy", tint = MaterialTheme.colorScheme.outline) 
                    } 
                } 
            }

            // Location Section
            Spacer(Modifier.height(24.dp))
            Text("Location", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) { 
                Column { 
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { 
                        Icon(Icons.Outlined.MeetingRoom, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) { 
                            Text("Room", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(item.room?.name_en ?: "Unknown", style = MaterialTheme.typography.bodyLarge) 
                        } 
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { 
                        Icon(Icons.Outlined.Business, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) { 
                            // ADDRESS FALLBACK LOGIC
                            val address = item.classroom?.building?.getAddress()
                            val displayAddress = if (address.isNullOrBlank()) "Building" else address
                            
                            Text(displayAddress, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            Text(item.classroom?.building?.getName() ?: "Campus", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) 
                        }
                        // Copy Button for Building
                        IconButton(onClick = { 
                            clipboardManager.setText(AnnotatedString(item.classroom?.building?.getName() ?: ""))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show() 
                        }) { 
                            Icon(Icons.Default.ContentCopy, "Copy", tint = MaterialTheme.colorScheme.outline) 
                        }
                        // Map Button using Building Name
                        IconButton(onClick = { 
                            val locationName = item.classroom?.building?.getName() ?: ""
                            if (locationName.isNotEmpty()) { 
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(locationName)}"))
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No map app found", Toast.LENGTH_SHORT).show()
                                }
                            } 
                        }) { 
                            Icon(Icons.Outlined.Map, "Map", tint = MaterialTheme.colorScheme.primary) 
                        } 
                    } 
                } 
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

// --- HELPER COMPONENTS ---
@Composable
fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    secondaryValue: String? = null,
    bg: Color,
    modifier: Modifier = Modifier
) {
    ElevatedButton(
        onClick = {},
        modifier = modifier,
        colors = ButtonDefaults.elevatedButtonColors(containerColor = bg),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Icon(icon, null, tint = Color.Black.copy(alpha=0.7f))
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.Black.copy(alpha=0.6f))
            Text(
                text = value,
                style = if(value.length > 8) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (secondaryValue != null) {
                Text(
                    text = secondaryValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ClassItem(item: ScheduleItem, timeString: String, onClick: () -> Unit) {
    val streamInfo = if (item.stream?.numeric != null) { val type = item.subject_type?.get(); if (type == "Lecture") "Stream ${item.stream.numeric}" else "Group ${item.stream.numeric}" } else ""
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(50.dp).background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp)).padding(vertical = 8.dp)) {
                Text("${item.id_lesson}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(timeString.split("-").firstOrNull()?.trim() ?: "", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.subject?.get() ?: "Subject", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val metaText = buildString { append(item.room?.name_en ?: "Room ?"); append(" • "); append(item.subject_type?.get() ?: "Lesson"); if (streamInfo.isNotEmpty()) { append(" • "); append(streamInfo) } }
                Text(metaText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Text(timeString, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun InfoSection(title: String) { Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, start = 4.dp)) }

@Composable
fun DetailCard(icon: ImageVector, title: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f))) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(16.dp))
            Column { Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline); Text(value, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

// --- SCREEN: GRADES TAB ---
@Composable
fun GradesScreen(vm: MainViewModel) {
    val session = vm.sessionData
    val activeSemId = vm.profileData?.active_semester
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (vm.isGradesLoading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else {
            LazyColumn(Modifier.fillMaxSize().widthIn(max = 840.dp).padding(16.dp)) {
                item { Spacer(Modifier.height(32.dp)); Text("Current Session", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(16.dp)) }
                if (session.isEmpty()) item { Text("No grades available.", color = Color.Gray) }
                else {
                    val currentSem = session.find { it.semester?.id == activeSemId } ?: session.lastOrNull()
                    if (currentSem != null) {
                        item { Text(currentSem.semester?.name_en ?: "", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(8.dp)) }
                        items(currentSem.subjects ?: emptyList()) { sub ->
                            Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(sub.subject?.get() ?: "Subject", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        ScoreColumn("M1", sub.marklist?.point1)
                                        ScoreColumn("M2", sub.marklist?.point2)
                                        ScoreColumn("Exam", sub.marklist?.finally)
                                        ScoreColumn("Total", sub.marklist?.total, true)
                                    }
                                }
                            }
                        }
                    } else item { Text("Semester data not found.", color = Color.Gray) }
                }
                item { Spacer(Modifier.height(24.dp)) } // REDUCED PADDING
            }
        }
    }
}

@Composable
fun ScoreColumn(label: String, score: Double?, isTotal: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(
            "${score?.toInt() ?: 0}", 
            style = MaterialTheme.typography.bodyLarge, 
            fontWeight = FontWeight.Bold, 
            color = if (isTotal && (score ?: 0.0) >= 50) Color(0xFF4CAF50) else if (isTotal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}