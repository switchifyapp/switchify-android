package com.enaboapps.switchify.backend.supabase.models

import kotlinx.serialization.Serializable

@Serializable
data class UserPreferences(
    val user_id: String,
    val preferences: Map<String, String>,
    val created_at: String? = null,
    val updated_at: String? = null
)