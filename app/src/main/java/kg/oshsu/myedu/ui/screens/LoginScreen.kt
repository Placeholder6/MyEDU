package kg.oshsu.myedu.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale 
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.ui.components.OshSuLogo

// --- SHAPE LIBRARY IMPLEMENTATION ---
object M3ExpressiveShapes {
    // 1. "Very Sunny": A 8-pointed star with sharp inner cuts (standard M3 "Burst" shape)
    fun verySunny(): RoundedPolygon {
        return RoundedPolygon.star(
            numVerticesPerRadius = 8,
            innerRadius = 0.78f,
            rounding = CornerRounding(radius = 0.15f), 
            innerRounding = CornerRounding(radius = 0f) 
        ).normalized()
    }

    // 2. "4 Sided Cookie": A 4-lobed shape (Flower/Clover)
    fun fourSidedCookie(): RoundedPolygon {
        return RoundedPolygon.star(
            numVerticesPerRadius = 4,
            innerRadius = 0.5f,
            rounding = CornerRounding(radius = 0.4f), 
            innerRounding = CornerRounding(radius = 0.4f) 
        ).normalized()
    }

    // 3. "Pill": Standard stadium shape (Rect with full rounding)
    fun pill(): RoundedPolygon {
        return RoundedPolygon(
            numVertices = 4,
            rounding = CornerRounding(radius = 1.0f) 
        ).normalized()
    }

    // 4. "Square": Standard rounded square (Squircle)
    fun square(): RoundedPolygon {
        return RoundedPolygon(
            numVertices = 4,
            rounding = CornerRounding(radius = 0.2f)
        ).normalized()
    }

    private fun RoundedPolygon.normalized(): RoundedPolygon {
        return this
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginScreen(vm: MainViewModel) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // --- ANIMATIONS ---
    val verticalBias by animateFloatAsState(
        targetValue = if (vm.isLoading || vm.isLoginSuccess) 0f else 0.85f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "VerticalBias"
    )

    val containerColor by animateColorAsState(
        targetValue = if (vm.isLoading && !vm.isLoginSuccess) Color.Transparent else MaterialTheme.colorScheme.primary,
        animationSpec = tween(300),
        label = "ColorFade"
    )

    val width by animateDpAsState(
        targetValue = if (vm.isLoading || vm.isLoginSuccess) 64.dp else 280.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "Width"
    )

    // Target is 1f (normal size) by default, scales to 50f to cover screen
    val expandScale by animateFloatAsState(
        targetValue = if (vm.isLoginSuccess) 50f else 1f,
        animationSpec = tween(durationMillis = 1500, easing = LinearOutSlowInEasing),
        label = "Expand"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (vm.isLoading || vm.isLoginSuccess) 0f else 1f,
        animationSpec = tween(400)
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // --- BACKGROUND SHAPES ---
        ExpressiveShapesBackground()

        // --- FORM CONTENT ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .alpha(contentAlpha)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(48.dp))
            OshSuLogo(modifier = Modifier.width(160.dp).height(80.dp))
            Spacer(Modifier.height(32.dp))
            
            Text(
                "Welcome Back", 
                style = MaterialTheme.typography.displaySmall, 
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Sign in to your account", 
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.height(48.dp))

            // Inputs Container
            Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.widthIn(max = 400.dp)) {
                
                // Input 1: Email
                OutlinedTextField(
                    value = email, 
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50), // Fully rounded Pill inputs
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    singleLine = true
                )

                // Input 2: Password
                OutlinedTextField(
                    value = pass, 
                    onValueChange = { pass = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    trailingIcon = { 
                        IconButton(onClick = { passwordVisible = !passwordVisible }) { 
                            Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) 
                        } 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    shape = RoundedCornerShape(50), // Fully rounded Pill inputs
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); vm.login(email, pass) }),
                    singleLine = true
                )
            }

            if (vm.errorMsg != null) {
                Spacer(Modifier.height(24.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        vm.errorMsg!!, 
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(Modifier.weight(1f))
        }

        // --- BUTTON / LOADER ---
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = BiasAlignment(0f, verticalBias)
        ) {
            // 1. SCALING BACKGROUND LAYER
            // This box acts as the background. It scales up to fill the screen on success.
            Box(
                modifier = Modifier
                    .size(width = width, height = 64.dp)
                    .scale(expandScale)
                    .clip(RoundedCornerShape(100))
                    .background(containerColor)
            )

            // 2. CONTENT LAYER
            // This box stays at the normal size (doesn't scale up 50x) so the loader remains visible.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = width, height = 64.dp)
                    .clip(RoundedCornerShape(100))
                    .clickable(enabled = !vm.isLoading && !vm.isLoginSuccess) { vm.login(email, pass) }
            ) {
                AnimatedContent(
                    targetState = vm.isLoading || vm.isLoginSuccess,
                    label = "ContentMorph"
                ) { isActivating ->
                    if (isActivating) {
                        // Switch color: Primary (Normal Loading) -> OnPrimary (Success/Zooming against blue bg)
                        val indicatorColor by animateColorAsState(
                            targetValue = if (vm.isLoginSuccess) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            label = "LoaderColor"
                        )
                        
                        LoadingIndicator(
                            modifier = Modifier.size(32.dp),
                            color = indicatorColor
                        )
                    } else {
                        Text(
                            "Sign In",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveShapesBackground() {
    // Capture colors outside the Canvas scope
    val primary = MaterialTheme.colorScheme.primaryContainer
    val secondary = MaterialTheme.colorScheme.secondaryContainer
    val tertiary = MaterialTheme.colorScheme.tertiaryContainer
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    val infiniteTransition = rememberInfiniteTransition(label = "bg_anim")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(80000, easing = LinearEasing)), label = "rot"
    )
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 40f,
        animationSpec = infiniteRepeatable(tween(5000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float"
    )

    Canvas(modifier = Modifier.fillMaxSize().alpha(0.3f)) { 
        val w = size.width
        val h = size.height

        // 1. "Very Sunny" (Top Left)
        rotate(rotation, pivot = Offset(0f, 0f)) {
            translate(left = -50f, top = -50f) {
                scale(scaleX = 400f, scaleY = 400f, pivot = Offset.Zero) {
                    val path = M3ExpressiveShapes.verySunny().toPath().asComposePath()
                    drawPath(path, primary, style = Fill)
                }
            }
        }

        // 2. "4 Sided Cookie" (Bottom Right)
        rotate(-15f, pivot = Offset(w, h)) {
            translate(left = w - 300f, top = h - 250f + floatY) {
                scale(scaleX = 300f, scaleY = 300f, pivot = Offset.Zero) {
                    val path = M3ExpressiveShapes.fourSidedCookie().toPath().asComposePath()
                    drawPath(path, secondary, style = Fill)
                }
            }
        }

        // 3. "Pill" (Center Left) - Stretched
        rotate(rotation * 0.5f, pivot = Offset(0f, h/2)) {
            translate(left = -100f, top = h/2 - 100f) {
                scale(scaleX = 300f, scaleY = 150f, pivot = Offset.Zero) {
                    val path = M3ExpressiveShapes.pill().toPath().asComposePath()
                    drawPath(path, tertiary, style = Fill)
                }
            }
        }
        
        // 4. "Square" (Top Right)
        translate(left = w - 150f, top = 100f) {
            rotate(-rotation, pivot = Offset(75f, 75f)) {
                 scale(scaleX = 150f, scaleY = 150f, pivot = Offset.Zero) {
                     val path = M3ExpressiveShapes.square().toPath().asComposePath()
                     drawPath(path, surfaceVariant, style = Fill)
                 }
            }
        }
    }
}