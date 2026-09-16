package com.enaboapps.switchify

import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.enaboapps.switchify.screens.settings.switches.models.AddEditExternalSwitchScreenModel
import com.enaboapps.switchify.switches.SwitchAction
import com.enaboapps.switchify.switches.SwitchEvent
import com.enaboapps.switchify.switches.SwitchEventStore
import com.enaboapps.switchify.switches.profiles.SwitchProfileLocalPersistence
import com.enaboapps.switchify.switches.profiles.SwitchProfileMutationResult
import com.enaboapps.switchify.switches.profiles.SwitchProfileRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HoldActionsPersistenceTest {
    @Test
    fun parentSavePreservesEditedHoldsAndUnsavedDetails() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = SwitchProfileRepository.getInstance(context)
        repository.initialize()
        val profile = (repository.createEmpty("Hold regression ${System.nanoTime()}")
            as SwitchProfileMutationResult.Success).profile
        val store = SwitchEventStore.getInstance()
        val viewModels = ViewModelStore()
        val original = SwitchEvent(name = "Hold regression", code = "62", pressAction = SwitchAction(1),
            holdActions = emptyList())
        val chrome = SwitchAction(19, "com.android.chrome")
        val settings = SwitchAction(19, "com.android.settings")
        try {
            assertTrue(repository.replaceEvents(profile.id, listOf(original)))
            store.initializeAsync(context)
            val model = withContext(Dispatchers.Main) {
                AddEditExternalSwitchScreenModel().also {
                    viewModels.put("switch", it)
                    it.init("62", context, profile.id)
                    it.updateName("Unsaved name")
                    it.setPressAction(chrome, context)
                }
            }
            val holdEdits = listOf(listOf(chrome), listOf(chrome, settings),
                listOf(settings, chrome), listOf(settings, SwitchAction(1)), listOf(settings), emptyList())
            for (holds in holdEdits) {
                val saved = CompletableDeferred<Boolean>()
                store.updateHoldActions("62", holds, context, profile.id) { saved.complete(it) }
                assertTrue(withTimeout(5000) { saved.await() })
                withContext(Dispatchers.Main) {
                    withTimeout(5000) {
                        while (model.longPressActions.value != holds) delay(10)
                    }
                    assertEquals("Unsaved name", model.name)
                    assertEquals(chrome, model.pressAction.value)
                    assertTrue(model.hasUnsavedChanges.value == true)
                }
                assertEquals(original.name, repository.events(profile.id).single().name)
                assertEquals(original.pressAction, repository.events(profile.id).single().pressAction)
            }
            val holds = listOf(settings, chrome)
            val saved = CompletableDeferred<Boolean>()
            store.updateHoldActions("62", holds, context, profile.id) { saved.complete(it) }
            assertTrue(withTimeout(5000) { saved.await() })
            val parentSaved = CompletableDeferred<Boolean>()
            withContext(Dispatchers.Main) {
                model.longPressActions.value = emptyList()
                model.save(context) { parentSaved.complete(it) }
            }
            assertTrue(withTimeout(5000) { parentSaved.await() })
            val expected = original.copy(name = "Unsaved name", pressAction = chrome, holdActions = holds)
            assertEquals(expected, repository.events(profile.id).single())
            val restored = SwitchProfileLocalPersistence(context).readProfiles().getOrThrow()!!
            assertEquals(expected, restored.profiles.first { it.id == profile.id }.switches.single())
        } finally {
            withContext(Dispatchers.Main) { viewModels.clear() }
            repository.delete(profile.id)
        }
    }

    @Test
    fun failedParentSaveRemainsAvailableForRetry() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = SwitchProfileRepository.getInstance(context)
        repository.initialize()
        val profile = (repository.createEmpty("Hold retry ${System.nanoTime()}")
            as SwitchProfileMutationResult.Success).profile
        val original = SwitchEvent(name = "Retry", code = "62", pressAction = SwitchAction(1),
            holdActions = listOf(SwitchAction(19, "com.android.settings")))
        val viewModels = ViewModelStore()
        try {
            assertTrue(repository.replaceEvents(profile.id, listOf(original)))
            SwitchEventStore.getInstance().initializeAsync(context)
            val model = withContext(Dispatchers.Main) {
                AddEditExternalSwitchScreenModel().also {
                    viewModels.put("switch", it)
                    it.init("62", context, profile.id)
                    it.updateName("Retry edited")
                }
            }
            assertTrue(repository.replaceEvents(profile.id, emptyList()))
            val failed = CompletableDeferred<Boolean>()
            withContext(Dispatchers.Main) { model.save(context) { failed.complete(it) } }
            assertFalse(withTimeout(5000) { failed.await() })
            withContext(Dispatchers.Main) {
                assertTrue(model.shouldSave.value == true)
                assertTrue(model.hasUnsavedChanges.value == true)
            }
            assertTrue(repository.replaceEvents(profile.id, listOf(original)))
            val retried = CompletableDeferred<Boolean>()
            withContext(Dispatchers.Main) { model.save(context) { retried.complete(it) } }
            assertTrue(withTimeout(5000) { retried.await() })
            assertEquals(original.copy(name = "Retry edited"), repository.events(profile.id).single())
        } finally {
            withContext(Dispatchers.Main) { viewModels.clear() }
            repository.delete(profile.id)
        }
    }
}
