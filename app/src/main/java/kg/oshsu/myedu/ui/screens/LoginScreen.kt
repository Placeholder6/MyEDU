package kg.oshsu.myedu.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState // [FIX] Added missing import
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment // [FIX] Added for custom alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.ui.components.OshSuLogo
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material 3 Expressive Shapes (Simulated)
 */
object ExpressiveShapes {
    // "Very Sunny" - A multi-pointed starburst
    val VerySunny = GenericShape { size, _ ->
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val outerRadius = minOf(size.width, size.height) / 2f
        val innerRadius = outerRadius * 0.75f
        val points = 20

        moveTo(centerX + outerRadius * cos(0f), centerY + outerRadius * sin(0f))
        for (i in 1 until points * 2) {
            val r = if (i % 2 == 0) outerRadius else innerRadius
            val angle = i * Math.PI / points
            lineTo((centerX + r * cos(angle)).toFloat(), (centerY + r * sin(angle)).toFloat())
        }
        close()
    }

    // "Pill" - Standard Stadium shape
    val Pill = RoundedCornerShape(100) 

    // "Square" - Small rounded corners
    val Square = RoundedCornerShape(16.dp) 
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginScreen(vm: MainViewModel) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // --- ANIMATIONS ---

    // 1. Vertical Position: Button (Bottom) -> Loader (Center)
    // We animate the Bias from 0.9 (Bottom) to 0.0 (Center)
    val verticalBias by animateFloatAsState(
        targetValue = if (vm.isLoading || vm.isLoginSuccess) 0f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "VerticalBias"
    )

    // 2. Container Color: Solid (Button) -> Transparent (Loader) -> Solid (Success Expand)
    val containerColor by animateColorAsState(
        targetValue = if (vm.isLoading && !vm.isLoginSuccess) Color.Transparent else MaterialTheme.colorScheme.primary,
        animationSpec = tween(300),
        label = "ColorFade"
    )

    // 3. Width Morph: Wide Button -> Small Icon
    val width by animateDpAsState(
        targetValue = if (vm.isLoading || vm.isLoginSuccess) 64.dp else 280.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "Width"
    )

    // 4. Success Expansion: Scale up from 0 to fill screen
    val expandScale by animateFloatAsState(
        targetValue = if (vm.isLoginSuccess) 50f else 0f,
        animationSpec = tween(durationMillis = 2000, easing = LinearOutSlowInEasing),
        label = "Expand"
    )

    // 5. Content Fade: Hide form fields
    val contentAlpha by animateFloatAsState(
        targetValue = if (vm.isLoading || vm.isLoginSuccess) 0f else 1f,
        animationSpec = tween(400)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // --- BACKGROUND ---
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
            OshSuLogo(
                modifier = Modifier.width(160.dp).height(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(32.dp))
            Text("Welcome Back", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("Sign in to your account", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(56.dp))

            // Inputs
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.widthIn(max = 400.dp)) {
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) } },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); vm.login(email, pass) })
                )
            }
            if (vm.errorMsg != null) {
                Spacer(Modifier.height(20.dp))
                Text(vm.errorMsg!!, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.weight(1f))
        }

        // --- INTERACTIVE ELEMENT (Button / Loader / Reveal) ---
        // We use a Box with alignment bias to handle the "Zoom to Center" movement
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = BiasAlignment(0f, verticalBias) // [FIX] Used BiasAlignment instead of Alignment constructor
        ) {
            // 1. Success Expansion Layer (Behind everything)
            if (vm.isLoginSuccess) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .scale(expandScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            // 2. Button / Loader Layer
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = width, height = 64.dp)
                    .clip(RoundedCornerShape(50)) // Always rounded
                    .background(containerColor) // Becomes transparent during load
                    .clickable(enabled = !vm.isLoading && !vm.isLoginSuccess) { vm.login(email, pass) }
            ) {
                AnimatedContent(
                    targetState = vm.isLoading || vm.isLoginSuccess,
                    label = "ContentMorph"
                ) { isActivating ->
                    if (isActivating) {
                        // LOADING STATE: Just the indicator, no container
                        // Tint matches primary because background is transparent
                        LoadingIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.primary 
                        )
                    } else {
                        // BUTTON STATE: Text on solid background
                        Text(
                            "Sign In",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1
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
        animationSpec = infiniteRepeatable(tween(60000, easing = LinearEasing)), label = "rot"
    )

    Canvas(modifier = Modifier.fillMaxSize().alpha(0.3f)) {
        val w = size.width
        val h = size.height

        // 1. "Very Sunny" (Top Left) - Rotating
        rotate(rotation, pivot = Offset(0f, 0f)) {
            drawPath(
                path = createPathFromShape(ExpressiveShapes.VerySunny, Size(500f, 500f)),
                color = primary,
                style = Fill
            )
        }

        // 2. "Pill" (Bottom Right) - Floating
        rotate(-15f, pivot = Offset(w, h)) {
            translate(left = w - 300f, top = h - 200f) {
                drawPath(
                    path = createPathFromShape(ExpressiveShapes.Pill, Size(400f, 200f)),
                    color = secondary,
                    style = Fill
                )
            }
        }

        // 3. "Square" (Center Left) - Rotating opposite
        rotate(-rotation * 0.5f, pivot = Offset(0f, h / 2)) {
            translate(left = -100f, top = h / 2 - 150f) {
                drawPath(
                    path = createPathFromShape(ExpressiveShapes.Square, Size(300f, 300f)),
                    color = tertiary,
                    style = Fill
                )
            }
        }
    }
}

// Helper to convert GenericShape to Path for Canvas
fun createPathFromShape(shape: androidx.compose.ui.graphics.Shape, size: Size): Path {
    val p = Path()
    val s = shape.createOutline(size, LayoutDirection.Ltr, androidx.compose.ui.unit.Density(1f))
    
    // Handle simple Outlines (Rect/Rounded) and Generic Paths
    when(s) {
        is androidx.compose.ui.graphics.Outline.Generic -> return s.path
        is androidx.compose.ui.graphics.Outline.Rectangle -> p.addRect(s.rect)
        is androidx.compose.ui.graphics.Outline.Rounded -> p.addRoundRect(s.roundRect)
    }
    return p
}
