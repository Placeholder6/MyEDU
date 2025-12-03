package kg.oshsu.myedu.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SettingsSystemDaydream
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.ui.components.M3ExpressiveShapes
import kg.oshsu.myedu.ui.components.PolygonShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun OnboardingScreen(
    vm: MainViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(vm.customName ?: vm.userData?.name ?: "") }
    // Use API photo if available
    var photoUri by remember { mutableStateOf(vm.customPhotoUri ?: vm.uiPhoto?.toString()) }
    var theme by remember { mutableStateOf(vm.appTheme) }
    var notifications by remember { mutableStateOf(vm.notificationsEnabled) }

    val photoPicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            photoUri = uri.toString()
        }
    }

    val containerShape = remember { PolygonShape(M3ExpressiveShapes.twelveSidedCookie()) }
    
    // Rotating effect for the profile picture to match the "morph"
    val infiniteTransition = rememberInfiniteTransition(label = "profile_rot")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)), 
        label = "slow_rot"
    )

    Scaffold(
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Box(Modifier.padding(24.dp)) {
                    Button(
                        onClick = { vm.saveOnboardingSettings(name, photoUri, theme, notifications) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("All Set", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.ArrowForward, null)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.weight(1f))
            Text("Make it Yours", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("Customize your profile & experience", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(48.dp))

            // 1. Photo Picker with CONTAINER TRANSFORM (Shared Element)
            with(sharedTransitionScope) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        // CONTAINER TRANSFORM: Define this as the shared element (Destination)
                        .sharedElement(
                            state = rememberSharedContentState(key = "cookie_transform"),
                            animatedVisibilityScope = animatedContentScope
                        )
                        .size(120.dp)
                        .rotate(rotation) // Rotating cookie shaped profile picture
                        .clip(containerShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        .border(2.dp, MaterialTheme.colorScheme.primary, containerShape)
                ) {
                    if (photoUri != null) {
                        AsyncImage(model = photoUri, contentDescription = "Profile Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Default.Add, contentDescription = "Add Photo", modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            TextButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text(if (photoUri == null) "Add Photo" else "Change Photo")
            }

            Spacer(Modifier.height(24.dp))

            // Name Field
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Display Name") }, singleLine = true,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline)
            )

            Spacer(Modifier.height(32.dp))

            // Theme Selector
            Text("App Theme", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().widthIn(max = 400.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeOption(Icons.Default.SettingsSystemDaydream, "System", theme == "system", { theme = "system" }, Modifier.weight(1f))
                ThemeOption(Icons.Default.LightMode, "Light", theme == "light", { theme = "light" }, Modifier.weight(1f))
                ThemeOption(Icons.Default.DarkMode, "Dark", theme == "dark", { theme = "dark" }, Modifier.weight(1f))
            }

            Spacer(Modifier.height(32.dp))

            // Notifications
            Row(
                Modifier.fillMaxWidth().widthIn(max = 400.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable { notifications = !notifications }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(16.dp))
                    Column { Text("Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Get class reminders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Switch(checked = notifications, onCheckedChange = { notifications = it }, thumbContent = if (notifications) { { Icon(Icons.Default.Check, null, Modifier.size(12.dp)) } } else null)
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun ThemeOption(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val containerColor by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow, label = "ThemeColor")
    val contentColor by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, label = "ThemeContent")
    Card(onClick = onClick, modifier = modifier.height(80.dp), colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor), border = if(selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(icon, null); Spacer(Modifier.height(4.dp)); Text(label, style = MaterialTheme.typography.labelMedium) }
    }
}