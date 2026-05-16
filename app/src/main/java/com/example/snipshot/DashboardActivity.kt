package com.example.snipshot

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.snipshot.api.ApiClient
import com.example.snipshot.ui.MyFilesFragment
import com.example.snipshot.ui.RecentFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class DashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        setSupportActionBar(findViewById(R.id.toolbar))

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_my_files -> {
                    loadFragment(MyFilesFragment())
                    true
                }
                R.id.nav_recent -> {
                    loadFragment(RecentFragment())
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    false // Don't select the settings item in bottom nav
                }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_my_files
        }

        if (!android.provider.Settings.canDrawOverlays(this)) {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, 1234)
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.dashboard_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val loginItem = menu.findItem(R.id.action_login)
        val logoutItem = menu.findItem(R.id.action_logout)
        
        if (ApiClient.isLoggedIn()) {
            loginItem?.isVisible = false
            logoutItem?.isVisible = true
        } else {
            loginItem?.isVisible = true
            logoutItem?.isVisible = false
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_login -> {
                startActivity(Intent(this, LoginActivity::class.java))
                true
            }
            R.id.action_logout -> {
                ApiClient.logout()
                invalidateOptionsMenu() // Refresh menu
                loadFragment(MyFilesFragment()) // Refresh fragment for logged out state
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        invalidateOptionsMenu() // In case user logged in via LoginActivity and returned
    }
}
