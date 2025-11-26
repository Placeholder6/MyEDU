package kg.oshsu.myedu.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.ui.components.ScoreColumn

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
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}