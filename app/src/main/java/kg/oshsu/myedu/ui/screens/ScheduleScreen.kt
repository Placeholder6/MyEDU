package kg.oshsu.myedu.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.ScheduleItem
import kg.oshsu.myedu.ui.components.*
import kotlinx.coroutines.launch
import java.util.Calendar
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScheduleScreen(vm: MainViewModel) {
    val tabs = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val scope = rememberCoroutineScope()
    val initialPage = remember { val cal = Calendar.getInstance(); val dow = cal.get(Calendar.DAY_OF_WEEK); if (dow == Calendar.SUNDAY) 0 else (dow - 2).coerceIn(0, 5) }
    val pagerState = rememberPagerState(initialPage = initialPage) { tabs.size }
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { OshSuLogo(modifier = Modifier.width(100.dp).height(40.dp)) }) }) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(pagerState.currentPage),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }
    }
}

@Composable
fun ClassDetailsSheet(vm: MainViewModel, item: ScheduleItem) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val groupLabel = if (item.subject_type?.get() == "Lecture") "Stream" else "Group"
    val groupValue = item.stream?.numeric?.toString() ?: "?"
    val scope = rememberCoroutineScope()
    
    val timeString = vm.getTimeString(item.id_lesson)

    // Grades Logic
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
                    IconButton(onClick = { 
                        scope.launch { 
                            clipboard.setText(AnnotatedString(item.teacher?.get() ?: "")) 
                        }
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show() }) { 
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
                            val address = item.classroom?.building?.getAddress()
                            val displayAddress = if (address.isNullOrBlank()) "Building" else address
                            
                            Text(displayAddress, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            Text(item.classroom?.building?.getName() ?: "Campus", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) 
                        }
                        IconButton(onClick = { 
                            scope.launch {
                                clipboard.setText(AnnotatedString(item.classroom?.building?.getName() ?: ""))
                            }
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show() 
                        }) { 
                            Icon(Icons.Default.ContentCopy, "Copy", tint = MaterialTheme.colorScheme.outline) 
                        }
                        IconButton(onClick = { 
                            val locationName = item.classroom?.building?.getName() ?: ""
                            if (locationName.isNotEmpty()) { 
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(locationName)}"))
                                try { context.startActivity(intent) } catch (e: Exception) { Toast.makeText(context, "No map app found", Toast.LENGTH_SHORT).show() }
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