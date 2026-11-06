package com.talkiwalki.junior.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Emits a local alert when a different participant starts a turn to speak. */
fun interface SpeakerStartNotifier {
    fun notifySpeakerStarted()
}

object NoopSpeakerStartNotifier : SpeakerStartNotifier {
    override fun notifySpeakerStarted() = Unit
}

class SpeakerStartVibration(context: Context) : SpeakerStartNotifier {
    private val triplePulse = ThreePulseVibration(context)

    override fun notifySpeakerStarted() = triplePulse.vibrate()
}

/** One short, recognisable alert used for both incoming messages and speech starts. */
class ThreePulseVibration(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun vibrate() {
        val deviceVibrator = vibrator ?: return
        if (!deviceVibrator.hasVibrator()) return
        deviceVibrator.vibrate(
            VibrationEffect.createWaveform(PULSE_PATTERN, -1)
        )
    }

    private companion object {
        // Three 150 ms pulses, spaced by 100 ms silences.
        val PULSE_PATTERN = longArrayOf(0L, 150L, 100L, 150L, 100L, 150L)
    }
}

/**
 * Detects the start of a remote speaking turn without alerting while a session
 * is first restored or when the local participant is the speaker.
 */
internal class SpeakerStartDetector {
    private var observedSelfId: String? = null
    private var observedSpeakerId: String? = null

    fun anotherParticipantStartedSpeaking(selfId: String?, speakerId: String?): Boolean {
        if (selfId == null || selfId != observedSelfId) {
            observedSelfId = selfId
            observedSpeakerId = speakerId
            return false
        }

        val started = speakerId != null && speakerId != selfId && speakerId != observedSpeakerId
        observedSpeakerId = speakerId
        return started
    }
}
