package kg.oshsu.myedu.ui.screens

import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import kg.oshsu.myedu.R
import kotlinx.coroutines.delay

class EditProfileRotatingShape(
    private val polygon: RoundedPolygon,
    private val rotation: Float
) : Shape {
    private val matrix = Matrix()
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
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
fun EditProfileScreen(
    vm: MainViewModel,
    onClose: () -> Unit, // Callback for navigation/closing
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val context = LocalContext.current
    
    // IMPORTANT: No BackHandler here. 
    // The predictive back gesture is handled centrally in MainActivity.

    val apiPhoto = vm.profileData?.avatar
    val apiName = remember { vm.userData?.let { "${it.last_name ?: ""} ${it.name ?: ""}".trim() } ?: "" }
    val startPhoto = remember { vm.customPhotoUri ?: apiPhoto }
    
    var name by remember { mutableStateOf(vm.customName ?: apiName) }
    var photoUri by remember { mutableStateOf(startPhoto) }

    val showRevertPhoto = photoUri != apiPhoto
    val showRevertName = name != apiName

    var isUiVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
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
    val animatedShape = EditProfileRotatingShape(cookiePolygon, rotation)

    with(sharedTransitionScope) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.desc_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            modifier = Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "edit_profile_card"),
                animatedVisibilityScope = animatedVisibilityScope
            )
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    
                    Text(
                        "Edit Profile",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(key = "text_edit_profile"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
                        )
                    )
                    
                    Spacer(Modifier.height(48.dp))

                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize().padding(8.dp)
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
                    
                    TextButton(
                        onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.graphicsLayer { alpha = uiAlpha }
                    ) {
                        Text(if (photoUri == null) stringResource(R.string.onboard_add_photo) else stringResource(R.string.onboard_change_photo))
                    }

                    Spacer(Modifier.height(24.dp))

                    Column(
                        modifier = Modifier.widthIn(max = 400.dp).graphicsLayer { alpha = uiAlpha; translationY = uiTranslationY.toPx() },
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        OutlinedTextField(
                            value = name, onValueChange = { name = it }, 
                            label = { Text(stringResource(R.string.onboard_display_name)) }, 
                            singleLine = true, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
                            trailingIcon = if (showRevertName) { { IconButton(onClick = { name = apiName }) { Icon(Icons.Default.Restore, "Revert", tint = MaterialTheme.colorScheme.primary) } } } else null
                        )
                    }

                    Spacer(Modifier.height(48.dp))

                    Button(
                        onClick = { 
                            vm.customName = name
                            vm.customPhotoUri = photoUri
                            vm.setTheme(vm.appTheme) // triggers save settings in VM
                            onClose()
                        },
                        modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp).height(56.dp).graphicsLayer { alpha = uiAlpha; translationY = uiTranslationY.toPx() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(50)
                    ) {
                        Text(stringResource(R.string.dict_btn_save), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}