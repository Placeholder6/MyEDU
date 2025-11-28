package kg.oshsu.myedu.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable // [FIX 1] Added Import
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha // [FIX 2] Added Import
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(vm: MainViewModel) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // -- ANIMATION STATES --
    // Button Morphing
    val buttonWidth by animateDpAsState(
        targetValue = if (vm.isLoading || vm.isLoginSuccess) 64.dp else 280.dp,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "BtnWidth"
    )
    
    val buttonCornerRadius by animateIntAsState(
        targetValue = if (vm.isLoading || vm.isLoginSuccess) 50 else 16, 
        animationSpec = tween(400),
        label = "BtnCorner"
    )

    // Screen Expansion (Transition from Loader to Full Screen)
    val expandScale by animateFloatAsState(
        targetValue = if (vm.isLoginSuccess) 50f else 1f,
        animationSpec = tween(durationMillis = 600, easing = LinearOutSlowInEasing),
        label = "ExpandScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // --- DECORATIVE BACKGROUND (Expressive Solid Shapes) ---
        ExpressiveBackground()

        // --- FOREGROUND CONTENT ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(48.dp))
            
            // Logo Area
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

            // -- INPUT FIELDS --
            // Container for inputs with unified styling
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
                    shape = RoundedCornerShape(24.dp), // M3e Large Corner
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

            // --- MORPHING BUTTON ---
            // We use a Box with dynamic size and shape
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = buttonWidth, height = 64.dp)
                    // The Expand Scale applies here
                    .scale(expandScale)
                    .clip(RoundedCornerShape(percent = buttonCornerRadius))
                    .background(MaterialTheme.colorScheme.primary)
                    // If not success, allow click. If success, it just expands.
                    .then(
                         if (!vm.isLoading && !vm.isLoginSuccess) {
                             Modifier.padding(0.dp) // Dummy
                                 // Add clickable behavior only when it's a button
                                 .clickable { vm.login(email, pass) }
                         } else Modifier
                    )
            ) {
                // Content Switcher
                androidx.compose.animation.AnimatedContent(
                    targetState = vm.isLoading || vm.isLoginSuccess,
                    label = "BtnContent"
                ) { loading ->
                    if (loading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp
                        )
                    } else {
                        Text(
                            "Sign In",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }
            
            Spacer(Modifier.weight(1f))
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

        // 1. Giant Star/Flower Top Left
        rotate(rotation, pivot = androidx.compose.ui.geometry.Offset(0f, 0f)) {
            drawPath(
                path = createStarPath(12, 400f, 300f),
                color = primary,
                style = Fill
            )
        }

        // 2. Giant Circle Bottom Right
        drawCircle(
            color = secondary,
            radius = 350f,
            center = androidx.compose.ui.geometry.Offset(w, h * 0.9f)
        )

        // 3. Floating Rounded Rect (Pill) Middle Right
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

// Helper for Star Shape
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

// [FIX 3] Removed the incorrect helper function 'fun Modifier.alpha(...)'.
// We now rely on the standard import 'androidx.compose.ui.draw.alpha'.
