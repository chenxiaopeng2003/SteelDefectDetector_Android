package com.example.steeldefectdetector.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import com.example.steeldefectdetector.model.DetectionResult

@Composable
fun BoundingBoxOverlay(
    bitmap: Bitmap,
    detections: List<DetectionResult>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // 绘制原始图片
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Detected Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // 绘制边界框
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            if (canvasWidth <= 0f || canvasHeight <= 0f) return@Canvas

            val bitmapWidth = bitmap.width.toFloat()
            val bitmapHeight = bitmap.height.toFloat()

            // 【核心修复】：计算 ContentScale.Fit 导致的真实缩放比例和居中偏移量
            val scale = minOf(canvasWidth / bitmapWidth, canvasHeight / bitmapHeight)
            val dx = (canvasWidth - bitmapWidth * scale) / 2f
            val dy = (canvasHeight - bitmapHeight * scale) / 2f

            detections.forEach { detection ->
                try {
                    // 将底层传来的“原图像素绝对坐标”，精准映射到当前屏幕 Canvas 的坐标
                    val left = detection.x1 * scale + dx
                    val top = detection.y1 * scale + dy
                    val right = detection.x2 * scale + dx
                    val bottom = detection.y2 * scale + dy

                    // 坐标有效性检查
                    if (left >= right || top >= bottom) return@forEach

                    val width = right - left
                    val height = bottom - top

                    // 根据置信度选择颜色
                    val color = when {
                        detection.confidence >= 0.8 -> Color.Red
                        detection.confidence >= 0.6 -> Color.Yellow
                        else -> Color.Green
                    }

                    // 绘制边界框
                    drawRect(
                        color = color,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = Stroke(width = 3f)
                    )

                    // 绘制标签背景
                    val label = "${detection.getChineseName()} ${(detection.confidence * 100).toInt()}%"
                    val textPaint = android.graphics.Paint().apply {
                        this.color = android.graphics.Color.WHITE
                        this.textSize = 28f
                        this.isAntiAlias = true
                    }

                    val textWidth = textPaint.measureText(label)
                    val textHeight = textPaint.descent() - textPaint.ascent()

                    // 标签背景
                    drawRect(
                        color = color.copy(alpha = 0.8f),
                        topLeft = Offset(left, top - textHeight),
                        size = Size(textWidth + 20f, textHeight)
                    )

                    // 绘制标签文本
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(
                            label,
                            left + 10f,
                            top - textPaint.descent() - 5f,
                            textPaint
                        )
                    }

                    // 绘制严重程度指示器
                    val severityColor = when (detection.getSeverity()) {
                        "严重" -> Color.Red
                        "中等" -> Color.Yellow
                        "轻微" -> Color.Green
                        else -> Color.Gray
                    }

                    // 在右上角绘制严重程度圆点
                    drawCircle(
                        color = severityColor,
                        radius = 8f,
                        center = Offset(right - 10f, top + 10f)
                    )
                } catch (e: Exception) {
                    // 忽略绘制错误，继续绘制其他边界框
                    e.printStackTrace()
                }
            }
        }
    }
}