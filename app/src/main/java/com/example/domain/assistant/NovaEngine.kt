package com.example.domain.assistant

import android.app.Activity
import com.example.BuildConfig
import com.example.data.model.ActionConfirmation
import com.example.data.model.ActionType
import com.example.data.model.ChatMessage
import com.example.data.model.ToolExecutionResult
import com.example.data.model.ToolStatus
import com.example.domain.tool.PhoneToolsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class AssistantResponse(
    val replyText: String,
    val toolResult: ToolExecutionResult? = null,
    val pendingConfirmation: ActionConfirmation? = null
)

class NovaEngine(
    private val toolsManager: PhoneToolsManager
) {
    var assistantName: String = "Nova"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun processUserCommand(
        input: String,
        activity: Activity?,
        conversationHistory: List<ChatMessage>
    ): AssistantResponse = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return@withContext AssistantResponse("Yes, Saheb? How can I assist you?")
        }

        // 1. Direct Intent & Multi-step parser (Instant, offline-first, multilingual)
        val localMatch = tryResolveLocalCommand(trimmed, activity)
        if (localMatch != null) {
            return@withContext localMatch
        }

        // 2. If no direct local rule matched, attempt Gemini 3.5 Flash if API Key is available
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }

        if (!apiKey.isNullOrBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val geminiReply = callGeminiAssistant(trimmed, conversationHistory)
                if (geminiReply != null) {
                    // Check if Gemini suggested an action or tool call in its response
                    val parsedFromGemini = tryResolveLocalCommand(geminiReply, activity)
                    if (parsedFromGemini?.toolResult != null || parsedFromGemini?.pendingConfirmation != null) {
                        return@withContext parsedFromGemini
                    }
                    return@withContext AssistantResponse(replyText = geminiReply)
                }
            } catch (_: Exception) {
                // Network or quota fallback
            }
        }

        // 3. Fallback for unrecognized command
        AssistantResponse(
            replyText = "I don't have access to that phone function yet, Saheb.",
            toolResult = ToolExecutionResult(
                toolName = "Unknown Tool",
                status = ToolStatus.UNAVAILABLE,
                briefMessage = "I don't have access to that phone function yet.",
                detail = "Command: '$trimmed'"
            )
        )
    }

    /**
     * Multilingual parser for English, Hindi/Hinglish, Bengali/Banglish commands.
     */
    private fun tryResolveLocalCommand(text: String, activity: Activity?): AssistantResponse? {
        val lower = text.lowercase().trim()

        // Addressing check: prepend Saheb if appropriate
        val prefix = "Saheb"

        // --- POWER OFF / SHUTDOWN / RESTART ---
        // "Turn off my phone", "Phone off koro", "Shutdown my phone", "Restart my phone"
        if (containsAny(lower, "turn off my phone", "turn off phone", "phone off", "shutdown", "shut down", "restart", "reboot") ||
            (containsAny(lower, "phone", "mobile") && containsAny(lower, "off koro", "bondho koro", "band karo", "switch off"))
        ) {
            val result = toolsManager.handlePowerOffOrRestart()
            return AssistantResponse(
                replyText = result.briefMessage,
                toolResult = result
            )
        }

        // --- MULTI-STEP: "Open YouTube and play music" / "YouTube kholo ar gaan chalao" ---
        if (containsAny(lower, "youtube") && (containsAny(lower, "play", "music", "song", "gaan", "gana", "chalao", "bajao"))) {
            val openResult = toolsManager.openAppByNameOrPackage("youtube")
            val mediaResult = toolsManager.controlMedia("play")
            return AssistantResponse(
                replyText = "YouTube is open. Playing music.",
                toolResult = ToolExecutionResult(
                    toolName = "YouTube & Playback",
                    status = ToolStatus.EXECUTED,
                    briefMessage = "YouTube is open. Playing music.",
                    detail = "Executed multi-step: launched YouTube & media key"
                )
            )
        }

        // --- MULTI-STEP / MESSAGING WITH CONFIRMATION (RULE 3 & 4) ---
        // "Rahul ke WhatsApp e message koro ami 10 minute e ashchi"
        // "Send WhatsApp message to Rahul"
        // "Text Mom that I'll be late"
        val whatsappMatch = matchWhatsAppMessage(lower, text)
        if (whatsappMatch != null) {
            val (recipient, message) = whatsappMatch
            return AssistantResponse(
                replyText = "Send this message to $recipient?\n\"$message\"",
                pendingConfirmation = ActionConfirmation(
                    id = java.util.UUID.randomUUID().toString(),
                    title = "Send WhatsApp Message",
                    description = "Send message to $recipient",
                    recipient = recipient,
                    content = message,
                    actionType = ActionType.SEND_WHATSAPP,
                    payload = mapOf("recipient" to recipient, "message" to message)
                )
            )
        }

        // SMS match
        val smsMatch = matchSmsMessage(lower, text)
        if (smsMatch != null) {
            val (recipient, message) = smsMatch
            return AssistantResponse(
                replyText = "Send this SMS to $recipient?\n\"$message\"",
                pendingConfirmation = ActionConfirmation(
                    id = java.util.UUID.randomUUID().toString(),
                    title = "Send SMS",
                    description = "Send text message to $recipient",
                    recipient = recipient,
                    content = message,
                    actionType = ActionType.SEND_SMS,
                    payload = mapOf("recipient" to recipient, "message" to message)
                )
            )
        }

        // Phone call match (RULE 3 & 4: confirm consequential phone call)
        // "Call Mom", "Call Rahul", "Rahul ko call karo", "Ma ke call kor"
        val callMatch = matchCallContact(lower)
        if (callMatch != null) {
            return AssistantResponse(
                replyText = "Call $callMatch now?",
                pendingConfirmation = ActionConfirmation(
                    id = java.util.UUID.randomUUID().toString(),
                    title = "Make Phone Call",
                    description = "Place a call to $callMatch",
                    recipient = callMatch,
                    actionType = ActionType.MAKE_CALL,
                    payload = mapOf("recipient" to callMatch, "phone" to callMatch)
                )
            )
        }

        // --- FLASHLIGHT / TORCH ---
        // "Light on kor", "Flashlight on", "Turn flashlight on", "Flashlight bandh karo", "Torch on"
        if (containsAny(lower, "flashlight", "torch", "light", "ফ্ল্যাশলাইট", "টর্চ")) {
            val isOff = containsAny(lower, "off", "bandh", "bondho", "nibhao", "বন্ধ", "turn off")
            val isOn = containsAny(lower, "on", "chalu", "jalao", "khol", "চালু", "জ্বালাও", "turn on")
            val result = if (isOff) {
                toolsManager.setFlashlight(false)
            } else if (isOn) {
                toolsManager.setFlashlight(true)
            } else {
                toolsManager.toggleFlashlight()
            }
            return AssistantResponse(
                replyText = result.briefMessage,
                toolResult = result
            )
        }

        // --- VOLUME & MUTE / SILENT ---
        // "Phone silent koro", "Put phone on silent", "Silent mode on", "Phone ta silent kore dao", "Mute"
        if (containsAny(lower, "silent", "নীরব", "mute", "unmute")) {
            val unMute = containsAny(lower, "unmute", "silent off", "silent bondho", "normal")
            val result = toolsManager.setSilentMode(!unMute)
            return AssistantResponse(
                replyText = result.briefMessage,
                toolResult = result
            )
        }

        // Volume percentage: "Volume 50%", "Volume set to 80"
        val volumePercent = extractPercentage(lower, "volume", "awaz", "sound")
        if (volumePercent != null) {
            val result = toolsManager.setVolumeLevel(volumePercent)
            return AssistantResponse(
                replyText = result.briefMessage,
                toolResult = result
            )
        }

        // Volume adjust: "Increase volume", "Awaz badhao", "Sound komao", "ভলিউম কমিয়ে দাও"
        if (containsAny(lower, "volume", "sound", "awaz", "শব্দ", "ভলিউম")) {
            val isRaise = containsAny(lower, "up", "increase", "badhao", "barau", "raise", "বেশি", "উঁচু", "বাড়িয়ে", "বাড়া")
            val isLower = containsAny(lower, "down", "decrease", "komao", "kam karo", "কম", "কমাও", "কমিয়ে", "কমা")
            if (isRaise || isLower) {
                val result = toolsManager.adjustVolumeStep(isRaise)
                return AssistantResponse(
                    replyText = result.briefMessage,
                    toolResult = result
                )
            }
        }

        // --- BRIGHTNESS ---
        // "Brightness 50% kore dao", "Change brightness to 70", "Brightness barau"
        val brightnessPercent = extractPercentage(lower, "brightness", "আলো")
        if (brightnessPercent != null) {
            val result = toolsManager.setBrightness(brightnessPercent, activity)
            return AssistantResponse(
                replyText = "Done.",
                toolResult = result
            )
        }
        if (containsAny(lower, "brightness", "আলো")) {
            val result = toolsManager.openDisplaySettings()
            return AssistantResponse(
                replyText = "Done.",
                toolResult = result
            )
        }

        // --- MEDIA CONTROLS ---
        // "Play music", "Pause media", "Skip song", "Next track"
        if (containsAny(lower, "play music", "pause music", "stop music", "next song", "skip song", "previous song", "gana chalao", "gaan thamau")) {
            val action = when {
                containsAny(lower, "next", "skip") -> "next"
                containsAny(lower, "prev", "previous", "back") -> "prev"
                containsAny(lower, "pause", "stop", "thamau", "roko") -> "pause"
                else -> "play"
            }
            val result = toolsManager.controlMedia(action)
            return AssistantResponse(
                replyText = result.briefMessage,
                toolResult = result
            )
        }

        // --- NAVIGATION ---
        // "Start navigation to Kolkata", "Navigate to Airport", "Rasta dekhao"
        if (containsAny(lower, "navigate to", "navigation to", "direction to", "rasta dekhao") ||
            (containsAny(lower, "maps", "map") && containsAny(lower, "to", "for"))
        ) {
            val dest = extractDestination(text)
            if (dest.isNotBlank()) {
                val result = toolsManager.startNavigation(dest)
                return AssistantResponse(
                    replyText = result.briefMessage,
                    toolResult = result
                )
            }
        }

        // --- WI-FI / BLUETOOTH / AIRPLANE / SETTINGS / QUICK SETTINGS ---
        if (containsAny(lower, "quick settings", "notification panel", "notifications", "quick panel")) {
            val result = toolsManager.openQuickSettings()
            return AssistantResponse(
                replyText = "Quick settings opened.",
                toolResult = result
            )
        }
        if (containsAny(lower, "display settings", "স্ক্রিন সেটিংস")) {
            val result = toolsManager.openDisplaySettings()
            return AssistantResponse(
                replyText = "Display settings opened.",
                toolResult = result
            )
        }
        if (containsAny(lower, "wi-fi", "wifi", "ওয়াইফাই")) {
            val result = toolsManager.openWifiSettings()
            return AssistantResponse(
                replyText = "Wi-Fi settings opened.",
                toolResult = result
            )
        }
        if (containsAny(lower, "bluetooth", "ব্লুটুথ")) {
            val result = toolsManager.openBluetoothSettings()
            return AssistantResponse(
                replyText = "Bluetooth settings opened.",
                toolResult = result
            )
        }
        if (containsAny(lower, "airplane", "aeroplane", "flight mode")) {
            val result = toolsManager.openAirplaneModeSettings()
            return AssistantResponse(
                replyText = "Airplane mode settings opened.",
                toolResult = result
            )
        }

        // --- APPLICATION LAUNCH (RULE 2 & 6) ---
        // "Open YouTube", "YT kholo", "YouTube open koro", "YouTube চালু করো", "Launch YouTube"
        // "WhatsApp kholo", "WhatsApp khol", "Instagram open koro", "Open Chrome", "Open Camera"
        val openAppQuery = extractAppNameToOpen(lower)
        if (openAppQuery != null) {
            val result = toolsManager.openAppByNameOrPackage(openAppQuery)
            return AssistantResponse(
                replyText = result.briefMessage,
                toolResult = result
            )
        }

        // Check if query is simply an app name e.g. "YouTube", "WhatsApp", "Instagram"
        val knownDirectApps = listOf("youtube", "yt", "whatsapp", "instagram", "chrome", "gmail", "maps", "camera", "settings")
        if (knownDirectApps.contains(lower)) {
            val result = toolsManager.openAppByNameOrPackage(lower)
            return AssistantResponse(
                replyText = result.briefMessage,
                toolResult = result
            )
        }

        return null
    }

    private fun extractAppNameToOpen(lower: String): String? {
        val patterns = listOf(
            Pattern.compile("^(?:open|launch|start)\\s+([a-zA-Z0-9\\s]+)$"),
            Pattern.compile("^([a-zA-Z0-9\\s]+)\\s+(?:kholo|khol|open koro|chalu koro|চালু করো|খুলুন|খোল)$"),
            Pattern.compile("^(?:kholo|khol|open koro)\\s+([a-zA-Z0-9\\s]+)$")
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(lower)
            if (matcher.find()) {
                val app = matcher.group(1)?.trim()
                if (!app.isNullOrBlank() && app.length > 1 && !isStopWord(app)) {
                    return app
                }
            }
        }
        // Direct checks for common commands:
        if (lower.contains("youtube") || lower.contains("yt") || lower.contains("ইউটিউব")) return "youtube"
        if (lower.contains("whatsapp") || lower.contains("হোয়াটসঅ্যাপ")) return "whatsapp"
        if (lower.contains("instagram") || lower.contains("ইনস্টাগ্রাম") || lower.contains("insta")) return "instagram"
        if (lower.contains("chrome") || lower.contains("ক্রোম")) return "chrome"
        if (lower.contains("gmail") || lower.contains("জিমেলে")) return "gmail"
        if (lower.contains("camera") || lower.contains("ক্যামেরা")) return "camera"
        if (lower.contains("settings") || lower.contains("সেটিংস")) return "settings"
        if (lower.contains("maps") || lower.contains("ম্যাপস")) return "maps"

        return null
    }

    private fun isStopWord(word: String): Boolean {
        return listOf("phone", "mobile", "light", "torch", "volume", "music", "call", "message", "wifi").contains(word)
    }

    private fun matchWhatsAppMessage(lower: String, original: String): Pair<String, String>? {
        // e.g., "Rahul ke WhatsApp e message koro ami 10 minute e ashchi"
        // "Send WhatsApp message to Rahul: I am coming"
        // "Open WhatsApp and message Rahul"
        if (!containsAny(lower, "whatsapp", "হোয়াটসঅ্যাপ")) return null

        // If it's an app opening command without a message instruction, let open-app handle it
        val isOpenCommand = containsAny(lower, "kholo", "khol", "open", "launch", "chalu", "চালু")
        val isMessageCommand = containsAny(lower, "message", "msg", "send", "bhejo", "pathao", "text", "মেসেজ", "পাঠাও")

        if (isOpenCommand && !isMessageCommand) {
            return null
        }

        // Check pattern: "Rahul ke ... message koro <text>"
        val bengaliPattern = Pattern.compile("(?i)([a-zA-Z0-9]+)\\s+ke\\s+whatsapp\\s*(?:e)?\\s*message\\s*koro\\s*(.+)")
        val bMatcher = bengaliPattern.matcher(original)
        if (bMatcher.find()) {
            val recipient = bMatcher.group(1) ?: "Contact"
            val msg = bMatcher.group(2) ?: "Hello"
            return Pair(recipient.trim(), msg.trim())
        }

        // Check English pattern: "send whatsapp message to <name>[:\\s]+<text>"
        val engPattern = Pattern.compile("(?i)(?:send\\s+)?whatsapp\\s+message\\s*(?:to)?\\s+([a-zA-Z0-9]+)[:\\s]+['\"]?(.+?)['\"]?$")
        val eMatcher = engPattern.matcher(original)
        if (eMatcher.find()) {
            val recipient = eMatcher.group(1) ?: "Contact"
            val msg = eMatcher.group(2) ?: "Hello"
            return Pair(recipient.trim(), msg.trim())
        }

        // If "Send WhatsApp message to Rahul" or "WhatsApp message to Rahul" without text
        val nameOnlyPattern = Pattern.compile("(?i)(?:send\\s+whatsapp\\s*(?:message)?\\s*(?:to)?|whatsapp\\s+message\\s+(?:to)?)\\s+([a-zA-Z0-9]+)$")
        val noMatcher = nameOnlyPattern.matcher(original)
        if (noMatcher.find()) {
            val recipient = noMatcher.group(1)?.trim()
            val actionVerbs = listOf("kholo", "khol", "open", "launch", "chalu", "koro", "pathao", "bhejo")
            if (!recipient.isNullOrBlank() && !actionVerbs.contains(recipient.lowercase())) {
                return Pair(recipient, "Hello from Nova Assistant")
            }
        }

        // Generic WhatsApp message prompt: "WhatsApp message pathao", "WhatsApp e message pathao", "Send WhatsApp message"
        if (containsAny(lower, "whatsapp message pathao", "whatsapp e message pathao", "send whatsapp message", "whatsapp message bhejo")) {
            return Pair("Contact", "Hello from Nova")
        }

        return null
    }

    private fun matchSmsMessage(lower: String, original: String): Pair<String, String>? {
        // "Text Mom that I'll be late"
        // "Send SMS to Dad I am reached"
        if (!containsAny(lower, "text", "sms", "message")) return null
        val textPattern = Pattern.compile("(?i)(?:text|sms|message)\\s+([a-zA-Z0-9]+)\\s+(?:that|:)?\\s*(.+)")
        val matcher = textPattern.matcher(original)
        if (matcher.find()) {
            val recipient = matcher.group(1) ?: "Contact"
            val msg = matcher.group(2) ?: "Hello"
            return Pair(recipient.trim(), msg.trim())
        }
        return null
    }

    private fun matchCallContact(lower: String): String? {
        // "Call Mom", "Call Rahul", "Rahul ko call karo", "Ma ke call kor"
        val patterns = listOf(
            Pattern.compile("^(?:call|dial)\\s+([a-zA-Z0-9\\s]+)$"),
            Pattern.compile("^([a-zA-Z0-9\\s]+)\\s+(?:ko call karo|ke call kor|ke phone koro|call koro)$")
        )
        for (p in patterns) {
            val m = p.matcher(lower)
            if (m.find()) {
                val target = m.group(1)?.trim()
                if (!target.isNullOrBlank() && target != "phone" && target != "karo") {
                    return target.replaceFirstChar { it.uppercase() }
                }
            }
        }
        return null
    }

    private fun extractPercentage(lower: String, vararg keywords: String): Int? {
        val hasKeyword = keywords.any { lower.contains(it) }
        if (!hasKeyword) return null
        val pattern = Pattern.compile("(\\d{1,3})\\s*(?:%|percent)?")
        val matcher = pattern.matcher(lower)
        if (matcher.find()) {
            return matcher.group(1)?.toIntOrNull()?.coerceIn(0, 100)
        }
        if (lower.contains("fifty percent") || lower.contains("fifty")) return 50
        if (lower.contains("hundred percent") || lower.contains("full")) return 100
        if (lower.contains("zero percent") || lower.contains("zero")) return 0
        return null
    }

    private fun extractDestination(text: String): String {
        val lower = text.lowercase()
        val index = lower.indexOf(" to ")
        return if (index != -1 && index + 4 < text.length) {
            text.substring(index + 4).trim()
        } else {
            text.replace(Regex("(?i)^(?:start\\s+)?(?:navigation|navigate|direction)\\s*(?:to)?"), "").trim()
        }
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it) }
    }

    /**
     * Calls Gemini 3.5 Flash using direct REST API endpoint with strict system instruction.
     */
    private suspend fun callGeminiAssistant(
        prompt: String,
        history: List<ChatMessage>
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Saheb, please provide a Gemini API Key in the settings or environment to enable generative AI reasoning."
        }
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val systemPrompt = """
            You are $assistantName, a personal AI assistant running inside an Android application.
            Address the user naturally as 'Saheb' when appropriate.
            Understand English, Hindi, Bengali, Hinglish, and Banglish.
            Respond naturally and very briefly (1-2 sentences maximum).
            Do not unnecessarily explain what you are doing.
            If a requested phone feature is not available, clearly tell the user that the capability is not currently available.
            Never pretend an action was completed if not actually done.
            Available phone tools include: Open Apps (YouTube, WhatsApp, Instagram, Chrome, Maps, Camera, Settings), Flashlight, Volume, Silent mode, Brightness, Wi-Fi, Bluetooth, Navigation, Media playback, Phone calls, WhatsApp messaging, SMS.
        """.trimIndent()

        val contentsArray = JSONArray()

        // Include recent history turns
        val recentHistory = history.takeLast(6)
        for (msg in recentHistory) {
            val role = if (msg.isUser) "user" else "model"
            val partObj = JSONObject().put("text", msg.text)
            val contentObj = JSONObject().put("role", role).put("parts", JSONArray().put(partObj))
            contentsArray.put(contentObj)
        }

        // Current user prompt
        val currentContent = JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))
        contentsArray.put(currentContent)

        val requestJson = JSONObject().apply {
            put("contents", contentsArray)
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("maxOutputTokens", 120)
            })
        }

        val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .build()

        val response = httpClient.newCall(request).execute()
        val bodyString = response.body?.string() ?: return@withContext null

        if (response.isSuccessful) {
            val respJson = JSONObject(bodyString)
            val candidates = respJson.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text")
            return@withContext text?.trim()
        } else {
            return@withContext null
        }
    }
}
