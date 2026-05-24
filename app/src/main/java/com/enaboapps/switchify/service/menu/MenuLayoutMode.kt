package com.enaboapps.switchify.service.menu

enum class MenuLayoutMode(val prefValue: Int) {
    RING(0),
    LIST(1);

    companion object {
        fun fromPref(value: Int): MenuLayoutMode =
            values().firstOrNull { it.prefValue == value } ?: RING
    }
}
