package com.enaboapps.switchify.pc

enum class PcWindowControlAction(val protocolValue: String) {
    SwitchNext("switchNext"),
    SwitchPrevious("switchPrevious"),
    TaskView("taskView"),
    ShowDesktop("showDesktop"),
    CloseFocused("closeFocused"),
    MinimizeFocused("minimizeFocused"),
    MaximizeFocused("maximizeFocused")
}
