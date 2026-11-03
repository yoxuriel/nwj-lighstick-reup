package app.njsmusic

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// =====================================================================================
// Tema: stickers Y2K (contorno negro, degradados brillantes, sombra dura)
// =====================================================================================
object Th {
    val INK = 0xFF141414.toInt()
    val INK_SOFT = 0xFF555566.toInt()
    val LAV_BOTTOM = 0xFFF1E6FF.toInt()
    val DOT_BLUE = 0xFF3A97F0.toInt()
    val DOT_LIGHT = 0xFFE8F4FF.toInt()
    val OK = 0xFF5DE101.toInt()
    val AMBER = 0xFFFFC21A.toInt()
    val GREY = 0xFFB9B9C8.toInt()

    // degradados de sticker: [arriba, abajo]
    val G_WHITE = intArrayOf(0xFFFFFFFF.toInt(), 0xFFF6F0FF.toInt())
    val G_BLUE = intArrayOf(0xFFE6FAFF.toInt(), 0xFF22C3FF.toInt())
    val G_GREEN = intArrayOf(0xFFF0FFE4.toInt(), 0xFF6FDA3A.toInt())
    val G_YELLOW = intArrayOf(0xFFFFFDE6.toInt(), 0xFFF6EE00.toInt())
    val G_PINK = intArrayOf(0xFFFFF0FB.toInt(), 0xFFFF7AD9.toInt())
    val G_PURPLE = intArrayOf(0xFFF3ECFF.toInt(), 0xFFAC86FF.toInt())

    var round: Typeface = Typeface.DEFAULT
    var roundBold: Typeface = Typeface.DEFAULT_BOLD
    var pixel: Typeface = Typeface.DEFAULT_BOLD
    var stars: Typeface = Typeface.MONOSPACE

    /**
     * Fuentes libres (SIL OFL): Fredoka, Pixelify Sans, Silkscreen.
     * Son versiones FIJAS de cada grosor (no fuentes variables): con fuentes variables algunos
     * Android calculan el espacio entre letras con un grosor y dibujan con otro (letras desfasadas).
     */
    fun init(ctx: Context) {
        round = load(ctx, "fonts/fredoka-regular.ttf") ?: Typeface.DEFAULT
        roundBold = load(ctx, "fonts/fredoka-bold.ttf") ?: Typeface.DEFAULT_BOLD
        pixel = load(ctx, "fonts/pixelify-semibold.ttf") ?: Typeface.DEFAULT_BOLD
        stars = load(ctx, "fonts/silkscreen.ttf") ?: Typeface.MONOSPACE
    }

    private fun load(ctx: Context, path: String): Typeface? = try {
        Typeface.createFromAsset(ctx.assets, path)
    } catch (e: Exception) {
        null
    }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun lighten(c: Int, f: Float): Int {
    val r = Color.red(c) + ((255 - Color.red(c)) * f).toInt()
    val g = Color.green(c) + ((255 - Color.green(c)) * f).toInt()
    val b = Color.blue(c) + ((255 - Color.blue(c)) * f).toInt()
    return Color.rgb(r, g, b)
}

fun mix(a: Int, b: Int, t: Float): Int {
    val k = t.coerceIn(0f, 1f)
    return Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt()
    )
}

fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or ((a and 255) shl 24)
fun opaque(rgb: Int): Int = rgb or 0xFF000000.toInt()

fun sparklePath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    moveTo(cx, cy - r)
    quadTo(cx, cy, cx + r, cy)
    quadTo(cx, cy, cx, cy + r)
    quadTo(cx, cy, cx - r, cy)
    quadTo(cx, cy, cx, cy - r)
    close()
}

