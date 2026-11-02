package app.njsmusic

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Paletas de colores (RGB como 0xRRGGBB). */
object Palettes {
    /** Los 6 colores del lightstick: blanco, verde, rosa, azul, naranja, amarillo. */
    val NEWJEANS = intArrayOf(0xFFFFFF, 0x00FF40, 0xFF2D9B, 0x1E50FF, 0xFF5A00, 0xFFE000)

    val MEMBER_NAMES = arrayOf("Minji", "Hanni", "Danielle", "Haerin", "Hyein")

    /** Minji azul, Hanni rosa, Danielle amarillo, Haerin verde, Hyein morado. */
    val MEMBER_COLORS = intArrayOf(0x1E50FF, 0xFF2D9B, 0xFFE000, 0x00FF40, 0x9B30FF)

    /**
     * Estilos del modo música:
     * 0 = Arcoíris, 1 = NewJeans (los 6 colores), 2..6 = una integrante (un solo color).
     */
    fun byId(id: Int): IntArray? = when (id) {
        0 -> null
        in 2..6 -> intArrayOf(MEMBER_COLORS[id - 2])
        else -> NEWJEANS
    }
}

class AnalyzerOut(val r: Int, val g: Int, val b: Int, val beat: Boolean)

/**
 * Convierte bloques de audio (1024 muestras mono a 44.1 kHz, valores -1..1)
 * en un color RGB que sigue el ritmo. Es la misma logica del script de Windows.
 */
class MusicAnalyzer(
    sampleRate: Int = 44100,
    private val block: Int = 1024,
    var minBrightness: Float = 0.04f,
    private val palette: IntArray? = null,
    private val hueSpeed: Float = 0.0015f,
    private val beatHueJump: Float = 0.11f
) {
    private val window = FloatArray(block) { (0.5 - 0.5 * cos(2.0 * PI * it / (block - 1))).toFloat() }
    private val re = DoubleArray(block)
    private val im = DoubleArray(block)
    private val cosT = DoubleArray(block / 2) { cos(2.0 * PI * it / block) }
    private val sinT = DoubleArray(block / 2) { -sin(2.0 * PI * it / block) }
    private val rev = IntArray(block)

    // rangos de bins (frecuencia = i * sampleRate / block)
    private val bassLo: Int
    private val bassHi: Int
    private val midLo: Int
    private val midHi: Int
    private val highLo: Int
    private val highHi: Int

    private var peak = 1e-6f
    private var bassAvg = 0f
    private var env = 0f
    private var hue = 0f
    private var lastBeat = -100.0
    private var beatFlash = 0f
    private var palIdx = 0

    init {
        var bits = 0
        while ((1 shl bits) < block) bits++
        for (i in 0 until block) {
            var x = i
            var r = 0
            for (k in 0 until bits) {
                r = (r shl 1) or (x and 1)
                x = x shr 1
            }
            rev[i] = r
        }
        val half = block / 2 + 1
        fun firstBin(hz: Double): Int {
            for (i in 0 until half) if (i.toDouble() * sampleRate / block >= hz) return i
            return half
        }
        bassLo = firstBin(30.0)
        bassHi = firstBin(160.0)
        midLo = bassHi
        midHi = firstBin(2000.0)
        highLo = midHi
        highHi = firstBin(9000.0)
    }

    private fun fft() {
        val n = block
        for (i in 0 until n) {
            val j = rev[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var size = 2
        while (size <= n) {
            val halfSize = size / 2
            val step = n / size
            var start = 0
            while (start < n) {
                var k = 0
                for (j in start until start + halfSize) {
                    val wr = cosT[k]
                    val wi = sinT[k]
                    val l = j + halfSize
                    val xr = re[l] * wr - im[l] * wi
                    val xi = re[l] * wi + im[l] * wr
                    re[l] = re[j] - xr
                    im[l] = im[j] - xi
                    re[j] += xr
                    im[j] += xi
                    k += step
                }
                start += size
            }
            size *= 2
        }
    }

    private fun meanMag(lo: Int, hi: Int): Float {
        if (hi <= lo) return 0f
        var s = 0.0
        for (i in lo until hi) s += sqrt(re[i] * re[i] + im[i] * im[i])
        return (s / (hi - lo)).toFloat()
    }

    /** samples: [block] valores float -1..1. nowSec: reloj en segundos. */
    fun process(samples: FloatArray, nowSec: Double): AnalyzerOut {
        for (i in 0 until block) {
            re[i] = (samples[i] * window[i]).toDouble()
            im[i] = 0.0
        }
        fft()
        val bass = meanMag(bassLo, bassHi)
        val mid = meanMag(midLo, midHi)
        val high = meanMag(highLo, highHi)

        val loud = bass + mid * 0.6f + high * 0.4f
        peak = max(max(loud, peak * 0.997f), 1e-6f)
        val level = min(loud / peak, 1f)
        val bassN = min(bass / peak * 2f, 1f)

        var beat = false
        if (bassN > 0.25f && bassN > bassAvg * 1.35f && nowSec - lastBeat > 0.17) {
            beat = true
            lastBeat = nowSec
            hue = (hue + beatHueJump) % 1f
            if (palette != null) palIdx = (palIdx + 1) % palette.size
            beatFlash = 1f
        }
        bassAvg = bassAvg * 0.92f + bassN * 0.08f

        val target = 0.25f * level + 0.75f * bassN
        env = if (target > env) target else env * 0.86f + target * 0.14f
        beatFlash *= 0.80f
        val brightness = max(minBrightness, min(1f, env + 0.35f * beatFlash))

        if (palette != null) {
            val c = palette[palIdx]
            return AnalyzerOut(
                (((c shr 16) and 255) * brightness).toInt(),
                (((c shr 8) and 255) * brightness).toInt(),
                ((c and 255) * brightness).toInt(),
                beat
            )
        }

        hue = (hue + hueSpeed) % 1f
        val sat = 1f - 0.35f * min(high / peak * 3f, 1f)
        return hsvToRgb(hue, sat, brightness, beat)
    }

    private fun hsvToRgb(h: Float, s: Float, v: Float, beat: Boolean): AnalyzerOut {
        val i = (h * 6f).toInt()
        val f = h * 6f - i
        val p = v * (1f - s)
        val q = v * (1f - f * s)
        val t = v * (1f - (1f - f) * s)
        val (r, g, b) = when (i % 6) {
            0 -> Triple(v, t, p)
            1 -> Triple(q, v, p)
            2 -> Triple(p, v, t)
            3 -> Triple(p, q, v)
            4 -> Triple(t, p, v)
            else -> Triple(v, p, q)
        }
        return AnalyzerOut((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt(), beat)
    }
}

/** Utilidad: diferencia total entre dos colores, para no mandar cambios minusculos. */
fun colorDiff(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Int =
    abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2)
