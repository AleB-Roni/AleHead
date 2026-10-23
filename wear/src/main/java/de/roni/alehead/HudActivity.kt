package de.roni.alehead

import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.WindowManager
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject

class HudActivity : Activity(), MessageClient.OnMessageReceivedListener {

    private lateinit var hud: HudView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Display bleibt an und auf voller Helligkeit, solange die App offen ist.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 1f }

        hud = HudView(this)
        setContentView(hud)
        Wearable.getMessageClient(this).addListener(this)
    }

    override fun onDestroy() {
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
        }
    }
}
