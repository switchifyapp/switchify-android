package com.enaboapps.switchify.utils

/**
 * Predefined logging events for consistent analytics tracking.
 * All events are organized by feature area for easy discovery and maintenance.
 *
 * Usage:
 * ```
 * Logger.log(LogEvent.AppLaunched)
 * Logger.log(LogEvent.ServiceConnected)
 * ```
 *
 * Available Events:
 * - App Lifecycle: AppLaunched
 * - Service: ServiceConnected, ServiceInterrupted, ServiceUnbound, ServiceDestroyed
 * - Onboarding: OnboardingUserType*, OnboardingCompleted
 * - Switches: SwitchAdded, SwitchUpdated, SwitchRemoved
 * - Access Technique: RadarTrialExpired
 * - Settings: AccessibilitySettingsOpened
 * - IAP: ProCheckedViaSubscription, ProCheckedViaPurchase
 */
sealed class LogEvent(val eventName: String) {

    // App Lifecycle Events
    object AppLaunched : LogEvent("app_launched")

    // Service Events
    object ServiceConnected : LogEvent("service_connected")
    object ServiceInterrupted : LogEvent("service_interrupted")
    object ServiceUnbound : LogEvent("service_unbound")
    object ServiceDestroyed : LogEvent("service_destroyed")

    // Onboarding Events
    object OnboardingUserTypeEndUser : LogEvent("onboarding_user_type_end_user")
    object OnboardingUserTypeSpecialist : LogEvent("onboarding_user_type_specialist")
    object OnboardingUserTypeCarerFamily : LogEvent("onboarding_user_type_carer_family")
    object OnboardingUserTypeOther : LogEvent("onboarding_user_type_other")
    object OnboardingCompleted : LogEvent("onboarding_completed")

    // Switch Events
    object SwitchAdded : LogEvent("switch_added")
    object SwitchUpdated : LogEvent("switch_updated")
    object SwitchRemoved : LogEvent("switch_removed")

    // Access Technique Events
    object RadarTrialExpired : LogEvent("radar_trial_expired")

    // Settings Events
    object AccessibilitySettingsOpened : LogEvent("accessibility_settings_opened")

    // IAP Events
    object ProCheckedViaSubscription : LogEvent("pro_checked_via_subscription")
    object ProCheckedViaPurchase : LogEvent("pro_checked_via_purchase")
}