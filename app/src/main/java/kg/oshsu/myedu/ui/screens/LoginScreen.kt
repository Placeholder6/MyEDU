package kg.oshsu.myedu.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.scale 
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.ui.components.OshSuLogo
import kotlin.math.sqrt
import kotlin.random.Random

// --- SHAPE LIBRARY IMPLEMENTATION ---
object M3ExpressiveShapes {
    // 1. "Very Sunny": A 8-pointed star with sharp inner cuts
    fun verySunny(): RoundedPolygon {
        return RoundedPolygon.star(
            numVerticesPerRadius = 8,
            innerRadius = 0.78f,
            rounding = CornerRounding(radius = 0.15f), 
            innerRounding = CornerRounding(radius = 0f) 
        ).normalized()
    }

    // 2. "4 Sided Cookie": A 4-lobed shape
    fun fourSidedCookie(): RoundedPolygon {
        return RoundedPolygon.star(
            numVerticesPerRadius = 4,
            innerRadius = 0.5f,
            rounding = CornerRounding(radius = 0.4f), 
            innerRounding = CornerRounding(radius = 0.4f) 
        ).normalized()
    }

    // 3. "12 Sided Cookie": 12-lobed shape for Login Success
    fun twelveSidedCookie(): RoundedPolygon {
        return RoundedPolygon.star(
            numVerticesPerRadius = 12,
            innerRadius = 0.8f,
            rounding = CornerRounding(radius = 0.2f),
            innerRounding = CornerRounding(radius = 0.2f)
        ).normalized()
    }

    // 4. "Pill": Standard stadium shape
    fun pill(): RoundedPolygon {
        return RoundedPolygon(
            numVertices = 4,
            rounding = CornerRounding(radius = 1.0f) 
        ).normalized()
    }

    // 5. "Square": Standard rounded square
    fun square(): RoundedPolygon {
        return RoundedPolygon(
            numVertices = 4,
            rounding = CornerRounding(radius = 0.2f)
        ).normalized()
    }

    // 6. "Triangle": Rounded 3-sided shape
    fun triangle(): RoundedPolygon {
        return RoundedPolygon(
            numVertices = 3,
            rounding = CornerRounding(radius = 0.2f)
        ).normalized()
    }

    // 7. "Scallop": Wavy 10-sided shape
    fun scallop(): RoundedPolygon {
        return RoundedPolygon.star(
            numVerticesPerRadius = 10,
            innerRadius = 0.9f,
            rounding = CornerRounding(radius = 0.5f),
            innerRounding = CornerRounding(radius = 0.5f)
        ).normalized()
    }
    
    // 8. "Flower": 6-sided flower
    fun flower(): RoundedPolygon {
        return RoundedPolygon.star(
            numVerticesPerRadius = 6,
            innerRadius = 0.6f,
            rounding = CornerRounding(radius = 0.8f),
            innerRounding = CornerRounding(radius = 0.2f)
        ).normalized()
    }
}

