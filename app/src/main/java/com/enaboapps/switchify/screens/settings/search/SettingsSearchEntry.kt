package com.enaboapps.switchify.screens.settings.search

data class SettingsSearchEntry(
    val route: String,
    val tab: Int?,
    val screenTitleResId: Int?,
    val sectionTitleResId: Int?,
    val titleResId: Int,
    val summaryResId: Int?,
    val explanationResId: Int?
)
