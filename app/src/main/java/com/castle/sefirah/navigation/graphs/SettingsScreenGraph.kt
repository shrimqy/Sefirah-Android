package com.komu.sekia.navigation.graphs

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.castle.sefirah.presentation.about.AboutScreen
import com.castle.sefirah.navigation.Graph
import com.castle.sefirah.navigation.MainRouteScreen
import com.castle.sefirah.navigation.SettingsRouteScreen

fun NavGraphBuilder.settingsNavGraph(rootNavController: NavHostController) {
    navigation(
        route = Graph.SettingsGraph,
        startDestination = MainRouteScreen.SettingsScreen.route
    ) {
        composable(route = SettingsRouteScreen.AboutScreen.route) {
            AboutScreen(rootNavController)
        }
    }
}
