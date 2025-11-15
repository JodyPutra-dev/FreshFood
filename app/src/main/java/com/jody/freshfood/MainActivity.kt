package com.jody.freshfood

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.jody.freshfood.databinding.ActivityMainBinding
import com.jody.freshfood.ui.home.HomeFragment
import com.jody.freshfood.ui.scan.ScanFragment
import com.jody.freshfood.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup bottom navigation listener
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    openFragment(HomeFragment(), "home")
                    true
                }
                R.id.navigation_scan -> {
                    openFragment(ScanFragment(), "scan")
                    true
                }
                R.id.navigation_settings -> {
                    openFragment(SettingsFragment(), "settings")
                    true
                }
                else -> false
            }
        }

        // Initialize default fragment only if first creation
        if (savedInstanceState == null) {
            binding.bottomNavigation.selectedItemId = R.id.navigation_home
        }
    }

    private fun openFragment(fragment: Fragment, tag: String) {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(binding.fragmentContainer.id, fragment, tag)
        }
    }
}