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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.snapdraw.ui.theme.SnapDrawTheme
import kotlinx.coroutines.delay
import kotlin.math.*
import androidx.core.graphics.createBitmap
import com.example.snapdraw.screens.SnapDrawApp
import com.example.snapdraw.screens.SnapDrawScreen
import com.example.snapdraw.screens.SplashScreen

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
@RequiresApi(Build.VERSION_CODES.Q)
@Preview(showBackground = true)
        @Composable
    fun GreetingPreview() {
        SnapDrawTheme {
            SnapDrawApp()
        }
    }

