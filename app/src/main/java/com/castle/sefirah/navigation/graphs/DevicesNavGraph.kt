package com.castle.sefirah.navigation.graphs

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.castle.sefirah.navigation.DeviceRouteScreen
import com.castle.sefirah.navigation.Graph
import com.castle.sefirah.navigation.MainRouteScreen
import com.castle.sefirah.presentation.devices.DeviceSettingsScreen
import com.castle.sefirah.presentation.devices.AddressScreen

fun NavGraphBuilder.deviceNavGraph(rootNavController: NavHostController) {
    navigation(
        route = Graph.DevicesGraph,
        startDestination = MainRouteScreen.SettingsScreen.route
    ) {
        composable(
            route = "device?deviceId={deviceId}",
            arguments = listOf(
                navArgument("deviceId") {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            backStackEntry.arguments?.getString("deviceId")?.let { deviceId ->
                DeviceSettingsScreen(
                    deviceId = deviceId,
                    onNavigateBack = { rootNavController.navigateUp() },
                    onNavigateToAddressScreen = {
                        rootNavController.navigate(DeviceRouteScreen.AddressScreen.route)
                    }
                )
            }
        }

        composable(
            route = DeviceRouteScreen.AddressScreen.route
        ) {
            AddressScreen(
                rootNavController = rootNavController,
                onNavigateBack = { rootNavController.navigateUp() }
            )
        }
    }
}