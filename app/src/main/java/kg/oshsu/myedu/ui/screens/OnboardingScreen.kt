package kg.oshsu.myedu.ui.screens

import android.graphics.Matrix
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
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import coil.compose.AsyncImage
import kg.oshsu.myedu.MainViewModel

// --- CUSTOM ROTATING SHAPE IMPLEMENTATION ---
class CustomRotatingShape(
    private val polygon: RoundedPolygon,
    private val rotation: Float
) : Shape {
    private val matrix = Matrix()

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        matrix.reset()
        // 1. Scale to fit the container size (mapping -1..1 range to width/height)
        matrix.postScale(size.width / 2f, size.height / 2f)
        
        // 2. Translate to center (moving 0,0 to center of container)
        matrix.postTranslate(size.width / 2f, size.height / 2f)
        
        // 3. Rotate around the center
        matrix.postRotate(rotation, size.width / 2f, size.height / 2f)

        val path = polygon.toPath().asComposePath()
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

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

    // 1. Define the 12-sided cookie polygon
    val cookiePolygon = remember {
        RoundedPolygon.star(
            numVerticesPerRadius = 12,
            innerRadius = 0.8f,
            rounding = CornerRounding(0.2f)
        )
    }
    
    // 2. Setup Infinite Rotation Animation
    val infiniteTransition = rememberInfiniteTransition(label = "profile_rot")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, 
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing), 
            repeatMode = RepeatMode.Restart
        ), 
        label = "rotation"
    )

    // 3. Create the Shape instance driven by the animation value
    // We recreate the shape object when rotation changes so the Outline updates
    val animatedShape = CustomRotatingShape(cookiePolygon, rotation)

    // TRANSPARENT BACKGROUND
    Scaffold(
        containerColor = Color.Transparent, 
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

            // --- PROFILE PICTURE SECTION ---
            with(sharedTransitionScope) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .sharedElement(
                            sharedContentState = rememberSharedContentState(key = "cookie_transform"),
                            animatedVisibilityScope = animatedContentScope
                        )
                        .size(160.dp) // Slightly larger to accommodate the shape
                        .clickable { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                ) {
                    // Profile Photo (Clipped + Bordered with Rotating Shape)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            // Draw the border FIRST so it follows the shape outline
                            .border(4.dp, MaterialTheme.colorScheme.primary, animatedShape)
                            // Then Clip the content to the same rotating shape
                            .clip(animatedShape)
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
                            Icon(Icons.Default.Add, contentDescription = "Add Photo", modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    
                    // Small "Edit" Badge (Static, overlaid)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-12).dp, y = (-12).dp)
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(20.dp))
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
                shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline)
            )

            Spacer(Modifier.height(32.dp))

            // --- THEME SELECTOR ---
            Text("App Theme", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().widthIn(max = 400.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeOption(Icons.Default.SettingsSystemDaydream, "System", theme == "system", { theme = "system" }, Modifier.weight(1f))
                ThemeOption(Icons.Default.LightMode, "Light", theme == "light", { theme = "light" }, Modifier.weight(1f))
                ThemeOption(Icons.Default.DarkMode, "Dark", theme == "dark", { theme = "dark" }, Modifier.weight(1f))
            }

            Spacer(Modifier.height(24.dp))

            // --- NOTIFICATIONS ---
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (notifications) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(24.dp), 
                onClick = { notifications = !notifications },
                modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically, 
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.Notifications, 
                            null, 
                            tint = if (notifications) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(16.dp))
                        Column { 
                            Text(
                                "Notifications", 
                                style = MaterialTheme.typography.titleMedium, 
                                fontWeight = FontWeight.Bold,
                                color = if (notifications) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Get class reminders", 
                                style = MaterialTheme.typography.bodySmall, 
                                color = if (notifications) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                            ) 
                        }
                    }
                    Switch(
                        checked = notifications, 
                        onCheckedChange = { notifications = it }, 
                        thumbContent = if (notifications) { { Icon(Icons.Default.Check, null, Modifier.size(12.dp)) } } else null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun ThemeOption(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val containerColor by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer, label = "ThemeColor")
    val contentColor by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, label = "ThemeContent")
    val scale by animateFloatAsState(if (selected) 1.05f else 1f, label = "Scale")

    Card(
        onClick = onClick, 
        modifier = modifier.height(100.dp).scale(scale), 
        shape = RoundedCornerShape(24.dp), 
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 6.dp else 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { 
            Icon(icon, null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) 
        }
    }
}