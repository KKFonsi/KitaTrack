package com.example.kitatrack

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.NavOptions
import androidx.navigation.ui.setupWithNavController
import androidx.lifecycle.lifecycleScope
import com.example.kitatrack.widget.KitaTrackWidgetUpdater
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Keep content clear of the status bar, but let BottomNavigationView handle the
            // navigation-bar inset itself so it remains visually anchored to the bottom edge.
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigation.setupWithNavController(navController)
        // Reports is temporarily hidden from the public APK while it remains under development.
        // If Android restores an old Reports destination, send the user back to Dashboard.
        navController.addOnDestinationChangedListener { controller, destination, _ ->
            if (destination.id == R.id.reportsFragment) {
                controller.navigate(
                    R.id.dashboardFragment,
                    null,
                    NavOptions.Builder()
                        .setPopUpTo(R.id.reportsFragment, true)
                        .setLaunchSingleTop(true)
                        .build()
                )
            }
        }
        handleWidgetAddTransactionIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetAddTransactionIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) {
            KitaTrackWidgetUpdater.updateAll(applicationContext)
        }
    }

    private fun handleWidgetAddTransactionIntent(intent: Intent?) {
        val type = intent?.getStringExtra(EXTRA_ADD_TRANSACTION_TYPE) ?: return
        val normalizedType = if (type == "INCOME") "INCOME" else "EXPENSE"
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment ?: return
        navHostFragment.navController.navigate(
            R.id.addTransactionFragment,
            Bundle().apply { putString("initialType", normalizedType) }
        )
        intent.removeExtra(EXTRA_ADD_TRANSACTION_TYPE)
    }

    companion object {
        const val ACTION_ADD_TRANSACTION = "com.example.kitatrack.action.ADD_TRANSACTION"
        const val EXTRA_ADD_TRANSACTION_TYPE = "com.example.kitatrack.extra.ADD_TRANSACTION_TYPE"
    }
}
