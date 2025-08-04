package com.enaboapps.switchify.backend.supabase.models

import kotlinx.serialization.Serializable

@Serializable
data class TypedPreferenceValue(
    val value: String,
    val type: String // "string", "boolean", "int", "long", "float"
)

@Serializable
data class TypedUserPreferences(
    val user_id: String,
    val preferences: Map<String, TypedPreferenceValue>,
    val created_at: String? = null,
    val updated_at: String? = null
)

/**
 * Helper class to convert between typed values and TypedPreferenceValue
 */
object PreferenceTypeConverter {
    fun toTypedValue(value: Any): TypedPreferenceValue {
        return when (value) {
            is String -> TypedPreferenceValue(value, "string")
            is Boolean -> TypedPreferenceValue(value.toString(), "boolean")
            is Int -> TypedPreferenceValue(value.toString(), "int")
            is Long -> TypedPreferenceValue(value.toString(), "long")
            is Float -> TypedPreferenceValue(value.toString(), "float")
            else -> TypedPreferenceValue(value.toString(), "string")
        }
    }
    
    fun fromTypedValue(typedValue: TypedPreferenceValue): Any {
        return when (typedValue.type) {
            "boolean" -> typedValue.value.toBooleanStrictOrNull() ?: false
            "int" -> typedValue.value.toIntOrNull() ?: 0
            "long" -> typedValue.value.toLongOrNull() ?: 0L
            "float" -> typedValue.value.toFloatOrNull() ?: 0f
            "string" -> typedValue.value
            else -> typedValue.value
        }
    }
    
    fun fromTypedPreferences(typedPrefs: Map<String, TypedPreferenceValue>): Map<String, Any> {
        return typedPrefs.mapValues { (_, typedValue) -> fromTypedValue(typedValue) }
    }
    
    fun toTypedPreferences(prefs: Map<String, Any>): Map<String, TypedPreferenceValue> {
        return prefs.mapValues { (_, value) -> toTypedValue(value) }
    }
}