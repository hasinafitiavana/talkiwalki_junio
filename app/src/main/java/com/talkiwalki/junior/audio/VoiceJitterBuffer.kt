package com.talkiwalki.junior.audio

import java.util.TreeMap

/**
 * Small ordered buffer for 20 ms UDP voice packets. Missing packets deliberately
 * become silence instead of shifting all later audio forward in time.
 */
class VoiceJitterBuffer(private val frameBytes: Int) {
    private val packets = TreeMap<Int, ByteArray>()
    private var speakerId: String? = null
    private var nextSequence: Int? = null

    fun offer(packet: UdpVoicePacket) {
        if (speakerId != packet.speakerId) {
            packets.clear()
            speakerId = packet.speakerId
            nextSequence = packet.sequence
        }
        val expected = nextSequence ?: packet.sequence.also { nextSequence = it }
        if (packet.sequence >= expected) packets.putIfAbsent(packet.sequence, packet.pcm)
    }

    fun nextFrame(): ByteArray {
        val sequence = nextSequence ?: return ByteArray(frameBytes)
        nextSequence = sequence + 1
        return packets.remove(sequence) ?: ByteArray(frameBytes)
    }
}
