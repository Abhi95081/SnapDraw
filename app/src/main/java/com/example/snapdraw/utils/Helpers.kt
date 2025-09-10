package com.example.snapdraw.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.view.View
import androidx.annotation.FloatRange
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.core.graphics.createBitmap
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

val COMMON_ANGLES = listOf(0f, 30f, 45f, 60f, 90f, 120f, 135f, 150f, 180f)

fun normalizeSignedDeg(angle: Float): Float {
    var a = (angle + 180f) % 360f - 180f
    if (a < -180f) a += 360f
    return a
}

fun snapAngle(rawDeg: Float, thresholdDeg: Float): Pair<Float, Boolean> {
    val norm = normalizeSignedDeg(rawDeg)
    var best = norm
    var bestDelta = Float.MAX_VALUE
    for (a in COMMON_ANGLES) {
        val d = abs(((norm - a + 540f) % 360f) - 180f)
        if (d < bestDelta) { bestDelta = d; best = a }
    }
    return Pair(best, bestDelta <= thresholdDeg)
}

fun projectOntoLine(point: Offset, center: Offset, angleDeg: Float): Offset {
    val theta = Math.toRadians(angleDeg.toDouble())
    val dx = cos(theta).toFloat()
    val dy = sin(theta).toFloat()
    val apx = point.x - center.x
    val apy = point.y - center.y
    val t = apx * dx + apy * dy
    return Offset(center.x + dx * t, center.y + dy * t)
}

fun distance(a: Offset, b: Offset) = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

fun pxToCm(px: Float, xdpi: Float, calibrationFactor: Float = 1f): Float = (px / xdpi) * 2.54f * calibrationFactor

fun angleAtVertex(p1: Offset, vertex: Offset, p2: Offset): Float {
    val v1x = p1.x - vertex.x
    val v1y = p1.y - vertex.y
    val v2x = p2.x - vertex.x
    val v2y = p2.y - vertex.y
    val dot = v1x * v2x + v1y * v2y
    val mag1 = sqrt(v1x * v1x + v1y * v1y)
    val mag2 = sqrt(v2x * v2x + v2y * v2y)
    if (mag1 <= 1e-6f || mag2 <= 1e-6f) return 0f
    val cosTheta = (dot / (mag1 * mag2)).coerceIn(-1f, 1f)
    return Math.toDegrees(acos(cosTheta).toDouble()).toFloat()
}

fun screenToWorld(screen: Offset, canvasOffset: Offset, canvasScale: Float): Offset =
    Offset((screen.x - canvasOffset.x) / canvasScale, (screen.y - canvasOffset.y) / canvasScale)

fun snapToGridIfClose(world: Offset, gridSpacingWorld: Float, @FloatRange(from = 0.0) thresholdWorld: Float = 8f): Offset {
    val gx = (world.x / gridSpacingWorld).roundToInt() * gridSpacingWorld
    val gy = (world.y / gridSpacingWorld).roundToInt() * gridSpacingWorld
    val snapped = Offset(gx, gy)
    return if (distance(world, snapped) <= thresholdWorld) snapped else world
}

@RequiresApi(Build.VERSION_CODES.Q)
suspend fun saveBitmap(view: View, context: Context) {
    try {
        val w = view.width.takeIf { it > 0 } ?: 800
        val h = view.height.takeIf { it > 0 } ?: 1200
        val bmp: Bitmap = createBitmap(w, h)
        val canvas = android.graphics.Canvas(bmp)
        view.draw(canvas)

        val filename = "SnapDraw_${System.currentTimeMillis()}.png"
        val mime = "image/png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return
        resolver.openOutputStream(uri)?.use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
