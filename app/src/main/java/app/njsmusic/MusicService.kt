package app.njsmusic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlin.math.abs
import kotlin.math.max

/**
 * Servicio en primer plano: escucha el audio (microfono o audio interno del celular),
 * lo analiza y manda colores al lightstick. Sigue funcionando con la pantalla apagada.
 */
class MusicService : Service() {

    companion object {
        const val ACTION_STOP = "app.njsmusic.STOP"
        const val EXTRA_SOURCE = "source"      // 0 = microfono, 1 = audio interno
        const val EXTRA_PALETTE = "palette"    // 0 arcoiris, 1 los 6 colores, 2 integrantes
        const val EXTRA_MODE = "mode"          // modo del lightstick 1..4
        const val EXTRA_MINB = "minb"          // brillo minimo 0..1
        const val EXTRA_CODE = "code"          // resultCode de MediaProjection
        const val EXTRA_DATA = "data"          // Intent de MediaProjection
        private const val CHANNEL = "musica"
        private const val NOTIF_ID = 7
        private const val SAMPLE_RATE = 44100
        private const val BLOCK = 1024
    }

    @Volatile private var running = false
    private var worker: Thread? = null
    private var projection: MediaProjection? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val source = intent.getIntExtra(EXTRA_SOURCE, 0)
        val paletteId = intent.getIntExtra(EXTRA_PALETTE, 1)
        val mode = intent.getIntExtra(EXTRA_MODE, 1)
        val minB = intent.getFloatExtra(EXTRA_MINB, 0.04f)
        val internal = source == 1

        // 1) pasar a primer plano de inmediato (Android lo exige en pocos segundos)
        try {
            val notif = buildNotification()
            if (Build.VERSION.SDK_INT >= 30) {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                if (internal) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                startForeground(NOTIF_ID, notif, type)
            } else if (Build.VERSION.SDK_INT == 29 && internal) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIF_ID, notif)
            }
        } catch (e: Exception) {
            Bus.message = "No se pudo iniciar el servicio: ${e.message}"
            stopSelf()
            return START_NOT_STICKY
        }

        // 2) si es audio interno, obtener el permiso de captura que dio el usuario
        if (internal) {
            if (Build.VERSION.SDK_INT < 29) {
                Bus.message = "El audio interno requiere Android 10 o superior. Usa el micrófono."
                stopSelf()
                return START_NOT_STICKY
            }
            val code = intent.getIntExtra(EXTRA_CODE, 0)
            val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
            if (data == null) {
                Bus.message = "Falta el permiso de captura de audio."
                stopSelf()
                return START_NOT_STICKY
            }
            try {
                val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val p = mpm.getMediaProjection(code, data)
                p.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        running = false
                    }
                }, Handler(Looper.getMainLooper()))
                projection = p
            } catch (e: Exception) {
                Bus.message = "No se pudo capturar el audio interno: ${e.message}"
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // 3) arrancar el analisis
        if (running) return START_NOT_STICKY   // ya estaba activo
        running = true
        Bus.musicRunning = true
        Bus.message = if (internal) "Escuchando el audio del celular…" else "Escuchando el micrófono…"
        val analyzer = MusicAnalyzer(
            sampleRate = SAMPLE_RATE,
            block = BLOCK,
            minBrightness = minB,
            palette = Palettes.byId(paletteId)
        )
        val t = Thread { runLoop(analyzer, internal, mode) }
        worker = t
        t.start()
        return START_NOT_STICKY
    }

    private fun buildRecord(internal: Boolean): AudioRecord {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = max(minBuf, BLOCK * 2 * 4)
        if (!internal) {
            return AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize
            )
        }
        val proj = projection ?: throw IllegalStateException("sin proyección")
        val cfg = AudioPlaybackCaptureConfiguration.Builder(proj)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val fmt = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        return AudioRecord.Builder()
            .setAudioFormat(fmt)
            .setBufferSizeInBytes(bufSize)
            .setAudioPlaybackCaptureConfig(cfg)
            .build()
    }

    private fun runLoop(analyzer: MusicAnalyzer, internal: Boolean, mode: Int) {
        var rec: AudioRecord? = null
        try {
            rec = buildRecord(internal)
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                Bus.message = "No se pudo abrir el audio. ¿Diste el permiso del micrófono?"
                return
            }
            rec.startRecording()
            val shorts = ShortArray(BLOCK)
            val floats = FloatArray(BLOCK)
            var lastR = -1000
            var lastG = -1000
            var lastB = -1000
            var silentSince = System.nanoTime()
            var warned = false

            while (running) {
                var n = 0
                while (n < BLOCK && running) {
                    val got = rec.read(shorts, n, BLOCK - n)
                    if (got < 0) {
                        Bus.message = "Se perdió la señal de audio (código $got)."
                        return
                    }
                    n += got
                }
                if (!running) break

                var peakAbs = 0
                for (i in 0 until BLOCK) {
                    peakAbs = max(peakAbs, abs(shorts[i].toInt()))
                    floats[i] = shorts[i] / 32768f
                }
                val now = System.nanoTime()
                if (peakAbs > 40) {
                    silentSince = now
                    if (warned) {
                        warned = false
                        Bus.message = if (internal) "Escuchando el audio del celular…" else "Escuchando el micrófono…"
                    }
                } else if (!warned && now - silentSince > 8_000_000_000L) {
                    warned = true
                    Bus.message = if (internal) {
                        "No llega audio. Puede que la app que suena bloquee la captura (o no hay música). Prueba con el micrófono."
                    } else {
                        "No se escucha nada. Sube el volumen o acerca el celular a la fuente."
                    }
                }

                val out = analyzer.process(floats, now / 1e9)
                Bus.r = out.r
                Bus.g = out.g
                Bus.b = out.b
                if (colorDiff(out.r, out.g, out.b, lastR, lastG, lastB) >= 14) {
                    LightStick.setLed(mode, out.r, out.g, out.b)
                    lastR = out.r
                    lastG = out.g
                    lastB = out.b
                }
            }
        } catch (e: Exception) {
            Bus.message = "Error de audio: ${e.message}"
        } finally {
            try {
                rec?.stop()
            } catch (e: Exception) {
            }
            try {
                rec?.release()
            } catch (e: Exception) {
            }
            try {
                projection?.stop()
            } catch (e: Exception) {
            }
            projection = null
            Bus.musicRunning = false
            // devolver el lightstick al ultimo color puesto a mano
            val m = LightStick.lastManual
            LightStick.setLed(m[0], m[1], m[2], m[3])
            stopSelf()
        }
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Modo música", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MusicService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        @Suppress("DEPRECATION")
        val action = Notification.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel, "Detener", stop
        ).build()
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Modo música activo")
            .setContentText("Tu lightstick sigue el ritmo")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(open)
            .addAction(action)
            .setOngoing(true)
            .build()
    }
}
