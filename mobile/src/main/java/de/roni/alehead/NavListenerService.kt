package de.roni.alehead

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Liest die laufende Navigations-Benachrichtigung von Google Maps aus:
 * Distanz, Anweisung/Straße und das Abbiege-Pfeil-Icon.
 */
class NavListenerService : NotificationListenerService() {

    companion object {
        const val MAPS = "com.google.android.apps.maps"
        private val DIST = Regex("""(\d+(?:[.,]\d+)?)\s?(km|m|mi|ft)(?![a-zA-ZäöüÄÖÜ])""", RegexOption.IGNORE_CASE)
        private val LEADING = Regex("""^\s*(in|nach|after)\s+""", RegexOption.IGNORE_CASE)
    }

    private var navKey: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.GERMANY)

    // Solange navigiert wird, alle 2 s senden, damit die Uhr nicht "Keine Daten" zeigt.
    private val heartbeat = object : Runnable {
        override fun run() {
            if (HudState.navActive) {
                WatchLink.publish(this@NavListenerService)
                handler.postDelayed(this, 2000)
            }
        }
    }

    override fun onListenerConnected() {
        try {
            activeNotifications?.forEach { handle(it) }
        } catch (_: Exception) {
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) handle(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null && sbn.key == navKey) {
            navKey = null
            HudState.clearNav()
            handler.removeCallbacks(heartbeat)
            WatchLink.publish(this)
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        if (sbn.packageName != MAPS) return
        val n = sbn.notification ?: return
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().trim()
        val sub = ex.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty().trim()
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty().trim()
        val ongoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val isNav = n.category == "navigation" || ongoing
        val iconPng = if (isNav) extractIcon(n) else null

        HudState.lastRaw = buildString {
            append("Zeit: ").append(timeFmt.format(Date())).append('\n')
            append("category: ").append(n.category).append("   ongoing: ").append(ongoing).append('\n')
            append("title: ").append(title).append('\n')
            append("text: ").append(text).append('\n')
            append("subText: ").append(sub).append('\n')
            append("bigText: ").append(big).append('\n')
            append("largeIcon: ").append(if (iconPng != null) "ja" else "nein")
            append("   customView: ").append(n.contentView != null).append('\n')
            append("extras: ").append(ex.keySet().joinToString())
        }

        if (!isNav) return

        val match = DIST.find(title) ?: DIST.find(text)
        val dist = match?.value?.trim().orEmpty()
        val lines = listOf(title, text, if (title.isEmpty() && text.isEmpty()) big else "")
            .map { clean(it, dist) }
            .filter { it.isNotEmpty() }
            .distinct()

        navKey = sbn.key
        HudState.navActive = true
        HudState.distance = dist
        HudState.primary = lines.getOrElse(0) { "" }
        HudState.secondary = lines.getOrElse(1) { "" }
        HudState.info = sub
        HudState.icon = iconPng

        WatchLink.publish(this)
        handler.removeCallbacks(heartbeat)
        handler.postDelayed(heartbeat, 2000)
    }

    private fun clean(s: String, dist: String): String {
        var r = s
        if (dist.isNotEmpty()) r = r.replace(dist, "")
        r = r.replace(LEADING, "")
        r = r.replace(Regex("""\s+"""), " ").trim(' ', '·', '-', ',', '–')
        return r
    }

    private fun extractIcon(n: Notification): ByteArray? {
        return try {
            val drawable: Drawable? = n.getLargeIcon()?.loadDrawable(this)
            @Suppress("DEPRECATION")
            val bmp: Bitmap = drawable?.let { toBitmap(it) }
                ?: (n.extras.get(Notification.EXTRA_LARGE_ICON) as? Bitmap)
                ?: return null
            val scaled = if (bmp.width > 128 || bmp.height > 128) Bitmap.createScaledBitmap(bmp, 128, 128, true) else bmp
            ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun toBitmap(d: Drawable): Bitmap {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, size, size)
        d.draw(c)
        return bmp
    }
}
