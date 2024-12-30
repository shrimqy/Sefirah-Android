package com.castle.sefirah

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import sefirah.network.NetworkService

abstract class BaseActivity : ComponentActivity() {
    protected var networkService: NetworkService? = null
    protected var bound: Boolean = false
    
    private var serviceConnectionCallback: ((Boolean) -> Unit)? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as NetworkService.LocalBinder
            networkService = binder.getService()
            bound = true
            serviceConnectionCallback?.invoke(true)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            bound = false
            serviceConnectionCallback?.invoke(false)
        }
    }

    protected fun setServiceConnectionCallback(callback: (Boolean) -> Unit) {
        serviceConnectionCallback = callback
        // If already bound, notify immediately
        if (bound) {
            callback(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindService()
    }

    private fun bindService() {
        Intent(this, NetworkService::class.java).also { intent ->
            Log.d("BaseActivity", "Binding Network Service")
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }
}