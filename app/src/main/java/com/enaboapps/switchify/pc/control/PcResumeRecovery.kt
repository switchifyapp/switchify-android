package com.enaboapps.switchify.pc.control

import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.storage.PcSavedPc

sealed class PcResumeRecovery {
    data class RetrySaved(val pc: PcSavedPc) : PcResumeRecovery()
    data object Rescan : PcResumeRecovery()

    companion object {
        fun blocksAutoConnect(state: PcConnectionState): Boolean =
            state is PcConnectionState.PermissionDenied || state is PcConnectionState.LocationOff

        fun pendingRetry(state: PcConnectionState): PcSavedPc? = when (state) {
            is PcConnectionState.PermissionDenied -> state.retry
            is PcConnectionState.LocationOff -> state.retry
            else -> null
        }

        fun after(state: PcConnectionState, permissionGranted: Boolean, locationOff: Boolean): PcResumeRecovery? {
            val resolved = when (state) {
                is PcConnectionState.PermissionDenied -> permissionGranted
                is PcConnectionState.LocationOff -> !locationOff
                else -> false
            }
            if (!resolved) return null
            return pendingRetry(state)?.let(::RetrySaved) ?: Rescan
        }
    }
}
