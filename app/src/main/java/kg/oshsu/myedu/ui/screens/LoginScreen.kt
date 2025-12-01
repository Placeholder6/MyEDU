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
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.abs
import kotlin.math.hypot
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
        // Pass screen dimensions to trigger the generation algorithm
        ExpressiveShapesBackground(screenWidth, screenHeight)

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
                    // FIXED: Made container transparent to see shapes behind the field and label
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
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
                    // FIXED: Made container transparent here as well
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
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

// --- BACKGROUND GENERATION ALGORITHM ---

sealed class BgElement {
    data class Shape(val polygon: RoundedPolygon) : BgElement()
    data class Icon(val imageVector: ImageVector) : BgElement()
}

// Represents the "Imaginary Square" that grows
private data class SimItem(
    val id: Int,
    var x: Float,
    var y: Float,
    var size: Float,
    var speed: Float,
    var active: Boolean = true
)

data class BgItem(
    val element: BgElement,
    val xOffset: Dp,
    val yOffset: Dp,
    val size: Dp,
    val color: Color,
    val alpha: Float,
    val direction: Float 
)

@Composable
fun ExpressiveShapesBackground(maxWidth: Dp, maxHeight: Dp) {
    val density = LocalDensity.current
    
    // --- COLORS ---
    val primary = MaterialTheme.colorScheme.primaryContainer
    val secondary = MaterialTheme.colorScheme.secondaryContainer
    val tertiary = MaterialTheme.colorScheme.tertiaryContainer
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val errorContainer = MaterialTheme.colorScheme.errorContainer
    val inversePrimary = MaterialTheme.colorScheme.inversePrimary
    val colors = listOf(primary, secondary, tertiary, surfaceVariant, errorContainer, inversePrimary)

    // --- GENERATE PACKED ITEMS ---
    // We remember the list so it doesn't regenerate on every frame, only on size change
    val items = remember(maxWidth, maxHeight) {
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        
        // 1. Grid Configuration (Stratified Sampling)
        // Divide screen into cells to ensure coverage
        val targetCellSize = with(density) { 140.dp.toPx() } // Approximate ideal size
        val cols = (w / targetCellSize).toInt().coerceAtLeast(3)
        val rows = (h / targetCellSize).toInt().coerceAtLeast(5)
        val cellW = w / cols
        val cellH = h / rows
        
        // 2. Initialize Seeds (One per cell with jitter)
        val simItems = mutableListOf<SimItem>()
        var idCounter = 0
        
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                // Center of cell
                val cx = c * cellW + cellW / 2
                val cy = r * cellH + cellH / 2
                
                // Add Jitter (offset) within 40% of cell bounds
                val jitterX = (Random.nextFloat() - 0.5f) * cellW * 0.8f
                val jitterY = (Random.nextFloat() - 0.5f) * cellH * 0.8f
                
                simItems.add(
                    SimItem(
                        id = idCounter++,
                        x = cx + jitterX,
                        y = cy + jitterY,
                        size = 50f, // Start size (not too small)
                        speed = Random.nextFloat() * 1.0f + 0.5f,
                        active = true
                    )
                )
            }
        }

        // 3. Simulation Loop (Grow until potential overlap)
        val maxIterations = 200
        // Diagonal Factor: Since shapes rotate, we must assume their collision bounds
        // is the circle that circumscribes the square.
        // Diagonal of square size S is S * sqrt(2) ~= S * 1.414.
        // We use 1.45 for a bit of extra safety padding.
        val rotSafeFactor = 1.45f 

        for (i in 0 until maxIterations) {
            var anyGrowing = false
            for (item in simItems) {
                if (!item.active) continue
                anyGrowing = true
                
                // Tentative growth
                val newSize = item.size + item.speed
                
                // A. Wall Collision (Keep inside screen)
                // We add padding (20f) so shapes don't get cut off at edges
                val halfSize = newSize / 2
                if (item.x - halfSize < 20f || item.x + halfSize > w - 20f || 
                    item.y - halfSize < 20f || item.y + halfSize > h - 20f) {
                    item.active = false
                    continue
                }

                // B. Neighbor Collision
                var collides = false
                for (other in simItems) {
                    if (item.id == other.id) continue
                    
                    val dx = abs(item.x - other.x)
                    val dy = abs(item.y - other.y)
                    val dist = hypot(dx, dy)
                    
                    // Collision Rule: Distance < (RadiusA + RadiusB) * SafeFactor
                    // Radius = Size / 2
                    val minSafeDist = (newSize/2 + other.size/2) * rotSafeFactor
                    
                    if (dist < minSafeDist) {
                        collides = true
                        break
                    }
                }

                if (collides) {
                    item.active = false
                } else {
                    item.size = newSize
                }
            }
            if (!anyGrowing) break
        }

        // 4. Map to Elements
        val elements = listOf(
            BgElement.Shape(M3ExpressiveShapes.verySunny()),
            BgElement.Shape(M3ExpressiveShapes.fourSidedCookie()),
            BgElement.Shape(M3ExpressiveShapes.pill()),
            BgElement.Shape(M3ExpressiveShapes.square()),
            BgElement.Shape(M3ExpressiveShapes.triangle()),
            BgElement.Shape(M3ExpressiveShapes.scallop()),
            BgElement.Shape(M3ExpressiveShapes.flower()),
            BgElement.Shape(M3ExpressiveShapes.twelveSidedCookie()),
            BgElement.Icon(Icons.Rounded.School),
            BgElement.Icon(Icons.Rounded.AutoStories),
            BgElement.Icon(Icons.Rounded.Edit),
            BgElement.Icon(Icons.Rounded.Lightbulb),
            BgElement.Icon(Icons.AutoMirrored.Rounded.MenuBook),
            BgElement.Icon(Icons.Rounded.HistoryEdu),
            BgElement.Icon(Icons.Rounded.Psychology),
            BgElement.Icon(Icons.Rounded.Calculate),
            BgElement.Icon(Icons.Rounded.Science),
            BgElement.Icon(Icons.Rounded.Star)
        )

        simItems.map { sim ->
            val el = elements.random()
            val xDp = with(density) { (sim.x - sim.size/2).toDp() }
            val yDp = with(density) { (sim.y - sim.size/2).toDp() }
            val sizeDp = with(density) { sim.size.toDp() }

            BgItem(
                element = el,
                xOffset = xDp,
                yOffset = yDp,
                size = sizeDp,
                color = colors.random(),
                alpha = Random.nextFloat() * 0.3f + 0.2f, 
                direction = if (Random.nextBoolean()) 1f else -1f
            )
        }
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
                    .offset(x = item.xOffset, y = item.yOffset)
                    .size(item.size)
                    .rotate(spin) 
                    .alpha(item.alpha)
            ) {
                when (val type = item.element) {
                    is BgElement.Shape -> {
                        Canvas(Modifier.fillMaxSize()) {
                            val path = android.graphics.Path()
                            type.polygon.toPath(path)
                            
                            val matrix = android.graphics.Matrix()
                            val bounds = android.graphics.RectF()
                            path.computeBounds(bounds, true)
                            
                            // Matrix Scaling: Fit the normalized shape into the Box
                            val scaleX = size.width / bounds.width()
                            val scaleY = size.height / bounds.height()
                            val scale = minOf(scaleX, scaleY)
                            
                            matrix.reset()
                            matrix.postTranslate(-bounds.centerX(), -bounds.centerY())
                            matrix.postScale(scale, scale)
                            matrix.postTranslate(size.width / 2f, size.height / 2f)
                            
                            path.transform(matrix)
                            drawPath(path.asComposePath(), item.color, style = Fill)
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
