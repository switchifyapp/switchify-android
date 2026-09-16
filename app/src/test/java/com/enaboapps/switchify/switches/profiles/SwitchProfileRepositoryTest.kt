package com.enaboapps.switchify.switches.profiles

import com.enaboapps.switchify.switches.SwitchAction
import com.enaboapps.switchify.switches.SwitchEvent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchProfileRepositoryTest {
    @Test
    fun eventUpdatesPreserveLatestHoldsAndOtherProfiles() = runBlocking {
        val original = event("62")
        val other = event("66")
        val persistence = FakePersistence(stored = SwitchProfileDocument(
            activeProfileId = "one",
            profiles = listOf(SwitchProfile("one", "One", listOf(original, other)),
                SwitchProfile("two", "Two", listOf(original)))
        ))
        val repository = repository(persistence)
        repository.initialize()
        val chrome = SwitchAction(19, "com.android.chrome")
        val settings = SwitchAction(19, "com.android.settings")
        val holdEdits = listOf(listOf(chrome), listOf(chrome, settings),
            listOf(settings, chrome), listOf(settings, SwitchAction(1)), listOf(settings), emptyList())
        for (holds in holdEdits) {
            assertTrue(repository.updateEvent("one", "62") { it.copy(holdActions = holds) })
            assertTrue(repository.updateEvent("one", "62") {
                it.copy(name = "Renamed", pressAction = chrome)
            })
            assertEquals(original.copy(name = "Renamed", pressAction = chrome, holdActions = holds),
                repository.events("one").first())
            assertEquals(other, repository.events("one")[1])
            assertEquals(listOf(original), repository.events("two"))
            assertEquals(repository.document.value, persistence.stored)
        }
    }

    @Test(timeout = 5000)
    fun eventUpdateReadsLatestStateAfterWaitingForPersistence() = runBlocking {
        val persistence = FakePersistence(legacy = listOf(event("62")))
        val repository = repository(persistence)
        repository.initialize()
        val profileId = repository.activeProfile().id
        val enteredWrite = CompletableDeferred<Unit>()
        val finishWrite = CompletableDeferred<Unit>()
        persistence.beforeWrite = { enteredWrite.complete(Unit); finishWrite.await() }
        val holds = listOf(SwitchAction(19, "com.android.settings"))
        val holding = async { repository.updateEvent(profileId, "62") { it.copy(holdActions = holds) } }
        enteredWrite.await()
        val editing = async(start = CoroutineStart.UNDISPATCHED) {
            repository.updateEvent(profileId, "62") { it.copy(name = "Edited") }
        }
        finishWrite.complete(Unit)
        assertTrue(holding.await())
        assertTrue(editing.await())
        assertEquals(event("62").copy(name = "Edited", holdActions = holds), repository.events().single())
    }

    @Test
    fun failedEventUpdatesPreserveStateAndCanBeRetried() = runBlocking {
        val original = event("62").copy(holdActions = listOf(SwitchAction(19, "com.android.chrome")))
        val persistence = FakePersistence(legacy = listOf(original))
        val repository = repository(persistence)
        repository.initialize()
        val profileId = repository.activeProfile().id
        val before = repository.document.value
        persistence.failWrites = true
        assertFalse(repository.updateEvent(profileId, "62") { it.copy(name = "Edited") })
        assertEquals(before, repository.document.value)
        assertEquals(before, persistence.stored)
        persistence.failWrites = false
        assertTrue(repository.updateEvent(profileId, "62") { it.copy(name = "Edited") })
        assertEquals(original.copy(name = "Edited"), repository.events().single())
        val writes = persistence.writeCount
        assertFalse(repository.updateEvent("missing", "62") { it.copy(name = "Lost") })
        assertFalse(repository.updateEvent(profileId, "missing") { it.copy(name = "Lost") })
        assertEquals(writes, persistence.writeCount)
    }

    @Test
    fun retiredBindingsMigrateAcrossAllProfilesAndImportedEvents() = runBlocking {
        val old = event("remote").copy(pressAction = SwitchAction(17), holdActions = listOf(SwitchAction(18)))
        val document = SwitchProfileDocument(activeProfileId = "one", profiles = listOf(
            SwitchProfile("one", "One", listOf(old)), SwitchProfile("two", "Two", listOf(old))))
        val persistence = FakePersistence(stored = document)
        val repository = repository(persistence)
        repository.initialize()
        val expected = SwitchAction(19, "com.enaboapps.switchify.remote")
        repository.profiles().forEach { profile ->
            assertEquals(expected, profile.switches.single().pressAction)
            assertEquals(listOf(expected), profile.switches.single().holdActions)
        }
        assertEquals(repository.document.value, persistence.stored)
        val writes = persistence.writeCount
        repository.refresh()
        assertEquals(writes, persistence.writeCount)
        repository.replaceEvents("one", listOf(old))
        assertEquals(expected, repository.events("one").single().pressAction)
    }

    @Test
    fun legacyRemoteEventsMigrateAndFailedWritesPreserveTheSource() = runBlocking {
        val old = event("remote").copy(pressAction = SwitchAction(18))
        val persistence = FakePersistence(legacy = listOf(old))
        val repository = repository(persistence)
        repository.initialize()
        assertEquals(SwitchAction(19, "com.enaboapps.switchify.remote"), repository.events().single().pressAction)
        val document = SwitchProfileDocument(activeProfileId = "one", profiles = listOf(SwitchProfile("one", "One", listOf(old))))
        val failing = FakePersistence(stored = document, failWrites = true)
        repository(failing).initialize()
        assertEquals(document, failing.stored)
    }

    @Test
    fun migratesLegacyMappingsBeforeDeletingLegacyFile() = runBlocking {
        val legacy = listOf(event("1"))
        val persistence = FakePersistence(legacy = legacy)
        val repository = repository(persistence)

        repository.initialize()

        assertEquals("Default", repository.activeProfile().name)
        assertEquals(legacy, repository.events())
        assertEquals(repository.document.value, persistence.stored)
        assertTrue(persistence.legacyDeleted)
    }

    @Test
    fun failedMigrationWriteKeepsLegacyFile() = runBlocking {
        val persistence = FakePersistence(legacy = listOf(event("1")), failWrites = true)
        val repository = repository(persistence)

        repository.initialize()

        assertFalse(persistence.legacyDeleted)
        assertEquals(null, persistence.stored)
    }

    @Test
    fun malformedDocumentIsNotOverwritten() = runBlocking {
        val malformed = SwitchProfileDocument(activeProfileId = "missing", profiles = emptyList())
        val persistence = FakePersistence(stored = malformed)
        val repository = repository(persistence)

        repository.initialize()

        assertEquals("Default", repository.activeProfile().name)
        assertEquals(1, repository.profiles().size)
        assertEquals(malformed, persistence.stored)
        assertEquals(0, persistence.writeCount)
    }

    @Test
    fun profileReadFailureDoesNotWriteDefaultOrDeleteLegacy() = runBlocking {
        val persistence = FakePersistence(
            legacy = listOf(event("legacy")),
            failProfileReads = true
        )
        val repository = repository(persistence)

        repository.initialize()

        assertEquals(0, persistence.writeCount)
        assertFalse(persistence.legacyDeleted)
        assertEquals(null, persistence.stored)
    }

    @Test
    fun legacyReadFailureDoesNotWriteDefault() = runBlocking {
        val persistence = FakePersistence(failLegacyReads = true)
        val repository = repository(persistence)

        repository.initialize()

        assertEquals(0, persistence.writeCount)
        assertEquals(null, persistence.stored)
    }

    @Test
    fun adoptsLegacyMappingsThatArriveAfterEmptyInitialization() = runBlocking {
        val persistence = FakePersistence()
        val repository = repository(persistence)
        repository.initialize()
        val originalProfileId = repository.activeProfile().id
        persistence.legacy = listOf(event("late"))

        repository.initialize()

        assertEquals(originalProfileId, repository.activeProfile().id)
        assertEquals(listOf("late"), repository.events().map { it.code })
        assertEquals(2, persistence.writeCount)
        assertTrue(persistence.legacyDeleted)
    }

    @Test
    fun lateLegacyMappingsDoNotReplacePopulatedDocument() = runBlocking {
        val persistence = FakePersistence()
        val repository = repository(persistence)
        repository.initialize()
        repository.replaceEvents(repository.activeProfile().id, listOf(event("current")))
        persistence.legacy = listOf(event("late"))

        repository.refresh()

        assertEquals(listOf("current"), repository.events().map { it.code })
        assertFalse(persistence.legacyDeleted)
    }

    @Test
    fun createsAndDuplicatesProfilesWithIsolatedMappings() = runBlocking {
        val repository = repository(FakePersistence())
        repository.initialize()
        val activeId = repository.activeProfile().id
        repository.replaceEvents(activeId, listOf(event("shared")))

        val duplicated = repository.duplicate(activeId, "Driving") as SwitchProfileMutationResult.Success
        assertNotEquals(activeId, duplicated.profile.id)
        assertEquals(listOf("shared"), repository.events(duplicated.profile.id).map { it.code })

        repository.replaceEvents(duplicated.profile.id, listOf(event("shared"), event("other")))
        assertEquals(listOf("shared"), repository.events(activeId).map { it.code })
        assertEquals(2, repository.events(duplicated.profile.id).size)
    }

    @Test
    fun enforcesNamesAndDeletionRules() = runBlocking {
        val repository = repository(FakePersistence())
        repository.initialize()
        val activeId = repository.activeProfile().id

        assertTrue(repository.createEmpty(" ") is SwitchProfileMutationResult.InvalidName)
        assertTrue(repository.createEmpty("default") is SwitchProfileMutationResult.InvalidName)
        assertTrue(repository.delete(activeId) is SwitchProfileMutationResult.ActiveProfile)

        val second = repository.createEmpty("Second") as SwitchProfileMutationResult.Success
        assertTrue(repository.rename(second.profile.id, "DEFAULT") is SwitchProfileMutationResult.InvalidName)
        assertTrue(repository.delete(second.profile.id) is SwitchProfileMutationResult.Success)
    }

    @Test
    fun failedSavePreservesDocumentAndActiveProfile() = runBlocking {
        val persistence = FakePersistence()
        val repository = repository(persistence)
        repository.initialize()
        val before = repository.document.value
        persistence.failWrites = true

        val result = repository.createEmpty("Will fail")

        assertTrue(result is SwitchProfileMutationResult.StorageFailure)
        assertEquals(before, repository.document.value)
        assertEquals(before.activeProfileId, repository.activeProfile().id)
    }

    @Test
    fun persistsActiveProfileOnlyAfterCommit() = runBlocking {
        val persistence = FakePersistence()
        val repository = repository(persistence)
        repository.initialize()
        val oldActive = repository.activeProfile().id
        val next = repository.createEmpty("Next") as SwitchProfileMutationResult.Success

        assertEquals(oldActive, repository.activeProfile().id)
        assertTrue(repository.commitActiveProfile(next.profile.id))
        assertEquals(next.profile.id, repository.activeProfile().id)
        assertEquals(next.profile.id, persistence.stored?.activeProfileId)
    }

    @Test
    fun activationPreparationReceivesLatestProfileMappings() = runBlocking {
        val repository = repository(FakePersistence())
        repository.initialize()
        val next = repository.createEmpty("Next") as SwitchProfileMutationResult.Success
        repository.replaceEvents(next.profile.id, listOf(event("edited")))
        var preparedCodes = emptyList<String>()

        val activated = repository.commitActiveProfile(
            next.profile.id,
            prepare = { target, _ ->
                preparedCodes = target.switches.map { it.code }
                true
            },
            rollback = {}
        )

        assertEquals(listOf("edited"), preparedCodes)
        assertEquals(next.profile.id, activated?.id)
    }

    @Test
    fun failedActivationCommitRollsBackPreparedRuntimeState() = runBlocking {
        val persistence = FakePersistence()
        val repository = repository(persistence)
        repository.initialize()
        val previousId = repository.activeProfile().id
        val next = repository.createEmpty("Next") as SwitchProfileMutationResult.Success
        persistence.failWrites = true
        var rolledBackProfileId: String? = null

        val activated = repository.commitActiveProfile(
            next.profile.id,
            prepare = { _, _ -> true },
            rollback = { rolledBackProfileId = it.id }
        )

        assertEquals(null, activated)
        assertEquals(previousId, rolledBackProfileId)
        assertEquals(previousId, repository.activeProfile().id)
    }

    @Test
    fun rejectsDuplicateCodesInsideOneProfile() = runBlocking {
        val repository = repository(FakePersistence())
        repository.initialize()

        assertFalse(
            repository.replaceEvents(
                repository.activeProfile().id,
                listOf(event("same"), event("same"))
            )
        )
    }

    private fun repository(persistence: FakePersistence): SwitchProfileRepository {
        var id = 0
        return SwitchProfileRepository(persistence) { "profile-${++id}" }
    }

    private fun event(code: String) = SwitchEvent(
        name = code,
        code = code,
        pressAction = SwitchAction(SwitchAction.ACTION_SELECT),
        holdActions = emptyList()
    )

    private class FakePersistence(
        var stored: SwitchProfileDocument? = null,
        var legacy: List<SwitchEvent>? = null,
        var failWrites: Boolean = false,
        private val failProfileReads: Boolean = false,
        private val failLegacyReads: Boolean = false
    ) : SwitchProfilePersistence {
        var legacyDeleted = false
        var writeCount = 0
        var beforeWrite: suspend () -> Unit = {}

        override suspend fun readProfiles(): Result<SwitchProfileDocument?> {
            if (failProfileReads) return Result.failure(IllegalStateException("read failed"))
            return Result.success(stored)
        }

        override suspend fun writeProfiles(document: SwitchProfileDocument): Result<Unit> {
            beforeWrite()
            if (failWrites) return Result.failure(IllegalStateException("write failed"))
            writeCount += 1
            stored = document
            return Result.success(Unit)
        }

        override suspend fun readLegacyEvents(): Result<List<SwitchEvent>?> {
            if (failLegacyReads) return Result.failure(IllegalStateException("read failed"))
            return Result.success(legacy)
        }

        override suspend fun deleteLegacyEvents(): Result<Unit> {
            legacyDeleted = true
            return Result.success(Unit)
        }
    }
}
