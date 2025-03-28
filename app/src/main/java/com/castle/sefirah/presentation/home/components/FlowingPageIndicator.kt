package com.castle.sefirah.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.zIndex
import kotlin.math.abs

@Composable
fun FlowingPageIndicator(
    pageCount: Int,
    currentPage: Int,
    currentPageOffset: Float,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
) {
    if (pageCount <= 1) return

    // Calculate which dot is receiving the flow animation
    val targetPage = if (currentPageOffset > 0) {
        if (currentPage < pageCount - 1) currentPage + 1 else currentPage
    } else {
        if (currentPage > 0) currentPage - 1 else currentPage
    }

    // Absolute offset for animation calculations
    val absOffset = abs(currentPageOffset)
    val isMovingForward = currentPageOffset > 0

    // Basic measurements
    val dotSize = 7.dp
    val expansionWidth = 14.dp

    Box(
        modifier = modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Static dots - using absolute positioning to maintain consistent layout
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier,
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (index in 0 until pageCount) {
                    if (index != currentPage && index != targetPage) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = dotSize / 2)
                                .height(dotSize)
                                .width(dotSize)
                                .clip(CircleShape)
                                .background(inactiveColor)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = dotSize / 2)
                                .height(dotSize)
                                .width(dotSize)
                        ) {
                            // Only show base circles when not animating
                            if (absOffset < 0.01f) {
                                Box(
                                    modifier = Modifier
                                        .height(dotSize)
                                        .width(dotSize)
                                        .clip(CircleShape)
                                        .background(if (index == currentPage) activeColor else inactiveColor)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Animated dots overlay - same container as static dots
        if (absOffset >= 0.01f) {
            val animationPhase = when {
                absOffset < 0.25f -> 1  // Phase 1: Source dot expands to target
                absOffset < 0.5f -> 2   // Phase 2: Target dot expands to source
                absOffset < 0.75f -> 3  // Phase 3: Source dot shrinks back
                else -> 4               // Phase 4: Target dot shrinks back
            }

            // Calculate horizontal positions for each dot
            val totalDotsWidth = pageCount * dotSize + (pageCount - 1) * dotSize
            val startX = -totalDotsWidth / 2 + dotSize / 2
            val dotPositions = List(pageCount) { index ->
                startX + index * (dotSize + dotSize)
            }

            val spaceBetweenDots = dotPositions[1] - dotPositions[0]

            // Source dot animation
            val sourceWidth = when (animationPhase) {
                1 -> {
                    // Phase 1: Expand toward target
                    val progress = absOffset / 0.25f
                    dotSize + progress * (expansionWidth - dotSize)
                }
                2 -> expansionWidth // Phase 2: Stay expanded
                3 -> {
                    // Phase 3: Shrink back
                    val progress = (absOffset - 0.5f) / 0.25f
                    expansionWidth * (1 - progress) + dotSize * progress
                }
                else -> dotSize // Phase 4: Back to circle
            }

            // Calculate source dot movement and position

            val moveProgress = when (animationPhase) {
                1 -> absOffset / 0.25f                 // In phase 1, move from 0 to 1
                2, 3 -> 1f                            // In phases 2 and 3, stay at full movement
                4 -> 1f - ((absOffset - 0.75f) / 0.25f) // In phase 4, move back from 1 to 0
                else -> 0f                            // Safety for unexpected cases
            }
            
            // Calculate how far to move (up to half the distance between dots)
            val movementDistance = (spaceBetweenDots / 2) * moveProgress
            
            // Set source dot position based on direction
            val sourcePosition = if (isMovingForward) {
                // Moving forward: source dot moves right
                dotPositions[currentPage] + movementDistance
            } else {
                // Moving backward: source dot moves left
                dotPositions[currentPage] - movementDistance
            }

            // Draw source dot
            Box(
                modifier = Modifier
                    .offset(x = sourcePosition)
                    .height(dotSize)
                    .width(sourceWidth)
                    .zIndex(if (animationPhase < 3) 2f else 0f)
                    .clip(RoundedCornerShape(50))
                    .background(activeColor)
            )

            // Target dot animation
            val targetWidth = when (animationPhase) {
                1 -> dotSize // Phase 1: Just a circle
                2 -> {
                    // Phase 2: Expand toward source
                    val progress = (absOffset - 0.25f) / 0.25f
                    dotSize + progress * (expansionWidth - dotSize)
                }
                3 -> expansionWidth // Phase 3: Stay expanded
                else -> {
                    // Phase 4: Shrink back
                    val progress = (absOffset - 0.75f) / 0.25f
                    expansionWidth * (1 - progress) + dotSize * progress
                }
            }

            // Color transition for target dot
            val colorProgress = if (animationPhase == 1) 0f else minOf(1f, (absOffset - 0.25f) / 0.5f)
            val targetColor = Color(
                red = inactiveColor.red + colorProgress * (activeColor.red - inactiveColor.red),
                green = inactiveColor.green + colorProgress * (activeColor.green - inactiveColor.green),
                blue = inactiveColor.blue + colorProgress * (activeColor.blue - inactiveColor.blue),
                alpha = inactiveColor.alpha + colorProgress * (activeColor.alpha - inactiveColor.alpha)
            )

            // Calculate target dot movement (opposite of source dot)
            val targetMoveProgress = when (animationPhase) {
                1 -> 0f                              // In phase 1, don't move yet
                2 -> (absOffset - 0.25f) / 0.25f     // In phase 2, move from 0 to 1
                3, 4 -> 1f                          // In phases 3 and 4, stay at full movement
                else -> 0f                          // Safety for unexpected cases
            }
            
            // Calculate movement distance for target
            val targetMovementDistance = (spaceBetweenDots / 2) * targetMoveProgress
            
            // Set target dot position based on direction
            val targetPosition = if (isMovingForward) {
                // Moving forward: target dot moves left
                dotPositions[targetPage] - targetMovementDistance
            } else {
                // Moving backward: target dot moves right
                dotPositions[targetPage] + targetMovementDistance
            }

            // Draw target dot
            Box(
                modifier = Modifier
                    .offset(x = targetPosition)
                    .height(dotSize)
                    .width(targetWidth)
                    .zIndex(if (animationPhase >= 3) 2f else 1f)
                    .clip(RoundedCornerShape(50))
                    .background(targetColor)
            )
        }
    }
}