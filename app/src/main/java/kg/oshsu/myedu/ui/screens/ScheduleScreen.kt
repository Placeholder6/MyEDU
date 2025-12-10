package kg.oshsu.myedu.ui.screens

import android.content.ClipData
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.R
import kg.oshsu.myedu.ScheduleItem
import kg.oshsu.myedu.ui.components.*
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScheduleScreen(vm: MainViewModel) {
    val tabs = listOf(
        stringResource(R.string.mon), stringResource(R.string.tue), stringResource(R.string.wed),
        stringResource(R.string.thu), stringResource(R.string.fri), stringResource(R.string.sat)
    )
    
    val scope = rememberCoroutineScope()
    val initialPage = remember { 
        val cal = Calendar.getInstance()
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        if (dow == Calendar.SUNDAY) 0 else (dow - 2).coerceIn(0, 5) 
    }
    
    val pagerState = rememberPagerState(initialPage = initialPage) { tabs.size }
    val pullState = rememberPullToRefreshState()

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { OshSuLogo(modifier = Modifier.width(100.dp).height(40.dp)) }) }) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { TabRowDefaults.PrimaryIndicator(modifier = Modifier.tabIndicatorOffset(pagerState.currentPage), color = MaterialTheme.colorScheme.primary) },
                tabs = {
                    tabs.forEachIndexed { index, title -> 
                        Tab(selected = pagerState.currentPage == index, onClick = { scope.launch { pagerState.animateScrollToPage(index) } }, text = { Text(title) }) 
                    } 
                }
            )
            
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
                val dayClasses = vm.fullSchedule.filter { it.day == pageIndex }
                PullToRefreshBox(
                    isRefreshing = vm.isRefreshing, onRefresh = { vm.refresh() }, state = pullState,
                    indicator = { PullToRefreshDefaults.LoadingIndicator(state = pullState, isRefreshing = vm.isRefreshing, modifier = Modifier.align(Alignment.TopCenter)) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { 
                        if (dayClasses.isEmpty()) { 
                            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                                item {
                                    Box(Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) { 
                                            Icon(Icons.Outlined.Weekend, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.surfaceVariant)
                                            Spacer(Modifier.height(16.dp))
                                            Text(stringResource(R.string.no_classes), color = Color.Gray) 
                                        }
                                    }
                                }
                            }
                        } else { 
                            LazyColumn(modifier = Modifier.fillMaxSize().widthIn(max = 840.dp), contentPadding = PaddingValues(16.dp)) { 
                                items(dayClasses) { item -> ClassItem(item, vm.getTimeString(item.id_lesson)) { vm.selectedClass = item } }
                                item { Spacer(Modifier.height(80.dp)) } 
                            } 
                        } 
                    }
                }
            }
        }
    }
}

@Composable
fun ClassDetailsSheet(vm: MainViewModel, item: ScheduleItem) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // --- LOCALIZATION FIXES ---
    val typeLecture = stringResource(R.string.type_lecture)
    val labelStream = stringResource(R.string.stream)
    val labelGroup = stringResource(R.string.group)
    
    // Use helper to translate "Practical Class" -> "Практика", etc.
    val localizedSubjectType = getLocalizedSubjectType(item.subject_type?.get())
    
    // Logic: If localized type is Lecture, use Stream label, else Group
    val groupLabel = if (localizedSubjectType == typeLecture) labelStream else labelGroup
    val groupValue = item.stream?.numeric?.toString() ?: "?"
    
    val timeString = vm.getTimeString(item.id_lesson)

    val activeSemester = vm.profileData?.active_semester
    val session = vm.sessionData
    val currentSemSession = session.find { it.semester?.id == activeSemester } ?: session.lastOrNull()
    val subjectGrades = currentSemSession?.subjects?.find { it.subject?.get() == item.subject?.get() }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxWidth().widthIn(max = 840.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { 
                Column(Modifier.padding(24.dp)) { 
                    Text(item.subject?.get() ?: stringResource(R.string.subject_default), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(4.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.8f), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(timeString, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.9f))
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { 
                        // UPDATED: Use localized string in chip
                        AssistChip(onClick = {}, label = { Text(localizedSubjectType) })
                        
                        if (item.stream?.numeric != null) { 
                            AssistChip(onClick = {}, label = { Text("$groupLabel $groupValue") }, colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface)) 
                        } 
                    } 
                } 
            }
            
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.current_performance), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                if (subjectGrades != null) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            ScoreColumn(stringResource(R.string.m1), subjectGrades.marklist?.point1)
                            ScoreColumn(stringResource(R.string.m2), subjectGrades.marklist?.point2)
                            ScoreColumn(stringResource(R.string.exam_short), subjectGrades.marklist?.finally)
                            ScoreColumn(stringResource(R.string.total_short), subjectGrades.marklist?.total, true)
                        }
                    }
                } else {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.no_grades), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.teacher), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) { 
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { 
                    Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(16.dp))
                    Text(item.teacher?.get() ?: stringResource(R.string.unknown), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    
                    val copiedMsg = stringResource(R.string.copied)
                    IconButton(onClick = { 
                        val text = item.teacher?.get() ?: ""
                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("teacher", text))) }
                        Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show() 
                    }) { Icon(Icons.Default.ContentCopy, stringResource(R.string.copy), tint = MaterialTheme.colorScheme.outline) } 
                } 
            }

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.location), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) { 
                Column { 
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { 
                        Icon(Icons.Outlined.MeetingRoom, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) { 
                            Text(stringResource(R.string.room), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            // UPDATED: Use helper to translate "Online"
                            Text(getLocalizedRoomName(item.room?.name_en), style = MaterialTheme.typography.bodyLarge) 
                        } 
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { 
                        Icon(Icons.Outlined.Business, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) { 
                            val address = item.classroom?.building?.getAddress()
                            val displayAddress = if (address.isNullOrBlank()) stringResource(R.string.building) else address
                            
                            Text(displayAddress, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            Text(item.classroom?.building?.getName() ?: stringResource(R.string.campus), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) 
                        }
                        
                        val copiedMsg = stringResource(R.string.copied)
                        IconButton(onClick = { 
                            val text = item.classroom?.building?.getName() ?: ""
                            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("location", text))) }
                            Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show() 
                        }) { Icon(Icons.Default.ContentCopy, stringResource(R.string.copy), tint = MaterialTheme.colorScheme.outline) }
                        
                        val noMapMsg = stringResource(R.string.no_map_app)
                        IconButton(onClick = { 
                            val locationName = item.classroom?.building?.getName() ?: ""
                            if (locationName.isNotEmpty()) { 
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(locationName)}"))
                                try { context.startActivity(intent) } catch (e: Exception) { Toast.makeText(context, noMapMsg, Toast.LENGTH_SHORT).show() }
                            } 
                        }) { Icon(Icons.Outlined.Map, stringResource(R.string.map), tint = MaterialTheme.colorScheme.primary) } 
                    } 
                } 
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
