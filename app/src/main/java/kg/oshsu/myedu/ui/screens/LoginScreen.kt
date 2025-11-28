package kg.oshsu.myedu.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.ui.components.OshSuLogo
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material 3 Expressive Shape Library (Simulation)
 * Creates organic, polygonal shapes for the "Expressive" look.
 */
object ExpressiveShapes {
    // A 12-point starburst often used in M3 Expressive loading indicators
    val Starburst: Shape = GenericShape { size: androidx.compose.ui.geometry.Size, _: LayoutDirection ->
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val outerRadius = minOf(size.width, size.height) / 2f
        val innerRadius = outerRadius * 0.6f
        val points = 12

        moveTo(centerX + outerRadius * cos(0f), centerY + outerRadius * sin(0f))
        for (i in 1 until points * 2) {
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            val angle = i * Math.PI / points
            lineTo(
                (centerX + radius * cos(angle)).toFloat(),
                (centerY + radius * sin(angle)).toFloat()
            )
        }
        close()
    }

    // A customized squircle for input fields
    val Squircle: Shape = RoundedCornerShape(24.dp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(vm: MainViewModel) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // --- ANIMATION CONTROLLERS ---
    
    // 1. Morph Width: Button (Wide) -> Loading (Square)
    val buttonWidth by animateDpAsState(
        targetValue = if (vm.isLoading || vm.isLoginSuccess) 64.dp else 320.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow), // Expressive Spring
        label = "width"
    )

    // 2. Shape Toggle: Rounded Rect -> Starburst
    // We toggle clip shapes based on state. 
    val isRound = vm.isLoading || vm.isLoginSuccess
    
    // 3. Rotation: Spins when loading
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "rotation"
    )
    val actualRotation = if (vm.isLoading) rotation else 0f

    // 4. Expansion: Scales up to fill screen on success
    val scale by animateFloatAsState(
        targetValue = if (vm.isLoginSuccess) 40f else 1f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // --- MAIN FORM CONTENT ---
        // Hidden when success expansion covers it
        if (!vm.isLoginSuccess) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.widthIn(max = 480.dp)
            ) {
                // Bold, Expressive Header
                OshSuLogo(modifier = Modifier.height(60.dp).width(120.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(32.dp))
                
                Text(
                    "Hello!",
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Sign in to continue",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                
                Spacer(Modifier.height(48.dp))

                // Expressive Inputs (Solid colors, Squircle shapes)
                TextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = { Text("Student Email") },
                    modifier = Modifier.fillMaxWidth().clip(ExpressiveShapes.Squircle),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )
                
                Spacer(Modifier.height(16.dp))
                
                TextField(
                    value = pass,
                    onValueChange = { pass = it },
                    placeholder = { Text("Password") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clip(ExpressiveShapes.Squircle),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { 
                        focusManager.clearFocus()
                        vm.login(email, pass) 
                    })
                )

                if (vm.errorMsg != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(vm.errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
                
                Spacer(Modifier.height(48.dp))
            }
        }

        // --- MORPHING BUTTON / LOADING INDICATOR ---
        // Placed in a Box to allow it to expand over everything else
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                // 1. Apply Scale (for expansion effect)
                .scale(scale)
                // 2. Apply Size (morphs from wide button to small icon)
                .size(width = buttonWidth, height = 64.dp)
                // 3. Apply Rotation (only when loading)
                .graphicsLayer { rotationZ = actualRotation }
                // 4. Apply Shape (morphs from Squircle to Starburst)
                .clip(if (isRound) ExpressiveShapes.Starburst else ExpressiveShapes.Squircle)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(enabled = !vm.isLoading && !vm.isLoginSuccess) {
                    vm.login(email, pass)
                }
        ) {
            // Content inside the shape
            if (!vm.isLoading && !vm.isLoginSuccess) {
                Text(
                    "Login",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            // When loading, the shape itself IS the indicator (spinning starburst).
            // The Box background acts as the spinner.
        }
    }
}
