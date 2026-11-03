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
import kotlin.math.max

class MainActivity : Activity() {

    private val REQ_BLE = 1
    private val REQ_AUDIO = 2
    private val REQ_PROJ = 3

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var store: Store

    // ---- estado
    private var baseColor = 0x1E5AFF   // color LED a brillo completo
    private var brightness = 1f        // 0.03..1
    private var effect = 0             // 0 fijo, 1 destello, 2 destello x2, 3 burbujas
    private var songIdx = -1           // -1 = detectar BPM solo
    private var lead = 0.08f           // adelanto de los destellos (s)
    private var reactive = false       // modo música: false = Ritmo, true = Reactivo
    private var paletteId = 1          // 0 arcoíris, 1 NewJeans, 2..6 integrantes
    private var sourceId = 0           // 0 micrófono, 1 audio del celular
    private var minBright = 0.04f
    private var tab = 0
    private var afterBle: (() -> Unit)? = null
    private var pendingSave: Pair<String, String>? = null
    private var lastConnState = -1
    private var wasConnected = false
    private var identifying = false
    private var seenLost = 0
    private var resumeMusicOnConnect = false
    private var lostDialog: Dialog? = null

    // ---- vistas
    private lateinit var sky: HalftoneSky
    private lateinit var pages: Array<View>
    private lateinit var nav: NavBar
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var lightOrb: Orb
    private lateinit var musicOrb: Orb
    private lateinit var deviceName: TextView
    private lateinit var deviceSub: TextView
    private lateinit var statusLine: TextView
    private lateinit var connectBtn: StickerText
    private lateinit var hueSlider: PillSlider
    private lateinit var brightSlider: PillSlider
    private lateinit var manualBlock: LinearLayout
    private lateinit var musicActiveNote: TextView
    private lateinit var playBtn: PlayButton
    private lateinit var musicMsg: TextView
    private lateinit var bpmText: StickerText
    private lateinit var bpmSub: TextView
    private lateinit var songBtn: TextView
    private lateinit var segMic: TextView
    private lateinit var segInternal: TextView
    private lateinit var segBeat: TextView
    private lateinit var segReactive: TextView
    private lateinit var root: FrameLayout
    private lateinit var mainColumn: LinearLayout
    private var welcome: View? = null
    private lateinit var savedList: LinearLayout
    private lateinit var infoRows: LinearLayout
    private val bubbles = ArrayList<ColorBubble>()
    private val effectCards = ArrayList<EffectCard>()
    private val styleCards = ArrayList<StyleCard>()
    private val bouncers = ArrayList<View>()