fun starPath(cx: Float, cy: Float, r: Float, inner: Float = 0.45f): Path = Path().apply {
    for (i in 0 until 10) {
        val a = -PI / 2 + i * PI / 5
        val rr = if (i % 2 == 0) r else r * inner
        val x = cx + (cos(a) * rr).toFloat()
        val y = cy + (sin(a) * rr).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Dibuja un sticker: sombra dura, relleno degradado, contorno negro y brillo. */
fun drawSticker(c: Canvas, path: Path, top: Int, bottom: Int, stroke: Float, shadow: Float, p: Paint, gloss: Boolean = true) {
    val b = RectF()
    path.computeBounds(b, true)
    p.shader = null
    p.style = Paint.Style.FILL
    if (shadow > 0f) {
        p.color = Th.INK
        c.save()
        c.translate(shadow, shadow)
        c.drawPath(path, p)
        c.restore()
    }
    p.shader = LinearGradient(0f, b.top, 0f, b.bottom, top, bottom, Shader.TileMode.CLAMP)
    c.drawPath(path, p)
    p.shader = null
    if (gloss) {
        p.color = 0x66FFFFFF
        val gw = b.width() * 0.34f
        val gh = b.height() * 0.16f
        c.drawOval(RectF(b.left + b.width() * 0.16f, b.top + b.height() * 0.12f, b.left + b.width() * 0.16f + gw, b.top + b.height() * 0.12f + gh), p)
    }
    p.style = Paint.Style.STROKE
    p.strokeWidth = stroke
    p.strokeJoin = Paint.Join.ROUND
    p.color = Th.INK
    c.drawPath(path, p)
    p.style = Paint.Style.FILL
}

/** Fondo de sticker redondeado (tarjetas y botones). */
class StickerDrawable(
    private val top: Int,
    private val bottom: Int,
    private val radius: Float,
    private val stroke: Float,
    private val shadow: Float,
    private val gloss: Boolean = false
) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(c: Canvas) {
        val b = bounds
        val h = stroke / 2f
        val body = RectF(b.left + h, b.top + h, b.right - shadow - h, b.bottom - shadow - h)
        val r = min(radius, min(body.width(), body.height()) / 2f)
        val path = Path()
        path.addRoundRect(body, r, r, Path.Direction.CW)
        p.alpha = 255
        drawSticker(c, path, top, bottom, stroke, shadow, p, false)
        if (gloss) {
            p.color = 0x73FFFFFF
            c.drawRoundRect(
                RectF(body.left + r * 0.6f, body.top + stroke * 1.6f, body.right - r * 0.6f, body.top + body.height() * 0.40f),
                r * 0.6f, r * 0.6f, p
            )
        }
    }

    override fun setAlpha(alpha: Int) {
        p.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        p.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Burbuja de chat con colita (estilo mensajería). */
class BubbleDrawable(private val top: Int, private val bottom: Int, private val stroke: Float,
                     private val shadow: Float, private val radius: Float, private val tail: Float,
                     private val tailRight: Boolean) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(c: Canvas) {
        val b = bounds
        val h = stroke / 2f
        val body = RectF(b.left + h, b.top + h, b.right - shadow - h, b.bottom - shadow - h - tail)
        val path = Path()
        path.addRoundRect(body, radius, radius, Path.Direction.CW)
        val t = Path()
        if (tailRight) {
            t.moveTo(body.right - radius * 1.6f, body.bottom - 2f)
            t.lineTo(body.right - radius * 0.2f, body.bottom + tail)
            t.lineTo(body.right - radius * 0.7f, body.bottom - 2f)
        } else {
            t.moveTo(body.left + radius * 0.7f, body.bottom - 2f)
            t.lineTo(body.left + radius * 0.2f, body.bottom + tail)
            t.lineTo(body.left + radius * 1.6f, body.bottom - 2f)
        }
        t.close()
        path.op(t, Path.Op.UNION)
        drawSticker(c, path, top, bottom, stroke, shadow, p, false)
    }

    override fun setAlpha(alpha: Int) {
        p.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        p.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Efecto de toque: onda + rebote. */
fun View.touchFx(radius: Float) {
    val mask = GradientDrawable().apply {
        cornerRadius = radius
        setColor(Color.WHITE)
    }
    foreground = RippleDrawable(ColorStateList.valueOf(0x22000000), null, mask)
    setOnTouchListener { v, e ->
        when (e.action) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
        }
        false
    }
}

/** Balanceo suave infinito (para stickers decorativos). */
fun View.wobble(deg: Float, ms: Long) {
    val a = ObjectAnimator.ofFloat(this, "rotation", -deg, deg)
    a.duration = ms
    a.repeatMode = ValueAnimator.REVERSE
    a.repeatCount = ValueAnimator.INFINITE
    a.start()
}

/** Una sola línea; si el texto no cabe (p. ej. en otro idioma), la letra se achica sola. */
fun TextView.fitOneLine(maxSp: Float, minSp: Int = 9) {
    maxLines = 1
    setAutoSizeTextTypeUniformWithConfiguration(minSp, maxSp.toInt().coerceAtLeast(minSp + 1), 1, TypedValue.COMPLEX_UNIT_SP)
}

fun Context.label(t: String, sp: Float, bold: Boolean = false, color: Int = Th.INK): TextView =
    TextView(this).apply {
        text = t
        textSize = sp
        typeface = if (bold) Th.roundBold else Th.round
        setTextColor(color)
    }

/** Título de sección en fuente pixel. */
fun Context.pixelTitle(t: String, sp: Float = 18f): TextView = TextView(this).apply {
    text = t
    textSize = sp
    typeface = Th.pixel
    setTextColor(Th.INK)
    letterSpacing = 0.04f
}

/** Tarjeta sticker blanca. */
fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = StickerDrawable(Th.G_WHITE[0], Th.G_WHITE[1], dpf(24f), dpf(2.5f), dpf(5f))
    setPadding(dp(18), dp(16), dp(18) + dp(5), dp(18) + dp(5))
}

fun Context.cardTitle(t: String, sub: String? = null): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    addView(pixelTitle(t))
    if (sub != null) addView(label(sub, 13f, false, Th.INK_SOFT))
    setPadding(0, 0, 0, dp(10))
}

// =====================================================================================
// Texto sticker: contorno negro + relleno degradado
// =====================================================================================
class StickerText(ctx: Context) : TextView(ctx) {
    var fillTop: Int = Color.WHITE
    var fillBottom: Int = Color.WHITE
    /** grosor del contorno; deja margen suficiente para que el contorno no se recorte */
    var outline: Float = ctx.dpf(3f)
        set(v) {
            field = v
            if (autoPad) applyPad()
        }
    private var autoPad = true

    init {
        setTextColor(Th.INK)
        typeface = Th.roundBold
        applyPad()
    }

    private fun applyPad() {
        val h = (max(outline, context.dpf(2f)) + context.dpf(4f)).toInt()
        val v = (max(outline, context.dpf(1f)) + context.dpf(2f)).toInt()
        super.setPadding(h, v, h, v)
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        autoPad = false
        super.setPadding(left, top, right, bottom)
    }

    /**
     * Se dibuja el texto directamente (sin el recorte interno de TextView, que cortaba el contorno
     * en los bordes): primero el contorno negro y encima el relleno degradado.
     */
    override fun onDraw(canvas: Canvas) {
        val lay = layout
        if (lay == null) {
            super.onDraw(canvas)
            return
        }
        val p = paint
        val availH = height - extendedPaddingTop - extendedPaddingBottom
        val dy = when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.CENTER_VERTICAL -> (availH - lay.height) / 2f
            Gravity.BOTTOM -> (availH - lay.height).toFloat()
            else -> 0f
        }
        canvas.save()
        canvas.translate(compoundPaddingLeft.toFloat(), extendedPaddingTop + dy)
        if (outline > 0f) {
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = outline * 2f
            p.strokeJoin = Paint.Join.ROUND
            p.color = Th.INK
            lay.draw(canvas)
        }
        p.style = Paint.Style.FILL
        p.strokeWidth = 0f
        p.color = Color.BLACK
        p.shader = LinearGradient(0f, 0f, 0f, lay.height.toFloat(), fillTop, fillBottom, Shader.TileMode.CLAMP)
        lay.draw(canvas)
        p.shader = null
        canvas.restore()
    }
}

