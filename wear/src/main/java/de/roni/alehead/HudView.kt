package de.roni.alehead

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random
import kotlin.math.min

/** Zeichnet das minimalistische HUD: Pfeil, Distanz, Straße, Tempolimit-Schild, Geschwindigkeit. */
class HudView(ctx: Context) : View(ctx) {

    private var nav = false
    private var dist = ""
    private var line1 = ""
    private var line2 = ""
    private var speed = -1
    private var limit = -1
    private var tolerance = 5
    private var mirror = false
    private var icon: Bitmap? = null
    private var lastMsg = 0L

    private var shiftX = 0f
    private var shiftY = 0f
    private var lastShift = 0L
    private val rnd = Random()

    private val grey = Color.rgb(150, 150, 150)
    private val red = Color.rgb(229, 57, 53)

    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
    }
    private val clock = SimpleDateFormat("HH:mm", Locale.GERMANY)
    private val clipPath = Path()
    private val iconRect = RectF()

    private val ticker = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 1000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(ticker)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    fun update(j: JSONObject) {
        nav = j.optBoolean("nav", false)
        dist = j.optString("dist", "")
        line1 = j.optString("p1", "")
        line2 = j.optString("p2", "")
        speed = j.optInt("speed", -1)
        limit = j.optInt("limit", -1)
        tolerance = j.optInt("tol", 5)
        mirror = j.optBoolean("mirror", false)
        lastMsg = System.currentTimeMillis()
        invalidate()
    }

    fun setIcon(b: Bitmap?) {
        icon = b
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.BLACK)
        val w = width.toFloat()
        val h = height.toFloat()
        val u = min(w, h)
        val now = System.currentTimeMillis()

        // Gegen OLED-Einbrennen: Inhalt jede Minute um ein paar Pixel verschieben.
        if (now - lastShift > 60_000) {
            shiftX = (rnd.nextInt(9) - 4).toFloat()
            shiftY = (rnd.nextInt(9) - 4).toFloat()
            lastShift = now
        }

        val stale = now - lastMsg > 8_000
        c.save()
        if (mirror) c.scale(-1f, 1f, w / 2f, h / 2f)
        c.translate(shiftX, shiftY)
        if (nav && !stale) drawNav(c, w, h, u) else drawIdle(c, h, u, stale)
        c.restore()
    }

    private fun drawNav(c: Canvas, w: Float, h: Float, u: Float) {
        val cx = w / 2f
        val b = icon
        val iconBottom: Float
        if (b != null) {
            val s = u * 0.27f
            val top = h * 0.08f
            iconRect.set(cx - s / 2f, top, cx + s / 2f, top + s)
            c.drawBitmap(b, null, iconRect, iconPaint)
            iconBottom = top + s
        } else {
            iconBottom = h * 0.30f
        }

        val distY = iconBottom + u * 0.14f
        text.color = Color.WHITE
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = u * 0.15f
        c.drawText(dist, cx, distY, text)

        text.typeface = Typeface.DEFAULT
        text.textSize = u * 0.065f
        drawEllipsized(c, line1, cx, distY + u * 0.085f, u * 0.80f)
        text.color = grey
        text.textSize = u * 0.055f
        drawEllipsized(c, line2, cx, distY + u * 0.15f, u * 0.66f)

        val rowY = h * 0.80f
        if (limit >= 0) {
            drawLimit(c, cx - u * 0.13f, rowY, u * 0.08f, limit)
            drawSpeed(c, cx + u * 0.13f, rowY, u * 0.11f)
        } else {
            drawSpeed(c, cx, rowY, u * 0.11f)
        }
    }

    private fun drawIdle(c: Canvas, h: Float, u: Float, stale: Boolean) {
        val cx = width / 2f
        text.typeface = Typeface.DEFAULT
        text.color = grey
        text.textSize = u * 0.08f
        drawCentered(c, clock.format(Date()), cx, h * 0.15f)

        if (stale) {
            text.textSize = u * 0.065f
            drawCentered(c, if (lastMsg == 0L) "Warte auf Handy …" else "Keine Daten vom Handy", cx, h * 0.50f)
            text.textSize = u * 0.05f
            drawCentered(c, "AleHead", cx, h * 0.60f)
            return
        }

        drawSpeed(c, cx, h * 0.47f, u * 0.26f)
        if (limit >= 0) drawLimit(c, cx, h * 0.80f, u * 0.085f, limit)
    }

    private fun drawSpeed(c: Canvas, x: Float, y: Float, size: Float) {
        val over = limit > 0 && speed > limit + tolerance
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = size
        text.color = if (over) red else Color.WHITE
        drawCentered(c, if (speed >= 0) speed.toString() else "–", x, y)
        text.typeface = Typeface.DEFAULT
        text.textSize = size * 0.32f
        text.color = grey
        drawCentered(c, "km/h", x, y + size * 0.72f)
    }

    /** Rundes Verkehrsschild: rot-weiß mit Zahl, oder „Ende aller Beschränkungen“ bei 0. */
    private fun drawLimit(c: Canvas, cx: Float, cy: Float, r: Float, lim: Int) {
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        c.drawCircle(cx, cy, r, paint)

        if (lim == 0) {
            c.save()
            clipPath.reset()
            clipPath.addCircle(cx, cy, r * 0.92f, Path.Direction.CW)
            c.clipPath(clipPath)
            paint.style = Paint.Style.STROKE
            paint.color = Color.BLACK
            paint.strokeWidth = r * 0.07f
            for (i in -2..2) {
                val o = i * r * 0.17f
                c.drawLine(cx - r + o, cy + r + o, cx + r + o, cy - r + o, paint)
            }
            c.restore()
            return
        }

        paint.style = Paint.Style.STROKE
        paint.color = red
        paint.strokeWidth = r * 0.2f
        c.drawCircle(cx, cy, r * 0.9f, paint)

        text.typeface = Typeface.DEFAULT_BOLD
        text.color = Color.BLACK
        text.textSize = r * (if (lim >= 100) 0.78f else 0.95f)
        drawCentered(c, lim.toString(), cx, cy)
    }

    private fun drawCentered(c: Canvas, s: String, x: Float, y: Float) {
        c.drawText(s, x, y - (text.descent() + text.ascent()) / 2f, text)
    }

    private fun drawEllipsized(c: Canvas, s: String, x: Float, baseline: Float, maxW: Float) {
        if (s.isEmpty()) return
        val t = TextUtils.ellipsize(s, text, maxW, TextUtils.TruncateAt.END).toString()
        c.drawText(t, x, baseline, text)
    }
}
