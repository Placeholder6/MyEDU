package kg.oshsu.myedu.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.ui.components.OshSuLogo

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginScreen(vm: MainViewModel) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // --- SMOOTH TRANSITION CONTROLLERS ---

    // 1. Button Width Morph: Wide -> Compact (Circle/Square)
    // Uses a low-stiffness spring for that "Expressive" motion feel
    val buttonWidth by animateDpAsState(
        targetValue = if (vm.isLoading || vm.isLoginSuccess) 64.dp else 280.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy, 
            stiffness = Spring.StiffnessLow
        ),
        label = "BtnWidth"
    )

    // 2. Button Corner Morph: Rounded Rect (24dp) -> Circle (50%)
    val buttonCornerPercent by animateIntAsState(
        targetValue = if (vm.isLoading || vm.isLoginSuccess) 50 else 16, 
        animationSpec = tween(400),
        label = "BtnCorner"
    )

    // 3. Screen Expansion: Scales up to fill screen on success
    // Matches the reference video style where the indicator/button grows to reveal the next screen
    val expandScale by animateFloatAsState(
        targetValue = if (vm.isLoginSuccess) 40f else 1f,
        animationSpec = tween(
            durationMillis = 800, 
            easing = FastOutSlowInEasing
        ),
        label = "ExpandScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // --- BACKGROUND DECORATION ---
        ExpressiveBackground()

        // --- FOREGROUND CONTENT ---
        // We fade out the form content when the success animation starts
        val contentAlpha by animateFloatAsState(
            targetValue = if (vm.isLoginSuccess) 0f else 1f, 
            animationSpec = tween(300)
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .alpha(contentAlpha) // Fade out on success
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
                text = "Welcome Back",
                style = MaterialTheme.typography.displaySmall, 
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Sign in to your account",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(56.dp))

            // Inputs
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
                Text(
                    text = vm.errorMsg!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(48.dp))
            Spacer(Modifier.weight(1f))
        }

        // --- MORPHING BUTTON & LOADING INDICATOR ---
        // This Box sits on top and handles the morphing from Button -> Loader -> Full Screen
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                // 1. Scale for Expansion (Success)
                .scale(expandScale)
                // 2. Morph Size (Button -> Loader)
                .size(width = buttonWidth, height = 64.dp)
                // 3. Morph Shape (Rect -> Circle)
                .clip(RoundedCornerShape(percent = buttonCornerPercent))
                .background(MaterialTheme.colorScheme.primary)
                // Clickable only when interactive
                .then(
                     if (!vm.isLoading && !vm.isLoginSuccess) {
                         Modifier.clickable { vm.login(email, pass) }
                     } else Modifier
                )
        ) {
            // Smoothly switch between "Sign In" text and "LoadingIndicator"
            AnimatedContent(
                targetState = vm.isLoading || vm.isLoginSuccess,
                label = "BtnContent"
            ) { loading ->
                if (loading) {
                    // --- REAL MATERIAL 3 EXPRESSIVE LOADING INDICATOR ---
                    // This component handles the morphing polygon shapes internally.
                    // We tint it to match the button's content color (onPrimary).
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

// --- EXPRESSIVE BACKGROUND ---
@Composable
fun ExpressiveBackground() {
    val primary = MaterialTheme.colorScheme.primaryContainer
    val secondary = MaterialTheme.colorScheme.secondaryContainer
    val tertiary = MaterialTheme.colorScheme.tertiaryContainer

    val infiniteTransition = rememberInfiniteTransition(label = "bg_anim")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(60000, easing = LinearEasing)), label = "rot"
    )

    Canvas(modifier = Modifier.fillMaxSize().alpha(0.4f)) {
        val w = size.width
        val h = size.height

        rotate(rotation, pivot = androidx.compose.ui.geometry.Offset(0f, 0f)) {
            drawPath(
                path = createStarPath(12, 400f, 300f),
                color = primary,
                style = Fill
            )
        }

        drawCircle(
            color = secondary,
            radius = 350f,
            center = androidx.compose.ui.geometry.Offset(w, h * 0.9f)
        )

        rotate(-rotation * 0.5f, pivot = androidx.compose.ui.geometry.Offset(w, h * 0.4f)) {
             drawRoundRect(
                 color = tertiary,
                 topLeft = androidx.compose.ui.geometry.Offset(w - 200f, h * 0.3f),
                 size = androidx.compose.ui.geometry.Size(300f, 150f),
                 cornerRadius = androidx.compose.ui.geometry.CornerRadius(75f, 75f)
             )
        }
    }
}

// Helper for Star Shape (Background only)
fun createStarPath(points: Int, outerRadius: Float, innerRadius: Float): Path {
    val path = Path()
    val angleStep = Math.PI / points
    
    for (i in 0 until 2 * points) {
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val angle = i * angleStep
        val x = (r * Math.cos(angle)).toFloat()
        val y = (r * Math.sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}