fun Context.stickerText(t: String, sp: Float, grad: IntArray, outlineDp: Float = 3f): StickerText =
    StickerText(this).apply {
        text = t
        textSize = sp
        fillTop = grad[0]
        fillBottom = grad[1]
        outline = dpf(outlineDp)
    }

/** Botón sticker grande (degradado + contorno + sombra dura + brillo). */
fun Context.stickerButton(t: String, grad: IntArray, onClick: () -> Unit): StickerText {
    val b = stickerText(t, 18f, intArrayOf(Color.WHITE, Color.WHITE), 2.6f)
    b.gravity = Gravity.CENTER
    b.background = StickerDrawable(grad[0], grad[1], dpf(30f), dpf(2.5f), dpf(4f), true)
    b.setPadding(dp(18), dp(12), dp(18) + dp(4), dp(12) + dp(4))
    b.fitOneLine(18f, 11)
    b.isClickable = true
    b.setOnClickListener { onClick() }
    b.touchFx(dpf(30f))
    return b
}

/** Botón sticker blanco con texto negro. */
fun Context.softButton(t: String, onClick: () -> Unit): TextView = TextView(this).apply {
    text = t
    textSize = 15f
    typeface = Th.roundBold
    gravity = Gravity.CENTER
    setTextColor(Th.INK)
    background = StickerDrawable(Th.G_WHITE[0], Th.G_WHITE[1], dpf(24f), dpf(2.2f), dpf(3f))
    setPadding(dp(12), dp(10), dp(12) + dp(3), dp(10) + dp(3))
    fitOneLine(15f, 10)
    isClickable = true
    setOnClickListener { onClick() }
    touchFx(dpf(24f))
}

/** Burbuja de chat. mine = burbuja verde a la derecha. */
fun Context.chatBubble(t: String, mine: Boolean = false): TextView = TextView(this).apply {
    text = t
    textSize = 14.5f
    typeface = Th.round
    setLineSpacing(0f, 1.1f)
    setTextColor(Th.INK)
    val g = if (mine) Th.G_GREEN else Th.G_WHITE
    background = BubbleDrawable(g[0], g[1], dpf(2.2f), dpf(3f), dpf(18f), dpf(9f), mine)
    setPadding(dp(14), dp(10), dp(14) + dp(3), dp(10) + dp(3) + dp(9))
}

