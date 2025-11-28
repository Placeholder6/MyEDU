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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.ui.components.OshSuLogo

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(vm: MainViewModel) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Infinite Animation for Background Shapes
    val infiniteTransition = rememberInfiniteTransition(label = "background")
    
    // Slow, sweeping movement for giant shapes
    val scrollOffset1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2000f,
        animationSpec = infiniteRepeatable(tween(60000, easing = LinearEasing), RepeatMode.Restart),
        label = "scroll1"
    )
    val scrollOffset2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -2000f,
        animationSpec = infiniteRepeatable(tween(60000, easing = LinearEasing), RepeatMode.Restart),
        label = "scroll2"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(40000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        // --- GIANT BACKGROUND SHAPES ---
        // Top Row: Scrolling Right
        ExpressiveShapeRow(
            offset = scrollOffset1,
            rotation = rotation,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = (-50).dp) // Shift up to cover top edge
                .alpha(0.06f) // Subtle texture
        )
        // Bottom Row: Scrolling Left (Opposite)
        ExpressiveShapeRow(
            offset = scrollOffset2,
            rotation = -rotation,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(y = 100.dp) // Shift down to cover bottom edge
                .alpha(0.06f)
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
            Spacer(Modifier.height(64.dp))
            
            // 1. Top Logo
            OshSuLogo(
                modifier = Modifier.width(180.dp).height(90.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Welcome to MyEDU",
                style = MaterialTheme.typography.displayMedium, // Expressive Type
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

            Spacer(Modifier.height(56.dp))

            // 2. Expressive Input Form
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                leadingIcon = { Icon(Icons.Default.Email, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )

            Spacer(Modifier.height(16.dp))

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
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); vm.login(email, pass) })
            )

            if (vm.errorMsg != null) {
                Spacer(Modifier.height(20.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = vm.errorMsg!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            // 3. Expressive Button with LoadingIndicator
            Button(
                onClick = { vm.login(email, pass) },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                enabled = !vm.isLoading,
                shape = RoundedCornerShape(20.dp)
            ) {
                if (vm.isLoading) {
                    // STANDALONE EXPRESSIVE LOADING INDICATOR
                    LoadingIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Sign In", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
    
    Layout(
        content = {
            repeat(10) { index ->
                Icon(
                    imageVector = icons[index % icons.size],
                    contentDescription = null,
                    // UPDATED: Giant size (360dp), removed background box
                    modifier = Modifier
                        .size(360.dp) 
                        .rotate(rotation + (index * 60f)),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        modifier = modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        
        // Overlap them slightly (300 spacing for 360 width)
        val itemSpacing = 300 
        
        // Define a large height for the row to accommodate giant shapes
        layout(constraints.maxWidth, 400) { 
            var xPos = offset.toInt() % (constraints.maxWidth + 1500) 
            if (xPos > 0) xPos -= (constraints.maxWidth + 1500)
            
            placeables.forEach { placeable ->
                // Center vertically in the row
                val yPos = (400 - placeable.height) / 2
                placeable.placeRelative(x = xPos, y = yPos)
                xPos += itemSpacing
            }
        }
    }
}
