package kg.oshsu.myedu.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
    var photoUri by remember { mutableStateOf(vm.customPhotoUri ?: vm.uiPhoto?.toString()) }
    var theme by remember { mutableStateOf(vm.appTheme) }
    var notifications by remember { mutableStateOf(vm.notificationsEnabled) }

    val photoPicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            photoUri = uri.toString()
        }
    }

    // Expressive Shapes
    val profileShape = remember { PolygonShape(M3ExpressiveShapes.verySunny()) }
    val buttonShape = remember { PolygonShape(M3ExpressiveShapes.twelveSidedCookie()) }
    
    // Ambient Rotation for Profile
    val infiniteTransition = rememberInfiniteTransition(label = "border_rot")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(25000, easing = LinearEasing)), 
        label = "slow_rot"
    )

    // Entry Animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // TRANSPARENT SCAFFOLD
    Scaffold(
        containerColor = Color.Transparent, 
        floatingActionButton = {
             AnimatedVisibility(
                 visible = visible,
                 enter = slideInVertically { it } + fadeIn()
             ) {
                 LargeFloatingActionButton(
                    onClick = { vm.saveOnboardingSettings(name, photoUri, theme, notifications) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = buttonShape,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(Icons.Rounded.ArrowForward, null, Modifier.size(36.dp))
                }
             }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(Modifier.height(64.dp))

            // 1. Header with Expressive Typography
            Text(
                "Make it Yours", 
                style = MaterialTheme.typography.displayMedium, 
                fontWeight = FontWeight.ExtraBold, 
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Customize your student profile", 
                style = MaterialTheme.typography.titleMedium, 
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(48.dp))

            // 2. Profile Section (Shared Element + Expressive Shape)
            with(sharedTransitionScope) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .sharedElement(
                            sharedContentState = rememberSharedContentState(key = "cookie_transform"),
                            animatedVisibilityScope = animatedContentScope
                        )
                        .size(160.dp)
                        .clickable { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                ) {
                    // Rotating Border
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(rotation)
                            .border(4.dp, MaterialTheme.colorScheme.tertiary, profileShape)
                    )

                    // Image Container
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .clip(profileShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        if (photoUri != null) {
                            AsyncImage(
                                model = photoUri, 
                                contentDescription = "Profile Photo", 
                                contentScale = ContentScale.Crop, 
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    
                    // Edit Badge
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = (-8).dp)
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                    }
                }
            }
            
            Spacer(Modifier.height(40.dp))

            // 3. Name Field (Pill Shape)
            AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically { 50 }) {
                OutlinedTextField(
                    value = name, 
                    onValueChange = { name = it }, 
                    label = { Text("Display Name") }, 
                    singleLine = true,
                    shape = RoundedCornerShape(50), 
                    modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha=0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha=0.5f)
                    )
                )
            }

            Spacer(Modifier.height(32.dp))

            // 4. Theme Selector (Segmented Button Row)
            Text("App Theme", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp)) {
                val options = listOf("System", "Light", "Dark")
                val icons = listOf(Icons.Default.SettingsSystemDaydream, Icons.Default.LightMode, Icons.Default.DarkMode)
                val values = listOf("system", "light", "dark")
                
                options.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = theme == values[index],
                        onClick = { theme = values[index] },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        icon = { SegmentedButtonDefaults.Icon(active = theme == values[index]) { Icon(icons[index], null) } }
                    ) {
                        Text(label)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // 5. Notifications (Expressive ListItem)
            ListItem(
                headlineContent = { Text("Notifications", fontWeight = FontWeight.Bold) },
                supportingContent = { Text("Get reminders 1h before class") },
                leadingContent = { 
                    Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.primary) 
                },
                trailingContent = {
                    Switch(
                        checked = notifications, 
                        onCheckedChange = { notifications = it },
                        thumbContent = if (notifications) { { Icon(Icons.Default.Check, null, Modifier.size(12.dp)) } } else null
                    )
                },
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { notifications = !notifications },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            Spacer(Modifier.height(120.dp)) // Spacing for FAB
        }
    }
}