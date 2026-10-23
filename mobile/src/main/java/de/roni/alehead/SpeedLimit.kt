package de.roni.alehead

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

/** Holt das Tempolimit der Straße an einer Position aus OpenStreetMap (Overpass-API). */
object SpeedLimit {

    data class Result(val kmh: Int, val raw: String)

    /** -1 = unbekannt, 0 = kein Limit, >0 = Limit in km/h. Wirft bei Netzwerkfehlern. */
    fun query(lat: Double, lon: Double): Result {
        val la = String.format(Locale.US, "%.6f", lat)
        val lo = String.format(Locale.US, "%.6f", lon)
        val q = """[out:json][timeout:8];way(around:20,$la,$lo)[highway~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street)(_link)?$"][maxspeed];out tags;"""

        val conn = URL("https://overpass-api.de/api/interpreter").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "AleHead/0.1 (private HUD project)")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(("data=" + URLEncoder.encode(q, "UTF-8")).toByteArray()) }
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val els = JSONObject(body).optJSONArray("elements") ?: return Result(-1, "")
            for (i in 0 until els.length()) {
                val raw = els.getJSONObject(i).optJSONObject("tags")?.optString("maxspeed").orEmpty()
                if (raw.isEmpty()) continue
                val v = parse(raw)
                if (v != -1) return Result(v, raw)
            }
            return Result(-1, "")
        } finally {
            conn.disconnect()
        }
    }

    fun parse(raw: String): Int {
        val s = raw.trim().lowercase(Locale.ROOT).substringBefore(';').trim()
        when (s) {
            "none", "de:motorway", "at:motorway" -> return 0
            "de:urban", "at:urban" -> return 50
            "de:rural" -> return 100
            "at:rural" -> return 100
            "de:living_street", "walk", "de:walk" -> return 7
            "de:bicycle_road" -> return 30
        }
        Regex("""zone:?(\d+)""").find(s)?.let { return it.groupValues[1].toInt() }
        Regex("""^(\d+)\s*mph""").find(s)?.let { return (it.groupValues[1].toInt() * 1.609).roundToInt() }
        Regex("""^(\d+)""").find(s)?.let { return it.groupValues[1].toInt() }
        return -1
    }
}
