package de.roni.alehead

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Läuft nur während der Fahrt:
 *  - AUTO:   gestartet per Bluetooth, wartet bis zu 2 Minuten auf Android Auto,
 *            beendet sich sofort, wenn Android Auto getrennt wird.
 *  - MANUAL: über die App gestartet, läuft bis man ihn stoppt.
 * Solange er aktiv ist: GPS-Tempo, Tempolimit aus OSM und Maps-Auslesen.
 */
class HudService : Service(), LocationListener {

    companion object {
        @Volatile var running = false
        @Volatile var active = false
        @Volatile var manual = false

        const val ACTION_AUTO = "de.roni.alehead.AUTO"
        const val ACTION_MANUAL = "de.roni.alehead.MANUAL"
        const val ACTION_STOP = "de.roni.alehead.STOP"
        private const val CHANNEL = "alehead"
        private const val WAIT_FOR_AA_MS = 120_000L

        fun start(ctx: Context, action: String) {
            ctx.startForegroundService(Intent(ctx, HudService::class.java).setAction(action))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pool: ExecutorService? = null
    private var lm: LocationManager? = null
    private var carConnection: CarConnection? = null
    private val carObserver = Observer<Int> { type -> onCarType(type) }

    private var lastQueryLoc: Location? = null
    private var lastQueryAt = 0L
    private var querying = false

    private val giveUp = Runnable { if (!active) stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_MANUAL) manual = true

        goForeground(active)

        if (!running) {
            running = true
            pool = Executors.newSingleThreadExecutor()
            observeCar()
        }

        if (manual) {
            activate()
        } else if (!active) {
            handler.removeCallbacks(giveUp)
            handler.postDelayed(giveUp, WAIT_FOR_AA_MS)
        }
        return START_NOT_STICKY
    }

    // ---------- Android-Auto-Erkennung ----------

    private fun observeCar() {
        try {
            val cc = CarConnection(this)
            carConnection = cc
            cc.type.observeForever(carObserver)
        } catch (_: Exception) {
            HudState.warning = "Android-Auto-Erkennung nicht verfügbar"
        }
    }

    private fun onCarType(type: Int) {
        val aa = type == CarConnection.CONNECTION_TYPE_PROJECTION
        HudState.aaConnected = aa
        if (aa && !active) {
            activate()
        } else if (!aa && active && !manual) {
            stopSelf()
        }
    }

    // ---------- Aktiv werden ----------

    private fun activate() {
        if (active) return
        active = true
        handler.removeCallbacks(giveUp)

        val hasGpsRights = goForeground(true)
        if (hasGpsRights) startLocation()

        NavListenerService.setEnabled(this, true)
        WatchLink.openOnWatch(this)
        WatchLink.publish(this)
    }

    /** @return true, wenn der Dienst mit Standort-Typ laufen darf. */
    private fun goForeground(withLocation: Boolean): Boolean {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "AleHead", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, HudService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = when {
            active && HudState.aaConnected -> "Android Auto verbunden – HUD aktiv"
            active -> "HUD aktiv (manuell)"
            else -> "Warte auf Android Auto …"
        }
        val notif = Notification.Builder(this, CHANNEL)
            .setContentTitle("AleHead")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_stat), "Beenden", stop).build())
            .build()

        if (Build.VERSION.SDK_INT < 29) {
            startForeground(1, notif)
            return withLocation
        }
        val base = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        if (withLocation) {
            try {
                startForeground(1, notif, base or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                return true
            } catch (_: Exception) {
                HudState.warning = "Standort auf „Immer erlauben“ stellen, sonst kein Tempo"
            }
        }
        try {
            startForeground(1, notif, base)
        } catch (_: Exception) {
            startForeground(1, notif)
        }
        return false
    }

    // ---------- GPS + Tempolimit ----------

    private fun startLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            HudState.warning = "Standort-Berechtigung fehlt"
            return
        }
        val m = getSystemService(LocationManager::class.java)
        lm = m
        try {
            m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
        } catch (_: Exception) {
            HudState.warning = "GPS konnte nicht gestartet werden"
        }
    }

    override fun onLocationChanged(loc: Location) {
        HudState.speedKmh = if (loc.hasSpeed()) (loc.speed * 3.6f).roundToInt() else -1
        maybeQueryLimit(loc)
        WatchLink.publish(this)
    }

    private fun maybeQueryLimit(loc: Location) {
        if (querying) return
        val now = SystemClock.elapsedRealtime()
        val since = now - lastQueryAt
        if (lastQueryAt != 0L && since < 8_000) return
        val last = lastQueryLoc
        val moved = last == null || last.distanceTo(loc) > 150f
        if (!moved && since < 30_000) return
        val exec = pool ?: return

        querying = true
        lastQueryAt = now
        lastQueryLoc = Location(loc)
        val lat = loc.latitude
        val lon = loc.longitude
        try {
            exec.execute {
                val result = try {
                    SpeedLimit.query(lat, lon)
                } catch (_: Exception) {
                    null
                }
                handler.post {
                    querying = false
                    if (result != null && running) {
                        HudState.limitKmh = result.kmh
                        HudState.limitRaw = result.raw
                        WatchLink.publish(this)
                    }
                }
            }
        } catch (_: Exception) {
            querying = false
        }
    }

    // Für ältere Android-Versionen nötig (dort sind diese Methoden abstrakt).
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    // ---------- Beenden: alles aus ----------

    override fun onDestroy() {
        running = false
        active = false
        manual = false
        handler.removeCallbacksAndMessages(null)
        try {
            carConnection?.type?.removeObserver(carObserver)
        } catch (_: Exception) {
        }
        try {
            lm?.removeUpdates(this)
        } catch (_: Exception) {
        }
        pool?.shutdownNow()
        NavListenerService.setEnabled(this, false)
        HudState.clearAll()
        WatchLink.closeOnWatch(this)
        super.onDestroy()
    }
}
