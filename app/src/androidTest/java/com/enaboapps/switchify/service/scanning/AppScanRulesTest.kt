package com.enaboapps.switchify.service.scanning

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AppScanRulesTest {
    @Test fun preferencesSurviveReloadAndRemoveOnlyTheChosenRule() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "app_scan_rules_${UUID.randomUUID()}"
        val prefs = base.getSharedPreferences(name, Context.MODE_PRIVATE)
        val context = object : ContextWrapper(base) {
            override fun createDeviceProtectedStorageContext(): Context = this
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
        }
        try {
            val settings = AppScanTechniqueSettings(context)
            assertTrue(settings.rules().isEmpty())
            settings.setRule("com.android.chrome", "radar")
            settings.setRule("com.android.settings", "item_scan")
            val restored = AppScanTechniqueSettings(context)
            assertEquals("radar", restored.techniqueFor("com.android.chrome"))
            restored.setRule("com.android.chrome", null)
            assertNull(settings.techniqueFor("com.android.chrome"))
            assertEquals("item_scan", settings.techniqueFor("com.android.settings"))
        } finally {
            base.deleteSharedPreferences(name)
        }
    }
}
