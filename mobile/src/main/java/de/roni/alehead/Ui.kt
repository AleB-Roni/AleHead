package de.roni.alehead

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val Green = Color(0xFF4CAF50)

@Composable
fun AleTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private class SetupItem(val title: String, val desc: String, val done: Boolean, val action: () -> Unit)

@Composable
fun AleHeadScreen(s: Snap, icon: ImageBitmap?, a: Actions) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var mirror by remember { mutableStateOf(prefs.mirror) }
    var tol by remember { mutableIntStateOf(prefs.tolerance) }
    var auto by remember { mutableStateOf(prefs.autoMode) }
    var btName by remember { mutableStateOf(prefs.btName) }
    var showPicker by remember { mutableStateOf(false) }
    var showDebug by remember { mutableStateOf(false) }

    val setup = listOf(
        SetupItem("Benachrichtigungszugriff", "Damit AleHead die Google-Maps-Hinweise lesen kann", s.notifAccess, a.openNotifAccess),
        SetupItem("Standort", "Für Geschwindigkeit und Tempolimit", s.location, a.askLocation),
        SetupItem("Standort: Immer erlauben", "Damit GPS auch beim automatischen Start läuft", s.bgLocation, a.askBackground),
        SetupItem("Bluetooth", "Erkennt, wann du ins Auto steigst", s.bluetooth, a.askBluetooth),
        SetupItem("Akku nicht einschränken", "Sonst beendet One UI den Dienst während der Fahrt", s.battery, a.askBattery),
    )

    Scaffold { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(pad)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Header(s, auto)

            // ---- Live-Vorschau ----
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    HudPreview(s, icon, mirror, tol)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = a.toggleManual) {
                            Crossfade(targetState = s.running, label = "startBtn") { running ->
                                Text(if (running) "HUD beenden" else "Jetzt starten")
                            }
                        }
                        FilledTonalButton(onClick = a.test) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Test an Uhr")
                        }
                    }
                }
            }

            // ---- Status ----
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("Auto", s.aa, Modifier.weight(1f))
                StatusPill("Uhr", s.watch.isNotEmpty(), Modifier.weight(1f))
                StatusPill("Maps", s.nav, Modifier.weight(1f))
                StatusPill("GPS", s.speed >= 0, Modifier.weight(1f))
            }

            AnimatedVisibility(
                visible = s.warning.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(10.dp))
                        Text(s.warning, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // ---- Einrichtung (verschwindet, sobald alles erledigt ist) ----
            AnimatedVisibility(
                visible = setup.any { !it.done },
                enter = fadeIn() + expandVertically(),
                exit = fadeOut(tween(400)) + shrinkVertically(tween(400))
            ) {
                SectionCard("Einrichtung") {
                    setup.forEachIndexed { i, item ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SetupRow(item)
                    }
                }
            }

            // ---- Automatik ----
            SectionCard("Automatik") {
                SwitchRow(
                    title = "Nur mit Android Auto",
                    desc = "Startet beim Einsteigen und beendet sich danach komplett",
                    checked = auto,
                    onChange = { auto = it; prefs.autoMode = it }
                )
                AnimatedVisibility(visible = auto, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clickable { showPicker = true }
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Head Unit (Bluetooth)", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    btName.ifEmpty { "Jedes Gerät – besser auswählen" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Filled.KeyboardArrowDown, null)
                        }
                    }
                }
            }

            // ---- Anzeige ----
            SectionCard("Anzeige auf der Uhr") {
                SwitchRow(
                    title = "Spiegel-Modus",
                    desc = "Für die Reflexion in der Windschutzscheibe",
                    checked = mirror,
                    onChange = {
                        mirror = it
                        prefs.mirror = it
                        WatchLink.publish(ctx)
                    }
                )
                Spacer(Modifier.size(12.dp))
                Text("Tempo rot ab Limit + $tol km/h", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = tol.toFloat(),
                    onValueChange = {
                        tol = it.roundToInt()
                        prefs.tolerance = tol
                    },
                    onValueChangeFinished = { WatchLink.publish(ctx) },
                    valueRange = 0f..20f,
                    steps = 19
                )
            }

            // ---- Debug ----
            DebugCard(s.debug, showDebug) { showDebug = !showDebug }

            Text(
                "AleHead 0.3",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    if (showPicker) {
        DevicePicker(
            devices = remember { a.loadDevices() },
            onPick = { dev ->
                prefs.btAddress = dev?.address.orEmpty()
                prefs.btName = dev?.name.orEmpty()
                btName = prefs.btName
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun Header(s: Snap, auto: Boolean) {
    val line = when {
        s.active && s.aa -> "Aktiv · Android Auto verbunden"
        s.active -> "Aktiv · manuell gestartet"
        s.running -> "Wartet auf Android Auto …"
        auto -> "Schläft · startet mit Android Auto"
        else -> "Aus"
    }
    Column(Modifier.padding(top = 8.dp, start = 4.dp)) {
        Text("AleHead", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        AnimatedContent(
            targetState = line,
            transitionSpec = {
                (fadeIn(tween(300)) + slideInVertically { it / 2 }) togetherWith
                    (fadeOut(tween(200)) + slideOutVertically { -it / 2 })
            },
            label = "status"
        ) { text ->
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusPill(label: String, on: Boolean, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(
        if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        tween(400), label = "pillBg"
    )
    Surface(shape = RoundedCornerShape(18.dp), color = bg, modifier = modifier) {
        Column(
            Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PulseDot(on)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PulseDot(on: Boolean) {
    val color by animateColorAsState(if (on) Green else MaterialTheme.colorScheme.outline, tween(400), label = "dot")
    val t = rememberInfiniteTransition(label = "pulse")
    val pulse by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "pulseValue"
    )
    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        if (on) {
            Box(
                Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        val sc = 0.45f + pulse * 0.75f
                        scaleX = sc
                        scaleY = sc
                        alpha = (1f - pulse) * 0.6f
                    }
                    .background(color, CircleShape)
            )
        }
        Box(
            Modifier
                .size(9.dp)
                .background(color, CircleShape)
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            content()
        }
    }
}

@Composable
private fun SetupRow(item: SetupItem) {
    val tint by animateColorAsState(
        if (item.done) Green else MaterialTheme.colorScheme.error, tween(400), label = "setupTint"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(tint.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (item.done) Icon(Icons.Filled.Check, null, tint = tint, modifier = Modifier.size(18.dp))
            else Box(Modifier.size(8.dp).background(tint, CircleShape))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge)
            Text(item.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(visible = !item.done, enter = fadeIn(), exit = fadeOut()) {
            TextButton(onClick = item.action) { Text("Erlauben") }
        }
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DebugCard(text: String, open: Boolean, onToggle: () -> Unit) {
    val rot by animateFloatAsState(if (open) 180f else 0f, tween(300), label = "chevron")
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Debug", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Was kommt von Google Maps an?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.rotate(rot))
            }
            AnimatedVisibility(visible = open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Text(
                    text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun DevicePicker(devices: List<BtDev>, onPick: (BtDev?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Head Unit auswählen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Wähle das Bluetooth-Gerät deiner Android-Auto-Box. Dann startet AleHead nur, wenn sich genau dieses Gerät verbindet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                PickerRow("Jedes Gerät", "Prüft bei jeder Bluetooth-Verbindung") { onPick(null) }
                devices.forEach { d -> PickerRow(d.name, d.address) { onPick(d) } }
                if (devices.isEmpty()) {
                    Text("Keine gekoppelten Geräte gefunden.", modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
}

@Composable
private fun PickerRow(title: String, sub: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
