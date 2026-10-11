package com.enaboapps.switchify.pc.widget

import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.protocol.PcPlatform
import com.enaboapps.switchify.pc.storage.PcSavedPc
import org.junit.Assert.assertEquals
import org.junit.Test

class PcWidgetPcTest {
    private val office = PcSavedPc("pc-1", "Office", PcPlatform.Windows, "ble-1", 3)
    private val studio = PcSavedPc("pc-2", "Studio", PcPlatform.MacOs, "ble-2", 2)
    private val spare = PcSavedPc("pc-3", "Spare", null, "ble-3", 1)

    @Test
    fun listsTheDefaultPcFirstAndKeepsTheRestInOrder() {
        val ordered = PcWidgetPc.order(listOf(office, studio, spare), studio.desktopId)
        assertEquals(listOf(studio, office, spare), ordered.map { it.saved })
        assertEquals(listOf(true, false, false), ordered.map { it.isDefault })
    }

    @Test
    fun keepsTheSavedOrderWithoutADefault() {
        val ordered = PcWidgetPc.order(listOf(office, studio), null)
        assertEquals(listOf(office, studio), ordered.map { it.saved })
    }

    @Test
    fun describesEachPc() {
        val ordered = PcWidgetPc.order(listOf(office, studio, spare), null)
        assertEquals(
            listOf(R.string.pc_widget_windows, R.string.pc_widget_mac, R.string.pc_widget_paired),
            ordered.map { it.detail }
        )
        assertEquals(R.string.pc_widget_default, PcWidgetPc.order(listOf(office), office.desktopId).single().detail)
    }
}
