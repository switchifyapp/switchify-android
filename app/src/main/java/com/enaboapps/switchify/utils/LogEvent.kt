package com.enaboapps.switchify.utils

sealed class LogEvent(
    val eventName: String,
    val level: String = "info",
    val dataset: String = "app",
    val tags: List<String> = emptyList()
) {
    object AppLaunched : LogEvent("app_launched", dataset = "app", tags = listOf("lifecycle"))

    object ServiceConnected : LogEvent("service_connected", dataset = "service", tags = listOf("lifecycle"))
    object ServiceInterrupted : LogEvent("service_interrupted", level = "warn", dataset = "service", tags = listOf("lifecycle"))
    object ServiceUnbound : LogEvent("service_unbound", dataset = "service", tags = listOf("lifecycle"))
    object ServiceDestroyed : LogEvent("service_destroyed", dataset = "service", tags = listOf("lifecycle"))

    object TrialStarted : LogEvent("trial_started", dataset = "service", tags = listOf("trial"))
    object TrialWarningShown : LogEvent("trial_warning_shown", level = "warn", dataset = "service", tags = listOf("trial"))
    object TrialExpired : LogEvent("trial_expired", level = "warn", dataset = "service", tags = listOf("trial"))
    object TrialStopped : LogEvent("trial_stopped", dataset = "service", tags = listOf("trial"))
    object TrialBlocked : LogEvent("trial_blocked", level = "warn", dataset = "service", tags = listOf("trial"))

    object OnboardingUserTypeEndUser : LogEvent("onboarding_user_type_end_user", dataset = "onboarding")
    object OnboardingUserTypeSpecialist : LogEvent("onboarding_user_type_specialist", dataset = "onboarding")
    object OnboardingUserTypeCarerFamily : LogEvent("onboarding_user_type_carer_family", dataset = "onboarding")
    object OnboardingUserTypeOther : LogEvent("onboarding_user_type_other", dataset = "onboarding")
    object OnboardingCompleted : LogEvent("onboarding_completed", dataset = "onboarding")

    object SwitchAdded : LogEvent("switch_added", dataset = "input", tags = listOf("switches"))
    object SwitchUpdated : LogEvent("switch_updated", dataset = "input", tags = listOf("switches"))
    object SwitchRemoved : LogEvent("switch_removed", dataset = "input", tags = listOf("switches"))

    object RadarTrialExpired : LogEvent("radar_trial_expired", dataset = "service")

    object AccessibilitySettingsOpened : LogEvent("accessibility_settings_opened", dataset = "ui", tags = listOf("settings"))

    object ProCheckResult : LogEvent("pro_check_result", dataset = "iap", tags = listOf("entitlement"))

    object ReviewRequested : LogEvent("review_requested", dataset = "ui", tags = listOf("review"))
    object ReviewLaunched : LogEvent("review_launched", dataset = "ui", tags = listOf("review"))
    object ReviewCompleted : LogEvent("review_completed", dataset = "ui", tags = listOf("review"))

    object StatsScreenOpened : LogEvent("stats_screen_opened", dataset = "stats", tags = listOf("ui"))
    object StatsTimeRangeChanged : LogEvent("stats_time_range_changed", dataset = "stats", tags = listOf("ui"))
    object Milestone100SwitchPresses : LogEvent("milestone_100_switch_presses", dataset = "stats", tags = listOf("milestone"))
    object Milestone1000SwitchPresses : LogEvent("milestone_1000_switch_presses", dataset = "stats", tags = listOf("milestone"))
    object StatsFlushStarted : LogEvent("stats_flush_started", dataset = "stats", tags = listOf("flush"))
    object StatsFlushSkipped : LogEvent("stats_flush_skipped", level = "warn", dataset = "stats", tags = listOf("flush"))
    object StatsFlushSucceeded : LogEvent("stats_flush_succeeded", dataset = "stats", tags = listOf("flush"))
    object StatsFlushFailed : LogEvent("stats_flush_failed", level = "error", dataset = "stats", tags = listOf("flush", "failure"))
    object StatsEventDropped : LogEvent("stats_event_dropped", level = "warn", dataset = "stats", tags = listOf("queue", "drop"))

    object ServiceCommandHandled : LogEvent("service_command_handled", dataset = "service", tags = listOf("command"))
    object ServiceCommandFailed : LogEvent("service_command_failed", level = "error", dataset = "service", tags = listOf("command", "failure"))

    object GestureDispatchStarted : LogEvent("gesture_dispatch_started", dataset = "input", tags = listOf("gesture", "dispatch"))
    object GestureDispatchCompleted : LogEvent("gesture_dispatch_completed", dataset = "input", tags = listOf("gesture", "dispatch"))
    object GestureDispatchCancelled : LogEvent("gesture_dispatch_cancelled", level = "warn", dataset = "input", tags = listOf("gesture", "dispatch"))
    object GestureDispatchFailed : LogEvent("gesture_dispatch_failed", level = "error", dataset = "input", tags = listOf("gesture", "dispatch", "failure"))

    object CameraStateEvaluated : LogEvent("camera_state_evaluated", dataset = "camera", tags = listOf("lifecycle"))
    object CameraStartAttempt : LogEvent("camera_start_attempt", dataset = "camera", tags = listOf("lifecycle"))
    object CameraStartFailed : LogEvent("camera_start_failed", level = "error", dataset = "camera", tags = listOf("lifecycle", "failure"))
    object CameraStop : LogEvent("camera_stop", dataset = "camera", tags = listOf("lifecycle"))
    object CameraPermissionMissing : LogEvent("camera_permission_missing", level = "error", dataset = "camera", tags = listOf("permission", "failure"))
    object CameraBindFailed : LogEvent("camera_bind_failed", level = "error", dataset = "camera", tags = listOf("bind", "failure"))
}
