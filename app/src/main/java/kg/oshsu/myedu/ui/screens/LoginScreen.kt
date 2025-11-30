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
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale 
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.Density
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
    // All shapes have at least 2-axis symmetry
    
    // 1. "Very Sunny": 8-pointed star
    fun verySunny(): RoundedPolygon {
        return RoundedPolygon.star(
            numVerticesPerRadius = 8,
            innerRadius = 0.78f,
            rounding = CornerRounding(radius = 0.15f), 
            innerRounding = CornerRounding(radius = 0f) 
        ).normalized()
    }

    // 2. "4 Sided Cookie": 4-lobed shape
    fun fourSidedCookie(): RoundedPolygon {
        return RoundedPolygon.star(
            numVerticesPerRadius = 4,
            innerRadius = 0.5f,
            rounding = CornerRounding(radius = 0.4f), 
            innerRounding = CornerRounding(radius = 0.4f) 
        ).normalized()
    }

    // 3. "12 Sided Cookie": Login Success Shape
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
    
    // 6. "Six Pointed Star"
    fun sixPointedStar(): RoundedPolygon {
        return RoundedPolygon.star(
            numVerticesPerRadius = 6,
            innerRadius = 0.65f,
            rounding = CornerRounding(radius = 0.15f)
        ).normalized()
    }

    // 7. "Octagon"
    fun octagon(): RoundedPolygon {
        return RoundedPolygon(
            numVertices = 8,
            rounding = CornerRounding(radius = 0.25f)
        ).normalized()
    }
    
    // 8. "Scallop"
    fun scallop(): RoundedPolygon {
        return RoundedPolygon.star(
             numVerticesPerRadius = 12,
             innerRadius = 0.92f,
             rounding = CornerRounding(radius = 1f)
        ).normalized()
    }

    private fun RoundedPolygon.normalized(): RoundedPolygon {
        return this
    }
    
    val randomShapes = listOf(
        { verySunny() },
        { fourSidedCookie() },
        { pill() },
        { square() },
        { sixPointedStar() },
        { octagon() },
        { scallop() }
    )
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

// Data class for background items with pre-calculated path
private data class BackgroundShapeItem(
    val path: Path,
    val xRatio: Float, 
    val yRatio: Float, 
    val scaleRatio: Float,
    val color: Color,
    val rotationSpeed: Float, 
    val startRotation: Float
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginScreen(vm: MainViewModel) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        
        // Dynamic scale calculation for the main button
        val calculatedScale = remember(screenWidth, screenHeight) {
            val widthVal = screenWidth.value
            val heightVal = screenHeight.value
            val screenDiagonal = sqrt((widthVal * widthVal) + (heightVal * heightVal))
            val buttonRadius = 32f
            val innerRadiusFactor = 0.8f 
            val effectiveRadius = buttonRadius * innerRadiusFactor
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
            animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            label = "Expand"
        )

        val rotation by animateFloatAsState(
            targetValue = if (vm.isLoginSuccess) 360f else 0f,
            animationSpec = tween(durationMillis = 1500, easing = LinearEasing),
            label = "CookieRotation"
        )

        val contentAlpha by animateFloatAsState(
            targetValue = if (vm.isLoading || vm.isLoginSuccess) 0f else 1f,
            animationSpec = tween(400)
        )

        val buttonShape = remember(vm.isLoginSuccess) {
            if (vm.isLoginSuccess) PolygonShape(M3ExpressiveShapes.twelveSidedCookie()) else RoundedCornerShape(100)
        }

        // --- BACKGROUND SHAPES ---
        val aspectRatio = if(screenHeight > 0.dp && screenWidth > 0.dp) screenHeight / screenWidth else 2f
        ExpressiveShapesBackground(aspectRatio)

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

            // Inputs
            Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.widthIn(max = 400.dp)) {
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
                        if (!vm.isLoginSuccess) {
                            LoadingIndicator(
                                modifier = Modifier.size(32.dp),
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

@Composable
fun ExpressiveShapesBackground(aspectRatio: Float = 2.0f) {
    val colors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.surfaceVariant
    )

    val infiniteTransition = rememberInfiniteTransition(label = "bg_anim")
    val baseRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)), // Faster rotation
        label = "base_rot"
    )

    // Generate shapes with fail-safe logic
    val shapeItems = remember {
        val items = mutableListOf<BackgroundShapeItem>()
        val rng = Random(seed = 1234) // Consistent seed
        
        val targetCount = 30
        var attempts = 0
        
        // 1. Try smart non-overlapping placement
        while (items.size < targetCount && attempts < 2000) {
            attempts++
            
            val x = rng.nextFloat()
            val y = rng.nextFloat()
            val s = 0.15f + (rng.nextFloat() * 0.15f) // Variable size
            
            var valid = true
            for (existing in items) {
                val dx = x - existing.xRatio
                val dy = (y - existing.yRatio) * aspectRatio
                val dist = sqrt(dx*dx + dy*dy)
                
                // Allow a little bit of overlap (0.75 factor) so we actually fill the screen
                val requiredDist = ((s + existing.scaleRatio) / 2f) * 0.75f 
                
                if (dist < requiredDist) {
                    valid = false
                    break
                }
            }
            
            if (valid) {
                val shapeGen = M3ExpressiveShapes.randomShapes[rng.nextInt(M3ExpressiveShapes.randomShapes.size)]
                
                // Pre-calculate path to avoid runtime issues
                val polygon = shapeGen()
                val androidPath = android.graphics.Path()
                polygon.toPath(androidPath)
                val composePath = androidPath.asComposePath()

                items.add(
                    BackgroundShapeItem(
                        path = composePath,
                        xRatio = x,
                        yRatio = y,
                        scaleRatio = s,
                        color = colors[rng.nextInt(colors.size)],
                        rotationSpeed = (1f + rng.nextFloat()) * (if (rng.nextBoolean()) 1f else -1f), // Faster individual spin
                        startRotation = rng.nextFloat() * 360f
                    )
                )
            }
        }
        
        // 2. Fallback: If we couldn't place enough items, force place random ones without strict checks
        if (items.size < 5) {
            repeat(10) {
                val shapeGen = M3ExpressiveShapes.randomShapes[rng.nextInt(M3ExpressiveShapes.randomShapes.size)]
                val androidPath = android.graphics.Path()
                shapeGen().toPath(androidPath)
                items.add(BackgroundShapeItem(
                    path = androidPath.asComposePath(),
                    xRatio = rng.nextFloat(),
                    yRatio = rng.nextFloat(),
                    scaleRatio = 0.2f,
                    color = colors[rng.nextInt(colors.size)],
                    rotationSpeed = 1f,
                    startRotation = 0f
                ))
            }
        }
        
        items
    }

    // Canvas with high visibility
    Canvas(modifier = Modifier.fillMaxSize().alpha(0.5f)) { 
        val w = size.width
        val h = size.height

        shapeItems.forEach { item ->
            val drawX = item.xRatio * w
            val drawY = item.yRatio * h
            val shapeSize = w * item.scaleRatio

            translate(left = drawX, top = drawY) {
                rotate(degrees = item.startRotation + (baseRotation * item.rotationSpeed)) {
                    scale(scaleX = shapeSize, scaleY = shapeSize) {
                        drawPath(item.path, item.color, style = Fill)
                    }
                }
            }
        }
    }
}
