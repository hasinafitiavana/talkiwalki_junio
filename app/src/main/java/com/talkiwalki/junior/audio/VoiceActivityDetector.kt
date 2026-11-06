package com.talkiwalki.junior.audio

/** Lightweight voice-activity check for 16-bit little-endian PCM frames. */
object VoiceActivityDetector {
    // A conservative threshold made quiet speech disappear on devices whose audio
    // driver applies aggressive noise reduction (notably some Xiaomi/Redmi models).
    private const val MINIMUM_AVERAGE_AMPLITUDE = 250L

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

/**
 * Keeps sending briefly after speech is detected.  A word often contains short,
 * low-energy phonemes; dropping those individual 20 ms frames makes speech sound
 * chopped even when the network is perfect.
 */
class VoiceActivityGate(private val hangoverFrames: Int = 12) {
    private var remainingHangoverFrames = 0

    fun shouldSend(frame: ByteArray): Boolean {
        if (VoiceActivityDetector.hasVoice(frame)) {
            remainingHangoverFrames = hangoverFrames
            return true
        }
        if (remainingHangoverFrames == 0) return false
        remainingHangoverFrames--
        return true
    }
}
