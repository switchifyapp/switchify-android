package com.enaboapps.switchify.tips

interface TipRegistrationState {
    var enabled: Boolean
    var registrationMayExist: Boolean
    var subscribedAtMillis: Long
    var subscribedVersionCode: Int
}

class TipOperationPendingException(operation: String) :
    Exception("$operation has not finished yet")

interface TipRegistrationClient {
    fun finishPendingDeletion()
    fun subscribe()
    fun unsubscribe()
    fun deleteToken()
    fun deleteInstallation()
}

class TipRegistrationReconciler(
    private val state: TipRegistrationState,
    private val versionCode: Int,
    private val now: () -> Long = System::currentTimeMillis
) {

    fun needsWork(): Boolean {
        return if (state.enabled) !isSubscriptionFresh() else state.registrationMayExist
    }

    fun reconcile(client: TipRegistrationClient) {
        if (state.enabled) subscribe(client) else removeRegistration(client)
    }

    private fun subscribe(client: TipRegistrationClient) {
        if (isSubscriptionFresh()) return
        client.finishPendingDeletion()
        state.registrationMayExist = true
        client.subscribe()
        state.subscribedAtMillis = now()
        state.subscribedVersionCode = versionCode
    }

    private fun removeRegistration(client: TipRegistrationClient) {
        if (!state.registrationMayExist) return
        state.subscribedAtMillis = 0L
        try {
            client.unsubscribe()
        } catch (e: TipOperationPendingException) {
            throw e
        } catch (e: Exception) {
            Unit
        }
        client.deleteToken()
        client.deleteInstallation()
        state.registrationMayExist = false
    }

    fun millisUntilRefresh(): Long? {
        if (!state.enabled || !isSubscriptionFresh()) return null
        return REFRESH_INTERVAL_MILLIS - (now() - state.subscribedAtMillis)
    }

    private fun isSubscriptionFresh(): Boolean {
        val age = now() - state.subscribedAtMillis
        return state.subscribedAtMillis > 0L &&
                state.subscribedVersionCode == versionCode &&
                age in 0 until REFRESH_INTERVAL_MILLIS
    }

    companion object {
        const val REFRESH_INTERVAL_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
