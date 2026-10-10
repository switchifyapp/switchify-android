package com.enaboapps.switchify.pc.remote

import androidx.annotation.StringRes
import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.connection.PcConnectionFailure
import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.connection.PcProfileStatus
import com.enaboapps.switchify.pc.protocol.PcCommandTypes

enum class PcDisconnectedChooseAction(@StringRes val labelRes: Int) {
    FindPc(R.string.pc_remote_find_pc),
    ChooseAnother(R.string.pc_remote_choose_another_pc)
}

data class PcDisconnectedPresentation(
    val busy: Boolean,
    val failed: Boolean,
    @StringRes val titleRes: Int,
    val message: PcText,
    val retry: Boolean,
    val choose: PcDisconnectedChooseAction?
)

data class PcDevicePresentation(val name: PcText, @StringRes val statusRes: Int)

data class PcProfilePresentation(@StringRes val titleRes: Int, @StringRes val bodyRes: Int)

object PcRemotePresentation {
    fun disconnected(connection: PcConnectionState): PcDisconnectedPresentation {
        val saved = connection.savedPcs.orEmpty()
        val busy = connection is PcConnectionState.Connecting ||
            connection is PcConnectionState.Pairing ||
            connection is PcConnectionState.Reconnecting
        val message = when (connection) {
            is PcConnectionState.Connecting -> PcText.Res(R.string.pc_remote_connecting_to, listOf(connection.desktop.displayName))
            is PcConnectionState.Reconnecting -> PcText.Res(
                R.string.pc_remote_reconnecting_to,
                listOf(connection.desktop.displayName, connection.attempt)
            )
            is PcConnectionState.Pairing -> PcText.Res(R.string.pc_remote_waiting_pairing, listOf(connection.desktop.displayName))
            is PcConnectionState.Failed -> PcText.Res(failureMessage(connection.failure))
            is PcConnectionState.PermissionDenied -> PcText.Res(R.string.pc_remote_permission_required)
            is PcConnectionState.BluetoothOff -> PcText.Res(R.string.pc_remote_bluetooth_off)
            is PcConnectionState.LocationOff -> PcText.Res(R.string.pc_location_off_body)
            is PcConnectionState.Unsupported -> PcText.Res(R.string.pc_remote_bluetooth_unsupported)
            else -> PcText.Res(if (saved.isEmpty()) R.string.pc_remote_pair_first else R.string.pc_remote_preparing_preferred)
        }
        val failed = connection is PcConnectionState.Failed
        return PcDisconnectedPresentation(
            busy = busy,
            failed = failed,
            titleRes = when {
                busy -> R.string.pc_remote_title_connecting
                failed -> R.string.pc_remote_title_could_not_connect
                saved.isEmpty() -> R.string.pc_remote_title_no_saved
                else -> R.string.pc_remote_title_connect
            },
            message = message,
            retry = !busy && saved.isNotEmpty(),
            choose = when {
                busy -> null
                saved.isEmpty() -> PcDisconnectedChooseAction.FindPc
                else -> PcDisconnectedChooseAction.ChooseAnother
            }
        )
    }

    fun device(connection: PcConnectionState): PcDevicePresentation = when (connection) {
        is PcConnectionState.Connected -> PcDevicePresentation(PcText.Literal(connection.desktop.displayName), R.string.pc_status_connected)
        is PcConnectionState.Connecting -> PcDevicePresentation(PcText.Literal(connection.desktop.displayName), R.string.pc_status_connecting)
        is PcConnectionState.Reconnecting -> PcDevicePresentation(PcText.Literal(connection.desktop.displayName), R.string.pc_remote_status_reconnecting)
        is PcConnectionState.Pairing -> PcDevicePresentation(PcText.Literal(connection.desktop.displayName), R.string.pc_remote_status_pairing)
        else -> PcDevicePresentation(PcText.Res(R.string.pc_remote_choose_pc), R.string.pc_remote_status_not_connected)
    }

    fun profile(status: PcProfileStatus): PcProfilePresentation = when (status) {
        PcProfileStatus.Recovering -> PcProfilePresentation(R.string.pc_remote_profile_restoring_title, R.string.pc_remote_profile_restoring_body)
        PcProfileStatus.Unavailable -> PcProfilePresentation(R.string.pc_remote_profile_unavailable_title, R.string.pc_remote_profile_unavailable_body)
        PcProfileStatus.Ready -> PcProfilePresentation(R.string.pc_remote_profile_restored, R.string.pc_remote_profile_restored)
    }

    @StringRes
    fun profileAnnouncement(status: PcProfileStatus?, previous: PcProfileStatus?): Int? = when {
        status == PcProfileStatus.Recovering -> R.string.pc_remote_profile_restoring_announcement
        status == PcProfileStatus.Unavailable -> R.string.pc_remote_profile_unavailable_announcement
        status == PcProfileStatus.Ready && previous == PcProfileStatus.Recovering -> R.string.pc_remote_profile_restored_announcement
        else -> null
    }

    @StringRes
    fun repeatStopLabel(repeat: String?): Int =
        if (repeat == PcCommandTypes.KEYBOARD_KEY) {
            R.string.pc_repeat_stop_repeating
        } else {
            R.string.pc_repeat_stop_movement
        }

    @StringRes
    fun failureMessage(failure: PcConnectionFailure): Int = when (failure) {
        PcConnectionFailure.CouldNotConnect -> R.string.pc_failure_could_not_connect
        PcConnectionFailure.NotFoundNearby -> R.string.pc_failure_not_found_nearby
        PcConnectionFailure.SavedAccessLoadFailed -> R.string.pc_failure_saved_access_load_failed
        PcConnectionFailure.SavedAccessUnavailable -> R.string.pc_failure_saved_access_unavailable
        PcConnectionFailure.SavedAccessInvalid -> R.string.pc_failure_saved_access_invalid
        PcConnectionFailure.AccessRevoked -> R.string.pc_failure_access_revoked
        PcConnectionFailure.PairingRejected -> R.string.pc_failure_pairing_rejected
        PcConnectionFailure.PairingExpired -> R.string.pc_failure_pairing_expired
        PcConnectionFailure.ConnectionLost -> R.string.pc_failure_connection_lost
        PcConnectionFailure.DiscoveryFailed -> R.string.pc_failure_discovery_failed
        PcConnectionFailure.UnpairFailed -> R.string.pc_failure_unpair_failed
    }
}
