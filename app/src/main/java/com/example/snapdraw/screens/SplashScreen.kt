package com.example.snapdraw.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.snapdraw.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    // ⏳ Auto navigate after 3 sec
    LaunchedEffect(Unit) {
        delay(4000) // 4 seconds
        onTimeout()
    }

    // Scale animation for logo
    val transition = rememberInfiniteTransition(label = "splashLogo")
    val scale by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scaleAnim"
    )

    // Fade-in animation for texts
    val fadeTransition = rememberInfiniteTransition(label = "fadeText")
    val alpha by fadeTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alphaAnim"
    )

    // Animated background gradient
    val colors = listOf(
        Color(0xFF00C6FF),
        Color(0xFF0072FF),
        Color(0xFF3A0CA3),
    )
    val bgTransition = rememberInfiniteTransition(label = "bgAnim")
    val offset by bgTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offsetAnim"
    )

    val animatedGradient = Brush.linearGradient(
        colors = colors,
        start = androidx.compose.ui.geometry.Offset(0f, offset),
        end = androidx.compose.ui.geometry.Offset(offset, 0f)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(animatedGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Glowing circle behind logo
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Your app logo
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "App Logo",
                        modifier = Modifier
                            .size(90.dp)
                            .scale(scale),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App name
            Text(
                text = "SnapDraw",
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.alpha(alpha)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Tagline
            Text(
                text = "   Snappy Ruler • Smart Drawing ",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.alpha(alpha)
            )
        }
    }
}
