package com.enaboapps.switchify.pc

internal const val PC_AUTH_RETRY_ATTEMPTS = 5

internal suspend fun <T> retryPcAuthFailure(
    attempts: Int = PC_AUTH_RETRY_ATTEMPTS,
    block: suspend () -> T,
    isAuthFailure: (T) -> Boolean
): T {
    require(attempts > 0)
    var result = block()
    var attempt = 1
    while (attempt < attempts && isAuthFailure(result)) {
        result = block()
        attempt++
    }
    return result
}
