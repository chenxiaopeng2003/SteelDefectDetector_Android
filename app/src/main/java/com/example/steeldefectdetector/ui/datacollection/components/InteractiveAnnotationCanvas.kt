package com.example.steeldefectdetector.ui.datacollection.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.example.steeldefectdetector.model.annotation.AnnotationBox
import com.example.steeldefectdetector.model.annotation.AnnotationMode

@Composable
fun InteractiveAnnotationCanvas(
    bitmap: ImageBitmap,
    mode: AnnotationMode,
    annotations: List<AnnotationBox>,
    currentLabelId: Int,
    currentLabelName: String,
    onAddAnnotation: (AnnotationBox) -> Unit,
    modifier: Modifier = Modifier
) {
    // 视图变换状态
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    // 绘制状态 (记录的是基于原始图片像素的绝对坐标)
    var drawingStart by remember { mutableStateOf<Offset?>(null) }
    var drawingCurrent by remember { mutableStateOf<Offset?>(null) }

    val textMeasurer = rememberTextMeasurer()

    // 防御 Compose 闭包陷阱：强制读取最新的标签 ID 与名称
    val currentLabelIdState by rememberUpdatedState(currentLabelId)
    val currentLabelNameState by rememberUpdatedState(currentLabelName)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            // 模式 1：查看、缩放、平移
            .pointerInput(mode) {
                if (mode == AnnotationMode.VIEW_PAN_ZOOM) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        scale = (scale * zoomChange).coerceIn(0.5f, 5f)
                        pan += panChange
                    }
                }
            }
            // 模式 2：绘制边界框
            .pointerInput(mode) {
                if (mode == AnnotationMode.DRAW_BBOX) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            // 必须先计算图片在 Canvas 上的实际绘制区域与缩放比例
                            val canvasSize = size
                            val imageRatio = bitmap.width.toFloat() / bitmap.height
                            val canvasRatio = canvasSize.width / canvasSize.height
                            
                            val drawWidth: Float
                            val drawHeight: Float
                            if (canvasRatio > imageRatio) {
                                drawHeight = canvasSize.height.toFloat()
                                drawWidth = drawHeight * imageRatio
                            } else {
                                drawWidth = canvasSize.width.toFloat()
                                drawHeight = drawWidth / imageRatio
                            }
                            
                            val offsetX = (canvasSize.width - drawWidth) / 2f
                            val offsetY = (canvasSize.height - drawHeight) / 2f

                            // 反向映射公式：屏幕触摸点 -> 原图物理像素点
                            val touchToImageCoords = { touchOffset: Offset ->
                                val scaledX = (touchOffset.x - pan.x - offsetX) / scale
                                val scaledY = (touchOffset.y - pan.y - offsetY) / scale
                                // 还原为图片真实像素比例
                                val realX = scaledX * (bitmap.width / drawWidth)
                                val realY = scaledY * (bitmap.height / drawHeight)
                                Offset(realX, realY)
                            }

                            val startImageCoord = touchToImageCoords(offset)
                            
                            // 防御越界：确保起始点在原图内部
                            if (startImageCoord.x in 0f..bitmap.width.toFloat() && 
                                startImageCoord.y in 0f..bitmap.height.toFloat()) {
                                drawingStart = startImageCoord
                                drawingCurrent = startImageCoord
                            } else {
                                drawingStart = null
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (drawingStart == null) return@detectDragGestures
                            
                            // 同理，计算当前拖拽点的反向映射
                            val canvasSize = size
                            val imageRatio = bitmap.width.toFloat() / bitmap.height
                            val canvasRatio = canvasSize.width / canvasSize.height
                            
                            val drawWidth = if (canvasRatio > imageRatio) canvasSize.height * imageRatio else canvasSize.width.toFloat()
                            val drawHeight = if (canvasRatio > imageRatio) canvasSize.height.toFloat() else canvasSize.width / imageRatio
                            
                            val offsetX = (canvasSize.width - drawWidth) / 2f
                            val offsetY = (canvasSize.height - drawHeight) / 2f

                            val scaledX = (change.position.x - pan.x - offsetX) / scale
                            val scaledY = (change.position.y - pan.y - offsetY) / scale
                            
                            val realX = (scaledX * (bitmap.width / drawWidth)).coerceIn(0f, bitmap.width.toFloat())
                            val realY = (scaledY * (bitmap.height / drawHeight)).coerceIn(0f, bitmap.height.toFloat())
                            
                            drawingCurrent = Offset(realX, realY)
                        },
                        onDragEnd = {
                            if (drawingStart != null && drawingCurrent != null) {
                                val left = minOf(drawingStart!!.x, drawingCurrent!!.x)
                                val top = minOf(drawingStart!!.y, drawingCurrent!!.y)
                                val right = maxOf(drawingStart!!.x, drawingCurrent!!.x)
                                val bottom = maxOf(drawingStart!!.y, drawingCurrent!!.y)
                                
                                // 防御极小面积的误触绘制 (过滤长宽小于 5 像素的框)
                                if ((right - left) > 5f && (bottom - top) > 5f) {
                                    val newBox = AnnotationBox(
                                        labelId = currentLabelIdState,
                                        labelName = currentLabelNameState,
                                        left = left,
                                        top = top,
                                        right = right,
                                        bottom = bottom
                                    )
                                    onAddAnnotation(newBox)
                                }
                            }
                            // 状态重置
                            drawingStart = null
                            drawingCurrent = null
                        },
                        onDragCancel = {
                            drawingStart = null
                            drawingCurrent = null
                        }
                    )
                }
            }
    ) {
        val canvasSize = size
        val imageRatio = bitmap.width.toFloat() / bitmap.height
        val canvasRatio = canvasSize.width / canvasSize.height

        val drawWidth: Float
        val drawHeight: Float
        if (canvasRatio > imageRatio) {
            drawHeight = canvasSize.height
            drawWidth = drawHeight * imageRatio
        } else {
            drawWidth = canvasSize.width
            drawHeight = drawWidth / imageRatio
        }

        val offsetX = (canvasSize.width - drawWidth) / 2f
        val offsetY = (canvasSize.height - drawHeight) / 2f

        // 全局变换矩阵：应用平移与缩放
        withTransform({
            translate(left = pan.x, top = pan.y)
            scale(scale, scale, pivot = Offset(offsetX, offsetY))
        }) {
            // 1. 绘制底图 (Fit Center 模式)
            drawImage(
                image = bitmap,
                dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                dstSize = IntSize(drawWidth.toInt(), drawHeight.toInt())
            )

            // 坐标正向映射辅助函数：物理像素 -> 当前画布绘制坐标
            fun imageToDrawRect(left: Float, top: Float, right: Float, bottom: Float): Rect {
                val scaleX = drawWidth / bitmap.width
                val scaleY = drawHeight / bitmap.height
                return Rect(
                    left = offsetX + left * scaleX,
                    top = offsetY + top * scaleY,
                    right = offsetX + right * scaleX,
                    bottom = offsetY + bottom * scaleY
                )
            }

            // 2. 绘制已保存的标注框
            annotations.forEach { box ->
                val rect = imageToDrawRect(box.left, box.top, box.right, box.bottom)
                drawRect(
                    color = Color.Red,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = 3f / scale) // 保持线宽不随缩放变粗
                )
                // 绘制标签文本
                drawText(
                    textMeasurer = textMeasurer,
                    text = box.labelName,
                    topLeft = Offset(rect.left, rect.top - 40f / scale),
                    style = TextStyle(color = Color.Yellow, fontSize = (14 / scale).sp)
                )
            }

            // 3. 绘制正在拖拽的临时框
            if (drawingStart != null && drawingCurrent != null) {
                val left = minOf(drawingStart!!.x, drawingCurrent!!.x)
                val top = minOf(drawingStart!!.y, drawingCurrent!!.y)
                val right = maxOf(drawingStart!!.x, drawingCurrent!!.x)
                val bottom = maxOf(drawingStart!!.y, drawingCurrent!!.y)
                
                val rect = imageToDrawRect(left, top, right, bottom)
                drawRect(
                    color = Color.Yellow,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = 3f / scale)
                )
            }
        }
    }
}