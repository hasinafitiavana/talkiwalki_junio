package com.talkiwalki.junior.audio

/** Lightweight voice-activity check for 16-bit little-endian PCM frames. */
object VoiceActivityDetector {
    private const val MINIMUM_AVERAGE_AMPLITUDE = 400L

    fun hasVoice(frame: ByteArray): Boolean {
        if (frame.size < 2) return false

        var amplitudeTotal = 0L
        var samples = 0
        var index = 0
        while (index + 1 < frame.size) {
            val sample = ((frame[index + 1].toInt() shl 8) or (frame[index].toInt() and 0xFF)).toShort().toInt()
            amplitudeTotal += kotlin.math.abs(sample).toLong()
            samples++
            index += 2
        }
        return amplitudeTotal / samples >= MINIMUM_AVERAGE_AMPLITUDE
    }
}
