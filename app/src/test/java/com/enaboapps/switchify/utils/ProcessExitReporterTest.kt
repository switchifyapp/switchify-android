package com.enaboapps.switchify.utils

import android.app.ApplicationExitInfo
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessExitReporterTest {
    @Test
    fun apiBelowThirtyPerformsNoActionableFiltering() {
        assertFalse(
            ProcessExitReporter.isActionableReason(
                ApplicationExitInfo.REASON_ANR,
                Build.VERSION_CODES.Q
            )
        )
    }

    @Test
    fun apiThirtyAndAboveFiltersActionableReasons() {
        val sdk = Build.VERSION_CODES.R

        assertTrue(ProcessExitReporter.isActionableReason(ApplicationExitInfo.REASON_ANR, sdk))
        assertTrue(ProcessExitReporter.isActionableReason(ApplicationExitInfo.REASON_CRASH_NATIVE, sdk))
        assertTrue(ProcessExitReporter.isActionableReason(ApplicationExitInfo.REASON_LOW_MEMORY, sdk))
        assertTrue(ProcessExitReporter.isActionableReason(ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE, sdk))
        assertTrue(ProcessExitReporter.isActionableReason(ApplicationExitInfo.REASON_INITIALIZATION_FAILURE, sdk))
        assertTrue(ProcessExitReporter.isActionableReason(ApplicationExitInfo.REASON_SIGNALED, sdk))
    }

    @Test
    fun apiThirtyAndAboveIgnoresNonActionableReasons() {
        val sdk = Build.VERSION_CODES.R

        assertFalse(ProcessExitReporter.isActionableReason(ApplicationExitInfo.REASON_USER_REQUESTED, sdk))
        assertFalse(ProcessExitReporter.isActionableReason(ApplicationExitInfo.REASON_PACKAGE_UPDATED, sdk))
        assertFalse(ProcessExitReporter.isActionableReason(ApplicationExitInfo.REASON_EXIT_SELF, sdk))
    }

    @Test
    fun reasonNamesAreStable() {
        val sdk = Build.VERSION_CODES.R

        assertEquals("ANR", ProcessExitReporter.reasonName(ApplicationExitInfo.REASON_ANR, sdk))
        assertEquals("CRASH_NATIVE", ProcessExitReporter.reasonName(ApplicationExitInfo.REASON_CRASH_NATIVE, sdk))
        assertEquals("USER_REQUESTED", ProcessExitReporter.reasonName(ApplicationExitInfo.REASON_USER_REQUESTED, sdk))
        assertEquals("UNSUPPORTED", ProcessExitReporter.reasonName(ApplicationExitInfo.REASON_ANR, Build.VERSION_CODES.Q))
    }
}
