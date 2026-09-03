package com.picscan.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.picscan.app.data.model.BeerVerdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

import android.media.MediaPlayer
import android.util.Log
import com.picscan.app.R
import java.io.File

object SoundEffectPlayer {

    private val audioScope = CoroutineScope(Dispatchers.Default)
    private var activeMediaPlayer: MediaPlayer? = null
    private val playerMutex = Any()

    /**
     * Stops and releases any currently active MediaPlayer.
     */
    fun stop() {
        synchronized(playerMutex) {
            try {
                activeMediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        player.stop()
                    }
                    player.release()
                }
            } catch (_: Exception) {}
            activeMediaPlayer = null
        }
    }

    /**
     * Plays an MP3 file using the best available source:
     * 1. Direct host absolute file path (if readable on the system)
     * 2. Android raw resource (bundled inside APK)
     * 3. Android asset file (sound/<filename>)
     * 4. Synthesizer fallback if playback could not be started
     */
    private fun playAudioFile(
        context: Context?,
        rawResId: Int,
        assetRelativePath: String,
        directFilePath: String,
        fallbackSynthesizer: () -> Unit
    ) {
        synchronized(playerMutex) {
            try {
                activeMediaPlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            player.stop()
                        }
                    } catch (_: Exception) {}
                    try {
                        player.release()
                    } catch (_: Exception) {}
                }
                activeMediaPlayer = null

                // 1. Direct host file check
                val directFile = File(directFilePath)
                if (directFile.exists() && directFile.canRead()) {
                    try {
                        val player = MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build()
                            )
                            setDataSource(directFile.absolutePath)
                            setOnCompletionListener { mp ->
                                synchronized(playerMutex) {
                                    try {
                                        mp.release()
                                        if (activeMediaPlayer == mp) {
                                            activeMediaPlayer = null
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                            setOnErrorListener { mp, _, _ ->
                                synchronized(playerMutex) {
                                    try {
                                        mp.release()
                                        if (activeMediaPlayer == mp) {
                                            activeMediaPlayer = null
                                        }
                                    } catch (_: Exception) {}
                                }
                                true
                            }
                            prepare()
                            start()
                        }
                        activeMediaPlayer = player
                        return
                    } catch (e: Exception) {
                        Log.w("SoundEffectPlayer", "Direct file playback failed for $directFilePath", e)
                    }
                }

                // 2. Android raw resource bundled in APK
                if (context != null) {
                    try {
                        val player = MediaPlayer.create(context, rawResId)
                        if (player != null) {
                            player.setOnCompletionListener { mp ->
                                synchronized(playerMutex) {
                                    try {
                                        mp.release()
                                        if (activeMediaPlayer == mp) {
                                            activeMediaPlayer = null
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                            player.setOnErrorListener { mp, _, _ ->
                                synchronized(playerMutex) {
                                    try {
                                        mp.release()
                                        if (activeMediaPlayer == mp) {
                                            activeMediaPlayer = null
                                        }
                                    } catch (_: Exception) {}
                                }
                                true
                            }
                            player.start()
                            activeMediaPlayer = player
                            return
                        }
                    } catch (e: Exception) {
                        Log.w("SoundEffectPlayer", "Raw resource playback failed for $rawResId", e)
                    }

                    // 3. Android asset file
                    try {
                        val afd = context.assets.openFd(assetRelativePath)
                        val player = MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build()
                            )
                            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                            afd.close()
                            setOnCompletionListener { mp ->
                                synchronized(playerMutex) {
                                    try {
                                        mp.release()
                                        if (activeMediaPlayer == mp) {
                                            activeMediaPlayer = null
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                            setOnErrorListener { mp, _, _ ->
                                synchronized(playerMutex) {
                                    try {
                                        mp.release()
                                        if (activeMediaPlayer == mp) {
                                            activeMediaPlayer = null
                                        }
                                    } catch (_: Exception) {}
                                }
                                true
                            }
                            prepare()
                            start()
                        }
                        activeMediaPlayer = player
                        return
                    } catch (e: Exception) {
                        Log.w("SoundEffectPlayer", "Asset playback failed for $assetRelativePath", e)
                    }
                }

                // Fallback to synthesized audio
                fallbackSynthesizer()
            } catch (e: Exception) {
                Log.e("SoundEffectPlayer", "Error during sound playback", e)
                fallbackSynthesizer()
            }
        }
    }

    /**
     * Plays the appropriate sound effect and haptics for the specified BeerVerdict tier.
     */
    fun playBeerVerdictSound(verdict: BeerVerdict, context: Context? = null) {
        when (verdict) {
            BeerVerdict.HOPFENBOMBE -> playHopfenbombeSound(context)
            BeerVerdict.LECKER_BIERCHEN -> playLeckerBierchenSound(context)
            BeerVerdict.WEGBIER -> playWegbierSound(context)
            BeerVerdict.PENNERGLUECK -> playPennerglueckSound(context)
            BeerVerdict.PISSBRUEHE -> playPissbrueheSound(context)
            BeerVerdict.NONE -> {}
        }
    }

    /**
     * Tier 1: Hopfenbombe (Beste Bier-Bewertung)
     * Plays: Voicy_Feuball Junge, BAM!.mp3
     */
    fun playHopfenbombeSound(context: Context? = null) {
        playAudioFile(
            context = context,
            rawResId = R.raw.voicy_feuball_junge_bam,
            assetRelativePath = "sound/Voicy_Feuball Junge, BAM!.mp3",
            directFilePath = "/home/joachim/IdeaProjects/picscan/sound/Voicy_Feuball Junge, BAM!.mp3",
            fallbackSynthesizer = { playSynthesizedHopfenbombe() }
        )

        context?.let { triggerExplosionVibration(it) }
    }

    private fun playSynthesizedHopfenbombe() {
        audioScope.launch {
            try {
                val sampleRate = 44100
                val sweepDuration = 0.28 // Rising sweep & impact
                val chordDuration = 0.55 // Triumphant sustained chord
                val totalSamples = ((sweepDuration + chordDuration) * sampleRate).toInt()
                val audioData = ShortArray(totalSamples)

                // 1. Rising explosive pitch sweep (200Hz -> 1200Hz)
                val sweepCount = (sweepDuration * sampleRate).toInt()
                for (i in 0 until sweepCount) {
                    val t = i.toDouble() / sampleRate
                    val progress = t / sweepDuration
                    val freq = 200.0 + 1000.0 * (progress * progress) // Quadratic rise
                    val phase = 2 * PI * freq * t
                    val envelope = (1.0 - exp(-30.0 * t)) * (0.8 + 0.2 * sin(2 * PI * 18.0 * t))
                    val sample = (0.6 * sin(phase) + 0.3 * sin(2 * phase) + 0.15 * sin(3 * phase)) * envelope
                    audioData[i] = (sample * Short.MAX_VALUE * 0.9).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }

                // 2. Triumphant bombastic explosion chord (Major triad: F5=698Hz, A5=880Hz, C6=1046Hz, F6=1396Hz)
                val chordCount = (chordDuration * sampleRate).toInt()
                var sampleIndex = sweepCount
                for (i in 0 until chordCount) {
                    val t = i.toDouble() / sampleRate
                    val envelope = (1.0 - exp(-25.0 * t)) * exp(-2.2 * t)
                    val sample = (
                        0.30 * sin(2 * PI * 698.46 * t) +
                        0.25 * sin(2 * PI * 880.00 * t) +
                        0.25 * sin(2 * PI * 1046.50 * t) +
                        0.20 * sin(2 * PI * 1396.91 * t)
                    ) * envelope
                    if (sampleIndex < audioData.size) {
                        audioData[sampleIndex++] = (sample * Short.MAX_VALUE * 0.95).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                }

                playPcmAudio(audioData, sampleRate)
            } catch (_: Exception) {}
        }
    }

    /**
     * Tier 2: Lecker Bierchen (Zweitbeste Bier-Bewertung)
     * Plays: fickschnitzl.mp3
     */
    fun playLeckerBierchenSound(context: Context? = null) {
        playAudioFile(
            context = context,
            rawResId = R.raw.fickschnitzl,
            assetRelativePath = "sound/fickschnitzl.mp3",
            directFilePath = "/home/joachim/IdeaProjects/picscan/sound/fickschnitzl.mp3",
            fallbackSynthesizer = { playSynthesizedLeckerBierchen() }
        )

        context?.let { triggerCelebrationVibration(it) }
    }

    private fun playSynthesizedLeckerBierchen() {
        audioScope.launch {
            try {
                val sampleRate = 44100
                val noteDuration = 0.12 // seconds per note
                val finalDuration = 0.45 // seconds for finale

                val notes = listOf(523.25, 659.25, 783.99, 1046.50)
                val totalSamples = ((noteDuration * notes.size + finalDuration) * sampleRate).toInt()
                val audioData = ShortArray(totalSamples)

                var sampleIndex = 0

                // Arpeggio notes
                for (freq in notes) {
                    val count = (noteDuration * sampleRate).toInt()
                    for (i in 0 until count) {
                        val t = i.toDouble() / sampleRate
                        val envelope = (1.0 - exp(-15.0 * t)) * exp(-3.5 * t)
                        val sample = (
                            0.60 * sin(2 * PI * freq * t) +
                            0.30 * sin(2 * PI * freq * 2 * t) +
                            0.10 * sin(2 * PI * freq * 3 * t)
                        ) * envelope
                        if (sampleIndex < audioData.size) {
                            audioData[sampleIndex++] = (sample * Short.MAX_VALUE * 0.85).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        }
                    }
                }

                // Final celebratory chord (C5 + E5 + G5 + C6)
                val chordCount = (finalDuration * sampleRate).toInt()
                for (i in 0 until chordCount) {
                    val t = i.toDouble() / sampleRate
                    val envelope = (1.0 - exp(-20.0 * t)) * exp(-2.0 * t)
                    val sample = (
                        0.30 * sin(2 * PI * 523.25 * t) +
                        0.25 * sin(2 * PI * 659.25 * t) +
                        0.25 * sin(2 * PI * 783.99 * t) +
                        0.20 * sin(2 * PI * 1046.50 * t)
                    ) * envelope
                    if (sampleIndex < audioData.size) {
                        audioData[sampleIndex++] = (sample * Short.MAX_VALUE * 0.90).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                }

                playPcmAudio(audioData, sampleRate)
            } catch (_: Exception) {}
        }
    }

    /**
     * Tier 3: Wegbier (Drittbeste Bier-Bewertung)
     * Plays: New kids Schultenbräu ! (128kbit_AAC).m4a
     */
    fun playWegbierSound(context: Context? = null) {
        playAudioFile(
            context = context,
            rawResId = R.raw.new_kids_schultenbraeu,
            assetRelativePath = "sound/New kids Schultenbräu ! (128kbit_AAC).m4a",
            directFilePath = "/home/joachim/IdeaProjects/picscan/sound/New kids Schultenbräu ! (128kbit_AAC).m4a",
            fallbackSynthesizer = { playSynthesizedWegbier() }
        )

        context?.let { triggerWegbierVibration(it) }
    }

    private fun playSynthesizedWegbier() {
        audioScope.launch {
            try {
                val sampleRate = 44100
                val noteDuration = 0.13
                val notes = listOf(392.00, 523.25, 659.25, 523.25)
                val totalSamples = (noteDuration * notes.size * sampleRate).toInt()
                val audioData = ShortArray(totalSamples)

                var sampleIndex = 0
                for (freq in notes) {
                    val count = (noteDuration * sampleRate).toInt()
                    for (i in 0 until count) {
                        val t = i.toDouble() / sampleRate
                        val envelope = (1.0 - exp(-25.0 * t)) * exp(-5.0 * t)
                        val sample = (0.7 * sin(2 * PI * freq * t) + 0.3 * sin(2 * PI * freq * 2 * t)) * envelope
                        if (sampleIndex < audioData.size) {
                            audioData[sampleIndex++] = (sample * Short.MAX_VALUE * 0.85).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        }
                    }
                }

                playPcmAudio(audioData, sampleRate)
            } catch (_: Exception) {}
        }
    }

    /**
     * Tier 4: Pennerglück - Wonky, metallic can clattering bounce & slide.
     */
    fun playPennerglueckSound(context: Context? = null) {
        audioScope.launch {
            try {
                val sampleRate = 44100
                val duration = 0.55
                val totalSamples = (duration * sampleRate).toInt()
                val audioData = ShortArray(totalSamples)

                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    // Metallic dissonant harmonics with slight downward wobble
                    val f1 = 340.0 * (1.0 - 0.2 * (t / duration))
                    val f2 = 512.0 * (1.0 - 0.15 * (t / duration))
                    val clank = sin(2 * PI * f1 * t) * 0.5 + sin(2 * PI * f2 * t) * 0.35 + sin(2 * PI * 180.0 * t) * 0.25
                    val wobble = 0.8 + 0.2 * sin(2 * PI * 14.0 * t)
                    val envelope = (1.0 - exp(-30.0 * t)) * exp(-2.8 * t)

                    val sample = clank * wobble * envelope * 0.8
                    audioData[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }

                playPcmAudio(audioData, sampleRate)
            } catch (_: Exception) {}
        }

        context?.let { triggerPennerglueckVibration(it) }
    }

    /**
     * Tier 5: Pissbrühe - Comical descending buzzer fail sound with rough dissonant tremolo.
     */
    fun playPissbrueheSound(context: Context? = null) {
        audioScope.launch {
            try {
                val sampleRate = 44100
                val duration = 0.70
                val totalSamples = (duration * sampleRate).toInt()
                val audioData = ShortArray(totalSamples)

                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val currentFreq = 240.0 - (160.0 * (t / duration))
                    val phase = 2 * PI * currentFreq * t
                    val fundamental = sin(phase)
                    val harmonic3 = 0.40 * sin(3 * phase)
                    val harmonic5 = 0.25 * sin(5 * phase)
                    val dissonant = 0.20 * sin(phase * 1.414)

                    val wobble = 0.85 + 0.15 * sin(2 * PI * 22.0 * t)
                    val envelope = (1.0 - exp(-30.0 * t)) * (1.0 - (t / duration) * 0.7)

                    val sample = (fundamental + harmonic3 + harmonic5 + dissonant) * wobble * envelope * 0.7
                    audioData[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }

                playPcmAudio(audioData, sampleRate)
            } catch (_: Exception) {}
        }

        context?.let { triggerNegativeVibration(it) }
    }

    private fun playPcmAudio(pcmData: ShortArray, sampleRate: Int) {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(pcmData.size * 2)

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(pcmData, 0, pcmData.size)
        audioTrack.play()

        val durationMs = ((pcmData.size.toDouble() / sampleRate) * 1000).toLong() + 150
        Thread.sleep(durationMs)
        try {
            audioTrack.stop()
            audioTrack.release()
        } catch (_: Exception) {}
    }

    private fun triggerExplosionVibration(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val effect = VibrationEffect.createWaveform(
                    longArrayOf(0, 40, 30, 60, 40, 100, 30, 250),
                    intArrayOf(0, 100, 0, 180, 0, 220, 0, 255),
                    -1
                )
                vibratorManager?.vibrate(CombinedVibration.createParallel(effect))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(
                        longArrayOf(0, 40, 30, 60, 40, 100, 30, 250),
                        intArrayOf(0, 100, 0, 180, 0, 220, 0, 255),
                        -1
                    )
                    vibrator?.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 40, 30, 60, 40, 100, 30, 250), -1)
                }
            }
        } catch (_: Exception) {}
    }

    private fun triggerCelebrationVibration(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val effect = VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 100, 50, 180), intArrayOf(0, 150, 0, 200, 0, 255), -1)
                vibratorManager?.vibrate(CombinedVibration.createParallel(effect))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 100, 50, 180), intArrayOf(0, 150, 0, 200, 0, 255), -1)
                    vibrator?.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 80, 60, 100, 50, 180), -1)
                }
            }
        } catch (_: Exception) {}
    }

    private fun triggerWegbierVibration(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val effect = VibrationEffect.createWaveform(longArrayOf(0, 70, 70, 70, 70, 120), intArrayOf(0, 120, 0, 150, 0, 180), -1)
                vibratorManager?.vibrate(CombinedVibration.createParallel(effect))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(longArrayOf(0, 70, 70, 70, 70, 120), intArrayOf(0, 120, 0, 150, 0, 180), -1)
                    vibrator?.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 70, 70, 70, 70, 120), -1)
                }
            }
        } catch (_: Exception) {}
    }

    private fun triggerPennerglueckVibration(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val effect = VibrationEffect.createWaveform(longArrayOf(0, 140, 80, 140), intArrayOf(0, 180, 0, 210), -1)
                vibratorManager?.vibrate(CombinedVibration.createParallel(effect))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(longArrayOf(0, 140, 80, 140), intArrayOf(0, 180, 0, 210), -1)
                    vibrator?.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 140, 80, 140), -1)
                }
            }
        } catch (_: Exception) {}
    }

    private fun triggerNegativeVibration(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val effect = VibrationEffect.createWaveform(longArrayOf(0, 250, 100, 400), intArrayOf(0, 255, 0, 255), -1)
                vibratorManager?.vibrate(CombinedVibration.createParallel(effect))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(longArrayOf(0, 250, 100, 400), intArrayOf(0, 255, 0, 255), -1)
                    vibrator?.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 250, 100, 400), -1)
                }
            }
        } catch (_: Exception) {}
    }
}
