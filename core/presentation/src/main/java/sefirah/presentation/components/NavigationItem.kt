package sefirah.presentation.components

import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable

@Composable
fun NavigationItemIcon(
    icon: AnimatedImageVector?,
    selected: Boolean,
) {
    icon?.let { imageVector ->
        Icon(
            painter = rememberAnimatedVectorPainter(
                animatedImageVector = imageVector,
                atEnd = selected,
            ),
            contentDescription = null,
        )
    }
}

data class NavigationItem (
    val icon: AnimatedImageVector?,
    val text: String,
    val route: String,
)
