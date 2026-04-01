package com.example.steeldefectdetector.model

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.steeldefectdetector.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections
import kotlin.math.max
import kotlin.math.min

/**
 * 真正的 ONNX 模型推理服务
 * 专门针对 YOLOv8 目标检测模型架构设计
 */
class RealOnnxInferenceService(private val context: Context) {

    companion object {
        private const val TAG = "RealOnnxInferenceService"

        // 你的10种钢材缺陷类别
        val DEFECT_CLASSES = listOf(
            "chongkong", "hanfeng", "yueyawan", "shuiban", "youban",
            "siban", "yiwu", "yahen", "zhehen", "yaozhe"
        )

        // 模型输入尺寸
        private const val INPUT_SIZE = 640
        // 置信度阈值和 NMS IoU 阈值 (可根据实际效果微调)
        private const val CONF_THRESHOLD = 0.45f
        private const val IOU_THRESHOLD = 0.45f
    }

    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var currentModelName: String? = null

    /**
     * 初始化环境
     */
    fun initialize() {
        Log.d(TAG, "初始化 ONNX Runtime 环境")
        ortEnvironment = OrtEnvironment.getEnvironment()
    }

    /**
     * 加载ONNX模型
     */
    suspend fun loadModel(modelName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (currentModelName == modelName && ortSession != null) {
                    return@withContext true
                }

                // 释放旧的 session
                ortSession?.close()

                val modelPath = "models/$modelName.onnx"
                val modelBytes = context.assets.open(modelPath).readBytes()

                // 配置 Session 选项，启用多线程加速
                val options = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(4)
                }

                ortSession = ortEnvironment?.createSession(modelBytes, options)
                currentModelName = modelName

