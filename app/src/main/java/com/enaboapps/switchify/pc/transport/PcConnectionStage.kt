package com.enaboapps.switchify.pc.transport

enum class PcConnectionStage(val code: String) {
    ProbeConnect("ble_probe_connect"),
    ProbeServices("ble_probe_services"),
    StatusRead("ble_status_read"),
    StatusParse("ble_status_parse"),
    SelectedMatch("ble_selected_match"),
    Resolution("ble_resolution"),
    Connect("ble_connect"),
    Priority("ble_priority"),
    Mtu("ble_mtu"),
    Services("ble_services"),
    Notifications("ble_notifications"),
    NotificationReady("ble_notification_ready")
}

enum class PcConnectionStageOutcome(val code: String) {
    Started("started"),
    Succeeded("succeeded"),
    Failed("failed"),
    TimedOut("timed_out"),
    NotMatched("not_matched")
}

fun interface PcConnectionStageObserver {
    fun onConnectionStage(stage: PcConnectionStage, outcome: PcConnectionStageOutcome)
}
