package app.njsmusic

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Paletas de colores (0xRRGGBB).
 * *_UI = como se ven en la pantalla (paleta estilo Phoning).
 * Sin sufijo = lo que se manda al LED (más saturado, porque en el LED los pasteles se ven blancos).
 */
object Palettes {
    val NEWJEANS_NAMES = arrayOf("Blanco", "Verde", "Rosa", "Azul", "Naranja", "Amarillo")
    val NEWJEANS_UI = intArrayOf(0xFFFFFF, 0x5DE101, 0xFF5DFF, 0x6CAAFF, 0xFF9F45, 0xF6FF02)
    val NEWJEANS = intArrayOf(0xFFFFFF, 0x3CFF00, 0xFF1ED2, 0x1E5AFF, 0xFF5A00, 0xFFE600)

    val MEMBER_NAMES = arrayOf("Minji", "Hanni", "Danielle", "Haerin", "Hyein")
    /** Minji azul, Hanni rosa, Danielle amarillo, Haerin verde, Hyein morado. */
    val MEMBER_UI = intArrayOf(0x6CAAFF, 0xFF5DFF, 0xF6FF02, 0x5DE101, 0xAC86FF)
    val MEMBER_COLORS = intArrayOf(0x1E5AFF, 0xFF1ED2, 0xFFE600, 0x3CFF00, 0x8C3CFF)

    /** 0 = Arcoíris, 1 = NewJeans (los 6 colores), 2..6 = una integrante (un solo color). */
    fun byId(id: Int): IntArray? = when (id) {
        0 -> null
        in 2..6 -> intArrayOf(MEMBER_COLORS[id - 2])
        else -> NEWJEANS
    }
}

/**
 * Canciones con BPM de referencia (detectores automáticos; pueden variar un poco).
 * Elegir una ayuda a fijar el tempo en lugares ruidosos. 0 = detectar solo.
 */
object Songs {
    val LIST: List<Pair<String, Float>> = listOf(
        "Attention" to 105f, "Hype Boy" to 100f, "Cookie" to 157f, "Hurt" to 180f,
        "OMG" to 127f, "Ditto" to 134f,
        "New Jeans" to 134f, "Super Shy" to 150f, "ETA" to 135f, "Cool With You" to 135f,
        "Get Up" to 85f, "ASAP" to 130f,
        "Zero" to 172f, "Gods" to 146f, "How Sweet" to 125f, "Bubble Gum" to 105f,
        "Supernatural" to 111f, "Our Night Is More Beautiful Than Your Day" to 105f
    )
}

class AnalyzerOut(
    val r: Int, val g: Int, val b: Int,
    /** true en el instante de un golpe (se manda con prioridad). */
    val beat: Boolean,
    /** tempo detectado (0 = aún no). */
    val bpm: Float,
    /** true cuando el tempo está fijado y los golpes se predicen. */
    val locked: Boolean
)

/**
 * Analizador de ritmo para el modo música.
 *
 * 1. Detecta "ataques" (onsets) con el flujo espectral: cuánto sube la energía en cada banda.
 * 2. Calcula el tempo (BPM) con autocorrelación de esos ataques en los últimos ~6 s.
 * 3. Cuando el tempo es estable, PREDICE el siguiente golpe y dispara el destello a tiempo
 *    (y un poco antes, [leadSec], para compensar el retraso del micrófono y del Bluetooth).
 * 4. Visual: destello fuerte en cada golpe que se desvanece antes del siguiente; el color cambia
 *    con los golpes. Así se usan pocos comandos y cada uno cuenta.
 *
 * Entrada: bloques de [hopSize] muestras mono (-1..1) a [sampleRate] Hz.
 */
