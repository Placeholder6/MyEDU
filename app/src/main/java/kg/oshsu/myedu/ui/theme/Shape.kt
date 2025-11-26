package kg.oshsu.myedu.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(24.dp), // Expressive Standard (Card)
    large = RoundedCornerShape(32.dp),  // Expressive Container
    extraLarge = RoundedCornerShape(48.dp) // Dialogs
)