package app.njsmusic

import android.content.Context

/** Guarda en el celular: lightsticks conocidos, cuál es "el mío" y los ajustes. */
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("njs", Context.MODE_PRIVATE)

    /** dirección -> nombre */
    fun known(): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        val set = sp.getStringSet("known", emptySet()) ?: emptySet()
        for (e in set.sorted()) {
            val i = e.indexOf('|')
            if (i > 0) out[e.substring(0, i)] = e.substring(i + 1)
        }
        return out
    }

    private fun saveKnown(m: Map<String, String>) {
        sp.edit().putStringSet("known", m.map { "${it.key}|${it.value}" }.toSet()).apply()
    }

    fun nameOf(addr: String): String? = known()[addr]

    fun remember(addr: String, name: String) {
        val m = known()
        m[addr] = name.replace("|", " ").trim().ifEmpty { tr("my_stick") }
        saveKnown(m)
    }

    fun forget(addr: String) {
        val m = known()
        m.remove(addr)
        saveKnown(m)
        if (defaultAddr == addr) defaultAddr = null
    }

    var defaultAddr: String?
        get() = sp.getString("default", null)
        set(v) {
            sp.edit().putString("default", v).apply()
        }

    fun getInt(k: String, d: Int) = sp.getInt(k, d)
    fun getFloat(k: String, d: Float) = sp.getFloat(k, d)
    fun putInt(k: String, v: Int) = sp.edit().putInt(k, v).apply()
    fun putFloat(k: String, v: Float) = sp.edit().putFloat(k, v).apply()
}

/** Últimos 4 bytes de la dirección, igual que la app oficial (**:**:CC:01:60:31). */
fun macTail(addr: String): String = if (addr.length >= 11) addr.substring(addr.length - 11) else addr

fun signalText(rssi: Int): String = when {
    rssi >= -60 -> tr("sig_near")
    rssi >= -75 -> tr("sig_mid")
    else -> tr("sig_far")
}
