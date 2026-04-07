package com.example.steeldefectdetector.model

import java.util.Date

/**
 * 检测历史记录
 */
data class DetectionHistory(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val modelName: String,
    val imagePath: String? = null,
    val imageWidth: Int,
    val imageHeight: Int,
    val defectCount: Int,
    val inferenceTime: Long,
    val comparisonData: String,
    // 必须加上这个字段，用于传递解析好的缺陷坐标交给 UI 画框
    val results: List<DetectionResult> = emptyList()
) {
    /**
     * 获取格式化时间
     */
    fun getFormattedTime(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(timestamp))
    }

    /**
     * 获取简短标题
     */
    fun getShortTitle(): String {
        return "${getFormattedTime()} - ${modelName} (${defectCount}个缺陷)"
    }

    /**
     * 获取详细信息
     */
    fun getDetails(): String {
        return """
            检测时间: ${getFormattedTime()}
            使用模型: $modelName
            图片尺寸: ${imageWidth} × ${imageHeight} 像素
            缺陷数量: $defectCount 个
            推理耗时: ${inferenceTime}ms
        """.trimIndent()
    }
}