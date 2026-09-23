package com.enaboapps.switchify.service.techniques

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AccessTechniqueTest {
    private lateinit var originalTechnique: String
    private var originalTemporaryTechniqueActive = false
    private var originalObserver: AccessTechniqueObserver? = null

    @Before
    fun captureState() {
        originalTechnique = AccessTechnique.getCurrentTechnique()
        originalTemporaryTechniqueActive = AccessTechnique.isTemporaryTechniqueActive()
        originalObserver = AccessTechnique.observer
        AccessTechnique.observer = null
    }

    @After
    fun restoreState() {
        AccessTechnique.observer = null
        AccessTechnique.setCurrentTechnique(originalTechnique)
        if (originalTemporaryTechniqueActive) {
            AccessTechnique.setTemporaryTechnique(originalTechnique)
        }
        AccessTechnique.observer = originalObserver
    }

    @Test
    fun unchangedPersistentTechniqueDoesNotNotifyObserverOnReload() {
        AccessTechnique.setCurrentTechnique(AccessTechnique.Technique.ITEM_SCAN)
        val observedTechniques = mutableListOf<String>()
        AccessTechnique.observer = observer(observedTechniques)

        AccessTechnique.reloadFromPreferences()

        assertEquals(emptyList<String>(), observedTechniques)
    }

    @Test
    fun temporaryTechniqueNotifiesObserverOnReload() {
        AccessTechnique.setCurrentTechnique(AccessTechnique.Technique.POINT_SCAN)
        AccessTechnique.setTemporaryTechnique(AccessTechnique.Technique.ITEM_SCAN)
        val observedTechniques = mutableListOf<String>()
        AccessTechnique.observer = observer(observedTechniques)

        AccessTechnique.reloadFromPreferences()

        assertEquals(listOf(AccessTechnique.Technique.ITEM_SCAN), observedTechniques)
    }

    private fun observer(observedTechniques: MutableList<String>) =
        object : AccessTechniqueObserver {
            override fun onAccessTechniqueChanged(accessTechnique: String) {
                observedTechniques.add(accessTechnique)
            }
        }
}
