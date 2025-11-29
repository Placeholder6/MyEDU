package kg.oshsu.myedu.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size // [FIXED] Added Import
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate // [FIXED] Added Import
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
 * Shape Utilities
 */
object ExpressiveShapes {
    // 12-point starburst
    val Starburst = GenericShape { size, _ ->
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val outerRadius = minOf(size.width, size.height) / 2f
        val innerRadius = outerRadius * 0.6f
        val points = 12
        moveTo(centerX + outerRadius * cos(0f), centerY + outerRadius * sin(0f))
        for (i in 1 until points * 2) {
            val r = if (i % 2 == 0) outerRadius else innerRadius
            val angle = i * Math.PI / points
            lineTo((centerX + r * cos(angle)).toFloat(), (centerY + r * sin(angle)).toFloat())
        }
        close()
    }
    
    // 4-leaf clover-like shape
    val Clover = GenericShape { size, _ ->
        val w = size.width
        val h = size.height
        moveTo(w/2, h/2)
        cubicTo(w/2, 0f, w, 0f, w, h/2)
        cubicTo(w, h, w/2, h, w/2, h/2)
        cubicTo(w/2, h, 0f, h, 0f, h/2)
        cubicTo(0f, 0f, w/2, 0f, w/2, h/2)
        close()
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
    
    // 1. Zoom Reveal: Slow expansion ONLY on success
    val expandScale by animateFloatAsState(
        targetValue = if (vm.isLoginSuccess) 50f else 0f, // Start at 0, grow huge
        animationSpec = tween(
            durationMillis = 2000, // Slow zoom
            easing = LinearOutSlowInEasing
        ),
        label = "ExpandScale"
    )

    // 2. Content Fade: Hide form elements smoothly
    val contentAlpha by animateFloatAsState(
        targetValue = if (vm.isLoading || vm.isLoginSuccess) 0f else 1f,
        animationSpec = tween(500)
    )
    
    // 3. Button Width Morph: Wide -> Compact (Circle/Square)
    val buttonWidth by animateDpAsState(
        targetValue = if (vm.isLoading || vm.isLoginSuccess) 64.dp else 280.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy, 
            stiffness = Spring.StiffnessLow
        ),
        label = "BtnWidth"
    )

    // 4. Button Corner Morph: Rounded Rect (24dp) -> Circle (50%)
    val buttonCornerPercent by animateIntAsState(
        targetValue = if (vm.isLoading || vm.isLoginSuccess) 50 else 16, 
        animationSpec = tween(400),
        label = "BtnCorner"
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
                .alpha(contentAlpha) // Hides during load/success
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
            Text(
                "Welcome Back",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Sign in to your account",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(56.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.widthIn(max = 400.dp)
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
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
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); vm.login(email, pass) })
                )
            }
            if (vm.errorMsg != null) {
                Spacer(Modifier.height(20.dp))
                Text(vm.errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(48.dp))
            
            // --- LOGIN BUTTON (Visible only when NOT loading/success to avoid double drawing) ---
            if (!vm.isLoading && !vm.isLoginSuccess) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 0.dp) // Reset padding
                ) {
                   // This space is reserved; the actual morphing box is below
                }
            }
            Spacer(Modifier.weight(1f))
        }

        // --- MORPHING BUTTON / LOADING INDICATOR / SUCCESS EXPANSION ---
        // This Box sits on top to handle the transitions smoothly
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                // 1. Success Expansion
                .scale(if (vm.isLoginSuccess) expandScale + 1f else 1f) // expandScale starts at 0, we need it to grow
                // 2. Morph Size
                .size(width = buttonWidth, height = 64.dp)
                // 3. Morph Shape
                .clip(RoundedCornerShape(percent = buttonCornerPercent))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(enabled = !vm.isLoading && !vm.isLoginSuccess) {
                    vm.login(email, pass)
                }
        ) {
             AnimatedContent(
                targetState = vm.isLoading || vm.isLoginSuccess,
                label = "BtnContent"
            ) { loading ->
                if (loading) {
                    // Just the icon, no container
                    LoadingIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        "Sign In",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        maxLines = 1
                    )
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

        // 1. Rotating Starburst (Top Left)
        rotate(rotation, pivot = Offset(0f, 0f)) {
            drawPath(
                path = createPathFromShape(ExpressiveShapes.Starburst, Size(500f, 500f)),
                color = primary,
                style = Fill
            )
        }

        // 2. Giant Circle (Bottom Right)
        drawCircle(
            color = secondary,
            radius = 400f,
            center = Offset(w, h * 0.9f)
        )

        // 3. Floating Clover (Center Right)
        rotate(-rotation * 0.8f, pivot = Offset(w, h * 0.4f)) {
            translate(left = w - 300f, top = h * 0.3f) {
                drawPath(
                     path = createPathFromShape(ExpressiveShapes.Clover, Size(300f, 300f)),
                     color = tertiary,
                     style = Fill
                )
            }
        }
    }
}

// Helper to convert GenericShape to Path for Canvas
fun createPathFromShape(shape: GenericShape, size: androidx.compose.ui.geometry.Size): Path {
    val p = Path()
    // GenericShape closure
    val s = shape.createOutline(size, LayoutDirection.Ltr, androidx.compose.ui.unit.Density(1f))
    if (s is androidx.compose.ui.graphics.Outline.Generic) {
        return s.path
    }
    return p
}
