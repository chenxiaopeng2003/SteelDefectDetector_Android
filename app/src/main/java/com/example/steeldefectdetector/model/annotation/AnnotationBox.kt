package com.example.steeldefectdetector.model.annotation

/**
 * 存储基于[原始图片绝对像素系]的边界框坐标
 */
data class AnnotationBox(
    val labelId: Int,
    val labelName: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    /**
     * 将绝对像素坐标转换为 YOLO 格式 (归一化中心点与宽高)
     * @param imageWidth 原始图片真实像素宽度
     * @param imageHeight 原始图片真实像素高度
     * @return 符合 YOLO 标准的单行文本
     */
    fun toYoloFormat(imageWidth: Int, imageHeight: Int): String {
        // 约束边界，防止画出图片外导致坐标异常
        val safeLeft = left.coerceIn(0f, imageWidth.toFloat())
        val safeRight = right.coerceIn(0f, imageWidth.toFloat())
        val safeTop = top.coerceIn(0f, imageHeight.toFloat())
        val safeBottom = bottom.coerceIn(0f, imageHeight.toFloat())

        val boxWidth = safeRight - safeLeft
        val boxHeight = safeBottom - safeTop
        val xCenter = safeLeft + boxWidth / 2f
        val yCenter = safeTop + boxHeight / 2f

        // 归一化处理 (保留 6 位小数以满足一般 CV 训练精度)
        val normXCenter = String.format("%.6f", xCenter / imageWidth)
        val normYCenter = String.format("%.6f", yCenter / imageHeight)
        val normWidth = String.format("%.6f", boxWidth / imageWidth)
        val normHeight = String.format("%.6f", boxHeight / imageHeight)

        return "$labelId $normXCenter $normYCenter $normWidth $normHeight"
    }
}