package kg.oshsu.myedu.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    val user = vm.userData
    val profile = vm.profileData
    var showNewsSheet by remember { mutableStateOf(false) }
    
    // Greeting Logic
    val currentHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greetingText = remember(currentHour) { 
        if(currentHour in 4..11) "Good Morning," 
        else if(currentHour in 12..16) "Good Afternoon," 
        else "Good Evening," 
    }
    
    // Expressive Pull-to-Refresh State
    val state = rememberPullToRefreshState()

    // Main Container with PullToRefreshBox
    PullToRefreshBox(
        isRefreshing = vm.isRefreshing,
        onRefresh = { vm.refresh() },
        state = state,
        indicator = {
            // Material 3 Expressive Loading Indicator
            PullToRefreshDefaults.LoadingIndicator(
                state = state,
                isRefreshing = vm.isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        },
        modifier = Modifier.fillMaxSize()
    ) {
        // Content Box
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            // Scrollable Column
            Column(
                Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(16.dp))
                
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.SpaceBetween, 
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) { 
                        Text(greetingText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        Text(
                            text = vm.uiName, // <--- CHANGED
                            style = MaterialTheme.typography.titleMedium, 
                            fontWeight = FontWeight.Bold, 
                            maxLines = 1, 
                            overflow = TextOverflow.Ellipsis
                        ) 
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OshSuLogo(modifier = Modifier.width(100.dp).height(40.dp))
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { showNewsSheet = true }, 
                            contentAlignment = Alignment.Center
                        ) { 
                            if (vm.newsList.isNotEmpty()) { 
                                BadgedBox(badge = { Badge { Text("${vm.newsList.size}") } }) { 
                                    Icon(Icons.Outlined.Notifications, contentDescription = "Announcements", tint = MaterialTheme.colorScheme.primary) 
                                } 
                            } else { 
                                Icon(Icons.Outlined.Notifications, contentDescription = "Announcements", tint = MaterialTheme.colorScheme.onSurfaceVariant) 
                            } 
                        }
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                
                // Stats Row
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
                
                Spacer(Modifier.height(32.dp))
                Text("${vm.todayDayName}'s Classes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                
                // Classes List
                if (vm.todayClasses.isEmpty()) { 
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), 
                        modifier = Modifier.fillMaxWidth()
                    ) { 
                        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) { 
                            Icon(Icons.Outlined.Weekend, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Text("No classes today!", style = MaterialTheme.typography.bodyLarge) 
                        } 
                    } 
                } else { 
                    vm.todayClasses.forEach { item -> 
                        ClassItem(item, vm.getTimeString(item.id_lesson)) { vm.selectedClass = item } 
                    } 
                }
                
                Spacer(Modifier.height(80.dp))
            }
        }
    }
    
    // News Bottom Sheet
    if (showNewsSheet) { 
        ModalBottomSheet(onDismissRequest = { showNewsSheet = false }) { 
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) { 
                Text("Announcements", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                LazyColumn { 
                    items(vm.newsList) { news -> 
                        Card(Modifier.padding(top=8.dp).fillMaxWidth()) { 
                            Column(Modifier.padding(16.dp)) { 
                                Text(news.title?:"", fontWeight=FontWeight.Bold)
                                Text(news.message?:"") 
                            } 
                        } 
                    } 
                } 
            } 
        } 
    }
}