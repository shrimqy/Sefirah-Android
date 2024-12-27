package com.castle.sefirah.navigation.transitions

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

object NavigationTransitions {
    private const val ANIMATION_DURATION = 400

    fun enterTransition(
        isEnteringFromRight: Boolean,
        durationMillis: Int = ANIMATION_DURATION
    ) = slideInHorizontally(
        animationSpec = tween(durationMillis),
        initialOffsetX = { fullWidth -> if (isEnteringFromRight) fullWidth else -fullWidth }
    ) + fadeIn(tween(durationMillis))

    fun exitTransition(
        isExitingToRight: Boolean,
        durationMillis: Int = ANIMATION_DURATION
    ) = slideOutHorizontally(
        animationSpec = tween(durationMillis),
        targetOffsetX = { fullWidth -> if (isExitingToRight) fullWidth else -fullWidth }
    ) + fadeOut(tween(durationMillis))

    // For root level transitions
    fun rootEnterTransition(scope: AnimatedContentTransitionScope<*>) =
        scope.slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Left,
            animationSpec = tween(ANIMATION_DURATION)
        ) + fadeIn(animationSpec = tween(ANIMATION_DURATION))

    fun rootExitTransition(scope: AnimatedContentTransitionScope<*>) =
        scope.slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Left,
            animationSpec = tween(ANIMATION_DURATION)
        ) + fadeOut(animationSpec = tween(ANIMATION_DURATION))

    fun rootPopEnterTransition(scope: AnimatedContentTransitionScope<*>) =
        scope.slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = tween(ANIMATION_DURATION)
        ) + fadeIn(animationSpec = tween(ANIMATION_DURATION))

    fun rootPopExitTransition(scope: AnimatedContentTransitionScope<*>) =
        scope.slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = tween(ANIMATION_DURATION)
        ) + fadeOut(animationSpec = tween(ANIMATION_DURATION))
} 