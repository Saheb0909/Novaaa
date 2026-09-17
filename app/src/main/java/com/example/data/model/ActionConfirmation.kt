package com.example.data.model

data class ActionConfirmation(
    val id: String,
    val title: String,
    val description: String,
    val recipient: String? = null,
    val content: String? = null,
    val actionType: ActionType,
    val payload: Map<String, String> = emptyMap()
)

enum class ActionType {
    SEND_WHATSAPP,
    SEND_SMS,
    MAKE_CALL,
    DELETE_ITEM,
    UNINSTALL_APP,
    DEVICE_CONTROL
}
