package app.njsmusic

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// =====================================================================================
// Tema: cielo pastel, burbujas, Y2K
// =====================================================================================
object Th {
    val SKY_TOP = 0xFFBFE1FF.toInt()
    val SKY_MID = 0xFFE6F3FF.toInt()
    val SKY_BOTTOM = 0xFFFFE4F1.toInt()
    val NAVY = 0xFF1B2A6B.toInt()
    val NAVY_SOFT = 0xFF6170A3.toInt()
    val BLUE = 0xFF4A74FF.toInt()
    val PINK = 0xFFFF7EB9.toInt()
    val CARD = 0xEFFFFFFF.toInt()
    val CARD_SOFT = 0x99FFFFFF.toInt()
    val LINE = 0x261B2A6B
    val GREEN_OK = 0xFF2ED47A.toInt()
    val AMBER = 0xFFFFB443.toInt()
    val GREY = 0xFFB4BCD6.toInt()

    var regular: Typeface = Typeface.DEFAULT
    var bold: Typeface = Typeface.DEFAULT_BOLD

    /** Carga la fuente redondeada (Fredoka, licencia OFL). Si falla, usa la del sistema. */
    fun init(ctx: Context) {
        try {
            regular = Typeface.Builder(ctx.assets, "fonts/fredoka.ttf")
                .setFontVariationSettings("'wght' 450").build() ?: Typeface.DEFAULT
            bold = Typeface.Builder(ctx.assets, "fonts/fredoka.ttf")
                .setFontVariationSettings("'wght' 640").build() ?: Typeface.DEFAULT_BOLD
        } catch (e: Exception) {
            regular = Typeface.DEFAULT
            bold = Typeface.DEFAULT_BOLD
        }
    }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

/** Mezcla un color con blanco (f = 0..1). */
fun lighten(c: Int, f: Float): Int {
    val r = Color.red(c) + ((255 - Color.red(c)) * f).toInt()
    val g = Color.green(c) + ((255 - Color.green(c)) * f).toInt()
    val b = Color.blue(c) + ((255 - Color.blue(c)) * f).toInt()
    return Color.rgb(r, g, b)
}

fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or ((a and 255) shl 24)

fun opaque(rgb: Int): Int = rgb or 0xFF000000.toInt()

fun roundRect(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
        if (stroke > 0) setStroke(stroke, strokeColor)
    }

fun gradientPill(radius: Float, vararg colors: Int): GradientDrawable =
    GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
        cornerRadius = radius
    }

/** Efecto de toque: onda + pequeño rebote. */
fun View.touchFx(radius: Float) {
    val mask = roundRect(Color.WHITE, radius)
    foreground = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, mask)
    setOnTouchListener { v, e ->
        when (e.action) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
        }
        false
    }
}

fun Context.label(t: String, sp: Float, bold: Boolean = false, color: Int = Th.NAVY): TextView =
    TextView(this).apply {
        text = t
        textSize = sp
        typeface = if (bold) Th.bold else Th.regular
        setTextColor(color)
    }

/** Tarjeta blanca redondeada con sombra suave. */
fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = roundRect(Th.CARD, dpf(28f), dp(2), Color.WHITE)
    elevation = dpf(5f)
    setPadding(dp(18), dp(16), dp(18), dp(18))
}

fun Context.cardTitle(t: String, sub: String? = null): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    addView(label(t, 19f, true))
    if (sub != null) addView(label(sub, 12.5f, false, Th.NAVY_SOFT))
    setPadding(0, 0, 0, dp(10))
}

fun Context.pillButton(t: String, onClick: () -> Unit): TextView = TextView(this).apply {
    text = t
    textSize = 17f
    typeface = Th.bold
    gravity = Gravity.CENTER
    setTextColor(Color.WHITE)
    background = gradientPill(dpf(30f), Th.BLUE, Th.PINK)
    elevation = dpf(4f)
    setPadding(dp(20), dp(14), dp(20), dp(14))
    isClickable = true
    setOnClickListener { onClick() }
    touchFx(dpf(30f))
}

