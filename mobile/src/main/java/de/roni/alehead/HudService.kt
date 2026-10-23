package de.roni.alehead

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Vordergrund-Dienst: GPS-Geschwindigkeit + Tempolimit aus OSM, schickt beides jede Sekunde an die Uhr. */
class HudService : Service(), LocationListener {

    companion object {
        @Volatile var running = false
        private const val CHANNEL = "alehead"
    }

    private lateinit var lm: LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private val pool = Executors.newSingleThreadExecutor()
    private var lastQueryLoc: Location? = null
    private var lastQueryAt = 0L
    private var querying = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (!running) {
            running = true
            startLocation()
        }
        return START_STICKY
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "AleHead", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = Notification.Builder(this, CHANNEL)
            .setContentTitle("AleHead läuft")
            .setContentText("Tempo & Navigation werden an die Uhr gesendet")
            .setSmallIcon(R.drawable.ic_stat)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, notif)
        }
    }

    private fun startLocation() {
        lm = getSystemService(LocationManager::class.java)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
        } catch (_: Exception) {
            stopSelf()
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

        querying = true
        lastQueryAt = now
        lastQueryLoc = Location(loc)
        val lat = loc.latitude
        val lon = loc.longitude
        pool.execute {
            val result = try {
                SpeedLimit.query(lat, lon)
            } catch (_: Exception) {
                null
            }
            handler.post {
                querying = false
                if (result != null) {
                    HudState.limitKmh = result.kmh
                    HudState.limitRaw = result.raw
                    WatchLink.publish(this)
                }
            }
        }
    }

    // Für ältere Android-Versionen nötig (dort sind diese Methoden abstrakt).
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun onDestroy() {
        running = false
        try {
            if (::lm.isInitialized) lm.removeUpdates(this)
        } catch (_: Exception) {
        }
        pool.shutdownNow()
        HudState.speedKmh = -1
        HudState.limitKmh = -1
        HudState.limitRaw = ""
        WatchLink.publish(this)
        super.onDestroy()
    }
}
