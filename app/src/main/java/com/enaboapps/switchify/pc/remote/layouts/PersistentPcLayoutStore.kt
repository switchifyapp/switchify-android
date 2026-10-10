package com.enaboapps.switchify.pc.remote.layouts

import com.enaboapps.switchify.pc.storage.PcKeyValueStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PersistentPcLayoutStore(private val storage: PcKeyValueStore) : PcLayoutStore {
    private val _layouts = MutableStateFlow<PcSurfaceLayouts>(emptyMap())
    override val layouts: StateFlow<PcSurfaceLayouts> = _layouts.asStateFlow()

    private val loadLock = Mutex()
    private val writeLock = Mutex()

    @Volatile
    private var loaded = false

    override suspend fun load() {
        if (loaded) return
        loadLock.withLock {
            if (loaded) return
            _layouts.value = try {
                PcLayoutCodec.decode(storage.get(KEY))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyMap()
            }
            loaded = true
        }
    }

    override suspend fun save(surface: PcLayoutSurface, section: String, layout: PcButtonLayout?) {
        require(PcLayoutSections.get(surface, section) != null) { INVALID }
        require(layout == null || PcLayoutSections.isValidSectionLayout(surface, section, layout)) { INVALID }
        val snapshot = layout?.let { PcButtonLayout(it.columns, it.cells.toList()) }
        load()
        writeLock.withLock {
            val next = PcLayoutCodec.withSection(_layouts.value, surface, section, snapshot)
            storage.put(KEY, PcLayoutCodec.encode(next))
            _layouts.value = next
        }
    }

    companion object {
        const val KEY = "switchify.remote.layouts.v2"
        const val DIRECTORY = "switchify_pc_layouts"
        const val FILE_NAME = "layouts.json"
        private const val INVALID = "Invalid section layout"
    }
}
