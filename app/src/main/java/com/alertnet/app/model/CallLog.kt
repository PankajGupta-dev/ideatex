package com.alertnet.app.model

import kotlinx.serialization.Serializable

/**
 * Status of a completed voice call stored in the call_logs SQLite table.
 */
@Serializable
enum class CallLogStatus {
    /** Caller cancelled or timed out before receiver answered */
    MISSED,
    /** Receiver explicitly rejected the call */
    REJECTED,
    /** Call was answered and audio connected */
    ANSWERED,
    /** Call ended normally after being connected */
    ENDED
}

/**
 * Represents a completed voice call record persisted in the call_logs table.
 *
 * @property id Unique call session identifier (same as CallSignal.callId)
 * @property callerId Device ID of the call initiator
 * @property receiverId Device ID of the call recipient
 * @property startTime Epoch millis when the call was initiated
 * @property endTime Epoch millis when the call ended (null if still active)
 * @property duration Call duration in seconds (0 if never connected)
 * @property status Final outcome of the call
 */
data class CallLog(
    val id: String,
    val callerId: String,
    val receiverId: String,
    val startTime: Long,
    val endTime: Long? = null,
    val duration: Int = 0,
    val status: CallLogStatus
)
