package com.enaboapps.switchify.service.menu

sealed class MenuLayoutMode {
    data object List : MenuLayoutMode()
    data class Grid(val columns: Int) : MenuLayoutMode()
}
