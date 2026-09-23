package com.enaboapps.switchify.switches.profiles

import com.enaboapps.switchify.switches.SwitchEvent
import com.google.gson.annotations.SerializedName

internal data class SwitchProfile(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("switches") val switches: List<SwitchEvent>
)

internal data class SwitchProfileDocument(
    @SerializedName("version") val version: Int = CURRENT_VERSION,
    @SerializedName("active_profile_id") val activeProfileId: String,
    @SerializedName("profiles") val profiles: List<SwitchProfile>
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

internal sealed interface SwitchProfileMutationResult {
    data class Success(val profile: SwitchProfile) : SwitchProfileMutationResult
    data class InvalidName(val reason: String) : SwitchProfileMutationResult
    data object NotFound : SwitchProfileMutationResult
    data object ActiveProfile : SwitchProfileMutationResult
    data object LastProfile : SwitchProfileMutationResult
    data object StorageFailure : SwitchProfileMutationResult
}

internal data class SwitchProfileValidationResult(
    val isValid: Boolean,
    val missingActionIds: Set<Int> = emptySet(),
    val unsupportedActionIds: Set<Int> = emptySet()
)

internal enum class SwitchProfileConfirmationMode {
    INPUT_ACTION,
    MENU
}

internal sealed interface SwitchProfileActivationState {
    data object Idle : SwitchProfileActivationState
    data class Verifying(
        val profile: SwitchProfile,
        val expiresAtMillis: Long,
        val confirmationMode: SwitchProfileConfirmationMode = SwitchProfileConfirmationMode.INPUT_ACTION
    ) :
        SwitchProfileActivationState
    data class Failed(val profileId: String, val reason: String) : SwitchProfileActivationState
    data class Activated(val profile: SwitchProfile) : SwitchProfileActivationState
}
