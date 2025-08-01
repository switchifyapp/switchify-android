package com.enaboapps.switchify

import android.app.Application
import android.util.Log
import com.enaboapps.switchify.backend.supabase.SupabaseClient

class SwitchifyApplication : Application() {
    
    companion object {
        private const val TAG = "SwitchifyApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Supabase client
        initializeSupabase()
        
        Log.i(TAG, "SwitchifyApplication initialized")
    }
    
    private fun initializeSupabase() {
        try {
            // Force initialization of Supabase client
            val client = SupabaseClient.client
            Log.i(TAG, "Supabase client initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Supabase client", e)
        }
    }
}