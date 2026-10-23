package de.roni.alehead

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject

/**
 * HUD-Anzeige. Das Display ist nur an, solange eine Fahrt läuft:
 *  - Fahrtbeginn (Android Auto verbunden): Handy öffnet die App, das Display geht an.
 *  - Android Auto getrennt: Handy schickt "close" → ausblenden, Display aus.
 *  - Handy außer Reichweite / Verbindung weg: nach 10 s → ausblenden, Display aus.
 *  - Keine Daten mehr vom Handy (z. B. Akku leer): nach 20 s → ausblenden, Display aus.
 */
class HudActivity : Activity(), MessageClient.OnMessageReceivedListener {

    companion object {
        /** Ohne jemals Daten bekommen zu haben: nach 60 s schließen (z. B. App versehentlich geöffnet). */
        private const val NEVER_DATA_CLOSE_MS = 60_000L
        /** Daten kamen, sind aber ausgeblieben: nach 20 s schließen. */
        private const val DATA_LOST_CLOSE_MS = 20_000L
        /** So oft wird geprüft, ob das Handy noch verbunden ist. */
        private const val CHECK_MS = 5_000L
        /** So viele Prüfungen hintereinander ohne Handy → schließen (= 10 s). */
        private const val NO_PHONE_CHECKS = 2
    }

    private lateinit var hud: HudView
    private val handler = Handler(Looper.getMainLooper())
    private var openedAt = 0L
    private var noPhoneCount = 0
    private var closing = false

    private val watchdog = object : Runnable {
        override fun run() {
            if (closing) return
            val now = SystemClock.uptimeMillis()
            val last = hud.lastMessageAt
            val tooLong = if (last == 0L) now - openedAt > NEVER_DATA_CLOSE_MS else now - last > DATA_LOST_CLOSE_MS
            if (tooLong) {
                close()
                return
            }
            checkPhoneConnected()
            handler.postDelayed(this, CHECK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeScreen()
        window.attributes = window.attributes.apply { screenBrightness = 1f }

        hud = HudView(this)
        setContentView(hud)
        openedAt = SystemClock.uptimeMillis()
        Wearable.getMessageClient(this).addListener(this)
        handler.postDelayed(watchdog, CHECK_MS)
    }

    /** Bei Fahrtbeginn das Display einschalten, auch wenn die Uhr gerade aus ist. */
    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun checkPhoneConnected() {
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                if (closing) return@addOnSuccessListener
                if (nodes.isEmpty()) {
                    noPhoneCount++
                    if (noPhoneCount >= NO_PHONE_CHECKS) close()
                } else {
                    noPhoneCount = 0
                }
            }
    }

    /** Sanft ausblenden, App schließen und das Display ausschalten. */
    private fun close() {
        if (closing) return
        closing = true
        handler.removeCallbacks(watchdog)
        hud.fadeOutThen {
            screenOff()
            finish()
        }
    }

    @Suppress("DEPRECATION")
    private fun screenOff() {
        // Display darf wieder ausgehen
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 27) {
            setTurnScreenOn(false)
            setShowWhenLocked(false)
        }
        window.attributes = window.attributes.apply { screenBrightness = 0.01f }

        // Sofort aus, wenn AleHead als Geräteadministrator freigeschaltet ist (macht Installieren.bat).
        // Sonst geht das Display nach der normalen Display-Zeitüberschreitung der Uhr aus.
        try {
            val dpm = getSystemService(DevicePolicyManager::class.java)
            if (dpm != null && dpm.isAdminActive(ComponentName(this, AdminReceiver::class.java))) {
                dpm.lockNow()
            }
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        Wearable.getMessageClient(this).removeListener(this)
        super.onDestroy()
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            "/alehead/state" -> {
                val json = try {
                    JSONObject(String(event.data, Charsets.UTF_8))
                } catch (_: Exception) {
                    return
                }
                runOnUiThread {
                    noPhoneCount = 0
                    hud.update(json)
                }
            }
            "/alehead/icon" -> {
                val bmp = BitmapFactory.decodeByteArray(event.data, 0, event.data.size)
                runOnUiThread { hud.setIcon(bmp) }
            }
            // Android Auto getrennt → sanft ausblenden, schließen, Display aus
            "/alehead/close" -> runOnUiThread { close() }
        }
    }
}
