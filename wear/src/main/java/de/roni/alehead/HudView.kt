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
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Zeichnet das HUD mit flüssigen Animationen:
 *  - Fortschrittsring am Rand füllt sich auf den letzten 1000 m vor dem Abbiegen
 *  - neuer Abbiege-Pfeil springt herein, der alte gleitet nach oben weg
 *  - Geschwindigkeit zählt weich hoch/runter und färbt sich rot beim Zu-schnell-Fahren
 *  - Tempolimit-Schild ploppt bei Änderungen kurz auf
 *  - sanfte Übergänge zwischen Ruhe- und Navigationsansicht sowie beim Spiegeln
 */
class HudView(ctx: Context) : View(ctx) {

    companion object {
        private val DIST_RE = Regex("""(\d+(?:[.,]\d+)?)\s?(km|m|mi|ft)""", RegexOption.IGNORE_CASE)

        fun parseMeters(dist: String): Float {
            val m = DIST_RE.find(dist) ?: return -1f
            val v = m.groupValues[1].replace(',', '.').toFloatOrNull() ?: return -1f
            return when (m.groupValues[2].lowercase(Locale.ROOT)) {
                "km" -> v * 1000f
                "mi" -> v * 1609f
                "ft" -> v * 0.3048f
                else -> v
            }
        }
    }

    // ---- Zielwerte (vom Handy) ----
    private var nav = false
    private var dist = ""
    private var line1 = ""
    private var line2 = ""
    private var speed = -1
    private var limit = -1
    private var tolerance = 5
    private var mirror = false
    private var icon: Bitmap? = null
    private var oldIcon: Bitmap? = null

    /** Zeitpunkt (uptimeMillis) der letzten Nachricht vom Handy, 0 = noch nie. */
    var lastMessageAt = 0L
        private set

    // ---- animierte Werte ----
    private var aNav = 0f
    private var aSpeed = 0f
    private var aOver = 0f
    private var aRing = 0f
    private var aLimit = 0f
    private var aFlip = 1f
    private var aMaster = 0f
    private var iconT = 1f
    private var limitBumpT = 1f
    private var lastFrame = 0L

    private var closing = false
    private var onClosed: (() -> Unit)? = null

    // ---- Einbrennschutz ----
    private var shiftX = 0f
    private var shiftY = 0f
    private var lastShift = 0L
    private val rnd = Random()

