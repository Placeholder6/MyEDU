package kg.oshsu.myedu.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kg.oshsu.myedu.GraphicInfo
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.R
import kg.oshsu.myedu.SortOption
import kg.oshsu.myedu.ui.components.OshSuLogo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(vm: MainViewModel) {
    val session = vm.sessionData
    val activeSemId = vm.profileData?.active_semester

    // Set default selected semester if null
    LaunchedEffect(session, activeSemId) {
        if (vm.selectedSemesterId == null && session.isNotEmpty()) {
            vm.selectedSemesterId = activeSemId ?: session.lastOrNull()?.semester?.id
        }
    }

    val state = rememberPullToRefreshState()
    
    // --- DATA PREPARATION ---
    val currentSem = session.find { it.semester?.id == vm.selectedSemesterId } 
        ?: session.find { it.semester?.id == activeSemId } 
        ?: session.lastOrNull()

    val rawSubjects = currentSem?.subjects ?: emptyList()
    
    // Sort logic
    val sortedSubjects = remember(rawSubjects, vm.gradesSortOption) {
        when (vm.gradesSortOption) {
            SortOption.DEFAULT -> rawSubjects
            SortOption.ALPHABETICAL -> rawSubjects.sortedBy { it.subject?.get(vm.language) ?: "" }
            SortOption.LOWEST_FIRST -> rawSubjects.sortedBy { it.marklist?.total ?: 0.0 }
            SortOption.HIGHEST_FIRST -> rawSubjects.sortedByDescending { it.marklist?.total ?: 0.0 }
            SortOption.UPDATED_TIME -> rawSubjects.sortedByDescending { it.marklist?.updated_at ?: "" }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = stringResource(R.string.grades_title), 
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // --- PINNED HEADER SECTION ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 8.dp)
            ) {
                // 1. Semesters Label (With Icon)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.semesters_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    items(session) { item ->
                        val semId = item.semester?.id ?: 0
                        val isSelected = semId == (vm.selectedSemesterId ?: activeSemId)
                        
                        FilterChip(
                            selected = isSelected,
                            onClick = { vm.selectedSemesterId = semId },
                            label = { Text(stringResource(R.string.semester_format, semId)) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                            } else null,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                }

                // 2. Sort Label (With Icon)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.sort_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                val options = listOf(
                    SortOption.DEFAULT to stringResource(R.string.sort_default),
                    SortOption.ALPHABETICAL to stringResource(R.string.sort_az),
                    SortOption.UPDATED_TIME to stringResource(R.string.sort_date),
                    SortOption.LOWEST_FIRST to stringResource(R.string.sort_low),
                    SortOption.HIGHEST_FIRST to stringResource(R.string.sort_high)
                )
                
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(options) { (option, label) ->
                        val isSelected = vm.gradesSortOption == option
                        FilterChip(
                            selected = isSelected,
                            onClick = { vm.gradesSortOption = option },
                            label = { Text(label) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                            } else null,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            // --- SCROLLABLE LIST ---
            PullToRefreshBox(
                isRefreshing = vm.isRefreshing,
                onRefresh = { vm.refresh() },
                state = state,
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = state,
                        isRefreshing = vm.isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                if (vm.isGradesLoading && !vm.isRefreshing) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (session.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_grades), color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 16.dp)
                    ) {
                        if (sortedSubjects.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.semester_not_found), color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        } else {
                            items(sortedSubjects) { sub ->
                                GradeItemCard(
                                    subjectName = sub.subject?.get(vm.language) ?: stringResource(R.string.subject_default),
                                    p1 = sub.marklist?.point1,
                                    p2 = sub.marklist?.point2,
                                    exam = sub.marklist?.finalScore,
                                    total = sub.marklist?.total,
                                    updatedAt = sub.marklist?.updated_at,
                                    graphic = sub.graphic
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GradeItemCard(
    subjectName: String,
    p1: Double?,
    p2: Double?,
    exam: Double?,
    total: Double?,
    updatedAt: String?,
    graphic: GraphicInfo?
) {
    val isUploadActive = remember(graphic) {
        try {
            if (graphic?.begin != null && graphic.end != null) {
                val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                parser.timeZone = TimeZone.getTimeZone("UTC")
                
                val start = parser.parse(graphic.begin)
                val end = parser.parse(graphic.end)
                val now = Date()
                
                now.after(start) && now.before(end)
            } else false
        } catch (e: Exception) { false }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessLow)),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            // 1. Subject Name
            Text(
                text = subjectName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // 2. Scores Row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScoreItem(stringResource(R.string.m1), p1)
                ScoreItem(stringResource(R.string.m2), p2)
                ScoreItem(stringResource(R.string.exam_short), exam)
                
                // Total Score Item (Color Coded)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.total_short), 
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    
                    val totalScore = total?.toInt() ?: 0
                    val scoreColor = getGradeColor(total)
                    
                    Text(
                        text = "$totalScore",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = scoreColor
                    )
                }
            }

            // 3. Status Footer
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Active / Inactive Status
                if (isUploadActive && graphic != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.CalendarMonth, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${stringResource(R.string.status_active)} ${formatDateShort(graphic.begin)} - ${formatDateShort(graphic.end)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.EventBusy, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.status_not_active),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }

                // Last Updated Text
                if (updatedAt != null) {
                    Text(
                        text = "${stringResource(R.string.status_updated)} ${formatDateFull(updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun ScoreItem(label: String, score: Double?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${score?.toInt() ?: "-"}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun getGradeColor(score: Double?): Color {
    val s = score ?: 0.0
    val isDark = isSystemInDarkTheme()
    return when {
        s >= 90 -> if (isDark) Color(0xFF82B1FF) else Color(0xFF2962FF) // Blue
        s >= 70 -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32) // Green
        s >= 60 -> if (isDark) Color(0xFFFFB74D) else Color(0xFFEF6C00) // Orange
        else -> MaterialTheme.colorScheme.error // Red
    }
}

private fun formatDateShort(dateStr: String?): String {
    if (dateStr == null) return ""
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val formatter = SimpleDateFormat("dd MMM", Locale.getDefault())
        formatter.timeZone = TimeZone.getDefault()
        val date = parser.parse(dateStr)
        formatter.format(date ?: return dateStr)
    } catch (e: Exception) {
        ""
    }
}

private fun formatDateFull(dateStr: String?): String {
    if (dateStr == null) return ""
    return try {
        val date = parseUtcDate(dateStr) ?: return dateStr
        val formatter = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
        formatter.timeZone = TimeZone.getDefault()
        formatter.format(date)
    } catch (e: Exception) {
        dateStr
    }
}

private fun parseUtcDate(dateStr: String): Date? {
    try {
        val clean = dateStr.replace("T", " ").substringBefore(".").replace("Z", "")
        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        parser.timeZone = TimeZone.getTimeZone("UTC")
        return parser.parse(clean)
    } catch (e: Exception) {
        return null
    }
}
