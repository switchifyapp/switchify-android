package com.enaboapps.switchify.service.llm

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.window.MessageSeverity
import com.enaboapps.switchify.service.window.ServiceMessageHUD

/**
 * Inserts a chosen reply suggestion into the focused editable text field.
 */
object ReplyDrafterTextInserter {

    fun insert(service: SwitchifyAccessibilityService, text: String) {
        val focused = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) {
            setNodeText(focused, text)
            return
        }
        val root = service.rootInActiveWindow
        if (root == null || !insertIntoFirstEditable(root, text)) {
            ServiceMessageHUD.instance.showMessage(
                R.string.reply_drafter_no_text_field,
                ServiceMessageHUD.MessageType.DISAPPEARING,
                ServiceMessageHUD.Time.MEDIUM,
                MessageSeverity.Warning
            )
        }
    }

    private fun insertIntoFirstEditable(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.isEditable) {
            setNodeText(node, text)
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (insertIntoFirstEditable(child, text)) return true
        }
        return false
    }

    private fun setNodeText(node: AccessibilityNodeInfo, text: String) {
        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }
}
