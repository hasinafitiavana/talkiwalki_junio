package com.talkiwalki.junior.audio

import java.nio.ByteBuffer

data class UdpVoicePacket(val speakerId: String, val sequence: Int, val pcm: ByteArray)
object UdpVoiceCodec {
    private const val MAGIC = 0x54574A41
    const val MAX_PAYLOAD = 1_200
    fun encode(packet: UdpVoicePacket): ByteArray {
        require(packet.speakerId.length in 1..64 && packet.pcm.size <= MAX_PAYLOAD)
        return ByteBuffer.allocate(4 + 4 + 1 + packet.speakerId.length + packet.pcm.size).putInt(MAGIC).putInt(packet.sequence).put(packet.speakerId.length.toByte()).put(packet.speakerId.toByteArray()).put(packet.pcm).array()
    }
    fun decode(bytes: ByteArray, length: Int): UdpVoicePacket? = runCatching {
        if (length !in 10..(MAX_PAYLOAD + 73)) return null
        val input = ByteBuffer.wrap(bytes, 0, length); require(input.int == MAGIC); val sequence = input.int; val idLen = input.get().toInt() and 0xff; require(idLen in 1..64 && input.remaining() in idLen..(idLen + MAX_PAYLOAD)); val idBytes = ByteArray(idLen); input.get(idBytes); val pcm = ByteArray(input.remaining()); input.get(pcm); UdpVoicePacket(String(idBytes), sequence, pcm)
    }.getOrNull()
}