                Log.d(TAG, "✅ 模型 $modelName 加载成功，底层 ONNX 引擎已就绪")
                true
            } catch (e: Exception) {
                Log.e(TAG, "❌ 加载模型失败: ${e.message}", e)
                false
            }
        }
    }

    /**
     * 核心推理逻辑
     */
    suspend fun runInference(bitmap: Bitmap): List<DetectionResult> {
        return withContext(Dispatchers.IO) {
            val session = ortSession ?: throw IllegalStateException("ONNX Session 未初始化")
            val env = ortEnvironment ?: throw IllegalStateException("ONNX Env 未初始化")

            val originalWidth = bitmap.width
            val originalHeight = bitmap.height

            try {
                val startTime = System.currentTimeMillis()

                // 1. 图像预处理 (缩放 + 填充黑边)
                val resizedBitmap = ImageUtils.resizeTo640x640(bitmap)

                // 2. 转换为模型需要的 CHW FloatBuffer
                val floatBuffer = ImageUtils.bitmapToFloatBuffer(resizedBitmap)

                // YOLOv8 的输入 shape: [batch_size, channels, height, width]
                val inputShape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())

                // 3. 构建张量并执行推理
                val inputName = session.inputNames.iterator().next()
                val inputTensor = OnnxTensor.createTensor(env, floatBuffer, inputShape)
                val output = session.run(Collections.singletonMap(inputName, inputTensor))

                val inferenceTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "ONNX 底层运算耗时: ${inferenceTime}ms")

                // 4. 解析输出张量
                // YOLOv8 默认输出 shape 为 [1, 14, 8400]
                // 修复：必须先提取 OnnxTensor，再调用 .value 转换为多维数组
                val outputTensor = output.first().value as OnnxTensor
                val rawOutput = outputTensor.value as Array<Array<FloatArray>>
                val outputArray = rawOutput[0]

                val detections = parseAndPostProcess(outputArray, originalWidth, originalHeight)

                // 5. 释放当前张量资源，防止内存泄漏
                inputTensor.close()
                output.close()
                resizedBitmap.recycle()

                detections
            } catch (e: Exception) {
                Log.e(TAG, "推理发生异常: ${e.message}", e)
                emptyList()
            }
        }
    }

    /**
     * 后处理：张量解析、坐标逆变换与 NMS
     */
    private fun parseAndPostProcess(
        outputArray: Array<FloatArray>,
        originalWidth: Int,
        originalHeight: Int
    ): List<DetectionResult> {
        val parsedResults = mutableListOf<DetectionResult>()

        // 检查维度，针对 YOLOv8 标准输出 [14, 8400]
        val numChannels = outputArray.size     // 14 (4 bbox + 10 classes)
        val numElements = outputArray[0].size  // 8400 anchors

        // 计算坐标逆变换的比例和偏移 (对应 ImageUtils 中的 resizeTo640x640 逻辑)
        val scale = INPUT_SIZE.toFloat() / max(originalWidth, originalHeight)
        val scaledWidth = originalWidth * scale
        val scaledHeight = originalHeight * scale
        val padLeft = (INPUT_SIZE - scaledWidth) / 2f
        val padTop = (INPUT_SIZE - scaledHeight) / 2f

        // 遍历 8400 个检测框
        for (i in 0 until numElements) {
            var maxClassConf = 0f
            var classId = -1

            // 在 10 个类别中找到置信度最高的
            for (c in 4 until numChannels) {
                val conf = outputArray[c][i]
                if (conf > maxClassConf) {
                    maxClassConf = conf
                    classId = c - 4
                }
            }

            // 只有置信度超过阈值才进行边界框计算
            if (maxClassConf > CONF_THRESHOLD) {
                // 读取中心点和宽高
                val cx = outputArray[0][i]
                val cy = outputArray[1][i]
                val w = outputArray[2][i]
                val h = outputArray[3][i]

                // 转换为 640x640 坐标系下的左上角和右下角
                val x1_640 = cx - w / 2f
                val y1_640 = cy - h / 2f
                val x2_640 = cx + w / 2f
                val y2_640 = cy + h / 2f

                // 逆变换回原图的真实坐标
                val x1 = (x1_640 - padLeft) / scale
                val y1 = (y1_640 - padTop) / scale
                val x2 = (x2_640 - padLeft) / scale
                val y2 = (y2_640 - padTop) / scale

                // 边界保护
                val safeX1 = max(0f, x1)
                val safeY1 = max(0f, y1)
                val safeX2 = min(originalWidth.toFloat(), x2)
                val safeY2 = min(originalHeight.toFloat(), y2)

                // 获取类别名
                val className = if (classId in DEFECT_CLASSES.indices) DEFECT_CLASSES[classId] else "unknown"

                parsedResults.add(
                    DetectionResult(
                        className = className,
                        confidence = maxClassConf,
                        x1 = safeX1, y1 = safeY1, x2 = safeX2, y2 = safeY2,
                        description = "${getChineseName(className)}缺陷"
                    )
                )
            }
        }

        // 应用非极大值抑制 (NMS) 过滤重叠框
        return applyNMS(parsedResults)
    }

    /**
     * 非极大值抑制 (NMS)
     */
    private fun applyNMS(detections: List<DetectionResult>): List<DetectionResult> {
        if (detections.isEmpty()) return emptyList()

        // 按置信度从高到低排序
        val sortedDetections = detections.sortedByDescending { it.confidence }
        val selected = mutableListOf<DetectionResult>()

        val active = BooleanArray(sortedDetections.size) { true }

        for (i in sortedDetections.indices) {
            if (!active[i]) continue

            val best = sortedDetections[i]
            selected.add(best)

            for (j in i + 1 until sortedDetections.size) {
                if (!active[j]) continue

                // 如果是同一类别，并且重叠度(IoU)太高，则认为是重复检测，剔除掉
                if (best.className == sortedDetections[j].className) {
                    val iou = calculateIoU(best, sortedDetections[j])
                    if (iou > IOU_THRESHOLD) {
                        active[j] = false
                    }
                }
            }
        }

        return selected
    }

    /**
     * 计算交并比 (IoU)
     */
    private fun calculateIoU(box1: DetectionResult, box2: DetectionResult): Float {
        val x1 = max(box1.x1, box2.x1)
        val y1 = max(box1.y1, box2.y1)
        val x2 = min(box1.x2, box2.x2)
        val y2 = min(box1.y2, box2.y2)

        val intersection = max(0f, x2 - x1) * max(0f, y2 - y1)
        val area1 = (box1.x2 - box1.x1) * (box1.y2 - box1.y1)
        val area2 = (box2.x2 - box2.x1) * (box2.y2 - box2.y1)
        val union = area1 + area2 - intersection

        return if (union > 0) intersection / union else 0f
    }

    private fun getChineseName(className: String): String {
        return when (className) {
            "chongkong" -> "冲孔"
            "hanfeng" -> "焊峰"
            "yueyawan" -> "月牙弯"
            "shuiban" -> "水斑"
            "youban" -> "油污"
            "siban" -> "撕斑"
            "yiwu" -> "异物"
            "yahen" -> "压痕"
            "zhehen" -> "折痕"
            "yaozhe" -> "腰折"
            else -> className
        }
    }

    /**
     * 释放底层 C++ 资源
     */
    fun release() {
        ortSession?.close()
        ortSession = null
        ortEnvironment?.close()
        ortEnvironment = null
        currentModelName = null
        Log.d(TAG, "ONNX 资源已彻底释放")
    }
}