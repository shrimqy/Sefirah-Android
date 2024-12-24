package com.castle.sefirah.navigation

object Graph {
    const val RootGraph = "rootGraph"
    const val SyncGraph = "SyncGraph"
    const val MainScreenGraph = "mainScreenGraph"
    const val SettingsGraph = "settingsGraph"
    const val DevicesGraph = "devicesGraph"
    const val OnboardingGraph = "onboardingGraph"
}
    

sealed class MainRouteScreen(var route: String) {
    data object HomeScreen: MainRouteScreen("home")
    data object SettingsScreen: SettingsRouteScreen("settings")
    data object DevicesScreen: DevicesRouteScreen("devices")
}

sealed class SyncRoute(var route: String) {
    data object SyncScreen: SyncRoute("syncing")
}

sealed class SettingsRouteScreen(var route: String) {
    data object AboutScreen: SettingsRouteScreen("about")
}

sealed class DevicesRouteScreen(var route: String) {

}

sealed class OnboardingRoute(var route: String) {
    data object OnboardingScreen : OnboardingRoute("onboarding")
}
