package com.enaboapps.switchify.components

import com.enaboapps.switchify.service.utils.FavouriteAppsManager.FavouriteApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPickerFilterTest {
    private val apps = listOf(FavouriteApp("com.example.browser", "Browser"),
        FavouriteApp("com.example.other", "Browser"), FavouriteApp("com.example.settings", "Settings"))

    @Test
    fun searchUsesNamesOnlyAndPreservesDistinctAppsWithTheSameName() {
        assertEquals(apps.take(2), AppPickerFilter.filter(apps, " bROWser ", emptySet()))
        assertTrue(AppPickerFilter.filter(apps, "com.example", emptySet()).isEmpty())
        assertTrue(AppPickerFilter.filter(apps, "Missing", emptySet()).isEmpty())
        assertEquals(apps, AppPickerFilter.filter(apps, "", emptySet()))
    }

    @Test
    fun favouritesAreExcludedByIdentityWithoutChangingRemainingOrder() {
        assertEquals(apps.drop(1), AppPickerFilter.filter(apps, "", setOf(apps.first().packageName)))
        assertTrue(AppPickerFilter.filter(apps, "", apps.map { it.packageName }.toSet()).isEmpty())
    }
}