/** Burbuja de chat (estilo mensajería). */
fun Context.chatBubble(t: String, mine: Boolean = false): TextView = TextView(this).apply {
    text = t
    textSize = 14f
    typeface = Th.regular
    setLineSpacing(0f, 1.1f)
    setPadding(dp(14), dp(10), dp(14), dp(10))
    val big = dpf(20f)
    val small = dpf(5f)
    val bg = if (mine) {
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Th.BLUE, Th.PINK))
    } else {
        GradientDrawable().apply { setColor(Color.WHITE) }
    }
    bg.cornerRadii = if (mine) {
        floatArrayOf(big, big, big, big, small, small, big, big)
    } else {
        floatArrayOf(big, big, big, big, big, big, small, small)
    }
    background = bg
    setTextColor(if (mine) Color.WHITE else Th.NAVY)
    elevation = dpf(2f)
}

fun sparklePath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    moveTo(cx, cy - r)
    quadTo(cx, cy, cx + r, cy)
    quadTo(cx, cy, cx, cy + r)
    quadTo(cx, cy, cx - r, cy)
    quadTo(cx, cy, cx, cy - r)
    close()
}

// =====================================================================================
// Fondo: cielo con burbujas y destellos
// =====================================================================================
class SkyBackground(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bg: Shader? = null
    private val rnd = Random(11)
    private val bubbles = Array(13) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), 0.04f + rnd.nextFloat() * 0.09f) }
    private val sparkles = Array(14) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), 0.010f + rnd.nextFloat() * 0.018f) }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        bg = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(Th.SKY_TOP, Th.SKY_MID, Th.SKY_BOTTOM), floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.style = Paint.Style.FILL
        p.shader = bg
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        for (b in bubbles) {
            val x = b[0] * w
            val y = b[1] * h
            val r = b[2] * w
            p.style = Paint.Style.FILL
            p.color = 0x2EFFFFFF
            c.drawCircle(x, y, r, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = context.dpf(1.4f)
            p.color = 0x80FFFFFF.toInt()
            c.drawCircle(x, y, r, p)
            p.style = Paint.Style.FILL
            p.color = 0x99FFFFFF.toInt()
            c.drawCircle(x - r * 0.38f, y - r * 0.38f, r * 0.16f, p)
        }
        p.style = Paint.Style.FILL
        for (s in sparkles) {
            p.color = 0xD9FFFFFF.toInt()
            c.drawPath(sparklePath(s[0] * w, s[1] * h, s[2] * w), p)
        }
    }
}

// =====================================================================================
// Orbe de luz: vista previa del color del lightstick
// =====================================================================================
class Orb(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var color: Int = opaque(0x1E50FF)
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f
        val col = color
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(withAlpha(col, 0xB0), withAlpha(col, 0x45), withAlpha(col, 0)),
            floatArrayOf(0.30f, 0.62f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, p)
        val core = r * 0.44f
        p.shader = RadialGradient(
            cx - core * 0.25f, cy - core * 0.3f, core * 1.2f,
            intArrayOf(lighten(col, 0.65f), col), null, Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, core, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = context.dpf(2.5f)
        p.color = 0xCCFFFFFF.toInt()
        c.drawCircle(cx, cy, core, p)
        p.style = Paint.Style.FILL
        p.color = 0xA6FFFFFF.toInt()
        c.drawOval(RectF(cx - core * 0.55f, cy - core * 0.72f, cx - core * 0.05f, cy - core * 0.40f), p)
        p.color = 0xE6FFFFFF.toInt()
        c.drawPath(sparklePath(cx + core * 0.95f, cy - core * 0.85f, core * 0.22f), p)
    }
}

// =====================================================================================
// Slider tipo píldora (color, brillo...)
// =====================================================================================
class PillSlider(ctx: Context, private val onChange: (Float) -> Unit) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shader: Shader? = null
    var colors: IntArray = intArrayOf(Color.BLACK, Color.WHITE)
        set(v) {
            field = v
            shader = null
            invalidate()
        }
    var value = 0.5f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        shader = null
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val trackH = h * 0.62f
        val top = (h - trackH) / 2f
        val r = trackH / 2f
        if (shader == null) {
            shader = LinearGradient(r, 0f, w - r, 0f, colors, null, Shader.TileMode.CLAMP)
        }
        p.style = Paint.Style.FILL
        p.shader = shader
        c.drawRoundRect(0f, top, w, top + trackH, r, r, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = context.dpf(2f)
        p.color = Color.WHITE
        c.drawRoundRect(0f, top, w, top + trackH, r, r, p)

        val x = r + value * (w - 2 * r)
        val tr = h / 2f - context.dpf(2f)
        p.style = Paint.Style.FILL
        p.color = 0x2A1B2A6B
        c.drawCircle(x, h / 2f + context.dpf(2f), tr, p)
        p.color = Color.WHITE
        c.drawCircle(x, h / 2f, tr, p)
        p.color = Th.LINE
        p.style = Paint.Style.STROKE
        p.strokeWidth = context.dpf(1f)
        c.drawCircle(x, h / 2f, tr, p)
        p.style = Paint.Style.FILL
        p.color = 0xFFE9EEFF.toInt()
        c.drawCircle(x, h / 2f, tr * 0.38f, p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
            parent?.requestDisallowInterceptTouchEvent(true)
            val r = height * 0.31f
            value = (e.x - r) / (width - 2 * r)
            onChange(value)
        }
        return true
    }
}

