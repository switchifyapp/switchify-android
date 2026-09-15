package com.enaboapps.switchify

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.enaboapps.switchify.service.utils.AppLauncher
import com.enaboapps.switchify.service.utils.FavouriteAppsManager
import com.enaboapps.switchify.switches.SwitchAction
import com.enaboapps.switchify.switches.SwitchEvent
import com.enaboapps.switchify.switches.profiles.SwitchProfile
import com.enaboapps.switchify.switches.profiles.SwitchProfileDocument
import com.enaboapps.switchify.switches.profiles.SwitchProfileLocalPersistence
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchAppTest {
    @Test fun launchTargetsSurviveStorageReloadAndOpenInstalledApps() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val persistence = SwitchProfileLocalPersistence(context)
        val original = persistence.readProfiles().getOrThrow()
        val apps = FavouriteAppsManager(context).getAllLaunchableApps()
        assertTrue(apps.any { it.packageName == "com.android.chrome" })
        assertTrue(apps.any { it.packageName == "com.android.settings" })
        val event = SwitchEvent(name = "Launch test", code = "24",
            pressAction = SwitchAction(19, "com.android.chrome"),
            holdActions = listOf(SwitchAction(19, "com.android.settings")))
        val document = SwitchProfileDocument(activeProfileId = "launch-test",
            profiles = listOf(SwitchProfile("launch-test", "Launch test", listOf(event))))
        try {
            persistence.writeProfiles(document).getOrThrow()
            val restored = SwitchProfileLocalPersistence(context).readProfiles().getOrThrow()!!
            assertEquals(document, restored)
            val saved = restored.profiles.single().switches.single()
            val launcher = AppLauncher(context)
            assertTrue(launcher.launch(saved.pressAction.packageName))
            assertTrue(launcher.launch(saved.holdActions.single().packageName))
            assertFalse(launcher.launch("invalid.missing.app"))
            assertFalse(launcher.launch(null))
        } finally {
            if (original != null) persistence.writeProfiles(original).getOrThrow()
        }
    }
}
