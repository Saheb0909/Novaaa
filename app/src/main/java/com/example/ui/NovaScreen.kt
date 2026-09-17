package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VoiceOverOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.ActionConfirmation
import com.example.data.model.ChatMessage
import com.example.data.model.InstalledAppInfo
import com.example.data.model.ToolExecutionResult
import com.example.data.model.ToolStatus
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.NovaAmber
import com.example.ui.theme.NovaCoral
import com.example.ui.theme.NovaCyan
import com.example.ui.theme.NovaCyanGlow
import com.example.ui.theme.NovaEmerald
import com.example.ui.theme.NovaViolet
import com.example.ui.theme.NovaVioletGlow
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovaScreen(
    viewModel: NovaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val messages by viewModel.messages.collectAsState()
    val assistantStatus by viewModel.assistantStatus.collectAsState()
    val activeConfirmation by viewModel.activeConfirmation.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val isFlashlightOn by viewModel.isFlashlightOn.collectAsState()
    val ttsEnabled by viewModel.ttsEnabled.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showAppsSheet by remember { mutableStateOf(false) }
    var appSearchQuery by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Audio record permission launcher
    var hasRecordAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRecordAudioPermission = isGranted
        if (isGranted) {
            viewModel.startVoiceInput {}
        }
    }

    // Auto-scroll when messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val quickCommands = remember {
        listOf(
            "Open YouTube",
            "WhatsApp kholo",
            "Light on kor",
            "Phone silent koro",
            "Brightness 50% kore dao",
            "Call Rahul",
            "Turn off my phone",
            "Camera kholo",
            "Open Settings"
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground),
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(NovaCyan, NovaViolet))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "N",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        Column {
                            Text(
                                text = "Nova",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimaryDark,
                                    letterSpacing = 0.5.sp
                                )
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (assistantStatus) {
                                                AssistantStatus.LISTENING -> NovaCoral
                                                AssistantStatus.THINKING -> NovaAmber
                                                AssistantStatus.SPEAKING -> NovaCyan
                                                AssistantStatus.EXECUTING -> NovaEmerald
                                                AssistantStatus.IDLE -> NovaEmerald
                                            }
                                        )
                                )
                                Text(
                                    text = when (assistantStatus) {
                                        AssistantStatus.LISTENING -> "Listening to Saheb..."
                                        AssistantStatus.THINKING -> "Thinking..."
                                        AssistantStatus.SPEAKING -> "Speaking..."
                                        AssistantStatus.EXECUTING -> "Executing tool..."
                                        AssistantStatus.IDLE -> "Online • Saheb's Assistant"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextSecondaryDark,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleTts() },
                        modifier = Modifier.testTag("tts_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (ttsEnabled) Icons.Default.RecordVoiceOver else Icons.Default.VoiceOverOff,
                            contentDescription = if (ttsEnabled) "Mute Voice" else "Enable Voice",
                            tint = if (ttsEnabled) NovaCyan else TextSecondaryDark
                        )
                    }
                    IconButton(
                        onClick = { showAppsSheet = true },
                        modifier = Modifier.testTag("apps_list_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = "Installed Apps",
                            tint = TextSecondaryDark
                        )
                    }
                    IconButton(
                        onClick = { viewModel.clearChat() },
                        modifier = Modifier.testTag("clear_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear History",
                            tint = TextSecondaryDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // Interactive Hero Orb & Status Header
            NovaAssistantOrbHeader(
                status = assistantStatus,
                onOrbClick = {
                    viewModel.toggleVoiceInput(
                        hasPermission = hasRecordAudioPermission,
                        requestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    )
                }
            )

            // Quick Prompt Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickCommands) { cmd ->
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { viewModel.sendCommand(cmd, activity) }
                            .testTag("quick_cmd_${cmd.replace(" ", "_")}"),
                        color = DarkSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                    ) {
                        Text(
                            text = cmd,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = NovaCyan,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            // Chat & Action History
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatMessageItem(
                        message = message,
                        onConfirmAction = { confirmation ->
                            viewModel.confirmConsequentialAction(confirmation)
                        },
                        onCancelAction = { confirmation ->
                            viewModel.cancelConsequentialAction(confirmation)
                        }
                    )
                }
            }

            // Quick Device Action Bar (Harmless shortcuts)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Flashlight quick toggle
                    QuickActionPill(
                        icon = if (isFlashlightOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                        label = if (isFlashlightOn) "Torch ON" else "Torch",
                        isActive = isFlashlightOn,
                        onClick = { viewModel.toggleFlashlight() },
                        testTag = "quick_torch_toggle"
                    )
                    // Mute / Silent toggle
                    QuickActionPill(
                        icon = Icons.AutoMirrored.Filled.VolumeMute,
                        label = "Silent",
                        isActive = false,
                        onClick = { viewModel.toggleSilent() },
                        testTag = "quick_silent_toggle"
                    )
                    // Volume Step
                    QuickActionPill(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        label = "+Vol",
                        isActive = false,
                        onClick = { viewModel.toolsManager.adjustVolumeStep(true) },
                        testTag = "quick_volume_up"
                    )
                }
            }

            // Bottom Command Input Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(28.dp),
                color = DarkSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Voice Mic Button
                    val isListening = assistantStatus == AssistantStatus.LISTENING
                    IconButton(
                        onClick = {
                            viewModel.toggleVoiceInput(
                                hasPermission = hasRecordAudioPermission,
                                requestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                            )
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isListening) NovaCoral else NovaCyanGlow
                            )
                            .testTag("voice_mic_button")
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isListening) "Stop Listening" else "Voice Command",
                            tint = if (isListening) Color.White else NovaCyan
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                text = if (isListening) "Listening to you, Saheb..." else "Command Nova (Hindi, Bengali, English)...",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondaryDark)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("command_input_field"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            cursorColor = NovaCyan,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendCommand(inputText, activity)
                                    inputText = ""
                                }
                            }
                        )
                    )

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendCommand(inputText, activity)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank(),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (inputText.isNotBlank()) NovaCyan else Color.Transparent)
                            .testTag("send_command_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Execute Command",
                            tint = if (inputText.isNotBlank()) Color.Black else TextSecondaryDark
                        )
                    }
                }
            }
        }
    }

    // Modal Sheet: Installed Apps on Phone
    if (showAppsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAppsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = DarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Installed Phone Applications",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                )
                Text(
                    text = "Nova can identify and launch any of these apps via voice or text.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondaryDark),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                TextField(
                    value = appSearchQuery,
                    onValueChange = { appSearchQuery = it },
                    placeholder = { Text("Filter applications...", color = TextSecondaryDark) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = NovaCyan) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceElevated,
                        unfocusedContainerColor = DarkSurfaceElevated,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark
                    )
                )

                val filteredApps = remember(installedApps, appSearchQuery) {
                    if (appSearchQuery.isBlank()) installedApps else {
                        installedApps.filter { it.appName.contains(appSearchQuery, ignoreCase = true) }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredApps) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceElevated)
                                .clickable {
                                    showAppsSheet = false
                                    viewModel.sendCommand("Open ${app.appName}", activity)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimaryDark
                                    )
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondaryDark),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Launch",
                                color = NovaCyan,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NovaAssistantOrbHeader(
    status: AssistantStatus,
    onOrbClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val waveRingRadius by infiniteTransition.animateFloat(
        initialValue = 28f,
        targetValue = 54f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_ring"
    )

    val coreGlowColor by animateColorAsState(
        targetValue = when (status) {
            AssistantStatus.LISTENING -> NovaCoral
            AssistantStatus.THINKING -> NovaAmber
            AssistantStatus.SPEAKING -> NovaCyan
            AssistantStatus.EXECUTING -> NovaEmerald
            AssistantStatus.IDLE -> NovaViolet
        },
        label = "glow_color"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clickable { onOrbClick() }
                .testTag("nova_orb_trigger"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerOffset = Offset(size.width / 2f, size.height / 2f)

                // Outer reactive soundwave ring
                if (status != AssistantStatus.IDLE) {
                    drawCircle(
                        color = coreGlowColor.copy(alpha = 0.25f),
                        radius = waveRingRadius * density,
                        center = centerOffset,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                }

                // Ambient glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(coreGlowColor.copy(alpha = 0.4f), Color.Transparent),
                        center = centerOffset,
                        radius = 48.dp.toPx()
                    ),
                    radius = 48.dp.toPx(),
                    center = centerOffset
                )

                // Central AI Sphere
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(NovaCyan, coreGlowColor),
                        start = Offset(centerOffset.x - 24.dp.toPx(), centerOffset.y - 24.dp.toPx()),
                        end = Offset(centerOffset.x + 24.dp.toPx(), centerOffset.y + 24.dp.toPx())
                    ),
                    radius = 26.dp.toPx() * (if (status == AssistantStatus.LISTENING) pulseScale else 1.0f),
                    center = centerOffset
                )

                // Specular Core reflection
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = 6.dp.toPx(),
                    center = Offset(centerOffset.x - 8.dp.toPx(), centerOffset.y - 8.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    onConfirmAction: (ActionConfirmation) -> Unit,
    onCancelAction: (ActionConfirmation) -> Unit
) {
    if (message.isUser) {
        // User message bubble
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                    .testTag("user_message_${message.id}"),
                color = NovaViolet,
                tonalElevation = 2.dp
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Normal
                    )
                )
            }
        }
    } else {
        // Nova Assistant Message & Tool Result
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(NovaCyan),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "N",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Nova",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = NovaCyan,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                    .testTag("assistant_message_${message.id}"),
                color = DarkSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                tonalElevation = 2.dp
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimaryDark,
                        lineHeight = 20.sp
                    )
                )
            }

            // Consequential Action Confirmation Card (RULE 4)
            if (message.pendingConfirmation != null) {
                ConsequentialConfirmationCard(
                    confirmation = message.pendingConfirmation,
                    onConfirm = { onConfirmAction(message.pendingConfirmation) },
                    onCancel = { onCancelAction(message.pendingConfirmation) }
                )
            }

            // Tool Execution Status Card (RULE 1, 8, 10)
            if (message.toolResult != null) {
                ToolResultCard(toolResult = message.toolResult)
            }
        }
    }
}