// =====================================================================================
// Burbuja de color (botón de color)
// =====================================================================================
class ColorBubble(ctx: Context, val rgb: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var chosen = false
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val ring = min(width, height) / 2f - context.dpf(1.5f)
        val r = ring - context.dpf(5f)
        if (chosen) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = context.dpf(2.6f)
            p.color = Th.NAVY
            c.drawCircle(cx, cy, ring, p)
        }
        val col = opaque(rgb)
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            cx - r * 0.3f, cy - r * 0.35f, r * 1.25f,
            intArrayOf(lighten(col, 0.7f), col), null, Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = context.dpf(2f)
        p.color = if (rgb == 0xFFFFFF) Th.LINE else 0xCCFFFFFF.toInt()
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.FILL
        p.color = 0xB3FFFFFF.toInt()
        c.drawOval(RectF(cx - r * 0.6f, cy - r * 0.68f, cx - r * 0.1f, cy - r * 0.38f), p)
    }
}

/** Burbuja + nombre debajo. */
fun Context.swatch(name: String, rgb: Int, sizeDp: Int, out: MutableList<ColorBubble>, onClick: () -> Unit): LinearLayout {
    val col = LinearLayout(this)
    col.orientation = LinearLayout.VERTICAL
    col.gravity = Gravity.CENTER_HORIZONTAL
    val b = ColorBubble(this, rgb)
    out.add(b)
    col.addView(b, LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)))
    val t = label(name, 12f, false, Th.NAVY)
    t.gravity = Gravity.CENTER
    col.addView(t)
    col.isClickable = true
    col.setOnClickListener { onClick() }
    col.touchFx(dpf(20f))
    return col
}

// =====================================================================================
// Íconos de efecto (en el orden de la app oficial)
// =====================================================================================
class EffectIcon(ctx: Context, private val kind: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var tint: Int = Th.NAVY
        set(v) {
            field = v
            invalidate()
        }

    private fun bolt(ox: Float, oy: Float, s: Float): Path = Path().apply {
        moveTo(ox + 0.58f * s, oy + 0.02f * s)
        lineTo(ox + 0.16f * s, oy + 0.56f * s)
        lineTo(ox + 0.44f * s, oy + 0.56f * s)
        lineTo(ox + 0.34f * s, oy + 0.98f * s)
        lineTo(ox + 0.84f * s, oy + 0.40f * s)
        lineTo(ox + 0.56f * s, oy + 0.40f * s)
        lineTo(ox + 0.70f * s, oy + 0.02f * s)
        close()
    }

    override fun onDraw(c: Canvas) {
        val s = min(width, height).toFloat()
        val ox = (width - s) / 2f
        val oy = (height - s) / 2f
        p.color = tint
        p.strokeCap = Paint.Cap.ROUND
        when (kind) {
            0 -> { // destellos (luz encendida)
                p.style = Paint.Style.STROKE
                p.strokeWidth = s * 0.08f
                val cx = ox + s / 2f
                val cy = oy + s / 2f
                for (i in 0 until 8) {
                    val a = i * PI / 4
                    val r1 = s * 0.16f
                    val r2 = s * 0.40f
                    c.drawLine(
                        cx + (cos(a) * r1).toFloat(), cy + (sin(a) * r1).toFloat(),
                        cx + (cos(a) * r2).toFloat(), cy + (sin(a) * r2).toFloat(), p
                    )
                }
            }
            1 -> { // destello doble
                p.style = Paint.Style.FILL
                c.drawPath(bolt(ox - s * 0.06f, oy + s * 0.10f, s * 0.78f), p)
                c.drawPath(bolt(ox + s * 0.42f, oy, s * 0.52f), p)
            }
            2 -> { // destello
                p.style = Paint.Style.FILL
                c.drawPath(bolt(ox + s * 0.08f, oy + s * 0.04f, s * 0.9f), p)
            }
            else -> { // burbujas
                p.style = Paint.Style.STROKE
                p.strokeWidth = s * 0.08f
                c.drawCircle(ox + s * 0.36f, oy + s * 0.64f, s * 0.20f, p)
                c.drawCircle(ox + s * 0.68f, oy + s * 0.52f, s * 0.15f, p)
                c.drawCircle(ox + s * 0.50f, oy + s * 0.22f, s * 0.11f, p)
            }
        }
    }
}

