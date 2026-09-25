package com.enaboapps.switchify.switches.profiles

import android.content.Context
import android.util.AtomicFile
import com.enaboapps.switchify.switches.SwitchEvent
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStreamWriter

internal interface SwitchProfilePersistence {
    suspend fun readProfiles(): Result<SwitchProfileDocument?>
    suspend fun writeProfiles(document: SwitchProfileDocument): Result<Unit>
    suspend fun readLegacyEvents(): Result<List<SwitchEvent>?>
    suspend fun deleteLegacyEvents(): Result<Unit>
    suspend fun backUpCorruptProfiles(): Result<Unit>
    suspend fun backUpCorruptLegacyEvents(): Result<Unit>
}

internal class CorruptSwitchDataException(cause: Throwable) : Exception(cause)

internal class SwitchProfileLocalPersistence(context: Context) : SwitchProfilePersistence {
    private val applicationContext = context.applicationContext
    private val protectedContext = applicationContext.createDeviceProtectedStorageContext()
    private val profileFile = AtomicFile(File(protectedContext.filesDir, PROFILE_FILE_NAME))
    private val legacyFiles = listOf(
        File(protectedContext.filesDir, LEGACY_FILE_NAME),
        File(applicationContext.filesDir, LEGACY_FILE_NAME)
    ).distinctBy { it.absolutePath }
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    override suspend fun readProfiles(): Result<SwitchProfileDocument?> = withContext(Dispatchers.IO) {
        runCatching {
            if (!profileFile.baseFile.exists()) return@runCatching null
            parseOrCorrupt {
                profileFile.openRead().bufferedReader().use { reader ->
                    checkNotNull(gson.fromJson(reader, SwitchProfileDocument::class.java))
                }
            }.sanitized()
        }
    }

    private fun SwitchProfileDocument.sanitized(): SwitchProfileDocument {
        val safeProfiles: List<SwitchProfile>? = profiles
        val safeActiveProfileId: String? = activeProfileId
        val cleanedProfiles = safeProfiles.orEmpty().filterNotNull().map { profile ->
            val safeSwitches: List<SwitchEvent>? = profile.switches
            profile.copy(
                switches = safeSwitches.orEmpty().filterNotNull().mapNotNull { it.sanitized() }
            )
        }
        check(cleanedProfiles.isNotEmpty())
        return copy(
            activeProfileId = safeActiveProfileId ?: cleanedProfiles.first().id,
            profiles = cleanedProfiles
        )
    }

    override suspend fun writeProfiles(document: SwitchProfileDocument): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val stream = profileFile.startWrite()
                try {
                    OutputStreamWriter(stream).apply {
                        gson.toJson(document, this)
                        flush()
                    }
                    profileFile.finishWrite(stream)
                } catch (error: Throwable) {
                    profileFile.failWrite(stream)
                    throw error
                }
                val verified = profileFile.openRead().bufferedReader().use { reader ->
                    gson.fromJson(reader, SwitchProfileDocument::class.java)
                }
                check(verified == document)
            }
        }

    override suspend fun readLegacyEvents(): Result<List<SwitchEvent>?> = withContext(Dispatchers.IO) {
        runCatching {
            val legacyFile = legacyFiles.firstOrNull { it.exists() } ?: return@runCatching null
            // Parse as a List: a Set would hash each event during parsing, which throws
            // when a legacy entry is missing fields that the data class treats as non-null.
            val type = object : TypeToken<List<SwitchEvent>>() {}.type
            parseOrCorrupt {
                legacyFile.bufferedReader().use { reader ->
                    val events: List<SwitchEvent?> = checkNotNull(gson.fromJson(reader, type))
                    events.filterNotNull().mapNotNull { it.sanitized() }.distinct()
                }
            }
        }
    }

    override suspend fun deleteLegacyEvents(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            legacyFiles.forEach { legacyFile ->
                if (legacyFile.exists()) check(legacyFile.delete())
            }
        }
    }

    override suspend fun backUpCorruptProfiles(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { backUp(profileFile.baseFile) }
    }

    override suspend fun backUpCorruptLegacyEvents(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { legacyFiles.filter { it.exists() }.forEach(::backUp) }
    }

    private fun backUp(file: File) {
        val backup = File(file.parentFile, "${file.name}$CORRUPT_SUFFIX")
        if (backup.exists()) check(backup.delete())
        check(file.renameTo(backup))
    }

    private inline fun <T> parseOrCorrupt(block: () -> T): T = try {
        block()
    } catch (error: JsonParseException) {
        throw CorruptSwitchDataException(error)
    } catch (error: IllegalStateException) {
        throw CorruptSwitchDataException(error)
    }

    private companion object {
        const val PROFILE_FILE_NAME = "switch_profiles.json"
        const val LEGACY_FILE_NAME = "switch_events.json"
        const val CORRUPT_SUFFIX = ".corrupt"
    }
}
