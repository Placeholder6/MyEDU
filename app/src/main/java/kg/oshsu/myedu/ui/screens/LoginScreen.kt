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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.ui.components.OshSuLogo
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material 3 Expressive Shapes (Mathematically approximated)
 */
object ExpressiveShapes {
    // "Starburst" - A smooth, wavy sun-like shape using Cubic Beziers
    val Starburst = GenericShape { size, _ ->
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val outerRadius = size.width / 2f
        val innerRadius = outerRadius * 0.85f // Less indent for a "soft" wave
        val points = 12 // 12-point starburst

        val angleStep = (2 * Math.PI / points).toFloat()
        
        // Start at top
        moveTo(centerX + outerRadius * cos(0f), centerY + outerRadius * sin(0f))

        for (i in 1..points) {
            val currentAngle = (i * angleStep) - angleStep
            val nextAngle = i * angleStep
            val midAngle = currentAngle + (angleStep / 2)

            // Control points for bezier (approximating the curve)
            val cp1X = centerX + outerRadius * cos(currentAngle + 0.1f)
            val cp1Y = centerY + outerRadius * sin(currentAngle + 0.1f)
            
            val innerX = centerX + innerRadius * cos(midAngle)
            val innerY = centerY + innerRadius * sin(midAngle)

            val cp2X = centerX + outerRadius * cos(nextAngle - 0.1f)
            val cp2Y = centerY + outerRadius * sin(nextAngle - 0.1f)
            
            val destX = centerX + outerRadius * cos(nextAngle)
            val destY = centerY + outerRadius * sin(nextAngle)

            // Curve to inner point
            quadraticBezierTo(innerX, innerY, destX, destY)
        }
        close()
    }

    // "Clover" - 4 rounded lobes
    val Clover = GenericShape { size, _ ->
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        
        moveTo(cx, 0f)
        // Top right lobe
        cubicTo(w, 0f, w, cy, cx, cy)
        // Bottom right lobe
        cubicTo(w, h, cx, h, cx, cy)
        // Bottom left lobe
        cubicTo(0f, h, 0f, cy, cx, cy)
        // Top left lobe
        cubicTo(0f, 0f, cx, 0f, cx, 0f)
        close()
    }

    // "Squircle" / Super-Ellipse approximation
    val SuperContainer = RoundedCornerShape(32.dp) 
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
        targetValue = if (vm.isLoading || vm.isLoginSuccess) 0f else 0.85f, // Slightly higher than bottom
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "VerticalBias"
    )

    val containerColor by animateColorAsState(
        targetValue = if (vm.isLoading && !vm.isLoginSuccess) Color.Transparent else MaterialTheme.colorScheme.primary,
        animationSpec = tween(300),
        label = "ColorFade"
    )

    val width by animateDpAsState(
        targetValue = if (vm.isLoading || vm.isLoginSuccess) 64.dp else 300.dp, // Wider default button
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
            Spacer(Modifier.height(40.dp))
            // Logo with Expressive Tint
            OshSuLogo(
                modifier = Modifier.width(140.dp).height(70.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(32.dp))
            
            Text("Welcome Back", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(
                "Sign in to continue", 
                style = MaterialTheme.typography.bodyLarge, 
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.height(48.dp))

            // Inputs Container
            Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.widthIn(max = 400.dp)) {
                OutlinedTextField(
                    value = email, 
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    modifier = Modifier.fillMaxWidth(),
                    // Material 3 Expressive "Extra Round" / Pill shape for inputs
                    shape = RoundedCornerShape(28.dp), 
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
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
                    shape = RoundedCornerShape(28.dp), // Consistent Pill shape
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); vm.login(email, pass) }),
                    singleLine = true
                )
            }

            if (vm.errorMsg != null) {
                Spacer(Modifier.height(24.dp))
                // Error displayed in a soft container
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        vm.errorMsg!!, 
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(Modifier.weight(1f))
        }

        // --- INTERACTIVE ELEMENT (Button / Loader / Reveal) ---
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = BiasAlignment(0f, verticalBias)
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
                    .clip(RoundedCornerShape(100)) // Full stadium/pill
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
                            color = MaterialTheme.colorScheme.primary,
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
    
    // Use LocalDensity to ensure shapes scale correctly on different screens
    val density = LocalDensity.current

    val infiniteTransition = rememberInfiniteTransition(label = "bg_anim")
    
    // Slower, subtle rotation
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(90000, easing = LinearEasing)), label = "rot"
    )
    
    // Gentle floating
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 50f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "float"
    )

    Canvas(modifier = Modifier.fillMaxSize().alpha(0.4f)) {
        val w = size.width
        val h = size.height

        // 1. "Starburst" (Top Left) - Rotating
        rotate(rotation, pivot = Offset(0f, 0f)) {
            translate(left = -50f, top = -50f) {
                drawPath(
                    path = createPathFromShape(ExpressiveShapes.Starburst, Size(400f, 400f), density),
                    color = primary,
                    style = Fill
                )
            }
        }

        // 2. "Clover" (Bottom Right) - Floating
        rotate(-10f, pivot = Offset(w, h)) {
            translate(left = w - 250f, top = h - 350f + floatY) {
                drawPath(
                    path = createPathFromShape(ExpressiveShapes.Clover, Size(300f, 300f), density),
                    color = secondary,
                    style = Fill
                )
            }
        }

        // 3. "SuperContainer" (Center Left) - Rotating opposite
        rotate(-rotation * 0.3f, pivot = Offset(0f, h / 2)) {
            translate(left = -80f, top = h / 2 - 100f) {
                drawPath(
                    path = createPathFromShape(ExpressiveShapes.SuperContainer, Size(250f, 250f), density),
                    color = tertiary,
                    style = Fill
                )
            }
        }
    }
}

// Updated Helper: Accepts Density for accurate sizing
fun createPathFromShape(shape: Shape, size: Size, density: Density): Path {
    val p = Path()
    val s = shape.createOutline(size, LayoutDirection.Ltr, density)
    
    when(s) {
        is Outline.Generic -> return s.path
        is Outline.Rectangle -> p.addRect(s.rect)
        is Outline.Rounded -> p.addRoundRect(s.roundRect)
    }
    return p
}