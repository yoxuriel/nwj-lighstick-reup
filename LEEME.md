# NJ Light Stick Music (APK para Android)

App para controlar tu NewJeans Official Light Stick desde el celular:

- **Color, brillo y los 4 efectos (modos 1 a 4)**, como la app oficial.
- **Colores del lightstick** (blanco, verde, rosa, azul, naranja, amarillo) y **colores de las integrantes**
  (Minji azul, Hanni rosa, Danielle amarillo, Haerin verde, Hyein morado).
- **Modo música**: el lightstick sigue el ritmo, con tres opciones de colores
  (arcoíris, los 6 colores, integrantes) y dos fuentes de audio
  (micrófono, o el audio del mismo celular en Android 10+).
- Muestra batería, firmware y modelo.

No incluye el "modo concierto" de la app oficial (asiento/NFC/datos del concierto).

## Cómo obtener el APK (una sola vez, ~15 minutos)

No necesitas instalar nada en la PC. GitHub compila el APK gratis.

1. Crea una cuenta gratis en https://github.com (si no tienes).
2. Arriba a la derecha toca **+ → New repository**. Ponle un nombre (por ejemplo `njs-music`),
   déjalo **Private** si quieres, y toca **Create repository**.
3. En la página del repositorio vacío toca el enlace **uploading an existing file**.
4. Descomprime este zip en tu PC y **arrastra todo el contenido de la carpeta** (no el zip) a la página:
   las carpetas `app`, `.github`, y los archivos sueltos. Abajo toca **Commit changes**.
   - Si la carpeta `.github` (empieza con punto) no se sube, crea ese archivo a mano:
     **Add file → Create new file**, escribe en el nombre `.github/workflows/build.yml`
     y pega el contenido del archivo `build.yml` del zip. Luego **Commit changes**.
5. Toca la pestaña **Actions**. Verás una ejecución llamada "Compilar APK" (círculo amarillo = trabajando).
   Tarda unos 5 a 10 minutos. Si no empieza sola: Actions → "Compilar APK" → **Run workflow**.
6. Cuando salga una palomita verde ✅, entra a esa ejecución y baja hasta **Artifacts**.
   Descarga **NJ-LightStick-Music-APK**: es un zip que contiene `app-debug.apk`.
7. Pasa el APK al celular (cable, Drive, Telegram...) y ábrelo. Android te pedirá permitir
   "instalar apps desconocidas" para esa app (Archivos o Chrome). Acepta.

Si sale una **X roja** ❌: entra a la ejecución, abre el paso que falló (el que tiene la X),
copia las últimas líneas del error y mándamelas para corregirlo.

## Uso

1. Cierra la app oficial y apaga nRF Connect: el lightstick acepta una sola conexión.
2. Enciende el lightstick, abre **NJ Light Stick Music** y toca **Conectar al lightstick**
   (acepta los permisos de Bluetooth).
3. Manual: mueve la barra de color, el brillo o toca un color/efecto.
4. Música: elige colores y fuente de audio, toca **Iniciar modo música**.
   - Micrófono: funciona con cualquier fuente de sonido cercana (concierto, bocina, PC).
   - Audio del celular: Android pedirá un permiso de "grabar o transmitir pantalla".
     Es solo para capturar el audio; la app no graba ni ve tu pantalla.
     Algunas apps (por ejemplo, con protección de audio) bloquean la captura y no llegará sonido;
     en ese caso usa el micrófono.
5. El modo música sigue funcionando con la pantalla apagada (hay una notificación con el botón **Detener**).
   En Realme/ColorOS, si se corta solo, quita las restricciones de batería de la app:
   Ajustes → Batería → (la app) → Permitir actividad en segundo plano.

## Notas de seguridad

La app solo escribe en la característica de color/modo del lightstick (la misma que usa la app oficial).
No toca la de actualización de firmware.
