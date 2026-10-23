package de.roni.alehead

import android.content.Context
import org.json.JSONObject

/** Aktueller Zustand, der an die Uhr geschickt wird. Wird nur vom Main-Thread verändert. */
object HudState {
    @Volatile var navActive = false
    @Volatile var distance = ""      // z. B. "300 m"
    @Volatile var primary = ""       // z. B. "Rechts abbiegen" / Straße
    @Volatile var secondary = ""     // zweite Zeile, falls vorhanden
    @Volatile var info = ""          // Sub-Text (oft Ankunftszeit)
    @Volatile var icon: ByteArray? = null   // Abbiege-Pfeil als PNG (direkt von Google Maps)

    @Volatile var speedKmh = -1      // -1 = unbekannt
    @Volatile var limitKmh = -1      // -1 = unbekannt, 0 = kein Limit
    @Volatile var limitRaw = ""      // OSM-Rohwert für Debug

    @Volatile var lastRaw = "(noch keine Maps-Benachrichtigung gesehen)"
    @Volatile var watchName = ""

    fun clearNav() {
        navActive = false
        distance = ""
        primary = ""
        secondary = ""
        info = ""
        icon = null
    }

    fun toJson(ctx: Context): JSONObject {
        val p = Prefs(ctx)
        return JSONObject()
            .put("nav", navActive)
            .put("dist", distance)
            .put("p1", primary)
            .put("p2", secondary)
            .put("info", info)
            .put("speed", speedKmh)
            .put("limit", limitKmh)
            .put("tol", p.tolerance)
            .put("mirror", p.mirror)
    }

    fun limitLabel(): String = when {
        limitKmh < 0 -> "unbekannt"
        limitKmh == 0 -> "kein Limit"
        else -> "$limitKmh km/h"
    } + if (limitRaw.isNotEmpty()) "  (OSM: $limitRaw)" else ""

    fun debugText(): String = buildString {
        append("Navigation aktiv: ").append(if (navActive) "ja" else "nein").append('\n')
        append("Distanz: ").append(distance.ifEmpty { "–" }).append('\n')
        append("Zeile 1: ").append(primary.ifEmpty { "–" }).append('\n')
        append("Zeile 2: ").append(secondary.ifEmpty { "–" }).append('\n')
        append("Info: ").append(info.ifEmpty { "–" }).append('\n')
        append("Pfeil-Icon: ").append(icon?.let { "${it.size} Bytes" } ?: "keins").append('\n')
        append("Tempo (GPS): ").append(if (speedKmh >= 0) "$speedKmh km/h" else "–").append('\n')
        append("Tempolimit: ").append(limitLabel()).append("\n\n")
        append("Letzte Google-Maps-Benachrichtigung (roh):\n").append(lastRaw)
    }
}

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("alehead", Context.MODE_PRIVATE)

    var mirror: Boolean
        get() = sp.getBoolean("mirror", false)
        set(v) = sp.edit().putBoolean("mirror", v).apply()

    /** Ab wie viel km/h über dem Limit die Geschwindigkeit rot wird. */
    var tolerance: Int
        get() = sp.getInt("tol", 5)
        set(v) = sp.edit().putInt("tol", v).apply()
}
