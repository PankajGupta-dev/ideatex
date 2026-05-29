package com.alertnet.app.ui.navigation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alertnet.app.AlertNetApplication
import com.alertnet.app.MainActivity
import com.alertnet.app.model.MeshMessage
import com.alertnet.app.security.CryptoManager
import com.alertnet.app.security.KeyManager
import com.alertnet.app.ui.screen.ChatScreen
import com.alertnet.app.ui.screen.LocationPrivacySettingsScreen
import com.alertnet.app.ui.screen.MeshMapScreen
import com.alertnet.app.ui.screen.MeshStatsScreen
import com.alertnet.app.ui.screen.PeersScreen
import com.alertnet.app.ui.theme.*
import com.alertnet.app.ui.viewmodel.*
import com.google.android.gms.location.LocationServices

private const val SOS_CHANNEL_ID = "alertnet_sos"
private const val SOS_NOTIFICATION_ID = 9999

/**
 * Navigation graph for AlertNet.
 *
 * Routes:
 * - "peers" → Nearby Users screen (home) — WhatsApp-style
 * - "chat/{peerId}/{peerName}" → Chat with a specific peer
 * - "stats" → Mesh network statistics
 * - "mesh_map?focusLat={}&focusLon={}&pinType={}" → Offline map with peer locations
 * - "location_privacy_settings" → Location broadcast & local map toggles
 */
