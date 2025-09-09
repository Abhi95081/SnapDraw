package com.example.snapdraw

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.FloatRange
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.snapdraw.ui.theme.SnapDrawTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import androidx.core.graphics.createBitmap

/**
 * SnapDraw — Revised full file
 * - Fixed Pen drawing (local mutable stroke points)
 * - Added color picker and stroke width slider
 * - Improved UI (TopBar, FABs, controls)
 * - Splash overlay on startup
 * - Undo/Redo, Grid toggle, Export
 */

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnapDrawTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SnapDrawApp()
                }
            }
        }
    }
}

/** --- Models & Helpers --- */

enum class Tool { PEN, RULER, SET_SQUARE, PROTRACTOR, PAN }

data class StrokeData(val points: List<Offset>, val color: Color = Color.Black, val widthPx: Float = 6f)

data class RulerState(var center: Offset = Offset.Zero, var rotationDeg: Float = 0f, var lengthPx: Float = 800f)

data class SetSquareState(var center: Offset = Offset.Zero, var rotationDeg: Float = 0f, var is45: Boolean = true)

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

/** --- App UI --- */

@RequiresApi(Build.VERSION_CODES.Q)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapDrawApp() {
    val scope = rememberCoroutineScope()
    var showSplash by remember { mutableStateOf(true) }

    // simple splash overlay
    LaunchedEffect(Unit) {
        delay(1400)
        showSplash = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SnapDrawScreen()

        if (showSplash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF4CAF50), Color(0xFF2E7D32)))),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Create, contentDescription = null, tint = Color.White, modifier = Modifier.size(96.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("SnapDraw", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("Snappy Ruler & Drawing Toolkit", color = Color.White.copy(alpha = 0.9f))
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapDrawScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // canvas transform (screen px)
    var canvasOffset by remember { mutableStateOf(Offset.Zero) }
    var canvasScale by remember { mutableStateOf(1f) }

    // tools
    var currentTool by remember { mutableStateOf(Tool.PEN) }
    var ruler by remember { mutableStateOf(RulerState(center = Offset(400f, 400f))) }
    var setSquare by remember { mutableStateOf(SetSquareState(center = Offset(600f, 600f))) }

    // drawing state (managed locally so pointerInput sees latest)
    val strokes = remember { mutableStateListOf<StrokeData>() }
    val currentStrokePoints = remember { mutableStateListOf<Offset>() }

    var strokeColor by remember { mutableStateOf(Color.Black) }
    var strokeWidth by remember { mutableStateOf(6f) }

    // undo/redo
    val undoStack = remember { ArrayDeque<List<StrokeData>>() }
    val redoStack = remember { ArrayDeque<List<StrokeData>>() }
    fun snapshotPush() {
        undoStack.addFirst(strokes.map { it.copy(points = it.points.toList()) })
        if (undoStack.size > 20) undoStack.removeLast()
        redoStack.clear()
    }
    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.addFirst(strokes.map { it.copy(points = it.points.toList()) })
            strokes.clear(); strokes.addAll(undoStack.removeFirst())
        }
    }
    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.addFirst(strokes.map { it.copy(points = it.points.toList()) })
            strokes.clear(); strokes.addAll(redoStack.removeFirst())
        }
    }

    // protractor
    var protractorVertex by remember { mutableStateOf<Offset?>(null) }
    var protractorRay1 by remember { mutableStateOf<Offset?>(null) }
    var protractorMeasuredDeg by remember { mutableStateOf<Float?>(null) }

    // grid/snapping/hud
    var showGrid by remember { mutableStateOf(true) }
    var gridSpacingPx by remember { mutableStateOf(40f) }
    var snapEnabled by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            // Gradient top bar look
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(Brush.horizontalGradient(listOf(Color(0xFF4CAF50), Color(0xFF2E7D32)))), contentAlignment = Alignment.CenterStart) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
                    Icon(Icons.Default.Create, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("SnapDraw", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                }
            }
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatingActionButton(onClick = { undo(); scope.launch { snackbarHostState.showSnackbar("Undo") } }) { Icon(Icons.Default.ArrowBack, contentDescription = "Undo") }
                FloatingActionButton(onClick = { redo(); scope.launch { snackbarHostState.showSnackbar("Redo") } }) { Icon(Icons.Default.ArrowForward, contentDescription = "Redo") }
                FloatingActionButton(onClick = { scope.launch { saveBitmap(view, context); snackbarHostState.showSnackbar("Saved PNG") } }) { Icon(Icons.Default.ThumbUp, contentDescription = "Save") }
            }
        },
        bottomBar = {
            BottomAppBar {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { currentTool = Tool.PEN; scope.launch { snackbarHostState.showSnackbar("Pen") } }) { Icon(Icons.Default.Create, contentDescription = "Pen") }
                    IconButton(onClick = { currentTool = Tool.RULER; scope.launch { snackbarHostState.showSnackbar("Ruler") } }) { Icon(Icons.Default.List, contentDescription = "Ruler") }
                    IconButton(onClick = { currentTool = Tool.SET_SQUARE; scope.launch { snackbarHostState.showSnackbar("SetSquare") } }) { Icon(Icons.Default.AccountBox, contentDescription = "SetSquare") }
                    IconButton(onClick = { currentTool = Tool.PROTRACTOR; scope.launch { snackbarHostState.showSnackbar("Protractor") } }) { Icon(Icons.Default.DateRange, contentDescription = "Protractor") }
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Grid")
                        Switch(checked = showGrid, onCheckedChange = { showGrid = it })
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            // left controls: color picker & stroke width
            Column(modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color.White.copy(alpha = 0.9f), shape = RoundedCornerShape(10.dp))
                .border(1.dp, Color.LightGray, RoundedCornerShape(10.dp))
                .padding(8.dp)
            ) {
                Text("Color", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val colors = listOf(Color.Black, Color.Red, Color.Blue, Color.Green, Color.Magenta, Color(0xFFFFA000))
                    colors.forEach { c ->
                        Box(modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(if (c == strokeColor) 3.dp else 1.dp, if (c == strokeColor) Color.White else Color.Gray, CircleShape)
                            .clickable { strokeColor = c; scope.launch { snackbarHostState.showSnackbar("Color changed") } }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Width: ${strokeWidth.toInt()}", fontWeight = FontWeight.SemiBold)
                Slider(value = strokeWidth, onValueChange = { strokeWidth = it }, valueRange = 1f..30f, modifier = Modifier.width(140.dp))
                Spacer(Modifier.height(6.dp))
                Button(onClick = { strokes.clear(); snapshotPush(); scope.launch { snackbarHostState.showSnackbar("Cleared") } }) { Text("Clear") }
            }

            // Canvas with gesture handling
            Canvas(modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF6F6F6))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, rotation ->
                        // if drawing tool selected and two fingers used, transform tool; otherwise pan/zoom canvas
                        // detectTransformGestures triggers for multi-touch gestures
                        if (currentTool == Tool.RULER) {
                            ruler.center = ruler.center + pan / canvasScale
                            val deltaDeg = Math.toDegrees(rotation.toDouble()).toFloat()
                            val (snapped, hard) = snapAngle(ruler.rotationDeg + deltaDeg, thresholdDeg = 6f / canvasScale)
                            if (snapEnabled && hard) ruler.rotationDeg = snapped else ruler.rotationDeg += deltaDeg
                        } else if (currentTool == Tool.SET_SQUARE) {
                            setSquare.center = setSquare.center + pan / canvasScale
                            val deltaDeg = Math.toDegrees(rotation.toDouble()).toFloat()
                            val (snapped, hard) = snapAngle(setSquare.rotationDeg + deltaDeg, thresholdDeg = 6f / canvasScale)
                            if (snapEnabled && hard) setSquare.rotationDeg = snapped else setSquare.rotationDeg += deltaDeg
                        } else {
                            canvasOffset += pan
                            canvasScale = (canvasScale * zoom).coerceIn(0.3f, 4f)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val world = screenToWorld(offset, canvasOffset, canvasScale)
                            when (currentTool) {
                                Tool.PEN -> {
                                    currentStrokePoints.clear(); currentStrokePoints.add(world)
                                }
                                Tool.RULER -> {
                                    val proj = projectOntoLine(world, ruler.center, ruler.rotationDeg)
                                    currentStrokePoints.clear(); currentStrokePoints.add(proj)
                                }
                                Tool.SET_SQUARE -> {
                                    val proj = projectOntoLine(world, setSquare.center, setSquare.rotationDeg)
                                    currentStrokePoints.clear(); currentStrokePoints.add(proj)
                                }
                                Tool.PROTRACTOR -> {
                                    if (protractorVertex == null) { protractorVertex = world; scope.launch { snackbarHostState.showSnackbar("Protractor: vertex set") } }
                                    else if (protractorRay1 == null) { protractorRay1 = world; scope.launch { snackbarHostState.showSnackbar("Protractor: ray1 set") } }
                                    else {
                                        val vertex = protractorVertex!!
                                        val ray1 = protractorRay1!!
                                        val ray2 = world
                                        val angle = angleAtVertex(ray1, vertex, ray2)
                                        val (snapped, hard) = snapAngle(angle, thresholdDeg = 1f)
                                        protractorMeasuredDeg = if (snapEnabled && hard) snapped else angle
                                        scope.launch { snackbarHostState.showSnackbar("Angle: ${protractorMeasuredDeg!!.toInt()}°") }
                                        protractorVertex = null; protractorRay1 = null
                                    }
                                }
                                else -> {}
                            }
                        },
                        onDrag = { change, _ ->
                            val world = screenToWorld(change.position, canvasOffset, canvasScale)
                            when (currentTool) {
                                Tool.PEN -> {
                                    currentStrokePoints.add(world)
                                }
                                Tool.RULER -> {
                                    val proj = projectOntoLine(world, ruler.center, ruler.rotationDeg)
                                    val snapped = if (snapEnabled) snapToGridIfClose(proj, gridSpacingPx / canvasScale) else proj
                                    currentStrokePoints.add(snapped)
                                }
                                Tool.SET_SQUARE -> {
                                    val proj = projectOntoLine(world, setSquare.center, setSquare.rotationDeg)
                                    currentStrokePoints.add(proj)
                                }
                                else -> {}
                            }
                        },
                        onDragEnd = {
                            when (currentTool) {
                                Tool.PEN -> {
                                    if (currentStrokePoints.size >= 2) {
                                        snapshotPush()
                                        strokes.add(StrokeData(points = currentStrokePoints.toList(), color = strokeColor, widthPx = strokeWidth))
                                    }
                                    currentStrokePoints.clear()
                                }
                                Tool.RULER -> {
                                    if (currentStrokePoints.size >= 2) {
                                        snapshotPush()
                                        strokes.add(StrokeData(points = currentStrokePoints.toList(), color = Color.DarkGray, widthPx = 4f))
                                    }
                                    currentStrokePoints.clear()
                                }
                                Tool.SET_SQUARE -> {
                                    if (currentStrokePoints.size >= 2) {
                                        snapshotPush()
                                        strokes.add(StrokeData(points = currentStrokePoints.toList(), color = Color.Blue, widthPx = 4f))
                                    }
                                    currentStrokePoints.clear()
                                }
                                else -> {}
                            }
                        }
                    )
                }
            ) {
                // Draw grid (screen space)
                val canvasW = size.width
                val canvasH = size.height
                if (showGrid) {
                    val gs = gridSpacingPx * canvasScale
                    var x = (canvasOffset.x % gs) - gs
                    while (x < canvasW + gs) {
                        drawLine(Color(0xFFE0E0E0), Offset(x, 0f), Offset(x, canvasH), 1f); x += gs
                    }
                    var y = (canvasOffset.y % gs) - gs
                    while (y < canvasH + gs) {
                        drawLine(Color(0xFFE0E0E0), Offset(0f, y), Offset(canvasW, y), 1f); y += gs
                    }
                }

                fun worldToScreen(p: Offset) = Offset(p.x * canvasScale + canvasOffset.x, p.y * canvasScale + canvasOffset.y)

                // existing strokes
                strokes.forEach { s ->
                    if (s.points.isNotEmpty()) {
                        val p = Path().apply {
                            val first = worldToScreen(s.points.first())
                            moveTo(first.x, first.y)
                            s.points.drop(1).forEach { lineTo(worldToScreen(it).x, worldToScreen(it).y) }
                        }
                        drawPath(p, color = s.color, style = Stroke(width = s.widthPx))
                    }
                }

                // preview
                if (currentStrokePoints.isNotEmpty()) {
                    val p = Path().apply {
                        val first = worldToScreen(currentStrokePoints.first())
                        moveTo(first.x, first.y)
                        currentStrokePoints.drop(1).forEach { lineTo(worldToScreen(it).x, worldToScreen(it).y) }
                    }
                    drawPath(p, color = when (currentTool) { Tool.PEN -> strokeColor; Tool.RULER -> Color.DarkGray; Tool.SET_SQUARE -> Color.Blue; else -> Color.Magenta }, style = Stroke(width = strokeWidth))
                }

                // ruler visual
                val half = ruler.lengthPx / 2f
                val rx = cos(Math.toRadians(ruler.rotationDeg.toDouble())).toFloat()
                val ry = sin(Math.toRadians(ruler.rotationDeg.toDouble())).toFloat()
                val rs = worldToScreen(Offset(ruler.center.x - rx * half, ruler.center.y - ry * half))
                val re = worldToScreen(Offset(ruler.center.x + rx * half, ruler.center.y + ry * half))
                drawLine(color = if (currentTool == Tool.RULER) Color.DarkGray else Color.LightGray, start = rs, end = re, strokeWidth = 6f)
                drawCircle(Color.Gray, center = worldToScreen(ruler.center), radius = 8f)

                // set-square indicator
                val ssCenter = worldToScreen(setSquare.center)
                drawRect(Color(0x220000FF), topLeft = ssCenter - Offset(30f, 30f), size = androidx.compose.ui.geometry.Size(60f, 60f))

                // protractor points + measure text
                protractorVertex?.let { v -> drawCircle(Color.Green, center = worldToScreen(v), radius = 8f) }
                protractorRay1?.let { r -> drawCircle(Color.Green, center = worldToScreen(r), radius = 8f) }
                protractorMeasuredDeg?.let { deg ->
                    drawContext.canvas.nativeCanvas.drawText("${deg.toInt()}°", 20f, 40f, android.graphics.Paint().apply { textSize = 40f; color = android.graphics.Color.BLACK })
                }
            }

            // bottom-right mini panel showing current tool & measurements
            Column(modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
                Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.widthIn(min = 140.dp), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Tool: ${currentTool.name}")
                        Spacer(Modifier.height(6.dp))
                        Text("Strokes: ${strokes.size}")
                        Spacer(Modifier.height(6.dp))
                        protractorMeasuredDeg?.let { Text("Angle: ${"%.1f".format(it)}°") }
                    }
                }
            }
        }
    }
}
