package com.castle.sefirah.navigation.graphs

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.castle.sefirah.presentation.main.MainScreen
import com.castle.sefirah.presentation.onboarding.OnboardingScreen
import com.castle.sefirah.presentation.sync.SyncScreen
import com.castle.sefirah.navigation.Graph
import com.castle.sefirah.navigation.OnboardingRoute
import com.castle.sefirah.navigation.SyncRoute

@Composable
fun RootNavGraph(startDestination: String) {
    val rootNavController = rememberNavController()

    NavHost(
        navController = rootNavController,
        route = Graph.RootGraph,
        startDestination = startDestination,
    ) {

        composable(route = OnboardingRoute.OnboardingScreen.route) {
            OnboardingScreen(
                onComplete = {
                // Navigate to main screen or sync screen after onboarding
                rootNavController.navigate(Graph.MainScreenGraph) {
                    popUpTo(OnboardingRoute.OnboardingScreen.route) {
                        inclusive = true
                    }
                }
            })
        }
        composable(route = SyncRoute.SyncScreen.route) {
            SyncScreen(rootNavController = rootNavController)
        }
        composable(route = Graph.MainScreenGraph) {
            MainScreen(rootNavController = rootNavController)
        }
        settingsNavGraph(rootNavController)
    }
}

