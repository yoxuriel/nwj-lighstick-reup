package app.njsmusic

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

/** Barra de color (tono) como la de la app oficial. */
class HueBar(ctx: Context, private val onHue: (Float) -> Unit) : View(ctx) {
    private var shader: Shader? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumb = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.WHITE
    }
    var hue = 240f
        set(v) {
            field = v
            invalidate()
        }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val colors = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)) }
        shader = LinearGradient(0f, 0f, w.toFloat(), 0f, colors, null, Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        paint.shader = shader
        val r = height / 2f
        c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, paint)
        val x = (hue / 360f * width).coerceIn(r, width - r)
        c.drawCircle(x, r, r - 4f, thumb)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
            parent?.requestDisallowInterceptTouchEvent(true)
            hue = (e.x / width * 360f).coerceIn(0f, 359f)
            onHue(hue)
        }
        return true
    }
}

class MainActivity : Activity() {

    private val REQ_BLE = 1
    private val REQ_AUDIO = 2
    private val REQ_PROJ = 3

    private val ui = Handler(Looper.getMainLooper())

    // estado de los controles manuales
    private var baseColor = 0x0000FF   // color a brillo completo (RGB)
    private var brightness = 100       // 1..100
    private var mode = 1               // 1..4

    // ajustes del modo musica
    private var paletteId = 1
    private var sourceId = 0
    private var minBrightnessPct = 4

