package kg.oshsu.myedu.ui.screens

import android.content.ClipData
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.R
import kg.oshsu.myedu.ui.components.OshSuLogo
import kg.oshsu.myedu.TranscriptYear
import kg.oshsu.myedu.TranscriptSemester
import kg.oshsu.myedu.TranscriptSubject
import kg.oshsu.myedu.MarkList
import kg.oshsu.myedu.ExamRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PdfUiState {
    IDLE, LOADING, SUCCESS, ERROR
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ReferenceView(
    vm: MainViewModel, 
    onClose: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current // Updated to LocalClipboard
    val scope = rememberCoroutineScope() // Needed for suspend clipboard calls
    val user = vm.userData
    val profile = vm.profileData
    val mov = profile?.studentMovement
    
    // Get current language for data localization
    val currentLang = vm.language

    DisposableEffect(Unit) {
        onDispose { vm.resetDocumentState() }
    }
    
    val activeSemester = profile?.active_semester ?: 1
    val course = (activeSemester + 1) / 2
    
    val facultyName = mov?.faculty?.getName(currentLang) 
        ?: mov?.speciality?.faculty?.getName(currentLang) 
        ?: "-"
        
    val datePattern = stringResource(R.string.config_date_format)

    val progressAnim = remember { Animatable(0f) }
    LaunchedEffect(vm.pdfProgress) {
        progressAnim.animateTo(
            targetValue = vm.pdfProgress,
            animationSpec = tween(1000, easing = LinearOutSlowInEasing)
        )
    }

    val currentState = when {
        vm.savedPdfUri != null -> PdfUiState.SUCCESS
        vm.isPdfGenerating -> PdfUiState.LOADING
        vm.pdfStatusMessage != null -> PdfUiState.ERROR
        else -> PdfUiState.IDLE
    }

    with(sharedTransitionScope) {
        Scaffold(
            modifier = Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "reference_card"),
                animatedVisibilityScope = animatedVisibilityScope
            ),
            topBar = { 
                TopAppBar(
                    title = { 
                        Column {
                            Text(
                                stringResource(R.string.reference),
                                modifier = Modifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "text_reference"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
                                )
                            )
                            AnimatedVisibility(
                                visible = !vm.isPdfGenerating,
                                enter = fadeIn(tween(delayMillis = 300))
                            ) {
                                Text("(Form 8)", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }, 
                    navigationIcon = { IconButton(onClick = { onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
                ) 
            },
            bottomBar = {
                Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Column(Modifier.padding(16.dp)) {
                        AnimatedContent(
                            targetState = currentState,
                            label = "pdf_state_morph",
                            transitionSpec = {
                                fadeIn(tween(300)) + scaleIn(initialScale = 0.95f, animationSpec = tween(300)) togetherWith 
                                fadeOut(tween(300))
                            }
                        ) { state ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                when (state) {
                                    PdfUiState.IDLE -> {
                                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Button(onClick = { vm.generateReferencePdf(context, "ru") }, modifier = Modifier.weight(1f)) { 
                                                Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp))
                                                Text(stringResource(R.string.pdf_ru)) 
                                            }
                                            Button(onClick = { vm.generateReferencePdf(context, "en") }, modifier = Modifier.weight(1f)) { 
                                                Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp))
                                                Text(stringResource(R.string.pdf_en)) 
                                            }
                                        }
                                    }
                                    PdfUiState.LOADING -> {
                                        LinearWavyProgressIndicator(
                                            progress = { progressAnim.value },
                                            modifier = Modifier.fillMaxWidth().height(10.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.secondaryContainer
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            text = vm.pdfStatusMessage ?: stringResource(R.string.generating_pdf),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        OutlinedButton(
                                            onClick = { vm.resetDocumentState() },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Icon(Icons.Default.Close, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(android.R.string.cancel))
                                        }
                                    }
                                    PdfUiState.SUCCESS -> {
                                        Button(
                                            onClick = { vm.openPdf(context, vm.savedPdfUri!!) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.PictureAsPdf, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.open_pdf))
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(R.string.saved_to_downloads, vm.savedPdfName ?: ""),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    PdfUiState.ERROR -> {
                                        Text(
                                            text = vm.pdfStatusMessage ?: stringResource(R.string.error_unknown),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            // FIX: Pre-fetch string resource outside the lambda
                                            val noErrorMsg = stringResource(R.string.no_error_msg)
                                            val errorMessage = vm.pdfStatusMessage ?: noErrorMsg
                                            
                                            OutlinedButton(
                                                onClick = { 
                                                    scope.launch {
                                                        val clipData = ClipData.newPlainText("Error Logs", errorMessage)
                                                        clipboard.setClipEntry(ClipEntry(clipData))
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, null)
                                                Spacer(Modifier.width(8.dp))
                                                Text(stringResource(R.string.copy_logs))
                                            }
                                            Button(
                                                onClick = { vm.resetDocumentState() }, 
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Refresh, null)
                                                Spacer(Modifier.width(8.dp))
                                                Text(stringResource(R.string.retry))
                                            }
                                        }
                                    }
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
                            RefDetailRow(stringResource(R.string.speciality), mov?.speciality?.getName(currentLang) ?: "-")
                            RefDetailRow(stringResource(R.string.year_of_study), "$course ($activeSemester ${stringResource(R.string.semester)})")
                            RefDetailRow(stringResource(R.string.edu_form), mov?.edu_form?.getName(currentLang) ?: "-")
                            
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
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun TranscriptView(
    vm: MainViewModel, 
    onClose: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current // Updated
    val scope = rememberCoroutineScope() // Added
    val isTransitionComplete = remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        vm.fetchTranscript(forceRefresh = true)
        delay(500)
        isTransitionComplete.value = true
    }

    DisposableEffect(Unit) {
        onDispose { vm.resetDocumentState() }
    }

    val progressAnim = remember { Animatable(0f) }
    LaunchedEffect(vm.pdfProgress) {
        progressAnim.animateTo(targetValue = vm.pdfProgress, animationSpec = tween(1000, easing = LinearOutSlowInEasing))
    }

    val currentState = when {
        vm.savedPdfUri != null -> PdfUiState.SUCCESS
        vm.isPdfGenerating -> PdfUiState.LOADING
        vm.pdfStatusMessage != null -> PdfUiState.ERROR
        else -> PdfUiState.IDLE
    }

    val isDataLoading = vm.isTranscriptLoading || !isTransitionComplete.value

    with(sharedTransitionScope) {
        Scaffold(
            modifier = Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "transcript_card"),
                animatedVisibilityScope = animatedVisibilityScope
            ),
            topBar = { 
                TopAppBar(
                    title = { 
                        Text(
                            stringResource(R.string.transcript_title),
                            modifier = Modifier.sharedBounds(
                                sharedContentState = rememberSharedContentState(key = "text_transcript"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
                            )
                        ) 
                    }, 
                    navigationIcon = { IconButton(onClick = { onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
                ) 
            },
            bottomBar = {
                if (!isDataLoading && (vm.transcriptData.isNotEmpty() || currentState != PdfUiState.IDLE)) {
                    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                        Column(Modifier.padding(16.dp)) {
                            AnimatedContent(
                                targetState = currentState,
                                label = "pdf_state_morph",
                                transitionSpec = {
                                    fadeIn(tween(300)) + scaleIn(initialScale = 0.95f, animationSpec = tween(300)) togetherWith 
                                    fadeOut(tween(300))
                                }
                            ) { state ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    when (state) {
                                        PdfUiState.IDLE -> {
                                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                Button(onClick = { vm.generateTranscriptPdf(context, "ru") }, modifier = Modifier.weight(1f)) { 
                                                    Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp))
                                                    Text(stringResource(R.string.pdf_ru)) 
                                                }
                                                Button(onClick = { vm.generateTranscriptPdf(context, "en") }, modifier = Modifier.weight(1f)) { 
                                                    Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp))
                                                    Text(stringResource(R.string.pdf_en)) 
                                                }
                                            }
                                        }
                                        PdfUiState.LOADING -> {
                                            LinearWavyProgressIndicator(
                                                progress = { progressAnim.value }, 
                                                modifier = Modifier.fillMaxWidth().height(10.dp),
                                                color = MaterialTheme.colorScheme.primary, // Monet Primary
                                                trackColor = MaterialTheme.colorScheme.secondaryContainer // Monet Track
                                            )
                                            Spacer(Modifier.height(12.dp))
                                            Text(text = vm.pdfStatusMessage ?: stringResource(R.string.generating_pdf), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.height(12.dp))
                                            OutlinedButton(onClick = { vm.resetDocumentState() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                                Icon(Icons.Default.Close, null); Spacer(Modifier.width(8.dp)); Text(stringResource(android.R.string.cancel))
                                            }
                                        }
                                        PdfUiState.SUCCESS -> {
                                            Button(onClick = { vm.openPdf(context, vm.savedPdfUri!!) }, modifier = Modifier.fillMaxWidth()) {
                                                Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.open_pdf))
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(text = stringResource(R.string.saved_to_downloads, vm.savedPdfName ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
                                        }
                                        PdfUiState.ERROR -> {
                                            Text(text = vm.pdfStatusMessage ?: stringResource(R.string.error_unknown), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 12.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                // FIX: Pre-fetch string resource outside the lambda
                                                val noErrorMsg = stringResource(R.string.no_error_msg)
                                                val errorMessage = vm.pdfStatusMessage ?: noErrorMsg
                                                
                                                OutlinedButton(onClick = { 
                                                    scope.launch {
                                                        val clipData = ClipData.newPlainText("Error Logs", errorMessage)
                                                        clipboard.setClipEntry(ClipEntry(clipData))
                                                    }
                                                }, modifier = Modifier.weight(1f)) {
                                                    Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.copy_logs))
                                                }
                                                Button(onClick = { vm.resetDocumentState() }, modifier = Modifier.weight(1f)) {
                                                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.retry))
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
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                if (isDataLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(modifier = Modifier.size(48.dp)) }
                } else if (vm.transcriptData.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_transcript_data)) }
                } else {
                    AnimatedVisibility(visible = true, enter = fadeIn(animationSpec = tween(500))) {
                        LazyColumn(Modifier.widthIn(max = 840.dp).padding(horizontal = 16.dp)) {
                            vm.transcriptData.forEach { yearData ->
                                item { 
                                    Spacer(Modifier.height(16.dp))
                                    Text(yearData.eduYear ?: stringResource(R.string.unknown_year), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) 
                                }
                                yearData.semesters?.forEach { sem ->
                                    item { 
                                        Spacer(Modifier.height(12.dp))
                                        Text(sem.semesterName ?: stringResource(R.string.semester), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                                        Spacer(Modifier.height(8.dp)) 
                                    }
                                    items(sem.subjects ?: emptyList()) { sub ->
                                        Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f)) { 
                                                    Text(sub.subjectName ?: stringResource(R.string.subject_default), fontWeight = FontWeight.SemiBold)
                                                    val codeLabel = stringResource(R.string.code); val creditsLabel = stringResource(R.string.credits)
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
            }
        }
    }
}

// --- ADDED HELPER ---
@Composable
fun RefDetailRow(label: String, value: String) { 
    Column(Modifier.padding(bottom = 16.dp)) { 
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge) 
    } 
}