// Helper Class to convert RoundedPolygon to a Compose Shape
class PolygonShape(private val polygon: RoundedPolygon) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = android.graphics.Path()
        polygon.toPath(p) 
        
        val matrix = android.graphics.Matrix()
        val bounds = android.graphics.RectF()
        p.computeBounds(bounds, true)
        
        val scaleX = size.width / bounds.width()
        val scaleY = size.height / bounds.height()
        
        matrix.postTranslate(-bounds.left, -bounds.top)
        matrix.postScale(scaleX, scaleY)
        p.transform(matrix)
        
        return Outline.Generic(p.asComposePath())
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginScreen(vm: MainViewModel) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // WRAP IN BOX WITH CONSTRAINTS to calculate precise scale
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // --- SCALE CALCULATION ---
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        
        // Calculate dynamic scale factor to cover the screen
        val calculatedScale = remember(screenWidth, screenHeight) {
            val widthVal = screenWidth.value
            val heightVal = screenHeight.value
            val screenDiagonal = sqrt((widthVal * widthVal) + (heightVal * heightVal))
            val buttonRadius = 32f
            val innerRadiusFactor = 0.8f 
            val effectiveRadius = buttonRadius * innerRadiusFactor

            // Required Scale = (ScreenDiagonal / 2) / EffectiveRadius
            ((screenDiagonal / 2f) / effectiveRadius) * 1.1f
        }

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
            targetValue = if (vm.isLoginSuccess) calculatedScale else 1f,
            animationSpec = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            label = "Expand"
        )

        val rotation by animateFloatAsState(
            targetValue = if (vm.isLoginSuccess) 360f else 0f,
            animationSpec = tween(durationMillis = 2000, easing = LinearEasing),
            label = "CookieRotation"
        )

        val contentAlpha by animateFloatAsState(
            targetValue = if (vm.isLoading || vm.isLoginSuccess) 0f else 1f,
            animationSpec = tween(400)
        )

        val buttonShape = remember(vm.isLoginSuccess) {
            if (vm.isLoginSuccess) PolygonShape(M3ExpressiveShapes.twelveSidedCookie()) else RoundedCornerShape(100)
        }

        // --- BACKGROUND SHAPES & ICONS ---
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
                    shape = RoundedCornerShape(50), 
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
                    shape = RoundedCornerShape(50),
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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = width, height = 64.dp)
                    .scale(expandScale)
                    .rotate(rotation)
                    .clip(buttonShape)
                    .background(containerColor)
                    .clickable(enabled = !vm.isLoading && !vm.isLoginSuccess) { vm.login(email, pass) }
            ) {
                AnimatedContent(
                    targetState = vm.isLoading || vm.isLoginSuccess,
                    label = "ContentMorph"
                ) { isActivating ->
                    if (isActivating) {
                        // Phase out loader on success
                        if (!vm.isLoginSuccess) {
                            LoadingIndicator(
                                modifier = Modifier.size(32.dp),
                                // CHANGED: Now matches the Box color (Primary)
                                color = MaterialTheme.colorScheme.primary 
                            )
                        }
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

// --- BACKGROUND COMPONENTS ---

sealed class BgElement {
    data class Shape(val polygon: RoundedPolygon) : BgElement()
    data class Icon(val imageVector: ImageVector) : BgElement()
}

data class BgItem(
    val element: BgElement,
    val align: Alignment,
    val size: Dp,
    val color: Color,
    val alpha: Float,
    val direction: Float = 1f // 1f for Clockwise, -1f for Anti-Clockwise
)

@Composable
fun ExpressiveShapesBackground() {
    val primary = MaterialTheme.colorScheme.primaryContainer
    val secondary = MaterialTheme.colorScheme.secondaryContainer
    val tertiary = MaterialTheme.colorScheme.tertiaryContainer
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val errorContainer = MaterialTheme.colorScheme.errorContainer
    val inversePrimary = MaterialTheme.colorScheme.inversePrimary
    
    // --- DENSELY PACKED & BIGGER ICONS ---
    val items = remember {
        listOf(
            // --- CORNERS & EDGES (Large Shapes) ---
            BgItem(
                element = BgElement.Shape(M3ExpressiveShapes.verySunny()),
                align = BiasAlignment(-1.2f, -1.2f),
                size = 400.dp,
                color = primary,
                alpha = 0.4f,
                direction = if (Random.nextBoolean()) 1f else -1f
            ),
            BgItem(
                element = BgElement.Shape(M3ExpressiveShapes.fourSidedCookie()),
                align = BiasAlignment(1.3f, 1.3f),
                size = 350.dp,
                color = secondary,
                alpha = 0.4f,
                direction = if (Random.nextBoolean()) 1f else -1f
            ),
            BgItem(
                element = BgElement.Shape(M3ExpressiveShapes.pill()),
                align = BiasAlignment(-1.2f, 0.4f),
                size = 300.dp,
                color = tertiary,
                alpha = 0.3f,
                direction = if (Random.nextBoolean()) 1f else -1f
            ),
            BgItem(
                element = BgElement.Shape(M3ExpressiveShapes.square()),
                align = BiasAlignment(1.2f, -0.8f),
                size = 220.dp,
                color = surfaceVariant,
                alpha = 0.3f,
                direction = if (Random.nextBoolean()) 1f else -1f
            ),
            
            // --- MID-FIELD (Medium Shapes) ---
            BgItem(
                element = BgElement.Shape(M3ExpressiveShapes.triangle()),
                align = BiasAlignment(-0.8f, 0.9f),
                size = 250.dp,
                color = errorContainer,
                alpha = 0.3f,
                direction = if (Random.nextBoolean()) 1f else -1f
            ),
            BgItem(
                element = BgElement.Shape(M3ExpressiveShapes.scallop()),
                align = BiasAlignment(0.8f, 0.4f),
                size = 200.dp,
                color = inversePrimary,
                alpha = 0.3f,
                direction = if (Random.nextBoolean()) 1f else -1f
            ),
             BgItem(
                element = BgElement.Shape(M3ExpressiveShapes.flower()),
                align = BiasAlignment(0f, -1.2f),
                size = 280.dp,
                color = surfaceVariant,
                alpha = 0.3f,
                direction = if (Random.nextBoolean()) 1f else -1f
            ),
             BgItem(
                element = BgElement.Shape(M3ExpressiveShapes.twelveSidedCookie()),
                align = BiasAlignment(0.5f, 0.9f),
                size = 180.dp,
                color = primary,
                alpha = 0.2f,
                direction = if (Random.nextBoolean()) 1f else -1f
            ),

            // --- ICONS (Densely Packed & Larger) ---
            // 1. Top Left Cluster
            BgItem(
                element = BgElement.Icon(Icons.Rounded.School),
                align = BiasAlignment(-0.7f, -0.6f),
                size = 120.dp, // Increased size
                color = primary,
                alpha = 0.5f,
                direction = if (Random.nextBoolean()) 1f else -1f
            ),
            BgItem(
                element = BgElement.Icon(Icons.Rounded.AutoStories),
                align = BiasAlignment(-0.3f, -0.85f),
                size = 90.dp,
                color = secondary,
                alpha = 0.4f,
                direction = if (Random.nextBoolean()) 1f else -1f
            ),

            // 2. Top Right Cluster
            BgItem(
                element = BgElement.Icon(Icons.Rounded.Edit),
                align = BiasAlignment(0.8f, -0.4f),
                size = 100.dp,
                color = tertiary,
                alpha = 0.5f,
                direction = if (Random.nextBoolean()) 1f else -1f
            ),
            BgItem(
                element = BgElement.Icon(Icons.Rounded.Lightbulb),
                align = BiasAlignment(0.4f, -0.7f),
                size = 80.dp,
                color = errorContainer,
                alpha = 0.4f,
                direction = if (Random.nextBoolean()) 1f else -1f
            ),

            // 3. Mid Left Cluster
            BgItem(
                element = BgElement.Icon(Icons.AutoMirrored.Rounded.MenuBook),
                align = BiasAlignment(-0.9f, 0f),
                size = 110.dp,
                color = surfaceVariant,
                alpha = 0.5f,
                direction = if (Random.nextBoolean()) 1f else -1f
            ),
            BgItem(
                element = BgElement.Icon(Icons.Rounded.HistoryEdu),
                align = BiasAlignment(-0.5f, 0.3f),
                size = 85.dp,
                color = inversePrimary,
                alpha = 0.4f,
                direction = if (Random.nextBoolean()) 1f else -1f
            ),

            // 4. Bottom Right Cluster
            BgItem(
                element = BgElement.Icon(Icons.Rounded.Psychology),
                align = BiasAlignment(0.7f, 0.7f),
                size = 130.dp,
                color = secondary,
                alpha = 0.5f,
                direction = if (Random.nextBoolean()) 1f else -1f
            ),
            BgItem(
                element = BgElement.Icon(Icons.Rounded.Calculate),
                align = BiasAlignment(0.9f, 0.1f),
                size = 95.dp,
                color = primary,
                alpha = 0.4f,
                direction = if (Random.nextBoolean()) 1f else -1f
            ),

            // 5. Bottom Center / Fillers
             BgItem(
                element = BgElement.Icon(Icons.Rounded.Science),
                align = BiasAlignment(-0.2f, 0.8f),
                size = 100.dp,
                color = tertiary,
                alpha = 0.4f,
                direction = if (Random.nextBoolean()) 1f else -1f
            ),
             BgItem(
                element = BgElement.Icon(Icons.Rounded.Star),
                align = BiasAlignment(0.2f, 0.5f),
                size = 70.dp,
                color = surfaceVariant,
                alpha = 0.3f,
                direction = if (Random.nextBoolean()) 1f else -1f
            )
        )
    }

    // Single Master Rotation Clock
    val infiniteTransition = rememberInfiniteTransition(label = "master_rot")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(60000, easing = LinearEasing)), 
        label = "rot"
    )

    Box(Modifier.fillMaxSize()) {
        items.forEach { item ->
            val spin = rotation * item.direction
            
            Box(
                modifier = Modifier
                    .align(item.align)
                    .size(item.size)
                    .rotate(spin) // Rotate in place
                    .alpha(item.alpha)
            ) {
                when (val type = item.element) {
                    is BgElement.Shape -> {
                        Canvas(Modifier.fillMaxSize()) {
                            // Scale shape to fit Box
                            // Using half of the size as radius because normalized shapes are usually [-1, 1]
                            val fitRadius = size.minDimension / 2f
                            
                            translate(left = size.width / 2f, top = size.height / 2f) {
                                scale(scaleX = fitRadius, scaleY = fitRadius) {
                                    val path = type.polygon.toPath().asComposePath()
                                    drawPath(path, item.color, style = Fill)
                                }
                            }
                        }
                    }
                    is BgElement.Icon -> {
                        Icon(
                            imageVector = type.imageVector,
                            contentDescription = null,
                            tint = item.color,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
