package com.example.steeldefectdetector.model

/**
 * 检测结果数据类
 * 用于存储单个缺陷的检测结果
 */
data class DetectionResult(
    val className: String,          // 缺陷类别名称
    val confidence: Float,          // 置信度 (0.0-1.0)
    val x1: Float,                  // 边界框左上角x坐标
    val y1: Float,                  // 边界框左上角y坐标
    val x2: Float,                  // 边界框右下角x坐标
    val y2: Float,                  // 边界框右下角y坐标
    val description: String = ""    // 缺陷描述
) {
    /**
     * 获取中文缺陷名称
     */
    fun getChineseName(): String {
        return when (className) {
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
            else -> className
        }
    }
    
    /**
     * 获取缺陷严重程度
     */
    fun getSeverity(): String {
        return when {
            confidence >= 0.9 -> "严重"
            confidence >= 0.7 -> "中等"
            else -> "轻微"
        }
    }
    
    /**
     * 获取建议处理措施
     */
    fun getRecommendation(): String {
        return when (className) {
            "chongkong" -> "建议进行补焊处理"
            "hanfeng" -> "检查焊接工艺参数"
            "yueyawan" -> "调整轧制工艺"
            "shuiban" -> "进行表面清洁和防锈处理"
            "youban" -> "使用专用清洁剂处理"
            "siban" -> "检查原材料质量"
            "yiwu" -> "清除异物并检查来源"
            "yahen" -> "调整轧制压力"
            "zhehen" -> "检查运输和存储条件"
            "yaozhe" -> "调整矫直工艺参数"
            else -> "建议进一步检查"
        }
    }
    
    /**
     * 计算边界框宽度
     */
    val width: Float get() = x2 - x1
    
    /**
     * 计算边界框高度
     */
    val height: Float get() = y2 - y1
    
    /**
     * 计算边界框中心点
     */
    val centerX: Float get() = (x1 + x2) / 2
    val centerY: Float get() = (y1 + y2) / 2
    
    /**
     * 计算边界框面积
     */
    val area: Float get() = width * height
    
    /**
     * 格式化显示信息
     */
    fun toDisplayString(): String {
        return "${getChineseName()} (${(confidence * 100).toInt()}%) - ${getSeverity()}"
    }
}