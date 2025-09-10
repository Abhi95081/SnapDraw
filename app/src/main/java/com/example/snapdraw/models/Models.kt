package com.example.snapdraw.models

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

enum class Tool {
    PEN,
    RULER,
    SET_SQUARE,
    PROTRACTOR,
    PAN
}

data class StrokeData(val points: List<Offset>, val color: Color = Color.Black, val widthPx: Float = 6f)

data class RulerState(var center: Offset = Offset.Zero, var rotationDeg: Float = 0f, var lengthPx: Float = 800f)

data class SetSquareState(var center: Offset = Offset.Zero, var rotationDeg: Float = 0f, var is45: Boolean = true)