    private lateinit var status: TextView
    private lateinit var info: TextView
    private lateinit var connectBtn: Button
    private lateinit var swatch: View
    private lateinit var manualBox: LinearLayout
    private lateinit var hueBar: HueBar
    private lateinit var musicBtn: Button
    private lateinit var musicMsg: TextView
    private val modeButtons = ArrayList<Button>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ------------------------------------------------------------------ ciclo de vida
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LightStick.init(applicationContext)
        buildUi()
    }

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            ui.postDelayed(this, 250)
        }
    }

    override fun onResume() {
        super.onResume()
        ui.post(tick)
    }

    override fun onPause() {
        ui.removeCallbacks(tick)
        super.onPause()
    }

    private fun refresh() {
        status.text = LightStick.status
        info.text = LightStick.infoLine()
        connectBtn.text =
            if (LightStick.connected || LightStick.connecting) "Desconectar" else "Conectar al lightstick"
        musicBtn.text = if (Bus.musicRunning) "Detener modo música" else "Iniciar modo música"
        musicMsg.text = Bus.message
        manualBox.alpha = if (Bus.musicRunning) 0.35f else 1f
        if (Bus.musicRunning) {
            swatch.setBackgroundColor(Color.rgb(Bus.r, Bus.g, Bus.b))
        } else {
            val m = manualRgb()
            swatch.setBackgroundColor(Color.rgb(m[0], m[1], m[2]))
        }
        for (i in modeButtons.indices) modeButtons[i].alpha = if (i + 1 == mode) 1f else 0.5f
    }

    // ------------------------------------------------------------------ interfaz
    private fun text(t: String, sp: Float = 14f, bold: Boolean = false): TextView = TextView(this).apply {
        text = t
        textSize = sp
        setTextColor(Color.WHITE)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(6), 0, dp(4))
    }

    private fun btn(t: String, click: () -> Unit): Button = Button(this).apply {
        text = t
        isAllCaps = false
        setOnClickListener { click() }
    }

    private fun colorBtn(label: String, rgb: Int): Button {
        val b = btn(label) { setBase(rgb) }
        b.setBackgroundColor(0xFF000000.toInt() or rgb)
        val lum = 0.299 * ((rgb shr 16) and 255) + 0.587 * ((rgb shr 8) and 255) + 0.114 * (rgb and 255)
        b.setTextColor(if (lum > 140) Color.BLACK else Color.WHITE)
        return b
    }

    private fun row(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        for (v in views) {
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(2), dp(2), dp(2), dp(2))
            addView(v, lp)
        }
    }

    private fun seek(max: Int, value: Int, change: (Int) -> Unit): SeekBar = SeekBar(this).apply {
        this.max = max
        progress = value
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) change(p)
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun radios(labels: List<String>, baseId: Int, checked: Int, change: (Int) -> Unit): RadioGroup {
        val rg = RadioGroup(this)
        rg.orientation = RadioGroup.VERTICAL
        labels.forEachIndexed { i, t ->
            val rb = RadioButton(this)
            rb.text = t
            rb.id = baseId + i
            rb.setTextColor(Color.WHITE)
            rg.addView(rb)
        }
        rg.check(baseId + checked)
        rg.setOnCheckedChangeListener { _, id -> change(id - baseId) }
        return rg
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(0xFF10141A.toInt())
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(24), dp(16), dp(32))
        scroll.addView(col)

        col.addView(text("NewJeans Light Stick", 22f, true))
        status = text("Desconectado", 15f)
        col.addView(status)
        info = text("", 12f)
        col.addView(info)
        connectBtn = btn("Conectar al lightstick") { onConnectClick() }
        col.addView(connectBtn)

        swatch = View(this)
        col.addView(swatch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)).apply {
            topMargin = dp(12)
        })

        // ---------------- controles manuales (como la app oficial)
        manualBox = LinearLayout(this)
        manualBox.orientation = LinearLayout.VERTICAL
        col.addView(manualBox)

        manualBox.addView(text("Color", 16f, true))
        hueBar = HueBar(this) { h -> setBase(Color.HSVToColor(floatArrayOf(h, 1f, 1f)) and 0xFFFFFF, fromBar = true) }
        manualBox.addView(hueBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))

        manualBox.addView(text("Colores del lightstick", 13f))
        manualBox.addView(
            row(
                colorBtn("Blanco", 0xFFFFFF),
                colorBtn("Verde", 0x00FF40),
                colorBtn("Rosa", 0xFF2D9B)
            )
        )
        manualBox.addView(
            row(
                colorBtn("Azul", 0x1E50FF),
                colorBtn("Naranja", 0xFF5A00),
                colorBtn("Amarillo", 0xFFE000)
            )
        )
        manualBox.addView(text("Integrantes", 13f))
        manualBox.addView(
            row(
                colorBtn("Minji", 0x1E50FF),
                colorBtn("Hanni", 0xFF2D9B),
                colorBtn("Danielle", 0xFFE000)
            )
        )
        manualBox.addView(
            row(
                colorBtn("Haerin", 0x00FF40),
                colorBtn("Hyein", 0x9B30FF),
                View(this)
            )
        )

        manualBox.addView(text("Brillo", 16f, true))
        manualBox.addView(seek(99, 99) { p ->
            brightness = p + 1
            sendManual()
        })

        manualBox.addView(text("Efecto", 16f, true))
        manualBox.addView(
            text("Los 4 modos de la app oficial. El modo 1 es el que usa la app al conectar.", 12f)
        )
        val mb = (1..4).map { m -> btn("Modo $m") { mode = m; sendManual() } }
        modeButtons.addAll(mb)
        manualBox.addView(row(*mb.toTypedArray()))

        // ---------------- modo musica
        col.addView(text("Modo música", 20f, true).apply { setPadding(0, dp(24), 0, dp(4)) })
        col.addView(text("Colores", 16f, true))
        col.addView(
            radios(
                listOf(
                    "Arcoíris (cambia de tono con el ritmo)",
                    "Los 6 colores del lightstick",
                    "Integrantes: Minji 💙 Hanni 💖 Danielle 💛 Haerin 💚 Hyein 💜"
                ), 1000, paletteId
            ) { paletteId = it }
        )
        col.addView(text("De dónde viene la música", 16f, true))
        col.addView(
            radios(
                listOf(
                    "Micrófono (concierto, bocina, otra fuente)",
                    "Audio del mismo celular (Android 10+)"
                ), 2000, sourceId
            ) { sourceId = it }
        )
        col.addView(text("Brillo mínimo (para que no se apague en silencios)", 14f))
        col.addView(seek(40, minBrightnessPct) { minBrightnessPct = it })

        musicBtn = btn("Iniciar modo música") { onMusicClick() }
        col.addView(musicBtn)
        musicMsg = text("", 12f)
        col.addView(musicMsg)
        col.addView(
            text(
                "El modo 'Audio del mismo celular' no funciona con apps que bloquean la captura de audio. " +
                    "En ese caso usa el micrófono.", 11f
            )
        )

        setContentView(scroll)
    }

    // ------------------------------------------------------------------ controles manuales
    private fun setBase(rgb: Int, fromBar: Boolean = false) {
        baseColor = rgb and 0xFFFFFF
        if (!fromBar) {
            val hsv = FloatArray(3)
            Color.colorToHSV(0xFF000000.toInt() or baseColor, hsv)
            hueBar.hue = hsv[0]
        }
        sendManual()
    }

    private fun manualRgb(): IntArray {
        val k = brightness / 100f
        return intArrayOf(
            (((baseColor shr 16) and 255) * k).toInt(),
            (((baseColor shr 8) and 255) * k).toInt(),
            ((baseColor and 255) * k).toInt()
        )
    }

    private fun sendManual() {
        val c = manualRgb()
        LightStick.lastManual = intArrayOf(mode, c[0], c[1], c[2])
        if (!Bus.musicRunning) LightStick.setLed(mode, c[0], c[1], c[2])
    }

    // ------------------------------------------------------------------ conexion
    private fun blePerms(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun has(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun onConnectClick() {
        if (LightStick.connected || LightStick.connecting) {
            LightStick.disconnect()
            return
        }
        val perms = blePerms()
        if (!perms.all { has(it) }) {
            requestPermissions(perms, REQ_BLE)
            return
        }
        if (!LightStick.bluetoothOn()) {
            Toast.makeText(this, "Enciende el Bluetooth del celular", Toast.LENGTH_LONG).show()
            return
        }
        LightStick.scan(4000) { list ->
            when {
                list.isEmpty() -> Toast.makeText(
                    this,
                    "No encontré el lightstick. Enciéndelo, acércalo y cierra la app oficial.",
                    Toast.LENGTH_LONG
                ).show()
                list.size == 1 -> LightStick.connect(list[0].address)
                else -> {
                    val labels = list.map { "${it.name}  (${it.address}, señal ${it.rssi})" }.toTypedArray()
                    AlertDialog.Builder(this)
                        .setTitle("Elige tu lightstick")
                        .setItems(labels) { _, i -> LightStick.connect(list[i].address) }
                        .show()
                }
            }
        }
    }

    // ------------------------------------------------------------------ modo musica
    private fun onMusicClick() {
        if (Bus.musicRunning) {
            stopService(Intent(this, MusicService::class.java))
            return
        }
        if (!LightStick.connected) {
            Toast.makeText(this, "Primero conecta el lightstick", Toast.LENGTH_LONG).show()
            return
        }
        if (!has(Manifest.permission.RECORD_AUDIO)) {
            val need = ArrayList<String>()
            need.add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS)) {
                need.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            requestPermissions(need.toTypedArray(), REQ_AUDIO)
            return
        }
        startMusic()
    }

    private fun startMusic() {
        if (sourceId == 1) {
            if (Build.VERSION.SDK_INT < 29) {
                Toast.makeText(this, "Requiere Android 10 o superior. Usa el micrófono.", Toast.LENGTH_LONG).show()
                return
            }
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            @Suppress("DEPRECATION")
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJ)
        } else {
            launchService(0, null)
        }
    }

    private fun launchService(resultCode: Int, data: Intent?) {
        val i = Intent(this, MusicService::class.java)
        i.putExtra(MusicService.EXTRA_SOURCE, sourceId)
        i.putExtra(MusicService.EXTRA_PALETTE, paletteId)
        i.putExtra(MusicService.EXTRA_MODE, mode)
        i.putExtra(MusicService.EXTRA_MINB, minBrightnessPct / 100f)
        if (data != null) {
            i.putExtra(MusicService.EXTRA_CODE, resultCode)
            i.putExtra(MusicService.EXTRA_DATA, data)
        }
        Bus.message = "Iniciando…"
        startForegroundService(i)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PROJ) {
            if (resultCode == RESULT_OK && data != null) {
                launchService(resultCode, data)
            } else {
                Toast.makeText(this, "Sin el permiso de captura no se puede usar el audio del celular", Toast.LENGTH_LONG).show()
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQ_BLE -> {
                if (blePerms().all { has(it) }) {
                    onConnectClick()
                } else {
                    Toast.makeText(this, "Hace falta el permiso de Bluetooth", Toast.LENGTH_LONG).show()
                }
            }
            REQ_AUDIO -> {
                if (has(Manifest.permission.RECORD_AUDIO)) {
                    startMusic()
                } else {
                    Toast.makeText(this, "Hace falta el permiso del micrófono", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
