package de.roni.alehead

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var startBtn: Button
    private lateinit var tolText: TextView
    private lateinit var iconView: ImageView
    private lateinit var debug: TextView
    private var shownIconHash = 0

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val act = this
        val prefs = Prefs(act)

        val root = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }

        root.addView(TextView(act).apply {
            text = "AleHead"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(act).apply {
            text = "Google-Maps-Hinweise & Tempo auf der Uhr"
            textSize = 14f
            setPadding(0, 0, 0, dp(8))
        })

        status = TextView(act).apply { textSize = 15f; setLineSpacing(0f, 1.2f) }
        root.addView(status)

        root.addView(header("Einrichtung"))
        root.addView(button("1. Benachrichtigungszugriff erlauben") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        root.addView(button("2. Standort & Benachrichtigungen erlauben") { askPermissions() })
        startBtn = button("HUD starten") { toggleService() }
        root.addView(startBtn)

        root.addView(header("Anzeige auf der Uhr"))
        root.addView(Switch(act).apply {
            text = "Spiegel-Modus (für Reflexion in der Scheibe)"
            textSize = 15f
            isChecked = prefs.mirror
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, checked ->
                prefs.mirror = checked
                WatchLink.publish(act)
            }
        })

        val tolRow = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tolText = TextView(act).apply {
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        tolRow.addView(tolText)
        tolRow.addView(button("−") {
            prefs.tolerance = (prefs.tolerance - 1).coerceAtLeast(0)
            updateTol()
            WatchLink.publish(act)
        })
        tolRow.addView(button("+") {
            prefs.tolerance = (prefs.tolerance + 1).coerceAtMost(30)
            updateTol()
            WatchLink.publish(act)
        })
        root.addView(tolRow)

        root.addView(button("Testanzeige an Uhr senden (15 s)") {
            TestMode.start(act)
            Toast.makeText(act, "Testdaten werden gesendet – Uhr-App öffnen", Toast.LENGTH_SHORT).show()
        })

        root.addView(header("Debug – was kommt von Google Maps an?"))
        iconView = ImageView(act).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72))
        }
        root.addView(iconView)
        debug = TextView(act).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(0, dp(8), 0, 0)
        }
        root.addView(debug)

        setContentView(ScrollView(act).apply {
            fitsSystemWindows = true
            addView(root)
        })
        updateTol()
    }

    override fun onResume() {
        super.onResume()
        WatchLink.refreshNodes(this)
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun refresh() {
        val notif = isListenerEnabled()
        val loc = hasLocation()
        status.text = buildString {
            append(if (notif) "✓" else "✗").append("  Benachrichtigungszugriff\n")
            append(if (loc) "✓" else "✗").append("  Standort\n")
            append(if (HudService.running) "✓  HUD-Dienst läuft" else "○  HUD-Dienst aus").append('\n')
            append(
                if (HudState.watchName.isNotEmpty()) "✓  Uhr verbunden: ${HudState.watchName}"
                else "?  Keine Uhr verbunden"
            )
        }
        startBtn.text = if (HudService.running) "HUD stoppen" else "HUD starten"
        debug.text = HudState.debugText()

        val icon = HudState.icon
        val hash = icon?.contentHashCode() ?: 0
        if (hash != shownIconHash) {
            shownIconHash = hash
            iconView.setImageBitmap(icon?.let { BitmapFactory.decodeByteArray(it, 0, it.size) })
        }
    }

    private fun updateTol() {
        tolText.text = "Tempo rot ab Limit + ${Prefs(this).tolerance} km/h"
    }

    private fun toggleService() {
        val intent = Intent(this, HudService::class.java)
        if (HudService.running) {
            stopService(intent)
        } else {
            if (!hasLocation()) {
                askPermissions()
                Toast.makeText(this, "Bitte zuerst Standort erlauben", Toast.LENGTH_SHORT).show()
                return
            }
            startForegroundService(intent)
        }
        handler.postDelayed({ refresh() }, 300)
    }

    private fun askPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(perms.toTypedArray(), 1)
    }

    private fun hasLocation() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun isListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(packageName)
    }

    private fun header(t: String) = TextView(this).apply {
        text = t
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(20), 0, dp(6))
    }

    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