@Composable
fun ConsequentialConfirmationCard(
    confirmation: ActionConfirmation,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("confirmation_card_${confirmation.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, NovaAmber)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Confirmation Required",
                    tint = NovaAmber,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Confirmation Required",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = NovaAmber,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Text(
                text = confirmation.description,
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondaryDark)
            )

            if (!confirmation.content.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = DarkSurfaceElevated
                ) {
                    Text(
                        text = "\"${confirmation.content}\"",
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = NovaCyan,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("action_cancel_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TextSecondaryDark)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = TextSecondaryDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cancel", color = TextSecondaryDark)
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("action_confirm_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NovaEmerald,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Confirm",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Confirm", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ToolResultCard(toolResult: ToolExecutionResult) {
    val statusColor = when (toolResult.status) {
        ToolStatus.EXECUTED -> NovaEmerald
        ToolStatus.FAILED -> NovaCoral
        ToolStatus.UNAVAILABLE -> NovaAmber
        ToolStatus.NONE -> TextSecondaryDark
    }

    val statusIcon = when (toolResult.status) {
        ToolStatus.EXECUTED -> Icons.Default.CheckCircle
        ToolStatus.FAILED -> Icons.Default.Warning
        ToolStatus.UNAVAILABLE -> Icons.Default.QuestionMark
        ToolStatus.NONE -> Icons.Default.CheckCircle
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .testTag("tool_result_${toolResult.toolName.replace(" ", "_")}"),
        color = DarkSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = toolResult.status.name,
                tint = statusColor,
                modifier = Modifier.size(16.dp)
            )
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = toolResult.toolName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                    )
                    Text(
                        text = "• ${toolResult.status.name}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = statusColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        )
                    )
                }
                if (!toolResult.detail.isNullOrBlank()) {
                    Text(
                        text = toolResult.detail,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondaryDark,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun QuickActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag(testTag),
        color = if (isActive) NovaCyan else DarkSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isActive) NovaCyan else DarkBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Color.Black else TextSecondaryDark,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (isActive) Color.Black else TextPrimaryDark,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}
