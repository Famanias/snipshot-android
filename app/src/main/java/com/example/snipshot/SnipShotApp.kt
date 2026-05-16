package com.example.snipshot

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import com.example.snipshot.api.ApiClient

class SnipShotApp : Application(), Application.ActivityLifecycleCallbacks {
    
    private var activityReferences = 0
    private var isActivityChangingConfigurations = false

    override fun onCreate() {
        super.onCreate()
        ApiClient.init(this)
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        if (++activityReferences == 1 && !isActivityChangingConfigurations) {
            // App enters foreground
            stopService(Intent(this, BubbleService::class.java))
        }
    }

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {
        isActivityChangingConfigurations = activity.isChangingConfigurations
        if (--activityReferences == 0 && !isActivityChangingConfigurations) {
            // App enters background
            if (Settings.canDrawOverlays(this)) {
                startService(Intent(this, BubbleService::class.java))
            }
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}
}
