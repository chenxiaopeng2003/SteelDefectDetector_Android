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
import java.io.File
import java.io.FileOutputStream

data class MainUiState(
    // === 缺陷检测系统专用状态 ===
    val selectedImage: Bitmap? = null,
    val currentImagePath: String? = null,

    // === 数据采集系统专用状态 ===
    val annotationImage: Bitmap? = null,
    val annotationImagePath: String? = null,

    // === 共享状态 ===
    val selectedModel: String? = null,
    val availableModels: List<String> = emptyList(),
    val isDetecting: Boolean = false,
    val detectionResults: List<DetectionResult> = emptyList(),
    val error: String? = null,
    val comparisonData: String? = null,
    val showComparison: Boolean = false,
    val historyList: List<DetectionHistory> = emptyList(),
    val selectedHistory: DetectionHistory? = null,
    val targetTabIndex: Int = 0 // 控制主页面 Tab (0=检测, 1=数据采集)
)

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var dbHelper: DetectionDatabaseHelper? = null
    private var inferenceService: OnnxInferenceService? = null

    var messageEvent by mutableStateOf<String?>(null)
        private set

    fun setContext(context: Context) {
        if (dbHelper == null) {
            dbHelper = DetectionDatabaseHelper(context)
            inferenceService = OnnxInferenceService(context)
            loadAvailableModels(context)
        }
    }

    fun showMessage(message: String) { messageEvent = message }
    fun clearMessage() { messageEvent = null }
    fun setTargetTab(index: Int) { _uiState.update { it.copy(targetTabIndex = index) } }

    private fun loadAvailableModels(context: Context) {
        try {
            val models = context.assets.list("models")?.filter { it.endsWith(".onnx") } ?: emptyList()
            val availableModels = models.map { it.replace(".onnx", "") }
            _uiState.update { it.copy(availableModels = availableModels, selectedModel = availableModels.firstOrNull()) }
            availableModels.firstOrNull()?.let { onModelSelected(it) }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "加载模型列表失败: ${e.message}") }
        }
    }

    fun onModelSelected(modelName: String) {
        _uiState.update { it.copy(selectedModel = modelName) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                inferenceService?.loadModel(modelName)
                withContext(Dispatchers.Main) { showMessage("已加载模型: $modelName") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showMessage("加载模型失败: ${e.message}") }
            }
        }
    }

    // ==========================================
    // 缺陷检测系统 - 图片加载
    // ==========================================
    fun loadImageFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(detectionResults = emptyList(), comparisonData = null, showComparison = false, currentImagePath = null) }
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                val fileName = "DETECT_${System.currentTimeMillis()}.jpg"
                val file = File(context.filesDir, fileName)
                FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out) }

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(selectedImage = bitmap, currentImagePath = file.absolutePath) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showMessage("加载图片失败: ${e.message}") }
            }
        }
    }

    fun loadImageFromFile(context: Context, file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(detectionResults = emptyList(), comparisonData = null, showComparison = false, currentImagePath = null) }
                val internalFile = File(context.filesDir, "DETECT_${file.name}")
                file.copyTo(internalFile, overwrite = true)
                val bitmap = android.graphics.BitmapFactory.decodeFile(internalFile.absolutePath)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(selectedImage = bitmap, currentImagePath = internalFile.absolutePath) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showMessage("加载图片失败: ${e.message}") }
            }
        }
    }

    // ==========================================
    // 数据采集系统 - 独立图片加载
    // ==========================================
    fun loadAnnotationImageFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                val fileName = "ANNOTATE_${System.currentTimeMillis()}.jpg"
                val file = File(context.filesDir, fileName)
                FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out) }

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(annotationImage = bitmap, annotationImagePath = file.absolutePath) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showMessage("加载标注图片失败: ${e.message}") }
            }
        }
    }

    fun loadAnnotationImageFromFile(context: Context, file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val internalFile = File(context.filesDir, "ANNOTATE_${file.name}")
                file.copyTo(internalFile, overwrite = true)
                val bitmap = android.graphics.BitmapFactory.decodeFile(internalFile.absolutePath)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(annotationImage = bitmap, annotationImagePath = internalFile.absolutePath) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showMessage("加载标注图片失败: ${e.message}") }
            }
        }
    }

    // ==========================================
    // 检测逻辑与数据库操作
    // ==========================================
    fun detectDefects() {
        val currentState = _uiState.value
        val bitmap = currentState.selectedImage ?: return
        val currentModel = currentState.selectedModel ?: return

        _uiState.update { it.copy(isDetecting = true, error = null) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                val results = inferenceService?.runInference(bitmap) ?: emptyList()
                val inferenceTime = System.currentTimeMillis() - startTime
                val comparisonData = generateComparisonData(results, currentModel, inferenceTime, bitmap.width, bitmap.height)

                dbHelper?.saveDetection(
                    imagePath = currentState.currentImagePath ?: "",
                    modelUsed = currentModel,
                    results = results,
                    inferenceTime = inferenceTime,
                    note = comparisonData
                )

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isDetecting = false, detectionResults = results, comparisonData = comparisonData, showComparison = false) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _uiState.update { it.copy(isDetecting = false, error = "检测失败: ${e.message}") } }
            }
        }
    }

    private fun generateComparisonData(results: List<DetectionResult>, modelName: String, inferenceTime: Long, width: Int, height: Int): String {
        val sb = StringBuilder().append("--- 钢材缺陷检测报告 ---\n\n基本信息:\n• 使用模型: $modelName\n• 图像分辨率: $width × $height\n• 推理耗时: $inferenceTime ms\n• 缺陷总数: ${results.size} 个\n\n")
        if (results.isEmpty()) sb.append("检测结果: 表面良好，未发现缺陷。\n") else {
            sb.append("缺陷类型统计:\n")
            results.groupingBy { it.getChineseName() }.eachCount().forEach { (type, count) -> sb.append("• $type: $count 处\n") }
            sb.append("\n详细缺陷位置:\n")
            results.forEachIndexed { index, result -> sb.append("${index + 1}. [${result.getChineseName()}] 置信度: ${String.format("%.1f%%", result.confidence * 100)}\n") }
        }
        return sb.append("\n--- 报告结束 ---").toString()
    }

    fun toggleComparisonData() { _uiState.update { it.copy(showComparison = !it.showComparison) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }

    fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val historyList = dbHelper?.getAllDetectionHistory() ?: emptyList()
                withContext(Dispatchers.Main) { _uiState.update { it.copy(historyList = historyList) } }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _uiState.update { it.copy(error = "加载历史记录失败: ${e.message}") } }
            }
        }
    }

    fun deleteHistory(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val deletedRows = dbHelper?.writableDatabase?.delete(DetectionDatabaseHelper.TABLE_DETECTIONS, "${DetectionDatabaseHelper.COLUMN_ID} = ?", arrayOf(id.toString())) ?: 0
            if (deletedRows > 0) { loadHistory(); withContext(Dispatchers.Main){ showMessage("删除成功") } }
        }
    }

    fun selectHistory(history: DetectionHistory?) { _uiState.update { it.copy(selectedHistory = history) } }

    override fun onCleared() {
        super.onCleared()
        inferenceService?.release()
        dbHelper?.close()
    }
}