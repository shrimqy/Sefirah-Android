package com.castle.sefirah.presentation.main

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.castle.sefirah.R
import com.castle.sefirah.navigation.DeviceRouteScreen
import com.castle.sefirah.navigation.MainRouteScreen
import com.castle.sefirah.navigation.SettingsRouteScreen
import com.castle.sefirah.navigation.SyncRoute
import com.castle.sefirah.navigation.graphs.MainNavGraph
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import sefirah.data.repository.ReleaseRepository
import sefirah.presentation.components.AppTopBar
import sefirah.presentation.components.NavBar
import sefirah.presentation.components.NavigationItem
import sefirah.presentation.components.PullRefresh
import sefirah.common.R as CommonR


@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun MainScreen(
    rootNavController: NavHostController,
    homeNavController: NavHostController = rememberNavController(),
    viewModel: ConnectionViewModel = hiltViewModel()
) {
    val backStackState = homeNavController.currentBackStackEntryAsState().value


    val homeText = stringResource(CommonR.string.home)
    val devicesText = stringResource(CommonR.string.devices)
    val settingsText = stringResource(CommonR.string.settings)

    val homeAnimatedIcon = AnimatedImageVector.animatedVectorResource(R.drawable.ic_home)
    val devicesAnimatedIcon = AnimatedImageVector.animatedVectorResource(R.drawable.ic_devices)
    val settingsAnimatedIcon = AnimatedImageVector.animatedVectorResource(R.drawable.ic_settings)

    val navigationItems = remember {
        listOf(
            NavigationItem(homeAnimatedIcon, text = homeText, route = MainRouteScreen.HomeScreen.route),
            NavigationItem(devicesAnimatedIcon, text = devicesText, route = MainRouteScreen.DeviceListScreen.route),
            NavigationItem(settingsAnimatedIcon, text = settingsText, route = MainRouteScreen.SettingsScreen.route)
        )
    }

    // Add this state to track if we've checked for updates
    val hasCheckedForUpdate = remember { viewModel.hasCheckedForUpdate }

    LaunchedEffect(key1 = Unit) {
        // Only check for updates if we haven't done so yet
        if (!hasCheckedForUpdate.value) {
            Log.d("MainScreen", "Checking for update")
            
            val result = viewModel.checkForUpdate()
            if (result is ReleaseRepository.Result.NewUpdate) {
                rootNavController.navigate(SettingsRouteScreen.NewUpdateScreen.route)
            }
            
            // Mark that we've checked for updates
            hasCheckedForUpdate.value = true
        }
    }

    val currentRoute = backStackState?.destination?.route

    val selectedItem = remember(backStackState) {
        navigationItems.indexOfFirst { it.route == currentRoute }.takeIf { it >= 0 } ?: 0
    }

    //Hide the bottom navigation when the user is in the details screen
    val isBarVisible = remember(key1 = backStackState) {
        navigationItems.any { it.route == currentRoute }
    }

    val isPullRefreshEnabled = remember(key1 = backStackState) {
        currentRoute == MainRouteScreen.HomeScreen.route
    }
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    PullRefresh(
        refreshing = isRefreshing,
        enabled = isPullRefreshEnabled,
        onRefresh = { viewModel.toggleSync(true) }
    ) {
        Scaffold (modifier = Modifier.fillMaxSize(),
            floatingActionButton = {
                if (currentRoute == MainRouteScreen.DeviceListScreen.route) {
                    FloatingActionButton(
                        onClick = { rootNavController.navigate(SyncRoute.SyncScreen.route) },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Device"
                        )
                    }
                }
            },
            topBar = {
                if (isBarVisible) {
                    AppTopBar(
                        items = navigationItems,
                        selectedItem = selectedItem,
                        onAddDeviceClick = { rootNavController.navigate(DeviceRouteScreen.CustomDeviceScreen.route) },
                        onSearchQueryChange = { query -> 
                            searchQuery = query
                        }
                    )
                }
            },
            bottomBar = {
                if (isBarVisible) {
                    BottomNavigationBar(
                        items = navigationItems,
                        selectedItem = selectedItem,
                        onItemClick = { index ->
                            navigateToTab(
                                navController = homeNavController,
                                route = navigationItems[index].route
                            )
                        }
                    )
                }
            }
        ) { innerPadding ->
            MainNavGraph(
                rootNavController = rootNavController,
                homeNavController = homeNavController,
                innerPadding = innerPadding,
                searchQuery = searchQuery
            )
        }
    }

}

private fun navigateToTab(navController: NavController, route: String) {
    navController.navigate(route) {
        navController.graph.startDestinationRoute?.let { screenRoute ->
            popUpTo(screenRoute ) {
                saveState = true
            }
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomNavigationBar(
    items: List<NavigationItem>,
    selectedItem: Int,
    onItemClick: (Int) -> Unit
) {
    val showBottomNavEvent = remember { Channel<Boolean>() }
    val bottomNavVisible by produceState(initialValue = true) {
        showBottomNavEvent.receiveAsFlow().collectLatest { value = it }
    }

    AnimatedVisibility(
        visible = bottomNavVisible,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        NavBar(
            items = items,
            selectedItem = selectedItem,
            onItemClick = onItemClick
        )
    }
}