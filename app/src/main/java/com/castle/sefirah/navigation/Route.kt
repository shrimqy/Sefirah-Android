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
    data object DeviceListScreen: DeviceRouteScreen("deviceList")
    data object SettingsScreen: SettingsRouteScreen("settings")
}

sealed class SyncRoute(var route: String) {
    data object SyncScreen: SyncRoute("syncing")
}

sealed class SettingsRouteScreen(var route: String) {
    data object NetworkScreen: SettingsRouteScreen("discovery")
    data object AboutScreen: SettingsRouteScreen("about")
    data object PermissionScreen: SettingsRouteScreen("permission")
}

sealed class DeviceRouteScreen(var route: String) {
    data object EditDeviceScreen: DeviceRouteScreen("editDevice")
    data object CustomDeviceScreen: DeviceRouteScreen("customDevice")
}

sealed class OnboardingRoute(var route: String) {
    data object OnboardingScreen : OnboardingRoute("onboarding")
}
