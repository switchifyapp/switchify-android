package com.enaboapps.switchify.backend.supabase

import android.util.Log
import com.enaboapps.switchify.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.SupabaseClient as SupabaseClientInstance

object SupabaseClient {

    private const val TAG = "SupabaseClient"

    @Volatile
    private var _client: SupabaseClientInstance? = null

    val client: SupabaseClientInstance
        get() {
            return _client ?: synchronized(this) {
                _client ?: createClient().also { _client = it }
            }
        }

    fun initialize() {
        // Force initialization of the client
        client
        Log.i(TAG, "Supabase client initialized")
    }

    private fun createClient(): SupabaseClientInstance {
        return createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }

    fun isInitialized(): Boolean {
        return _client != null
    }
}