// =====================================================================================
// Fondo animado: nubes en tramado de puntos (halftone)
// =====================================================================================
class HalftoneSky(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bg: Shader? = null
    private val cell = ctx.dpf(10f)
    private val t0 = SystemClock.uptimeMillis()

    // nubes: x inicial (fracción), y (fracción), radio (fracción del ancho), velocidad (dp/s)
    private val clouds = arrayOf(
        floatArrayOf(0.12f, 0.08f, 0.26f, 7f), floatArrayOf(0.82f, 0.20f, 0.30f, 5f),
        floatArrayOf(0.38f, 0.46f, 0.27f, 6f), floatArrayOf(0.95f, 0.64f, 0.24f, 8f),
        floatArrayOf(0.08f, 0.80f, 0.26f, 5.5f), floatArrayOf(0.60f, 0.95f, 0.22f, 6.5f)
    )
    private val puffs = arrayOf(
        floatArrayOf(0f, 0f, 1f), floatArrayOf(-0.95f, 0.15f, 0.74f), floatArrayOf(0.98f, 0.22f, 0.70f),
        floatArrayOf(0.42f, -0.50f, 0.68f), floatArrayOf(-0.45f, -0.40f, 0.60f),
        floatArrayOf(-0.30f, 0.55f, 0.62f), floatArrayOf(0.45f, 0.55f, 0.60f)
    )
    private val cx = FloatArray(clouds.size)
    private val cy = FloatArray(clouds.size)
    private val cr = FloatArray(clouds.size)

    /** Activa o detiene la animación (para no gastar batería en segundo plano). */
    var running = false
        set(v) {
            field = v
            if (v) invalidate()
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        bg = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.WHITE, Th.LAV_BOTTOM, Shader.TileMode.CLAMP)
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.shader = bg
        p.style = Paint.Style.FILL
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        val t = (SystemClock.uptimeMillis() - t0) / 1000f
        val d = resources.displayMetrics.density
        for (i in clouds.indices) {
            val cl = clouds[i]
            val r = cl[2] * w
            val span = w + 4 * r
            var x = (cl[0] * w + cl[3] * d * t + 2 * r) % span
            if (x < 0) x += span
            cx[i] = x - 2 * r
            cy[i] = cl[1] * h + sin(t * 0.5f + cl[0] * 7f) * r * 0.08f
            cr[i] = r
        }

        val cols = (w / cell).toInt() + 1
        val rows = (h / cell).toInt() + 1
        for (gy in 0..rows) {
            val y = gy * cell + cell / 2f
            for (gx in 0..cols) {
                val x = gx * cell + cell / 2f
                var f = 0f
                var hl = 0f
                for (i in clouds.indices) {
                    val r = cr[i]
                    if (y < cy[i] - r * 1.8f || y > cy[i] + r * 1.8f) continue
                    if (x < cx[i] - r * 2.2f || x > cx[i] + r * 2.2f) continue
                    for (pf in puffs) {
                        val px = cx[i] + pf[0] * r * 0.75f
                        val py = cy[i] + pf[1] * r * 0.75f
                        val rr = r * 0.62f * pf[2] * 1.6f
                        val dd = hypot(x - px, y - py)
                        if (dd < rr) f = max(f, 1f - dd / rr)
                    }
                    val hd = hypot(x - (cx[i] - r * 0.32f), y - (cy[i] - r * 0.30f)) / (r * 0.75f)
                    if (hd < 1f) hl = max(hl, 1f - hd)
                }
                if (f <= 0f) continue
                val size = cell * (0.10f + 0.92f * smooth(0.02f, 0.62f, f))
                p.color = mix(Th.DOT_BLUE, Th.DOT_LIGHT, min(1f, hl.pow(0.8f) * 1.25f))
                val s = size / 2f
                val rr = s * 0.55f
                c.drawRoundRect(x - s, y - s, x + s, y + s, rr, rr, p)
            }
        }
        if (running) postInvalidateDelayed(40)
    }
}

/** Logo "RE UP!" (el "!" es un lightstick). */
fun Context.reupLogo(): android.widget.ImageView = android.widget.ImageView(this).apply {
    setImageResource(R.drawable.reup_logo)
    scaleType = android.widget.ImageView.ScaleType.FIT_START
    adjustViewBounds = true
}

/** Logo: el conejo (imagen del proyecto). */
fun Context.bunnyLogo(): android.widget.ImageView = android.widget.ImageView(this).apply {
    setImageResource(R.drawable.bunny_logo)
    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
    adjustViewBounds = true
}

// =====================================================================================
// Título hecho de estrellitas (cada "pixel" de una fuente pixel es una estrella)
// =====================================================================================
class StarText(ctx: Context, text: String, private val color: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cols: Int
    private val rows: Int
    private val on: Array<BooleanArray>
    private val maxCell = ctx.dpf(9f)

    init {
        val tp = Paint()
        tp.isAntiAlias = false
        tp.typeface = Th.stars
        tp.textSize = 8f
        tp.color = Color.WHITE
        val b = Rect()
        tp.getTextBounds(text, 0, text.length, b)
        val w = max(1, b.width() + 2)
        val h = max(1, b.height() + 2)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(text, 1f - b.left, 1f - b.top, tp)
        cols = w
        rows = h
        on = Array(rows) { y -> BooleanArray(cols) { x -> Color.alpha(bmp.getPixel(x, y)) > 110 } }
        bmp.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val cell = min(w.toFloat() / cols, maxCell)
        setMeasuredDimension(w, (cell * rows).toInt() + 2)
    }

    override fun onDraw(c: Canvas) {
        val cell = min(width.toFloat() / cols, maxCell)
        p.color = color
        p.style = Paint.Style.FILL
        for (y in 0 until rows) for (x in 0 until cols) {
            if (on[y][x]) c.drawPath(starPath(x * cell + cell / 2f, y * cell + cell / 2f, cell * 0.62f), p)
        }
    }
}

