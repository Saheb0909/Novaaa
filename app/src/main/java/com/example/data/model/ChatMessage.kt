package com.example.data.model

enum class ToolStatus {
    NONE,
    EXECUTED,
    FAILED,
    UNAVAILABLE
}

data class ToolExecutionResult(
    val toolName: String,
    val status: ToolStatus,
    val briefMessage: String,
    val detail: String? = null
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val toolResult: ToolExecutionResult? = null,
    val pendingConfirmation: ActionConfirmation? = null
)
