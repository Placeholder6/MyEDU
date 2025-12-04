package kg.oshsu.myedu.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import kg.oshsu.myedu.MainViewModel
import kg.oshsu.myedu.ui.components.M3ExpressiveShapes
import kg.oshsu.myedu.ui.components.OshSuLogo
import kg.oshsu.myedu.ui.components.PolygonShape
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun LoginScreen(
    vm: MainViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope
) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        val verticalBias by animateFloatAsState(
            targetValue = if (vm.isLoading || vm.isLoginSuccess) 0f else 0.85f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
            label = "VerticalBias"
        )

        // Width animates: Wide (Sign In) -> Small (Loader/Cookie)
        val width by animateDpAsState(
            targetValue = if (vm.isLoading || vm.isLoginSuccess) 56.dp else 280.dp,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
            label = "Width"
        )

        // Shape Morph: Pill -> Circle (Loader) -> Cookie (Success)
        val buttonShape = remember(vm.isLoginSuccess, vm.isLoading) {
            when {
                vm.isLoginSuccess -> PolygonShape(M3ExpressiveShapes.twelveSidedCookie())
                vm.isLoading -> CircleShape
                else -> RoundedCornerShape(100)
            }
        }
        
        // Color Morph: Primary -> Transparent (Loader) -> Primary (Success)
        val containerColor by animateColorAsState(
            targetValue = if (vm.isLoading && !vm.isLoginSuccess) Color.Transparent else MaterialTheme.colorScheme.primary,
            animationSpec = tween(300),
            label = "ColorFade"
        )

        // Rotate only when it's a cookie (Success)
        val rotation by animateFloatAsState(
            targetValue = if (vm.isLoginSuccess) 360f else 0f,
            animationSpec = tween(durationMillis = 2000, easing = LinearEasing),
            label = "CookieRotation"
        )

        val contentAlpha by animateFloatAsState(
            targetValue = if (vm.isLoading || vm.isLoginSuccess) 0f else 1f,
            animationSpec = tween(400)
        )

        ExpressiveShapesBackground(screenWidth, screenHeight)

        // --- FORM CONTENT ---
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).alpha(contentAlpha).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(48.dp))
            OshSuLogo(modifier = Modifier.width(160.dp).height(80.dp))
            Spacer(Modifier.height(32.dp))
            Text("Welcome Back", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("Sign in to your account", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(48.dp))

            Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.widthIn(max = 400.dp)) {
                OutlinedTextField(
                    value = email, onValueChange = { email = it }, label = { Text("Email") }, leadingIcon = { Icon(Icons.Default.Email, null) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(50), 
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next), 
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }), 
                    singleLine = true
                )
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it }, label = { Text("Password") }, leadingIcon = { Icon(Icons.Default.Lock, null) },
                    trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) } },
                    modifier = Modifier.fillMaxWidth(), visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); vm.login(email, pass) }), singleLine = true
                )
            }

            if (vm.errorMsg != null) {
                Spacer(Modifier.height(24.dp))
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                    Text(vm.errorMsg!!, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.weight(1f))
        }

        // --- SHARED ELEMENT (BUTTON -> LOADER -> COOKIE) ---
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = BiasAlignment(0f, verticalBias)) {
            with(sharedTransitionScope) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .sharedElement(
                            sharedContentState = rememberSharedContentState(key = "cookie_transform"),
                            animatedVisibilityScope = animatedContentScope
                        )
                        .size(width = width, height = 56.dp) 
                        .rotate(rotation)
                        .clip(buttonShape)
                        .background(containerColor)
                        .clickable(enabled = !vm.isLoading && !vm.isLoginSuccess) { vm.login(email, pass) }
                ) {
                    AnimatedContent(
                        targetState = when {
                            vm.isLoginSuccess -> 2
                            vm.isLoading -> 1
                            else -> 0
                        }, 
                        label = "ContentMorph"
                    ) { state ->
                        when(state) {
                            0 -> Text("Sign In", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                            1 -> LoadingIndicator(modifier = Modifier.size(32.dp), color = MaterialTheme.colorScheme.primary) // Expressive Loading Indicator
                            2 -> Box(Modifier.fillMaxSize()) // Empty Cookie (No Tick)
                        }
                    }
                }
            }
        }
    }
}

// ... [BgElement, SimItem, BgItem, and ExpressiveShapesBackground] ...
sealed class BgElement {
    data class Shape(val polygon: RoundedPolygon) : BgElement()
    data class Icon(val imageVector: ImageVector) : BgElement()
}
private data class SimItem(val id: Int, var x: Float, var y: Float, var size: Float, var speed: Float, var active: Boolean = true)
data class BgItem(val element: BgElement, val xOffset: Dp, val yOffset: Dp, val size: Dp, val color: Color, val alpha: Float, val direction: Float)

