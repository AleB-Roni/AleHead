package de.roni.alehead

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

val HudRed = Color(0xFFE53935)
val HudAccent = Color(0xFF40C4FF)
private val HudGrey = Color(0xFF9E9E9E)
private val HudTrack = Color(0xFF1E1E1E)

private val DIST_RE = Regex("""(\d+(?:[.,]\d+)?)\s?(km|m|mi|ft)""", RegexOption.IGNORE_CASE)

/** Distanz-Text ("1,2 km") → Meter, -1 wenn unbekannt. */
fun parseMeters(dist: String): Float {
    val m = DIST_RE.find(dist) ?: return -1f
    val v = m.groupValues[1].replace(',', '.').toFloatOrNull() ?: return -1f
    return when (m.groupValues[2].lowercase()) {
        "km" -> v * 1000f
        "mi" -> v * 1609f
        "ft" -> v * 0.3048f
        else -> v
    }
}

/** Ring füllt sich auf den letzten 1000 m vor dem Abbiegen. */
fun ringFraction(dist: String): Float {
    val m = parseMeters(dist)
    if (m < 0) return 0f
    return 1f - (m / 1000f).coerceIn(0f, 1f)
}

/** Live-Vorschau der Uhr-Anzeige in der Handy-App. */
@Composable
fun HudPreview(s: Snap, icon: ImageBitmap?, mirror: Boolean, tol: Int, size: Dp = 220.dp) {
    val speedAnim by animateFloatAsState(
        if (s.speed >= 0) s.speed.toFloat() else 0f,
        tween(700, easing = FastOutSlowInEasing), label = "speed"
    )
    val over = s.limit > 0 && s.speed > s.limit + tol
    val speedColor by animateColorAsState(if (over) HudRed else Color.White, tween(300), label = "over")
    val navA by animateFloatAsState(if (s.nav) 1f else 0f, tween(450), label = "nav")
    val limitScale by animateFloatAsState(
        if (s.limit >= 0) 1f else 0f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow), label = "limit"
    )
    val ring by animateFloatAsState(if (s.nav) ringFraction(s.dist) else 0f, tween(700), label = "ring")
    val flip by animateFloatAsState(if (mirror) -1f else 1f, tween(450), label = "mirror")
    val speedText = if (s.speed >= 0) speedAnim.roundToInt().toString() else "–"

    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = flip }
            .clip(CircleShape)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Fortschrittsring bis zum Abbiegen
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.025f
            val inset = stroke
            val arcSize = Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
            drawArc(
                color = HudTrack, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke)
            )
            if (ring > 0.001f) {
                drawArc(
                    color = HudAccent, startAngle = -90f, sweepAngle = 360f * ring, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }

        // Ruhe-Ansicht: nur Tempo + Limit
        Column(
            Modifier.graphicsLayer {
                alpha = 1f - navA
                val sc = 1f - navA * 0.08f
                scaleX = sc
                scaleY = sc
            },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(speedText, color = speedColor, fontSize = 56.sp, fontWeight = FontWeight.Bold)
            Text("km/h", color = HudGrey, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            LimitSign(s.limit, 36.dp, Modifier.graphicsLayer { scaleX = limitScale; scaleY = limitScale })
        }

        // Navigations-Ansicht
        Column(
            Modifier.graphicsLayer {
                alpha = navA
                translationY = (1f - navA) * 24f
            },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = icon,
                transitionSpec = {
                    (fadeIn(tween(300)) + scaleIn(spring(dampingRatio = 0.5f), initialScale = 0.6f)) togetherWith
                        (fadeOut(tween(200)) + slideOutVertically(tween(250)) { -it / 2 })
                },
                label = "arrow"
            ) { bmp ->
                if (bmp != null) {
                    Image(bmp, null, Modifier.size(54.dp), colorFilter = ColorFilter.tint(Color.White))
                } else {
                    Spacer(Modifier.size(54.dp))
                }
            }
            Text(s.dist.ifEmpty { "–" }, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                s.p1, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = size * 0.72f)
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LimitSign(s.limit, 30.dp, Modifier.graphicsLayer { scaleX = limitScale; scaleY = limitScale })
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(speedText, color = speedColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("km/h", color = HudGrey, fontSize = 9.sp)
                }
            }
        }
    }
}

/** Rundes Verkehrsschild: rot-weiß mit Zahl, oder „Ende aller Beschränkungen“ bei 0. */
@Composable
fun LimitSign(limit: Int, d: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(d).clip(CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            drawCircle(Color.White, r)
            if (limit == 0) {
                for (i in -2..2) {
                    val o = i * r * 0.17f
                    drawLine(
                        Color.Black,
                        Offset(center.x - r + o, center.y + r + o),
                        Offset(center.x + r + o, center.y - r + o),
                        strokeWidth = r * 0.08f
                    )
                }
            } else {
                drawCircle(HudRed, r * 0.9f, style = Stroke(r * 0.2f))
            }
        }
        if (limit > 0) {
            Text(
                limit.toString(), color = Color.Black, fontWeight = FontWeight.Bold,
                fontSize = (d.value * (if (limit >= 100) 0.32f else 0.40f)).sp
            )
        }
    }
}