class MusicAnalyzer(
    private val sampleRate: Int = 44100,
    private val hopSize: Int = 512,
    var minBrightness: Float = 0.04f,
    private val palette: IntArray? = null,
    songBpm: Float = 0f,
    var leadSec: Double = 0.06,
    /** true = modo Reactivo (solo reacciona al sonido, sin seguir el tempo). */
    private val reactive: Boolean = false
) {
    private val n = 1024
    private val hop = hopSize.toDouble() / sampleRate
    private val buf = FloatArray(n)
    private val window = FloatArray(n) { (0.5 - 0.5 * cos(2.0 * PI * it / (n - 1))).toFloat() }
    private val re = DoubleArray(n)
    private val im = DoubleArray(n)
    private val cosT = DoubleArray(n / 2) { cos(2.0 * PI * it / n) }
    private val sinT = DoubleArray(n / 2) { -sin(2.0 * PI * it / n) }
    private val rev = IntArray(n)

    private val half = n / 2 + 1
    private val lo: Int
    private val hi: Int
    private val prevLog = FloatArray(half)
    private val bandWeight = FloatArray(half)

    // historial de ataques (~6 s)
    private val histLen = (6.0 / hop).toInt()
    private val hist = FloatArray(histLen)
    private var histPos = 0
    private var histCount = 0
    private val thrWin = (0.45 / hop).toInt()

    // tempo
    private val minLag: Int
    private val maxLag: Int
    private val priorCenter: Double
    private val priorSigma: Double
    private var bpm = 0.0
    private var period = 0.0
    private var conf = 0.0
    private var locked = false
    private var candidate = 0.0
    private var candidateHits = 0
    private var framesToTempo = 0
    private val acBuf: DoubleArray
    private val lockConf: Double

    // fase
    private var nextBeat = -1.0
    private var lastOnset = -10.0
    private var o1 = 0f
    private var o2 = 0f
    private var beatCount = 0
    private var lastFired = -10.0
    /** última vez que un ataque real coincidió con un golpe predicho */
    private var lastConfirm = -10.0
    /** el tempo está confirmado por golpes reales recientes */
    private var trusted = false
    /** envolvente lenta de la fuerza de los ataques (para ignorar cosas suaves en un puente) */
    private var onsetPeak = 1e-6f

    // volumen
    private var levelPeak = 1e-6
    private var level = 0f
    private var quiet = true

    // visual
    private var flash = 0f
    private var floorLevel = 0f
    private var hue = 0f
    private var palIdx = 0
    private var sparkle = 0f

    init {
        var bits = 0
        while ((1 shl bits) < n) bits++
        for (i in 0 until n) {
            var x = i
            var r = 0
            for (k in 0 until bits) {
                r = (r shl 1) or (x and 1)
                x = x shr 1
            }
            rev[i] = r
        }
        fun bin(hz: Double): Int = min(half - 1, (hz * n / sampleRate).roundToInt())
        lo = max(1, bin(30.0))
        hi = bin(9000.0)
        val bassHi = bin(160.0)
        val midHi = bin(2000.0)
        for (k in 0 until half) {
            bandWeight[k] = when {
                k < bassHi -> 1.6f
                k < midHi -> 1.0f
                else -> 0.55f
            }
        }
        val fixed = songBpm > 0f
        val minBpm = if (fixed) songBpm * 0.95 else 70.0
        val maxBpm = if (fixed) songBpm * 1.05 else 185.0
        priorCenter = if (fixed) songBpm.toDouble() else AUTO_CENTER
        lockConf = if (fixed) 0.8 else 2.0
        priorSigma = if (fixed) 0.05 else AUTO_SIGMA
        minLag = max(2, (60.0 / (maxBpm * hop)).toInt())
        maxLag = ceil(60.0 / (minBpm * hop)).toInt() + 1
        acBuf = DoubleArray(maxLag * 4 + 3)
    }

    companion object {
        // parámetros del detector de tempo automático (ajustados con canciones de prueba)
        var AUTO_CENTER = 135.0
        var AUTO_SIGMA = 0.45
        var H2 = 0.5
        var H4 = 0.0
    }

    private fun fft() {
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

    private fun histAt(back: Int): Float {
        // back = 0 -> el más reciente
        var i = histPos - 1 - back
        while (i < 0) i += histLen
        return hist[i]
    }

    /** Autocorrelación de los ataques para encontrar el tempo. */
    private fun estimateTempo() {
        val w = histCount
        if (w < (3.0 / hop).toInt()) return
        // serie en orden temporal, sin la media
        var mean = 0.0
        for (b in 0 until w) mean += histAt(b)
        mean /= w
        fun y(b: Int): Double = max(0.0, histAt(b) - mean)

        val top = min(maxLag * 4 + 2, w - 2)
        for (lag in 1..top) {
            var s = 0.0
            for (b in 0 until w - lag) s += y(b) * y(b + lag)
            acBuf[lag] = s / (w - lag)
        }
        var best = -1
        var bestScore = -1e9
        var sum = 0.0
        var sum2 = 0.0
        var cnt = 0
        val scores = DoubleArray(maxLag + 2)
        for (lag in minLag..min(maxLag, top)) {
            // pulso + medio compás + compás: el patrón se repite cada compás de 4 tiempos
            var sc = acBuf[lag]
            if (lag * 2 <= top) sc += H2 * acBuf[lag * 2]
            if (lag * 4 <= top) sc += H4 * acBuf[lag * 4]
            val bpmL = 60.0 / (lag * hop)
            val d = ln(bpmL / priorCenter) / ln(2.0) / priorSigma
            sc *= exp(-0.5 * d * d)
            scores[lag] = sc
            sum += sc
            sum2 += sc * sc
            cnt++
            if (sc > bestScore) {
                bestScore = sc
                best = lag
            }
        }
        if (best < 0 || cnt < 3) return
        val mu = sum / cnt
        val sd = sqrt(max(1e-12, sum2 / cnt - mu * mu))
        conf = (bestScore - mu) / sd

        // refinar con interpolación parabólica
        var lagF = best.toDouble()
        if (best > minLag && best < min(maxLag, top)) {
            val a = scores[best - 1]
            val b = scores[best]
            val c = scores[best + 1]
            val den = a - 2 * b + c
            if (abs(den) > 1e-12) lagF += (0.5 * (a - c) / den).coerceIn(-0.5, 0.5)
        }
        val cand = 60.0 / (lagF * hop)

        if (bpm == 0.0 || abs(cand - bpm) / bpm < 0.04) {
            bpm = if (bpm == 0.0) cand else bpm * 0.8 + cand * 0.2
            candidateHits = 0
        } else {
            if (candidate > 0 && abs(cand - candidate) / candidate < 0.04) candidateHits++ else {
                candidate = cand
                candidateHits = 1
            }
            if (candidateHits >= 4) {
                bpm = cand
                candidateHits = 0
                nextBeat = -1.0
            }
        }
        period = 60.0 / bpm
        val wasLocked = locked
        locked = conf > lockConf
        if (locked && !wasLocked) nextBeat = -1.0
    }

    /**
     * Fase del pulso: prueba todos los desfases posibles y se queda con el que mejor coincide
     * con los ataques de los últimos segundos (filtro de peine). Devuelve la hora del último golpe.
     */
    private fun estimatePhase(nowSec: Double): Double {
        val pF = period / hop
        if (pF < 2) return -1.0
        val reach = min(histCount - 2, (4.0 / hop).toInt())
        var bestPhi = 0
        var bestSc = -1.0
        val phiMax = ceil(pF).toInt()
        for (phi in 0 until phiMax) {
            var sc = 0.0
            var k = 0
            while (true) {
                val pos = phi + k * pF
                if (pos > reach) break
                val i0 = pos.toInt()
                val f = pos - i0
                // ventana de ±1 cuadro para tolerar pequeñas variaciones
                val v = (1 - f) * histAt(i0) + f * histAt(i0 + 1)
                val vm = max(v, max(histAt(max(0, i0 - 1)).toDouble(), histAt(i0 + 2).toDouble()) * 0.7)
                sc += vm
                k++
            }
            if (k > 0) sc /= k
            if (sc > bestSc) {
                bestSc = sc
                bestPhi = phi
            }
        }
        // histAt(b) corresponde al bloque que terminó hace b cuadros
        return nowSec - bestPhi * hop
    }

    /** Procesa [count] muestras nuevas. nowSec = reloj en segundos. */
    fun process(samples: FloatArray, count: Int, nowSec: Double): AnalyzerOut {
        val c = min(count, n)
        System.arraycopy(buf, c, buf, 0, n - c)
        System.arraycopy(samples, 0, buf, n - c, c)
        for (i in 0 until n) {
            re[i] = (buf[i] * window[i]).toDouble()
            im[i] = 0.0
        }
        fft()

        var flux = 0.0
        var energy = 0.0
        for (k in lo until hi) {
            val mag = sqrt(re[k] * re[k] + im[k] * im[k])
            energy += mag * mag
            val lm = ln(1.0 + 20.0 * mag).toFloat()
            val d = lm - prevLog[k]
            if (d > 0) flux += d * bandWeight[k]
            prevLog[k] = lm
        }
        val o = (flux / (hi - lo)).toFloat()

        // volumen relativo (control automático de ganancia)
        val loud = sqrt(energy / (hi - lo))
        levelPeak = max(max(loud, levelPeak * 0.9996), 1e-6)
        level = (loud / levelPeak).toFloat().coerceIn(0f, 1f)
        quiet = level < 0.05f || loud < 0.01

        // historial
        hist[histPos] = o
        histPos = (histPos + 1) % histLen
        if (histCount < histLen) histCount++

        // umbral adaptable: media + 1.2·desviación de los últimos ~0.45 s
        val wN = min(thrWin, histCount)
        var m = 0.0
        var m2 = 0.0
        for (b in 0 until wN) {
            val v = histAt(b).toDouble()
            m += v
            m2 += v * v
        }
        m /= wN
        val sd = sqrt(max(0.0, m2 / wN - m * m))
        val thr = (m + 1.2 * sd).toFloat()

        // pico en el cuadro anterior (1 cuadro de retraso)
        val tPeak = nowSec - hop
        val isOnset = !quiet && o1 > thr && o1 >= o2 && o1 >= o && tPeak - lastOnset > 0.10
        val strength = if (isOnset) min(2f, o1 / max(thr, 1e-6f)) else 0f
        if (isOnset) onsetPeak = max(onsetPeak, o1)
        onsetPeak = max(1e-6f, onsetPeak * 0.9992f)
        val bigEnough = o1 > 0.40f * onsetPeak
        if (isOnset) lastOnset = tPeak
        o2 = o1
        o1 = o

        // tempo y fase cada ~0.2 s
        if (--framesToTempo <= 0) {
            framesToTempo = (0.2 / hop).toInt()
            estimateTempo()
            if (locked && period > 0) {
                val lastBeat = estimatePhase(nowSec)
                if (lastBeat > 0) {
                    val predicted = lastBeat + period * ceil((nowSec - lastBeat) / period + 1e-9)
                    if (nextBeat < 0) {
                        nextBeat = predicted
                    } else {
                        var d = predicted - nextBeat
                        while (d > period / 2) d -= period
                        while (d < -period / 2) d += period
                        nextBeat += 0.5 * d
                    }
                }
            }
        }

        // ---- golpes
        var fired = false
        // Protección: si en ~2.5 s ningún golpe real confirmó el tempo (puente, intro, cambio),
        // se deja de predecir y se reacciona al sonido hasta volver a confirmarlo.
        trusted = !reactive && locked && period > 0 && nowSec - lastConfirm < max(2.5, 4 * period)
        if (!reactive && locked && period > 0) {
            if (nextBeat < 0) {
                val lb = estimatePhase(nowSec)
                val base = if (lb > 0) lb else nowSec
                nextBeat = base + period * ceil((nowSec - base) / period + 1e-9)
            }
            if (isOnset) {
                // corregir la fase hacia el ataque real más cercano
                var pb = nextBeat
                while (pb - tPeak > period / 2) pb -= period
                while (tPeak - pb > period / 2) pb += period
                val err = tPeak - pb
                if (abs(err) < 0.15 * period) {
                    nextBeat += 0.15 * err
                    if (strength > 1.1f && bigEnough) lastConfirm = tPeak
                }
            }
            while (nowSec >= nextBeat - leadSec) {
                if (trusted && nowSec - (nextBeat - leadSec) < 2 * hop && nowSec - lastFired > period * 0.5) fired = true
                nextBeat += period
            }
        }
        if (!trusted && isOnset && strength > (if (reactive) 1.15f else 1.25f) && (reactive || bigEnough) && tPeak - lastFired > 0.16) {
            fired = true
        }
        if (quiet) fired = false
        if (fired) lastFired = nowSec

        // ---- visual
        if (fired) {
            beatCount++
            val accent = beatCount % 4 == 1
            flash = if (trusted) {
                // en partes suaves, destello más suave
                0.55f + 0.45f * min(1f, level / 0.35f)
            } else {
                0.6f + 0.4f * min(1f, strength / 1.6f)
            }
            if (palette != null) palIdx = (palIdx + 1) % palette.size
            hue = (hue + if (accent) 0.17f else 0.07f) % 1f
            sparkle = if (accent && palette != null && palette.size == 1) 0.35f else 0f
        }
        val tau = if (period > 0) (0.30 * period).coerceIn(0.07, 0.22) else 0.12
        flash *= exp(-hop / tau).toFloat()
        sparkle *= 0.6f
        floorLevel = floorLevel * 0.92f + level * 0.08f
        val follow = if (reactive || !trusted) 0.40f else 0.22f
        val base = minBrightness + follow * floorLevel * (1f - minBrightness)
        var bright = max(base, flash).coerceIn(0f, 1f)
        bright = ((bright * 16f).roundToInt() / 16f).coerceIn(minBrightness, 1f)

        val col = if (palette != null) palette[palIdx] else hsv(hue, 1f)
        var r = ((col shr 16) and 255) * bright
        var g = ((col shr 8) and 255) * bright
        var b = (col and 255) * bright
        if (sparkle > 0.05f) {
            r += (255 - r) * sparkle
            g += (255 - g) * sparkle
            b += (255 - b) * sparkle
        }
        return AnalyzerOut(r.toInt(), g.toInt(), b.toInt(), fired, if (bpm > 0 && !reactive) bpm.toFloat() else 0f, trusted)
    }

    private fun hsv(h: Float, s: Float): Int {
        val i = (h * 6f).toInt()
        val f = h * 6f - i
        val p = 1f - s
        val q = 1f - f * s
        val t = 1f - (1f - f) * s
        val (r, g, b) = when (i % 6) {
            0 -> Triple(1f, t, p)
            1 -> Triple(q, 1f, p)
            2 -> Triple(p, 1f, t)
            3 -> Triple(p, q, 1f)
            4 -> Triple(t, p, 1f)
            else -> Triple(1f, p, q)
        }
        return ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
    }
}

/** Diferencia total entre dos colores, para no mandar cambios minúsculos. */
fun colorDiff(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Int =
    abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2)