// =====================================================================================
// Stickers decorativos (estrella, destellos, corazón, notas, corona, burbuja)
// =====================================================================================
class StickerShape(ctx: Context, private val kind: String, private val top: Int, private val bottom: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var path: Path? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        path = null
    }

    private fun build(): Path {
        val sh = context.dpf(2.5f)
        val s = min(width, height).toFloat() - sh - context.dpf(3f)
        val o = context.dpf(1.5f)
        fun X(v: Float) = o + v * s
        fun Y(v: Float) = o + v * s
        val path = Path()
        when (kind) {
            "star" -> path.addPath(starPath(X(0.5f), Y(0.53f), s * 0.5f, 0.48f))
            "sparkle" -> {
                path.addPath(sparklePath(X(0.40f), Y(0.58f), s * 0.40f))
                path.op(sparklePath(X(0.82f), Y(0.20f), s * 0.17f), Path.Op.UNION)
            }
            "heart" -> {
                path.moveTo(X(0.5f), Y(0.92f))
                path.cubicTo(X(0.05f), Y(0.62f), X(0f), Y(0.28f), X(0.25f), Y(0.14f))
                path.cubicTo(X(0.38f), Y(0.07f), X(0.5f), Y(0.18f), X(0.5f), Y(0.28f))
                path.cubicTo(X(0.5f), Y(0.18f), X(0.62f), Y(0.07f), X(0.75f), Y(0.14f))
                path.cubicTo(X(1f), Y(0.28f), X(0.95f), Y(0.62f), X(0.5f), Y(0.92f))
                path.close()
            }
            "notes" -> {
                path.addOval(RectF(X(0.04f), Y(0.70f), X(0.42f), Y(0.96f)), Path.Direction.CW)
                path.op(Path().apply { addOval(RectF(X(0.56f), Y(0.62f), X(0.94f), Y(0.88f)), Path.Direction.CW) }, Path.Op.UNION)
                path.op(Path().apply { addRect(X(0.30f), Y(0.12f), X(0.40f), Y(0.82f), Path.Direction.CW) }, Path.Op.UNION)
                path.op(Path().apply { addRect(X(0.82f), Y(0.04f), X(0.92f), Y(0.74f), Path.Direction.CW) }, Path.Op.UNION)
                path.op(Path().apply {
                    moveTo(X(0.30f), Y(0.12f)); lineTo(X(0.92f), Y(0.04f))
                    lineTo(X(0.92f), Y(0.22f)); lineTo(X(0.30f), Y(0.30f)); close()
                }, Path.Op.UNION)
            }
            "crown" -> {
                path.moveTo(X(0.08f), Y(0.82f))
                path.lineTo(X(0.04f), Y(0.26f))
                path.lineTo(X(0.30f), Y(0.50f))
                path.lineTo(X(0.50f), Y(0.14f))
                path.lineTo(X(0.70f), Y(0.50f))
                path.lineTo(X(0.96f), Y(0.26f))
                path.lineTo(X(0.92f), Y(0.82f))
                path.close()
            }
            else -> { // burbuja de diálogo
                path.addOval(RectF(X(0.04f), Y(0.06f), X(0.96f), Y(0.76f)), Path.Direction.CW)
                path.op(Path().apply {
                    moveTo(X(0.30f), Y(0.62f)); lineTo(X(0.18f), Y(0.96f)); lineTo(X(0.52f), Y(0.70f)); close()
                }, Path.Op.UNION)
            }
        }
        return path
    }

    override fun onDraw(c: Canvas) {
        val pa = path ?: build().also { path = it }
        drawSticker(c, pa, top, bottom, context.dpf(2.2f), context.dpf(2.5f), p, true)
    }
}

// =====================================================================================
// Orbe de luz: vista previa del color del lightstick
// =====================================================================================
class Orb(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var color: Int = opaque(0x1E5AFF)
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
            intArrayOf(withAlpha(col, 0xB0), withAlpha(col, 0x40), withAlpha(col, 0)),
            floatArrayOf(0.30f, 0.62f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, p)
        val core = r * 0.46f
        p.shader = null
        p.color = Th.INK
        c.drawCircle(cx + context.dpf(3f), cy + context.dpf(3f), core, p)
        p.shader = RadialGradient(
            cx - core * 0.25f, cy - core * 0.3f, core * 1.2f,
            intArrayOf(lighten(col, 0.7f), col), null, Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, core, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = context.dpf(2.5f)
        p.color = Th.INK
        c.drawCircle(cx, cy, core, p)
        p.style = Paint.Style.FILL
        p.color = 0xB3FFFFFF.toInt()
        c.drawOval(RectF(cx - core * 0.55f, cy - core * 0.72f, cx - core * 0.05f, cy - core * 0.40f), p)
    }
}

