package com.castle.sefirah

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.castle.sefirah.ui.theme.SefirahTheme
import com.castle.sefirah.navigation.graphs.RootNavGraph
import dagger.hilt.android.AndroidEntryPoint
import sefirah.common.notifications.AppNotifications

@AndroidEntryPoint
class MainActivity : BaseActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen().apply {
            setKeepOnScreenCondition {
                viewModel.splashCondition
            }
        }
        setupNotificationChannels()
        enableEdgeToEdge()
        setContent {
            SefirahTheme() {
                Box(
                    modifier = Modifier
                        .background(color = MaterialTheme.colorScheme.background)
                        .fillMaxSize()
                ) {
                    RootNavGraph(startDestination = viewModel.startDestination)
                }
            }
        }
    }

    private fun setupNotificationChannels() {
        try {
            AppNotifications.createChannels(this)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error creating notification channels", e)
        }
    }
}

