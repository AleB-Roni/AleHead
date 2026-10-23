package de.roni.alehead

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject

/** Schickt den Zustand per Wear-Data-Layer (MessageClient) an alle verbundenen Uhren. */
object WatchLink {
    const val PATH_STATE = "/alehead/state"
    const val PATH_ICON = "/alehead/icon"
    const val PATH_OPEN = "/alehead/open"
    const val PATH_CLOSE = "/alehead/close"

    private var nodes: List<Node> = emptyList()
    private var nodesAt = 0L
    private var lastIconHash = 0
    private var lastIconAt = 0L

    /** Echten Zustand senden (wird während des Testmodus unterdrückt). */
    fun publish(ctx: Context) {
        if (TestMode.active) return
        send(ctx, HudState.toJson(ctx), if (HudState.navActive) HudState.icon else null)
    }

    fun send(ctx: Context, json: JSONObject, icon: ByteArray?) {
        val app = ctx.applicationContext
        val data = json.toString().toByteArray(Charsets.UTF_8)
        val now = System.currentTimeMillis()
        val iconHash = icon?.contentHashCode() ?: 0
        // Icon nur bei Änderung schicken – und alle 10 s erneut, falls die Uhr-App später gestartet wurde.
        val withIcon = icon != null && (iconHash != lastIconHash || now - lastIconAt > 10_000)
        if (withIcon) {
            lastIconHash = iconHash
            lastIconAt = now
        }
        withNodes(app) { list ->
            val mc = Wearable.getMessageClient(app)
            for (n in list) {
                if (withIcon && icon != null) mc.sendMessage(n.id, PATH_ICON, icon)
                mc.sendMessage(n.id, PATH_STATE, data)
            }
        }
    }

    /** Öffnet die AleHead-App auf der Uhr. */
    fun openOnWatch(ctx: Context) {
        val app = ctx.applicationContext
        nodesAt = 0
        withNodes(app) { list ->
            val mc = Wearable.getMessageClient(app)
            for (n in list) {
                // Weg 1: Nachricht an den Listener-Dienst der Uhr (klappt bei Wear OS 2 zuverlässig)
                mc.sendMessage(n.id, PATH_OPEN, ByteArray(0))
                // Weg 2: offizieller Remote-Start (für neuere Wear-OS-Versionen)
                try {
                    val intent = Intent(Intent.ACTION_VIEW)
                        .addCategory(Intent.CATEGORY_BROWSABLE)
                        .setData(Uri.parse("alehead://hud"))
                    RemoteActivityHelper(app).startRemoteActivity(intent, n.id)
                } catch (_: Exception) {
                }
            }
        }
    }

    /** Schließt die AleHead-App auf der Uhr. */
    fun closeOnWatch(ctx: Context) {
        val app = ctx.applicationContext
        withNodes(app) { list ->
            val mc = Wearable.getMessageClient(app)
            for (n in list) mc.sendMessage(n.id, PATH_CLOSE, ByteArray(0))
        }
    }

    fun refreshNodes(ctx: Context) {
        nodesAt = 0
        withNodes(ctx.applicationContext) { }
    }

    private fun withNodes(app: Context, block: (List<Node>) -> Unit) {
        val now = System.currentTimeMillis()
        val maxAge = if (nodes.isEmpty()) 5_000 else 30_000
        if (nodesAt != 0L && now - nodesAt < maxAge) {
            block(nodes)
            return
        }
        nodesAt = now
        Wearable.getNodeClient(app).connectedNodes
            .addOnSuccessListener { list ->
                nodes = list
                nodesAt = System.currentTimeMillis()
                HudState.watchName = list.joinToString { it.displayName }
                block(list)
            }
            .addOnFailureListener {
                nodes = emptyList()
                HudState.watchName = ""
            }
    }
}
