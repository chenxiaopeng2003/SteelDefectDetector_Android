package com.example.steeldefectdetector.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.steeldefectdetector.model.DetectionResult
import com.example.steeldefectdetector.model.DetectionHistory
import com.example.steeldefectdetector.model.OnnxInferenceService
import com.example.steeldefectdetector.data.DetectionDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

data class MainUiState(
    val selectedImage: Bitmap? = null,
    val selectedModel: String? = null,
    val availableModels: List<String> = emptyList(),
    val isDetecting: Boolean = false,
    val detectionResults: List<DetectionResult> = emptyList(),
    val error: String? = null,
    val isSaving: Boolean = false,
    val showSaveSuccess: Boolean = false,
    val comparisonData: String? = null,  // 数据对比文本
    val showComparison: Boolean = false,  // 是否显示对比数据
    val historyList: List<DetectionHistory> = emptyList(),  // 新增：历史记录列表
    val selectedHistory: DetectionHistory? = null  // 新增：选中的历史记录
)

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _selectedModel = mutableStateOf("")
    val selectedModel = _selectedModel

    private val _availableModels = mutableStateOf(emptyList<String>())
    val availableModels = _availableModels

    // 添加Context参数用于加载assets
    private var context: Context? = null

    // 当前选中的模型文件
    private var currentModelFile: String? = null

    // ONNX推理服务
    private var onnxService: OnnxInferenceService? = null

    init {
        // ViewModel 初始化块
    }

    /**
     * 设置 Context 并初始化底层推理引擎
     */
    fun setContext(context: Context) {
        this.context = context
        if (this.onnxService == null) {
            this.onnxService = OnnxInferenceService(context)
            this.onnxService?.initialize()
        }
        // 【修复】：使用新的扫描方法替代旧的 loadAvailableModels
        scanAvailableModels(context)
    }

    /**
     * 当用户在下拉菜单中选择模型时触发
     */
    fun onModelSelected(modelName: String) {
        _selectedModel.value = modelName

        currentModelFile = if (modelName.isNotEmpty()) {
            "$modelName.onnx"
        } else {
            null
        }

        // 必须同步更新 UI 状态，这样屏幕上的文字才会跟着变
        _uiState.update { it.copy(selectedModel = modelName) }

        Log.d("MainViewModel", "选择模型: $modelName, 模型文件: $currentModelFile")
    }

    /**
     * 扫描 assets/models 目录下的所有 .onnx 模型，并更新到 UI 状态
     */
    fun scanAvailableModels(ctx: Context) {
        // 如果引擎还没初始化，顺便初始化一下
        if (this.onnxService == null) {
            this.context = ctx
            this.onnxService = OnnxInferenceService(ctx)
            this.onnxService?.initialize()
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val models = mutableListOf<String>()
                val modelFiles = ctx.assets.list("models")?.toList() ?: emptyList()
                Log.d("MainViewModel", "扫描到文件: ${modelFiles.joinToString(", ")}")

                modelFiles.forEach { fileName ->
                    if (fileName.endsWith(".onnx")) {
                        val modelName = fileName.substringBeforeLast(".").trim()
                        if (modelName.isNotEmpty()) {
                            models.add(modelName)
                        }
                    }
                }
                models.sort()

                val firstModel = models.firstOrNull() ?: ""

                // 将扫描到的结果更新给 UI State
                _uiState.update { currentState ->
                    currentState.copy(
                        availableModels = models,
                        selectedModel = if (currentState.selectedModel.isNullOrEmpty()) firstModel else currentState.selectedModel
                    )
                }

                // 同步更新旧的变量，兼容旧代码
                withContext(Dispatchers.Main) {
                    _availableModels.value = models
                    if (_selectedModel.value.isEmpty()) {
                        _selectedModel.value = firstModel
                        currentModelFile = if (firstModel.isNotEmpty()) "$firstModel.onnx" else null
                    }
                }

            } catch (e: Exception) {
                Log.e("MainViewModel", "扫描模型失败", e)
            }
        }
    }

    fun onImageSelected(bitmap: Bitmap) {
        _uiState.update { it.copy(selectedImage = bitmap, detectionResults = emptyList(), error = null) }
    }

    fun loadImageFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    bitmap
                }

                bitmap?.let {
                    _uiState.update { it.copy(selectedImage = bitmap, detectionResults = emptyList(), error = null) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "图片加载失败: ${e.message}") }
            }
        }
    }

    fun detectDefects() {
        val bitmap = _uiState.value.selectedImage ?: return

        // 检查是否选择了模型
        val modelToRun = _uiState.value.selectedModel
        if (modelToRun.isNullOrEmpty()) {
            _uiState.update {
                it.copy(error = "请先选择检测模型")
            }
            return
        }

        // 获取推理服务，如果为空则拦截
        val service = onnxService
        if (service == null) {
            _uiState.update {
                it.copy(error = "ONNX推理服务未初始化")
            }
            return
        }

        _uiState.update {
            it.copy(
                isDetecting = true,
                error = null,
                comparisonData = null,
                showComparison = false
            )
        }

        viewModelScope.launch {
            try {
                Log.d("MainViewModel", "开始检测，模型: $modelToRun")

                // 1. 加载真实的 ONNX 模型
                val modelLoaded = service.loadModel(modelToRun)
                if (!modelLoaded) {
                    throw Exception("加载模型失败: $modelToRun")
                }

                // 2. 执行张量推理并记录底层耗时
                val inferenceStartTime = System.currentTimeMillis()
                val results = withContext(Dispatchers.IO) {
                    service.runInference(bitmap)
                }
                val inferenceTime = System.currentTimeMillis() - inferenceStartTime

                Log.d("MainViewModel", "检测完成，找到 ${results.size} 个缺陷，耗时: ${inferenceTime}ms")

                // 3. 生成对比数据报告
                val comparisonData = generateComparisonData(
                    selectedModel = modelToRun,
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    results = results,
                    inferenceTime = inferenceTime
                )

                _uiState.update {
                    it.copy(
                        isDetecting = false,
                        detectionResults = results,
                        comparisonData = comparisonData,
                        error = null
                    )
                }

                // 4. 保存检测结果到数据库
                if (results.isNotEmpty()) {
                    saveDetectionToDatabase()
                }

            } catch (e: Exception) {
                Log.e("MainViewModel", "检测失败", e)
                _uiState.update {
                    it.copy(
                        isDetecting = false,
                        error = "检测失败: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 生成数据对比文本
     */
    private fun generateComparisonData(
        selectedModel: String,
        imageWidth: Int,
        imageHeight: Int,
        results: List<DetectionResult>,
        inferenceTime: Long
    ): String {
        val timestamp = System.currentTimeMillis()
        val dateTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(timestamp))

        return buildString {
            // 头部信息
            appendLine("=".repeat(60))
            appendLine("钢材缺陷检测数据对比报告")
            appendLine("=".repeat(60))
            appendLine()

            // 基本信息
            appendLine("📋 检测基本信息")
            appendLine("-".repeat(40))
            appendLine("检测时间: $dateTime")
            appendLine("使用模型: $selectedModel")
            appendLine("图片尺寸: ${imageWidth} × ${imageHeight} 像素")
            appendLine("检测数量: ${results.size} 个缺陷")
            appendLine("推理耗时: ${inferenceTime}ms")
            appendLine()

            // 检测结果详情
            appendLine("🔍 缺陷检测详情")
            appendLine("-".repeat(40))
            if (results.isEmpty()) {
                appendLine("未检测到缺陷")
            } else {
                results.forEachIndexed { index, result ->
                    appendLine("缺陷 #${index + 1}:")
                    appendLine("  • 类型: ${result.className} (${result.getChineseName()})")
                    appendLine("  • 置信度: ${String.format("%.2f", result.confidence)} (${(result.confidence * 100).toInt()}%)")
                    appendLine("  • 边界框: [${String.format("%.4f", result.x1)}, ${String.format("%.4f", result.y1)}, ${String.format("%.4f", result.x2)}, ${String.format("%.4f", result.y2)}]")
                    appendLine("  • 像素坐标: (${result.x1.toInt()}, ${result.y1.toInt()}) - (${result.x2.toInt()}, ${result.y2.toInt()})")
                    appendLine("  • 尺寸: ${result.width.toInt()} × ${result.height.toInt()} 像素")
                    appendLine("  • 面积: ${result.area.toInt()} 像素")
                    appendLine("  • 中心点: (${result.centerX.toInt()}, ${result.centerY.toInt()})")
                    appendLine("  • 严重程度: ${result.getSeverity()}")
                    appendLine("  • 处理建议: ${result.description}")
                    appendLine()
                }
            }

            // 统计信息
            appendLine("📊 检测统计信息")
            appendLine("-".repeat(40))
            if (results.isNotEmpty()) {
                val avgConfidence = results.map { it.confidence }.average()
                val maxConfidence = results.maxOf { it.confidence }
                val minConfidence = results.minOf { it.confidence }

                appendLine("平均置信度: ${String.format("%.2f", avgConfidence)}")
                appendLine("最高置信度: ${String.format("%.2f", maxConfidence)}")
                appendLine("最低置信度: ${String.format("%.2f", minConfidence)}")
                appendLine()

                // 按类型统计
                val typeCounts = results.groupingBy { it.className }.eachCount()
                appendLine("缺陷类型分布:")
                typeCounts.forEach { (type, count) ->
                    val chineseName = DetectionResult(type, 0f, 0f, 0f, 0f, 0f).getChineseName()
                    appendLine("  • $chineseName ($type): $count 个")
                }
                appendLine()

                // 按严重程度统计
                val severityCounts = results.groupingBy { it.getSeverity() }.eachCount()
                appendLine("严重程度分布:")
                severityCounts.forEach { (severity, count) ->
                    appendLine("  • $severity: $count 个")
                }
            } else {
                appendLine("无统计信息")
            }
            appendLine()

            // 数据格式说明（方便电脑端解析）
            appendLine("💡 数据格式说明")
            appendLine("-".repeat(40))
            appendLine("• 边界框格式: [x1, y1, x2, y2] (归一化坐标 0-1)")
            appendLine("• 像素坐标: 基于原始图片尺寸")
            appendLine("• 置信度: 0.0-1.0，越高表示检测越可靠")
            appendLine("• 可与电脑端YOLO/PyTorch输出直接对比")
            appendLine()

            appendLine("=".repeat(60))
            appendLine("报告结束")
            appendLine("=".repeat(60))
        }
    }

    suspend fun saveImageToDatabase(bitmap: Bitmap, source: String) {
        // 简化版本，暂时不实现
    }

    suspend fun saveDetectionToDatabase() {
        _uiState.update { it.copy(isSaving = true) }

        try {
            withContext(Dispatchers.IO) {
                // 模拟保存
                kotlinx.coroutines.delay(1000)
                true
            }.let { success ->
                if (success) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            showSaveSuccess = true
                        )
                    }

                    // 3秒后隐藏成功提示
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(3000)
                        _uiState.update { it.copy(showSaveSuccess = false) }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = "保存失败"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isSaving = false,
                    error = "保存失败: ${e.message}"
                )
            }
        }
    }

    fun initializeDatabase(context: Context) {
        // 简化版本，暂时不实现
    }

    /**
     * 显示消息
     */
    fun showMessage(message: String) {
        _uiState.update {
            it.copy(error = message)
        }

        // 3秒后清除消息
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            _uiState.update { it.copy(error = null) }
        }
    }

    /**
     * 切换对比数据显示
     */
    fun toggleComparisonData() {
        _uiState.update {
            it.copy(showComparison = !it.showComparison)
        }
    }

    /**
     * 从文件加载图片
     */
    fun loadImageFromFile(context: Context, file: java.io.File) {
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                }

                bitmap?.let {
                    _uiState.update { it.copy(selectedImage = bitmap, detectionResults = emptyList(), error = null) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "图片文件加载失败: ${e.message}") }
            }
        }
    }

    /**
     * 加载历史记录
     */
    fun loadHistory() {
        viewModelScope.launch {
            try {
                val dbHelper = DetectionDatabaseHelper(context!!)
                val historyList = dbHelper.getAllDetectionHistory()

                _uiState.update {
                    it.copy(historyList = historyList)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "加载历史记录失败", e)
                _uiState.update {
                    it.copy(error = "加载历史记录失败: ${e.message}")
                }
            }
        }
    }

    /**
     * 删除历史记录
     */
    fun deleteHistory(id: Long) {
        viewModelScope.launch {
            try {
                val dbHelper = DetectionDatabaseHelper(context!!)
                val db = dbHelper.writableDatabase

                val deletedRows = db.delete(
                    DetectionDatabaseHelper.TABLE_DETECTIONS,
                    "${DetectionDatabaseHelper.COLUMN_ID} = ?",
                    arrayOf(id.toString())
                )

                if (deletedRows > 0) {
                    // 重新加载历史记录
                    loadHistory()
                    showMessage("删除成功")
                } else {
                    showMessage("删除失败")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "删除历史记录失败", e)
                _uiState.update {
                    it.copy(error = "删除历史记录失败: ${e.message}")
                }
            }
        }
    }

    /**
     * 选择历史记录查看详情
     */
    fun selectHistory(history: DetectionHistory?) {
        _uiState.update {
            it.copy(selectedHistory = history)
        }
    }

    /**
     * 复制对比数据到剪贴板
     */
    fun copyComparisonDataToClipboard(context: android.content.Context) {
        val comparisonData = _uiState.value.comparisonData
        if (comparisonData != null) {
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("检测数据", comparisonData)
            clipboard.setPrimaryClip(clip)

            showMessage("已复制到剪贴板")
        } else {
            showMessage("没有可复制的数据")
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 释放引擎资源
        onnxService?.release()
    }
}