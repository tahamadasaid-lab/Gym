package com.gymtracker.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.gymtracker.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Alertes sonores du minuteur.
 * - Bips générés (aucun fichier audio) et annonce vocale (synthèse vocale du téléphone).
 * - Par défaut, le son suit la sortie « média » : il passe dans les écouteurs (filaires ou Bluetooth)
 *   et baisse temporairement la musique pendant l'alerte.
 * - Option « canal alarme » : sonne même en mode silencieux (mais aussi sur le haut-parleur).
 */
class AlertPlayer(private val context: Context, private val scope: CoroutineScope) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    /** Initialise la synthèse vocale à l'avance (appelé au démarrage d'un repos). */
    fun prepare() {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val t = tts ?: return@TextToSpeech
                val r = t.setLanguage(Locale.FRENCH)
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    private fun attributes(settings: AppSettings, speech: Boolean): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(if (settings.useAlarmChannel) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(if (speech) AudioAttributes.CONTENT_TYPE_SPEECH else AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    fun playEnd(settings: AppSettings, label: String) {
        if (!settings.soundEnabled && !settings.voiceEnabled) return
        val text = if (label.isBlank()) "Repos terminé. C'est reparti !" else "Repos terminé. Série suivante : $label"
        play(settings, beeps = if (settings.soundEnabled) 3 else 0, highPitch = true, speech = text.takeIf { settings.voiceEnabled })
    }

    fun playWarning(settings: AppSettings, seconds: Int) {
        if (!settings.warningEnabled || (!settings.soundEnabled && !settings.voiceEnabled)) return
        if (settings.voiceEnabled) play(settings, beeps = 0, highPitch = false, speech = "$seconds secondes")
        else play(settings, beeps = 1, highPitch = false, speech = null)
    }

    private fun play(settings: AppSettings, beeps: Int, highPitch: Boolean, speech: String?) {
        val beepAttrs = attributes(settings, speech = false)
        val focus = requestFocus(beepAttrs)
        scope.launch {
            var duration = 0L
            if (beeps > 0) duration += playBeeps(beepAttrs, beeps, highPitch)
            if (speech != null) {
                if (beeps > 0) delay(duration)
                if (!speak(settings, speech, focus)) {
                    // Synthèse vocale indisponible : on se rabat sur des bips.
                    val fallback = if (beeps == 0) playBeeps(beepAttrs, if (highPitch) 3 else 1, highPitch) else 0L
                    delay(fallback + 200)
                    abandonFocus(focus)
                }
            } else {
                delay(duration + 200)
                abandonFocus(focus)
            }
        }
    }

    /** Retourne la durée du son en ms. */
    private fun playBeeps(attrs: AudioAttributes, count: Int, highPitch: Boolean): Long {
        val rate = 44_100
        val beepMs = 180
        val gapMs = 110
        val freq = if (highPitch) 1046.5 else 784.0 // do6 / sol5
        val beepSamples = rate * beepMs / 1000
        val gapSamples = rate * gapMs / 1000
        val total = count * beepSamples + (count - 1) * gapSamples
        val pcm = ShortArray(total)
        val fade = rate / 100 // 10 ms
        for (b in 0 until count) {
            val start = b * (beepSamples + gapSamples)
            val f = if (b == count - 1 && count > 1) freq * 1.5 else freq
            for (i in 0 until beepSamples) {
                val env = min(1.0, min(i, beepSamples - i).toDouble() / fade)
                pcm[start + i] = (sin(2 * PI * f * i / rate) * env * 0.85 * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track.write(pcm, 0, pcm.size)
            track.play()
            val ms = total * 1000L / rate
            scope.launch {
                delay(ms + 300)
                runCatching { track.stop() }
                track.release()
            }
            ms
        }.getOrDefault(0L)
    }

    /** Retourne false si la synthèse vocale n'est pas disponible. */
    private fun speak(settings: AppSettings, text: String, focus: AudioFocusRequest?): Boolean {
        val t = tts
        if (t == null || !ttsReady) {
            prepare()
            return false
        }
        t.setAudioAttributes(attributes(settings, speech = true))
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = abandonFocus(focus)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = abandonFocus(focus)
        })
        val params = Bundle()
        if (settings.useAlarmChannel) params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
        return t.speak(text, TextToSpeech.QUEUE_FLUSH, params, "rest-${System.nanoTime()}") == TextToSpeech.SUCCESS
    }

    private fun requestFocus(attrs: AudioAttributes): AudioFocusRequest? = runCatching {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .build()
        audioManager?.requestAudioFocus(req)
        req
    }.getOrNull()

    private fun abandonFocus(req: AudioFocusRequest?) {
        if (req != null) runCatching { audioManager?.abandonAudioFocusRequest(req) }
    }
}
