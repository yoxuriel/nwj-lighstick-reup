package app.njsmusic

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Efectos generados por la app (velocidad ajustable), usando solo el modo de luz fija del lightstick.
 * Los efectos originales del lightstick (modos 2, 3 y 4) tienen velocidad fija en su firmware.
 *
 * 0 = Fijo, 1 = Destello, 2 = Destello x2, 3 = Burbujas (respira suave).
 */
object Effects {
    @Volatile var kind = 0
        private set
    /** 0 = lento … 1 = rápido */
    @Volatile var speed = 0.35f
    @Volatile var paused = false

    private var r = 0
    private var g = 0
    private var b = 0
    private val h = Handler(Looper.getMainLooper())
    private var running = false
    private var t0 = SystemClock.elapsedRealtime()

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            step()
            h.postDelayed(this, 40)
        }
    }

    /** Aplica un efecto con un color (ya con el brillo aplicado). */
    fun apply(effect: Int, red: Int, green: Int, blue: Int) {
        val changed = effect != kind
        kind = effect
        r = red
        g = green
        b = blue
        if (effect == 0) {
            stop()
            if (!Bus.musicRunning && !paused) LightStick.setLed(1, r, g, b)
            return
        }
        if (changed) t0 = SystemClock.elapsedRealtime()
        if (!running) {
            running = true
            h.post(tick)
        }
    }

    fun stop() {
        running = false
        h.removeCallbacks(tick)
    }

    private fun lerp(a: Float, bb: Float, t: Float) = a + (bb - a) * t

    /** Nivel de luz 0..1 del efecto en el instante t (segundos). */
    fun level(effect: Int, sp: Float, t: Double): Float {
        return when (effect) {
            1 -> { // destello: encendido / apagado
                val p = lerp(1.6f, 0.30f, sp)
                if ((t % p) / p < 0.45) 1f else 0f
            }
            2 -> { // destello doble y pausa
                val p = lerp(2.4f, 0.75f, sp)
                val x = (t % p) / p
                if (x < 0.12 || (x >= 0.24 && x < 0.36)) 1f else 0f
            }
            3 -> { // burbujas: sube y baja suave
                val p = lerp(5.0f, 1.3f, sp)
                val k = 0.5 - 0.5 * cos(2 * PI * t / p)
                val v = 0.06 + 0.94 * k
                ((v * 20).roundToInt() / 20.0).toFloat()
            }
            else -> 1f
        }
    }

    private fun step() {
        if (Bus.musicRunning || paused || !LightStick.connected) return
        val t = (SystemClock.elapsedRealtime() - t0) / 1000.0
        val k = level(kind, speed, t)
        LightStick.setLed(1, (r * k).toInt(), (g * k).toInt(), (b * k).toInt())
    }
}
