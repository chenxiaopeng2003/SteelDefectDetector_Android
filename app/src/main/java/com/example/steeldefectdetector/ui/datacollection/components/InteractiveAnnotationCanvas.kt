package com.example.steeldefectdetector.ui.datacollection.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
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

 // 【修复 Bug 3】：避免上层发生 Recomposition 时传入新的 ImageBitmap 实例导致误触碰重置。
 // 改用图像的宽高作为稳定的 Key，确保只有真正切换不同分辨率图片时才重置状态。
 LaunchedEffect(bitmap.width, bitmap.height) {
 scale = 1f
 pan = Offset.Zero
 }

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
 // 【防御设计】：将手势合并到单个 pointerInput 中，避免模式切换时生命周期冲突
 .pointerInput(mode, bitmap.width, bitmap.height) {
 if (mode == AnnotationMode.VIEW_PAN_ZOOM) {
 detectTransformGestures { centroid, panChange, zoomChange, _ ->
 val oldScale = scale
 // 限制缩放级别为 1 倍到 10 倍，防止反转或过度缩小
 scale = (scale * zoomChange).coerceIn(1f, 10f)
 val fractionalScale = scale / oldScale

 // 获取布局信息以计算精确轴心 (Pivot)
 val canvasSize = size
 val imageRatio = bitmap.width.toFloat() / bitmap.height
 val canvasRatio = canvasSize.width.toFloat() / canvasSize.height.toFloat()
 
 val drawWidth = if (canvasRatio > imageRatio) canvasSize.height.toFloat() * imageRatio else canvasSize.width.toFloat()
 val drawHeight = if (canvasRatio > imageRatio) canvasSize.height.toFloat() else canvasSize.width.toFloat() / imageRatio
 
 val offsetX = (canvasSize.width - drawWidth) / 2f
 val offsetY = (canvasSize.height - drawHeight) / 2f
 val pivot = Offset(offsetX, offsetY)

 // 【修复 Bug 2】：实施形心补偿算法
 // 公式: 新平移 = 原平移 + 基础平移改变量 + (手势形心 - 原平移 - 绘图轴心) * (1 - 缩放系数差)
 pan = pan + panChange + (centroid - pan - pivot) * (1f - fractionalScale)
 }
 } else if (mode == AnnotationMode.DRAW_BBOX) {
 detectDragGestures(
 onDragStart = { offset ->
 val canvasSize = size
 val imageRatio = bitmap.width.toFloat() / bitmap.height
 val canvasRatio = canvasSize.width.toFloat() / canvasSize.height.toFloat()
 
 val drawWidth = if (canvasRatio > imageRatio) canvasSize.height.toFloat() * imageRatio else canvasSize.width.toFloat()
 val drawHeight = if (canvasRatio > imageRatio) canvasSize.height.toFloat() else canvasSize.width.toFloat() / imageRatio
 
 val offsetX = (canvasSize.width - drawWidth) / 2f
 val offsetY = (canvasSize.height - drawHeight) / 2f

 val touchToImageCoords = { touchOffset: Offset ->
 val scaledX = (touchOffset.x - pan.x - offsetX) / scale
 val scaledY = (touchOffset.y - pan.y - offsetY) / scale
 val realX = scaledX * (bitmap.width / drawWidth)
 val realY = scaledY * (bitmap.height / drawHeight)
 Offset(realX, realY)
 }

 val startImageCoord = touchToImageCoords(offset)
 
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
 
 val canvasSize = size
 val imageRatio = bitmap.width.toFloat() / bitmap.height
 val canvasRatio = canvasSize.width.toFloat() / canvasSize.height.toFloat()
 
 val drawWidth = if (canvasRatio > imageRatio) canvasSize.height.toFloat() * imageRatio else canvasSize.width.toFloat()
 val drawHeight = if (canvasRatio > imageRatio) canvasSize.height.toFloat() else canvasSize.width.toFloat() / imageRatio
 
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

 withTransform({
 translate(left = pan.x, top = pan.y)
 scale(scale, scale, pivot = Offset(offsetX, offsetY))
 }) {
 drawImage(
 image = bitmap,
 dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
 dstSize = IntSize(drawWidth.toInt(), drawHeight.toInt())
 )

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

 annotations.forEach { box ->
 val rect = imageToDrawRect(box.left, box.top, box.right, box.bottom)
 drawRect(
 color = Color.Red,
 topLeft = rect.topLeft,
 size = rect.size,
 style = Stroke(width = 3f / scale)
 )
 drawText(
 textMeasurer = textMeasurer,
 text = box.labelName,
 topLeft = Offset(rect.left, rect.top - 40f / scale),
 style = TextStyle(color = Color.Yellow, fontSize = (14 / scale).sp)
 )
 }

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