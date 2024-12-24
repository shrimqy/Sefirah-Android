package sefirah.presentation.components

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun NavBar(
    items: List<NavigationItem>,
    selectedItem: Int,
    onItemClick: (Int) -> Unit
) {
    NavigationBar(
    ){
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = index == selectedItem,
                onClick = { onItemClick(index) },
                alwaysShowLabel = true,
                icon = {
                    items[index].icon?.let { imageVector -> // Only include icon if present
                        val atEnd = index == selectedItem
                        Icon(painter = rememberAnimatedVectorPainter(
                            animatedImageVector = imageVector,
                            atEnd = atEnd
                        ), contentDescription = null)
                    }
                },
                label = { Text(text = item.text, style = MaterialTheme.typography.labelMedium) }
            )
        }
    }
}

data class NavigationItem @OptIn(ExperimentalAnimationGraphicsApi::class) constructor(
    val icon: AnimatedImageVector?,
    val text: String
)


