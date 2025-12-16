package kg.oshsu.myedu.ui.screens

import android.graphics.Matrix
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SettingsSystemDaydream
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kg.oshsu.myedu.R
import kotlinx.coroutines.delay

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
    
    // Check if we are in "Edit Mode" (onboarding already completed previously)
    val isEditMode = remember { vm.isOnboardingComplete() }

    val apiPhoto = vm.profileData?.avatar
    val apiName = remember { vm.userData?.let { "${it.last_name ?: ""} ${it.name ?: ""}".trim() } ?: "" }
    val startPhoto = remember { vm.customPhotoUri ?: apiPhoto }
    
    var name by remember { mutableStateOf(vm.customName ?: apiName) }
    var photoUri by remember { mutableStateOf(startPhoto) }
    var theme by remember { mutableStateOf(vm.appTheme) }
    var notifications by remember { mutableStateOf(vm.notificationsEnabled) }

    val showRevertPhoto = photoUri != apiPhoto
    val showRevertName = name != apiName

    var isUiVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(600)
        isUiVisible = true
    }

    val uiAlpha by animateFloatAsState(if (isUiVisible) 1f else 0f, tween(600, easing = LinearOutSlowInEasing))
    val uiTranslationY by animateDpAsState(if (isUiVisible) 0.dp else 40.dp, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
    val holeScale by animateFloatAsState(if (isUiVisible) 1f else 0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
    val revertHoleScale by animateFloatAsState(if (showRevertPhoto && isUiVisible) 1f else 0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))

    val photoPicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            photoUri = uri.toString()
        }
    }

    val cookiePolygon = remember { RoundedPolygon.star(12, innerRadius = 0.8f, rounding = CornerRounding(0.2f)) }
    val infiniteTransition = rememberInfiniteTransition(label = "profile_rot")
    val rotation by infiniteTransition.animateFloat(0f, 360f, infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart))
    val animatedShape = CustomRotatingShape(cookiePolygon, rotation)

    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(48.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = uiAlpha; translationY = uiTranslationY.toPx() }
            ) {
                // UPDATED: String Resource
                Text(stringResource(R.string.onboard_title), style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                // UPDATED: String Resource
                Text(stringResource(R.string.onboard_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(48.dp))

            with(sharedTransitionScope) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize().padding(8.dp)
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(key = "cookie_transform"),
                                animatedVisibilityScope = animatedContentScope,
                                boundsTransform = { _, _ -> tween(durationMillis = 500, easing = LinearOutSlowInEasing) }
                            )
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithCache {
                                val buttonRadius = 20.dp.toPx(); val borderSize = 4.dp.toPx()
                                val editCenter = Offset(size.width - 22.dp.toPx(), size.height - 22.dp.toPx())
                                val editCutRadius = (buttonRadius + borderSize) * holeScale
                                val revertCenter = Offset(22.dp.toPx(), size.height - 22.dp.toPx())
                                val revertCutRadius = (buttonRadius + borderSize) * revertHoleScale
                                onDrawWithContent {
                                    drawContent()
                                    if (holeScale > 0f) drawCircle(Color.Black, editCutRadius, editCenter, blendMode = BlendMode.Clear)
                                    if (revertHoleScale > 0f) drawCircle(Color.Black, revertCutRadius, revertCenter, blendMode = BlendMode.Clear)
                                }
                            }
                            .clip(animatedShape)
                            .clickable { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        if (photoUri != null) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(photoUri).crossfade(true).build(),
                                contentDescription = "Profile Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                                loading = { Box(Modifier.fillMaxSize()) }
                            )
                        }
                    }
                    
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isUiVisible && showRevertPhoto,
                        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)), exit = scaleOut(),
                        modifier = Modifier.align(Alignment.BottomStart).offset(x = 10.dp, y = (-10).dp)
                    ) {
                        Surface(onClick = { photoUri = apiPhoto }, shape = CircleShape, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Restore, "Revert", tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(20.dp)) }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isUiVisible,
                        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)), exit = scaleOut(),
                        modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-10).dp, y = (-10).dp)
                    ) {
                        Surface(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Edit, "Edit", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
            }
            
            TextButton(
                onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.graphicsLayer { alpha = uiAlpha }
            ) {
                // UPDATED: String Resource
                Text(if (photoUri == null) stringResource(R.string.onboard_add_photo) else stringResource(R.string.onboard_change_photo))
            }

            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier.widthIn(max = 400.dp).graphicsLayer { alpha = uiAlpha; translationY = uiTranslationY.toPx() },
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, 
                    // UPDATED: String Resource
                    label = { Text(stringResource(R.string.onboard_display_name)) }, 
                    singleLine = true, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
                    trailingIcon = if (showRevertName) { { IconButton(onClick = { name = apiName }) { Icon(Icons.Default.Restore, "Revert", tint = MaterialTheme.colorScheme.primary) } } } else null
                )

                // Only show Theme and Notification options if NOT in "Edit Mode"
                if (!isEditMode) {
                    Column {
                        // UPDATED: String Resource
                        Text(stringResource(R.string.onboard_theme), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // UPDATED: String Resources for System/Light/Dark
                            ThemeOption(Icons.Default.SettingsSystemDaydream, stringResource(R.string.follow_system), theme == "system", { theme = "system" }, Modifier.weight(1f))
                            ThemeOption(Icons.Default.LightMode, stringResource(R.string.light_mode), theme == "light", { theme = "light" }, Modifier.weight(1f))
                            ThemeOption(Icons.Default.DarkMode, stringResource(R.string.dark_mode), theme == "dark", { theme = "dark" }, Modifier.weight(1f))
                        }
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(24.dp), onClick = { notifications = !notifications }, modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(stringResource(R.string.onboard_notifications), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                            supportingContent = { Text(stringResource(R.string.onboard_notif_desc), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            leadingContent = { Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            trailingContent = { Switch(checked = notifications, onCheckedChange = { notifications = it }, thumbContent = if (notifications) { { Icon(Icons.Default.Check, null, Modifier.size(12.dp)) } } else null) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(48.dp))

            Button(
                onClick = { vm.saveOnboardingSettings(name, photoUri, theme, notifications) },
                modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp).height(56.dp).graphicsLayer { alpha = uiAlpha; translationY = uiTranslationY.toPx() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(50)
            ) {
                // If in Edit Mode, show "Save", otherwise "All Set"
                val btnText = if (isEditMode) stringResource(R.string.dict_btn_save) else stringResource(R.string.onboard_btn_finish)
                Text(btnText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!isEditMode) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.ArrowForward, null)
                }
            }
            
            Spacer(Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.onboard_disclaimer),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp).graphicsLayer { alpha = uiAlpha; translationY = uiTranslationY.toPx() }
            )

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
