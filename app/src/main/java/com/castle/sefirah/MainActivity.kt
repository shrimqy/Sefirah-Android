package com.castle.sefirah

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.castle.sefirah.ui.theme.SefirahTheme
import com.castle.sefirah.navigation.graphs.RootNavGraph
import dagger.hilt.android.AndroidEntryPoint
import sefirah.common.notifications.AppNotifications

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        installSplashScreen()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setupNotificationChannels()
        }
        enableEdgeToEdge()
        setContent {
            SefirahTheme {
                val pendingApproval by viewModel.pendingDeviceApproval.collectAsState()

                Box(
                    modifier = Modifier
                        .background(color = MaterialTheme.colorScheme.background)
                        .fillMaxSize()
                ) {
                    RootNavGraph(viewModel.startDestination)

                    pendingApproval?.let { approval ->
                        AlertDialog(
                            onDismissRequest = { viewModel.rejectDevice(approval.deviceId) },
                            title = { Text("Connection Request") },
                            text = {
                                Column {
                                    Text("${approval.deviceName} wants to connect.")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Verification Code: ${approval.verificationCode}",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = { viewModel.approveDevice(approval.deviceId) }
                                ) {
                                    Text("Accept")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { viewModel.rejectDevice(approval.deviceId) }
                                ) {
                                    Text("Reject")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupNotificationChannels() {
        try {
            AppNotifications.createChannels(this)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error creating notification channels", e)
        }
    }
}

