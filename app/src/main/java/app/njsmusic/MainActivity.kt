package app.njsmusic

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val REQ_BLE = 1
    private val REQ_AUDIO = 2
    private val REQ_PROJ = 3

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var store: Store

    // ---- estado
    private var baseColor = 0x0000FF   // color a brillo completo
    private var brightness = 1f        // 0.03..1
    private var mode = 1               // modo del lightstick 1..4
    private var paletteId = 1          // 0 arcoiris, 1 NewJeans, 2..6 integrantes
    private var sourceId = 0           // 0 micrófono, 1 audio del celular
    private var minBright = 0.04f
    private var tab = 0
    private var afterBle: (() -> Unit)? = null
    private var pendingSave: Pair<String, String>? = null
    private var lastConnState = -1
    private var identifying = false

    // ---- vistas
    private lateinit var pages: Array<View>
    private lateinit var nav: NavBar
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var lightOrb: Orb
    private lateinit var musicOrb: Orb
    private lateinit var deviceName: TextView
    private lateinit var deviceSub: TextView
    private lateinit var statusLine: TextView
    private lateinit var connectBtn: TextView
    private lateinit var hueSlider: PillSlider
    private lateinit var brightSlider: PillSlider
    private lateinit var manualBlock: LinearLayout
    private lateinit var musicActiveNote: TextView
    private lateinit var playBtn: PlayButton
    private lateinit var musicMsg: TextView
    private lateinit var segMic: TextView
    private lateinit var segInternal: TextView
    private lateinit var savedList: LinearLayout
    private lateinit var infoRows: LinearLayout
    private val bubbles = ArrayList<ColorBubble>()
    private val effectCards = ArrayList<EffectCard>()
    private val styleCards = ArrayList<StyleCard>()

    // =================================================================== ciclo de vida
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Th.init(this)
        store = Store(this)
        LightStick.init(applicationContext)
        loadSettings()

        window.statusBarColor = Th.SKY_TOP
        window.navigationBarColor = Th.SKY_BOTTOM
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        buildUi()
        showTab(0)
    }

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            ui.postDelayed(this, 120)
        }
    }

    override fun onResume() {
        super.onResume()
        ui.post(tick)
    }

    override fun onPause() {
        ui.removeCallbacks(tick)
        saveSettings()
        super.onPause()
    }

    private fun loadSettings() {
        baseColor = store.getInt("baseColor", 0x0000FF)
        brightness = store.getFloat("brightness", 1f)
        mode = store.getInt("mode", 1).coerceIn(1, 4)
        paletteId = store.getInt("palette", 1).coerceIn(0, 6)
        sourceId = store.getInt("source", 0).coerceIn(0, 1)
        minBright = store.getFloat("minBright", 0.04f)
        val c = manualRgb()
        LightStick.lastManual = intArrayOf(mode, c[0], c[1], c[2])
    }

    private fun saveSettings() {
        store.putInt("baseColor", baseColor)
        store.putFloat("brightness", brightness)
        store.putInt("mode", mode)
        store.putInt("palette", paletteId)
        store.putInt("source", sourceId)
        store.putFloat("minBright", minBright)
    }

    // =================================================================== estructura
    private fun buildUi() {
        val root = FrameLayout(this)
        root.addView(SkyBackground(this), FrameLayout.LayoutParams(-1, -1))

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.addView(buildHeader())
        val host = FrameLayout(this)
        column.addView(host, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(column, FrameLayout.LayoutParams(-1, -1))

        pages = arrayOf(page(buildLightPage()), page(buildMusicPage()), page(buildInfoPage()))
        for (p in pages) host.addView(p, FrameLayout.LayoutParams(-1, -1))

        nav = NavBar(this, listOf("Luz", "Música", "Info")) { showTab(it) }
        val navLp = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        navLp.setMargins(dp(18), 0, dp(18), dp(14))
        root.addView(nav, navLp)
        setContentView(root)
        updateChoices()
    }

    private fun page(content: LinearLayout): ScrollView {
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.overScrollMode = View.OVER_SCROLL_NEVER
        sv.clipToPadding = false
        content.setPadding(dp(16), dp(4), dp(16), dp(120))
        sv.addView(content)
        return sv
    }

    private fun column(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun LinearLayout.addGap(v: View, topDp: Int = 14) {
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(topDp)
        addView(v, lp)
    }

    private fun row(vararg views: View, gapDp: Int = 4): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        for (v in views) {
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.setMargins(dp(gapDp), dp(gapDp), dp(gapDp), dp(gapDp))
            addView(v, lp)
        }
    }

    private fun softButton(t: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = t
        textSize = 14.5f
        typeface = Th.bold
        gravity = Gravity.CENTER
        setTextColor(Th.NAVY)
        background = roundRect(Color.WHITE, dpf(24f), dp(2), Th.LINE)
        setPadding(dp(12), dp(11), dp(12), dp(11))
        isClickable = true
        setOnClickListener { onClick() }
        touchFx(dpf(24f))
    }

    private fun showTab(i: Int) {
        tab = i
        for (k in pages.indices) pages[k].visibility = if (k == i) View.VISIBLE else View.GONE
        pages[i].alpha = 0f
        pages[i].translationY = dpf(10f)
        pages[i].animate().alpha(1f).translationY(0f).setDuration(200).start()
        nav.select(i)
        if (i == 2) rebuildSaved()
    }

    // =================================================================== encabezado
    private fun buildHeader(): View {
        val h = LinearLayout(this)
        h.orientation = LinearLayout.HORIZONTAL
        h.gravity = Gravity.CENTER_VERTICAL
        h.setPadding(dp(22), dp(14), dp(18), dp(10))

        val titles = column()
        titles.addView(label("Light Stick", 30f, true))
        titles.addView(label("✦ color & music ✦", 14f, true, Th.PINK))
        h.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))

        val chip = LinearLayout(this)
        chip.orientation = LinearLayout.HORIZONTAL
        chip.gravity = Gravity.CENTER_VERTICAL
        chip.background = roundRect(Color.WHITE, dpf(20f))
        chip.elevation = dpf(3f)
        chip.setPadding(dp(12), dp(8), dp(14), dp(8))
        statusDot = View(this)
        statusDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Th.GREY)
        }
        chip.addView(statusDot, LinearLayout.LayoutParams(dp(10), dp(10)))
        statusText = label("Sin conexión", 13f, true)
        statusText.setPadding(dp(7), 0, 0, 0)
        chip.addView(statusText)
        h.addView(chip)
        return h
    }

    // =================================================================== página: Luz
    private fun buildLightPage(): LinearLayout {
        val col = column()

        // ---- tarjeta: mi lightstick
        val dev = card()
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        lightOrb = Orb(this)
        top.addView(lightOrb, LinearLayout.LayoutParams(dp(112), dp(112)))
        val txt = column()
        txt.setPadding(dp(10), 0, 0, 0)
        deviceName = label("Mi lightstick", 19f, true)
        deviceSub = label("", 12.5f, false, Th.NAVY_SOFT)
        statusLine = label("", 12.5f, false, Th.NAVY_SOFT)
        txt.addView(deviceName)
        txt.addView(deviceSub)
        txt.addView(statusLine)
        top.addView(txt, LinearLayout.LayoutParams(0, -2, 1f))
        dev.addView(top)
        connectBtn = pillButton("Conectar") { onConnectClick() }
        dev.addGap(connectBtn, 12)
        dev.addGap(row(softButton("Elegir otro") { openPicker() }, softButton("Identificar") { identify() }), 6)
        col.addGap(dev, 6)

        // ---- controles manuales
        manualBlock = column()
        musicActiveNote = chatBubble("El modo música está activo. Detenlo para usar estos controles.")
        musicActiveNote.visibility = View.GONE
        col.addGap(musicActiveNote, 12)

        val colorCard = card()
        colorCard.addView(cardTitle("Color", "Desliza o toca una burbuja"))
        hueSlider = PillSlider(this) { v ->
            setBase(Color.HSVToColor(floatArrayOf(v * 359f, 1f, 1f)) and 0xFFFFFF, fromSlider = true)
        }
        hueSlider.colors = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)) }
        colorCard.addView(hueSlider, LinearLayout.LayoutParams(-1, dp(42)))

        colorCard.addGap(label("Colores del lightstick", 14f, true), 14)
        val lsNames = arrayOf("Blanco", "Verde", "Rosa", "Azul", "Naranja", "Amarillo")
        val lsViews = lsNames.indices.map { i ->
            val rgb = Palettes.NEWJEANS[i]
            swatch(lsNames[i], rgb, 48, bubbles) { setBase(rgb) }
        }
        colorCard.addView(row(lsViews[0], lsViews[1], lsViews[2], gapDp = 2))
        colorCard.addView(row(lsViews[3], lsViews[4], lsViews[5], gapDp = 2))

        colorCard.addGap(label("Integrantes", 14f, true), 10)
        val mViews = Palettes.MEMBER_NAMES.indices.map { i ->
            val rgb = Palettes.MEMBER_COLORS[i]
            swatch(Palettes.MEMBER_NAMES[i], rgb, 44, bubbles) { setBase(rgb) }
        }
        colorCard.addView(row(*mViews.toTypedArray(), gapDp = 1))
        manualBlock.addGap(colorCard, 0)

        val brCard = card()
        brCard.addView(cardTitle("Brillo"))
        brightSlider = PillSlider(this) { v ->
            brightness = 0.03f + v * 0.97f
            sendManual()
        }
        brightSlider.value = ((brightness - 0.03f) / 0.97f)
        brCard.addView(brightSlider, LinearLayout.LayoutParams(-1, dp(42)))
        manualBlock.addGap(brCard)

        val fxCard = card()
        fxCard.addView(cardTitle("Efecto", "Los 4 efectos de la app oficial"))
        // Mismo orden de íconos que la app oficial; el número de modo sale del código de esa app.
        val fx = listOf(
            EffectCard(this, 0, "Encendido", 1),
            EffectCard(this, 1, "Destello doble", 4),
            EffectCard(this, 2, "Destello", 2),
            EffectCard(this, 3, "Burbujas", 3)
        )
        for (c in fx) {
            c.setOnClickListener {
                mode = c.mode
                updateChoices()
                sendManual()
            }
            effectCards.add(c)
        }
        fxCard.addView(row(fx[0], fx[1], gapDp = 5))
        fxCard.addView(row(fx[2], fx[3], gapDp = 5))
        manualBlock.addGap(fxCard)

        col.addGap(manualBlock, 14)
        updateHueFromBase()
        brightSlider.colors = intArrayOf(0xFF101018.toInt(), opaque(baseColor))
        updateChoices()
        return col
    }

    // =================================================================== página: Música
    private fun buildMusicPage(): LinearLayout {
        val col = column()

        val hero = card()
        hero.gravity = Gravity.CENTER_HORIZONTAL
        val stage = FrameLayout(this)
        musicOrb = Orb(this)
        stage.addView(musicOrb, FrameLayout.LayoutParams(dp(210), dp(210), Gravity.CENTER))
        playBtn = PlayButton(this)
        playBtn.isClickable = true
        playBtn.setOnClickListener { onMusicClick() }
        playBtn.touchFx(dpf(50f))
        stage.addView(playBtn, FrameLayout.LayoutParams(dp(96), dp(96), Gravity.CENTER))
        hero.addView(stage, LinearLayout.LayoutParams(-2, -2))
        musicMsg = chatBubble("Elige un estilo y toca ▶")
        val msgLp = LinearLayout.LayoutParams(-2, -2)
        msgLp.topMargin = dp(8)
        msgLp.gravity = Gravity.START
        hero.addView(musicMsg, msgLp)
        col.addGap(hero, 6)

        val styles = card()
        styles.addView(cardTitle("Estilo", "Cómo cambia la luz con el ritmo"))
        val rainbow = StyleCard(this, rainbowPreview(), dp(96), dp(22), "Arcoíris")
        val nj = StyleCard(this, DotsPreview(this, Palettes.NEWJEANS), dp(108), dp(22), "NewJeans")
        styles.addView(row(rainbow, nj, gapDp = 5))
        styleCards.add(rainbow)
        styleCards.add(nj)
        styles.addGap(label("Integrantes", 14f, true), 10)
        val memberCards = Palettes.MEMBER_NAMES.indices.map { i ->
            val b = ColorBubble(this, Palettes.MEMBER_COLORS[i])
            StyleCard(this, b, dp(36), dp(36), Palettes.MEMBER_NAMES[i], 11.5f)
        }
        styleCards.addAll(memberCards)
        styles.addView(row(*memberCards.toTypedArray(), gapDp = 2))
        styleCards.forEachIndexed { i, c ->
            c.setOnClickListener {
                paletteId = i
                updateChoices()
                if (Bus.musicRunning) restartMusicSoon()
            }
        }
        col.addGap(styles)

        val src = card()
        src.addView(cardTitle("¿De dónde viene la música?"))
        val seg = LinearLayout(this)
        seg.orientation = LinearLayout.HORIZONTAL
        seg.background = roundRect(0x1A1B2A6B, dpf(24f))
        seg.setPadding(dp(4), dp(4), dp(4), dp(4))
        segMic = segItem("Micrófono") { sourceId = 0; updateChoices() }
        segInternal = segItem("Este celular") { sourceId = 1; updateChoices() }
        seg.addView(segMic, LinearLayout.LayoutParams(0, -2, 1f))
        seg.addView(segInternal, LinearLayout.LayoutParams(0, -2, 1f))
        src.addView(seg)
        src.addGap(
            label(
                "Micrófono: concierto, bocina o cualquier sonido cercano.\n" +
                    "Este celular: lo que suena en tu teléfono (algunas apps lo bloquean).",
                12.5f, false, Th.NAVY_SOFT
            ), 10
        )
        col.addGap(src)

        val minCard = card()
        minCard.addView(cardTitle("Brillo mínimo", "Para que no se apague en los silencios"))
        val minSlider = PillSlider(this) { v -> minBright = v * 0.4f }
        minSlider.colors = intArrayOf(0xFF1B1F3A.toInt(), 0xFFB9C8FF.toInt())
        minSlider.value = minBright / 0.4f
        minCard.addView(minSlider, LinearLayout.LayoutParams(-1, dp(42)))
        minCard.addGap(
            label("Durante la música se usa el efecto elegido en Luz (recomendado: Encendido).", 12.5f, false, Th.NAVY_SOFT),
            10
        )
        col.addGap(minCard)
        return col
    }

    private fun segItem(t: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = t
        textSize = 14.5f
        typeface = Th.bold
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(11), dp(8), dp(11))
        isClickable = true
        setOnClickListener { onClick() }
    }

    // =================================================================== página: Info
    private fun buildInfoPage(): LinearLayout {
        val col = column()

        val devCard = card()
        devCard.addView(cardTitle("Lightstick conectado"))
        infoRows = column()
        devCard.addView(infoRows)
        devCard.addGap(row(softButton("Renombrar") { renameCurrent() }, softButton("Identificar") { identify() }), 10)
        col.addGap(devCard, 6)

        val savedCard = card()
        savedCard.addView(cardTitle("Mis lightsticks", "Toca uno para hacerlo el principal"))
        savedList = column()
        savedCard.addView(savedList)
        savedCard.addGap(softButton("Agregar / elegir otro") { openPicker() }, 10)
        col.addGap(savedCard)

        val tips = card()
        tips.addView(cardTitle("Consejos"))
        val msgs = listOf(
            "Antes de conectar, cierra la app oficial: el lightstick acepta una sola conexión.",
            "¿Muchos lightsticks cerca? Usa \"Elegir otro\": el más cercano tiene la señal más fuerte, y el final de la dirección es el mismo que muestra la app oficial en Ajustes → MAC.",
            "Con \"Identificar\" el lightstick conectado parpadea rosa y azul.",
            "Si el modo música con \"Este celular\" no reacciona, la app que suena bloquea la captura. Usa el micrófono.",
            "En Realme, si el modo música se corta con la pantalla apagada: Ajustes → Batería → esta app → permitir actividad en segundo plano."
        )
        for (m in msgs) {
            val b = chatBubble(m)
            val lp = LinearLayout.LayoutParams(-2, -2)
            lp.topMargin = dp(8)
            lp.rightMargin = dp(24)
            tips.addView(b, lp)
        }
        col.addGap(tips)

        val about = card()
        about.addView(cardTitle("Acerca de"))
        about.addView(
            label(
                "App personal, no oficial, para el NewJeans Official Light Stick.\n" +
                    "Solo escribe en la función de color/efecto (la misma que usa la app oficial); " +
                    "nunca toca la actualización de firmware.\n\n" +
                    "Los nombres de los efectos siguen los íconos de la app oficial.\n" +
                    "Fuente: Fredoka (SIL Open Font License).",
                13f, false, Th.NAVY_SOFT
            )
        )
        col.addGap(about)
        return col
    }

    private fun infoRow(k: String, v: String): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.setPadding(0, dp(5), 0, dp(5))
        r.addView(label(k, 14f, false, Th.NAVY_SOFT), LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(label(v, 14f, true))
        return r
    }

    private var lastInfoKey = ""

    private fun refreshInfoRows() {
        val addr = LightStick.address
        val name = if (addr != null) (store.nameOf(addr) ?: "Lightstick") else "—"
        val key = "$addr|$name|${LightStick.battery}|${LightStick.firmware}|${LightStick.model}"
        if (key == lastInfoKey) return
        lastInfoKey = key
        infoRows.removeAllViews()
        if (addr == null || !LightStick.connected) {
            infoRows.addView(label("Ninguno conectado ahora.", 14f, false, Th.NAVY_SOFT))
            return
        }
        infoRows.addView(infoRow("Nombre", name))
        infoRows.addView(infoRow("MAC", "**:**:" + macTail(addr)))
        if (LightStick.battery >= 0) infoRows.addView(infoRow("Batería", "${LightStick.battery}%"))
        if (LightStick.firmware.isNotEmpty()) infoRows.addView(infoRow("Firmware", LightStick.firmware))
        if (LightStick.model.isNotEmpty()) infoRows.addView(infoRow("Modelo", LightStick.model))
    }

    private fun rebuildSaved() {
        savedList.removeAllViews()
        val known = store.known()
        if (known.isEmpty()) {
            savedList.addView(label("Aún no guardas ninguno. Conéctate y se guarda solo.", 14f, false, Th.NAVY_SOFT))
            return
        }
        val def = store.defaultAddr
        for ((addr, name) in known) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            r.background = roundRect(if (addr == def) 0xFFEAF0FF.toInt() else Th.CARD_SOFT, dpf(18f), dp(2), if (addr == def) Th.BLUE else Color.WHITE)
            r.setPadding(dp(14), dp(10), dp(10), dp(10))
            val t = column()
            t.addView(label((if (addr == def) "★ " else "") + name, 15f, true))
            t.addView(label("**:**:" + macTail(addr), 12.5f, false, Th.NAVY_SOFT))
            r.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
            val del = label("Olvidar", 13f, true, Th.PINK)
            del.setPadding(dp(10), dp(8), dp(10), dp(8))
            del.setOnClickListener {
                store.forget(addr)
                rebuildSaved()
            }
            r.addView(del)
            r.isClickable = true
            r.setOnClickListener {
                store.defaultAddr = addr
                rebuildSaved()
                toast("$name es ahora tu lightstick principal")
            }
            r.touchFx(dpf(18f))
            savedList.addGap(r, 8)
        }
    }

    // =================================================================== refresco periódico
    private fun refresh() {
        val conn = LightStick.connected
        val connecting = LightStick.connecting
        val music = Bus.musicRunning

        statusText.text = when {
            conn -> "Conectado"
            connecting -> "Conectando"
            else -> "Sin conexión"
        }
        (statusDot.background as GradientDrawable).setColor(
            when {
                conn -> Th.GREEN_OK
                connecting -> Th.AMBER
                else -> Th.GREY
            }
        )

        val state = if (conn || connecting) 1 else 0
        if (state != lastConnState) {
            lastConnState = state
            if (state == 1) {
                connectBtn.text = "Desconectar"
                connectBtn.background = roundRect(Color.WHITE, dpf(30f), dp(2), Th.LINE)
                connectBtn.setTextColor(Th.NAVY)
            } else {
                connectBtn.background = gradientPill(dpf(30f), Th.BLUE, Th.PINK)
                connectBtn.setTextColor(Color.WHITE)
            }
        }
        if (state == 0) {
            connectBtn.text = if (store.defaultAddr != null) "Conectar a mi lightstick" else "Buscar mi lightstick"
        }

        val addr = LightStick.address ?: store.defaultAddr
        if (addr != null) {
            deviceName.text = store.nameOf(addr) ?: "Lightstick"
            val parts = ArrayList<String>()
            parts.add("**:**:" + macTail(addr))
            if (conn && LightStick.battery >= 0) parts.add("Batería ${LightStick.battery}%")
            deviceSub.text = parts.joinToString(" · ")
        } else {
            deviceName.text = "Mi lightstick"
            deviceSub.text = "Aún no hay uno guardado"
        }
        statusLine.text = LightStick.status

        // guardar como "mío" cuando la conexión se confirma
        val ps = pendingSave
        if (ps != null && conn && LightStick.address == ps.first) {
            if (store.nameOf(ps.first) == null) store.remember(ps.first, ps.second)
            store.defaultAddr = ps.first
            pendingSave = null
        }

        val manual = manualRgb()
        lightOrb.color = if (music) Color.rgb(Bus.r, Bus.g, Bus.b) else Color.rgb(manual[0], manual[1], manual[2])
        musicOrb.color = if (music) Color.rgb(Bus.r, Bus.g, Bus.b) else previewColor()
        playBtn.playing = music
        musicMsg.text = if (Bus.message.isNotEmpty()) Bus.message else "Elige un estilo y toca ▶"
        manualBlock.alpha = if (music) 0.4f else 1f
        musicActiveNote.visibility = if (music) View.VISIBLE else View.GONE
        if (tab == 2) refreshInfoRows()
    }

    private fun previewColor(): Int {
        val t = System.currentTimeMillis()
        return when (paletteId) {
            0 -> Color.HSVToColor(floatArrayOf(((t / 25) % 360).toFloat(), 0.85f, 1f))
            1 -> opaque(Palettes.NEWJEANS[((t / 700) % Palettes.NEWJEANS.size).toInt()])
            else -> opaque(Palettes.MEMBER_COLORS[(paletteId - 2).coerceIn(0, 4)])
        }
    }

    private fun updateChoices() {
        for (c in effectCards) c.setChosen(c.mode == mode)
        styleCards.forEachIndexed { i, c -> c.setChosen(i == paletteId) }
        for (b in bubbles) b.chosen = b.rgb == baseColor
        if (::segMic.isInitialized) {
            for ((i, s) in listOf(segMic, segInternal).withIndex()) {
                val on = i == sourceId
                s.background = if (on) roundRect(Color.WHITE, dpf(20f)) else null
                s.elevation = if (on) dpf(2f) else 0f
                s.setTextColor(if (on) Th.NAVY else Th.NAVY_SOFT)
            }
        }
    }

    // =================================================================== controles manuales
    private fun setBase(rgb: Int, fromSlider: Boolean = false) {
        baseColor = rgb and 0xFFFFFF
        if (!fromSlider) updateHueFromBase()
        brightSlider.colors = intArrayOf(0xFF101018.toInt(), opaque(baseColor))
        updateChoices()
        sendManual()
    }

    private fun updateHueFromBase() {
        val hsv = FloatArray(3)
        Color.colorToHSV(opaque(baseColor), hsv)
        hueSlider.value = hsv[0] / 359f
    }

    private fun manualRgb(): IntArray = intArrayOf(
        (((baseColor shr 16) and 255) * brightness).toInt(),
        (((baseColor shr 8) and 255) * brightness).toInt(),
        ((baseColor and 255) * brightness).toInt()
    )

    private fun sendManual() {
        val c = manualRgb()
        LightStick.lastManual = intArrayOf(mode, c[0], c[1], c[2])
        if (!Bus.musicRunning && !identifying) LightStick.setLed(mode, c[0], c[1], c[2])
    }

    /** Hace parpadear el lightstick conectado para reconocerlo. */
    private fun identify() {
        if (!LightStick.connected) {
            toast("Primero conecta un lightstick")
            return
        }
        if (Bus.musicRunning) {
            toast("Detén el modo música para identificar")
            return
        }
        if (identifying) return
        identifying = true
        val seq = intArrayOf(0xFF2D9B, 0x000000, 0x1E50FF, 0x000000, 0xFF2D9B, 0x000000, 0x1E50FF, 0x000000)
        seq.forEachIndexed { i, c ->
            ui.postDelayed({
                LightStick.setLed(1, (c shr 16) and 255, (c shr 8) and 255, c and 255)
            }, i * 220L)
        }
        ui.postDelayed({
            identifying = false
            val m = LightStick.lastManual
            LightStick.setLed(m[0], m[1], m[2], m[3])
        }, seq.size * 220L + 50L)
    }

    private fun renameCurrent() {
        val addr = LightStick.address ?: store.defaultAddr
        if (addr == null) {
            toast("Conecta o guarda un lightstick primero")
            return
        }
        val input = EditText(this)
        input.setText(store.nameOf(addr) ?: "Mi lightstick")
        input.setSelectAllOnFocus(true)
        AlertDialog.Builder(this)
            .setTitle("Nombre del lightstick")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                store.remember(addr, input.text.toString())
                lastInfoKey = ""
                rebuildSaved()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // =================================================================== conexión
    private fun blePerms(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun has(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()

    /** Revisa permisos y Bluetooth. Si falta un permiso, lo pide y luego ejecuta [after]. */
    private fun ensureBle(after: () -> Unit): Boolean {
        val perms = blePerms()
        if (!perms.all { has(it) }) {
            afterBle = after
            requestPermissions(perms, REQ_BLE)
            return false
        }
        if (!LightStick.bluetoothOn()) {
            toast("Enciende el Bluetooth del celular")
            return false
        }
        return true
    }

    private fun connectTo(addr: String, name: String) {
        pendingSave = Pair(addr, name)
        LightStick.connect(addr)
    }

    /** Botón principal: se conecta a "mi lightstick"; si no hay uno guardado, abre el selector. */
    private fun onConnectClick() {
        if (LightStick.connected || LightStick.connecting) {
            LightStick.disconnect()
            return
        }
        if (!ensureBle { onConnectClick() }) return
        val def = store.defaultAddr
        if (def == null) {
            openPicker()
            return
        }
        var handled = false
        LightStick.status = "Buscando tu lightstick…"
        LightStick.scan(6000, onUpdate = { list ->
            if (!handled && list.any { it.address == def }) {
                handled = true
                LightStick.stopScan()
                connectTo(def, store.nameOf(def) ?: "Mi lightstick")
            }
        }, done = { list ->
            if (!handled) {
                handled = true
                if (list.any { it.address == def }) {
                    connectTo(def, store.nameOf(def) ?: "Mi lightstick")
                } else {
                    LightStick.status = "No encontré tu lightstick guardado"
                    toast("No encontré tu lightstick guardado. ¿Está encendido? Puedes elegir otro.")
                    openPicker()
                }
            }
        })
    }

    /** Lista en vivo de lightsticks cercanos para elegir el tuyo. */
    private fun openPicker() {
        if (!ensureBle { openPicker() }) return
        if (LightStick.connected || LightStick.connecting) LightStick.disconnect()

        val dlg = Dialog(this)
        val box = card()
        box.addView(cardTitle("Elige tu lightstick", "Ordenados por cercanía. Compara el final de la dirección con la app oficial (Ajustes → MAC)."))
        val scanning = label("Buscando…", 13.5f, true, Th.PINK)
        box.addView(scanning)
        val list = column()
        val sv = ScrollView(this)
        sv.addView(list)
        box.addGap(sv, 8)
        (sv.layoutParams as LinearLayout.LayoutParams).height = dp(300)
        val cancel = softButton("Cancelar") { dlg.dismiss() }
        box.addGap(cancel, 10)

        val known = store.known()
        val def = store.defaultAddr

        fun render(found: List<Found>) {
            list.removeAllViews()
            val sorted = found.sortedWith(compareByDescending<Found> { known.containsKey(it.address) }.thenByDescending { it.rssi })
            for (f in sorted) {
                val r = LinearLayout(this)
                r.orientation = LinearLayout.HORIZONTAL
                r.gravity = Gravity.CENTER_VERTICAL
                val mine = known.containsKey(f.address)
                r.background = roundRect(if (mine) 0xFFEAF0FF.toInt() else Th.CARD_SOFT, dpf(18f), dp(2), if (f.address == def) Th.BLUE else Color.WHITE)
                r.setPadding(dp(12), dp(10), dp(12), dp(10))
                val icon = NavIcon(this, 0)
                icon.tint = if (mine) Th.BLUE else Th.NAVY_SOFT
                r.addView(icon, LinearLayout.LayoutParams(dp(26), dp(26)))
                val t = column()
                t.setPadding(dp(10), 0, 0, 0)
                val title = (if (f.address == def) "★ " else "") + (known[f.address] ?: f.name)
                t.addView(label(title, 15f, true))
                t.addView(label("**:**:" + macTail(f.address) + " · " + signalText(f.rssi), 12.5f, false, Th.NAVY_SOFT))
                r.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
                r.isClickable = true
                r.setOnClickListener {
                    LightStick.stopScan()
                    dlg.dismiss()
                    connectTo(f.address, known[f.address] ?: "Mi lightstick")
                    toast("Conectando… usa \"Identificar\" para confirmar que es el tuyo")
                }
                r.touchFx(dpf(18f))
                list.addGap(r, 8)
            }
        }

        dlg.setContentView(box)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dlg.window?.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dlg.setOnDismissListener { LightStick.stopScan() }
        dlg.show()

        LightStick.scan(8000, onUpdate = { render(it) }, done = { found ->
            render(found)
            scanning.text = if (found.isEmpty()) {
                "No encontré ninguno. Enciéndelo, acércalo y cierra la app oficial."
            } else {
                "Toca el tuyo"
            }
        })
    }

    // =================================================================== modo música
    private fun onMusicClick() {
        if (Bus.musicRunning) {
            stopService(Intent(this, MusicService::class.java))
            return
        }
        if (!LightStick.connected) {
            toast("Primero conecta tu lightstick (pestaña Luz)")
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

    /** Al cambiar de estilo con la música sonando, la reinicia con el estilo nuevo. */
    private fun restartMusicSoon() {
        if (sourceId == 1) {
            toast("El estilo nuevo se aplica al volver a iniciar")
            return
        }
        stopService(Intent(this, MusicService::class.java))
        ui.postDelayed({ if (!Bus.musicRunning && LightStick.connected) launchService(0, null) }, 400)
    }

    private fun startMusic() {
        if (sourceId == 1) {
            if (Build.VERSION.SDK_INT < 29) {
                toast("Requiere Android 10 o superior. Usa el micrófono.")
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
        i.putExtra(MusicService.EXTRA_SOURCE, if (data != null) 1 else 0)
        i.putExtra(MusicService.EXTRA_PALETTE, paletteId)
        i.putExtra(MusicService.EXTRA_MODE, mode)
        i.putExtra(MusicService.EXTRA_MINB, minBright)
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
                toast("Sin ese permiso no se puede usar el audio del celular")
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQ_BLE -> {
                if (blePerms().all { has(it) }) {
                    val a = afterBle
                    afterBle = null
                    a?.invoke()
                } else {
                    toast("Hace falta el permiso de Bluetooth")
                }
            }
            REQ_AUDIO -> {
                if (has(Manifest.permission.RECORD_AUDIO)) {
                    startMusic()
                } else {
                    toast("Hace falta el permiso del micrófono")
                }
            }
        }
    }
}
