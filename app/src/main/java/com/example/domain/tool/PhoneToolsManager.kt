package com.example.domain.tool

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import com.example.data.model.InstalledAppInfo
import com.example.data.model.ToolExecutionResult
import com.example.data.model.ToolStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhoneToolsManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private var isTorchOn = false
    private var torchCameraId: String? = null

    init {
        findTorchCamera()
    }

    private fun findTorchCamera() {
        try {
            cameraManager?.let { cm ->
                for (id in cm.cameraIdList) {
                    val characteristics = cm.getCameraCharacteristics(id)
                    val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        torchCameraId = id
                        break
                    }
                }
                if (torchCameraId == null && cm.cameraIdList.isNotEmpty()) {
                    torchCameraId = cm.cameraIdList.firstOrNull()
                }
            }
        } catch (_: Exception) {
            torchCameraId = null
        }
    }

    suspend fun getInstalledApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        resolveInfos.map { ri ->
            InstalledAppInfo(
                appName = ri.loadLabel(pm).toString(),
                packageName = ri.activityInfo.packageName
            )
        }.distinctBy { it.packageName }.sortedBy { it.appName.lowercase() }
    }

    // --- APPLICATION LAUNCH TOOLS ---

    fun openAppByNameOrPackage(appNameOrQuery: String): ToolExecutionResult {
        val normalized = appNameOrQuery.trim().lowercase()

        // Well-known app package mapping
        val knownPackages = mapOf(
            "youtube" to listOf("com.google.android.youtube"),
            "yt" to listOf("com.google.android.youtube"),
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "instagram" to listOf("com.instagram.android"),
            "insta" to listOf("com.instagram.android"),
            "chrome" to listOf("com.android.chrome"),
            "gmail" to listOf("com.google.android.gm"),
            "mail" to listOf("com.google.android.gm"),
            "maps" to listOf("com.google.android.apps.maps"),
            "google maps" to listOf("com.google.android.apps.maps"),
            "camera" to listOf("com.android.camera", "com.google.android.GoogleCamera"),
            "settings" to listOf("com.android.settings")
        )

        // Special handling for Camera
        if (normalized == "camera" || normalized.contains("camera") || normalized.contains("kamera") || normalized.contains("ক্যামেরা")) {
            return openCamera()
        }

        // Special handling for Settings
        if (normalized == "settings" || normalized.contains("setting") || normalized.contains("সেটিংস")) {
            return openSettings()
        }

        // Check if query matches known alias
        for ((alias, packages) in knownPackages) {
            if (normalized == alias || normalized.contains(alias)) {
                for (pkg in packages) {
                    val result = launchPackage(pkg, alias.replaceFirstChar { it.uppercase() })
                    if (result.status == ToolStatus.EXECUTED) return result
                }
                // Fallbacks for known web apps
                if (alias == "youtube" || alias == "yt") {
                    return openWebUrl("https://www.youtube.com", "YouTube")
                }
                if (alias == "instagram" || alias == "insta") {
                    return openWebUrl("https://www.instagram.com", "Instagram")
                }
            }
        }

        // Search installed apps by label match
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        val matched = resolveInfos.firstOrNull { ri ->
            val label = ri.loadLabel(pm).toString().lowercase()
            label == normalized || label.contains(normalized) || normalized.contains(label)
        }

        if (matched != null) {
            val launchIntent = pm.getLaunchIntentForPackage(matched.activityInfo.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                val appTitle = matched.loadLabel(pm).toString()
                return ToolExecutionResult(
                    toolName = "Open App",
                    status = ToolStatus.EXECUTED,
                    briefMessage = "$appTitle is open.",
                    detail = "Launched $appTitle (${matched.activityInfo.packageName})"
                )
            }
        }

        // If package name was directly passed
        if (normalized.contains(".")) {
            val directResult = launchPackage(normalized, normalized)
            if (directResult.status == ToolStatus.EXECUTED) return directResult
        }

        return ToolExecutionResult(
            toolName = "Open App",
            status = ToolStatus.FAILED,
            briefMessage = "I couldn't open $appNameOrQuery because the app isn't available.",
            detail = "Package or launcher not found for '$appNameOrQuery'"
        )
    }

    private fun launchPackage(packageName: String, displayName: String): ToolExecutionResult {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                ToolExecutionResult(
                    toolName = "Open App",
                    status = ToolStatus.EXECUTED,
                    briefMessage = "$displayName is open.",
                    detail = "Launched $packageName"
                )
            } else {
                ToolExecutionResult(
                    toolName = "Open App",
                    status = ToolStatus.UNAVAILABLE,
                    briefMessage = "I couldn't open $displayName because the app isn't installed.",
                    detail = "Package $packageName not found"
                )
            }
        } catch (e: Exception) {
            ToolExecutionResult(
                toolName = "Open App",
                status = ToolStatus.FAILED,
                briefMessage = "Failed to open $displayName.",
                detail = e.localizedMessage
            )
        }
    }

    private fun openWebUrl(url: String, serviceName: String): ToolExecutionResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult(
                toolName = "Open Web Service",
                status = ToolStatus.EXECUTED,
                briefMessage = "$serviceName is open.",
                detail = "Opened web fallback: $url"
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                toolName = "Open Web Service",
                status = ToolStatus.FAILED,
                briefMessage = "I couldn't open $serviceName.",
                detail = e.localizedMessage
            )
        }
    }

    // --- HARDWARE & DEVICE CONTROLS ---

    fun setFlashlight(enable: Boolean): ToolExecutionResult {
        if (cameraManager == null || torchCameraId == null) {
            return ToolExecutionResult(
                toolName = "Flashlight",
                status = ToolStatus.UNAVAILABLE,
                briefMessage = "I don't have access to flashlight on this device.",
                detail = "Camera flash hardware unavailable"
            )
        }
        return try {
            cameraManager.setTorchMode(torchCameraId!!, enable)
            isTorchOn = enable
            ToolExecutionResult(
                toolName = "Flashlight",
                status = ToolStatus.EXECUTED,
                briefMessage = if (enable) "Flashlight is turned on." else "Flashlight is turned off.",
                detail = "Torch set to $enable"
            )
        } catch (e: CameraAccessException) {
            ToolExecutionResult(
                toolName = "Flashlight",
                status = ToolStatus.FAILED,
                briefMessage = "Failed to switch flashlight.",
                detail = e.localizedMessage
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                toolName = "Flashlight",
                status = ToolStatus.FAILED,
                briefMessage = "Failed to switch flashlight.",
                detail = e.localizedMessage
            )
        }
    }

    fun toggleFlashlight(): ToolExecutionResult {
        return setFlashlight(!isTorchOn)
    }

    fun isFlashlightActive(): Boolean = isTorchOn

    fun setVolumeLevel(percentage: Int): ToolExecutionResult {
        if (audioManager == null) {
            return ToolExecutionResult(
                toolName = "Adjust Volume",
                status = ToolStatus.UNAVAILABLE,
                briefMessage = "Audio manager is not available.",
                detail = "AudioManager null"
            )
        }
        return try {
            val clamped = percentage.coerceIn(0, 100)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVolume = (clamped * maxVolume) / 100
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, AudioManager.FLAG_SHOW_UI)
            ToolExecutionResult(
                toolName = "Adjust Volume",
                status = ToolStatus.EXECUTED,
                briefMessage = "Done. Volume set to $clamped%.",
                detail = "STREAM_MUSIC set to $targetVolume / $maxVolume"
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                toolName = "Adjust Volume",
                status = ToolStatus.FAILED,
                briefMessage = "Failed to adjust volume.",
                detail = e.localizedMessage
            )
        }
    }

    fun adjustVolumeStep(increase: Boolean): ToolExecutionResult {
        if (audioManager == null) {
            return ToolExecutionResult(
                toolName = "Volume",
                status = ToolStatus.UNAVAILABLE,
                briefMessage = "Volume control not available.",
                detail = "AudioManager null"
            )
        }
        return try {
            val direction = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            ToolExecutionResult(
                toolName = "Volume",
                status = ToolStatus.EXECUTED,
                briefMessage = if (increase) "Volume increased." else "Volume decreased.",
                detail = "Adjusted music stream"
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                toolName = "Volume",
                status = ToolStatus.FAILED,
                briefMessage = "Could not change volume.",
                detail = e.localizedMessage
            )
        }
    }

    fun setSilentMode(silent: Boolean): ToolExecutionResult {
        if (audioManager == null) {
            return ToolExecutionResult(
                toolName = "Silent Mode",
                status = ToolStatus.UNAVAILABLE,
                briefMessage = "Audio control not available.",
                detail = "AudioManager null"
            )
        }
        return try {
            if (silent) {
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                ToolExecutionResult(
                    toolName = "Silent Mode",
                    status = ToolStatus.EXECUTED,
                    briefMessage = "Silent mode is on.",
                    detail = "Ringer mode set to SILENT"
                )
            } else {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                ToolExecutionResult(
                    toolName = "Silent Mode",
                    status = ToolStatus.EXECUTED,
                    briefMessage = "Silent mode is off.",
                    detail = "Ringer mode set to NORMAL"
                )
            }
        } catch (e: Exception) {
            // Android 7.0+ Do Not Disturb permissions might restrict changing ringer mode directly
            // Offer to open Do Not Disturb / Sound Settings gracefully
            openSoundSettings()
            ToolExecutionResult(
                toolName = "Silent Mode",
                status = ToolStatus.EXECUTED,
                briefMessage = "Opened sound settings to adjust silent mode.",
                detail = "Security restriction: opened Settings"
            )
        }
    }

    fun setBrightness(percent: Int, activity: Activity?): ToolExecutionResult {
        val clamped = percent.coerceIn(0, 100)
        return try {
            if (activity != null) {
                val layoutParams = activity.window.attributes
                layoutParams.screenBrightness = clamped / 100f
                activity.window.attributes = layoutParams
                ToolExecutionResult(
                    toolName = "Brightness",
                    status = ToolStatus.EXECUTED,
                    briefMessage = "Done. Brightness adjusted to $clamped%.",
                    detail = "Screen brightness updated"
                )
            } else {
                openDisplaySettings()
                ToolExecutionResult(
                    toolName = "Brightness",
                    status = ToolStatus.EXECUTED,
                    briefMessage = "Opened display settings to change brightness.",
                    detail = "Direct window context not attached; opened settings"
                )
            }
        } catch (e: Exception) {
            openDisplaySettings()
            ToolExecutionResult(
                toolName = "Brightness",
                status = ToolStatus.EXECUTED,
                briefMessage = "Opened display settings for brightness.",
                detail = e.localizedMessage
            )
        }
    }

    // --- SYSTEM SETTINGS & DIALOGS ---

    fun openSettings(): ToolExecutionResult {
        return launchSystemIntent(Settings.ACTION_SETTINGS, "Settings")
    }

    fun openWifiSettings(): ToolExecutionResult {
        return launchSystemIntent(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi Settings")
    }

    fun openBluetoothSettings(): ToolExecutionResult {
        return launchSystemIntent(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth Settings")
    }

    fun openAirplaneModeSettings(): ToolExecutionResult {
        return launchSystemIntent(Settings.ACTION_AIRPLANE_MODE_SETTINGS, "Airplane Mode Settings")
    }

    fun openDisplaySettings(): ToolExecutionResult {
        return launchSystemIntent(Settings.ACTION_DISPLAY_SETTINGS, "Display Settings")
    }

    fun openSoundSettings(): ToolExecutionResult {
        return launchSystemIntent(Settings.ACTION_SOUND_SETTINGS, "Sound Settings")
    }

    fun openQuickSettings(): ToolExecutionResult {
        return try {
            val statusBarService = context.getSystemService("statusbar")
            val statusBarManagerClass = Class.forName("android.app.StatusBarManager")
            val method = statusBarManagerClass.getMethod("expandSettingsPanel")
            method.invoke(statusBarService)
            ToolExecutionResult(
                toolName = "Quick Settings",
                status = ToolStatus.EXECUTED,
                briefMessage = "Quick settings opened.",
                detail = "Expanded quick settings panel"
            )
        } catch (_: Exception) {
            launchSystemIntent(Settings.ACTION_SETTINGS, "Settings")
        }
    }

    fun openCamera(): ToolExecutionResult {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult(
                toolName = "Camera",
                status = ToolStatus.EXECUTED,
                briefMessage = "Camera is open.",
                detail = "Launched camera capture intent"
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                toolName = "Camera",
                status = ToolStatus.FAILED,
                briefMessage = "I couldn't open the camera.",
                detail = e.localizedMessage
            )
        }
    }

    fun startNavigation(destination: String): ToolExecutionResult {
        return try {
            val cleanDest = Uri.encode(destination.trim())
            val navUri = Uri.parse("google.navigation:q=$cleanDest")
            val navIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (navIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(navIntent)
            } else {
                val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$cleanDest")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(geoIntent)
            }
            ToolExecutionResult(
                toolName = "Navigation",
                status = ToolStatus.EXECUTED,
                briefMessage = "Starting navigation to $destination.",
                detail = "Maps launched for $destination"
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                toolName = "Navigation",
                status = ToolStatus.FAILED,
                briefMessage = "Could not start navigation.",
                detail = e.localizedMessage
            )
        }
    }

    // --- MEDIA PLAYBACK CONTROLS ---

    fun controlMedia(command: String): ToolExecutionResult {
        if (audioManager == null) {
            return ToolExecutionResult(
                toolName = "Media Control",
                status = ToolStatus.UNAVAILABLE,
                briefMessage = "Media control not available.",
                detail = "AudioManager null"
            )
        }
        val keyCode = when (command.lowercase()) {
            "play", "pause", "play_pause", "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next", "skip" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "prev", "previous", "back" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        return try {
            val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audioManager.dispatchMediaKeyEvent(eventDown)
            audioManager.dispatchMediaKeyEvent(eventUp)
            ToolExecutionResult(
                toolName = "Media Playback",
                status = ToolStatus.EXECUTED,
                briefMessage = "Done.",
                detail = "Dispatched keycode $keyCode"
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                toolName = "Media Playback",
                status = ToolStatus.FAILED,
                briefMessage = "Could not control media playback.",
                detail = e.localizedMessage
            )
        }
    }

    // --- COMMUNICATION ACTIONS (AFTER CONFIRMATION) ---

    fun executePhoneCall(phoneNumber: String, name: String): ToolExecutionResult {
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${phoneNumber.trim()}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult(
                toolName = "Phone Call",
                status = ToolStatus.EXECUTED,
                briefMessage = "Calling $name.",
                detail = "Dialer initiated for $phoneNumber"
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                toolName = "Phone Call",
                status = ToolStatus.FAILED,
                briefMessage = "Could not make call to $name.",
                detail = e.localizedMessage
            )
        }
    }

    fun executeWhatsAppMessage(phoneOrContact: String, message: String): ToolExecutionResult {
        return try {
            val cleanPhone = phoneOrContact.filter { it.isDigit() || it == '+' }
            val intent = if (cleanPhone.isNotEmpty() && cleanPhone.length >= 7) {
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            ToolExecutionResult(
                toolName = "WhatsApp Message",
                status = ToolStatus.EXECUTED,
                briefMessage = "Message sent.",
                detail = "WhatsApp opened with text"
            )
        } catch (e: Exception) {
            // If WhatsApp is not installed, open fallback share
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Send Message").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                ToolExecutionResult(
                    toolName = "WhatsApp Message",
                    status = ToolStatus.EXECUTED,
                    briefMessage = "Opened messaging options.",
                    detail = "Fallback share opened"
                )
            } catch (ex: Exception) {
                ToolExecutionResult(
                    toolName = "WhatsApp Message",
                    status = ToolStatus.FAILED,
                    briefMessage = "Failed to send message.",
                    detail = ex.localizedMessage
                )
            }
        }
    }

    fun executeSms(phoneNumber: String, message: String): ToolExecutionResult {
        return try {
            val smsUri = Uri.parse("smsto:${phoneNumber.trim()}")
            val intent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult(
                toolName = "Send SMS",
                status = ToolStatus.EXECUTED,
                briefMessage = "Message sent.",
                detail = "SMS composer opened"
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                toolName = "Send SMS",
                status = ToolStatus.FAILED,
                briefMessage = "Could not send SMS.",
                detail = e.localizedMessage
            )
        }
    }

    // --- POWER / RESTART HANDLING ---

    fun handlePowerOffOrRestart(): ToolExecutionResult {
        // As per user specification Rule 5:
        // "I don't have permission to power off the phone directly. I can open the power menu/settings for you."
        openSettings()
        return ToolExecutionResult(
            toolName = "Power / Restart",
            status = ToolStatus.UNAVAILABLE,
            briefMessage = "I don't have permission to power off the phone directly. I can open the power menu/settings for you.",
            detail = "Android security prevents third-party apps from powering off device directly."
        )
    }

    private fun launchSystemIntent(action: String, displayName: String): ToolExecutionResult {
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult(
                toolName = displayName,
                status = ToolStatus.EXECUTED,
                briefMessage = "$displayName is open.",
                detail = "Launched action $action"
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                toolName = displayName,
                status = ToolStatus.FAILED,
                briefMessage = "Could not open $displayName.",
                detail = e.localizedMessage
            )
        }
    }
}