// =====================================================================================
// Slider sticker
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
        val sh = context.dpf(3f)
        val w = width.toFloat() - sh
        val h = height.toFloat() - sh
        val trackH = h * 0.58f
        val top = (h - trackH) / 2f
        val r = trackH / 2f
        if (shader == null) shader = LinearGradient(r, 0f, w - r, 0f, colors, null, Shader.TileMode.CLAMP)
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = Th.INK
        c.drawRoundRect(sh, top + sh, w + sh, top + trackH + sh, r, r, p)
        p.shader = shader
        c.drawRoundRect(0f, top, w, top + trackH, r, r, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = context.dpf(2.2f)
        p.color = Th.INK
        c.drawRoundRect(0f, top, w, top + trackH, r, r, p)

        val tr = h / 2f - context.dpf(2f)
        val x = (tr + value * (w - 2 * tr))
        p.style = Paint.Style.FILL
        p.color = Th.INK
        c.drawCircle(x + context.dpf(2.5f), h / 2f + context.dpf(2.5f), tr, p)
        p.color = Color.WHITE
        c.drawCircle(x, h / 2f, tr, p)
        p.style = Paint.Style.STROKE
        p.color = Th.INK
        c.drawCircle(x, h / 2f, tr, p)
        p.style = Paint.Style.FILL
        p.color = Th.INK
        c.drawPath(sparklePath(x, h / 2f, tr * 0.45f), p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
            parent?.requestDisallowInterceptTouchEvent(true)
            val sh = context.dpf(3f)
            val tr = (height - sh) / 2f
            value = (e.x - tr) / (width - sh - 2 * tr)
            onChange(value)
        }
        return true
    }
}

// =====================================================================================
// Burbuja de color estilo paleta (color arriba → claro abajo, contorno negro)
// =====================================================================================
class ColorBubble(ctx: Context, val ui: Int, val led: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var chosen = false
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }

    override fun onDraw(c: Canvas) {
        val sh = context.dpf(2.5f)
        val size = min(width, height) - sh
        val r = size / 2f - context.dpf(4f)
        val cx = size / 2f
        val cy = size / 2f + context.dpf(1f)
        val col = opaque(ui)
        val path = Path()
        path.addCircle(cx, cy, r, Path.Direction.CW)
        drawSticker(c, path, col, lighten(col, 0.72f), context.dpf(if (chosen) 3.2f else 2.2f), sh, p, false)
        p.color = 0x99FFFFFF.toInt()
        c.drawOval(RectF(cx - r * 0.6f, cy - r * 0.72f, cx - r * 0.05f, cy - r * 0.42f), p)
        if (chosen) {
            val sp = sparklePath(cx + r * 0.78f, cy - r * 0.78f, r * 0.42f)
            drawSticker(c, sp, Th.G_YELLOW[0], Th.G_YELLOW[1], context.dpf(1.8f), context.dpf(1.5f), p, false)
        }
    }
}

fun Context.swatch(name: String, ui: Int, led: Int, sizeDp: Int, out: MutableList<ColorBubble>, onClick: () -> Unit): LinearLayout {
    val col = LinearLayout(this)
    col.orientation = LinearLayout.VERTICAL
    col.gravity = Gravity.CENTER_HORIZONTAL
    val b = ColorBubble(this, ui, led)
    out.add(b)
    col.addView(b, LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)))
    val t = label(name, 12.5f, true)
    t.gravity = Gravity.CENTER
    t.fitOneLine(12.5f, 8)
    col.addView(t, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
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
        val s = min(width, height).toFloat() - context.dpf(4f)
        val ox = (width - s) / 2f - context.dpf(1f)
        val oy = (height - s) / 2f - context.dpf(1f)
        val st = context.dpf(2f)
        val sh = context.dpf(2f)
        when (kind) {
            0 -> {
                val path = Path()
                val cx = ox + s / 2f
                val cy = oy + s / 2f
                for (i in 0 until 8) {
                    val a = i * PI / 4
                    val ray = Path()
                    val r1 = s * 0.18f
                    val r2 = s * 0.46f
                    val wdt = s * 0.07f
                    val ca = cos(a).toFloat()
                    val sa = sin(a).toFloat()
                    ray.moveTo(cx + ca * r1 - sa * wdt, cy + sa * r1 + ca * wdt)
                    ray.lineTo(cx + ca * r2 - sa * wdt, cy + sa * r2 + ca * wdt)
                    ray.lineTo(cx + ca * r2 + sa * wdt, cy + sa * r2 - ca * wdt)
                    ray.lineTo(cx + ca * r1 + sa * wdt, cy + sa * r1 - ca * wdt)
                    ray.close()
                    path.op(ray, Path.Op.UNION)
                }
                drawSticker(c, path, Th.G_YELLOW[0], Th.G_YELLOW[1], st, sh, p, false)
            }
            1 -> {
                val path = bolt(ox - s * 0.06f, oy + s * 0.12f, s * 0.80f)
                path.op(bolt(ox + s * 0.44f, oy, s * 0.52f), Path.Op.UNION)
                drawSticker(c, path, Th.G_YELLOW[0], Th.G_YELLOW[1], st, sh, p, false)
            }
            2 -> drawSticker(c, bolt(ox + s * 0.06f, oy + s * 0.03f, s * 0.92f), Th.G_YELLOW[0], Th.G_YELLOW[1], st, sh, p, false)
            else -> {
                val path = Path()
                path.addCircle(ox + s * 0.34f, oy + s * 0.64f, s * 0.24f, Path.Direction.CW)
                path.op(Path().apply { addCircle(ox + s * 0.72f, oy + s * 0.54f, s * 0.17f, Path.Direction.CW) }, Path.Op.UNION)
                path.op(Path().apply { addCircle(ox + s * 0.52f, oy + s * 0.20f, s * 0.13f, Path.Direction.CW) }, Path.Op.UNION)
                drawSticker(c, path, Th.G_BLUE[0], Th.G_BLUE[1], st, sh, p, true)
            }
        }
    }
}

