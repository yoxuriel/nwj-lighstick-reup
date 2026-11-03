package app.njsmusic

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.LinkedList
import java.util.UUID

/** Estado compartido entre la pantalla y el servicio de musica. */
object Bus {
    @Volatile var musicRunning = false
    @Volatile var r = 0
    @Volatile var g = 0
    @Volatile var b = 0
    @Volatile var message = ""

    @Volatile var bpm = 0f
    @Volatile var locked = false

    /** El modo música se pausó porque se desconectó el lightstick. */
    @Volatile var pausedByDisconnect = false
}

class Found(val address: String, val name: String, var rssi: Int)

/**
 * Control del NewJeans Official Light Stick por Bluetooth LE.
 *
 * Protocolo (sacado de la app oficial): servicio 0001fe01, caracteristica 0001ff02,
 * escritura con respuesta de 4 bytes [modo, R, G, B].
 * Esta clase SOLO escribe en esa caracteristica (nunca en la de firmware/OTA).
 */
@SuppressLint("MissingPermission")
object LightStick {
    private val SVC: UUID = UUID.fromString("0001fe01-0000-1000-8000-00805f9800c4")
    private val LED_CH: UUID = UUID.fromString("0001ff02-0000-1000-8000-00805f9800c4")
    private val BATT_SVC: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    private val BATT_CH: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    private val INFO_SVC: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    private val MODEL_CH: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
    private val FW_CH: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")

    @Volatile var status = ""
    @Volatile var connected = false
    @Volatile var connecting = false
    @Volatile var battery = -1
    @Volatile var firmware = ""
    @Volatile var model = ""

    /** Dirección del lightstick conectado (o al que se está conectando). */
    @Volatile var address: String? = null

    /** Sube en 1 cada vez que el lightstick se desconecta SIN que el usuario lo pida. */
    @Volatile var lostCount = 0

    /** Dirección del último lightstick que se desconectó solo (para reconectar). */
    @Volatile var lostAddress: String? = null

    @Volatile private var userDisconnect = false

    /** Ultimo color puesto a mano [modo, R, G, B]; se restaura al terminar el modo musica. */
    @Volatile var lastManual = intArrayOf(1, 0, 0, 255)

    private var appContext: Context? = null
    private var adapter: BluetoothAdapter? = null
    private val main = Handler(Looper.getMainLooper())

    private val lock = Any()
    private var gatt: BluetoothGatt? = null
    private var ledChar: BluetoothGattCharacteristic? = null
    private val reads = LinkedList<BluetoothGattCharacteristic>()
    private var pending: ByteArray? = null
    private var lastQueued: ByteArray? = null
    private var busy = false
    private var busySince = 0L
    private const val MIN_GAP_MS = 50L
    private const val URGENT_GAP_MS = 25L
    private var pendingUrgent = false
    private var lastWriteAt = 0L
    private var pumpScheduled = false
    private val pumpLater = Runnable {
        synchronized(lock) { pumpScheduled = false }
        pump()
    }

    fun init(ctx: Context) {
        appContext = ctx.applicationContext
        val bm = ctx.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        adapter = bm.adapter
    }

    fun bluetoothOn(): Boolean = adapter?.isEnabled == true

    fun infoLine(): String {
        val parts = ArrayList<String>()
        if (battery >= 0) parts.add(tr("battery_x", battery))
        if (firmware.isNotEmpty()) parts.add("Firmware $firmware")
        if (model.isNotEmpty()) parts.add(model)
        return parts.joinToString(" · ")
    }

