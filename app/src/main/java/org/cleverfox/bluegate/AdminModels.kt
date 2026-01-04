package org.cleverfox.bluegate

data class KeyInfo(
    val keyHex: String,
    val type: String,
    val admin: Boolean
)

data class LogEntry(
    val index: Int,
    val pubkey: String,
    val addr: String,
    val uptimeMs: Long,
    val authAction: Int,
    val success: Boolean
)
