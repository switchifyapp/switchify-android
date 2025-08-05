package com.enaboapps.switchify

import android.app.Application
import android.util.Log

class SwitchifyApplication : Application() {
    
    companion object {
        private const val TAG = "SwitchifyApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.i(TAG, "SwitchifyApplication initialized")
    }
}