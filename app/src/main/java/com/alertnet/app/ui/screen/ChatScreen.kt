package com.alertnet.app.ui.screen

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alertnet.app.media.PlayerState
import com.alertnet.app.media.RecorderState
import com.alertnet.app.model.MessageType
import com.alertnet.app.model.LocationSharePayload
import com.alertnet.app.ui.components.LocationShareBottomSheet
import com.alertnet.app.ui.components.MessageBubble
import com.alertnet.app.ui.components.formatVoiceDuration
import com.alertnet.app.ui.theme.*
import com.alertnet.app.ui.viewmodel.ChatViewModel
import com.alertnet.app.ui.viewmodel.LocationShareViewModel
import com.alertnet.app.ui.viewmodel.SendingState

/**
 * Chat screen with message bubbles, image/voice attachments, and delivery status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    peerId: String,
    peerName: String,
    viewModel: ChatViewModel,
    locationShareViewModel: LocationShareViewModel? = null,
    onBack: () -> Unit,
    onViewOnMap: ((Double, Double) -> Unit)? = null
) {
    var messageText by remember { mutableStateOf("") }
    var showLocationSheet by remember { mutableStateOf(false) }
    val messages by viewModel.messages.collectAsState()
    val sendingState by viewModel.sendingState.collectAsState()
    val listState = rememberLazyListState()

    // Voice recording state
    val recorderState by viewModel.voiceRecorderState.collectAsState()
    val voiceAmplitude by viewModel.voiceAmplitude.collectAsState()
    val voiceDuration by viewModel.voiceRecordingDuration.collectAsState()

    // Voice playback state
    val playerState by viewModel.voicePlayerState.collectAsState()
    val playingMessageId by viewModel.playingMessageId.collectAsState()
    val playbackProgress by viewModel.playbackProgress.collectAsState()

    // Transfer progress
    val activeTransfers by viewModel.activeTransfers.collectAsState()

    // ─── Permission launcher for audio recording ─────────────────
    var hasAudioPermission by remember { mutableStateOf(false) }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            viewModel.startVoiceRecording()
        }
    }

    // Image picker
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendImage(it) }
    }

    // File picker (generic)
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendFile(it) }
    }

    // Initialize conversation
    LaunchedEffect(peerId) {
        viewModel.openConversation(peerId)
    }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        containerColor = MeshNavy,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Peer avatar
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(MeshBlue, MeshBlueBright)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = peerName.take(2).uppercase(),
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = peerName,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = peerId.take(12) + "...",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.onBackPressed()
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MeshNavyLight
                )
            )
        },
        bottomBar = {
            Surface(
                color = MeshNavyLight,
                tonalElevation = 4.dp
            ) {
                AnimatedContent(
                    targetState = recorderState,
                    transitionSpec = {
                        fadeIn() + slideInVertically() togetherWith
                                fadeOut() + slideOutVertically()
                    },
                    label = "inputBarTransition"
                ) { state ->
                    when (state) {
                        RecorderState.RECORDING -> {
                            // Voice recording bar
                            VoiceRecordingBar(
                                durationMs = voiceDuration,
                                amplitude = voiceAmplitude,
                                onCancel = { viewModel.cancelVoiceRecording() },
                                onSend = { viewModel.stopVoiceRecordingAndSend() }
                            )
                        }
                        RecorderState.IDLE -> {
                            // Normal input bar
                            NormalInputBar(
                                messageText = messageText,
                                onMessageChange = { messageText = it },
                                sendingState = sendingState,
                                onSendText = {
                                    if (messageText.isNotBlank()) {
                                        viewModel.sendTextMessage(messageText.trim())
                                        messageText = ""
                                    }
                                },
                                onPickImage = { imagePicker.launch("image/*") },
                                onStartRecording = {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onShareLocation = { showLocationSheet = true }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        // Message list
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = MeshBlue.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Start a conversation",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Send text, images, or voice messages",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(
                    items = messages,
                    key = { it.id }
                ) { message ->
                    val isSentByMe = message.senderId == viewModel.deviceId
                    val isThisPlaying = playingMessageId == message.id &&
                            playerState == PlayerState.PLAYING
                    val isThisPaused = playingMessageId == message.id &&
                            playerState == PlayerState.PAUSED
                    val voiceProg = if (playingMessageId == message.id) playbackProgress else 0f
                    val transferProg = activeTransfers.values.find { it.messageId == message.id }

                     MessageBubble(
                        message = message,
                        decryptedText = viewModel.decryptPayload(message),
                        isSentByMe = isSentByMe,
                        mediaFilePath = if (message.type == MessageType.IMAGE ||
                            message.type == MessageType.VOICE
                        ) {
                            viewModel.getMediaFilePath(message)
                        } else null,
                        transferProgress = transferProg,
                        isPlayingVoice = isThisPlaying,
                        isVoicePaused = isThisPaused,
                        voiceProgress = voiceProg,
                        onPlayVoice = { viewModel.playVoiceMessage(message) },
                        onPauseVoice = { viewModel.pauseVoicePlayback() },
                        onResumeVoice = { viewModel.resumeVoicePlayback() },
                        onSeekVoice = { viewModel.seekVoicePlayback(it) },
                        onViewOnMap = onViewOnMap
                    )
                }
            }
        }

        // Error snackbar
        if (sendingState is SendingState.Error) {
            val error = (sendingState as SendingState.Error).message
            Snackbar(
                modifier = Modifier.padding(16.dp),
                containerColor = StatusFailed
            ) {
                Text(text = "Send failed: $error")
            }
        }
    }

    // Location share bottom sheet
    if (showLocationSheet && locationShareViewModel != null) {
        LocationShareBottomSheet(
            onConfirm = { payload ->
                viewModel.sendLocationShare(payload)
                showLocationSheet = false
            },
            onDismiss = { showLocationSheet = false },
            viewModel = locationShareViewModel
        )
    }
}

// ─── Normal Input Bar ────────────────────────────────────────────

@Composable
private fun NormalInputBar(
    messageText: String,
    onMessageChange: (String) -> Unit,
    sendingState: SendingState,
    onSendText: () -> Unit,
    onPickImage: () -> Unit,
    onStartRecording: () -> Unit,
    onShareLocation: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .imePadding(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Image picker button
        IconButton(
            onClick = onPickImage,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = "Send image",
                tint = TextSecondary
            )
        }

        // Location share button
        IconButton(
            onClick = onShareLocation,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Share location",
                tint = TextSecondary
            )
        }
        // Text field
        OutlinedTextField(
            value = messageText,
            onValueChange = onMessageChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            placeholder = {
                Text("Message...", color = TextMuted)
            },
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceCard,
                unfocusedContainerColor = SurfaceCard,
                focusedBorderColor = MeshBlue,
                unfocusedBorderColor = SurfaceDivider,
                cursorColor = MeshBlue
            ),
            maxLines = 4
        )

        // Voice record button (shown when no text)
        if (messageText.isBlank()) {
            IconButton(
                onClick = onStartRecording,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Record voice",
                    tint = MeshBlueBright
                )
            }
        }

        // Send button (shown when text is entered)
        if (messageText.isNotBlank()) {
            FilledIconButton(
                onClick = onSendText,
                enabled = sendingState !is SendingState.Sending,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MeshBlue,
                    contentColor = TextPrimary,
                    disabledContainerColor = MeshBlue.copy(alpha = 0.3f)
                )
            ) {
                if (sendingState is SendingState.Sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = TextPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send"
                    )
                }
            }
        }
    }
}

// ─── Voice Recording Bar ─────────────────────────────────────────

@Composable
private fun VoiceRecordingBar(
    durationMs: Long,
    amplitude: Int,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    // Pulsing red dot animation
    val pulseAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recordingPulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pulsing record indicator
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color.Red.copy(alpha = pulseAlpha))
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Duration
        Text(
            text = formatVoiceDuration(durationMs),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Simple amplitude indicator
        val normalizedAmp = (amplitude / 32767f).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MeshBlue.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = 0.1f + normalizedAmp * 0.9f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MeshBlue)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Cancel button
        IconButton(
            onClick = onCancel,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel recording",
                tint = StatusFailed
            )
        }

        // Send button
        FilledIconButton(
            onClick = onSend,
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MeshBlue,
                contentColor = TextPrimary
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send voice"
            )
        }
    }
}
