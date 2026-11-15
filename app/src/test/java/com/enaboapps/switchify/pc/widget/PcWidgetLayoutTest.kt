package com.enaboapps.switchify.pc.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class PcWidgetLayoutTest {
    @Test
    fun showsTheFullListAtTwoByTwoAndLarger() {
        assertEquals(PcWidgetLayout.AllPcs, PcWidgetLayout.forSize(250f, 176f))
        assertEquals(PcWidgetLayout.AllPcs, PcWidgetLayout.forSize(110f, 110f))
    }

    @Test
    fun showsTwoButtonsWhenWideButShort() {
        assertEquals(PcWidgetLayout.TwoPcs, PcWidgetLayout.forSize(250f, 76f))
    }

    @Test
    fun showsOneButtonWhenSmall() {
        assertEquals(PcWidgetLayout.OnePc, PcWidgetLayout.forSize(76f, 76f))
        assertEquals(PcWidgetLayout.OnePc, PcWidgetLayout.forSize(76f, 250f))
    }
}
