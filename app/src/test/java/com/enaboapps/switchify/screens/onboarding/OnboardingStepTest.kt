package com.enaboapps.switchify.screens.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingStepTest {
    @Test
    fun resolvesKnownStep() {
        assertEquals(OnboardingStep.PRACTICE, resolveOnboardingStep("PRACTICE"))
    }

    @Test
    fun unknownStepDefaultsToSwitchSetup() {
        assertEquals(OnboardingStep.SWITCH_SETUP, resolveOnboardingStep("unknown"))
    }
}
