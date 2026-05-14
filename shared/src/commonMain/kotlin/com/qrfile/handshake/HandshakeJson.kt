package com.qrfile.handshake

import kotlinx.serialization.json.Json

/** Decodes payloads from QR/NFC; tolerates forward-compatible fields from other clients. */
val HandshakeJson = Json { ignoreUnknownKeys = true }
