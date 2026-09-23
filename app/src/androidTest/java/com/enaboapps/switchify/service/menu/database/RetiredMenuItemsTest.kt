package com.enaboapps.switchify.service.menu.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RetiredMenuItemsTest {
    @Test fun restoredMenusRemoveRetiredEntriesAndKeepOtherCustomizations() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val menu = "migration_test_${UUID.randomUUID()}"
        val database = MenuDatabase.getInstance(context)
        val keep = MenuItemConfiguration(menu, "settings", 7, false)
        database.menuItemConfigurationDao().insertConfigurationsRaw(listOf(
            keep, MenuItemConfiguration(menu, "control_pc", 0),
            MenuItemConfiguration(menu, "pc_switch_forwarding", 1, sourceMenuId = "main_menu")))
        database.close()
        MenuDatabase.clearInstance()
        val restored = MenuDatabase.getInstance(context).menuItemConfigurationDao()
        try {
            assertEquals(listOf(keep), restored.getConfigurationsForMenu(menu))
            restored.insertConfiguration(MenuItemConfiguration(menu, "control_pc", 0))
            restored.insertConfigurations(listOf(MenuItemConfiguration(menu, "pc_switch_forwarding", 1)))
            assertEquals(listOf(keep), restored.getConfigurationsForMenu(menu))
        } finally {
            restored.deleteConfigurationsForMenu(menu)
        }
    }
}
