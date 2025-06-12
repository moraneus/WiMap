package com.ner.wimap.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

// Custom shape for the BottomAppBar notch with modern curves
class ModernNotchShape(private val notchRadiusPx: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val centerBarX = size.width / 2f
        val notchWidth = notchRadiusPx * 1.8f

        path.moveTo(0f, 0f)
        path.lineTo(centerBarX - notchWidth, 0f)

        // Create a more modern, slightly flattened curve
        path.cubicTo(
            centerBarX - notchWidth * 0.7f, 0f,
            centerBarX - notchWidth * 0.5f, notchRadiusPx * 0.8f,
            centerBarX, notchRadiusPx * 0.8f
        )
        path.cubicTo(
            centerBarX + notchWidth * 0.5f, notchRadiusPx * 0.8f,
            centerBarX + notchWidth * 0.7f, 0f,
            centerBarX + notchWidth, 0f
        )

        path.lineTo(size.width, 0f)
        path.lineTo(size.width, size.height)
        path.lineTo(0f, size.height)
        path.close()
        return Outline.Generic(path)
    }
}