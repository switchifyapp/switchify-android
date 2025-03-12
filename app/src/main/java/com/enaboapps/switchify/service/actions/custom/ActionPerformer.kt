package com.enaboapps.switchify.service.actions.custom

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.enaboapps.switchify.service.actions.custom.store.ActionStore
import com.enaboapps.switchify.service.actions.custom.store.data.ACTION_CALL_NUMBER
import com.enaboapps.switchify.service.actions.custom.store.data.ACTION_COPY_TEXT_TO_CLIPBOARD
import com.enaboapps.switchify.service.actions.custom.store.data.ACTION_OPEN_APP
import com.enaboapps.switchify.service.actions.custom.store.data.ACTION_OPEN_LINK
import com.enaboapps.switchify.service.actions.custom.store.data.ACTION_SEND_EMAIL
import com.enaboapps.switchify.service.actions.custom.store.data.ACTION_SEND_TEXT
import com.enaboapps.switchify.service.actions.custom.store.data.ActionExtra
import com.enaboapps.switchify.utils.AppLauncher

class ActionPerformer(
    private val context: Context
) {
    private val actionStore = ActionStore(context)

    /**
     * Performs an action from the action store.
     *
     * @param actionId The ID of the action to perform.
     */
    fun performActionFromStore(actionId: String) {
        val action = actionStore.getAction(actionId)
        executeAction(action?.action ?: "", action?.extra)
    }

    /**
     * Performs an action from the action store.
     *
     * @param action The action to perform.
     * @param extra The extra data for the action.
     */
    fun test(action: String, extra: ActionExtra?) {
        executeAction(action, extra)
    }

    /**
     * Performs an action.
     *
     * @param action The action to perform.
     * @param extra The extra data for the action.
     */
    private fun executeAction(action: String, extra: ActionExtra?) {
        when (action) {
            ACTION_OPEN_APP -> openApp(extra?.appPackage ?: "") // Open app
            ACTION_COPY_TEXT_TO_CLIPBOARD -> copyTextToClipboard(
                extra?.textToCopy ?: ""
            ) // Copy text to clipboard
            ACTION_CALL_NUMBER -> callNumber(extra?.numberToCall ?: "") // Call a number
            ACTION_OPEN_LINK -> openLink(extra?.linkUrl ?: "") // Open a link
            ACTION_SEND_TEXT -> sendText(
                extra?.numberToSend ?: "",
                extra?.message ?: ""
            ) // Send a text
            ACTION_SEND_EMAIL -> sendEmail(extra?.emailAddress ?: "") // Send an email
        }
    }

    /**
     * Opens an app by package name.
     *
     * @param appPackage The package name of the app to open.
     */
    private fun openApp(appPackage: String) {
        val appLauncher = AppLauncher(context)
        appLauncher.launchAppByPackageName(appPackage)
    }

    /**
     * Copies text to the clipboard.
     *
     * @param textToCopy The text to copy.
     */
    private fun copyTextToClipboard(textToCopy: String) {
        val clipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("text", textToCopy)
        clipboardManager.setPrimaryClip(clipData)
    }

    /**
     * Calls a number.
     *
     * @param numberToCall The number to call.
     */
    private fun callNumber(numberToCall: String) {
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:$numberToCall")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.applicationContext.startActivity(intent)
    }

    /**
     * Opens a link.
     *
     * @param linkUrl The URL of the link to open.
     */
    private fun openLink(linkUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(linkUrl)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.applicationContext.startActivity(intent)
    }

    /**
     * Sends a text message.
     *
     * @param numberToSend The number to send the text to.
     * @param message The message to send.
     */
    private fun sendText(numberToSend: String, message: String) {
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.data = Uri.parse("smsto:$numberToSend")
        intent.putExtra("sms_body", message)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.applicationContext.startActivity(intent)
    }

    /**
     * Sends an email.
     *
     * @param emailAddress The email address to send the email to.
     */
    private fun sendEmail(emailAddress: String) {
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.data = Uri.parse("mailto:$emailAddress")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.applicationContext.startActivity(intent)
    }
}