class EffectCard(ctx: Context, kind: Int, title: String, val mode: Int) : LinearLayout(ctx) {
    private val icon = EffectIcon(ctx, kind)
    private val text = ctx.label(title, 13.5f, true)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(ctx.dp(8), ctx.dp(14), ctx.dp(8), ctx.dp(12))
        addView(icon, LayoutParams(ctx.dp(40), ctx.dp(40)))
        text.gravity = Gravity.CENTER
        text.setPadding(0, ctx.dp(6), 0, 0)
        addView(text)
        isClickable = true
        touchFx(ctx.dpf(22f))
        setChosen(false)
    }

    fun setChosen(b: Boolean) {
        if (b) {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Th.BLUE, Th.PINK)).apply {
                cornerRadius = context.dpf(22f)
            }
            icon.tint = Color.WHITE
            text.setTextColor(Color.WHITE)
            elevation = context.dpf(4f)
        } else {
            background = roundRect(Th.CARD_SOFT, context.dpf(22f), context.dp(2), Color.WHITE)
            icon.tint = Th.NAVY
            text.setTextColor(Th.NAVY)
            elevation = 0f
        }
    }
}

/** Tarjeta de estilo para el modo música. */
class StyleCard(ctx: Context, preview: View, previewW: Int, previewH: Int, title: String, titleSp: Float = 13f) : LinearLayout(ctx) {
    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(ctx.dp(6), ctx.dp(12), ctx.dp(6), ctx.dp(10))
        addView(preview, LayoutParams(previewW, previewH))
        val t = ctx.label(title, titleSp, true)
        t.gravity = Gravity.CENTER
        t.maxLines = 1
        t.setPadding(0, ctx.dp(6), 0, 0)
        addView(t)
        isClickable = true
        touchFx(ctx.dpf(22f))
        setChosen(false)
    }

    fun setChosen(b: Boolean) {
        background = if (b) {
            roundRect(Color.WHITE, context.dpf(22f), context.dp(3), Th.BLUE)
        } else {
            roundRect(Th.CARD_SOFT, context.dpf(22f), context.dp(2), Color.WHITE)
        }
        elevation = if (b) context.dpf(4f) else 0f
    }
}

/** Fila de puntitos de colores (vista previa de una paleta). */
class DotsPreview(ctx: Context, private val cols: IntArray) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val n = cols.size
        val step = width.toFloat() / n
        val r = min(step * 0.42f, height / 2f)
        for (i in 0 until n) {
            val cx = step * i + step / 2f
            val cy = height / 2f
            p.style = Paint.Style.FILL
            p.color = opaque(cols[i])
            c.drawCircle(cx, cy, r, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = context.dpf(1.2f)
            p.color = if (cols[i] == 0xFFFFFF) Th.LINE else 0xB3FFFFFF.toInt()
            c.drawCircle(cx, cy, r, p)
        }
    }
}

/** Pildora arcoíris (vista previa del modo arcoíris). */
fun Context.rainbowPreview(): View = View(this).apply {
    val cols = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 0.85f, 1f)) }
    background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, cols).apply {
        cornerRadius = dpf(12f)
    }
}

// =====================================================================================
// Botón grande de reproducir / detener
// =====================================================================================
class PlayButton(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var playing = false
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - context.dpf(6f)
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = 0x301B2A6B
        c.drawCircle(cx, cy + context.dpf(4f), r, p)
        p.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, Th.BLUE, Th.PINK, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = context.dpf(3f)
        p.color = Color.WHITE
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.FILL
        p.color = 0x66FFFFFF
        c.drawOval(RectF(cx - r * 0.62f, cy - r * 0.78f, cx + r * 0.1f, cy - r * 0.42f), p)
        p.color = Color.WHITE
        if (playing) {
            val s = r * 0.34f
            c.drawRoundRect(cx - s, cy - s, cx + s, cy + s, s * 0.25f, s * 0.25f, p)
        } else {
            val s = r * 0.40f
            val path = Path()
            path.moveTo(cx - s * 0.6f, cy - s)
            path.lineTo(cx + s, cy)
            path.lineTo(cx - s * 0.6f, cy + s)
            path.close()
            c.drawPath(path, p)
        }
    }
}

