package com.jody.freshfood.receiver

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import android.widget.Toast

/**
 * BroadcastReceiver to handle connectivity changes
 * - Monitors airplane mode changes
 * - Detects network connectivity status
 * - Schedules pending tasks when network becomes available
 */
class ConnectivityReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                val isAirplaneModeOn = intent.getBooleanExtra("state", false)
                handleAirplaneModeChanged(context, isAirplaneModeOn)
            }

            ConnectivityManager.CONNECTIVITY_ACTION -> {
                handleConnectivityChanged(context)
            }
        }
    }

    private fun handleAirplaneModeChanged(context: Context, isEnabled: Boolean) {
        if (isEnabled) {
            Log.d(TAG, "Airplane mode enabled - Network unavailable")
            saveConnectivityState(context, false)
            Toast.makeText(
                context,
                "Airplane mode ON - Model updates paused",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Log.d(TAG, "Airplane mode disabled - Checking network...")

            // Wait a moment for network to stabilize
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (isNetworkAvailable(context)) {
                    handleNetworkAvailable(context)
                }
            }, 1000)
        }
    }

    private fun handleConnectivityChanged(context: Context) {
        val isConnected = isNetworkAvailable(context)
        val wasConnected = getLastConnectivityState(context)

        Log.d(TAG, "Connectivity changed - Connected: $isConnected (was: $wasConnected)")

        if (isConnected && !wasConnected) {
            handleNetworkAvailable(context)
        } else if (!isConnected && wasConnected) {
            handleNetworkUnavailable(context)
        }

        saveConnectivityState(context, isConnected)
    }

    private fun handleNetworkAvailable(context: Context) {
        Log.d(TAG, "Network available - Ready for tasks")

        saveConnectivityState(context, true)

        Toast.makeText(
            context,
            "Network connected - Ready to sync",
            Toast.LENGTH_SHORT
        ).show()

        // Check if there are pending tasks
        checkPendingTasks(context)
    }

    private fun handleNetworkUnavailable(context: Context) {
        Log.d(TAG, "Network unavailable - Pausing tasks")
        saveConnectivityState(context, false)
    }

    private fun checkPendingTasks(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasPendingUpdate = prefs.getBoolean(KEY_PENDING_UPDATE, false)

        if (hasPendingUpdate) {
            Log.d(TAG, "Found pending tasks - Ready to execute")
            Toast.makeText(
                context,
                "Resuming pending updates...",
                Toast.LENGTH_SHORT
            ).show()

            // Clear pending flag
            prefs.edit().putBoolean(KEY_PENDING_UPDATE, false).apply()

            // TODO: Trigger actual update logic here
            // For example: start a service or call ViewModel
        }
    }

    @SuppressLint("MissingPermission")
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo != null && networkInfo.isConnected
        }
    }

    private fun saveConnectivityState(context: Context, isConnected: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LAST_CONNECTIVITY_STATE, isConnected).apply()
    }

    private fun getLastConnectivityState(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LAST_CONNECTIVITY_STATE, true)
    }

    companion object {
        private const val TAG = "ConnectivityReceiver"
        private const val PREFS_NAME = "FreshFoodPrefs"
        private const val KEY_LAST_CONNECTIVITY_STATE = "last_connectivity_state"
        private const val KEY_PENDING_UPDATE = "pending_model_update"

        /**
         * Register receiver dynamically (recommended for connectivity)
         */
        fun register(context: Context): ConnectivityReceiver {
            val receiver = ConnectivityReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
                addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            }

            context.registerReceiver(receiver, filter)
            Log.d(TAG, "ConnectivityReceiver registered")
            return receiver
        }

        /**
         * Unregister receiver
         */
        fun unregister(context: Context, receiver: ConnectivityReceiver?) {
            receiver?.let {
                try {
                    context.unregisterReceiver(it)
                    Log.d(TAG, "ConnectivityReceiver unregistered")
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Receiver not registered", e)
                }
            }
        }

        /**
         * Check current network status
         */
        @SuppressLint("MissingPermission")
        fun isNetworkAvailable(context: Context): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo != null && networkInfo.isConnected
            }
        }

        /**
         * Mark pending update
         */
        fun markPendingUpdate(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_PENDING_UPDATE, true).apply()
            Log.d(TAG, "Marked pending update")
        }
    }
}