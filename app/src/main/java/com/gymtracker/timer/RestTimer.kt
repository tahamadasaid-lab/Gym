package com.gymtracker.timer

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Minuteur de repos partagé par toute l'application.
 * - Affiché en bas de tous les écrans.
 * - Pendant le décompte, un service au premier plan affiche une notification (visible en changeant d'appli
 *   et sur l'écran de verrouillage) et un wake lock garantit l'alerte même écran éteint.
 * - À la fin : vibration, bip et notification « Repos terminé ».
 */
class RestTimer(private val context: Context, private val scope: CoroutineScope) {

    data class State(
        val totalSeconds: Int = 0,
        val remainingSeconds: Int = 0,
        val running: Boolean = false,
        val label: String = "",
        /** Heure murale de fin (pour le compte à rebours de la notification). */
        val endsAtWallClock: Long = 0,
    ) {
        val visible: Boolean get() = totalSeconds > 0
        val finished: Boolean get() = visible && !running
        val progress: Float get() = if (totalSeconds == 0) 0f else remainingSeconds / totalSeconds.toFloat()
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null
    private var endAt = 0L

    private val wakeLock: PowerManager.WakeLock? by lazy {
        context.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GymTracker:rest")
            ?.apply { setReferenceCounted(false) }
    }

    fun start(seconds: Int, label: String) {
        if (seconds <= 0) return
        endAt = SystemClock.elapsedRealtime() + seconds * 1000L
        _state.value = State(seconds, seconds, true, label, wallEnd())
        Notifications.cancelFinished(context)
        holdWakeLock(seconds)
        startService()
        loop()
    }

    fun adjust(deltaSeconds: Int) {
        if (!_state.value.running) return
        endAt += deltaSeconds * 1000L
        val remaining = remaining()
        if (remaining <= 0) {
            finish()
        } else {
            holdWakeLock(remaining)
            _state.update {
                it.copy(
                    totalSeconds = maxOf(it.totalSeconds + deltaSeconds, remaining),
                    remainingSeconds = remaining,
                    endsAtWallClock = wallEnd(),
                )
            }
        }
    }

    fun dismiss() {
        job?.cancel()
        releaseWakeLock()
        Notifications.cancelFinished(context)
        _state.value = State()
    }

    private fun wallEnd(): Long = System.currentTimeMillis() + (endAt - SystemClock.elapsedRealtime())

    private fun remaining(): Int = ((endAt - SystemClock.elapsedRealtime() + 999) / 1000).toInt()

    private fun loop() {
        job?.cancel()
        job = scope.launch {
            while (true) {
                val r = remaining()
                if (r <= 0) {
                    finish()
                    break
                }
                _state.update { it.copy(remainingSeconds = r) }
                delay(200)
            }
        }
    }

    private fun finish() {
        job?.cancel()
        val label = _state.value.label
        _state.update { it.copy(remainingSeconds = 0, running = false) }
        alert()
        Notifications.showFinished(context, label)
        scope.launch {
            delay(3_000) // laisse le temps au bip / à la vibration
            releaseWakeLock()
        }
    }

    private fun startService() {
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, RestTimerService::class.java))
        }
    }

    private fun holdWakeLock(seconds: Int) {
        runCatching { wakeLock?.acquire((seconds + 30) * 1000L) }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
    }

    private fun alert() {
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 700), -1))
        }
        scope.launch {
            runCatching {
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
                tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 800)
                delay(1000)
                tone.release()
            }
        }
    }
}
