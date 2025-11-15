package com.jody.freshfood.ui.splash

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jody.freshfood.MainActivity
import com.jody.freshfood.databinding.ActivitySplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    companion object {
        @Volatile
        var isInitialized: Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ensure minimum splash time and wait for initialization signal from Application
        lifecycleScope.launch {
            val minDelayMs = 1500L
            val start = System.currentTimeMillis()

            // Wait until Model/Application initialization flag is set or timeout (5s)
            val timeoutMs = 5000L
            var waited = 0L
            while (!isInitialized && waited < timeoutMs) {
                delay(100)
                waited = System.currentTimeMillis() - start
            }

            val elapsed = System.currentTimeMillis() - start
            if (elapsed < minDelayMs) {
                delay(minDelayMs - elapsed)
            }

            // Proceed to MainActivity
            try {
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            } catch (e: Exception) {
                Log.e("SplashActivity", "Failed to start MainActivity: ${e.message}")
                Toast.makeText(this@SplashActivity, getString(com.jody.freshfood.R.string.splash_error), Toast.LENGTH_LONG).show()
            } finally {
                finish()
            }
        }
    }
}
