package de.roni.alehead

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/** Schickt 15 Sekunden lang eine Fake-Navigation an die Uhr – zum Testen am Schreibtisch. */
object TestMode {
    @Volatile var active = false
    private val handler = Handler(Looper.getMainLooper())
    private var until = 0L
    private var step = 0

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        until = System.currentTimeMillis() + 15_000
        if (active) return
        active = true
        step = 0
        val arrow = arrowPng()
        handler.post(object : Runnable {
            override fun run() {
                if (System.currentTimeMillis() > until) {
                    active = false
                    WatchLink.publish(app)
                    return
                }
                val p = Prefs(app)
                val dist = (300 - step * 20).coerceAtLeast(0)
                val json = JSONObject()
                    .put("nav", true)
                    .put("dist", "$dist m")
                    .put("p1", "Rechts abbiegen")
                    .put("p2", "Leopoldstraße")
                    .put("info", "Testmodus")
                    .put("speed", 58)
                    .put("limit", 50)
                    .put("tol", p.tolerance)
                    .put("mirror", p.mirror)
                WatchLink.send(app, json, arrow)
                step++
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun arrowPng(): ByteArray {
        val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 18f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val stem = Path().apply {
            moveTo(42f, 116f)
            lineTo(42f, 66f)
            quadTo(42f, 52f, 56f, 52f)
            lineTo(84f, 52f)
        }
        c.drawPath(stem, stroke)
        val head = Path().apply {
            moveTo(80f, 26f)
            lineTo(116f, 52f)
            lineTo(80f, 78f)
            close()
        }
        c.drawPath(head, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        return ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }
}
