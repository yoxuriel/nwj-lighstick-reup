package app.njsmusic

import android.content.Context
import java.util.Locale

/**
 * Textos de la app en 5 idiomas: español, inglés, coreano, japonés y portugués.
 * Automático según el idioma del celular, o elegido a mano (se guarda en "lang").
 */
object L {
    val CODES = arrayOf("es", "en", "ko", "ja", "pt")
    val NAMES = arrayOf("Español", "English", "한국어", "日本語", "Português")

    /** 0 es, 1 en, 2 ko, 3 ja, 4 pt */
    @Volatile var lang = 1

    /** "auto" o un código de [CODES] */
    fun pref(ctx: Context): String = ctx.getSharedPreferences("njs", Context.MODE_PRIVATE).getString("lang", "auto") ?: "auto"

    fun setPref(ctx: Context, code: String) {
        ctx.getSharedPreferences("njs", Context.MODE_PRIVATE).edit().putString("lang", code).apply()
        init(ctx)
    }

    fun init(ctx: Context) {
        val p = pref(ctx)
        val i = CODES.indexOf(p)
        lang = if (i >= 0) i else detect()
    }

    fun detect(): Int = when (Locale.getDefault().language) {
        "es" -> 0
        "ko" -> 2
        "ja" -> 3
        "pt" -> 4
        else -> 1
    }

    fun t(key: String, vararg args: Any): String {
        val arr = S[key] ?: return key
        var s = arr[lang.coerceIn(0, 4)]
        for (i in args.indices) s = s.replace("{$i}", args[i].toString())
        return s
    }

    /** Para pruebas: claves con traducciones incompletas. */
    fun problems(): List<String> = S.filter { it.value.size != 5 || it.value.any { v -> v.isEmpty() } }.keys.toList()
    fun keys(): Set<String> = S.keys

    private fun e(es: String, en: String, ko: String, ja: String, pt: String) = arrayOf(es, en, ko, ja, pt)

