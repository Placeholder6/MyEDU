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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
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
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kg.oshsu.myedu.MainViewModel
import kotlinx.coroutines.delay

// --- CUSTOM ROTATING SHAPE IMPLEMENTATION ---
class CustomRotatingShape(
    private val polygon: RoundedPolygon,
    private val rotation: Float
) : Shape {
    private val matrix = Matrix()

    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        matrix.reset()
        matrix.postScale(size.width / 2f, size.height / 2f)
        matrix.postTranslate(size.width / 2f, size.height / 2f)
        matrix.postRotate(rotation, size.width / 2f, size.height / 2f)

        val androidPath = polygon.toPath()
        androidPath.transform(matrix)
        
        return Outline.Generic(androidPath.asComposePath())
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

    // --- TRANSITION STATE ---
    var isUiVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(600) // 500ms morph + 100ms buffer
        isUiVisible = true
    }

    // Standard UI Fade/Slide
    val uiAlpha by animateFloatAsState(
        targetValue = if (isUiVisible) 1f else 0f,
        animationSpec = tween(600, easing = LinearOutSlowInEasing), 
        label = "uiAlpha"
    )
    val uiTranslationY by animateDpAsState(
        targetValue = if (isUiVisible) 0.dp else 40.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow), 
        label = "uiOffset"
    )

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

    val animatedShape = CustomRotatingShape(cookiePolygon, rotation)

    // TRANSPARENT BACKGROUND
    Scaffold(
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(48.dp))

            // HEADER (Delayed)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { 
                    alpha = uiAlpha
                    translationY = uiTranslationY.toPx() 
                }
            ) {
                Text(
                    "Make it Yours", 
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), 
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Customize your profile & experience", 
                    style = MaterialTheme.typography.bodyLarge, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(48.dp))

            // --- PROFILE PICTURE SECTION ---
            with(sharedTransitionScope) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(160.dp)
                ) {
                    // Profile Photo (Shared Element)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(key = "cookie_transform"),
                                animatedVisibilityScope = animatedContentScope,
                                boundsTransform = { _, _ -> tween(durationMillis = 500, easing = LinearOutSlowInEasing) }
                            )
                            .clip(animatedShape)
                            .clickable { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        if (photoUri != null) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(photoUri)
                                    .crossfade(true)
                                    .build(), 
                                contentDescription = "Profile Photo", 
                                contentScale = ContentScale.Crop, 
                                modifier = Modifier.fillMaxSize(),
                                loading = { Box(Modifier.fillMaxSize()) }
                            )
                        }
                    }
                    
                    // Small "Edit" Badge (Native AnimatedVisibility + Surface)
                    // FIX: Explicitly call androidx.compose.animation.AnimatedVisibility to avoid scope ambiguity error
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isUiVisible,
                        enter = scaleIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ),
                        exit = scaleOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-10).dp, y = (-10).dp)
                    ) {
                        Surface(
                            onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            // Thicker border (4dp) for clean cutout effect
                            border = BorderStroke(4.dp, MaterialTheme.colorScheme.surface),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "Edit Profile",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            // "Change Photo" Button (Delayed)
            TextButton(
                onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.graphicsLayer { alpha = uiAlpha }
            ) {
                Text(if (photoUri == null) "Add Photo" else "Change Photo")
            }

            Spacer(Modifier.height(24.dp))

            // --- FORM FIELDS (Delayed) ---
            Column(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .graphicsLayer { 
                        alpha = uiAlpha
                        translationY = uiTranslationY.toPx() 
                    },
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Name Field
                OutlinedTextField(
                    value = name, 
                    onValueChange = { name = it }, 
                    label = { Text("Display Name") }, 
                    singleLine = true,
                    shape = RoundedCornerShape(50), 
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                // Theme Selector
                Column {
                    Text(
                        "App Theme", 
                        style = MaterialTheme.typography.labelMedium, 
                        color = MaterialTheme.colorScheme.primary, 
                        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(), 
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ThemeOption(Icons.Default.SettingsSystemDaydream, "System", theme == "system", { theme = "system" }, Modifier.weight(1f))
                        ThemeOption(Icons.Default.LightMode, "Light", theme == "light", { theme = "light" }, Modifier.weight(1f))
                        ThemeOption(Icons.Default.DarkMode, "Dark", theme == "dark", { theme = "dark" }, Modifier.weight(1f))
                    }
                }

                // Notifications Toggle
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (notifications) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(24.dp), 
                    onClick = { notifications = !notifications },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { 
                            Text(
                                "Notifications", 
                                fontWeight = FontWeight.Bold,
                                color = if (notifications) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            ) 
                        },
                        supportingContent = { 
                            Text(
                                "Get class reminders", 
                                color = if (notifications) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                            ) 
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.Notifications, 
                                null, 
                                tint = if (notifications) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
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
                    )
                }
            }

            Spacer(Modifier.height(48.dp))

            // --- MAIN ACTION BUTTON ---
            Button(
                onClick = { vm.saveOnboardingSettings(name, photoUri, theme, notifications) },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 400.dp)
                    .height(56.dp)
                    .graphicsLayer { 
                        alpha = uiAlpha
                        translationY = uiTranslationY.toPx() 
                    },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(50)
            ) {
                Text("All Set", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.ArrowForward, null)
            }
            
            Spacer(Modifier.height(32.dp))
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