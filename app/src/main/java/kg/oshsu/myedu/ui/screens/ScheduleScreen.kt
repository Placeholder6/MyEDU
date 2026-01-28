package kg.oshsu.myedu.ui.screens

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.R
import kg.oshsu.myedu.ScheduleItem
import kg.oshsu.myedu.ui.components.*
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(vm: MainViewModel) {
    val tabs = listOf(
        stringResource(R.string.mon), stringResource(R.string.tue), stringResource(R.string.wed),
        stringResource(R.string.thu), stringResource(R.string.fri), stringResource(R.string.sat)
    )

    val scope = rememberCoroutineScope()
    // Calculate initial page based on current day
    val initialPage = remember {
        val cal = Calendar.getInstance()
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        // Sunday (1) -> 0 (Monday), Monday (2) -> 0, ..., Saturday (7) -> 5
        if (dow == Calendar.SUNDAY) 0 else (dow - 2).coerceIn(0, 5)
    }

    val pagerState = rememberPagerState(initialPage = initialPage) { tabs.size }
    val pullState = rememberPullToRefreshState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nav_schedule),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    OshSuLogo(modifier = Modifier.width(80.dp).height(32.dp))
                    Spacer(Modifier.width(16.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // --- DAY CHIPS HEADER ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 12.dp)
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(tabs) { index, title ->
                        val isSelected = pagerState.currentPage == index
                        FilterChip(
                            selected = isSelected,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            label = { Text(title) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                            } else null,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            // --- CONTENT PAGER ---
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { pageIndex ->
                val dayClasses = vm.fullSchedule.filter { it.day == pageIndex }

                PullToRefreshBox(
                    isRefreshing = vm.isRefreshing,
                    onRefresh = { vm.refresh() },
                    state = pullState,
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = pullState,
                            isRefreshing = vm.isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (dayClasses.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Outlined.Weekend,
                                    null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    stringResource(R.string.no_classes),
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 840.dp),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            items(dayClasses) { item ->
                                ScheduleCard(
                                    item = item,
                                    timeString = vm.getTimeString(item.id_lesson),
                                    onClick = { vm.selectedClass = item }
                                )
                            }
                            item { Spacer(Modifier.height(80.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleCard(
    item: ScheduleItem,
    timeString: String,
    onClick: () -> Unit
) {
    val localizedType = getLocalizedSubjectType(item.subject_type?.get())
    val localizedRoom = getLocalizedRoomName(item.room?.name_en)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessLow)),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- 1. Class Number "Button" ---
            // Presentable squircle shape for class number
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${item.id_lesson}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // --- 2. Details Column ---
            Column(modifier = Modifier.weight(1f)) {
                // Subject Name
                Text(
                    text = item.subject?.get() ?: stringResource(R.string.subject_default),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                // Time & Type Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Spacer(Modifier.width(8.dp))
                    
                    // Small Type Badge
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = localizedType,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                
                Spacer(Modifier.height(4.dp))

                // Room & Teacher Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.MeetingRoom,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = localizedRoom,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (item.teacher?.get() != null) {
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = item.teacher?.get() ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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

    // -- Strings --
    val copiedMsg = stringResource(R.string.copied)
    val noMapMsg = stringResource(R.string.no_map_app)
    val subjectDefault = stringResource(R.string.subject_default)
    val unknownText = stringResource(R.string.unknown)
    val buildingText = stringResource(R.string.building)
    val campusText = stringResource(R.string.campus)
    val noGradesText = stringResource(R.string.no_grades)
    val typeLecture = stringResource(R.string.type_lecture)
    val labelStream = stringResource(R.string.stream)
    val labelGroup = stringResource(R.string.group)

    val timeString = vm.getTimeString(item.id_lesson)
    val localizedSubjectType = getLocalizedSubjectType(item.subject_type?.get())
    val groupLabel = if (localizedSubjectType == typeLecture) labelStream else labelGroup
    val groupValue = item.stream?.numeric?.toString() ?: "?"
    
    val activeSemester = vm.profileData?.active_semester
    val session = vm.sessionData
    val currentSemSession = session.find { it.semester?.id == activeSemester } ?: session.lastOrNull()
    val subjectGrades = currentSemSession?.subjects?.find { it.subject?.get() == item.subject?.get() }

    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 840.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        // --- 1. HEADER CARD (Main Info) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = MaterialTheme.shapes.large
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = item.subject?.get() ?: subjectDefault,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                Spacer(Modifier.height(12.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Type Chip
                    SuggestionChip(
                        onClick = {},
                        label = { Text(localizedSubjectType) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha=0.9f),
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = null
                    )
                    
                    // Group Chip
                    if (item.stream?.numeric != null) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("$groupLabel $groupValue") },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha=0.9f),
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = null
                        )
                    }
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // --- 2. GRADES SECTION ---
        SectionTitle(stringResource(R.string.current_performance), Icons.Outlined.BarChart)
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = MaterialTheme.shapes.large
        ) {
            if (subjectGrades != null) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ScoreColumn(stringResource(R.string.m1), subjectGrades.marklist?.point1)
                    ScoreColumn(stringResource(R.string.m2), subjectGrades.marklist?.point2)
                    ScoreColumn(stringResource(R.string.exam_short), subjectGrades.marklist?.finalScore)
                    
                    // Total with colored logic
                    val total = subjectGrades.marklist?.total
                    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                    val totalColor = when {
                        (total ?: 0.0) >= 90 -> if (isDark) Color(0xFF82B1FF) else Color(0xFF2962FF)
                        (total ?: 0.0) >= 70 -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                        (total ?: 0.0) >= 60 -> if (isDark) Color(0xFFFFB74D) else Color(0xFFEF6C00)
                        else -> MaterialTheme.colorScheme.error
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.total_short), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${total?.toInt() ?: 0}", 
                            style = MaterialTheme.typography.titleLarge, 
                            fontWeight = FontWeight.Black, 
                            color = totalColor
                        )
                    }
                }
            } else {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(16.dp))
                    Text(noGradesText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // --- 3. TEACHER SECTION ---
        SectionTitle(stringResource(R.string.teacher), Icons.Default.Person)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = MaterialTheme.shapes.large
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                 Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = item.teacher?.get() ?: unknownText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    val text = item.teacher?.get() ?: ""
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("teacher", text))) }
                    Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.ContentCopy, stringResource(R.string.copy), tint = MaterialTheme.colorScheme.outline)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // --- 4. LOCATION SECTION ---
        SectionTitle(stringResource(R.string.location), Icons.Default.LocationOn)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = MaterialTheme.shapes.large
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                // Room Row
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.MeetingRoom, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.room), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(getLocalizedRoomName(item.room?.name_en), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.2f), modifier = Modifier.padding(horizontal = 16.dp))

                // Building & Address Row with Actions
                val locationName = item.classroom?.building?.getName() ?: ""
                val address = item.classroom?.building?.getAddress()
                val displayAddress = if (address.isNullOrBlank()) buildingText else address
                val displayName = if (locationName.isNotEmpty()) locationName else campusText

                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Business, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(displayAddress, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(displayName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                    
                    // Actions
                    IconButton(onClick = {
                        val text = locationName
                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("location", text))) }
                        Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, stringResource(R.string.copy), tint = MaterialTheme.colorScheme.outline)
                    }
                    IconButton(onClick = {
                        if (locationName.isNotEmpty()) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(locationName)}"))
                            try { context.startActivity(intent) } catch (e: Exception) { Toast.makeText(context, noMapMsg, Toast.LENGTH_SHORT).show() }
                        }
                    }) {
                         Icon(Icons.Outlined.Map, stringResource(R.string.map), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun SectionTitle(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically, 
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    ) {
        Icon(
            icon, 
            contentDescription = null, 
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title, 
            style = MaterialTheme.typography.titleSmall, 
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}
