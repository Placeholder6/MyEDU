package kg.oshsu.myedu.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.R
import kg.oshsu.myedu.ui.components.OshSuLogo
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- REFERENCE VIEW ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReferenceView(vm: MainViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val user = vm.userData
    val profile = vm.profileData
    val mov = profile?.studentMovement
    
    // Cleanup on Exit
    DisposableEffect(Unit) {
        onDispose {
            vm.resetDocumentState()
        }
    }
    
    val activeSemester = profile?.active_semester ?: 1
    val course = (activeSemester + 1) / 2
    val facultyName = mov?.faculty?.let { it.name_en ?: it.name_ru } ?: mov?.speciality?.faculty?.let { it.name_en ?: it.name_ru } ?: "-"
    
    val datePattern = stringResource(R.string.config_date_format)

    // Animation State (Hoisted to persist across logic switches)
    val progressAnim = remember { Animatable(0f) }
    
    LaunchedEffect(vm.pdfProgress) {
        progressAnim.animateTo(
            targetValue = vm.pdfProgress,
            animationSpec = tween(1000, easing = LinearOutSlowInEasing)
        )
    }

    // Logic: Keep loading visible if generating OR (finished but animation hasn't caught up)
    val isLoadingVisible = vm.isPdfGenerating || (vm.savedPdfUri != null && progressAnim.value < 0.99f)

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text(stringResource(R.string.reference_title)) }, 
                navigationIcon = { 
                    IconButton(onClick = { onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } 
                }
            ) 
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Column(Modifier.padding(16.dp)) {
                    if (isLoadingVisible) {
                         // LOADING STATE
                         Column(
                             Modifier.fillMaxWidth().padding(vertical = 4.dp), 
                             horizontalAlignment = Alignment.CenterHorizontally
                         ) {
                             LinearWavyProgressIndicator(
                                 progress = { progressAnim.value },
                                 modifier = Modifier.fillMaxWidth().height(10.dp)
                             )
                             Spacer(Modifier.height(8.dp))
                             Text(
                                 text = vm.pdfStatusMessage ?: stringResource(R.string.generating_pdf),
                                 style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.primary
                             )
                         }
                    } else if (vm.savedPdfUri != null) {
                        // SUCCESS STATE
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { vm.openPdf(context, vm.savedPdfUri!!) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.PictureAsPdf, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Open PDF")
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Saved to Downloads as ${vm.savedPdfName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // DEFAULT STATE
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(onClick = { vm.generateReferencePdf(context, "ru") }, modifier = Modifier.weight(1f)) { 
                                Icon(Icons.Default.Download, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.pdf_ru)) 
                            }
                            Button(onClick = { vm.generateReferencePdf(context, "en") }, modifier = Modifier.weight(1f)) { 
                                Icon(Icons.Default.Download, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.pdf_en)) 
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { 
                            OshSuLogo(modifier = Modifier.width(180.dp).height(60.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.cert_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) 
                        }
                        Spacer(Modifier.height(24.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(24.dp))
                        
                        Text(stringResource(R.string.cert_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        Text("${user?.last_name} ${user?.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        
                        Spacer(Modifier.height(24.dp))
                        
                        RefDetailRow(stringResource(R.string.student_id), "${user?.id}")
                        RefDetailRow(stringResource(R.string.faculty), facultyName)
                        RefDetailRow(stringResource(R.string.speciality), mov?.speciality?.name_en ?: "-")
                        RefDetailRow(stringResource(R.string.year_of_study), "$course ($activeSemester ${stringResource(R.string.semester)})")
                        RefDetailRow(stringResource(R.string.edu_form), mov?.edu_form?.name_en ?: "-")
                        
                        val contractLabel = stringResource(R.string.contract)
                        val budgetLabel = stringResource(R.string.budget)
                        RefDetailRow(stringResource(R.string.payment), if (mov?.id_payment_form == 2) contractLabel else budgetLabel)
                        
                        Spacer(Modifier.height(32.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) { 
                            Icon(Icons.Default.Verified, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${stringResource(R.string.active_student)} • ${SimpleDateFormat(datePattern, Locale.getDefault()).format(Date())}", 
                                style = MaterialTheme.typography.labelMedium, 
                                color = Color(0xFF4CAF50)
                            ) 
                        }
                    }
                }
                // Status/Error Message (Persists outside loading view)
                if (vm.pdfStatusMessage != null && !isLoadingVisible) {
                    Spacer(Modifier.height(16.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface)) {
                        Text(vm.pdfStatusMessage!!, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(16.dp))
                    }
                }
                Spacer(Modifier.height(20.dp))
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

// --- TRANSCRIPT VIEW ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TranscriptView(vm: MainViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    
    // UI State: Simulate transition delay
    val isTransitionComplete = remember { mutableStateOf(false) }
    
    // Effect 1: Start Fetch and Transition Wait
    LaunchedEffect(Unit) {
        vm.fetchTranscript(forceRefresh = true) // Background fetch
        delay(500) // Transition simulation
        isTransitionComplete.value = true
    }

    // Effect 2: Cleanup on Exit
    DisposableEffect(Unit) {
        onDispose {
            vm.resetDocumentState()
        }
    }

    // Animation State for PDF Generation
    val progressAnim = remember { Animatable(0f) }
    LaunchedEffect(vm.pdfProgress) {
        progressAnim.animateTo(
            targetValue = vm.pdfProgress,
            animationSpec = tween(1000, easing = LinearOutSlowInEasing)
        )
    }

    // Logic: Keep PDF loading visible if generating OR (finished but animation hasn't caught up)
    val isPdfLoadingVisible = vm.isPdfGenerating || (vm.savedPdfUri != null && progressAnim.value < 0.99f)

    // Data Loading State: True if (VM says loading) OR (Transition not done)
    val isDataLoading = vm.isTranscriptLoading || !isTransitionComplete.value

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text(stringResource(R.string.transcript_title)) }, 
                navigationIcon = { 
                    IconButton(onClick = { onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } 
                }
            ) 
        },
        bottomBar = {
            // Show bottom bar only if we have data or are in success state
            // Hide during initial data load to avoid clutter
            if (!isDataLoading && (vm.transcriptData.isNotEmpty() || vm.savedPdfUri != null)) {
                Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Column(Modifier.padding(16.dp)) {
                         if (isPdfLoadingVisible) {
                             // PDF LOADING STATE
                             Column(
                                 Modifier.fillMaxWidth().padding(vertical = 4.dp), 
                                 horizontalAlignment = Alignment.CenterHorizontally
                             ) {
                                 LinearWavyProgressIndicator(
                                     progress = { progressAnim.value },
                                     modifier = Modifier.fillMaxWidth().height(10.dp)
                                 )
                                 Spacer(Modifier.height(8.dp))
                                 Text(
                                     text = vm.pdfStatusMessage ?: stringResource(R.string.generating_pdf),
                                     style = MaterialTheme.typography.bodySmall,
                                     color = MaterialTheme.colorScheme.primary
                                 )
                             }
                         } else if (vm.savedPdfUri != null) {
                             // SUCCESS STATE
                             Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                 Button(
                                     onClick = { vm.openPdf(context, vm.savedPdfUri!!) },
                                     modifier = Modifier.fillMaxWidth()
                                 ) {
                                     Icon(Icons.Default.PictureAsPdf, null)
                                     Spacer(Modifier.width(8.dp))
                                     Text("Open PDF")
                                 }
                                 Spacer(Modifier.height(4.dp))
                                 Text(
                                     text = "Saved to Downloads as ${vm.savedPdfName}",
                                     style = MaterialTheme.typography.bodySmall,
                                     color = MaterialTheme.colorScheme.outline,
                                     textAlign = TextAlign.Center
                                 )
                             }
                         } else {
                             // DEFAULT STATE (Download Buttons)
                             Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Button(onClick = { vm.generateTranscriptPdf(context, "ru") }, modifier = Modifier.weight(1f)) { 
                                    Icon(Icons.Default.Download, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.pdf_ru)) 
                                }
                                Button(onClick = { vm.generateTranscriptPdf(context, "en") }, modifier = Modifier.weight(1f)) { 
                                    Icon(Icons.Default.Download, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.pdf_en)) 
                                }
                            }
                         }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            
            // 1. LOADING STATE
            if (isDataLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator(
                        modifier = Modifier.size(48.dp) // Material 3 Expressive
                    ) 
                }
            } 
            // 2. EMPTY STATE (Only check after transition & loading are done)
            else if (vm.transcriptData.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                    Text(stringResource(R.string.no_transcript_data)) 
                }
            } 
            // 3. CONTENT STATE
            else {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(500))
                ) {
                    LazyColumn(Modifier.widthIn(max = 840.dp).padding(horizontal = 16.dp)) {
                        vm.transcriptData.forEach { yearData ->
                            item { 
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    yearData.eduYear ?: stringResource(R.string.unknown_year), 
                                    style = MaterialTheme.typography.headlineSmall, 
                                    fontWeight = FontWeight.Bold, 
                                    color = MaterialTheme.colorScheme.primary
                                ) 
                            }
                            yearData.semesters?.forEach { sem ->
                                item { 
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        sem.semesterName ?: stringResource(R.string.semester), 
                                        style = MaterialTheme.typography.titleMedium, 
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(Modifier.height(8.dp)) 
                                }
                                items(sem.subjects ?: emptyList()) { sub ->
                                    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) { 
                                                Text(sub.subjectName ?: stringResource(R.string.subject_default), fontWeight = FontWeight.SemiBold)
                                                val codeLabel = stringResource(R.string.code)
                                                val creditsLabel = stringResource(R.string.credits)
                                                Text("$codeLabel: ${sub.code ?: "-"} • $creditsLabel: ${sub.credit?.toInt() ?: 0}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) 
                                            }
                                            Column(horizontalAlignment = Alignment.End) { 
                                                val total = sub.markList?.total?.toInt() ?: 0
                                                Text("$total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (total >= 50) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
                                                Text(sub.examRule?.alphabetic ?: "-", style = MaterialTheme.typography.bodyMedium) 
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(20.dp)) }
                    }
                }
            }
            
            // Status/Error Message (Persists outside loading view, e.g. upload failure)
            if (vm.pdfStatusMessage != null && !isPdfLoadingVisible && !isDataLoading) {
                Card(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface)) { 
                    Text(vm.pdfStatusMessage!!, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(16.dp)) 
                }
            }
        }
    }
}