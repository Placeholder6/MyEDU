package kg.oshsu.myedu.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.ui.components.DetailCard
import kg.oshsu.myedu.ui.components.InfoSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(vm: MainViewModel) {
    val user = vm.userData
    val profile = vm.profileData
    val pay = vm.payStatus
    val fullName = "${user?.last_name ?: ""} ${user?.name ?: ""}".trim().ifEmpty { "Student" }
    
    var showSettingsDialog by remember { mutableStateOf(false) }

    val facultyName = profile?.studentMovement?.faculty?.let { it.name_en ?: it.name_ru } 
        ?: profile?.studentMovement?.speciality?.faculty?.let { it.name_en ?: it.name_ru } 
        ?: "-"

    // Expressive Pull-to-Refresh State
    val state = rememberPullToRefreshState()

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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp), 
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))
                
                // Settings Button
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                     IconButton(onClick = { showSettingsDialog = true }) { 
                         Icon(Icons.Default.Settings, "Settings") 
                     }
                }
                
                // Profile Picture
                Box(
                    contentAlignment = Alignment.Center, 
                    modifier = Modifier
                        .size(128.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary, 
                                    MaterialTheme.colorScheme.tertiary
                                )
                            ), 
                            CircleShape
                        )
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background)
                ) { 
                    AsyncImage(
                        model = profile?.avatar, 
                        contentDescription = null, 
                        contentScale = ContentScale.Crop, 
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    ) 
                }
                
                Spacer(Modifier.height(16.dp))
                Text(fullName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(user?.email ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                
                Spacer(Modifier.height(24.dp))
                
                // Payment Card
                if (pay != null) {
                    Card(
                        Modifier.fillMaxWidth(), 
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), 
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { 
                                Text("Tuition Contract", fontWeight = FontWeight.Bold)
                                Icon(Icons.Outlined.Payments, null, tint = MaterialTheme.colorScheme.primary) 
                            }
                            Spacer(Modifier.height(12.dp))
                            
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { 
                                Column { 
                                    Text("Paid", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                    Text("${pay.paid_summa?.toInt() ?: 0} KGS", style = MaterialTheme.typography.titleMedium, color = Color(0xFF4CAF50)) 
                                }
                                Column(horizontalAlignment = Alignment.End) { 
                                    Text("Total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                    Text("${pay.need_summa?.toInt() ?: 0} KGS", style = MaterialTheme.typography.titleMedium) 
                                } 
                            }
                            
                            val debt = pay.getDebt()
                            if (debt > 0) { 
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(8.dp))
                                Text("Remaining: ${debt.toInt()} KGS", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) 
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                
                // Documents Section
                InfoSection("Documents")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { vm.showReferenceScreen = true }, 
                        modifier = Modifier.weight(1f), 
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) { 
                        Icon(Icons.Default.Description, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reference") 
                    }
                    Button(
                        onClick = { vm.fetchTranscript() }, 
                        modifier = Modifier.weight(1f), 
                        enabled = !vm.isTranscriptLoading
                    ) { 
                        if (vm.isTranscriptLoading) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp), 
                                color = MaterialTheme.colorScheme.onPrimary, 
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.School, null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Transcript") 
                    }
                }

                Spacer(Modifier.height(24.dp))
                
                // Personal Details Section
                InfoSection("Personal Details")
                DetailCard(Icons.Outlined.School, "Faculty", facultyName)
                DetailCard(Icons.Outlined.Book, "Speciality", profile?.studentMovement?.speciality?.name_en ?: "-")
                
                Spacer(Modifier.height(32.dp))
                
                // Logout Button
                Button(
                    onClick = { vm.logout() }, 
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer, 
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ), 
                    modifier = Modifier.fillMaxWidth()
                ) { 
                    Text("Log Out") 
                }
                
                Spacer(Modifier.height(80.dp))
            }
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
            confirmButton = { 
                TextButton(onClick = { showSettingsDialog = false }) { 
                    Text("Done") 
                } 
            }
        )
    }
}