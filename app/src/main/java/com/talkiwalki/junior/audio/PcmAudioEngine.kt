package com.talkiwalki.junior.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioManager
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** PCM voice engine. It stores no audio and owns no network socket. */
class PcmAudioEngine(
    private val context: Context? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private var recorder: AudioRecord? = null
    private var captureJob: Job? = null
    private var player: AudioTrack? = null
    private val audioManager = context?.getSystemService(AudioManager::class.java)
    private var previousAudioMode: Int? = null
    private var audioRouteConfigured = false

    fun startCapture(onFrame: (ByteArray) -> Unit): Result<Unit> = runCatching {
        if (context != null && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            error("Autorisation microphone refusée.")
        }
        routeToSpeaker()
        stopCapture()
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
        require(min > 0) { "Microphone indisponible." }
        val record = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE, CHANNEL_IN, ENCODING, maxOf(min * 2, FRAME_BYTES * 4))
        require(record.state == AudioRecord.STATE_INITIALIZED) { "Initialisation microphone impossible." }
        recorder = record; record.startRecording()
        captureJob = scope.launch {
            val buffer = ByteArray(FRAME_BYTES)
            while (isActive && recorder === record) {
                val count = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (count > 0) onFrame(buffer.copyOf(count)) else break
            }
        }
    }

    fun stopCapture() { captureJob?.cancel(); captureJob = null; recorder?.let { runCatching { it.stop() }; it.release() }; recorder = null }
    fun play(frame: ByteArray) {
        writeToSpeaker(frame)
    }
    fun release() {
        stopCapture()
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        restoreAudioRoute()
    }

    @Suppress("DEPRECATION")
    private fun routeToSpeaker() {
        val manager = audioManager ?: return
        if (audioRouteConfigured) return
        previousAudioMode = manager.mode
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            manager.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                ?.let(manager::setCommunicationDevice)
        } else {
            manager.isSpeakerphoneOn = true
        }
        audioRouteConfigured = true
    }

    @Suppress("DEPRECATION")
    private fun restoreAudioRoute() {
        val manager = audioManager ?: return
        if (!audioRouteConfigured) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) manager.clearCommunicationDevice()
        else manager.isSpeakerphoneOn = false
        previousAudioMode?.let { manager.mode = it }
        previousAudioMode = null
        audioRouteConfigured = false
    }
    private fun createPlayer(): AudioTrack {
        val min = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
        return AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setEncoding(ENCODING).setChannelMask(CHANNEL_OUT).build()).setBufferSizeInBytes(maxOf(min * 2, FRAME_BYTES * 6)).setTransferMode(AudioTrack.MODE_STREAM).build()
    }
    private fun writeToSpeaker(frame: ByteArray) {
        if (frame.isEmpty()) return
        routeToSpeaker()
        val track = player ?: createPlayer().also { player = it }
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
        track.write(frame, 0, frame.size, AudioTrack.WRITE_NON_BLOCKING)
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_BYTES = 640
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
