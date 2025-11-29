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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
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
import kotlin.math.max

// --- SHAPE LIBRARY IMPLEMENTATION ---
object M3ExpressiveShapes {
    // 1. "Very Sunny": A 12-pointed star with sharp inner cuts and rounded tips
    fun verySunny(radius: Float = 1f): RoundedPolygon {
        return RoundedPolygon.star(
            numVerticesPerRadius = 12,
            innerRadius = 0.6f,
            rounding = CornerRounding(radius = 0.1f), // Slightly rounded tips
            innerRounding = CornerRounding(radius = 0f) // Sharp inner corners
        ).normalized()
    }

    // 2. "4 Sided Cookie": A 4-sided scalloped shape (Clover-like)
    fun fourSidedCookie(radius: Float = 1f): RoundedPolygon {
        return RoundedPolygon.star(
            numVerticesPerRadius = 4,
            innerRadius = 0.5f,
            rounding = CornerRounding(radius = 0.4f), // Soft outer lobes
            innerRounding = CornerRounding(radius = 0.4f) // Soft inner joins
        ).normalized()
    }

    // 3. "Pill": The standard stadium shape (approximated by a very rounded rectangle if pill() isn't available)
    fun pill(radius: Float = 1f): RoundedPolygon {
        // Creates a rectangle with maximum corner rounding
        return RoundedPolygon(
            numVertices = 4,
            rounding = CornerRounding(radius = 1.0f) // Fully rounded
        ).normalized()
        // Note: For a true "long" pill, we stretch this in the Canvas using scale
    }

    // 4. "Square": A rectangle with standard Material 3 rounding (Squircle-ish)
    fun square(radius: Float = 1f): RoundedPolygon {
        return RoundedPolygon(
            numVertices = 4,
            rounding = CornerRounding(radius = 0.2f)
        ).normalized()
    }

    // Helper to normalize shape size to 1x1 roughly
    private fun RoundedPolygon.normalized(): RoundedPolygon {
        // In a real app, you might scale this based on bounds. 
        // For simplicity, we return as is since Graphics Shapes defaults to radius ~1
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

    val expandScale by animateFloatAsState(
        targetValue = if (vm.isLoginSuccess) 50f else 0f,
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
            
            // Text Contrast Fix: Ensure text is explicitly colored for the surface
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
                
                // Contrast Fix: Added containerColor = surfaceContainerHigh
                // This ensures the input is legible even if a shape floats behind it.
                OutlinedTextField(
                    value = email, 
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp), // Pill Shape UI
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
                    shape = RoundedCornerShape(24.dp),
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
                // Error Container for better visibility
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
            if (vm.isLoginSuccess) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .scale(expandScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = width, height = 64.dp)
                    .clip(RoundedCornerShape(100))
                    .background(containerColor)
                    .clickable(enabled = !vm.isLoading && !vm.isLoginSuccess) { vm.login(email, pass) }
            ) {
                AnimatedContent(
                    targetState = vm.isLoading || vm.isLoginSuccess,
                    label = "ContentMorph"
                ) { isActivating ->
                    if (isActivating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary, // Contrast Fix
                            strokeWidth = 3.dp
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
    val primary = MaterialTheme.colorScheme.primaryContainer
    val secondary = MaterialTheme.colorScheme.secondaryContainer
    val tertiary = MaterialTheme.colorScheme.tertiaryContainer

    val infiniteTransition = rememberInfiniteTransition(label = "bg_anim")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(80000, easing = LinearEasing)), label = "rot"
    )
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 40f,
        animationSpec = infiniteRepeatable(tween(5000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float"
    )

    Canvas(modifier = Modifier.fillMaxSize().alpha(0.3f)) { // Low alpha so text stands out
        val w = size.width
        val h = size.height

        // 1. "Very Sunny" (Top Left)
        rotate(rotation, pivot = Offset(0f, 0f)) {
            translate(left = -50f, top = -50f) {
                // Scale up the 1x1 polygon to 400x400
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

        // 3. "Pill" (Center Left) - Stretched to look like a capsule
        rotate(rotation * 0.5f, pivot = Offset(0f, h/2)) {
            translate(left = -100f, top = h/2 - 100f) {
                // Scale X more than Y to make it a "Pill"
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
                     drawPath(path, MaterialTheme.colorScheme.surfaceVariant, style = Fill)
                 }
            }
        }
    }
}
