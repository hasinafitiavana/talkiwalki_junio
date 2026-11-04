package com.talkiwalki.junior.audio

/** Host-owned, platform-free PTT lease arbiter. */
class PttArbiter(private val timeoutMs: Long = 8_000L, private val clock: () -> Long = { System.currentTimeMillis() }) {
    private var speaker: String? = null
    private var expiry = 0L
    fun request(id: String): Boolean { expire(); return if (speaker == null) { speaker = id; expiry = clock() + timeoutMs; true } else false }
    fun release(id: String): Boolean = if (speaker == id) { speaker = null; expiry = 0; true } else false
    fun disconnect(id: String): Boolean = release(id)
    fun currentSpeaker(): String? { expire(); return speaker }
    fun renew(id: String): Boolean = if (currentSpeaker() == id) { expiry = clock() + timeoutMs; true } else false
    private fun expire() { if (speaker != null && clock() >= expiry) { speaker = null; expiry = 0 } }
}