    // =================================================================== ciclo de vida
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        L.init(this)
        Th.init(this)
        store = Store(this)
        LightStick.init(applicationContext)
        loadSettings()
        seenLost = LightStick.lostCount

        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Th.LAV_BOTTOM
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        buildUi()
        showTab(0)
        if (store.getInt("welcomed", 0) == 0 || store.getInt("welcomeOpen", 0) == 1) showWelcome(0)
    }

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            ui.postDelayed(this, 100)
        }
    }

    override fun onResume() {
        super.onResume()
        sky.running = true
        ui.post(tick)
    }

    override fun onPause() {
        sky.running = false
        ui.removeCallbacks(tick)
        saveSettings()
        super.onPause()
    }

    private fun loadSettings() {
        baseColor = store.getInt("ledColor", 0x1E5AFF)
        brightness = store.getFloat("brightness", 1f)
        effect = store.getInt("effect", 0).coerceIn(0, 3)
        Effects.speed = store.getFloat("speed", 0.35f)
        songIdx = store.getInt("song", -1).coerceIn(-1, Songs.LIST.size - 1)
        lead = store.getFloat("lead", 0.08f)
        reactive = store.getInt("reactive", 0) == 1
        paletteId = store.getInt("palette", 1).coerceIn(0, 6)
        sourceId = store.getInt("source", 0).coerceIn(0, 1)
        minBright = store.getFloat("minBright", 0.04f)
        val c = manualRgb()
        LightStick.lastManual = intArrayOf(1, c[0], c[1], c[2])
    }

    private fun saveSettings() {
        store.putInt("ledColor", baseColor)
        store.putFloat("brightness", brightness)
        store.putInt("effect", effect)
        store.putFloat("speed", Effects.speed)
        store.putInt("song", songIdx)
        store.putFloat("lead", lead)
        store.putInt("reactive", if (reactive) 1 else 0)
        store.putInt("palette", paletteId)
        store.putInt("source", sourceId)
        store.putFloat("minBright", minBright)
    }

    // =================================================================== estructura
    private fun buildUi() {
        root = FrameLayout(this)
        sky = HalftoneSky(this)
        root.addView(sky, FrameLayout.LayoutParams(-1, -1))

        val column = LinearLayout(this)
        mainColumn = column
        column.orientation = LinearLayout.VERTICAL
        column.addView(buildHeader())
        val host = FrameLayout(this)
        column.addView(host, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(column, FrameLayout.LayoutParams(-1, -1))

        pages = arrayOf(page(buildLightPage()), page(buildMusicPage()), page(buildInfoPage()))
        for (p in pages) host.addView(p, FrameLayout.LayoutParams(-1, -1))

        nav = NavBar(this, listOf(tr("tab_light"), tr("tab_music"), tr("tab_info"))) { showTab(it) }
        val navLp = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        navLp.setMargins(dp(14), 0, dp(14), dp(12))
        root.addView(nav, navLp)
        setContentView(root)
        updateChoices()
    }

    private fun page(content: LinearLayout): ScrollView {
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.overScrollMode = View.OVER_SCROLL_NEVER
        sv.clipToPadding = false
        content.setPadding(dp(14), dp(4), dp(14), dp(118))
        sv.addView(content)
        return sv
    }

    private fun column(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun LinearLayout.addGap(v: View, topDp: Int = 16) {
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(topDp)
        addView(v, lp)
    }

    private fun row(vararg views: View, gapDp: Int = 4): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        isBaselineAligned = false
        for (v in views) {
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.setMargins(dp(gapDp), dp(gapDp), dp(gapDp), dp(gapDp))
            addView(v, lp)
        }
    }

    private fun sticker(kind: String, g: IntArray, sizeDp: Int, wobbleDeg: Float = 8f, ms: Long = 1700): StickerShape {
        val s = StickerShape(this, kind, g[0], g[1])
        s.layoutParams = FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        if (wobbleDeg > 0f) s.wobble(wobbleDeg, ms)
        return s
    }

    private fun place(parent: FrameLayout, v: View, gravity: Int, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) {
        val lp = v.layoutParams as FrameLayout.LayoutParams
        lp.gravity = gravity
        lp.setMargins(dp(l), dp(t), dp(r), dp(b))
        parent.addView(v, lp)
    }

    private fun showTab(i: Int) {
        tab = i
        for (k in pages.indices) pages[k].visibility = if (k == i) View.VISIBLE else View.GONE
        pages[i].alpha = 0f
        pages[i].translationY = dpf(12f)
        pages[i].animate().alpha(1f).translationY(0f).setDuration(220).start()
        nav.select(i)
        if (i == 2) rebuildSaved()
    }

    // =================================================================== encabezado
    private fun buildHeader(): View {
        val frame = FrameLayout(this)
        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.HORIZONTAL
        outer.isBaselineAligned = false
        outer.gravity = Gravity.CENTER_VERTICAL
        outer.setPadding(dp(12), dp(10), dp(14), dp(6))
        val logo = brandMark()
        logo.wobble(4f, 2200)
        outer.addView(logo, LinearLayout.LayoutParams(dp(62), dp(66)))
        val col = column()
        col.setPadding(dp(6), 0, 0, 0)
        outer.addView(col, LinearLayout.LayoutParams(0, -2, 1f))

        val titleRow = LinearLayout(this)
        titleRow.orientation = LinearLayout.HORIZONTAL
        titleRow.isBaselineAligned = false
        titleRow.gravity = Gravity.CENTER_VERTICAL
        titleRow.addView(reupLogo(), LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(36) })
        col.addView(titleRow, LinearLayout.LayoutParams(-1, -2))

        val sub = LinearLayout(this)
        sub.orientation = LinearLayout.HORIZONTAL
        sub.isBaselineAligned = false
        sub.gravity = Gravity.CENTER_VERTICAL
        val w1 = stickerText("light", 19f, Th.G_BLUE, 2.6f)
        val w2 = stickerText("stick", 19f, Th.G_PINK, 2.6f)
        val w3 = StickerShape(this, "sparkle", Th.G_YELLOW[0], Th.G_YELLOW[1])
        w3.layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
        val words = LinearLayout(this)
        words.orientation = LinearLayout.HORIZONTAL
        words.isBaselineAligned = false
        words.gravity = Gravity.CENTER_VERTICAL
        words.addView(w1)
        words.addView(w2)
        words.addView(w3)
        words.rotation = -3f
        sub.addView(words, LinearLayout.LayoutParams(0, -2, 1f))

        val chip = LinearLayout(this)
        chip.orientation = LinearLayout.HORIZONTAL
        chip.isBaselineAligned = false
        chip.gravity = Gravity.CENTER_VERTICAL
        chip.background = StickerDrawable(Color.WHITE, Color.WHITE, dpf(18f), dpf(2.2f), dpf(3f))
        chip.setPadding(dp(10), dp(6), dp(12) + dp(3), dp(6) + dp(3))
        statusDot = View(this)
        statusDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Th.GREY)
            setStroke(dp(2), Th.INK)
        }
        chip.addView(statusDot, LinearLayout.LayoutParams(dp(12), dp(12)))
        statusText = label(tr("st_none"), 13f, true)
        statusText.setPadding(dp(6), 0, 0, 0)
        chip.addView(statusText)
        sub.addView(chip)
        col.addView(sub)
        frame.addView(outer, FrameLayout.LayoutParams(-1, -2))

        place(frame, sticker("star", Th.G_BLUE, 40, 10f, 1900), Gravity.TOP or Gravity.END, r = 14, t = 4)
        place(frame, sticker("sparkle", Th.G_YELLOW, 26, 14f, 1300), Gravity.TOP or Gravity.END, r = 4, t = 40)
        return frame
    }

    // =================================================================== página: Luz
    private fun buildLightPage(): LinearLayout {
        val col = column()

        // ---- tarjeta: mi lightstick
        val devFrame = FrameLayout(this)
        val dev = card()
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.isBaselineAligned = false
        top.gravity = Gravity.CENTER_VERTICAL
        lightOrb = Orb(this)
        top.addView(lightOrb, LinearLayout.LayoutParams(dp(112), dp(112)))
        val txt = column()
        txt.setPadding(dp(10), 0, 0, 0)
        deviceName = label(tr("my_stick"), 20f, true)
        deviceSub = label("", 13f, false, Th.INK_SOFT)
        statusLine = label("", 13f, false, Th.INK_SOFT)
        txt.addView(deviceName)
        txt.addView(deviceSub)
        txt.addView(statusLine)
        top.addView(txt, LinearLayout.LayoutParams(0, -2, 1f))
        dev.addView(top)
        connectBtn = stickerButton(tr("btn_connect_mine"), Th.G_GREEN) { onConnectClick() }
        dev.addGap(connectBtn, 12)
        dev.addGap(row(softButton(tr("btn_pick")) { openPicker() }, softButton(tr("btn_identify")) { identify() }), 4)
        devFrame.addView(dev, FrameLayout.LayoutParams(-1, -2))
        place(devFrame, sticker("crown", Th.G_YELLOW, 34, 6f, 2100), Gravity.TOP or Gravity.START, l = 2, t = -6)
        col.addGap(devFrame, 8)

        musicActiveNote = chatBubble(tr("music_active_note"), mine = true)
        musicActiveNote.visibility = View.GONE
        col.addGap(musicActiveNote, 12)

        // ---- controles manuales
        manualBlock = column()

        val colorFrame = FrameLayout(this)
        val colorCard = card()
        colorCard.addView(cardTitle(tr("color"), tr("color_sub")))
        hueSlider = PillSlider(this) { v ->
            setBase(Color.HSVToColor(floatArrayOf(v * 359f, 1f, 1f)) and 0xFFFFFF, fromSlider = true)
        }
        hueSlider.colors = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 0.85f, 1f)) }
        colorCard.addView(hueSlider, LinearLayout.LayoutParams(-1, dp(44)))

        colorCard.addGap(pixelTitle(tr("ls_colors"), 14f), 14)
        val lsViews = Palettes.NEWJEANS_NAMES.indices.map { i ->
            val led = Palettes.NEWJEANS[i]
            swatch(colorName(i), Palettes.NEWJEANS_UI[i], led, 54, bubbles) { setBase(led) }
        }
        colorCard.addView(row(lsViews[0], lsViews[1], lsViews[2], gapDp = 2))
        colorCard.addView(row(lsViews[3], lsViews[4], lsViews[5], gapDp = 2))

        colorCard.addGap(pixelTitle(tr("members"), 14f), 10)
        val mViews = Palettes.MEMBER_NAMES.indices.map { i ->
            val led = Palettes.MEMBER_COLORS[i]
            swatch(memberName(i), Palettes.MEMBER_UI[i], led, 50, bubbles) { setBase(led) }
        }
        colorCard.addView(row(*mViews.toTypedArray(), gapDp = 1))
        colorFrame.addView(colorCard, FrameLayout.LayoutParams(-1, -2))
        place(colorFrame, sticker("heart", Th.G_PINK, 34, 9f, 1600), Gravity.TOP or Gravity.END, r = 14, t = -10)
        manualBlock.addGap(colorFrame, 0)

        val brCard = card()
        brCard.addView(cardTitle(tr("brightness")))
        brightSlider = PillSlider(this) { v ->
            brightness = 0.03f + v * 0.97f
            sendManual()
        }
        brightSlider.value = ((brightness - 0.03f) / 0.97f)
        brCard.addView(brightSlider, LinearLayout.LayoutParams(-1, dp(44)))
        manualBlock.addGap(brCard)

        val fxFrame = FrameLayout(this)
        val fxCard = card()
        fxCard.addView(cardTitle(tr("effect"), tr("effect_sub")))
        val fx = listOf(
            EffectCard(this, 0, tr("fx_solid"), 0),
            EffectCard(this, 2, tr("fx_flash"), 1),
            EffectCard(this, 1, tr("fx_flash2"), 2),
            EffectCard(this, 3, tr("fx_bubbles"), 3)
        )
        for (c in fx) {
            c.setOnClickListener {
                effect = c.mode
                updateChoices()
                sendManual()
            }
            effectCards.add(c)
        }
        fxCard.addView(row(fx[0], fx[1], gapDp = 5))
        fxCard.addView(row(fx[2], fx[3], gapDp = 5))
        val spHead = LinearLayout(this)
        spHead.orientation = LinearLayout.HORIZONTAL
        spHead.isBaselineAligned = false
        spHead.addView(pixelTitle(tr("speed"), 14f), LinearLayout.LayoutParams(0, -2, 1f))
        spHead.addView(label(tr("speed_hint"), 12.5f, false, Th.INK_SOFT))
        fxCard.addGap(spHead, 12)
        val speedSlider = PillSlider(this) { v -> Effects.speed = v }
        speedSlider.colors = intArrayOf(0xFFE2D8FF.toInt(), 0xFFFF7AD9.toInt())
        speedSlider.value = Effects.speed
        fxCard.addView(speedSlider, LinearLayout.LayoutParams(-1, dp(44)))
        fxFrame.addView(fxCard, FrameLayout.LayoutParams(-1, -2))
        place(fxFrame, sticker("sparkle", Th.G_BLUE, 34, 12f, 1500), Gravity.TOP or Gravity.END, r = 14, t = -8)
        manualBlock.addGap(fxFrame)

        col.addGap(manualBlock, 14)
        updateHueFromBase()
        brightSlider.colors = intArrayOf(0xFF101018.toInt(), uiColorFor(baseColor))
        return col
    }

    // =================================================================== página: Música
    private fun buildMusicPage(): LinearLayout {
        val col = column()

        val hero = card()
        hero.gravity = Gravity.CENTER_HORIZONTAL
        val stage = FrameLayout(this)
        musicOrb = Orb(this)
        stage.addView(musicOrb, FrameLayout.LayoutParams(dp(220), dp(220), Gravity.CENTER))
        playBtn = PlayButton(this)
        playBtn.isClickable = true
        playBtn.setOnClickListener { onMusicClick() }
        playBtn.touchFx(dpf(50f))
        stage.addView(playBtn, FrameLayout.LayoutParams(dp(100), dp(100), Gravity.CENTER))
        val n1 = sticker("notes", Th.G_BLUE, 46, 10f, 1200)
        val n2 = sticker("notes", Th.G_YELLOW, 38, 12f, 1000)
        val n3 = sticker("notes", Th.G_PINK, 40, 9f, 1400)
        val s1 = sticker("star", Th.G_YELLOW, 30, 14f, 1700)
        place(stage, n1, Gravity.TOP or Gravity.START, l = 0, t = 6)
        place(stage, n2, Gravity.BOTTOM or Gravity.END, r = 2, b = 14)
        place(stage, n3, Gravity.TOP or Gravity.END, r = 4, t = 18)
        place(stage, s1, Gravity.BOTTOM or Gravity.START, l = 12, b = 6)
        bouncers.addAll(listOf(n1, n2, n3, s1))
        hero.addView(stage, LinearLayout.LayoutParams(-1, dp(230)))
        val bpmRow = LinearLayout(this)
        bpmRow.orientation = LinearLayout.HORIZONTAL
        bpmRow.isBaselineAligned = false
        bpmRow.gravity = Gravity.CENTER_VERTICAL
        bpmText = stickerText("— BPM", 26f, Th.G_YELLOW, 3f)
        bpmRow.addView(bpmText)
        bpmSub = label("", 13.5f, true, Th.INK_SOFT)
        bpmSub.setPadding(dp(8), 0, 0, 0)
        bpmRow.addView(bpmSub)
        hero.addView(bpmRow)
        musicMsg = chatBubble(tr("music_hint"))
        val msgLp = LinearLayout.LayoutParams(-2, -2)
        msgLp.topMargin = dp(6)
        msgLp.gravity = Gravity.START
        hero.addView(musicMsg, msgLp)
        col.addGap(hero, 8)

        val styles = card()
        styles.addView(cardTitle(tr("style"), tr("style_sub")))
        val rainbow = StyleCard(this, RainbowPreview(this), dp(96), dp(24), tr("rainbow"))
        val nj = StyleCard(this, DotsPreview(this, Palettes.NEWJEANS_UI), dp(112), dp(24), "NewJeans")
        styles.addView(row(rainbow, nj, gapDp = 5))
        styleCards.add(rainbow)
        styleCards.add(nj)
        styles.addGap(pixelTitle(tr("members"), 14f), 10)
        val memberCards = Palettes.MEMBER_NAMES.indices.map { i ->
            val b = ColorBubble(this, Palettes.MEMBER_UI[i], Palettes.MEMBER_COLORS[i])
            StyleCard(this, b, dp(38), dp(38), memberName(i), 11.5f)
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

        val modeCard = card()
        modeCard.addView(cardTitle(tr("mode")))
        val modeSeg = LinearLayout(this)
        modeSeg.orientation = LinearLayout.HORIZONTAL
        modeSeg.isBaselineAligned = false
        modeSeg.background = StickerDrawable(0xFFEDE6FF.toInt(), 0xFFE2D8FF.toInt(), dpf(24f), dpf(2.2f), dpf(3f))
        modeSeg.setPadding(dp(5), dp(5), dp(5) + dp(3), dp(5) + dp(3))
        segBeat = segItem(tr("mode_beat")) {
            reactive = false
            updateChoices()
            if (Bus.musicRunning) restartMusicSoon()
        }
        segReactive = segItem(tr("mode_reactive")) {
            reactive = true
            updateChoices()
            if (Bus.musicRunning) restartMusicSoon()
        }
        modeSeg.addView(segBeat, LinearLayout.LayoutParams(0, -2, 1f))
        modeSeg.addView(segReactive, LinearLayout.LayoutParams(0, -2, 1f))
        modeCard.addView(modeSeg)
        modeCard.addGap(label(tr("mode_hint"), 13f, false, Th.INK_SOFT), 10)
        col.addGap(modeCard)

        val src = card()
        src.addView(cardTitle(tr("source")))
        val seg = LinearLayout(this)
        seg.orientation = LinearLayout.HORIZONTAL
        seg.isBaselineAligned = false
        seg.background = StickerDrawable(0xFFEDE6FF.toInt(), 0xFFE2D8FF.toInt(), dpf(24f), dpf(2.2f), dpf(3f))
        seg.setPadding(dp(5), dp(5), dp(5) + dp(3), dp(5) + dp(3))
        segMic = segItem(tr("src_mic")) { sourceId = 0; updateChoices() }
        segInternal = segItem(tr("src_phone")) { sourceId = 1; updateChoices() }
        seg.addView(segMic, LinearLayout.LayoutParams(0, -2, 1f))
        seg.addView(segInternal, LinearLayout.LayoutParams(0, -2, 1f))
        src.addView(seg)
        src.addGap(
            label(
                tr("src_hint"),
                13f, false, Th.INK_SOFT
            ), 10
        )
        col.addGap(src)

        val songCard = card()
        songCard.addView(cardTitle(tr("song"), tr("song_sub")))
        songBtn = softButton(songLabel()) { openSongPicker() }
        songCard.addView(songBtn)
        val syncHead = LinearLayout(this)
        syncHead.orientation = LinearLayout.HORIZONTAL
        syncHead.isBaselineAligned = false
        syncHead.addView(pixelTitle(tr("sync"), 14f), LinearLayout.LayoutParams(0, -2, 1f))
        syncHead.addView(label(tr("sync_hint"), 12.5f, false, Th.INK_SOFT))
        songCard.addGap(syncHead, 14)
        val syncSlider = PillSlider(this) { v -> lead = v * 0.25f }
        syncSlider.colors = intArrayOf(0xFFDDF6FF.toInt(), 0xFF22C3FF.toInt())
        syncSlider.value = lead / 0.25f
        songCard.addView(syncSlider, LinearLayout.LayoutParams(-1, dp(44)))
        songCard.addGap(
            label(tr("sync_help"), 13f, false, Th.INK_SOFT),
            8
        )
        col.addGap(songCard)

        val minCard = card()
        minCard.addView(cardTitle(tr("min_bright"), tr("min_bright_sub")))
        val minSlider = PillSlider(this) { v -> minBright = v * 0.4f }
        minSlider.colors = intArrayOf(0xFF1B1F3A.toInt(), 0xFFB9C8FF.toInt())
        minSlider.value = minBright / 0.4f
        minCard.addView(minSlider, LinearLayout.LayoutParams(-1, dp(44)))
        col.addGap(minCard)
        return col
    }

    private fun segItem(t: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = t
        textSize = 15f
        typeface = Th.roundBold
        gravity = Gravity.CENTER
        setTextColor(Th.INK)
        setPadding(dp(8), dp(10), dp(8) + dp(3), dp(10) + dp(3))
        fitOneLine(15f, 9)
        isClickable = true
        setOnClickListener { onClick() }
    }

    // =================================================================== página: Info
    private fun buildInfoPage(): LinearLayout {
        val col = column()

        val devCard = card()
        devCard.addView(cardTitle(tr("connected_title")))
        infoRows = column()
        devCard.addView(infoRows)
        devCard.addGap(row(softButton(tr("btn_rename")) { renameCurrent() }, softButton(tr("btn_identify")) { identify() }), 10)
        col.addGap(devCard, 8)

        val savedCard = card()
        savedCard.addView(cardTitle(tr("my_sticks"), tr("my_sticks_sub")))
        savedList = column()
        savedCard.addView(savedList)
        savedCard.addGap(softButton(tr("btn_add")) { openPicker() }, 10)
        col.addGap(savedCard)

        val tipsFrame = FrameLayout(this)
        val tips = card()
        tips.addView(cardTitle(tr("tips")))
        val msgs = listOf(
            tr("tip1") to false,
            tr("tip2q") to true,
            tr("tip2a") to false,
            tr("tip3q") to true,
            tr("tip3a") to false,
            tr("tip4") to false,
            tr("tip5") to false,
            tr("tip6") to false
        )
        for ((m, mine) in msgs) {
            val b = chatBubble(m, mine)
            val lp = LinearLayout.LayoutParams(-2, -2)
            lp.topMargin = dp(8)
            if (mine) {
                lp.gravity = Gravity.END
                lp.leftMargin = dp(40)
            } else {
                lp.rightMargin = dp(30)
            }
            tips.addView(b, lp)
        }
        tipsFrame.addView(tips, FrameLayout.LayoutParams(-1, -2))
        place(tipsFrame, sticker("bubble", Th.G_GREEN, 40, 8f, 1800), Gravity.TOP or Gravity.END, r = 14, t = -8)
        col.addGap(tipsFrame)

        val langCard = card()
        langCard.addView(cardTitle(tr("language")))
        langCard.addView(languageChooser())
        langCard.addGap(softButton(tr("btn_welcome")) { showWelcome(0) }, 10)
        col.addGap(langCard)

        val about = card()
        about.addView(cardTitle(tr("about")))
        val aboutHead = LinearLayout(this)
        aboutHead.orientation = LinearLayout.HORIZONTAL
        aboutHead.isBaselineAligned = false
        aboutHead.gravity = Gravity.CENTER_VERTICAL
        aboutHead.addView(brandMark(), LinearLayout.LayoutParams(dp(54), dp(58)))
        aboutHead.addView(reupLogo(), LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(8) })
        about.addView(aboutHead)
        about.addGap(label(tr("about_body"), 13.5f, false, Th.INK), 6)
        about.addGap(stickerText(tr("made_by"), 17f, Th.G_PINK, 2.5f), 10)
        about.addGap(label(tr("fonts_credit"), 12f, false, Th.INK_SOFT), 8)
        col.addGap(about)
        return col
    }

    private fun infoRow(k: String, v: String): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.isBaselineAligned = false
        r.setPadding(0, dp(5), 0, dp(5))
        r.addView(label(k, 14.5f, false, Th.INK_SOFT), LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(label(v, 14.5f, true))
        return r
    }

    private var lastInfoKey = ""

    private fun refreshInfoRows() {
        val addr = LightStick.address
        val name = if (addr != null) (store.nameOf(addr) ?: "Lightstick") else "—"
        val key = "$addr|$name|${LightStick.connected}|${LightStick.battery}|${LightStick.firmware}|${LightStick.model}"
        if (key == lastInfoKey) return
        lastInfoKey = key
        infoRows.removeAllViews()
        if (addr == null || !LightStick.connected) {
            infoRows.addView(label(tr("none_connected"), 14.5f, false, Th.INK_SOFT))
            return
        }
        infoRows.addView(infoRow(tr("row_name"), name))
        infoRows.addView(infoRow("MAC", "**:**:" + macTail(addr)))
        if (LightStick.battery >= 0) infoRows.addView(infoRow(tr("row_battery"), "${LightStick.battery}%"))
        if (LightStick.firmware.isNotEmpty()) infoRows.addView(infoRow(tr("row_fw"), LightStick.firmware))
        if (LightStick.model.isNotEmpty()) infoRows.addView(infoRow(tr("row_model"), LightStick.model))
    }

    private fun rebuildSaved() {
        savedList.removeAllViews()
        val known = store.known()
        if (known.isEmpty()) {
            savedList.addView(label(tr("none_saved_long"), 14.5f, false, Th.INK_SOFT))
            return
        }
        val def = store.defaultAddr
        for ((addr, name) in known) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.isBaselineAligned = false
            r.gravity = Gravity.CENTER_VERTICAL
            val g = if (addr == def) Th.G_YELLOW else Th.G_WHITE
            r.background = StickerDrawable(g[0], g[1], dpf(18f), dpf(2.2f), dpf(3f))
            r.setPadding(dp(14), dp(10), dp(10) + dp(3), dp(10) + dp(3))
            val t = column()
            t.addView(label((if (addr == def) "★ " else "") + name, 15.5f, true))
            t.addView(label("**:**:" + macTail(addr), 13f, false, Th.INK_SOFT))
            r.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
            val del = label(tr("forget"), 13.5f, true, 0xFFE0249B.toInt())
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
                toast(tr("made_main", name))
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
            conn -> tr("st_connected")
            connecting -> tr("st_connecting_short")
            else -> tr("st_none")
        }
        (statusDot.background as GradientDrawable).setColor(
            when {
                conn -> Th.OK
                connecting -> Th.AMBER
                else -> Th.GREY
            }
        )

        // al quedar conectado, retomar el color/efecto elegido
        if (conn && !wasConnected && !music) sendManual()
        wasConnected = conn

        val state = if (conn || connecting) 1 else 0
        if (state != lastConnState) {
            lastConnState = state
            if (state == 1) {
                connectBtn.text = tr("btn_disconnect")
                connectBtn.background = StickerDrawable(Th.G_WHITE[0], Th.G_WHITE[1], dpf(30f), dpf(2.5f), dpf(4f), false)
                connectBtn.fillTop = Th.INK
                connectBtn.fillBottom = Th.INK
                connectBtn.outline = 0f
            } else {
                connectBtn.background = StickerDrawable(Th.G_GREEN[0], Th.G_GREEN[1], dpf(30f), dpf(2.5f), dpf(4f), true)
                connectBtn.fillTop = Color.WHITE
                connectBtn.fillBottom = Color.WHITE
                connectBtn.outline = dpf(2.6f)
            }
            connectBtn.invalidate()
        }
        if (state == 0) {
            connectBtn.text = if (store.defaultAddr != null) tr("btn_connect_mine") else tr("btn_find")
        }

        val addr = LightStick.address ?: store.defaultAddr
        if (addr != null) {
            deviceName.text = store.nameOf(addr) ?: "Lightstick"
            val parts = ArrayList<String>()
            parts.add("**:**:" + macTail(addr))
            if (conn && LightStick.battery >= 0) parts.add(tr("battery_x", LightStick.battery))
            deviceSub.text = parts.joinToString(" · ")
        } else {
            deviceName.text = tr("my_stick")
            deviceSub.text = tr("none_saved_short")
        }
        statusLine.text = LightStick.status.ifEmpty { tr("st_disconnected") }

        // guardar como "mío" cuando la conexión se confirma
        val ps = pendingSave
        if (ps != null && conn && LightStick.address == ps.first) {
            if (store.nameOf(ps.first) == null) store.remember(ps.first, ps.second)
            store.defaultAddr = ps.first
            pendingSave = null
        }

        // ¿se desconectó solo?
        if (LightStick.lostCount != seenLost) {
            seenLost = LightStick.lostCount
            showLostDialog()
        }
        // al reconectar, seguir con la música
        if (resumeMusicOnConnect && conn) {
            resumeMusicOnConnect = false
            Bus.pausedByDisconnect = false
            if (!Bus.musicRunning && has(Manifest.permission.RECORD_AUDIO)) launchService(0, null)
        }

        val manual = manualRgb()
        val live = Color.rgb(Bus.r, Bus.g, Bus.b)
        lightOrb.color = if (music) live else Color.rgb(manual[0], manual[1], manual[2])
        musicOrb.color = if (music) live else previewColor()
        playBtn.playing = music
        musicMsg.text = if (Bus.message.isNotEmpty()) Bus.message else tr("music_hint")
        if (music && Bus.bpm > 0f && !reactive) {
            bpmText.text = "${Bus.bpm.toInt()} BPM"
            bpmSub.text = if (reactive) tr("reactive_now") else if (Bus.locked) tr("in_rhythm") else tr("finding_rhythm")
        } else {
            bpmText.text = if (songIdx >= 0) "${Songs.LIST[songIdx].second.toInt()} BPM" else "— BPM"
            bpmSub.text = if (music) (if (reactive) tr("reactive_now") else tr("listening")) else if (songIdx >= 0) Songs.LIST[songIdx].first else tr("auto_detect_short")
        }
        manualBlock.alpha = if (music) 0.4f else 1f
        musicActiveNote.visibility = if (music) View.VISIBLE else View.GONE

        // los stickers de notas brincan con la música
        val k = if (music) max(Bus.r, max(Bus.g, Bus.b)) / 255f else 0f
        for (b in bouncers) {
            b.scaleX = 1f + 0.22f * k
            b.scaleY = 1f + 0.22f * k
        }
        if (tab == 2) refreshInfoRows()
    }

    private fun previewColor(): Int {
        val t = System.currentTimeMillis()
        return when (paletteId) {
            0 -> Color.HSVToColor(floatArrayOf(((t / 25) % 360).toFloat(), 0.8f, 1f))
            1 -> opaque(Palettes.NEWJEANS_UI[((t / 700) % Palettes.NEWJEANS_UI.size).toInt()])
            else -> opaque(Palettes.MEMBER_UI[(paletteId - 2).coerceIn(0, 4)])
        }
    }

    private fun updateChoices() {
        for (c in effectCards) c.setChosen(c.mode == effect)
        styleCards.forEachIndexed { i, c -> c.setChosen(i == paletteId) }
        for (b in bubbles) b.chosen = b.led == baseColor
        if (::segMic.isInitialized) {
            for ((i, s) in listOf(segMic, segInternal).withIndex()) segOn(s, i == sourceId)
        }
        if (::segBeat.isInitialized) {
            segOn(segBeat, !reactive)
            segOn(segReactive, reactive)
        }
    }

    private fun segOn(s: TextView, on: Boolean) {
        s.background = if (on) StickerDrawable(Th.G_YELLOW[0], Th.G_YELLOW[1], dpf(20f), dpf(2.2f), dpf(3f), true) else null
        s.setTextColor(if (on) Th.INK else Th.INK_SOFT)
    }

    private val colorKeys = arrayOf("c_white", "c_green", "c_pink", "c_blue", "c_orange", "c_yellow")
    private val memberKeys = arrayOf("m_minji", "m_hanni", "m_danielle", "m_haerin", "m_hyein")
    private fun colorName(i: Int) = tr(colorKeys[i])
    private fun memberName(i: Int) = tr(memberKeys[i])

    // =================================================================== controles manuales
    /** Color de pantalla para un color LED (si es de la paleta, el pastel de la paleta). */
    private fun uiColorFor(led: Int): Int {
        val i1 = Palettes.NEWJEANS.indexOf(led)
        if (i1 >= 0) return opaque(Palettes.NEWJEANS_UI[i1])
        val i2 = Palettes.MEMBER_COLORS.indexOf(led)
        if (i2 >= 0) return opaque(Palettes.MEMBER_UI[i2])
        return opaque(led)
    }

    private fun setBase(rgb: Int, fromSlider: Boolean = false) {
        baseColor = rgb and 0xFFFFFF
        if (!fromSlider) updateHueFromBase()
        brightSlider.colors = intArrayOf(0xFF101018.toInt(), uiColorFor(baseColor))
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
        LightStick.lastManual = intArrayOf(1, c[0], c[1], c[2])
        Effects.apply(effect, c[0], c[1], c[2])
    }

    /** Hace parpadear el lightstick conectado para reconocerlo. */
    private fun identify() {
        if (!LightStick.connected) {
            toast(tr("t_connect_first"))
            return
        }
        if (Bus.musicRunning) {
            toast(tr("t_stop_music_identify"))
            return
        }
        if (identifying) return
        identifying = true
        Effects.paused = true
        val seq = intArrayOf(0xFF1ED2, 0x000000, 0x1E5AFF, 0x000000, 0xFF1ED2, 0x000000, 0x1E5AFF, 0x000000)
        seq.forEachIndexed { i, c ->
            ui.postDelayed({
                LightStick.setLed(1, (c shr 16) and 255, (c shr 8) and 255, c and 255)
            }, i * 220L)
        }
        ui.postDelayed({
            identifying = false
            Effects.paused = false
            sendManual()
        }, seq.size * 220L + 80L)
    }

    private fun renameCurrent() {
        val addr = LightStick.address ?: store.defaultAddr
        if (addr == null) {
            toast(tr("t_save_first"))
            return
        }
        val input = EditText(this)
        input.setText(store.nameOf(addr) ?: tr("my_stick"))
        input.setSelectAllOnFocus(true)
        AlertDialog.Builder(this)
            .setTitle(tr("rename_title"))
            .setView(input)
            .setPositiveButton(tr("save")) { _, _ ->
                store.remember(addr, input.text.toString())
                lastInfoKey = ""
                rebuildSaved()
            }
            .setNegativeButton(tr("cancel"), null)
            .show()
    }

    // =================================================================== diálogos estilo sticker
    private fun stickerDialog(): Pair<Dialog, LinearLayout> {
        val dlg = Dialog(this)
        val box = card()
        dlg.setContentView(box)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dlg.window?.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        return Pair(dlg, box)
    }

    /** Aviso de desconexión inesperada, con opción de reconectar (y seguir la música). */
    private fun showLostDialog() {
        lostDialog?.dismiss()
        val wasMusic = Bus.pausedByDisconnect
        val (dlg, box) = stickerDialog()
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.isBaselineAligned = false
        head.gravity = Gravity.CENTER_VERTICAL
        val hs = StickerShape(this, "heart", Th.G_PINK[0], Th.G_PINK[1])
        hs.wobble(10f, 900)
        head.addView(hs, LinearLayout.LayoutParams(dp(42), dp(42)))
        head.addView(stickerText(tr("lost_title"), 24f, Th.G_PINK, 3f))
        box.addView(head)
        box.addGap(
            label(
                if (wasMusic) tr("lost_music")
                else tr("lost_plain"),
                15f
            ), 8
        )
        val again = stickerButton(if (wasMusic && sourceId == 0) tr("btn_reconnect_resume") else tr("btn_reconnect"), Th.G_GREEN) {
            dlg.dismiss()
            reconnectLost(wasMusic)
        }
        box.addGap(again, 14)
        box.addGap(softButton(tr("btn_not_now")) { dlg.dismiss() }, 4)
        lostDialog = dlg
        dlg.show()
    }

    private fun reconnectLost(resumeMusic: Boolean) {
        val addr = LightStick.lostAddress ?: store.defaultAddr
        if (addr == null) {
            openPicker()
            return
        }
        if (!ensureBle { reconnectLost(resumeMusic) }) return
        resumeMusicOnConnect = resumeMusic && sourceId == 0
        if (resumeMusic && sourceId == 1) toast(tr("t_resume_tap_play"))
        connectTo(addr, store.nameOf(addr) ?: tr("my_stick"))
    }

    private fun songLabel(): String =
        if (songIdx >= 0) "♪ ${Songs.LIST[songIdx].first} · ${Songs.LIST[songIdx].second.toInt()} BPM" else "♪ " + tr("song_auto")

    private fun openSongPicker() {
        val (dlg, box) = stickerDialog()
        box.addView(cardTitle(tr("song"), tr("song_picker_sub")))
        val list = column()
        val sv = ScrollView(this)
        sv.addView(list)
        box.addView(sv)
        (sv.layoutParams as LinearLayout.LayoutParams).height = dp(380)
        val opts = ArrayList<Pair<String, Int>>()
        opts.add(tr("song_auto") to -1)
        Songs.LIST.forEachIndexed { i, s -> opts.add("${s.first} · ${s.second.toInt()} BPM" to i) }
        for ((t, idx) in opts) {
            val b = softButton(t) {
                songIdx = idx
                songBtn.text = songLabel()
                dlg.dismiss()
                if (Bus.musicRunning) restartMusicSoon()
            }
            if (idx == songIdx) b.background = StickerDrawable(Th.G_YELLOW[0], Th.G_YELLOW[1], dpf(24f), dpf(2.2f), dpf(3f))
            list.addGap(b, 6)
        }
        box.addGap(softButton(tr("close")) { dlg.dismiss() }, 10)
        dlg.show()
    }

    // =================================================================== idioma
    /** Botones: Automático + los 5 idiomas. Al elegir, se guarda y la pantalla se vuelve a crear. */
    private fun languageChooser(): LinearLayout {
        val box = column()
        val current = L.pref(this)
        val opts = ArrayList<Pair<String, String>>()
        opts.add("auto" to tr("lang_auto"))
        for (i in L.CODES.indices) opts.add(L.CODES[i] to L.NAMES[i])
        val first = softButton(opts[0].second) { chooseLanguage("auto") }
        if (current == "auto") first.background = StickerDrawable(Th.G_YELLOW[0], Th.G_YELLOW[1], dpf(24f), dpf(2.2f), dpf(3f), true)
        box.addView(first)
        var i = 1
        while (i < opts.size) {
            val views = ArrayList<View>()
            for (k in i until minOf(i + 2, opts.size)) {
                val code = opts[k].first
                val b = softButton(opts[k].second) { chooseLanguage(code) }
                if (current == code) b.background = StickerDrawable(Th.G_YELLOW[0], Th.G_YELLOW[1], dpf(24f), dpf(2.2f), dpf(3f), true)
                views.add(b)
            }
            if (views.size == 1) views.add(View(this))
            box.addView(row(*views.toTypedArray()))
            i += 2
        }
        return box
    }

    private fun chooseLanguage(code: String) {
        saveSettings()
        L.setPref(this, code)
        if (LightStick.status.isNotEmpty() && !LightStick.connected && !LightStick.connecting) LightStick.status = ""
        recreate()
    }

    // =================================================================== bienvenida
    private fun showWelcome(page: Int) {
        store.putInt("welcomeOpen", 1)
        welcome?.let { root.removeView(it) }
        mainColumn.visibility = View.INVISIBLE
        nav.visibility = View.INVISIBLE

        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        val col = column()
        col.setPadding(dp(18), dp(28), dp(18), dp(28))
        sv.addView(col)

        val top = FrameLayout(this)
        val titles = column()
        titles.gravity = Gravity.CENTER_HORIZONTAL
        val big = brandMark()
        big.wobble(5f, 2000)
        titles.addView(big, LinearLayout.LayoutParams(dp(150), dp(160)))
        val wl = reupLogo()
        wl.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        titles.addView(wl, LinearLayout.LayoutParams(-1, dp(96)).apply { topMargin = dp(6) })
        val words = LinearLayout(this)
        words.orientation = LinearLayout.HORIZONTAL
        words.isBaselineAligned = false
        words.gravity = Gravity.CENTER
        words.addView(stickerText("light", 24f, Th.G_BLUE, 3f))
        words.addView(stickerText("stick", 24f, Th.G_PINK, 3f))
        words.rotation = -3f
        titles.addView(words, LinearLayout.LayoutParams(-1, -2))
        top.addView(titles, FrameLayout.LayoutParams(-1, -2))
        place(top, sticker("heart", Th.G_PINK, 40, 10f, 1500), Gravity.BOTTOM or Gravity.START, l = 0, b = -14)
        place(top, sticker("star", Th.G_BLUE, 38, 12f, 1800), Gravity.TOP or Gravity.END, r = 0, t = -18)
        place(top, sticker("notes", Th.G_YELLOW, 40, 9f, 1300), Gravity.BOTTOM or Gravity.END, r = 4, b = -18)
        col.addView(top)

        if (page == 0) {
            val hi = chatBubble(tr("w_hi"), mine = true)
            col.addGap(hi, 30)
            (hi.layoutParams as LinearLayout.LayoutParams).apply {
                width = -2
                gravity = Gravity.END
            }
            val desc = chatBubble(tr("w_desc"))
            col.addGap(desc, 8)
            (desc.layoutParams as LinearLayout.LayoutParams).apply {
                width = -2
                rightMargin = dp(30)
            }
            val lc = card()
            lc.addView(cardTitle(tr("w_lang")))
            lc.addView(languageChooser())
            col.addGap(lc, 18)
            col.addGap(stickerButton(tr("btn_next"), Th.G_GREEN) { showWelcome(1) }, 18)
        } else {
            val tc = card()
            tc.addView(cardTitle(tr("w_before")))
            val tips = listOf("w_tip1" to "sparkle", "w_tip2" to "star", "w_tip3" to "notes")
            val grads = listOf(Th.G_YELLOW, Th.G_BLUE, Th.G_PINK)
            tips.forEachIndexed { idx, (k, shape) ->
                val r = LinearLayout(this)
                r.orientation = LinearLayout.HORIZONTAL
                r.isBaselineAligned = false
                r.gravity = Gravity.CENTER_VERTICAL
                val st = StickerShape(this, shape, grads[idx][0], grads[idx][1])
                r.addView(st, LinearLayout.LayoutParams(dp(34), dp(34)))
                val b = chatBubble(tr(k))
                val lp = LinearLayout.LayoutParams(0, -2, 1f)
                lp.leftMargin = dp(8)
                r.addView(b, lp)
                tc.addGap(r, 10)
            }
            col.addGap(tc, 26)
            col.addGap(stickerButton(tr("btn_start"), Th.G_GREEN) { closeWelcome() }, 18)
            col.addGap(softButton(tr("btn_back")) { showWelcome(0) }, 6)
        }
        col.addGap(label(tr("w_unofficial"), 13f, true, Th.INK_SOFT).apply { gravity = Gravity.CENTER }, 18)

        val ov = FrameLayout(this)
        ov.isClickable = true
        ov.addView(sv, FrameLayout.LayoutParams(-1, -1))
        root.addView(ov, FrameLayout.LayoutParams(-1, -1))
        welcome = ov
        ov.alpha = 0f
        ov.animate().alpha(1f).setDuration(220).start()
    }

    private fun closeWelcome() {
        store.putInt("welcomed", 1)
        store.putInt("welcomeOpen", 0)
        welcome?.let { root.removeView(it) }
        welcome = null
        mainColumn.visibility = View.VISIBLE
        nav.visibility = View.VISIBLE
        showTab(0)
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
            toast(tr("t_bt_on"))
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
            resumeMusicOnConnect = false
            if (Bus.musicRunning) stopService(Intent(this, MusicService::class.java))
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
        LightStick.status = tr("st_searching_yours")
        LightStick.scan(6000, onUpdate = { list ->
            if (!handled && list.any { it.address == def }) {
                handled = true
                LightStick.stopScan()
                connectTo(def, store.nameOf(def) ?: tr("my_stick"))
            }
        }, done = { list ->
            if (!handled) {
                handled = true
                if (list.any { it.address == def }) {
                    connectTo(def, store.nameOf(def) ?: tr("my_stick"))
                } else {
                    LightStick.status = tr("st_saved_not_found")
                    toast(tr("t_saved_not_found"))
                    openPicker()
                }
            }
        })
    }

    /** Lista en vivo de lightsticks cercanos para elegir el tuyo. */
    private fun openPicker() {
        if (!ensureBle { openPicker() }) return
        if (LightStick.connected || LightStick.connecting) {
            if (Bus.musicRunning) stopService(Intent(this, MusicService::class.java))
            LightStick.disconnect()
        }

        val (dlg, box) = stickerDialog()
        box.addView(cardTitle(tr("picker_title"), tr("picker_sub")))
        val scanning = label(tr("searching"), 14f, true, 0xFFE0249B.toInt())
        box.addView(scanning)
        val list = column()
        val sv = ScrollView(this)
        sv.addView(list)
        box.addGap(sv, 6)
        (sv.layoutParams as LinearLayout.LayoutParams).height = dp(300)
        box.addGap(softButton(tr("cancel")) { dlg.dismiss() }, 10)

        val known = store.known()
        val def = store.defaultAddr

        fun render(found: List<Found>) {
            list.removeAllViews()
            val sorted = found.sortedWith(compareByDescending<Found> { known.containsKey(it.address) }.thenByDescending { it.rssi })
            for (f in sorted) {
                val r = LinearLayout(this)
                r.orientation = LinearLayout.HORIZONTAL
                r.isBaselineAligned = false
                r.gravity = Gravity.CENTER_VERTICAL
                val mine = known.containsKey(f.address)
                val g = if (f.address == def) Th.G_YELLOW else if (mine) Th.G_BLUE else Th.G_WHITE
                r.background = StickerDrawable(g[0], g[1], dpf(18f), dpf(2.2f), dpf(3f))
                r.setPadding(dp(12), dp(10), dp(12) + dp(3), dp(10) + dp(3))
                val icon = NavIcon(this, 0)
                r.addView(icon, LinearLayout.LayoutParams(dp(26), dp(26)))
                val t = column()
                t.setPadding(dp(10), 0, 0, 0)
                val title = (if (f.address == def) "★ " else "") + (known[f.address] ?: f.name)
                t.addView(label(title, 15.5f, true))
                t.addView(label("**:**:" + macTail(f.address) + " · " + signalText(f.rssi), 13f, false, Th.INK_SOFT))
                r.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
                r.isClickable = true
                r.setOnClickListener {
                    LightStick.stopScan()
                    dlg.dismiss()
                    connectTo(f.address, known[f.address] ?: tr("my_stick"))
                    toast(tr("t_connecting_identify"))
                }
                r.touchFx(dpf(18f))
                list.addGap(r, 8)
            }
        }

        dlg.setOnDismissListener { LightStick.stopScan() }
        dlg.show()

        LightStick.scan(8000, onUpdate = { render(it) }, done = { found ->
            render(found)
            scanning.text = if (found.isEmpty()) {
                tr("none_found")
            } else {
                tr("tap_yours")
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
            toast(tr("t_connect_first_music"))
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
            toast(tr("t_style_on_restart"))
            return
        }
        stopService(Intent(this, MusicService::class.java))
        ui.postDelayed({ if (!Bus.musicRunning && LightStick.connected) launchService(0, null) }, 400)
    }

    private fun startMusic() {
        if (sourceId == 1) {
            if (Build.VERSION.SDK_INT < 29) {
                toast(tr("t_needs_android10"))
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
        i.putExtra(MusicService.EXTRA_MODE, 1)
        i.putExtra(MusicService.EXTRA_SONG_BPM, if (songIdx >= 0) Songs.LIST[songIdx].second else 0f)
        i.putExtra(MusicService.EXTRA_LEAD, lead)
        i.putExtra(MusicService.EXTRA_REACTIVE, reactive)
        i.putExtra(MusicService.EXTRA_MINB, minBright)
        if (data != null) {
            i.putExtra(MusicService.EXTRA_CODE, resultCode)
            i.putExtra(MusicService.EXTRA_DATA, data)
        }
        Bus.message = tr("m_starting")
        startForegroundService(i)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PROJ) {
            if (resultCode == RESULT_OK && data != null) {
                launchService(resultCode, data)
            } else {
                toast(tr("t_capture_denied"))
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
                    toast(tr("t_need_bt_perm"))
                }
            }
            REQ_AUDIO -> {
                if (has(Manifest.permission.RECORD_AUDIO)) {
                    startMusic()
                } else {
                    toast(tr("t_need_mic_perm"))
                }
            }
        }
    }
}
