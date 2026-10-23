package de.roni.alehead

import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject

class HudActivity : Activity(), MessageClient.OnMessageReceivedListener {

    companion object {
        /** Ohne Daten vom Handy schließt sich die App nach 3 Minuten selbst (spart Uhr-Akku). */
        private const val IDLE_CLOSE_MS = 180_000L
    }

    private lateinit var hud: HudView
    private val handler = Handler(Looper.getMainLooper())
    private var openedAt = 0L

    private val idleCheck = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            val lastData = hud.lastMessageAt
            val idleSince = if (lastData == 0L) openedAt else lastData
            if (now - idleSince > IDLE_CLOSE_MS) {
                hud.fadeOutThen { finish() }
                return
            }
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Display bleibt an und auf voller Helligkeit, solange die App offen ist.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 1f }

        hud = HudView(this)
        setContentView(hud)
        openedAt = SystemClock.uptimeMillis()
        Wearable.getMessageClient(this).addListener(this)
        handler.postDelayed(idleCheck, 15_000)
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
                runOnUiThread { hud.update(json) }
            }
            "/alehead/icon" -> {
                val bmp = BitmapFactory.decodeByteArray(event.data, 0, event.data.size)
                runOnUiThread { hud.setIcon(bmp) }
            }
            // Android Auto getrennt → sanft ausblenden und schließen
            "/alehead/close" -> runOnUiThread { hud.fadeOutThen { finish() } }
        }
    }
}
