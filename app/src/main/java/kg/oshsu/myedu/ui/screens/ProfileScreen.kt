package kg.oshsu.myedu.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.R
import kg.oshsu.myedu.ui.components.DetailCard
import kg.oshsu.myedu.ui.components.InfoSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(vm: MainViewModel) {
    val user = vm.userData
    val profile = vm.profileData
    val pay = vm.payStatus
    
    val fullName = vm.uiName
    val displayPhoto = vm.uiPhoto
    
    var showSettingsDialog by remember { mutableStateOf(false) }

    val facultyName = profile?.studentMovement?.faculty?.let { it.name_en ?: it.name_ru } ?: profile?.studentMovement?.speciality?.faculty?.let { it.name_en ?: it.name_ru } ?: "-"

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
            Column(
                Modifier.fillMaxSize().widthIn(max = 840.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), 
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                     IconButton(onClick = { vm.showSettingsScreen = true }) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                }
                
                Box(
                    contentAlignment = Alignment.Center, 
                    modifier = Modifier.size(128.dp).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)), CircleShape).padding(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background)
                ) { 
                    AsyncImage(model = displayPhoto, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape)) 
                }
                
                Spacer(Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vm.appState = "ONBOARDING" }) {
                    Text(fullName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.Edit, stringResource(R.string.dict_edit_desc), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
                
                Text(user?.email ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                
                Spacer(Modifier.height(24.dp))
                
                if (pay != null) {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { 
                                Text(stringResource(R.string.tuition_contract), fontWeight = FontWeight.Bold)
                                Icon(Icons.Outlined.Payments, null, tint = MaterialTheme.colorScheme.primary) 
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { 
                                Column { Text(stringResource(R.string.paid), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline); Text("${pay.paid_summa?.toInt() ?: 0} ${stringResource(R.string.currency_kgs)}", style = MaterialTheme.typography.titleMedium, color = Color(0xFF4CAF50)) }
                                Column(horizontalAlignment = Alignment.End) { Text(stringResource(R.string.total), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline); Text("${pay.need_summa?.toInt() ?: 0} ${stringResource(R.string.currency_kgs)}", style = MaterialTheme.typography.titleMedium) } 
                            }
                            val debt = pay.getDebt()
                            if (debt > 0) { 
                                Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.remaining, debt.toInt()) + " ${stringResource(R.string.currency_kgs)}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) 
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                
                InfoSection(stringResource(R.string.documents))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { vm.showReferenceScreen = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { 
                        Icon(Icons.Default.Description, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.reference)) 
                    }
                    Button(onClick = { vm.fetchTranscript() }, modifier = Modifier.weight(1f), enabled = !vm.isTranscriptLoading) { 
                        if (vm.isTranscriptLoading) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp) else Icon(Icons.Default.School, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.transcript)) 
                    }
                }

                Spacer(Modifier.height(24.dp))
                
                InfoSection(stringResource(R.string.academic))
                DetailCard(Icons.Outlined.School, stringResource(R.string.faculty), facultyName)
                DetailCard(Icons.Outlined.Book, stringResource(R.string.speciality), profile?.studentMovement?.speciality?.name_en ?: "-")
                
                Spacer(Modifier.height(32.dp))
                
                Button(onClick = { vm.logout() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer), modifier = Modifier.fillMaxWidth()) { 
                    Text(stringResource(R.string.log_out)) 
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text(stringResource(R.string.settings)) },
            text = {
                Column {
                    // UPDATED: String Resource
                    Text(stringResource(R.string.dict_url))
                    OutlinedTextField(value = vm.dictionaryUrl, onValueChange = { vm.dictionaryUrl = it }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { showSettingsDialog = false }) { Text(stringResource(R.string.dict_btn_save)) } }
        )
    }
}
