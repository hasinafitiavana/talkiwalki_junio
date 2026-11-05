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
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun notifySpeakerStarted() {
        val deviceVibrator = vibrator ?: return
        if (!deviceVibrator.hasVibrator()) return
        deviceVibrator.vibrate(
            VibrationEffect.createOneShot(VIBRATION_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    private companion object {
        const val VIBRATION_DURATION_MS = 5_000L
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
