package kg.oshsu.myedu.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.R
import kg.oshsu.myedu.ui.components.ScoreColumn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(vm: MainViewModel) {
    val session = vm.sessionData
    val activeSemId = vm.profileData?.active_semester
    
    val state = rememberPullToRefreshState()
    
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
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (vm.isGradesLoading && !vm.isRefreshing) { 
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                    CircularProgressIndicator() 
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp)
                        .padding(16.dp)
                ) {
                    item { 
                        Spacer(Modifier.height(32.dp))
                        Text(
                            stringResource(R.string.current_session), 
                            style = MaterialTheme.typography.headlineMedium, 
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(16.dp)) 
                    }
                    
                    if (session.isEmpty()) {
                        item { 
                            Text(stringResource(R.string.no_grades), color = Color.Gray) 
                        }
                    } else {
                        val currentSem = session.find { it.semester?.id == activeSemId } ?: session.lastOrNull()
                        
                        if (currentSem != null) {
                            item { 
                                // UPDATED: Use localized format instead of API name_en
                                val semId = currentSem.semester?.id ?: 0
                                Text(
                                    text = stringResource(R.string.semester_format, semId),
                                    style = MaterialTheme.typography.titleMedium, 
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(8.dp)) 
                            }
                            
                            items(currentSem.subjects ?: emptyList()) { sub ->
                                Card(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp), 
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(
                                            sub.subject?.get() ?: stringResource(R.string.subject_default), 
                                            style = MaterialTheme.typography.titleMedium, 
                                            fontWeight = FontWeight.Bold
                                        )
                                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                        
                                        Row(
                                            Modifier.fillMaxWidth(), 
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            ScoreColumn(stringResource(R.string.m1), sub.marklist?.point1)
                                            ScoreColumn(stringResource(R.string.m2), sub.marklist?.point2)
                                            ScoreColumn(stringResource(R.string.exam_short), sub.marklist?.finally)
                                            ScoreColumn(stringResource(R.string.total_short), sub.marklist?.total, true)
                                        }
                                    }
                                }
                            }
                        } else {
                            item { 
                                Text(stringResource(R.string.semester_not_found), color = Color.Gray) 
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