@Composable
fun NavGraph(app: AlertNetApplication) {
    val navController = rememberNavController()
    val factory = remember { ViewModelFactory(app) }
    val context = LocalContext.current

    // Create SOS notification channel once
    LaunchedEffect(Unit) {
        createSOSNotificationChannel(context)
    }

    NavHost(
        navController = navController,
        startDestination = "peers"
    ) {
        composable("peers") {
            val viewModel: PeersViewModel = viewModel(factory = factory)
            val connectedUsers by viewModel.connectedUsers.collectAsState()
            val nearbyUsers by viewModel.nearbyOnlyUsers.collectAsState()
            val meshStats by viewModel.meshStats.collectAsState()
            val isDiscovering by viewModel.isDiscovering.collectAsState()
            val discoveryState by viewModel.discoveryState.collectAsState()
            val activeSource by viewModel.activeDiscoverySource.collectAsState()
            val sosSending by viewModel.sosSending.collectAsState()
            val connectingUserId by viewModel.connectingUserId.collectAsState()
            val connectionError by viewModel.connectionError.collectAsState()

            // Fused location client for SOS GPS
            val fusedClient = remember {
                LocationServices.getFusedLocationProviderClient(context)
            }

            // ─── Incoming SOS Alert ──────────────────────────────
            var sosAlert by remember { mutableStateOf<MeshMessage?>(null) }

            LaunchedEffect(Unit) {
                viewModel.incomingSOS.collect { message ->
                    // Decrypt the payload for display
                    val decrypted = try {
                        val key = KeyManager.getKey(context)
                        if (key != null) {
                            CryptoManager.decryptString(message.payload, key) ?: message.payload
                        } else {
                            message.payload
                        }
                    } catch (_: Exception) {
                        message.payload
                    }

                    // Fire system notification
                    fireSOSNotification(context, message.senderId, decrypted)

                    // Show in-app alert
                    sosAlert = message.copy(payload = decrypted)
                }
            }

            // SOS Received Alert Dialog
            sosAlert?.let { msg ->
                AlertDialog(
                    onDismissRequest = { sosAlert = null },
                    containerColor = SurfaceCard,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = StatusFailed,
                            modifier = Modifier.size(56.dp)
                        )
                    },
                    title = {
                        Text(
                            "🆘 SOS ALERT",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = StatusFailed
                        )
                    },
                    text = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "From: ${msg.senderId.take(8)}…",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = msg.payload,
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { sosAlert = null },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusFailed
                            )
                        ) {
                            Text("ACKNOWLEDGED", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            PeersScreen(
                connectedUsers = connectedUsers,
                nearbyUsers = nearbyUsers,
                meshStats = meshStats,
                isDiscovering = isDiscovering,
                discoveryState = discoveryState,
                activeSource = activeSource,
                connectingUserId = connectingUserId,
                connectionError = connectionError,
                onUserClick = { user ->
                    // Initiate real WiFi Direct connection; navigate to chat only on success
                    val name = user.name.replace("/", "-")
                    viewModel.connectToUser(user.id) { actualId ->
                        navController.navigate("chat/$actualId/$name")
                    }
                },
                onConnectionErrorDismissed = { viewModel.clearConnectionError() },
                onRefresh = { viewModel.refreshPeers() },
                onStatsClick = { navController.navigate("stats") },
                onMapClick = { navController.navigate("mesh_map") },
                onLocationSettingsClick = { navController.navigate("location_privacy_settings") },
                onSOSClick = { viewModel.sendSOS(fusedClient) },
                sosSending = sosSending
            )
        }

        composable(
            route = "chat/{peerId}/{peerName}",
            arguments = listOf(
                navArgument("peerId") { type = NavType.StringType },
                navArgument("peerName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val peerId = backStackEntry.arguments?.getString("peerId") ?: return@composable
            val peerName = backStackEntry.arguments?.getString("peerName") ?: "Peer"
            val chatVm: ChatViewModel = viewModel(factory = factory)
            val locationShareVm: LocationShareViewModel = viewModel(factory = factory)

            ChatScreen(
                peerId = peerId,
                peerName = peerName,
                viewModel = chatVm,
                locationShareViewModel = locationShareVm,
                onBack = { navController.popBackStack() },
                onViewOnMap = { lat, lon ->
                    navController.navigate(
                        "mesh_map?focusLat=$lat&focusLon=$lon&pinType=SHARED_LOCATION"
                    )
                }
            )
        }

        composable("stats") {
            val viewModel: PeersViewModel = viewModel(factory = factory)
            val meshStats by viewModel.meshStats.collectAsState()

            MeshStatsScreen(
                stats = meshStats,
                deviceId = app.deviceId,
                onBack = { navController.popBackStack() }
            )
        }

        // ─── Mesh Map ────────────────────────────────────────────
        composable(
            route = "mesh_map?focusLat={focusLat}&focusLon={focusLon}&pinType={pinType}",
            arguments = listOf(
                navArgument("focusLat") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("focusLon") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("pinType") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val focusLat = backStackEntry.arguments?.getString("focusLat")?.toDoubleOrNull()
            val focusLon = backStackEntry.arguments?.getString("focusLon")?.toDoubleOrNull()
            val pinType = backStackEntry.arguments?.getString("pinType")
            val mapVm: MeshMapViewModel = viewModel(factory = factory)

            MeshMapScreen(
                focusLat = focusLat,
                focusLon = focusLon,
                pinType = pinType,
                viewModel = mapVm,
                onBack = { navController.popBackStack() }
            )
        }

        // ─── Location Privacy Settings ───────────────────────────
        composable("location_privacy_settings") {
            val privacyVm: LocationPrivacyViewModel = viewModel(factory = factory)

            LocationPrivacySettingsScreen(
                viewModel = privacyVm,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

// ─── SOS Notification Helpers ─────────────────────────────────────

private fun createSOSNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            SOS_CHANNEL_ID,
            "SOS Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Emergency SOS alerts from nearby AlertNet devices"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            enableLights(true)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}

private fun fireSOSNotification(context: Context, senderId: String, payload: String) {
    val pendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    val notification = NotificationCompat.Builder(context, SOS_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle("🆘 SOS ALERT — HELP ME!")
        .setContentText("Emergency from ${senderId.take(8)}…")
        .setStyle(
            NotificationCompat.BigTextStyle()
                .bigText(payload)
                .setBigContentTitle("🆘 SOS ALERT")
                .setSummaryText("Emergency Broadcast")
        )
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setSound(alarmSound)
        .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .setFullScreenIntent(pendingIntent, true)
        .build()

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(SOS_NOTIFICATION_ID, notification)
}
