package com.enaboapps.switchify.service.actions.custom.store.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlin.collections.get

data class ActionExtra(
    @SerializedName("app_package") val appPackage: String = "",
    @SerializedName("app_name") val appName: String = "",
    @SerializedName("text_to_copy") val textToCopy: String = "",
    @SerializedName("number_to_call") val numberToCall: String = "",
    @SerializedName("link_url") val linkUrl: String = "",
    @SerializedName("number_to_send") val numberToSend: String = "",
    @SerializedName("message") val message: String = "",
    @SerializedName("email_address") val emailAddress: String = ""
) {
    companion object {
        fun fromJson(json: String): ActionExtra =
            Gson().fromJson(json, ActionExtra::class.java)

        fun fromFirestore(document: Map<String, Any>): ActionExtra {
            val data = document["extra"] as? Map<*, *> ?: return ActionExtra()
            return ActionExtra(
                appName = data["appName"] as? String ?: "",
                appPackage = data["appPackage"] as? String ?: "",
                textToCopy = data["textToCopy"] as? String ?: "",
                numberToCall = data["numberToCall"] as? String ?: "",
                linkUrl = data["linkUrl"] as? String ?: "",
                numberToSend = data["numberToSend"] as? String ?: "",
                message = data["message"] as? String ?: "",
                emailAddress = data["emailAddress"] as? String ?: ""
            )
        }
    }

    fun toJson(): String = Gson().toJson(this)
}
