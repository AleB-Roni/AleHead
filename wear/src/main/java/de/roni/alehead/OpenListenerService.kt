package de.roni.alehead

import android.content.Intent
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/** Startet die HUD-Anzeige, sobald das Handy meldet, dass eine Fahrt beginnt. */
class OpenListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == "/alehead/open") {
            try {
                startActivity(
                    Intent(this, HudActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }
}