@Composable
fun ExpressiveShapesBackground(maxWidth: Dp, maxHeight: Dp) {
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primaryContainer
    val secondary = MaterialTheme.colorScheme.secondaryContainer
    val tertiary = MaterialTheme.colorScheme.tertiaryContainer
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val errorContainer = MaterialTheme.colorScheme.errorContainer
    val inversePrimary = MaterialTheme.colorScheme.inversePrimary
    val colors = listOf(primary, secondary, tertiary, surfaceVariant, errorContainer, inversePrimary)

    val items = remember(maxWidth, maxHeight) {
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val targetCellSize = with(density) { 140.dp.toPx() }
        val cols = (w / targetCellSize).toInt().coerceAtLeast(3)
        val rows = (h / targetCellSize).toInt().coerceAtLeast(5)
        val cellW = w / cols
        val cellH = h / rows
        val simItems = mutableListOf<SimItem>()
        var idCounter = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cx = c * cellW + cellW / 2
                val cy = r * cellH + cellH / 2
                val jitterX = (Random.nextFloat() - 0.5f) * cellW * 0.8f
                val jitterY = (Random.nextFloat() - 0.5f) * cellH * 0.8f
                simItems.add(SimItem(idCounter++, cx + jitterX, cy + jitterY, 50f, Random.nextFloat() * 1.0f + 0.5f, true))
            }
        }
        val maxIterations = 200
        val rotSafeFactor = 1.45f 
        for (i in 0 until maxIterations) {
            var anyGrowing = false
            for (item in simItems) {
                if (!item.active) continue
                anyGrowing = true
                val newSize = item.size + item.speed
                val halfSize = newSize / 2
                if (item.x - halfSize < 20f || item.x + halfSize > w - 20f || item.y - halfSize < 20f || item.y + halfSize > h - 20f) {
                    item.active = false
                    continue
                }
                var collides = false
                for (other in simItems) {
                    if (item.id == other.id) continue
                    val dx = abs(item.x - other.x)
                    val dy = abs(item.y - other.y)
                    val dist = hypot(dx, dy)
                    val minSafeDist = (newSize/2 + other.size/2) * rotSafeFactor
                    if (dist < minSafeDist) { collides = true; break }
                }
                if (collides) item.active = false else item.size = newSize
            }
            if (!anyGrowing) break
        }
        val elements = listOf(
            BgElement.Shape(M3ExpressiveShapes.verySunny()), BgElement.Shape(M3ExpressiveShapes.fourSidedCookie()), BgElement.Shape(M3ExpressiveShapes.pill()),
            BgElement.Shape(M3ExpressiveShapes.square()), BgElement.Shape(M3ExpressiveShapes.triangle()), BgElement.Shape(M3ExpressiveShapes.scallop()),
            BgElement.Shape(M3ExpressiveShapes.flower()), BgElement.Shape(M3ExpressiveShapes.twelveSidedCookie()),
            BgElement.Icon(Icons.Rounded.School), BgElement.Icon(Icons.Rounded.AutoStories), BgElement.Icon(Icons.Rounded.Edit), BgElement.Icon(Icons.Rounded.Lightbulb),
            BgElement.Icon(Icons.Rounded.MenuBook), 
            BgElement.Icon(Icons.Rounded.HistoryEdu), BgElement.Icon(Icons.Rounded.Psychology), BgElement.Icon(Icons.Rounded.Calculate),
            BgElement.Icon(Icons.Rounded.Science), BgElement.Icon(Icons.Rounded.Star)
        )
        simItems.map { sim ->
            BgItem(elements.random(), with(density) { (sim.x - sim.size/2).toDp() }, with(density) { (sim.y - sim.size/2).toDp() }, with(density) { sim.size.toDp() }, colors.random(), Random.nextFloat() * 0.3f + 0.2f, if (Random.nextBoolean()) 1f else -1f)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "master_rot")
    val rotation by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(tween(60000, easing = LinearEasing)), label = "rot")

    Box(Modifier.fillMaxSize()) {
        items.forEach { item ->
            Box(modifier = Modifier.offset(x = item.xOffset, y = item.yOffset).size(item.size).rotate(rotation * item.direction).alpha(item.alpha)) {
                when (val type = item.element) {
                    is BgElement.Shape -> { 
                        Canvas(Modifier.fillMaxSize()) { 
                            val path = android.graphics.Path()
                            type.polygon.toPath(path)
                            val matrix = android.graphics.Matrix()
                            val bounds = android.graphics.RectF()
                            path.computeBounds(bounds, true)
                            val scale = minOf(size.width / bounds.width(), size.height / bounds.height())
                            matrix.postTranslate(-bounds.centerX(), -bounds.centerY())
                            matrix.postScale(scale, scale)
                            matrix.postTranslate(size.width / 2f, size.height / 2f)
                            path.transform(matrix)
                            drawPath(path.asComposePath(), item.color, style = Fill) 
                        } 
                    }
                    is BgElement.Icon -> { Icon(imageVector = type.imageVector, contentDescription = null, tint = item.color, modifier = Modifier.fillMaxSize()) }
                }
            }
        }
    }
}