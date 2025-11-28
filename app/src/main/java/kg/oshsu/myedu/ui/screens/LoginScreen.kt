package kg.oshsu.myedu.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.ui.components.OshSuLogo

// Opt-in for Expressive APIs (LoadingIndicator)
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(vm: MainViewModel) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Infinite Animation for Background Shapes
    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val scrollOffset1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart),
        label = "scroll1"
    )
    val scrollOffset2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -1000f,
        animationSpec = infiniteRepeatable(tween(25000, easing = LinearEasing), RepeatMode.Restart),
        label = "scroll2"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest) // Expressive base
    ) {
        // --- BACKGROUND SHAPES LAYERS ---
        // Row 1: Scrolling Right
        ExpressiveShapeRow(
            offset = scrollOffset1,
            rotation = rotation,
            modifier = Modifier.align(Alignment.TopStart).offset(y = 120.dp).alpha(0.1f)
        )
        // Row 2: Scrolling Left (Opposite)
        ExpressiveShapeRow(
            offset = scrollOffset2,
            rotation = -rotation, // Rotate opposite too
            modifier = Modifier.align(Alignment.BottomStart).offset(y = (-80).dp).alpha(0.1f)
        )

        // --- FOREGROUND CONTENT ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 1. Top Logo (Moved Up)
            Spacer(Modifier.height(48.dp))
            OshSuLogo(
                modifier = Modifier.width(160.dp).height(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Welcome to MyEDU",
                style = MaterialTheme.typography.displaySmall, // Expressive Type
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Sign in to access your portal",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(48.dp))

            // 2. Expressive Input Form
            // Email
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                leadingIcon = { Icon(Icons.Default.Email, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(24.dp), // Extra Large Corners
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )

            Spacer(Modifier.height(16.dp))

            // Password
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
                shape = RoundedCornerShape(24.dp), // Extra Large Corners
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); vm.login(email, pass) })
            )

            if (vm.errorMsg != null) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = vm.errorMsg!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // 3. Expressive Button with Loading Indicator
            Button(
                onClick = { vm.login(email, pass) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !vm.isLoading,
                shape = RoundedCornerShape(18.dp) // Expressive Medium Shape
            ) {
                if (vm.isLoading) {
                    // STANDALONE EXPRESSIVE LOADING INDICATOR
                    LoadingIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Sign In", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(Modifier.weight(1f))
        }
    }
}

// Helper to render a row of shapes
@Composable
fun ExpressiveShapeRow(offset: Float, rotation: Float, modifier: Modifier = Modifier) {
    val icons = listOf(Icons.Default.Star, Icons.Default.Hexagon, Icons.Default.Circle, Icons.Default.Square)
    
    // Custom Layout to handle the infinite scrolling offset visually
    Layout(
        content = {
            repeat(10) { index ->
                Icon(
                    imageVector = icons[index % icons.size],
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .rotate(rotation + (index * 45f)) // Rotate in place + offset
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), 
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(12.dp), // Inner padding for shape visual
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        modifier = modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        layout(constraints.maxWidth, 100) {
            var xPos = offset.toInt() % (constraints.maxWidth + 200) // Wrap around logic
            if (xPos > 0) xPos -= (constraints.maxWidth + 200) // Ensure seamless loop start
            
            placeables.forEach { placeable ->
                placeable.placeRelative(x = xPos, y = 0)
                xPos += 160 // Spacing between shapes
            }
        }
    }
}
