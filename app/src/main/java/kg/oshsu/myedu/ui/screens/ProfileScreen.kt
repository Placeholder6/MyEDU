package kg.oshsu.myedu.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.R
import kg.oshsu.myedu.ui.components.DetailCard
import kg.oshsu.myedu.AppScreen
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ProfileScreen(
    vm: MainViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val user = vm.userData
    val profile = vm.profileData
    val pay = vm.payStatus
    
    val fullName = vm.uiName
    val displayPhoto = vm.uiPhoto
    val context = LocalContext.current
    
    var showSettingsDialog by remember { mutableStateOf(false) }

    val facultyName = profile?.studentMovement?.faculty?.let { it.name_en ?: it.name_ru } ?: profile?.studentMovement?.speciality?.faculty?.let { it.name_en ?: it.name_ru } ?: "-"

    val state = rememberPullToRefreshState()

    // Animation for Cookie Shape
    val cookiePolygon = remember { RoundedPolygon.star(12, innerRadius = 0.8f, rounding = CornerRounding(0.2f)) }
    val infiniteTransition = rememberInfiniteTransition(label = "profile_rot")
    val rotation by infiniteTransition.animateFloat(0f, 360f, infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart))
    
    val animatedShape = remember(rotation) { CustomRotatingShape(cookiePolygon, rotation) }

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
                Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.Start 
            ) {
                Spacer(Modifier.height(24.dp))
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                     IconButton(onClick = { vm.showSettingsScreen = true }) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                }
                
                // --- CENTERED HEADER SECTION ---
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // --- ANIMATED PROFILE PICTURE ---
                    Box(
                        contentAlignment = Alignment.Center, 
                        modifier = Modifier.size(136.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(animatedShape)
                        ) {
                            key(displayPhoto, vm.avatarRefreshTrigger) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(displayPhoto)
                                        .crossfade(true)
                                        .setParameter("retry_hash", vm.avatarRefreshTrigger)
                                        .build(),
                                    contentDescription = null, 
                                    contentScale = ContentScale.Crop, 
                                    modifier = Modifier.fillMaxSize()
                                ) 
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // --- NAME & EMAIL ---
                    Text(fullName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(4.dp))
                    Text(user?.email ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // --- EDIT PROFILE BUTTON (SMALLER) ---
                    ElevatedButton(
                        onClick = { vm.appState = "ONBOARDING" },
                        // Using CircleShape for standard Stadium/Pill shape without hardcoded radius
                        shape = CircleShape,
                        // Compact padding
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.dict_edit_desc), style = MaterialTheme.typography.labelLarge) 
                    }
                }
                
                Spacer(Modifier.height(32.dp))
                
                // --- TUITION CONTRACT CARD ---
                if (pay != null) {
                    SectionTitle(stringResource(R.string.tuition_contract), Icons.Outlined.AccountBalanceWallet)
                    
                    val paid = pay.paid_summa?.toFloat() ?: 0f
                    val total = pay.need_summa?.toFloat() ?: 1f
                    val progress = (paid / total).coerceIn(0f, 1f)
                    val debt = pay.getDebt().toInt()

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                Modifier.fillMaxWidth(), 
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) { 
                                Column {
                                    Text(stringResource(R.string.paid), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "${pay.paid_summa?.toInt() ?: 0} ${stringResource(R.string.currency_kgs)}", 
                                        style = MaterialTheme.typography.titleLarge, 
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(
                                    Icons.Outlined.Payments, 
                                    contentDescription = null, 
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                ) 
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeCap = StrokeCap.Round,
                            )
                            
                            Spacer(Modifier.height(16.dp))
                            
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(stringResource(R.string.total), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${pay.need_summa?.toInt() ?: 0} ${stringResource(R.string.currency_kgs)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                }
                                
                                if (debt > 0) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(stringResource(R.string.remaining, debt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                                        Text("$debt ${stringResource(R.string.currency_kgs)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                    }
                                } else {
                                     Column(horizontalAlignment = Alignment.End) {
                                        Text(stringResource(R.string.remaining, 0), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("0 ${stringResource(R.string.currency_kgs)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                
                // --- DOCUMENTS SECTION ---
                SectionTitle(stringResource(R.string.documents), Icons.Outlined.Description)
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    with(sharedTransitionScope) {
                        Button(
                            onClick = { vm.currentScreen = AppScreen.REFERENCE }, 
                            modifier = Modifier
                                .weight(1f)
                                .sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "reference_card"),
                                    animatedVisibilityScope = animatedVisibilityScope
                                ), 
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                            // Using standard CircleShape for Pill/Stadium look
                            shape = CircleShape
                        ) { 
                            Icon(Icons.Default.Description, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.reference),
                                modifier = Modifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "text_reference"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
                                )
                            )
                        }
                        
                        Button(
                            onClick = { vm.fetchTranscript() }, 
                            modifier = Modifier
                                .weight(1f)
                                .sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "transcript_card"),
                                    animatedVisibilityScope = animatedVisibilityScope
                                ),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                            // Using standard CircleShape for Pill/Stadium look
                            shape = CircleShape
                        ) { 
                            // Removed loading indicator logic
                            Icon(Icons.Default.School, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.transcript),
                                modifier = Modifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "text_transcript"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                
                // --- ACADEMIC SECTION ---
                SectionTitle(stringResource(R.string.academic), Icons.Outlined.School)
                
                DetailCard(Icons.Outlined.School, stringResource(R.string.faculty), facultyName)
                DetailCard(Icons.Outlined.Book, stringResource(R.string.speciality), profile?.studentMovement?.speciality?.name_en ?: "-")
                
                Spacer(Modifier.height(32.dp))
                
                // --- LOGOUT BUTTON ---
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = { vm.logout() },
                            onLongClick = {
                                Toast.makeText(context, "DEBUG: Token Expired. Refreshing...", Toast.LENGTH_SHORT).show()
                                vm.debugForceTokenExpiry()
                            }
                        ),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.log_out), 
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(Modifier.height(24.dp))

                // --- FOOTER INFO ---
                Column(
                    modifier = Modifier.fillMaxWidth().alpha(0.6f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (vm.lastRefreshTime > 0) {
                        val formattedTime = remember(vm.lastRefreshTime) {
                            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                            sdf.format(vm.lastRefreshTime)
                        }
                        Text(
                            text = "Last updated: $formattedTime",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text(
                        text = "Session: Active", 
                        style = MaterialTheme.typography.labelSmall
                    )
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
                    Text(stringResource(R.string.dict_url))
                    OutlinedTextField(value = vm.dictionaryUrl, onValueChange = { vm.dictionaryUrl = it }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { showSettingsDialog = false }) { Text(stringResource(R.string.dict_btn_save)) } }
        )
    }
}

// Replicating SettingsSectionTitle locally to match style exactly
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
