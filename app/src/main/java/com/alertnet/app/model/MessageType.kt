package com.alertnet.app.model

import kotlinx.serialization.Serializable

/**
 * Type of mesh message. Each type is handled differently by the router.
 */
@Serializable
enum class MessageType {
    /** Regular text message */
    TEXT,
    /** Image attachment (payload contains base64-encoded image) */
    IMAGE,
    /** File attachment (payload contains base64-encoded file) */
    FILE,
    /** Voice recording (payload contains local file path) */
    VOICE,
    /** Delivery acknowledgment routed back to sender */
    ACK,
    /** Periodic peer presence announcement */
    PEER_ANNOUNCE,
    /** Peer leaving the mesh gracefully */
    PEER_LEAVE,
    /** Automatic background location broadcast — feeds the live peer map */
    LOCATION_PING,
    /** User-triggered location share — renders as a chat bubble, never updates peer DB */
    LOCATION_SHARE,
    /** Emergency SOS broadcast — sends location + "Help me" to all nearby peers */
    SOS
}