class EffectCard(ctx: Context, kind: Int, title: String, val mode: Int) : LinearLayout(ctx) {
    private val icon = EffectIcon(ctx, kind)
    private val text = ctx.label(title, 14f, true)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(ctx.dp(8), ctx.dp(12), ctx.dp(8) + ctx.dp(4), ctx.dp(10) + ctx.dp(4))
        addView(icon, LayoutParams(ctx.dp(46), ctx.dp(46)))
        text.gravity = Gravity.CENTER
        text.setPadding(0, ctx.dp(4), 0, 0)
        text.fitOneLine(14f, 9)
        addView(text, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        isClickable = true
        touchFx(ctx.dpf(22f))
        setChosen(false)
    }

    fun setChosen(b: Boolean) {
        val g = if (b) Th.G_BLUE else Th.G_WHITE
        background = StickerDrawable(g[0], g[1], context.dpf(22f), context.dpf(if (b) 2.8f else 2f), context.dpf(4f), b)
    }
}

class StyleCard(ctx: Context, preview: View, previewW: Int, previewH: Int, title: String, titleSp: Float = 14f) : LinearLayout(ctx) {
    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(ctx.dp(4), ctx.dp(10), ctx.dp(4) + ctx.dp(4), ctx.dp(8) + ctx.dp(4))
        addView(preview, LayoutParams(previewW, previewH))
        val t = ctx.label(title, titleSp, true)
        t.gravity = Gravity.CENTER
        t.setPadding(0, ctx.dp(5), 0, 0)
        t.fitOneLine(titleSp, 8)
        addView(t, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        isClickable = true
        touchFx(ctx.dpf(22f))
        setChosen(false)
    }

    fun setChosen(b: Boolean) {
        val g = if (b) Th.G_YELLOW else Th.G_WHITE
        background = StickerDrawable(g[0], g[1], context.dpf(22f), context.dpf(if (b) 2.8f else 2f), context.dpf(4f), b)
    }
}

/** Fila de puntos de colores con contorno (vista previa de una paleta). */
class DotsPreview(ctx: Context, private val cols: IntArray) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val n = cols.size
        val step = width.toFloat() / n
        val r = min(step * 0.40f, height / 2f - context.dpf(2f))
        for (i in 0 until n) {
            val path = Path()
            path.addCircle(step * i + step / 2f, height / 2f - context.dpf(1f), r, Path.Direction.CW)
            val col = opaque(cols[i])
            drawSticker(c, path, col, lighten(col, 0.6f), context.dpf(1.6f), context.dpf(1.5f), p, false)
        }
    }
}

/** Píldora arcoíris con contorno (vista previa del modo arcoíris). */
class RainbowPreview(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val sh = context.dpf(2f)
        val w = width - sh
        val h = height - sh
        val r = h / 2f
        p.style = Paint.Style.FILL
        p.color = Th.INK
        c.drawRoundRect(sh, sh, w + sh, h + sh, r, r, p)
        val cols = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 0.75f, 1f)) }
        p.shader = LinearGradient(0f, 0f, w, 0f, cols, null, Shader.TileMode.CLAMP)
        c.drawRoundRect(0f, 0f, w, h, r, r, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = context.dpf(1.8f)
        p.color = Th.INK
        c.drawRoundRect(0f, 0f, w, h, r, r, p)
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
        val sh = context.dpf(4f)
        val size = min(width, height) - sh
        val cx = size / 2f
        val cy = size / 2f
        val r = size / 2f - context.dpf(3f)
        val circle = Path()
        circle.addCircle(cx, cy, r, Path.Direction.CW)
        val g = if (playing) Th.G_PINK else Th.G_GREEN
        drawSticker(c, circle, g[0], g[1], context.dpf(3f), sh, p, true)
        val icon = Path()
        if (playing) {
            val s = r * 0.32f
            icon.addRoundRect(RectF(cx - s, cy - s, cx + s, cy + s), s * 0.3f, s * 0.3f, Path.Direction.CW)
        } else {
            val s = r * 0.40f
            icon.moveTo(cx - s * 0.55f, cy - s)
            icon.lineTo(cx + s, cy)
            icon.lineTo(cx - s * 0.55f, cy + s)
            icon.close()
        }
        drawSticker(c, icon, Color.WHITE, 0xFFEFEFEF.toInt(), context.dpf(2.5f), 0f, p, false)
    }
}

