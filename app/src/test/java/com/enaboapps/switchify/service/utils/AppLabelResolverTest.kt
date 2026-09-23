package com.enaboapps.switchify.service.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLabelResolverTest {
    @Test
    fun missingLabelsAndPackageFallbacksUseTheLocalizedPlaceholder() {
        listOf(null, "", "  ", "com.example.app", "com.example.app.MainActivity").forEach {
            assertEquals("Unnamed localized", AppLabelResolver.safeInstalledLabel(
                "com.example.app", it, "Unnamed localized"))
        }
    }

    @Test
    fun humanReadableNamesRemainIntactIncludingLongAndNonEnglishNames() {
        listOf("A long app name that should wrap across multiple lines", "Cámara", "カメラ").forEach {
            assertEquals(it, AppLabelResolver.safeInstalledLabel("com.example.app", " $it ", "Unnamed"))
        }
    }
}
