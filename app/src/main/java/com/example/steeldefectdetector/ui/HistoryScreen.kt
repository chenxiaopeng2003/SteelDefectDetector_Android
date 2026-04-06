package com.example.steeldefectdetector.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.steeldefectdetector.model.DetectionHistory
import androidx.compose.foundation.BorderStroke // 用于绘制边框
import androidx.compose.ui.text.style.TextOverflow // 用于处理文字过长时的省略号
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

/**
 * 历史检测记录页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onViewDetails: (DetectionHistory) -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // 加载历史记录
    LaunchedEffect(Unit) {
        viewModel.loadHistory()
    }
    
    // 显示历史记录详情对话框
    if (uiState.selectedHistory != null) {
        HistoryDetailDialog(
            history = uiState.selectedHistory,
            onDismiss = { viewModel.selectHistory(null) }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史检测记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (uiState.historyList.isEmpty()) {
                // 空状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "空状态",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "暂无检测记录",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 18.sp
                        )
                        Text(
                            text = "进行检测后，记录将显示在这里",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                // 历史记录列表
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.historyList) { history ->
                        HistoryItem(
                            history = history,
                            onViewDetails = {
                                // 1. 设置当前选中的历史记录
                                viewModel.selectHistory(history)
                                // 2. 如果有专门的详情页导航，也可以调用 onViewDetails(history)
                            },
                            onDelete = { viewModel.deleteHistory(history.id) }
                        )

                    }
                }
            }
        }
    }
}

/**
 * 历史记录项
 */
@Composable
fun HistoryItem(
    history: DetectionHistory,
    onViewDetails: () -> Unit,
    onDelete: () -> Unit
) {
    // 1. 定义中英文映射表
    val nameMapping = mapOf(
        "chongkong" to "冲孔",
        "hanfeng" to "焊缝",
        "yueyawan" to "月牙弯",
        "shuiban" to "水斑",
        "youban" to "油污",
        "siban" to "撕斑",
        "yiwu" to "异物",
        "yahen" to "压痕",
        "zhehen" to "折痕",
        "yaozhe" to "腰折"
    )

    // 2. 从报告文本中提取英文类型并转换为中文
    val allDefects = history.comparisonData.lines()
        .filter { it.contains("• 类型:") }
        .map { line ->
            // 提取 "• 类型: chongkong" 中的 chongkong
            val englishName = line.substringAfter("• 类型:").substringBefore("(").trim()
            // 如果在映射表里就转中文，否则保持原样
            nameMapping[englishName] ?: englishName
        }
        .distinct()

    val defectDisplay = if (allDefects.isEmpty()) "无缺陷" else allDefects.joinToString(", ")

    // 3. 严重程度判断逻辑（基于中文名称）
    val severityInfo = when {
        history.defectCount == 0 -> "正常" to Color(0xFF4CAF50)
        allDefects.any { it in listOf("月牙弯", "冲孔") } -> "严重" to Color(0xFFF44336)
        allDefects.all { it in listOf("水斑", "油污") } -> "轻微" to Color(0xFF03A9F4)
        else -> "一般" to Color(0xFFFFC107)
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        onClick = onViewDetails
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 检测时间
            InfoColumn("检测时间", history.getFormattedTime().split(" ")[1], Modifier.weight(1f))

            // 使用模型
            InfoColumn("模型", history.modelName, Modifier.weight(0.8f))

            // 缺陷类型（现在显示中文了）
            InfoColumn("缺陷类型", defectDisplay, Modifier.weight(1.5f))

            // 严重程度标签
            Column(modifier = Modifier.weight(0.8f), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    color = severityInfo.second.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, severityInfo.second.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = severityInfo.first,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = severityInfo.second,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun InfoColumn(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
/**
 * 历史记录详情对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailDialog(
    history: DetectionHistory?,
    onDismiss: () -> Unit
) {
    if (history == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("检测详情报告", fontWeight = FontWeight.Bold) },
        text = {
            // 【关键点】：使用 rememberScrollState() 让 Column 支持滚动
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.7f) // 限制高度，防止对话框撑满全屏
                    .verticalScroll(rememberScrollState()), // 启用滚动
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = history.comparisonData,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 20.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace // 使用等宽字体，报告对齐更整齐
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}