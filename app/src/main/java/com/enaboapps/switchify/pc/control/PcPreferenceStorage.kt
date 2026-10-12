package com.enaboapps.switchify.pc.control

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

interface PcPreferenceStorage {
    fun getString(key: String): String?

    fun putString(key: String, value: String?)
}

class SharedPreferencesPcStorage(private val preferences: SharedPreferences) : PcPreferenceStorage {
    constructor(context: Context, fileName: String) : this(
        context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    )

    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun putString(key: String, value: String?) {
        preferences.edit { if (value == null) remove(key) else putString(key, value) }
    }
}

object PcControlStorage {
    const val FILE_NAME = "switchify_pc_control"
}
