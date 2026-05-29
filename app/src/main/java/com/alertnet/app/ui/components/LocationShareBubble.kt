package com.alertnet.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alertnet.app.model.LocationSharePayload
import com.alertnet.app.ui.theme.*

/**
 * Chat bubble for LOCATION_SHARE messages.
 *
 * Renders:
 * - Location pin icon + label or "Shared Location"
 * - Coordinate readout with accuracy
 * - Relative timestamp
 * - "View on Map" CTA button
 */
@Composable
fun LocationShareBubble(
    payload: LocationSharePayload,
    isSelf: Boolean,
    onViewOnMap: (lat: Double, lon: Double) -> Unit
) {
    val bubbleGradient = if (isSelf) {
        Brush.linearGradient(listOf(BubbleSent, BubbleSentLight))
    } else {
        Brush.linearGradient(listOf(BubbleReceived, BubbleReceivedLight))
    }

    Column(
        modifier = Modifier
            .widthIn(min = 200.dp, max = 280.dp)
            .clip(
                RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isSelf) 16.dp else 4.dp,
                    bottomEnd = if (isSelf) 4.dp else 16.dp
                )
            )
            .background(bubbleGradient)
            .padding(12.dp)
    ) {
        // Location header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = if (isSelf) MeshBlueGlow else MeshBlueBright,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = payload.label ?: "Shared Location",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = TextPrimary
            )
        }

        Spacer(Modifier.height(6.dp))

        // Coordinates
        Text(
            text = "%.5f, %.5f".format(payload.lat, payload.lon),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )

        // Accuracy + relative time
        Text(
            text = "±${payload.accuracyMeters.toInt()}m  •  ${formatRelativeTime(payload.timestampEpochSec)}",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )

        Spacer(Modifier.height(10.dp))

        // View on Map CTA
        OutlinedButton(
            onClick = { onViewOnMap(payload.lat, payload.lon) },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 6.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MeshBlueBright
            )
        ) {
            Icon(
                Icons.Default.Map,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text("View on Map", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Format an epoch-second timestamp as a relative time string.
 */
fun formatRelativeTime(epochSec: Long): String {
    val ageSeconds = (System.currentTimeMillis() / 1000) - epochSec
    return when {
        ageSeconds < 60 -> "Just now"
        ageSeconds < 3600 -> "${ageSeconds / 60}m ago"
        ageSeconds < 86400 -> "${ageSeconds / 3600}h ago"
        else -> "${ageSeconds / 86400}d ago"
    }
}