    // ------------------------------------------------------------------ busqueda
    private val found = LinkedHashMap<String, Found>()
    private var scanning = false
    private var lastUpdate = 0L

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name ?: ""
            val hasSvc = result.scanRecord?.serviceUuids?.any { it.uuid == SVC } == true
            if (name.contains("NEWJEANS", ignoreCase = true) || hasSvc) {
                val isNew = !found.containsKey(result.device.address)
                found[result.device.address] =
                    Found(result.device.address, if (name.isEmpty()) "Lightstick" else name, result.rssi)
                val cb = onScanUpdate
                val now = SystemClock.elapsedRealtime()
                if (cb != null && (isNew || now - lastUpdate > 700)) {
                    lastUpdate = now
                    cb(currentList())
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            status = tr("st_scan_error", errorCode)
        }
    }

    private var scanStopper: Runnable? = null
    private var activeScanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var onScanUpdate: ((List<Found>) -> Unit)? = null

    private fun currentList(): List<Found> = found.values.sortedByDescending { it.rssi }

    /**
     * Busca durante [ms] milisegundos. [onUpdate] recibe la lista cada vez que aparece o cambia
     * un lightstick; [done] recibe la lista final. Todo en el hilo principal.
     */
    fun scan(ms: Long, onUpdate: (List<Found>) -> Unit, done: (List<Found>) -> Unit) {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            status = tr("st_bt_off")
            done(emptyList())
            return
        }
        stopScan()
        found.clear()
        scanning = true
        activeScanner = scanner
        onScanUpdate = onUpdate
        if (!connected && !connecting) status = tr("st_scanning")
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(null, settings, scanCb)
        } catch (e: Exception) {
            scanning = false
            status = tr("st_scan_fail", e.message ?: "")
            done(emptyList())
            return
        }
        val stopper = Runnable {
            stopScan()
            done(currentList())
        }
        scanStopper = stopper
        main.postDelayed(stopper, ms)
    }

    /** Detiene la búsqueda sin avisar a [done]. */
    fun stopScan() {
        scanStopper?.let { main.removeCallbacks(it) }
        scanStopper = null
        onScanUpdate = null
        val sc = activeScanner
        activeScanner = null
        if (sc != null) {
            try {
                sc.stopScan(scanCb)
            } catch (e: Exception) {
            }
        }
        if (scanning) {
            scanning = false
            if (!connected && !connecting) status = tr("st_disconnected")
        }
    }

    // ------------------------------------------------------------------ conexion
    fun connect(address: String) {
        val dev = adapter?.getRemoteDevice(address) ?: return
        userDisconnect = false
        closeGatt()
        battery = -1
        firmware = ""
        model = ""
        connecting = true
        connected = false
        this.address = address
        status = tr("st_connecting")
        gatt = dev.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        val g = gatt
        if (g == null) {
            connected = false
            connecting = false
            status = tr("st_disconnected")
            return
        }
        status = tr("st_disconnecting")
        userDisconnect = true
        g.disconnect()
        // por si el sistema nunca avisa del cierre
        main.postDelayed({
            if (gatt === g) {
                closeGatt()
                status = tr("st_disconnected")
            }
        }, 1500)
    }

    private fun closeGatt() {
        val g = gatt
        gatt = null
        synchronized(lock) {
            ledChar = null
            reads.clear()
            pending = null
            lastQueued = null
            busy = false
            pumpScheduled = false
        }
        connected = false
        connecting = false
        address = null
        try {
            g?.close()
        } catch (e: Exception) {
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, st: Int, newState: Int) {
            if (g !== gatt) {
                try { g.close() } catch (e: Exception) { }
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                status = tr("st_preparing")
                try { g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) } catch (e: Exception) { }
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                val wasConnected = connected
                val addr = address
                val byUser = userDisconnect
                userDisconnect = false
                closeGatt()
                if (wasConnected && !byUser) {
                    lostAddress = addr
                    lostCount++
                    status = tr("st_lost")
                } else if (byUser) {
                    status = tr("st_disconnected")
                } else {
                    status = tr("st_connect_fail", st)
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, st: Int) {
            val c = g.getService(SVC)?.getCharacteristic(LED_CH)
            if (st != BluetoothGatt.GATT_SUCCESS || c == null) {
                status = tr("st_incompatible")
                g.disconnect()
                return
            }
            synchronized(lock) {
                ledChar = c
                busy = false
                reads.clear()
                g.getService(BATT_SVC)?.getCharacteristic(BATT_CH)?.let { reads.add(it) }
                g.getService(INFO_SVC)?.getCharacteristic(FW_CH)?.let { reads.add(it) }
                g.getService(INFO_SVC)?.getCharacteristic(MODEL_CH)?.let { reads.add(it) }
            }
            connected = true
            connecting = false
            status = tr("st_connected_to", g.device.name ?: "lightstick")
            // igual que la app oficial: al conectar deja un color fijo (por defecto azul)
            val m = lastManual
            setLed(m[0], m[1], m[2], m[3])
            pump()
        }

        private fun handleRead(c: BluetoothGattCharacteristic, v: ByteArray, st: Int) {
            if (st == BluetoothGatt.GATT_SUCCESS) {
                when (c.uuid) {
                    BATT_CH -> if (v.isNotEmpty()) battery = v[0].toInt() and 0xFF
                    FW_CH -> firmware = String(v).trim()
                    MODEL_CH -> model = String(v).trim()
                    else -> {}
                }
            }
            synchronized(lock) { busy = false }
            pump()
        }

        // Android 13+ llama a esta version...
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, st: Int) {
            handleRead(c, value, st)
        }

        // ...y las anteriores a esta.
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, st: Int) {
            if (Build.VERSION.SDK_INT < 33) handleRead(c, c.value ?: ByteArray(0), st)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, st: Int) {
            synchronized(lock) { busy = false }
            pump()
        }
    }

    // ------------------------------------------------------------------ envio de colores
    /** Pone el color. Si hay una escritura en curso, solo se guarda la ultima (la mas reciente gana). */
    fun setLed(mode: Int, r: Int, g: Int, b: Int, urgent: Boolean = false) {
        val msg = byteArrayOf(
            mode.coerceIn(1, 4).toByte(),
            r.coerceIn(0, 255).toByte(),
            g.coerceIn(0, 255).toByte(),
            b.coerceIn(0, 255).toByte()
        )
        synchronized(lock) {
            if (lastQueued != null && lastQueued!!.contentEquals(msg) && pending == null) return
            pending = msg
            lastQueued = msg
            pendingUrgent = urgent
        }
        pump()
    }

    @Suppress("DEPRECATION")
    private fun pump() {
        synchronized(lock) {
            val g = gatt ?: return
            if (busy && SystemClock.elapsedRealtime() - busySince > 1500) busy = false // perdio un aviso
            while (!busy) {
                val rd = reads.poll()
                if (rd != null) {
                    busy = g.readCharacteristic(rd)
                    busySince = SystemClock.elapsedRealtime()
                    continue
                }
                val p = pending ?: return
                val c = ledChar ?: return
                // No saturar al lightstick: como máximo ~20 comandos por segundo
                // (igual que el script de Windows). Si es muy pronto, se reintenta después.
                val gap = if (pendingUrgent) URGENT_GAP_MS else MIN_GAP_MS
                val wait = gap - (SystemClock.elapsedRealtime() - lastWriteAt)
                if (wait > 0) {
                    if (!pumpScheduled || pendingUrgent) {
                        main.removeCallbacks(pumpLater)
                        pumpScheduled = true
                        main.postDelayed(pumpLater, wait)
                    }
                    return
                }
                pendingUrgent = false
                pending = null
                lastWriteAt = SystemClock.elapsedRealtime()
                busy = if (Build.VERSION.SDK_INT >= 33) {
                    g.writeCharacteristic(c, p, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == 0
                } else {
                    c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    c.value = p
                    g.writeCharacteristic(c)
                }
                busySince = SystemClock.elapsedRealtime()
                return
            }
        }
    }
}
