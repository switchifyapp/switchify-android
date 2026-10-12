package com.enaboapps.switchify.pc.control

class InMemoryPcPreferenceStorage : PcPreferenceStorage {
    val values = mutableMapOf<String, String>()

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}