    // ---- Farben & Stifte ----
    private val grey = Color.rgb(150, 150, 150)
    private val red = Color.rgb(229, 57, 53)
    private val accent = Color.rgb(64, 196, 255)
    private val track = Color.rgb(30, 30, 30)

    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
    }
    private val overshoot = OvershootInterpolator(2.2f)
    private val decel = DecelerateInterpolator(1.8f)
    private val clock = SimpleDateFormat("HH:mm", Locale.GERMANY)
    private val clipPath = Path()
    private val rect = RectF()

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

    // ---------- Eingänge ----------

    fun update(j: JSONObject) {
        nav = j.optBoolean("nav", false)
        dist = j.optString("dist", "")
        line1 = j.optString("p1", "")
        line2 = j.optString("p2", "")
        speed = j.optInt("speed", -1)
        val newLimit = j.optInt("limit", -1)
        if (newLimit >= 0 && newLimit != limit) limitBumpT = 0f
        limit = newLimit
        tolerance = j.optInt("tol", 5)
        mirror = j.optBoolean("mirror", false)
        lastMessageAt = SystemClock.uptimeMillis()
        invalidate()
    }

    fun setIcon(b: Bitmap?) {
        if (b == null) return
        val cur = icon
        if (cur != null && cur.sameAs(b)) return
        oldIcon = cur
        icon = b
        iconT = 0f
        invalidate()
    }

    /** Blendet alles aus und ruft danach [then] auf. */
    fun fadeOutThen(then: () -> Unit) {
        if (closing) return
        closing = true
        onClosed = then
        invalidate()
        // Sicherheitsnetz, falls gerade nicht gezeichnet wird
        postDelayed({ fireClosed() }, 1200)
    }

    private fun fireClosed() {
        val cb = onClosed ?: return
        onClosed = null
        cb()
    }

    // ---------- Zeichnen ----------

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 16f else (now - lastFrame).coerceIn(1L, 100L).toFloat()
        lastFrame = now

        val w = width.toFloat()
        val h = height.toFloat()
        val u = min(w, h)

        val stale = lastMessageAt == 0L || now - lastMessageAt > 8_000
        val showNav = nav && !stale
        val over = limit > 0 && speed > limit + tolerance
        val distM = if (showNav) parseMeters(dist) else -1f
        val ringTarget = if (distM >= 0f) 1f - (distM / 1000f).coerceIn(0f, 1f) else 0f
        val closeToTurn = showNav && distM in 0f..80f

        // Werte weich an ihre Ziele heranführen
        aNav = approach(aNav, if (showNav) 1f else 0f, dt, 140f)
        if (speed >= 0) aSpeed = approach(aSpeed, speed.toFloat(), dt, 180f)
        aOver = approach(aOver, if (over) 1f else 0f, dt, 120f)
        aRing = approach(aRing, ringTarget, dt, 250f)
        aLimit = approach(aLimit, if (limit >= 0) 1f else 0f, dt, 110f)
        aFlip = approach(aFlip, if (mirror) -1f else 1f, dt, 110f)
        aMaster = approach(aMaster, if (closing) 0f else 1f, dt, if (closing) 90f else 160f)
        iconT = min(1f, iconT + dt / 450f)
        limitBumpT = min(1f, limitBumpT + dt / 450f)

        if (now - lastShift > 60_000) {
            shiftX = (rnd.nextInt(9) - 4).toFloat()
            shiftY = (rnd.nextInt(9) - 4).toFloat()
            lastShift = now
        }

        c.drawColor(Color.BLACK)
        c.save()
        // Spiegeln als weiche "Umdreh"-Animation
        c.scale(if (abs(aFlip) < 0.02f) 0.02f else aFlip, 1f, w / 2f, h / 2f)
        c.translate(shiftX, shiftY)

        layer(c, aMaster) {
            drawRing(c, w, h, u, now, closeToTurn)
            layer(c, 1f - aNav) {
                val sc = 1f - aNav * 0.08f
                c.save()
                c.scale(sc, sc, w / 2f, h / 2f)
                drawIdle(c, w, h, u, stale, now)
                c.restore()
            }
            layer(c, aNav) {
                c.save()
                c.translate(0f, (1f - aNav) * u * 0.05f)
                drawNav(c, w, h, u, now, closeToTurn)
                c.restore()
            }
        }
        c.restore()

        if (closing && aMaster < 0.02f) fireClosed()

        val animating = closing ||
            abs(aNav - (if (showNav) 1f else 0f)) > 0.003f ||
            (speed >= 0 && abs(aSpeed - speed) > 0.3f) ||
            abs(aOver - (if (over) 1f else 0f)) > 0.003f ||
            abs(aRing - ringTarget) > 0.002f ||
            abs(aLimit - (if (limit >= 0) 1f else 0f)) > 0.003f ||
            abs(aFlip - (if (mirror) -1f else 1f)) > 0.003f ||
            aMaster < 0.997f || iconT < 1f || limitBumpT < 1f ||
            over || closeToTurn || stale
        if (animating) postInvalidateOnAnimation()
    }

    private fun drawRing(c: Canvas, w: Float, h: Float, u: Float, now: Long, closeToTurn: Boolean) {
        if (aNav < 0.01f) return
        val stroke = u * 0.025f
        val r = u / 2f - stroke
        rect.set(w / 2f - r, h / 2f - r, w / 2f + r, h / 2f + r)

        ringPaint.strokeWidth = stroke
        ringPaint.color = track
        ringPaint.alpha = (aNav * 255).toInt()
        c.drawArc(rect, 0f, 360f, false, ringPaint)

        if (aRing > 0.002f) {
            if (closeToTurn) {
                // Glühen kurz vor dem Abbiegen
                val glow = 0.5f + 0.5f * sin(now / 180.0).toFloat()
                ringPaint.strokeWidth = stroke * 2.2f
                ringPaint.color = accent
                ringPaint.alpha = (aNav * glow * 70).toInt()
                c.drawArc(rect, -90f, 360f * aRing, false, ringPaint)
                ringPaint.strokeWidth = stroke
            }
            ringPaint.color = accent
            ringPaint.alpha = (aNav * 255).toInt()
            c.drawArc(rect, -90f, 360f * aRing, false, ringPaint)
        }
    }

    private fun drawNav(c: Canvas, w: Float, h: Float, u: Float, now: Long, closeToTurn: Boolean) {
        val cx = w / 2f
        val iconSize = u * 0.27f
        val iconTop = h * 0.08f
        val iconCy = iconTop + iconSize / 2f
        val e = decel.getInterpolation(iconT)

        // alter Pfeil gleitet nach oben weg
        val old = oldIcon
        if (old != null && iconT < 1f) {
            drawIcon(c, old, cx, iconCy, iconSize, 1f - e * 0.25f, 1f - e, -e * u * 0.08f)
        }
        // neuer Pfeil springt herein (+ Pulsieren kurz vor dem Abbiegen)
        val cur = icon
        if (cur != null) {
            val pop = if (iconT < 1f) overshoot.getInterpolation(iconT).coerceAtLeast(0.01f) else 1f
            val pulse = if (closeToTurn) 1f + 0.06f * sin(now / 160.0).toFloat() else 1f
            drawIcon(c, cur, cx, iconCy, iconSize, pop * pulse, min(1f, iconT * 2.5f), 0f)
        }
        val iconBottom = iconTop + iconSize

        val distY = iconBottom + u * 0.14f
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = u * 0.15f
        text.color = if (closeToTurn) accent else Color.WHITE
        c.drawText(dist, cx, distY, text)

        text.typeface = Typeface.DEFAULT
        text.textSize = u * 0.065f
        text.color = Color.WHITE
        drawEllipsized(c, line1, cx, distY + u * 0.085f, u * 0.80f)
        text.color = grey
        text.textSize = u * 0.055f
        drawEllipsized(c, line2, cx, distY + u * 0.15f, u * 0.66f)

        val rowY = h * 0.80f
        val offset = u * 0.13f * aLimit
        drawLimit(c, cx - offset, rowY, u * 0.08f, now)
        drawSpeed(c, cx + offset, rowY, u * 0.11f)
    }

    private fun drawIdle(c: Canvas, w: Float, h: Float, u: Float, stale: Boolean, now: Long) {
        val cx = w / 2f
        text.typeface = Typeface.DEFAULT
        text.color = grey
        text.textSize = u * 0.08f
        drawCentered(c, clock.format(Date()), cx, h * 0.15f)

        if (stale) {
            // "atmendes" Warten
            val breathe = 0.55f + 0.45f * sin(now / 450.0).toFloat()
            val dots = ".".repeat(((now / 400) % 4).toInt())
            text.textSize = u * 0.065f
            text.color = Color.WHITE
            text.alpha = (breathe * 255).toInt()
            val label = if (lastMessageAt == 0L) "Warte auf Handy" else "Keine Daten vom Handy"
            drawCentered(c, label + dots, cx, h * 0.50f)
            text.alpha = 255
            text.color = grey
            text.textSize = u * 0.05f
            drawCentered(c, "AleHead", cx, h * 0.60f)
            return
        }

        drawSpeed(c, cx, h * 0.47f, u * 0.26f)
        drawLimit(c, cx, h * 0.80f, u * 0.085f, now)
    }

    private fun drawSpeed(c: Canvas, x: Float, y: Float, size: Float) {
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = size
        text.color = blend(Color.WHITE, red, aOver)
        drawCentered(c, if (speed >= 0) aSpeed.roundToInt().toString() else "–", x, y)
        text.typeface = Typeface.DEFAULT
        text.textSize = size * 0.32f
        text.color = grey
        drawCentered(c, "km/h", x, y + size * 0.72f)
    }

    /** Rundes Verkehrsschild mit Einblend-, Wechsel- und Warn-Animation. */
    private fun drawLimit(c: Canvas, cx: Float, cy: Float, r: Float, now: Long) {
        if (aLimit < 0.01f || limit < 0) return
        val bump = 1f + 0.22f * sin(PI * limitBumpT).toFloat() * (1f - limitBumpT)
        val scale = aLimit * bump

        c.save()
        c.scale(scale, scale, cx, cy)

        // roter Warn-Halo beim Zu-schnell-Fahren
        if (aOver > 0.01f) {
            val pulse = 0.5f + 0.5f * sin(now / 200.0).toFloat()
            paint.style = Paint.Style.STROKE
            paint.color = red
            paint.strokeWidth = r * 0.12f
            paint.alpha = (aOver * (0.35f + 0.65f * pulse) * 255).toInt()
            c.drawCircle(cx, cy, r * (1.18f + 0.08f * pulse), paint)
            paint.alpha = 255
        }

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        c.drawCircle(cx, cy, r, paint)

        if (limit == 0) {
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
        } else {
            paint.style = Paint.Style.STROKE
            paint.color = red
            paint.strokeWidth = r * 0.2f
            c.drawCircle(cx, cy, r * 0.9f, paint)

            text.typeface = Typeface.DEFAULT_BOLD
            text.color = Color.BLACK
            text.textSize = r * (if (limit >= 100) 0.78f else 0.95f)
            drawCentered(c, limit.toString(), cx, cy)
        }
        c.restore()
    }

    // ---------- Helfer ----------

    private fun drawIcon(c: Canvas, b: Bitmap, cx: Float, cy: Float, size: Float, scale: Float, alpha: Float, dy: Float) {
        if (alpha <= 0.01f) return
        val s = size * scale / 2f
        rect.set(cx - s, cy - s + dy, cx + s, cy + s + dy)
        iconPaint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        c.drawBitmap(b, null, rect, iconPaint)
    }

    private inline fun layer(c: Canvas, alpha: Float, block: () -> Unit) {
        if (alpha <= 0.01f) return
        val save = if (alpha < 0.99f) c.saveLayerAlpha(null, (alpha * 255).toInt()) else c.save()
        block()
        c.restoreToCount(save)
    }

    private fun approach(cur: Float, target: Float, dt: Float, tauMs: Float): Float {
        val k = 1f - exp(-dt / tauMs)
        val v = cur + (target - cur) * k
        return if (abs(target - v) < 0.0005f) target else v
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * f).roundToInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * f).roundToInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).roundToInt()
        )
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
