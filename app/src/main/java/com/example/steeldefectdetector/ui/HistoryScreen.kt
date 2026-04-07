package com.example.steeldefectdetector.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.activity.ComponentActivity
import com.example.steeldefectdetector.model.DetectionHistory
import java.io.File
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onViewDetails: (DetectionHistory) -> Unit,
    viewModel: MainViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as? ComponentActivity ?: LocalViewModelStoreOwner.current!!
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadHistory()
    }

    if (uiState.selectedHistory != null) {
        HistoryDetailFullScreen(
            history = uiState.selectedHistory!!,
            onDismiss = { viewModel.selectHistory(null) },
            onReAnnotate = {
                val historyToAnnotate = uiState.selectedHistory
                viewModel.selectHistory(null)

                // 【核心修改】：使用专属的标注图片加载方法！
                if (historyToAnnotate?.imagePath != null) {
                    val file = File(historyToAnnotate.imagePath)
                    if (file.exists()) {
                        viewModel.loadAnnotationImageFromFile(context, file)
                        viewModel.setTargetTab(1)
                        onBack()
                    } else {
                        Toast.makeText(context, "原始图片已被清理，无法重新标注", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "该记录无图片信息", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("历史记录", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        if (uiState.historyList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text(text = "暂无检测历史记录", color = Color.Gray, fontSize = 16.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                items(uiState.historyList) { history ->
                    HistoryItemCard(
                        history = history,
                        onClick = { viewModel.selectHistory(history) },
                        onDelete = { viewModel.deleteHistory(history.id) }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryItemCard(
    history: DetectionHistory,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = history.getFormattedTime(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "模型: ${history.modelName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (history.results.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "正常 (未检测到缺陷)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    val groupedResults = history.results.groupBy { it.getChineseName() }
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        groupedResults.forEach { (name, list) ->
                            val severity = getSeverityLevel(name)
                            val (bgColor, textColor) = getSeverityColor(severity)
                            Surface(shape = RoundedCornerShape(6.dp), color = bgColor) {
                                Text(text = "$name x${list.size} ($severity)", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = textColor)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

fun getSeverityLevel(chineseName: String): String {
    return when (chineseName) {
        "月牙弯", "冲孔" -> "严重"
        "水斑", "油斑" -> "轻微"
        else -> "一般"
    }
}

@Composable
fun getSeverityColor(severity: String): Pair<Color, Color> {
    return when (severity) {
        "严重" -> Pair(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        "一般" -> Pair(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        "轻微" -> Pair(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        else -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}