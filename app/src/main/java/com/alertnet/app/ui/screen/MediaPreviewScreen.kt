package com.alertnet.app.ui.screen

import android.net.Uri
import android.util.Log
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.alertnet.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Modern media preview screen supporting images and videos.
 * Features:
 * - High-fidelity dark mode matching reference UI
 * - Crop, Draw, Text, Emoji mock edit actions (top row)
 * - Immersive full-screen media render
 * - Floating Caption TextField
 * - Floating electric blue Send button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPreviewScreen(
    mediaUri: Uri,
    mediaType: String, // "image" or "video"
    onSend: (String) -> Unit, // passes the caption
    onBack: () -> Unit
) {
    var captionText by remember { mutableStateOf("") }
    val context = LocalContext.current

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Modern edit action overlays (cropping, emojis, text, brush overlays)
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Crop, contentDescription = "Crop", tint = Color.White)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Edit, contentDescription = "Draw", tint = Color.White)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.TextFields, contentDescription = "Text", tint = Color.White)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Mood, contentDescription = "Emoji", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.5f)
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Immersive Media Render
            if (mediaType == "video") {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(mediaUri)
                            setOnPreparedListener { mediaPlayer ->
                                mediaPlayer.isLooping = true
                                // Scale fit video logic
                                val videoRatio = mediaPlayer.videoWidth.toFloat() / mediaPlayer.videoHeight.toFloat()
                                Log.d("MediaPreview", "Playing video ratio: $videoRatio")
                                start()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                var imageBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                LaunchedEffect(mediaUri) {
                    withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openInputStream(mediaUri)?.use { stream ->
                                imageBitmap = android.graphics.BitmapFactory.decodeStream(stream)
                            }
                        } catch (_: Exception) {}
                    }
                }

                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap!!.asImageBitmap(),
                        contentDescription = "Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Bottom Caption Capsule & Send controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Caption Field (Rounded modern bar)
                    OutlinedTextField(
                        value = captionText,
                        onValueChange = { captionText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text("Add a caption…", color = Color.White.copy(alpha = 0.6f))
                        },
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceCard.copy(alpha = 0.85f),
                            unfocusedContainerColor = SurfaceCard.copy(alpha = 0.85f),
                            focusedBorderColor = MeshBlue,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = MeshBlue,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        maxLines = 3
                    )

                    // Send Floating Button
                    FilledIconButton(
                        onClick = { onSend(captionText) },
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MeshBlue,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
