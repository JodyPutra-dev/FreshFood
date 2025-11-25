package com.jody.freshfood

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.jody.freshfood.databinding.ActivityMainBinding
import com.jody.freshfood.receiver.ConnectivityReceiver
import androidx.navigation.fragment.NavHostFragment // Diperlukan untuk perbaikan NavController

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var connectivityReceiver: ConnectivityReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.bottomNavigation

        // Ini mengatasi masalah timing (IllegalStateException)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment
        val navController = navHostFragment.navController

        // Menghubungkan BottomNavigationView dengan NavController
        navView.setupWithNavController(navController)

        // Register ConnectivityReceiver
        connectivityReceiver = ConnectivityReceiver.register(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister receiver ketika activity dihancurkan
        ConnectivityReceiver.unregister(this, connectivityReceiver)
        connectivityReceiver = null
    }
}