// =====================================================================================
// Barra inferior estilo "chat" (Phoning)
// =====================================================================================
class NavIcon(ctx: Context, private val kind: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var tint: Int = Th.INK
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
        p.strokeWidth = s * 0.11f
        when (kind) {
            0 -> {
                p.style = Paint.Style.STROKE
                c.drawCircle(ox + s * 0.5f, oy + s * 0.34f, s * 0.24f, p)
                p.style = Paint.Style.FILL
                c.drawRoundRect(ox + s * 0.42f, oy + s * 0.58f, ox + s * 0.58f, oy + s * 0.96f, s * 0.06f, s * 0.06f, p)
                c.drawPath(sparklePath(ox + s * 0.5f, oy + s * 0.34f, s * 0.12f), p)
            }
            1 -> {
                p.style = Paint.Style.FILL
                c.drawOval(RectF(ox + s * 0.06f, oy + s * 0.66f, ox + s * 0.40f, oy + s * 0.92f), p)
                c.drawOval(RectF(ox + s * 0.56f, oy + s * 0.58f, ox + s * 0.90f, oy + s * 0.84f), p)
                c.drawRect(ox + s * 0.30f, oy + s * 0.16f, ox + s * 0.40f, oy + s * 0.80f, p)
                c.drawRect(ox + s * 0.80f, oy + s * 0.08f, ox + s * 0.90f, oy + s * 0.72f, p)
                val beam = Path()
                beam.moveTo(ox + s * 0.30f, oy + s * 0.16f)
                beam.lineTo(ox + s * 0.90f, oy + s * 0.08f)
                beam.lineTo(ox + s * 0.90f, oy + s * 0.24f)
                beam.lineTo(ox + s * 0.30f, oy + s * 0.32f)
                beam.close()
                c.drawPath(beam, p)
            }
            else -> {
                p.style = Paint.Style.FILL
                val bub = Path()
                bub.addOval(RectF(ox + s * 0.04f, oy + s * 0.08f, ox + s * 0.96f, oy + s * 0.76f), Path.Direction.CW)
                bub.moveTo(ox + s * 0.28f, oy + s * 0.64f)
                bub.lineTo(ox + s * 0.16f, oy + s * 0.96f)
                bub.lineTo(ox + s * 0.52f, oy + s * 0.70f)
                bub.close()
                c.drawPath(bub, p)
                p.color = Color.WHITE
                c.drawCircle(ox + s * 0.30f, oy + s * 0.42f, s * 0.07f, p)
                c.drawCircle(ox + s * 0.50f, oy + s * 0.42f, s * 0.07f, p)
                c.drawCircle(ox + s * 0.70f, oy + s * 0.42f, s * 0.07f, p)
            }
        }
    }
}

class NavBar(ctx: Context, labels: List<String>, private val onSelect: (Int) -> Unit) : LinearLayout(ctx) {
    private val items = ArrayList<LinearLayout>()
    private val icons = ArrayList<NavIcon>()
    private val texts = ArrayList<StickerText>()

    init {
        orientation = HORIZONTAL
        isBaselineAligned = false
        gravity = Gravity.CENTER_VERTICAL
        background = StickerDrawable(Color.WHITE, Color.WHITE, ctx.dpf(32f), ctx.dpf(2.6f), ctx.dpf(5f))
        setPadding(ctx.dp(6), ctx.dp(6), ctx.dp(6) + ctx.dp(5), ctx.dp(6) + ctx.dp(5))
        labels.forEachIndexed { i, t ->
            val item = LinearLayout(ctx)
            item.orientation = HORIZONTAL
            item.isBaselineAligned = false
            item.gravity = Gravity.CENTER
            item.setPadding(ctx.dp(6), ctx.dp(8), ctx.dp(6) + ctx.dp(3), ctx.dp(8) + ctx.dp(3))
            val ic = NavIcon(ctx, i)
            item.addView(ic, LayoutParams(ctx.dp(22), ctx.dp(22)))
            val tv = ctx.stickerText(t, 15f, intArrayOf(Th.INK, Th.INK), 0f)
            tv.setPadding(ctx.dp(6), ctx.dp(5), ctx.dp(6), ctx.dp(5))
            item.addView(tv)
            item.isClickable = true
            item.setOnClickListener { onSelect(i) }
            item.touchFx(ctx.dpf(26f))
            addView(item, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            items.add(item)
            icons.add(ic)
            texts.add(tv)
        }
    }

    fun select(i: Int) {
        for (k in items.indices) {
            val on = k == i
            items[k].background = if (on) {
                StickerDrawable(Th.G_GREEN[1], 0xFF4DBE1F.toInt(), context.dpf(26f), context.dpf(2.2f), context.dpf(3f), true)
            } else {
                null
            }
            icons[k].tint = Th.INK
            val tv = texts[k]
            if (on) {
                tv.fillTop = Color.WHITE
                tv.fillBottom = Color.WHITE
                tv.outline = context.dpf(2.4f)
            } else {
                tv.fillTop = Th.INK
                tv.fillBottom = Th.INK
                tv.outline = 0f
            }
            tv.invalidate()
        }
    }
}