// =====================================================================================
// Barra de navegación inferior
// =====================================================================================
class NavIcon(ctx: Context, private val kind: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var tint: Int = Th.NAVY
        set(v) {
            field = v
            invalidate()
        }

    override fun onDraw(c: Canvas) {
        val s = min(width, height).toFloat()
        val ox = (width - s) / 2f
        val oy = (height - s) / 2f
        p.color = tint
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.10f
        when (kind) {
            0 -> { // lightstick: esfera + mango
                p.style = Paint.Style.STROKE
                c.drawCircle(ox + s * 0.5f, oy + s * 0.34f, s * 0.24f, p)
                p.style = Paint.Style.FILL
                c.drawRoundRect(ox + s * 0.42f, oy + s * 0.58f, ox + s * 0.58f, oy + s * 0.96f, s * 0.06f, s * 0.06f, p)
                c.drawPath(sparklePath(ox + s * 0.5f, oy + s * 0.34f, s * 0.11f), p)
            }
            1 -> { // nota musical
                p.style = Paint.Style.FILL
                c.drawOval(RectF(ox + s * 0.18f, oy + s * 0.64f, ox + s * 0.50f, oy + s * 0.90f), p)
                p.style = Paint.Style.STROKE
                c.drawLine(ox + s * 0.47f, oy + s * 0.76f, ox + s * 0.47f, oy + s * 0.12f, p)
                val path = Path()
                path.moveTo(ox + s * 0.47f, oy + s * 0.12f)
                path.quadTo(ox + s * 0.62f, oy + s * 0.30f, ox + s * 0.80f, oy + s * 0.34f)
                c.drawPath(path, p)
            }
            else -> { // info
                p.style = Paint.Style.STROKE
                c.drawCircle(ox + s * 0.5f, oy + s * 0.5f, s * 0.40f, p)
                p.style = Paint.Style.FILL
                c.drawCircle(ox + s * 0.5f, oy + s * 0.31f, s * 0.06f, p)
                p.style = Paint.Style.STROKE
                c.drawLine(ox + s * 0.5f, oy + s * 0.47f, ox + s * 0.5f, oy + s * 0.70f, p)
            }
        }
    }
}

class NavBar(ctx: Context, labels: List<String>, private val onSelect: (Int) -> Unit) : LinearLayout(ctx) {
    private val items = ArrayList<LinearLayout>()
    private val icons = ArrayList<NavIcon>()
    private val texts = ArrayList<TextView>()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = roundRect(0xF5FFFFFF.toInt(), ctx.dpf(34f), ctx.dp(2), Color.WHITE)
        elevation = ctx.dpf(10f)
        setPadding(ctx.dp(6), ctx.dp(6), ctx.dp(6), ctx.dp(6))
        labels.forEachIndexed { i, t ->
            val item = LinearLayout(ctx)
            item.orientation = HORIZONTAL
            item.gravity = Gravity.CENTER
            item.setPadding(ctx.dp(10), ctx.dp(10), ctx.dp(10), ctx.dp(10))
            val ic = NavIcon(ctx, i)
            item.addView(ic, LayoutParams(ctx.dp(22), ctx.dp(22)))
            val tv = ctx.label(t, 14f, true)
            tv.setPadding(ctx.dp(6), 0, 0, 0)
            item.addView(tv)
            item.isClickable = true
            item.setOnClickListener { onSelect(i) }
            item.touchFx(ctx.dpf(28f))
            addView(item, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            items.add(item)
            icons.add(ic)
            texts.add(tv)
        }
    }

    fun select(i: Int) {
        for (k in items.indices) {
            val on = k == i
            items[k].background = if (on) gradientPill(context.dpf(28f), Th.BLUE, Th.PINK) else null
            icons[k].tint = if (on) Color.WHITE else Th.NAVY_SOFT
            texts[k].setTextColor(if (on) Color.WHITE else Th.NAVY_SOFT)
        }
    }
}
