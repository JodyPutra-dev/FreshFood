package com.jody.freshfood

import android.app.Application
import android.util.Log
import com.jody.freshfood.data.local.database.FreshFoodDatabase
import com.jody.freshfood.ml.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FreshFoodApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize DB and ML models off the main thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // initialize Room database (lazy singletons will be created)
                FreshFoodDatabase.getDatabase(applicationContext)
                // initialize ML models (copy assets to internal storage if needed)
                ModelManager.initialize(applicationContext)
                Log.i("FreshFoodApp", "Initialization complete")
                // signal SplashActivity that initialization is complete
                com.jody.freshfood.ui.splash.SplashActivity.isInitialized = true
            } catch (e: Exception) {
                Log.e("FreshFoodApp", "Initialization error: ${e.message}")
                // still signal to avoid hanging splash
                com.jody.freshfood.ui.splash.SplashActivity.isInitialized = true
            }
        }
    }
}
