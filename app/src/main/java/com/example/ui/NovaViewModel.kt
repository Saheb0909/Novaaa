package com.example.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ActionConfirmation
import com.example.data.model.ActionType
import com.example.data.model.ChatMessage
import com.example.data.model.InstalledAppInfo
import com.example.data.model.ToolExecutionResult
import com.example.data.model.ToolStatus
import com.example.domain.assistant.AssistantResponse
import com.example.domain.assistant.NovaEngine
import com.example.domain.assistant.VoiceAssistantHelper
import com.example.domain.tool.PhoneToolsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AssistantStatus {
    IDLE,
    LISTENING,
    THINKING,
    EXECUTING,
    SPEAKING
}

class NovaViewModel(application: Application) : AndroidViewModel(application) {

    val toolsManager = PhoneToolsManager(application.applicationContext)
    val novaEngine = NovaEngine(toolsManager)
    val voiceHelper = VoiceAssistantHelper(application.applicationContext)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _assistantStatus = MutableStateFlow(AssistantStatus.IDLE)
    val assistantStatus: StateFlow<AssistantStatus> = _assistantStatus.asStateFlow()

    private val _activeConfirmation = MutableStateFlow<ActionConfirmation?>(null)
    val activeConfirmation: StateFlow<ActionConfirmation?> = _activeConfirmation.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn: StateFlow<Boolean> = _isFlashlightOn.asStateFlow()

    private val _ttsEnabled = MutableStateFlow(true)
    val ttsEnabled: StateFlow<Boolean> = _ttsEnabled.asStateFlow()

    init {
        // Initial greeting as per Nova Assistant personality
        val welcomeMsg = ChatMessage(
            text = "Namaste Saheb. I am Nova, your personal phone assistant. Tell me what to open or control.",
            isUser = false
        )
        _messages.value = listOf(welcomeMsg)

        loadInstalledApps()

        // Sync voice helper speaking state
        viewModelScope.launch {
            voiceHelper.isSpeaking.collect { speaking ->
                if (speaking) {
                    _assistantStatus.value = AssistantStatus.SPEAKING
                } else if (_assistantStatus.value == AssistantStatus.SPEAKING) {
                    _assistantStatus.value = AssistantStatus.IDLE
                }
            }
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = toolsManager.getInstalledApps()
            _installedApps.value = apps
        }
    }

    fun sendCommand(text: String, activity: Activity?) {
        val input = text.trim()
        if (input.isEmpty()) return

        // 1. Add user message
        val userMsg = ChatMessage(text = input, isUser = true)
        _messages.value = _messages.value + userMsg

        _assistantStatus.value = AssistantStatus.THINKING

        viewModelScope.launch {
            val response: AssistantResponse = novaEngine.processUserCommand(
                input = input,
                activity = activity,
                conversationHistory = _messages.value
            )

            _isFlashlightOn.value = toolsManager.isFlashlightActive()

            if (response.pendingConfirmation != null) {
                _activeConfirmation.value = response.pendingConfirmation
                val assistantMsg = ChatMessage(
                    text = response.replyText,
                    isUser = false,
                    pendingConfirmation = response.pendingConfirmation
                )
                _messages.value = _messages.value + assistantMsg
                _assistantStatus.value = AssistantStatus.IDLE

                if (_ttsEnabled.value) {
                    voiceHelper.speak(response.replyText)
                }
            } else {
                val assistantMsg = ChatMessage(
                    text = response.replyText,
                    isUser = false,
                    toolResult = response.toolResult
                )
                _messages.value = _messages.value + assistantMsg
                _assistantStatus.value = AssistantStatus.IDLE

                if (_ttsEnabled.value) {
                    voiceHelper.speak(response.replyText)
                }
            }
        }
    }

    fun startVoiceInput(onPermissionNeeded: () -> Unit) {
        _assistantStatus.value = AssistantStatus.LISTENING
        voiceHelper.startListening(
            onResult = { recognizedText ->
                _assistantStatus.value = AssistantStatus.IDLE
                sendCommand(recognizedText, null)
            },
            onError = { errorMsg ->
                _assistantStatus.value = AssistantStatus.IDLE
                if (errorMsg.contains("permission", ignoreCase = true)) {
                    onPermissionNeeded()
                }
            }
        )
    }

    fun stopVoiceInput() {
        voiceHelper.stopListening()
        _assistantStatus.value = AssistantStatus.IDLE
    }

    fun toggleVoiceInput(hasPermission: Boolean, requestPermission: () -> Unit) {
        if (!hasPermission) {
            requestPermission()
            return
        }
        if (_assistantStatus.value == AssistantStatus.LISTENING) {
            stopVoiceInput()
        } else {
            startVoiceInput(requestPermission)
        }
    }

    fun confirmConsequentialAction(confirmation: ActionConfirmation) {
        _activeConfirmation.value = null
        _assistantStatus.value = AssistantStatus.EXECUTING

        viewModelScope.launch {
            val result = when (confirmation.actionType) {
                ActionType.SEND_WHATSAPP -> {
                    val recipient = confirmation.payload["recipient"] ?: "Contact"
                    val message = confirmation.payload["message"] ?: ""
                    toolsManager.executeWhatsAppMessage(recipient, message)
                }
                ActionType.SEND_SMS -> {
                    val recipient = confirmation.payload["recipient"] ?: "Contact"
                    val message = confirmation.payload["message"] ?: ""
                    toolsManager.executeSms(recipient, message)
                }
                ActionType.MAKE_CALL -> {
                    val phone = confirmation.payload["phone"] ?: confirmation.recipient ?: ""
                    val name = confirmation.recipient ?: "Contact"
                    toolsManager.executePhoneCall(phone, name)
                }
                else -> {
                    ToolExecutionResult(
                        toolName = confirmation.title,
                        status = ToolStatus.EXECUTED,
                        briefMessage = "Action completed."
                    )
                }
            }

            val assistantMsg = ChatMessage(
                text = result.briefMessage,
                isUser = false,
                toolResult = result
            )
            _messages.value = _messages.value + assistantMsg
            _assistantStatus.value = AssistantStatus.IDLE

            if (_ttsEnabled.value) {
                voiceHelper.speak(result.briefMessage)
            }
        }
    }

    fun cancelConsequentialAction(confirmation: ActionConfirmation) {
        _activeConfirmation.value = null
        val msg = ChatMessage(
            text = "Cancelled, Saheb.",
            isUser = false
        )
        _messages.value = _messages.value + msg
    }

    fun toggleFlashlight() {
        val result = toolsManager.toggleFlashlight()
        _isFlashlightOn.value = toolsManager.isFlashlightActive()
        val msg = ChatMessage(text = result.briefMessage, isUser = false, toolResult = result)
        _messages.value = _messages.value + msg
        if (_ttsEnabled.value) voiceHelper.speak(result.briefMessage)
    }

    fun toggleSilent() {
        val result = toolsManager.setSilentMode(true)
        val msg = ChatMessage(text = result.briefMessage, isUser = false, toolResult = result)
        _messages.value = _messages.value + msg
        if (_ttsEnabled.value) voiceHelper.speak(result.briefMessage)
    }

    fun toggleTts() {
        _ttsEnabled.value = !_ttsEnabled.value
        if (!_ttsEnabled.value) {
            voiceHelper.stopSpeaking()
        }
    }

    fun clearChat() {
        _messages.value = listOf(
            ChatMessage(
                text = "History cleared. Ready for your commands, Saheb.",
                isUser = false
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        voiceHelper.destroy()
    }
}
