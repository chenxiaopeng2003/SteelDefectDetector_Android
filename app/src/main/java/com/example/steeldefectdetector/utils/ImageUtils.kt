package com.example.steeldefectdetector.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import java.nio.FloatBuffer

/**
 * 图像处理工具类
 */
object ImageUtils {

    private const val TAG = "ImageUtils"

    /**
     * 将任意尺寸的图像调整为640x640
     * 保持宽高比，添加黑色填充
     */
    fun resizeTo640x640(bitmap: Bitmap): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        val targetSize = 640

        Log.d(TAG, "调整图像尺寸: ${originalWidth}x${originalHeight} → ${targetSize}x${targetSize}")

        // 计算缩放比例
        val scale = targetSize.toFloat() / originalWidth.coerceAtLeast(originalHeight)
        val scaledWidth = (originalWidth * scale).toInt()
        val scaledHeight = (originalHeight * scale).toInt()

        // 创建缩放后的图像
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)

        // 创建目标图像（640x640）
        val targetBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(targetBitmap)

        // 填充黑色背景
        canvas.drawColor(Color.BLACK)

        // 计算居中位置
        val left = (targetSize - scaledWidth) / 2
        val top = (targetSize - scaledHeight) / 2

        // 绘制缩放后的图像
        canvas.drawBitmap(scaledBitmap, left.toFloat(), top.toFloat(), null)

        // 回收临时bitmap
        scaledBitmap.recycle()

        Log.d(TAG, "图像调整完成: ${targetBitmap.width}x${targetBitmap.height}")
        return targetBitmap
    }

    /**
     * 在图像上绘制检测框和标签
     */
    fun drawDetectionResults(
        bitmap: Bitmap,
        results: List<DefectResult>,
        originalWidth: Int,
        originalHeight: Int
    ): Bitmap {
        if (results.isEmpty()) {
            Log.d(TAG, "无检测结果，跳过绘制")
            return bitmap
        }

        Log.d(TAG, "绘制检测结果: ${results.size} 个缺陷")

        // 创建可修改的bitmap副本
        val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        // 创建画笔
        val boxPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            textSize = 24f
            isAntiAlias = true
        }

        val textBgPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // 计算缩放比例（从640x640回到原始尺寸）
        val scaleX = originalWidth.toFloat() / 640
        val scaleY = originalHeight.toFloat() / 640

        results.forEachIndexed { index, result ->
            // 将坐标从640x640空间转换回原始图像空间
            val x1 = result.x1 * scaleX
            val y1 = result.y1 * scaleY
            val x2 = result.x2 * scaleX
            val y2 = result.y2 * scaleY

            // 绘制检测框
            val rect = RectF(x1, y1, x2, y2)
            canvas.drawRect(rect, boxPaint)

            // 准备标签文本
            val className = when (result.className) {
                "chongkong" -> "冲孔"
                "hanfeng" -> "焊缝"
                "yueyawan" -> "月牙弯"
                "shuiban" -> "水斑"
                "youban" -> "油斑"
                "siban" -> "丝斑"
                "yiwu" -> "异物"
                "yahen" -> "压痕"
                "zhehen" -> "折痕"
                "yaozhe" -> "腰折"
                else -> result.className
            }

            val confidencePercent = (result.confidence * 100).toInt()
            val label = "$className $confidencePercent%"

            // 计算文本尺寸
            val textBounds = Rect()
            textPaint.getTextBounds(label, 0, label.length, textBounds)
            val textWidth = textBounds.width()
            val textHeight = textBounds.height()

            // 绘制文本背景
            val textBgRect = RectF(
                x1,
                y1 - textHeight - 10,
                x1 + textWidth + 20,
                y1
            )
            canvas.drawRect(textBgRect, textBgPaint)

            // 绘制文本
            canvas.drawText(label, x1 + 10, y1 - 10, textPaint)

            Log.d(TAG, "绘制缺陷[$index]: $label 位置: ($x1, $y1) - ($x2, $y2)")
        }

        return resultBitmap
    }

    /**
     * 计算图像在ImageView中的显示尺寸
     */
    fun calculateDisplaySize(
        imageWidth: Int,
        imageHeight: Int,
        maxWidth: Int,
        maxHeight: Int
    ): Pair<Int, Int> {
        val widthRatio = maxWidth.toFloat() / imageWidth
        val heightRatio = maxHeight.toFloat() / imageHeight
        val scale = widthRatio.coerceAtMost(heightRatio)

        val displayWidth = (imageWidth * scale).toInt()
        val displayHeight = (imageHeight * scale).toInt()

        return Pair(displayWidth, displayHeight)
    }

    /**
     * 将调整好尺寸的 Bitmap (640x640) 转换为 YOLOv8 需要的 FloatBuffer
     * 1. 提取 RGB 通道
     * 2. 归一化：将 0-255 的像素值除以 255.0f
     * 3. 格式转换：Android 默认是 HWC (交替排列)，模型需要 CHW (先存所有R，再存所有G，最后存所有B)
     */
    fun bitmapToFloatBuffer(bitmap: Bitmap): FloatBuffer {
        val width = bitmap.width
        val height = bitmap.height
        // 申请内存：3个通道(RGB) * 宽 * 高
        val floatBuffer = FloatBuffer.allocate(3 * width * height)
        floatBuffer.rewind()

        // 提取所有像素点
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // 图像像素总数 (如果是 640x640，则为 409600)
        val area = width * height

        // 遍历所有像素，按 CHW 格式写入 Buffer
        for (i in 0 until area) {
            val pixel = pixels[i]

            // 使用位运算提取 RGB 通道，并立刻归一化到 [0.0, 1.0]
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            // 写入 R 通道数据 (从索引 0 开始)
            floatBuffer.put(i, r)
            // 写入 G 通道数据 (从索引 area 开始)
            floatBuffer.put(i + area, g)
            // 写入 B 通道数据 (从索引 area * 2 开始)
            floatBuffer.put(i + area * 2, b)
        }

        floatBuffer.rewind()
        return floatBuffer
    }
}

/**
 * 缺陷检测结果
 */
data class DefectResult(
    val className: String,
    val confidence: Float,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float
)