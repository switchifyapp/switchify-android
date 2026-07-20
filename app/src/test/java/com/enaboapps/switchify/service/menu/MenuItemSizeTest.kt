package com.enaboapps.switchify.service.menu

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuItemSizeTest {
    @Test
    fun contentProfilesKeepTheirExistingRowHeights() {
        assertEquals(64, MenuSizes.PHONE_COMPACT.rowHeightDp)
        assertEquals(72, MenuSizes.PHONE.rowHeightDp)
        assertEquals(88, MenuSizes.TABLET.rowHeightDp)
        assertEquals(100, MenuSizes.TABLET_LARGE.rowHeightDp)
    }

    @Test
    fun scalingKeepsPaginationCountAndExistingHeightFormula() {
        val scaled = MenuSizes.PHONE.scaledBy(150)

        assertEquals(MenuSizes.PHONE.itemsPerPage, scaled.itemsPerPage)
        assertEquals(96, scaled.rowHeightDp)
    }

    @Test
    fun navigationProfilesKeepTheirExistingCellDimensions() {
        assertEquals(70f, MenuSizes.PHONE_SMALL.width.value, 0f)
        assertEquals(50f, MenuSizes.PHONE_SMALL.height.value, 0f)
        assertEquals(90f, MenuSizes.TABLET_SMALL.width.value, 0f)
        assertEquals(60f, MenuSizes.TABLET_SMALL.height.value, 0f)
    }
}
