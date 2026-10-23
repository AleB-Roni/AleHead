package de.roni.alehead

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Wird vom System geweckt, wenn sich ein Bluetooth-Gerät verbindet oder trennt.
 * Verbindet sich die Head Unit, startet der HudService und prüft, ob Android Auto kommt.
 */
class BtReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val prefs = Prefs(ctx)
        if (!prefs.autoMode) return

        val address = deviceOf(intent)?.address.orEmpty()
        val filter = prefs.btAddress
        if (filter.isNotEmpty() && !filter.equals(address, ignoreCase = true)) return

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                if (HudService.running) return
                try {
                    HudService.start(ctx, HudService.ACTION_AUTO)
                } catch (_: Exception) {
                    // Android hat den Start aus dem Hintergrund blockiert → Hinweis zum Antippen
                    showTapToStart(ctx)
                }
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                // Nur bei fest ausgewählter Head Unit sicher – sonst übernimmt die Android-Auto-Erkennung.
                if (filter.isNotEmpty() && HudService.running && !HudService.manual) {
                    ctx.stopService(Intent(ctx, HudService::class.java))
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun deviceOf(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    private fun showTapToStart(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("alehead_hint", "AleHead Hinweise", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val pi = PendingIntent.getForegroundService(
            ctx, 7,
            Intent(ctx, HudService::class.java).setAction(HudService.ACTION_AUTO),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(ctx, "alehead_hint")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("AleHead starten?")
            .setContentText("Tippen, um das HUD für diese Fahrt zu starten")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(7, n)
        } catch (_: SecurityException) {
        }
    }
}
