package com.enaboapps.switchify.screens.pc

import com.enaboapps.switchify.pc.connection.PcDiagnosticEvent
import com.enaboapps.switchify.pc.connection.PcDiagnosticLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class PcDiagnosticsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun awaitResult(viewModel: PcDiagnosticsViewModel, output: () -> OutputStream?): Boolean {
        val result = java.util.concurrent.LinkedBlockingQueue<Boolean>()
        viewModel.exportTo(output) { result.put(it) }
        return result.poll(5, java.util.concurrent.TimeUnit.SECONDS)!!
    }

    @Test
    fun exportWritesTheLogAndReportsSuccess() {
        val log = PcDiagnosticLog().apply { add(PcDiagnosticEvent.Connected) }
        val viewModel = PcDiagnosticsViewModel(log)
        val bytes = ByteArrayOutputStream()
        assertTrue(awaitResult(viewModel) { bytes })
        assertEquals(log.export(), bytes.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun exportReportsFailureWhenTheFileCannotBeOpenedOrWritten() {
        val viewModel = PcDiagnosticsViewModel(PcDiagnosticLog().apply { add(PcDiagnosticEvent.Connected) })
        assertEquals(false, awaitResult(viewModel) { null })
        assertEquals(false, awaitResult(viewModel) {
            object : OutputStream() {
                override fun write(b: Int) = throw IOException("full")
            }
        })
    }
}
