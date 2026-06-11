package com.castle.sefirah.presentation.main

import android.util.Log
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.castle.sefirah.R
import com.castle.sefirah.navigation.MainRouteScreen
import com.castle.sefirah.navigation.SettingsRouteScreen
import com.castle.sefirah.navigation.SyncRoute
import com.castle.sefirah.navigation.graphs.MainNavGraph
import sefirah.data.repository.ReleaseRepository
import sefirah.presentation.components.AppTopBar
import sefirah.presentation.components.NavigationItem
import sefirah.presentation.components.NavigationItemIcon
import sefirah.presentation.components.PullRefresh
import sefirah.common.R as CommonR

@Composable
fun MainScreen(
    rootNavController: NavHostController,
    homeNavController: NavHostController = rememberNavController(),
    viewModel: ConnectionViewModel = hiltViewModel(),
) {
    val backStackState = homeNavController.currentBackStackEntryAsState().value
    val currentRoute = backStackState?.destination?.route

    val navigationItems = navigationItems()
    val selectedItem = navigationItems.indexOfFirst { it.route == currentRoute }.takeIf { it >= 0 } ?: 0

    val hasCheckedForUpdate = remember { viewModel.hasCheckedForUpdate }

    LaunchedEffect(Unit) {
        if (!hasCheckedForUpdate.value) {
            Log.d("MainScreen", "Checking for update")
            val result = viewModel.checkForUpdate()
            if (result is ReleaseRepository.Result.NewUpdate) {
                rootNavController.navigate(SettingsRouteScreen.NewUpdateScreen.route)
            }
            hasCheckedForUpdate.value = true
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    PullRefresh(
        refreshing = isRefreshing,
        enabled = currentRoute == MainRouteScreen.HomeScreen.route,
        onRefresh = {
            if (currentRoute == MainRouteScreen.HomeScreen.route) {
                viewModel.toggleSync(true)
            }
        },
    ) {
        MainNavigationSuite(
            rootNavController = rootNavController,
            homeNavController = homeNavController,
            connectionViewModel = viewModel,
            navigationItems = navigationItems,
            selectedItem = selectedItem,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            currentRoute = currentRoute,
        )
    }
}

@Composable
private fun MainNavigationSuite(
    rootNavController: NavHostController,
    homeNavController: NavHostController,
    connectionViewModel: ConnectionViewModel,
    navigationItems: List<NavigationItem>,
    selectedItem: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    currentRoute: String?,
) {
    val navigationSuiteType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(
        currentWindowAdaptiveInfo(),
    )

    NavigationSuiteScaffold(
        modifier = Modifier.fillMaxSize(),
        navigationSuiteType = navigationSuiteType,
        navigationItemVerticalArrangement = Arrangement.Center,
        navigationItems = {
            navigationItems.forEachIndexed { index, item ->
                NavigationSuiteItem(
                    navigationSuiteType = navigationSuiteType,
                    icon = {
                        NavigationItemIcon(
                            icon = item.icon,
                            selected = index == selectedItem,
                        )
                    },
                    label = { Text(text = item.text) },
                    selected = index == selectedItem,
                    onClick = {
                        navigateToTab(homeNavController, navigationItems[index].route)
                    },
                )
            }
        },
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                AppTopBar(
                    items = navigationItems,
                    selectedItem = selectedItem,
                    onSearchQueryChange = onSearchQueryChange,
                )
            },
            floatingActionButton = {
                if (currentRoute == MainRouteScreen.DeviceListScreen.route) {
                    FloatingActionButton(
                        onClick = { rootNavController.navigate(SyncRoute.SyncScreen.route) },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Device",
                        )
                    }
                }
            },
        ) { contentPadding ->
            MainNavGraph(
                rootNavController = rootNavController,
                homeNavController = homeNavController,
                innerPadding = contentPadding,
                searchQuery = searchQuery,
                connectionViewModel = connectionViewModel,
            )
        }
    }
}

@Composable
private fun navigationItems(): List<NavigationItem> = listOf(
    NavigationItem(
        icon = AnimatedImageVector.animatedVectorResource(R.drawable.ic_home),
        text = stringResource(CommonR.string.home),
        route = MainRouteScreen.HomeScreen.route,
    ),
    NavigationItem(
        icon = AnimatedImageVector.animatedVectorResource(R.drawable.ic_devices),
        text = stringResource(CommonR.string.devices),
        route = MainRouteScreen.DeviceListScreen.route,
    ),
    NavigationItem(
        icon = AnimatedImageVector.animatedVectorResource(R.drawable.ic_settings),
        text = stringResource(CommonR.string.settings),
        route = MainRouteScreen.SettingsScreen.route,
    ),
)

internal fun navigateToTab(navController: NavController, route: String) {
    navController.navigate(route) {
        navController.graph.startDestinationRoute?.let { screenRoute ->
            popUpTo(screenRoute) {
                saveState = true
            }
        }
        launchSingleTop = true
        restoreState = true
    }
}
