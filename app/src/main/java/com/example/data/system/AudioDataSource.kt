package com.example.data.system

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import com.example.data.model.AudioDeviceSummary
import com.example.data.model.AudioEnvironmentInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

enum class SpeakerTestMode(val label: String) {
  STEREO_1000HZ("Stereo 1 kHz"),
  LEFT_440HZ("Left Ch 440 Hz"),
  RIGHT_440HZ("Right Ch 440 Hz"),
  SWEEP("Sweep 150–3000 Hz"),
}

data class MicSample(
  val rmsNormalized: Float,
  val dbFs: Float,
  val waveform: List<Float>,
)

class AudioDataSource(private val context: Context) {

  fun readAudioEnvironment(): AudioEnvironmentInfo {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    val ringer = when (am?.ringerMode) {
      AudioManager.RINGER_MODE_NORMAL -> "Normal"
      AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
      AudioManager.RINGER_MODE_SILENT -> "Silent"
      else -> "Unknown"
    }

    val devices = runCatching {
      am?.getDevices(AudioManager.GET_DEVICES_ALL)?.map { dev ->
        AudioDeviceSummary(
          id = dev.id,
          productName = dev.productName?.toString()?.takeIf { it.isNotBlank() } ?: deviceTypeName(dev.type),
          typeLabel = deviceTypeName(dev.type),
          isSink = dev.isSink,
          isSource = dev.isSource,
          sampleRates = dev.sampleRates.toList(),
          channelCounts = dev.channelCounts.toList(),
        )
      }.orEmpty()
    }.getOrDefault(emptyList())

    return AudioEnvironmentInfo(
      ringerModeLabel = ringer,
      mediaVolume = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0,
      mediaMaxVolume = am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15,
      ringVolume = am?.getStreamVolume(AudioManager.STREAM_RING) ?: 0,
      ringMaxVolume = am?.getStreamMaxVolume(AudioManager.STREAM_RING) ?: 15,
      alarmVolume = am?.getStreamVolume(AudioManager.STREAM_ALARM) ?: 0,
      alarmMaxVolume = am?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 15,
      isMicrophoneMuted = am?.isMicrophoneMute ?: false,
      devices = devices,
    )
  }

  suspend fun runSpeakerTest(mode: SpeakerTestMode) = withContext(Dispatchers.Default) {
    val sampleRate = 44100
    val channelMask = AudioFormat.CHANNEL_OUT_STEREO
    val encoding = AudioFormat.ENCODING_PCM_16BIT
    val minBuffer = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding).coerceAtLeast(4096)

    val track = AudioTrack.Builder()
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build()
      )
      .setAudioFormat(
        AudioFormat.Builder()
          .setEncoding(encoding)
          .setSampleRate(sampleRate)
          .setChannelMask(channelMask)
          .build()
      )
      .setBufferSizeInBytes(minBuffer)
      .setTransferMode(AudioTrack.MODE_STREAM)
      .build()

    val framesPerChunk = 1024
    val buffer = ShortArray(framesPerChunk * 2)
    var phase = 0.0
    var sweepStep = 0L

    try {
      track.play()
      while (currentCoroutineContext().isActive) {
        val freq = when (mode) {
          SpeakerTestMode.STEREO_1000HZ -> 1000.0
          SpeakerTestMode.LEFT_440HZ, SpeakerTestMode.RIGHT_440HZ -> 440.0
          SpeakerTestMode.SWEEP -> {
            val cycle = (sweepStep % (sampleRate * 3L)).toDouble() / (sampleRate * 3.0)
            150.0 + cycle * 2850.0
          }
        }
        val phaseInc = 2.0 * PI * freq / sampleRate
        for (i in 0 until framesPerChunk) {
          val sampleVal = (sin(phase) * 0.25 * Short.MAX_VALUE).toInt().toShort()
          phase += phaseInc
          if (phase >= 2.0 * PI) phase -= 2.0 * PI
          sweepStep++

          when (mode) {
            SpeakerTestMode.LEFT_440HZ -> {
              buffer[i * 2] = sampleVal
              buffer[i * 2 + 1] = 0
            }
            SpeakerTestMode.RIGHT_440HZ -> {
              buffer[i * 2] = 0
              buffer[i * 2 + 1] = sampleVal
            }
            else -> {
              buffer[i * 2] = sampleVal
              buffer[i * 2 + 1] = sampleVal
            }
          }
        }
        track.write(buffer, 0, buffer.size)
      }
    } finally {
      runCatching {
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
        track.release()
      }
    }
  }

  @SuppressLint("MissingPermission")
  fun observeMicrophoneInput(): Flow<MicSample> = flow {
    val sampleRate = 16000
    val minBuffer = AudioRecord.getMinBufferSize(
      sampleRate,
      AudioFormat.CHANNEL_IN_MONO,
      AudioFormat.ENCODING_PCM_16BIT,
    ).coerceAtLeast(2048)

    val recorder = AudioRecord(
      MediaRecorder.AudioSource.MIC,
      sampleRate,
      AudioFormat.CHANNEL_IN_MONO,
      AudioFormat.ENCODING_PCM_16BIT,
      minBuffer,
    )

    if (recorder.state != AudioRecord.STATE_INITIALIZED) {
      recorder.release()
      throw IllegalStateException("AudioRecord hardware initialization failed")
    }

    val readBuf = ShortArray(1024)
    try {
      recorder.startRecording()
      while (currentCoroutineContext().isActive) {
        val read = recorder.read(readBuf, 0, readBuf.size)
        if (read > 0) {
          var sumSq = 0.0
          for (i in 0 until read) {
            val norm = readBuf[i] / 32768.0
            sumSq += norm * norm
          }
          val rms = sqrt(sumSq / read).toFloat().coerceIn(0f, 1f)
          val dbFs = if (rms > 1e-6f) (20.0 * log10(rms.toDouble())).toFloat().coerceIn(-90f, 0f) else -90f
          val step = (read / 40).coerceAtLeast(1)
          val wave = (0 until read step step).take(40).map { (readBuf[it] / 32768f).coerceIn(-1f, 1f) }
          emit(MicSample(rmsNormalized = rms, dbFs = dbFs, waveform = wave))
        }
      }
    } finally {
      runCatching {
        if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
        recorder.release()
      }
    }
  }.flowOn(Dispatchers.IO)

  private fun deviceTypeName(type: Int): String = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Built-in Earpiece"
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in Speaker"
    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
    AudioDeviceInfo.TYPE_HDMI -> "HDMI"
    AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio Device"
    AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB Accessory"
    AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Microphone"
    AudioDeviceInfo.TYPE_TELEPHONY -> "Telephony"
    AudioDeviceInfo.TYPE_HEARING_AID -> "Hearing Aid"
    AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
    else -> {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
        "BLE Audio Headset"
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
        "BLE Audio Speaker"
      } else {
        "Audio Device ($type)"
      }
    }
  }
}
