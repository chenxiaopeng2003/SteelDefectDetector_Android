package com.example.steeldefectdetector.ui.datacollection

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.steeldefectdetector.model.annotation.AnnotationBox
import com.example.steeldefectdetector.model.annotation.AnnotationMode
import com.example.steeldefectdetector.utils.DatasetExporter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DataCollectionViewModel : ViewModel() {

    // 1. 当前交互模式：默认为拖拽缩放
    private val _currentMode = MutableStateFlow(AnnotationMode.VIEW_PAN_ZOOM)
    val currentMode: StateFlow<AnnotationMode> = _currentMode.asStateFlow()

    // 2. 标注框列表
    private val _annotations = MutableStateFlow<List<AnnotationBox>>(emptyList())
    val annotations: StateFlow<List<AnnotationBox>> = _annotations.asStateFlow()

    // 3. 当前选中的缺陷类别 ID 与名称
    private val _currentSelectedLabelId = MutableStateFlow(0) 
    val currentSelectedLabelId: StateFlow<Int> = _currentSelectedLabelId.asStateFlow()
    
    private val _currentSelectedLabelName = MutableStateFlow("默认缺陷")
    val currentSelectedLabelName: StateFlow<String> = _currentSelectedLabelName.asStateFlow()

    // --- 状态操作意图 (Intents) ---

    fun switchMode(mode: AnnotationMode) {
        _currentMode.value = mode
    }

    fun setSelectedLabel(id: Int, name: String) {
        _currentSelectedLabelId.value = id
        _currentSelectedLabelName.value = name
    }

    fun addAnnotation(box: AnnotationBox) {
        _annotations.update { currentList ->
            currentList + box
        }
    }

    fun clearLastAnnotation() {
        _annotations.update { currentList ->
            if (currentList.isNotEmpty()) currentList.dropLast(1) else currentList
        }
    }
    
    fun clearAllAnnotations() {
        _annotations.value = emptyList()
    }

    // --- 数据集导出逻辑 ---
    
    // 导出结果事件流 (用于触发 UI 的 Toast 等一次性事件)
    private val _exportEvent = MutableSharedFlow<String>()
    val exportEvent = _exportEvent.asSharedFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    /**
     * 执行保存数据集逻辑
     */
    fun saveToDataset(context: Context, imageBitmap: ImageBitmap) {
        if (_annotations.value.isEmpty()) {
            viewModelScope.launch { _exportEvent.emit("错误：请至少标注一个缺陷") }
            return
        }
        
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val path = DatasetExporter.saveYoloDataset(context, imageBitmap, _annotations.value)
                _exportEvent.emit("保存成功！路径: $path")
                // 保存成功后清空画布
                clearAllAnnotations()
            } catch (e: Exception) {
                _exportEvent.emit("保存失败: ${e.localizedMessage}")
            } finally {
                _isExporting.value = false
            }
        }
    }

}