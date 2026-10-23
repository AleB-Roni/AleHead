package de.roni.alehead

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Momentaufnahme für die Oberfläche (wird alle 400 ms neu erzeugt, solange die App offen ist). */
data class Snap(
    val notifAccess: Boolean = false,
    val location: Boolean = false,
    val bgLocation: Boolean = false,
    val bluetooth: Boolean = false,
    val battery: Boolean = false,
    val running: Boolean = false,
    val active: Boolean = false,
    val manual: Boolean = false,
    val aa: Boolean = false,
    val watch: String = "",
    val nav: Boolean = false,
    val dist: String = "",
    val p1: String = "",
    val p2: String = "",
    val speed: Int = -1,
    val limit: Int = -1,
    val warning: String = "",
    val debug: String = "",
)

data class BtDev(val name: String, val address: String)

class Actions(
    val openNotifAccess: () -> Unit,
    val askLocation: () -> Unit,
    val askBackground: () -> Unit,
    val askBluetooth: () -> Unit,
    val askBattery: () -> Unit,
    val toggleManual: () -> Unit,
    val test: () -> Unit,
    val loadDevices: () -> List<BtDev>,
)

class MainActivity : ComponentActivity() {

    private val snap = mutableStateOf(Snap())
    private val icon = mutableStateOf<ImageBitmap?>(null)
    private var iconHash = 0

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val actions = Actions(
            openNotifAccess = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            askLocation = {
                val p = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
                permLauncher.launch(p.toTypedArray())
            },
            askBackground = {
                if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    toast("Bitte zuerst den normalen Standort erlauben")
                } else if (Build.VERSION.SDK_INT >= 29) {
                    permLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                }
            },
            askBluetooth = {
                if (Build.VERSION.SDK_INT >= 31) permLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            },
            askBattery = {
                try {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                    )
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            },
            toggleManual = {
                if (HudService.running) {
                    stopService(Intent(this, HudService::class.java))
                } else if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    toast("Bitte zuerst Standort erlauben")
                } else {
                    HudService.start(this, HudService.ACTION_MANUAL)
                }
            },
            test = {
                TestMode.start(this)
                toast("Testanzeige läuft 15 Sekunden auf der Uhr")
            },
            loadDevices = { bondedDevices() },
        )

        setContent {
            AleTheme {
                AleHeadScreen(snap.value, icon.value, actions)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                WatchLink.refreshNodes(this@MainActivity)
                while (true) {
                    refresh()
                    delay(400)
                }
            }
        }
    }

    private fun refresh() {
        val bytes = HudState.icon
        val h = bytes?.contentHashCode() ?: 0
        if (h != iconHash) {
            iconHash = h
            icon.value = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }
        snap.value = Snap(
            notifAccess = isListenerEnabled(),
            location = has(Manifest.permission.ACCESS_FINE_LOCATION),
            bgLocation = Build.VERSION.SDK_INT < 29 || has(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            bluetooth = Build.VERSION.SDK_INT < 31 || has(Manifest.permission.BLUETOOTH_CONNECT),
            battery = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName),
            running = HudService.running,
            active = HudService.active,
            manual = HudService.manual,
            aa = HudState.aaConnected,
            watch = HudState.watchName,
            nav = HudState.navActive,
            dist = HudState.distance,
            p1 = HudState.primary,
            p2 = HudState.secondary,
            speed = HudState.speedKmh,
            limit = HudState.limitKmh,
            warning = HudState.warning,
            debug = HudState.debugText(),
        )
    }

    private fun bondedDevices(): List<BtDev> = try {
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        adapter?.bondedDevices.orEmpty()
            .map { BtDev(it.name ?: it.address, it.address) }
            .sortedBy { it.name.lowercase() }
    } catch (_: SecurityException) {
        toast("Bitte zuerst die Bluetooth-Berechtigung erlauben")
        emptyList()
    }

    private fun has(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun isListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(packageName)
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
