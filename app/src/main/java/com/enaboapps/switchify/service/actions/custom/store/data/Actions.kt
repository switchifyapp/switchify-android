package com.enaboapps.switchify.service.actions.custom.store.data

const val ACTION_OPEN_APP = "Open App"
const val ACTION_COPY_TEXT_TO_CLIPBOARD = "Copy Text to Clipboard"
const val ACTION_CALL_NUMBER = "Call Number"
const val ACTION_OPEN_LINK = "Open Link"
const val ACTION_SEND_TEXT = "Send Text"
const val ACTION_SEND_EMAIL = "Send Email"

val ACTIONS = listOf(
    ACTION_OPEN_APP,
    ACTION_COPY_TEXT_TO_CLIPBOARD,
    ACTION_CALL_NUMBER,
    ACTION_OPEN_LINK,
    ACTION_SEND_TEXT,
    ACTION_SEND_EMAIL
)

fun getActionDescription(action: String): String {
    return when (action) {
        ACTION_OPEN_APP -> "Open an app"
        ACTION_COPY_TEXT_TO_CLIPBOARD -> "Copy text to clipboard"
        ACTION_CALL_NUMBER -> "Call a number"
        ACTION_OPEN_LINK -> "Open a link in a browser"
        ACTION_SEND_TEXT -> "Send a text to someone"
        ACTION_SEND_EMAIL -> "Send an email to someone"
        else -> "Unknown"
    }
}
