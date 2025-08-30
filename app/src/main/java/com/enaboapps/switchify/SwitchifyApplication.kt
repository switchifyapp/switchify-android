package com.enaboapps.switchify

import android.app.Application
import android.util.Log
import com.enaboapps.switchify.utils.Resources

class SwitchifyApplication : Application() {

    companion object {
        private const val TAG = "SwitchifyApplication"
    }

    override fun onCreate() {
        super.onCreate()

        Resources.init(this)

        Log.i(TAG, "SwitchifyApplication initialized")
    }
}