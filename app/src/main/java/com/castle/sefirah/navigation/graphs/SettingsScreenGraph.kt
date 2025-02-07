package com.castle.sefirah.navigation.graphs

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.castle.sefirah.presentation.about.AboutScreen
import com.castle.sefirah.navigation.Graph
import com.castle.sefirah.navigation.MainRouteScreen
import com.castle.sefirah.navigation.SettingsRouteScreen
import com.castle.sefirah.presentation.network.NetworkScreen
import com.castle.sefirah.presentation.permission.PermissionScreen

fun NavGraphBuilder.settingsNavGraph(rootNavController: NavHostController) {
    navigation(
        route = Graph.SettingsGraph,
        startDestination = MainRouteScreen.SettingsScreen.route
    ) {
        composable(route = SettingsRouteScreen.NetworkScreen.route) {
            NetworkScreen(rootNavController)
        }
        composable(route = SettingsRouteScreen.AboutScreen.route) {
            AboutScreen(rootNavController)
        }
        composable(route = SettingsRouteScreen.PermissionScreen.route) {
            PermissionScreen(rootNavController,)
        }
    }
}
