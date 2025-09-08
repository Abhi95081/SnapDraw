package com.example.snapdraw

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.snapdraw.ui.theme.SnapDrawTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnapDrawTheme {
                SnapDrawApp()
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SnapDrawApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var drawingPath by remember { mutableStateOf(Path()) }
    var paths by remember { mutableStateOf(listOf<Path>()) }
    var currentColor by remember { mutableStateOf(Color.Red) }

    // Pick image from gallery
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, it)
        }
    }

    // Take photo from camera
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { photo: Bitmap? ->
        photo?.let {
            bitmap = it
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📸 SnapDraw", style = MaterialTheme.typography.titleLarge) }
            )
        },
        bottomBar = {
            BottomAppBar(
                modifier = Modifier.height(70.dp),
                actions = {
                    Button(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.padding(8.dp)) {
                        Text("Gallery")
                    }
                    Button(onClick = { cameraLauncher.launch(null) }, modifier = Modifier.padding(8.dp)) {
                        Text("Camera")
                    }
                    Button(onClick = { paths = emptyList() }, modifier = Modifier.padding(8.dp)) {
                        Text("Clear")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF0F0F0)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap == null) {
                Text(
                    text = "Pick an Image from Gallery or Camera",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .border(2.dp, Color.Gray, RoundedCornerShape(12.dp))
                ) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Drawing overlay
                    Canvas(modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    drawingPath.moveTo(offset.x, offset.y)
                                },
                                onDrag = { change, dragAmount ->
                                    drawingPath.lineTo(change.position.x, change.position.y)
                                    paths = paths + drawingPath
                                },
                                onDragEnd = {
                                    paths = paths + drawingPath
                                    drawingPath = Path()
                                }
                            )
                        }
                    ) {
                        paths.forEach { path ->
                            drawPath(path, color = currentColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8f))
                        }
                    }
                }
            }
        }
    }
}