    private val S: Map<String, Array<String>> = mapOf(
        // ---------------- general / encabezado
        "st_connected" to e("Conectado", "Connected", "연결됨", "接続済み", "Conectado"),
        "st_connecting_short" to e("Conectando", "Connecting", "연결 중", "接続中", "Conectando"),
        "st_none" to e("Sin conexión", "Not connected", "연결 안 됨", "未接続", "Sem conexão"),
        "tab_light" to e("Luz", "Light", "라이트", "ライト", "Luz"),
        "tab_music" to e("Música", "Music", "음악", "音楽", "Música"),
        "tab_info" to e("Info", "Info", "정보", "情報", "Info"),

        // ---------------- pestaña Luz
        "my_stick" to e("Mi lightstick", "My lightstick", "내 응원봉", "マイペンライト", "Meu lightstick"),
        "none_saved_short" to e("Aún no hay uno guardado", "None saved yet", "저장된 응원봉 없음", "まだ保存されていません", "Nenhum salvo ainda"),
        "battery_x" to e("Batería {0}%", "Battery {0}%", "배터리 {0}%", "バッテリー {0}%", "Bateria {0}%"),
        "btn_connect_mine" to e("Conectar a mi lightstick", "Connect my lightstick", "내 응원봉 연결", "マイペンライトに接続", "Conectar meu lightstick"),
        "btn_find" to e("Buscar mi lightstick", "Find my lightstick", "내 응원봉 찾기", "ペンライトを探す", "Procurar meu lightstick"),
        "btn_disconnect" to e("Desconectar", "Disconnect", "연결 해제", "切断", "Desconectar"),
        "btn_pick" to e("Elegir otro", "Choose another", "다른 응원봉", "別のを選ぶ", "Escolher outro"),
        "btn_identify" to e("Identificar", "Identify", "식별하기", "識別", "Identificar"),
        "music_active_note" to e(
            "El modo música está activo. Detenlo para usar estos controles.",
            "Music mode is on. Stop it to use these controls.",
            "음악 모드가 켜져 있어요. 이 기능을 쓰려면 먼저 멈춰 주세요.",
            "音楽モードがオンです。これらを使うには停止してください。",
            "O modo música está ativo. Pare-o para usar estes controles."
        ),
        "color" to e("COLOR", "COLOR", "색상", "カラー", "COR"),
        "color_sub" to e("Desliza o toca una burbuja", "Slide or tap a bubble", "밀거나 버블을 탭하세요", "スライドするかバブルをタップ", "Deslize ou toque numa bolha"),
        "ls_colors" to e("LIGHTSTICK", "LIGHTSTICK", "응원봉 컬러", "ペンライトカラー", "LIGHTSTICK"),
        "members" to e("INTEGRANTES", "MEMBERS", "멤버", "メンバー", "INTEGRANTES"),
        "c_white" to e("Blanco", "White", "화이트", "ホワイト", "Branco"),
        "c_green" to e("Verde", "Green", "그린", "グリーン", "Verde"),
        "c_pink" to e("Rosa", "Pink", "핑크", "ピンク", "Rosa"),
        "c_blue" to e("Azul", "Blue", "블루", "ブルー", "Azul"),
        "c_orange" to e("Naranja", "Orange", "오렌지", "オレンジ", "Laranja"),
        "c_yellow" to e("Amarillo", "Yellow", "옐로", "イエロー", "Amarelo"),
        "m_minji" to e("Minji", "Minji", "민지", "ミンジ", "Minji"),
        "m_hanni" to e("Hanni", "Hanni", "하니", "ハニ", "Hanni"),
        "m_danielle" to e("Danielle", "Danielle", "다니엘", "ダニエル", "Danielle"),
        "m_haerin" to e("Haerin", "Haerin", "해린", "ヘリン", "Haerin"),
        "m_hyein" to e("Hyein", "Hyein", "혜인", "ヘイン", "Hyein"),
        "brightness" to e("BRILLO", "BRIGHTNESS", "밝기", "明るさ", "BRILHO"),
        "effect" to e("EFECTO", "EFFECT", "효과", "エフェクト", "EFEITO"),
        "effect_sub" to e("Con velocidad a tu gusto", "At the speed you like", "원하는 속도로", "好きなスピードで", "Na velocidade que quiser"),
        "fx_solid" to e("Fijo", "Solid", "고정", "点灯", "Fixo"),
        "fx_flash" to e("Destello", "Flash", "깜빡임", "フラッシュ", "Pisca"),
        "fx_flash2" to e("Destello x2", "Double flash", "더블 깜빡임", "ダブルフラッシュ", "Pisca x2"),
        "fx_bubbles" to e("Burbujas", "Bubbles", "버블", "バブル", "Bolhas"),
        "speed" to e("VELOCIDAD", "SPEED", "속도", "スピード", "VELOCIDADE"),
        "speed_hint" to e("lento ← → rápido", "slow ← → fast", "느리게 ← → 빠르게", "ゆっくり ← → 速く", "lento ← → rápido"),

        // ---------------- pestaña Música
        "in_rhythm" to e("♪ en ritmo", "♪ on beat", "♪ 박자 맞춤", "♪ リズム同期中", "♪ no ritmo"),
        "finding_rhythm" to e("buscando el ritmo…", "finding the beat…", "박자 찾는 중…", "リズムを探しています…", "procurando o ritmo…"),
        "listening" to e("escuchando…", "listening…", "듣는 중…", "聴いています…", "ouvindo…"),
        "auto_detect_short" to e("se detecta solo", "auto-detected", "자동 감지", "自動で検出", "detectado sozinho"),
        "reactive_now" to e("♪ modo reactivo", "♪ reactive mode", "♪ 반응 모드", "♪ リアクティブ", "♪ modo reativo"),
        "music_hint" to e("Elige un estilo y toca ▶", "Pick a style and tap ▶", "스타일을 고르고 ▶를 누르세요", "スタイルを選んで▶をタップ", "Escolha um estilo e toque ▶"),
        "style" to e("ESTILO", "STYLE", "스타일", "スタイル", "ESTILO"),
        "style_sub" to e("Cómo cambia la luz con la música", "How the light follows the music", "음악에 맞춰 빛이 바뀌는 방식", "音楽に合わせた光の変わり方", "Como a luz acompanha a música"),
        "rainbow" to e("Arcoíris", "Rainbow", "무지개", "レインボー", "Arco-íris"),
        "mode" to e("MODO", "MODE", "모드", "モード", "MODO"),
        "mode_beat" to e("Ritmo", "Beat", "리듬", "リズム", "Ritmo"),
        "mode_reactive" to e("Reactivo", "Reactive", "반응", "リアクティブ", "Reativo"),
        "mode_hint" to e(
            "Ritmo: sigue el BPM y predice cada golpe (más preciso). Reactivo: responde al sonido al instante (más libre, ideal para baladas).",
            "Beat: follows the BPM and predicts every hit (more precise). Reactive: responds to the sound instantly (freer, great for ballads).",
            "리듬: BPM을 따라 박자를 미리 예측해요(더 정확). 반응: 소리에 바로 반응해요(더 자유롭고 발라드에 좋아요).",
            "リズム：BPMに合わせて拍を予測（より正確）。リアクティブ：音にすぐ反応（自由な感じでバラード向き）。",
            "Ritmo: segue o BPM e prevê cada batida (mais preciso). Reativo: responde ao som na hora (mais livre, ótimo para baladas)."
        ),
        "source" to e("FUENTE DE AUDIO", "AUDIO SOURCE", "오디오 소스", "音声ソース", "FONTE DE ÁUDIO"),
        "src_mic" to e("Micrófono", "Microphone", "마이크", "マイク", "Microfone"),
        "src_phone" to e("Este celular", "This phone", "이 폰", "このスマホ", "Este celular"),
        "src_hint" to e(
            "Micrófono: concierto, bocina o cualquier sonido cercano.\nEste celular: lo que suena en tu teléfono (algunas apps lo bloquean).",
            "Microphone: concert, speaker or any sound nearby.\nThis phone: what plays on your phone (some apps block it).",
            "마이크: 콘서트, 스피커 등 주변 소리.\n이 폰: 휴대폰에서 재생되는 소리(일부 앱은 차단해요).",
            "マイク：コンサートやスピーカーなど周りの音。\nこのスマホ：スマホで再生中の音（一部のアプリは不可）。",
            "Microfone: show, caixa de som ou qualquer som por perto.\nEste celular: o que toca no seu telefone (alguns apps bloqueiam)."
        ),
        "song" to e("CANCIÓN", "SONG", "노래", "曲", "MÚSICA"),
        "song_sub" to e(
            "Opcional: elegirla fija el tempo y ayuda con mucho ruido",
            "Optional: picking it locks the tempo and helps in loud places",
            "선택 사항: 고르면 템포가 고정되어 시끄러운 곳에서 도움이 돼요",
            "任意：選ぶとテンポが固定され、騒がしい場所で役立ちます",
            "Opcional: escolher fixa o andamento e ajuda com muito barulho"
        ),
        "song_auto" to e("Automático (detectar BPM)", "Automatic (detect BPM)", "자동 (BPM 감지)", "自動（BPMを検出）", "Automático (detectar BPM)"),
        "song_picker_sub" to e("Fija el tempo de la canción que va a sonar", "Locks the tempo of the song that will play", "재생될 노래의 템포를 고정해요", "これから流れる曲のテンポを固定します", "Fixa o andamento da música que vai tocar"),
        "sync" to e("SINCRONÍA", "SYNC", "싱크", "シンクロ", "SINCRONIA"),
        "sync_hint" to e("adelanto 0 → 250 ms", "advance 0 → 250 ms", "앞당김 0 → 250 ms", "前倒し 0 → 250 ms", "adiantar 0 → 250 ms"),
        "sync_help" to e(
            "Si las luces llegan tarde respecto a la música, mueve a la derecha. Se aplica al iniciar.",
            "If the lights come late compared to the music, move it right. Applied when you start.",
            "빛이 음악보다 늦으면 오른쪽으로 옮기세요. 시작할 때 적용돼요.",
            "光が音楽より遅れるときは右へ。開始時に反映されます。",
            "Se as luzes chegam atrasadas em relação à música, mova para a direita. Vale ao iniciar."
        ),
        "min_bright" to e("BRILLO MÍNIMO", "MINIMUM BRIGHTNESS", "최소 밝기", "最低の明るさ", "BRILHO MÍNIMO"),
        "min_bright_sub" to e("Para que no se apague en los silencios", "So it doesn't go dark in quiet parts", "조용한 부분에서 꺼지지 않도록", "静かな部分で消えないように", "Para não apagar nos silêncios"),

        // ---------------- pestaña Info
        "connected_title" to e("CONECTADO", "CONNECTED", "연결된 응원봉", "接続中", "CONECTADO"),
        "btn_rename" to e("Renombrar", "Rename", "이름 변경", "名前を変更", "Renomear"),
        "my_sticks" to e("MIS LIGHTSTICKS", "MY LIGHTSTICKS", "내 응원봉 목록", "マイペンライト", "MEUS LIGHTSTICKS"),
        "my_sticks_sub" to e("Toca uno para hacerlo el principal", "Tap one to make it your main one", "탭하면 기본 응원봉으로 설정돼요", "タップでメインに設定", "Toque em um para torná-lo o principal"),
        "btn_add" to e("Agregar / elegir otro", "Add / choose another", "추가 / 다른 응원봉", "追加 / 別のを選ぶ", "Adicionar / escolher outro"),
        "none_connected" to e("Ninguno conectado ahora.", "None connected right now.", "지금 연결된 응원봉이 없어요.", "現在接続されていません。", "Nenhum conectado agora."),
        "none_saved_long" to e("Aún no guardas ninguno. Conéctate y se guarda solo.", "None saved yet. Connect and it saves automatically.", "아직 저장된 응원봉이 없어요. 연결하면 자동으로 저장돼요.", "まだ保存されていません。接続すると自動で保存されます。", "Nenhum salvo ainda. Conecte e ele é salvo sozinho."),
        "forget" to e("Olvidar", "Forget", "삭제", "削除", "Esquecer"),
        "made_main" to e("{0} es ahora tu lightstick principal", "{0} is now your main lightstick", "{0}이(가) 기본 응원봉이 되었어요", "{0} をメインに設定しました", "{0} agora é seu lightstick principal"),
        "row_name" to e("Nombre", "Name", "이름", "名前", "Nome"),
        "row_battery" to e("Batería", "Battery", "배터리", "バッテリー", "Bateria"),
        "row_fw" to e("Firmware", "Firmware", "펌웨어", "ファームウェア", "Firmware"),
        "row_model" to e("Modelo", "Model", "모델", "モデル", "Modelo"),
        "language" to e("IDIOMA", "LANGUAGE", "언어", "言語", "IDIOMA"),
        "lang_auto" to e("Automático (idioma del celular)", "Automatic (phone language)", "자동 (휴대폰 언어)", "自動（スマホの言語）", "Automático (idioma do celular)"),
        "btn_welcome" to e("Ver bienvenida", "Show welcome", "환영 화면 보기", "ようこそ画面を見る", "Ver boas-vindas"),
        "tips" to e("CONSEJOS", "TIPS", "팁", "ヒント", "DICAS"),
        "tip1" to e(
            "Antes de conectar, cierra la app oficial: el lightstick acepta una sola conexión.",
            "Before connecting, close the official app: the lightstick accepts only one connection.",
            "연결하기 전에 공식 앱을 닫아 주세요. 응원봉은 한 번에 하나만 연결돼요.",
            "接続する前に公式アプリを閉じてください。ペンライトは1台としか接続できません。",
            "Antes de conectar, feche o app oficial: o lightstick aceita só uma conexão."
        ),
        "tip2q" to e("¿Y si hay muchos lightsticks en el concierto?", "What if there are lots of lightsticks at the concert?", "콘서트에 응원봉이 아주 많으면?", "コンサートでペンライトがたくさんあったら？", "E se tiver muitos lightsticks no show?"),
        "tip2a" to e(
            "Usa \"Elegir otro\": el más cercano tiene la señal más fuerte, y el final de la dirección es el mismo que muestra la app oficial en Ajustes → MAC. Después \"Identificar\" lo hace parpadear rosa y azul.",
            "Use \"Choose another\": the closest one has the strongest signal, and the end of the address matches what the official app shows in Settings → MAC. Then \"Identify\" makes it blink pink and blue.",
            "\"다른 응원봉\"을 쓰세요. 가장 가까운 응원봉이 신호가 가장 강하고, 주소 끝자리는 공식 앱의 설정 → MAC과 같아요. \"식별하기\"를 누르면 핑크와 블루로 깜빡여요.",
            "「別のを選ぶ」を使ってください。一番近いものが一番強い信号で、アドレスの末尾は公式アプリの設定 → MAC と同じです。「識別」でピンクとブルーに点滅します。",
            "Use \"Escolher outro\": o mais próximo tem o sinal mais forte, e o final do endereço é o mesmo que o app oficial mostra em Ajustes → MAC. Depois \"Identificar\" faz ele piscar rosa e azul."
        ),
        "tip3q" to e("¿Y si se desconecta?", "What if it disconnects?", "연결이 끊기면?", "接続が切れたら？", "E se desconectar?"),
        "tip3a" to e(
            "Te aviso, pauso la música y con \"Reconectar\" sigue sola.",
            "I'll let you know, pause the music, and \"Reconnect\" picks it back up.",
            "알려 드리고 음악을 멈춰요. \"다시 연결\"을 누르면 이어서 재생돼요.",
            "お知らせして音楽を一時停止します。「再接続」で続きから再開します。",
            "Eu aviso, pauso a música e com \"Reconectar\" ela continua sozinha."
        ),
        "tip4" to e(
            "Si \"Este celular\" no reacciona, la app que suena bloquea la captura: usa el micrófono.",
            "If \"This phone\" doesn't react, the app playing blocks capture: use the microphone.",
            "\"이 폰\"이 반응하지 않으면 재생 중인 앱이 캡처를 막는 거예요. 마이크를 쓰세요.",
            "「このスマホ」が反応しない場合、再生中のアプリが取り込みを禁止しています。マイクを使ってください。",
            "Se \"Este celular\" não reagir, o app que está tocando bloqueia a captura: use o microfone."
        ),
        "tip5" to e(
            "Si la música se corta con la pantalla apagada, quita las restricciones de batería a esta app (Ajustes → Apps → esta app → Batería → Sin restricciones; el nombre cambia según la marca).",
            "If music stops with the screen off, remove battery restrictions for this app (Settings → Apps → this app → Battery → Unrestricted; names vary by brand).",
            "화면이 꺼지면 음악 모드가 멈춘다면 이 앱의 배터리 제한을 해제하세요(설정 → 앱 → 이 앱 → 배터리 → 제한 없음, 이름은 제조사마다 달라요).",
            "画面オフで止まる場合は、このアプリのバッテリー制限を解除してください（設定 → アプリ → このアプリ → バッテリー → 制限なし。名称は機種により異なります）。",
            "Se a música parar com a tela desligada, tire as restrições de bateria deste app (Ajustes → Apps → este app → Bateria → Sem restrições; o nome muda conforme a marca)."
        ),
        "tip6" to e(
            "¿Las luces van tarde? En Música → Sincronía, mueve el control a la derecha.",
            "Lights running late? In Music → Sync, move the slider right.",
            "빛이 늦나요? 음악 → 싱크에서 오른쪽으로 옮기세요.",
            "光が遅れる？ 音楽 → シンクロで右に動かしてください。",
            "As luzes estão atrasadas? Em Música → Sincronia, mova para a direita."
        ),
        "about" to e("ACERCA DE", "ABOUT", "앱 정보", "このアプリについて", "SOBRE"),
        "about_body" to e(
            "App NO oficial para el NewJeans Official Light Stick. No está afiliada a ADOR ni a HYBE.\n\nQué hace: controla el color, el brillo y efectos con velocidad ajustable; convierte la música en luz siguiendo el ritmo (BPM) con estilos Arcoíris, NewJeans y uno por integrante; recuerda tu lightstick para encontrarlo entre muchos y te avisa si se desconecta.\n\nSolo usa la función de color del lightstick (la misma que la app oficial) y nunca toca su firmware.",
            "UNOFFICIAL app for the NewJeans Official Light Stick. Not affiliated with ADOR or HYBE.\n\nWhat it does: controls color, brightness and effects with adjustable speed; turns music into light following the beat (BPM) with Rainbow, NewJeans and per-member styles; remembers your lightstick so you can find it among many, and warns you if it disconnects.\n\nIt only uses the lightstick's color function (the same one the official app uses) and never touches its firmware.",
            "NewJeans 공식 응원봉을 위한 비공식 앱이에요. ADOR, HYBE와 관련이 없어요.\n\n기능: 색상·밝기·속도 조절 가능한 효과를 제어하고, 음악의 박자(BPM)에 맞춰 빛을 바꿔요(무지개, NewJeans, 멤버별 스타일). 많은 응원봉 속에서도 내 응원봉을 기억하고, 연결이 끊기면 알려 줘요.\n\n응원봉의 색상 기능(공식 앱과 같은 기능)만 사용하며 펌웨어는 절대 건드리지 않아요.",
            "NewJeans公式ペンライト用の非公式アプリです。ADOR・HYBEとは関係ありません。\n\nできること：色・明るさ・スピード調整できるエフェクトの操作、音楽のリズム（BPM）に合わせた光（レインボー、NewJeans、メンバー別スタイル）、たくさんの中からマイペンライトを記憶、接続が切れたらお知らせ。\n\nペンライトの色の機能（公式アプリと同じ）だけを使い、ファームウェアには一切触れません。",
            "App NÃO oficial para o NewJeans Official Light Stick. Não é afiliado à ADOR nem à HYBE.\n\nO que faz: controla cor, brilho e efeitos com velocidade ajustável; transforma a música em luz seguindo o ritmo (BPM) com estilos Arco-íris, NewJeans e um por integrante; lembra do seu lightstick para achá-lo entre muitos e avisa se ele desconectar.\n\nUsa só a função de cor do lightstick (a mesma do app oficial) e nunca mexe no firmware."
        ),
        "made_by" to e("Creada por @UriEz", "Made by @UriEz", "제작: @UriEz", "制作：@UriEz", "Criado por @UriEz"),
        "fonts_credit" to e(
            "Fuentes: Fredoka, Pixelify Sans y Silkscreen (SIL Open Font License).",
            "Fonts: Fredoka, Pixelify Sans and Silkscreen (SIL Open Font License).",
            "글꼴: Fredoka, Pixelify Sans, Silkscreen (SIL Open Font License).",
            "フォント：Fredoka、Pixelify Sans、Silkscreen（SIL Open Font License）。",
            "Fontes: Fredoka, Pixelify Sans e Silkscreen (SIL Open Font License)."
        ),

        // ---------------- diálogos
        "lost_title" to e("¡Se desconectó!", "Disconnected!", "연결이 끊겼어요!", "接続が切れました！", "Desconectou!"),
        "lost_music" to e(
            "Tu lightstick perdió la conexión y pausé el modo música. ¿Lo reconecto?",
            "Your lightstick lost the connection and I paused music mode. Reconnect?",
            "응원봉 연결이 끊겨서 음악 모드를 멈췄어요. 다시 연결할까요?",
            "ペンライトの接続が切れたので音楽モードを一時停止しました。再接続しますか？",
            "Seu lightstick perdeu a conexão e pausei o modo música. Reconectar?"
        ),
        "lost_plain" to e(
            "Tu lightstick perdió la conexión. ¿Está encendido y cerca? ¿Lo reconecto?",
            "Your lightstick lost the connection. Is it on and nearby? Reconnect?",
            "응원봉 연결이 끊겼어요. 켜져 있고 가까이 있나요? 다시 연결할까요?",
            "ペンライトの接続が切れました。電源が入っていて近くにありますか？再接続しますか？",
            "Seu lightstick perdeu a conexão. Ele está ligado e perto? Reconectar?"
        ),
        "btn_reconnect_resume" to e("Reconectar y seguir", "Reconnect and resume", "다시 연결하고 계속", "再接続して再開", "Reconectar e continuar"),
        "btn_reconnect" to e("Reconectar", "Reconnect", "다시 연결", "再接続", "Reconectar"),
        "btn_not_now" to e("Ahora no", "Not now", "나중에", "あとで", "Agora não"),
        "rename_title" to e("Nombre del lightstick", "Lightstick name", "응원봉 이름", "ペンライトの名前", "Nome do lightstick"),
        "save" to e("Guardar", "Save", "저장", "保存", "Salvar"),
        "cancel" to e("Cancelar", "Cancel", "취소", "キャンセル", "Cancelar"),
        "close" to e("Cerrar", "Close", "닫기", "閉じる", "Fechar"),
        "picker_title" to e("ELIGE TU LIGHTSTICK", "CHOOSE YOUR LIGHTSTICK", "내 응원봉 선택", "ペンライトを選ぶ", "ESCOLHA SEU LIGHTSTICK"),
        "picker_sub" to e(
            "Ordenados por cercanía. Compara el final de la dirección con la app oficial (Ajustes → MAC).",
            "Sorted by distance. Compare the end of the address with the official app (Settings → MAC).",
            "가까운 순서예요. 주소 끝자리를 공식 앱(설정 → MAC)과 비교하세요.",
            "近い順です。アドレスの末尾を公式アプリ（設定 → MAC）と比べてください。",
            "Ordenados por proximidade. Compare o final do endereço com o app oficial (Ajustes → MAC)."
        ),
        "searching" to e("Buscando…", "Searching…", "찾는 중…", "検索中…", "Procurando…"),
        "none_found" to e(
            "No encontré ninguno. Enciéndelo, acércalo y cierra la app oficial.",
            "None found. Turn it on, bring it closer and close the official app.",
            "찾지 못했어요. 응원봉을 켜고 가까이 두고 공식 앱을 닫아 주세요.",
            "見つかりませんでした。電源を入れて近づけ、公式アプリを閉じてください。",
            "Nenhum encontrado. Ligue, aproxime e feche o app oficial."
        ),
        "tap_yours" to e("Toca el tuyo", "Tap yours", "내 응원봉을 탭하세요", "自分のものをタップ", "Toque no seu"),
        "sig_near" to e("muy cerca", "very close", "아주 가까움", "とても近い", "muito perto"),
        "sig_mid" to e("cerca", "close", "가까움", "近い", "perto"),
        "sig_far" to e("lejos", "far", "멂", "遠い", "longe"),

        // ---------------- avisos (toasts)
        "t_connect_first" to e("Primero conecta un lightstick", "Connect a lightstick first", "먼저 응원봉을 연결하세요", "先にペンライトを接続してください", "Conecte um lightstick primeiro"),
        "t_stop_music_identify" to e("Detén el modo música para identificar", "Stop music mode to identify", "식별하려면 음악 모드를 멈추세요", "識別するには音楽モードを停止してください", "Pare o modo música para identificar"),
        "t_save_first" to e("Conecta o guarda un lightstick primero", "Connect or save a lightstick first", "먼저 응원봉을 연결하거나 저장하세요", "先にペンライトを接続または保存してください", "Conecte ou salve um lightstick primeiro"),
        "t_bt_on" to e("Enciende el Bluetooth del celular", "Turn on your phone's Bluetooth", "휴대폰 블루투스를 켜 주세요", "スマホのBluetoothをオンにしてください", "Ligue o Bluetooth do celular"),
        "t_saved_not_found" to e(
            "No encontré tu lightstick guardado. ¿Está encendido? Puedes elegir otro.",
            "Couldn't find your saved lightstick. Is it on? You can choose another.",
            "저장된 응원봉을 찾지 못했어요. 켜져 있나요? 다른 응원봉을 고를 수도 있어요.",
            "保存したペンライトが見つかりません。電源は入っていますか？別のを選ぶこともできます。",
            "Não achei seu lightstick salvo. Está ligado? Você pode escolher outro."
        ),
        "t_connecting_identify" to e(
            "Conectando… usa \"Identificar\" para confirmar que es el tuyo",
            "Connecting… use \"Identify\" to make sure it's yours",
            "연결 중… \"식별하기\"로 내 응원봉인지 확인하세요",
            "接続中…「識別」で自分のものか確認してください",
            "Conectando… use \"Identificar\" para confirmar que é o seu"
        ),
        "t_connect_first_music" to e("Primero conecta tu lightstick (pestaña Luz)", "Connect your lightstick first (Light tab)", "먼저 응원봉을 연결하세요 (라이트 탭)", "先にペンライトを接続してください（ライトタブ）", "Conecte seu lightstick primeiro (aba Luz)"),
        "t_style_on_restart" to e("El cambio se aplica al volver a iniciar", "The change applies when you start again", "다시 시작하면 적용돼요", "再開すると反映されます", "A mudança vale ao iniciar de novo"),
        "t_needs_android10" to e("Requiere Android 10 o superior. Usa el micrófono.", "Requires Android 10 or newer. Use the microphone.", "Android 10 이상이 필요해요. 마이크를 쓰세요.", "Android 10以上が必要です。マイクを使ってください。", "Requer Android 10 ou superior. Use o microfone."),
        "t_capture_denied" to e("Sin ese permiso no se puede usar el audio del celular", "Without that permission the phone's audio can't be used", "그 권한이 없으면 휴대폰 오디오를 쓸 수 없어요", "その許可がないとスマホの音声は使えません", "Sem essa permissão não dá para usar o áudio do celular"),
        "t_need_bt_perm" to e("Hace falta el permiso de Bluetooth", "Bluetooth permission is needed", "블루투스 권한이 필요해요", "Bluetoothの許可が必要です", "É preciso a permissão de Bluetooth"),
        "t_need_mic_perm" to e("Hace falta el permiso del micrófono", "Microphone permission is needed", "마이크 권한이 필요해요", "マイクの許可が必要です", "É preciso a permissão do microfone"),
        "t_resume_tap_play" to e("Al conectar, toca ▶ para seguir con el audio del celular", "Once connected, tap ▶ to continue with the phone's audio", "연결되면 ▶를 눌러 휴대폰 오디오로 계속하세요", "接続したら▶をタップしてスマホの音声で再開してください", "Ao conectar, toque ▶ para continuar com o áudio do celular"),

        // ---------------- estados del lightstick
        "st_disconnected" to e("Desconectado", "Disconnected", "연결 해제됨", "切断されました", "Desconectado"),
        "st_scan_error" to e("Error al buscar (código {0})", "Search error (code {0})", "검색 오류 (코드 {0})", "検索エラー（コード {0}）", "Erro ao procurar (código {0})"),
        "st_bt_off" to e("Bluetooth apagado", "Bluetooth is off", "블루투스 꺼짐", "Bluetoothがオフです", "Bluetooth desligado"),
        "st_scanning" to e("Buscando lightsticks…", "Searching for lightsticks…", "응원봉 찾는 중…", "ペンライトを検索中…", "Procurando lightsticks…"),
        "st_scan_fail" to e("No se pudo buscar: {0}", "Couldn't search: {0}", "검색할 수 없어요: {0}", "検索できませんでした：{0}", "Não foi possível procurar: {0}"),
        "st_connecting" to e("Conectando…", "Connecting…", "연결 중…", "接続中…", "Conectando…"),
        "st_disconnecting" to e("Desconectando…", "Disconnecting…", "연결 해제 중…", "切断中…", "Desconectando…"),
        "st_preparing" to e("Conectado, preparando…", "Connected, getting ready…", "연결됨, 준비 중…", "接続しました。準備中…", "Conectado, preparando…"),
        "st_lost" to e("Se desconectó el lightstick", "The lightstick disconnected", "응원봉 연결이 끊겼어요", "ペンライトの接続が切れました", "O lightstick desconectou"),
        "st_connect_fail" to e("No se pudo conectar (código {0}). Inténtalo de nuevo.", "Couldn't connect (code {0}). Try again.", "연결할 수 없어요 (코드 {0}). 다시 시도하세요.", "接続できませんでした（コード {0}）。もう一度お試しください。", "Não foi possível conectar (código {0}). Tente de novo."),
        "st_incompatible" to e("Este dispositivo no es un lightstick compatible", "This device isn't a compatible lightstick", "호환되는 응원봉이 아니에요", "対応するペンライトではありません", "Este dispositivo não é um lightstick compatível"),
        "st_connected_to" to e("Conectado a {0}", "Connected to {0}", "{0}에 연결됨", "{0} に接続しました", "Conectado a {0}"),
        "st_searching_yours" to e("Buscando tu lightstick…", "Looking for your lightstick…", "내 응원봉 찾는 중…", "マイペンライトを検索中…", "Procurando seu lightstick…"),
        "st_saved_not_found" to e("No encontré tu lightstick guardado", "Couldn't find your saved lightstick", "저장된 응원봉을 찾지 못했어요", "保存したペンライトが見つかりません", "Não achei seu lightstick salvo"),

        // ---------------- servicio de música
        "m_starting" to e("Iniciando…", "Starting…", "시작 중…", "開始中…", "Iniciando…"),
        "m_service_fail" to e("No se pudo iniciar el servicio: {0}", "Couldn't start the service: {0}", "서비스를 시작할 수 없어요: {0}", "サービスを開始できませんでした：{0}", "Não foi possível iniciar o serviço: {0}"),
        "m_missing_capture" to e("Falta el permiso de captura de audio.", "Audio capture permission is missing.", "오디오 캡처 권한이 없어요.", "音声取り込みの許可がありません。", "Falta a permissão de captura de áudio."),
        "m_capture_fail" to e("No se pudo capturar el audio interno: {0}", "Couldn't capture the phone's audio: {0}", "휴대폰 오디오를 캡처할 수 없어요: {0}", "スマホの音声を取り込めませんでした：{0}", "Não foi possível capturar o áudio interno: {0}"),
        "m_listening_phone" to e("Escuchando el audio del celular…", "Listening to the phone's audio…", "휴대폰 오디오 듣는 중…", "スマホの音声を聴いています…", "Ouvindo o áudio do celular…"),
        "m_listening_mic" to e("Escuchando el micrófono…", "Listening through the microphone…", "마이크로 듣는 중…", "マイクで聴いています…", "Ouvindo pelo microfone…"),
        "m_open_audio_fail" to e("No se pudo abrir el audio. ¿Diste el permiso del micrófono?", "Couldn't open the audio. Did you allow the microphone?", "오디오를 열 수 없어요. 마이크 권한을 허용했나요?", "音声を開けませんでした。マイクを許可しましたか？", "Não foi possível abrir o áudio. Você permitiu o microfone?"),
        "m_lost_paused" to e("Se desconectó tu lightstick. La música está en pausa.", "Your lightstick disconnected. Music is paused.", "응원봉 연결이 끊겼어요. 음악 모드가 일시 정지됐어요.", "ペンライトの接続が切れました。音楽は一時停止中です。", "Seu lightstick desconectou. A música está pausada."),
        "m_audio_lost" to e("Se perdió la señal de audio (código {0}).", "The audio signal was lost (code {0}).", "오디오 신호가 끊겼어요 (코드 {0}).", "音声信号が途切れました（コード {0}）。", "O sinal de áudio caiu (código {0})."),
        "m_no_audio_phone" to e(
            "No llega audio. Puede que la app que suena bloquee la captura (o no hay música). Prueba con el micrófono.",
            "No audio coming in. The app playing may block capture (or nothing is playing). Try the microphone.",
            "소리가 들어오지 않아요. 재생 중인 앱이 캡처를 막고 있거나 음악이 없어요. 마이크로 해 보세요.",
            "音が入ってきません。再生中のアプリが取り込みを禁止しているか、音楽が流れていません。マイクを試してください。",
            "Não está chegando áudio. O app que toca pode bloquear a captura (ou não há música). Tente o microfone."
        ),
        "m_no_audio_mic" to e(
            "No se escucha nada. Sube el volumen o acerca el celular a la fuente.",
            "Can't hear anything. Turn the volume up or move the phone closer to the sound.",
            "아무 소리도 안 들려요. 볼륨을 높이거나 소리 가까이 가져가세요.",
            "何も聞こえません。音量を上げるか、音の近くにスマホを置いてください。",
            "Não dá para ouvir nada. Aumente o volume ou aproxime o celular do som."
        ),
        "m_audio_error" to e("Error de audio: {0}", "Audio error: {0}", "오디오 오류: {0}", "音声エラー：{0}", "Erro de áudio: {0}"),
        "n_channel_music" to e("Modo música", "Music mode", "음악 모드", "音楽モード", "Modo música"),
        "n_title" to e("Modo música activo", "Music mode is on", "음악 모드 켜짐", "音楽モード実行中", "Modo música ativo"),
        "n_text" to e("Tu lightstick sigue el ritmo", "Your lightstick follows the beat", "응원봉이 박자를 따라가요", "ペンライトがリズムに合わせて光ります", "Seu lightstick acompanha o ritmo"),
        "n_stop" to e("Detener", "Stop", "정지", "停止", "Parar"),
        "n_alert_channel" to e("Avisos del lightstick", "Lightstick alerts", "응원봉 알림", "ペンライトのお知らせ", "Avisos do lightstick"),
        "n_lost_title" to e("Se desconectó tu lightstick", "Your lightstick disconnected", "응원봉 연결이 끊겼어요", "ペンライトの接続が切れました", "Seu lightstick desconectou"),
        "n_lost_text" to e("El modo música está en pausa. Toca para reconectar.", "Music mode is paused. Tap to reconnect.", "음악 모드가 일시 정지됐어요. 탭해서 다시 연결하세요.", "音楽モードは一時停止中です。タップして再接続。", "O modo música está pausado. Toque para reconectar."),

        // ---------------- bienvenida
        "w_hi" to e("¡Hola, Bunnies!", "Hi, Bunnies!", "안녕, 버니즈!", "こんにちは、バニーズ！", "Oi, Bunnies!"),
        "w_desc" to e(
            "Controla tu lightstick: colores, efectos y luces que siguen la música.",
            "Control your lightstick: colors, effects and lights that follow the music.",
            "응원봉을 내 마음대로: 색상, 효과, 음악에 맞춰 빛나는 라이트까지.",
            "ペンライトを自由に：カラー、エフェクト、音楽に合わせて光るライト。",
            "Controle seu lightstick: cores, efeitos e luzes que acompanham a música."
        ),
        "w_lang" to e("Elige tu idioma", "Choose your language", "언어를 선택하세요", "言語を選んでください", "Escolha seu idioma"),
        "w_before" to e("ANTES DE EMPEZAR", "BEFORE YOU START", "시작하기 전에", "はじめる前に", "ANTES DE COMEÇAR"),
        "w_tip1" to e(
            "Enciende tu lightstick y cierra la app oficial (solo acepta una conexión).",
            "Turn on your lightstick and close the official app (it only accepts one connection).",
            "응원봉을 켜고 공식 앱을 닫아 주세요 (한 번에 하나만 연결돼요).",
            "ペンライトの電源を入れ、公式アプリを閉じてください（1台としか接続できません）。",
            "Ligue seu lightstick e feche o app oficial (ele só aceita uma conexão)."
        ),
        "w_tip2" to e(
            "La primera vez elige el tuyo de la lista; la app lo recordará.",
            "The first time, pick yours from the list; the app will remember it.",
            "처음에는 목록에서 내 응원봉을 고르세요. 앱이 기억해요.",
            "初回はリストから自分のものを選んでください。アプリが覚えます。",
            "Na primeira vez, escolha o seu na lista; o app vai lembrar."
        ),
        "w_tip3" to e(
            "En Música elige un estilo y toca ▶. ¡A brillar!",
            "In Music, pick a style and tap ▶. Time to shine!",
            "음악에서 스타일을 고르고 ▶를 누르면 끝! 빛나 보세요!",
            "音楽でスタイルを選んで▶をタップ。さあ光らせよう！",
            "Em Música, escolha um estilo e toque ▶. Hora de brilhar!"
        ),
        "w_unofficial" to e("App no oficial · creada por @UriEz", "Unofficial app · made by @UriEz", "비공식 앱 · 제작 @UriEz", "非公式アプリ · 制作 @UriEz", "App não oficial · criado por @UriEz"),
        "btn_next" to e("Siguiente", "Next", "다음", "次へ", "Próximo"),
        "btn_start" to e("¡Empezar!", "Let's go!", "시작하기!", "はじめる！", "Começar!"),
        "btn_back" to e("Atrás", "Back", "뒤로", "戻る", "Voltar")
    )
}

/** Atajo para traducir. */
fun tr(key: String, vararg args: Any): String = L.t(key, *args)
