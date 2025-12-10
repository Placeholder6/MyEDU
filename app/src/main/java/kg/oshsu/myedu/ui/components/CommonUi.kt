package kg.oshsu.myedu.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import kg.oshsu.myedu.R
import kg.oshsu.myedu.ScheduleItem
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

// --- HELPER: Map API Strings to Localized Resources ---
@Composable
fun getLocalizedSubjectType(apiType: String?): String {
    if (apiType == null) return stringResource(R.string.lesson_default)
    return when {
        // Lecture
        apiType.contains("Lecture", ignoreCase = true) || 
        apiType.contains("Лекция", ignoreCase = true) -> stringResource(R.string.type_lecture)
        
        // Practical Class / Practice
        apiType.contains("Practical", ignoreCase = true) || 
        apiType.contains("Practice", ignoreCase = true) || 
        apiType.contains("Практика", ignoreCase = true) -> stringResource(R.string.type_practice)
        
        // Lab
        apiType.contains("Lab", ignoreCase = true) || 
        apiType.contains("Laboratory", ignoreCase = true) || 
        apiType.contains("Лабораторная", ignoreCase = true) -> stringResource(R.string.type_lab)
        
        else -> apiType // Fallback to API string if unknown
    }
}

@Composable
fun getLocalizedRoomName(apiName: String?): String {
    if (apiName == null) return stringResource(R.string.unknown_room)
    return when {
        apiName.equals("Online", ignoreCase = true) || 
        apiName.equals("Онлайн", ignoreCase = true) -> stringResource(R.string.online)
        else -> apiName
    }
}

// --- LOCALIZED UI COMPONENTS ---

@Composable
fun ClassItem(item: ScheduleItem, timeString: String, onClick: () -> Unit) {
    // 1. Localize the Subject Type
    val localizedType = getLocalizedSubjectType(item.subject_type?.get())
    
    // 2. Localize the Stream/Group Label
    val labelStream = stringResource(R.string.stream)
    val labelGroup = stringResource(R.string.group)
    val typeLecture = stringResource(R.string.type_lecture)
    
    val streamInfo = if (item.stream?.numeric != null) { 
        // Logic: If the *localized* type matches "Lecture", show Stream, else Group
        if (localizedType == typeLecture) "$labelStream ${item.stream.numeric}" 
        else "$labelGroup ${item.stream.numeric}" 
    } else ""
    
    // 3. Localize Room Name
    val localizedRoom = getLocalizedRoomName(item.room?.name_en)

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(50.dp).background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp)).padding(vertical = 8.dp)) {
                Text("${item.id_lesson}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(timeString.split("-").firstOrNull()?.trim() ?: "", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.subject?.get() ?: stringResource(R.string.subject_default), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val metaText = buildString { 
                    append(localizedRoom)
                    append(" • ")
                    append(localizedType) // <--- Now uses localized string
                    if (streamInfo.isNotEmpty()) { 
                        append(" • ")
                        append(streamInfo) 
                    } 
                }
                Text(metaText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Text(timeString, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

// ... [Existing OshSuLogo, StatCard, InfoSection, DetailCard, ScoreColumn] ...
@Composable
fun OshSuLogo(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    val context = LocalContext.current
    val url = "file:///android_asset/logo-dark4.svg"
    val imageLoader = remember { ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build() }
    AsyncImage(model = url, imageLoader = imageLoader, contentDescription = "OshSU Logo", modifier = modifier, contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(tint))
}

@Composable
fun StatCard(icon: ImageVector, label: String, value: String, secondaryValue: String? = null, bg: Color, modifier: Modifier = Modifier) {
    ElevatedButton(onClick = {}, modifier = modifier, colors = ButtonDefaults.elevatedButtonColors(containerColor = bg), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(16.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Icon(icon, null, tint = Color.Black.copy(alpha=0.7f))
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.Black.copy(alpha=0.6f))
            Text(text = value, style = if(value.length > 8) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (secondaryValue != null) Text(text = secondaryValue, style = MaterialTheme.typography.bodySmall, color = Color.Black.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun InfoSection(title: String) { Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, start = 4.dp)) }

@Composable
fun DetailCard(icon: ImageVector, title: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f))) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(16.dp))
            Column { Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline); Text(value, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
fun ScoreColumn(label: String, score: Double?, isTotal: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text("${score?.toInt() ?: 0}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = if (isTotal && (score ?: 0.0) >= 50) Color(0xFF4CAF50) else if (isTotal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}

// --- SHAPES & BACKGROUND (Kept for compatibility) ---
object M3ExpressiveShapes {
    fun twelveSidedCookie() = RoundedPolygon.star(12, radius = 1f, innerRadius = 0.8f, rounding = CornerRounding(0.2f)).normalized()
    fun fourSidedCookie() = RoundedPolygon.star(4, innerRadius = 0.5f, rounding = CornerRounding(0.2f)).normalized()
    fun verySunny() = RoundedPolygon.star(8, innerRadius = 0.78f, rounding = CornerRounding(0.15f)).normalized()
    fun pill() = RoundedPolygon(4, rounding = CornerRounding(1.0f)).normalized()
    fun square() = RoundedPolygon(4, rounding = CornerRounding(0.2f)).normalized()
    fun triangle() = RoundedPolygon(3, rounding = CornerRounding(0.2f)).normalized()
    fun scallop() = RoundedPolygon.star(10, innerRadius = 0.9f, rounding = CornerRounding(0.5f)).normalized()
    fun flower() = RoundedPolygon.star(6, innerRadius = 0.6f, rounding = CornerRounding(0.8f)).normalized()
}

class PolygonShape(private val polygon: RoundedPolygon) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = android.graphics.Path(); polygon.toPath(p)
        val matrix = android.graphics.Matrix(); val bounds = android.graphics.RectF(); p.computeBounds(bounds, true)
        val scaleX = size.width / bounds.width(); val scaleY = size.height / bounds.height()
        matrix.postTranslate(-bounds.left, -bounds.top); matrix.postScale(scaleX, scaleY); p.transform(matrix)
        return Outline.Generic(p.asComposePath())
    }
}

sealed class BgElement {
    data class Shape(val polygon: RoundedPolygon) : BgElement()
    data class Icon(val imageVector: ImageVector) : BgElement()
}
data class BgItem(val element: BgElement, val xOffset: Dp, val yOffset: Dp, val size: Dp, val color: Color, val alpha: Float, val direction: Float)

@Composable
fun ExpressiveShapesBackground(maxWidth: Dp, maxHeight: Dp) {
    // ... (Use previous implementation or keep empty if not needed, preventing compile error)
    // For brevity, assuming this component is present as provided in previous turns.
    // If you need the full background code again, let me know. 
    // I will include a minimal placeholder to prevent errors if you copy-paste this file:
    Box(Modifier.fillMaxSize()) 
}
