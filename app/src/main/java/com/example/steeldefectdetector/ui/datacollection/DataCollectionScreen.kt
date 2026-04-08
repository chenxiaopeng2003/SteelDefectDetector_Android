package com.example.steeldefectdetector.ui.datacollection

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.steeldefectdetector.model.annotation.AnnotationMode
import com.example.steeldefectdetector.ui.datacollection.components.InteractiveAnnotationCanvas

@Composable
fun DataCollectionScreen(
    currentImageBitmap: ImageBitmap?, // 从上层传入的待标注图片
    viewModel: DataCollectionViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentMode by viewModel.currentMode.collectAsState()
    val annotations by viewModel.annotations.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()

    // 监听导出事件 (Toast)
    LaunchedEffect(Unit) {
        viewModel.exportEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. 顶部控制栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row {
                Button(
                    onClick = { viewModel.switchMode(AnnotationMode.VIEW_PAN_ZOOM) },
                    enabled = currentMode != AnnotationMode.VIEW_PAN_ZOOM,
                    modifier = Modifier.padding(end = 8.dp)
                ) { Text("🔍 拖拽") }
                
                Button(
                    onClick = { viewModel.switchMode(AnnotationMode.DRAW_BBOX) },
                    enabled = currentMode != AnnotationMode.DRAW_BBOX
                ) { Text("✏️ 绘制") }
            }
            
            Button(
                onClick = { viewModel.clearLastAnnotation() },
                enabled = annotations.isNotEmpty()
            ) { Text("撤销") }
        }

        // 2. 核心画板区 (占据主体空间)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (currentImageBitmap != null) {
                InteractiveAnnotationCanvas(
                    bitmap = currentImageBitmap,
                    mode = currentMode,
                    annotations = annotations,
                    currentLabelId = 0, // 实际应用中这里可替换为下拉框选择的 ID
                    currentLabelName = "缺陷类别", // 实际应用中替换为下拉框选择的名称
                    onAddAnnotation = { viewModel.addAnnotation(it) }
                )
            } else {
                Text(
                    text = "请先选择或拍摄一张图片",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // 3. 底部操作区 (导出)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "当前标注数: ${annotations.size}")
                
                Button(
                    onClick = { 
                        if (currentImageBitmap != null) {
                            viewModel.saveToDataset(context, currentImageBitmap)
                        }
                    },
                    enabled = !isExporting && annotations.isNotEmpty() && currentImageBitmap != null
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("💾 保存至数据集 (YOLO)")
                    }
                }
            }
        }
    }
}