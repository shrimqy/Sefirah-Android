package com.castle.sefirah.navigation.graphs

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.castle.sefirah.presentation.devices.DevicesScreen
import com.castle.sefirah.presentation.home.HomeScreen
import com.castle.sefirah.presentation.settings.SettingsScreen
import com.castle.sefirah.navigation.Graph
import com.castle.sefirah.navigation.MainRouteScreen

@Composable
fun MainNavGraph(
    rootNavController: NavHostController,
    homeNavController: NavHostController,
    innerPadding: PaddingValues
) {
    NavHost(
        navController = homeNavController,
        route = Graph.MainScreenGraph,
        startDestination = MainRouteScreen.HomeScreen.route,
        modifier = Modifier.padding(innerPadding),
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() }
    ) {
        composable(route = MainRouteScreen.HomeScreen.route) {
            HomeScreen(rootNavController)
        }
        composable(route = MainRouteScreen.DevicesScreen.route) {
            DevicesScreen(rootNavController)
        }
        composable(route = MainRouteScreen.SettingsScreen.route) {
            SettingsScreen(rootNavController)
        }
    }
}