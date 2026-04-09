
package com.example.steeldefectdetector.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.steeldefectdetector.model.DetectionResult
import com.example.steeldefectdetector.ui.components.BoundingBoxOverlay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    onNavigateToExport: () -> Unit,
    onNavigateToHistory: () -> Unit,
    viewModel: MainViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val pagerState = rememberPagerState(
        initialPage = uiState.targetTabIndex,
        pageCount = { 2 }
    )

    LaunchedEffect(Unit) { viewModel.setContext(context) }

    LaunchedEffect(viewModel.messageEvent) {
        viewModel.messageEvent?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(uiState.targetTabIndex) {
        if (pagerState.currentPage != uiState.targetTabIndex) {
            pagerState.animateScrollToPage(uiState.targetTabIndex)
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        if (uiState.targetTabIndex != pagerState.settledPage) {
            viewModel.setTargetTab(pagerState.settledPage)
        }
    }

    val tabs = listOf("缺陷检测", "数据采集")

    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    "钢材缺陷检测系统",
                    fontWeight = FontWeight.SemiBold,
                    color = com.example.steeldefectdetector.ui.theme.OnSurface,
                    maxLines = 1
                )
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = com.example.steeldefectdetector.ui.theme.SurfaceWhite,
                scrolledContainerColor = com.example.steeldefectdetector.ui.theme.SurfaceWhite,
                titleContentColor = com.example.steeldefectdetector.ui.theme.OnSurface
            )
            // 【UI 3】：已删除右上角历史记录与导出功能按钮
        )

        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = com.example.steeldefectdetector.ui.theme.SurfaceWhite,
            contentColor = com.example.steeldefectdetector.ui.theme.PrimaryBlue
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { viewModel.setTargetTab(index) },
                    text = { Text(text = title, fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal, maxLines = 1) }
                )
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> DetectionView(viewModel, onNavigateToHistory, onNavigateToExport, context)
                1 -> DataCollectionView(viewModel, context)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetectionView(
    viewModel: MainViewModel,
    onNavigateToHistory: () -> Unit,
    onNavigateToExport: () -> Unit,
    context: android.content.Context
) {
    val uiState by viewModel.uiState.collectAsState()

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.loadImageFromUri(context, it) }
    }
    val photoFile = remember {
        try {
            val storageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            File.createTempFile("DETECT_${System.currentTimeMillis()}_", ".jpg", storageDir)
        } catch (e: Exception) { null }
    }
    val photoUri = remember(photoFile) {
        photoFile?.let { androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && photoFile != null) viewModel.loadImageFromFile(context, photoFile) else viewModel.showMessage("拍照失败")
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) photoUri?.let { cameraLauncher.launch(it) } else viewModel.showMessage("需要相机权限")
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Spacer(modifier = Modifier.height(16.dp))

        // 【UI 2】：模型选择框与历史记录按钮齐平布局
        var expanded by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = uiState.selectedModel ?: "选择模型",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = com.example.steeldefectdetector.ui.theme.PrimaryBlue,
                        unfocusedBorderColor = com.example.steeldefectdetector.ui.theme.OutlineVariant,
                        focusedLabelColor = com.example.steeldefectdetector.ui.theme.PrimaryBlue,
                        unfocusedLabelColor = com.example.steeldefectdetector.ui.theme.OnSurfaceVariant,
                        focusedTextColor = com.example.steeldefectdetector.ui.theme.OnSurface,
                        unfocusedTextColor = com.example.steeldefectdetector.ui.theme.OnSurface
                    ),
                    trailingIcon = {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "选择模型", tint = com.example.steeldefectdetector.ui.theme.OnSurfaceVariant)
                        }
                    },
                    label = { Text("检测模型", maxLines = 1) }
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    uiState.availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model) },
                            onClick = {
                                viewModel.onModelSelected(model)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Button(
                onClick = onNavigateToHistory,
                modifier = Modifier.height(56.dp), // 对齐输入框近似高度
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = com.example.steeldefectdetector.ui.theme.PrimaryBlue)
            ) {
                Icon(Icons.Default.History, contentDescription = "历史")
                Spacer(Modifier.width(4.dp))
                Text("历史记录", maxLines = 1)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().height(360.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                uiState.selectedImage?.let { bitmap ->
                    if (uiState.detectionResults.isNotEmpty()) {
                        BoundingBoxOverlay(bitmap = bitmap, detections = uiState.detectionResults, modifier = Modifier.fillMaxSize())
                    } else {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    // 【Bug 1】：在已选图像预览界面，补回图库选择按钮
                    Box(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FloatingActionButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                containerColor = com.example.steeldefectdetector.ui.theme.SurfaceWhite,
                                contentColor = com.example.steeldefectdetector.ui.theme.PrimaryBlue
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = "重新选择")
                            }
                            FloatingActionButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                        photoUri?.let { cameraLauncher.launch(it) }
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                containerColor = com.example.steeldefectdetector.ui.theme.PrimaryBlue,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "重新拍照")
                            }
                        }
                    }
                } ?: run {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Card(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    photoUri?.let { cameraLauncher.launch(it) }
                                } else {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = com.example.steeldefectdetector.ui.theme.PrimaryBlue.copy(alpha = 0.1f))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(48.dp), tint = com.example.steeldefectdetector.ui.theme.PrimaryBlue)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("拍照", color = com.example.steeldefectdetector.ui.theme.PrimaryBlue, fontWeight = FontWeight.Medium)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = com.example.steeldefectdetector.ui.theme.PrimaryBlue.copy(alpha = 0.1f))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(48.dp), tint = com.example.steeldefectdetector.ui.theme.PrimaryBlue)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("从相册选择", color = com.example.steeldefectdetector.ui.theme.PrimaryBlue, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.detectDefects() },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = com.example.steeldefectdetector.ui.theme.PrimaryBlue,
                contentColor = com.example.steeldefectdetector.ui.theme.SurfaceWhite,
                disabledContainerColor = com.example.steeldefectdetector.ui.theme.PrimaryBlue.copy(alpha = 0.5f),
                disabledContentColor = com.example.steeldefectdetector.ui.theme.SurfaceWhite.copy(alpha = 0.5f)
            ),
            enabled = uiState.selectedImage != null && !uiState.isDetecting
        ) {
            if (uiState.isDetecting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 3.dp)
                Spacer(modifier = Modifier.width(16.dp))
                Text("检测中...", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
            } else {
                Text("开始检测", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        uiState.error?.let { errorMsg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(text = errorMsg, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (uiState.detectionResults.isNotEmpty() || uiState.comparisonData != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = com.example.steeldefectdetector.ui.theme.CardBackground,
                    contentColor = com.example.steeldefectdetector.ui.theme.OnSurface
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("检测结果", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = com.example.steeldefectdetector.ui.theme.OnSurface)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (uiState.detectionResults.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.detectionResults.forEach { result -> DetectionResultItem(result = result) }
                        }
                    } else {
                        NoDefectResultItem()
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 【UI 1】：底部操作按钮引入 FlowRow 防止硬换行，加入 maxLines=1
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { viewModel.toggleComparisonData() }, modifier = Modifier.height(48.dp), shape = RoundedCornerShape(8.dp)) {
                            Icon(imageVector = if (uiState.showComparison) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Toggle", modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (uiState.showComparison) "隐藏数据" else "显示数据", maxLines = 1)
                        }
                        Button(onClick = onNavigateToExport, modifier = Modifier.height(48.dp), shape = RoundedCornerShape(8.dp)) {
                            Text("导出数据", maxLines = 1)
                        }
                    }

                    if (uiState.showComparison && uiState.comparisonData != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = com.example.steeldefectdetector.ui.theme.SurfaceLight,
                                contentColor = com.example.steeldefectdetector.ui.theme.OnSurface
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("检测详情", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    IconButton(onClick = {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("检测数据", uiState.comparisonData)
                                        clipboard.setPrimaryClip(clip)
                                        viewModel.showMessage("已复制到剪贴板")
                                    }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy") }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = uiState.comparisonData!!, fontSize = 14.sp, lineHeight = 20.sp)
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DataCollectionView(
    viewModel: MainViewModel,
    context: android.content.Context,
    dataViewModel: com.example.steeldefectdetector.ui.datacollection.DataCollectionViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentMode by dataViewModel.currentMode.collectAsState()
    val annotations by dataViewModel.annotations.collectAsState()
    val isExporting by dataViewModel.isExporting.collectAsState()

    var selectedClassId by remember { mutableStateOf(0) }
    var selectedClassName by remember { mutableStateOf("冲孔") }

    LaunchedEffect(Unit) {
        dataViewModel.exportEvent.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        uri?.let { viewModel.loadAnnotationImageFromUri(context, it) }
    }
    val photoFile = remember {
        try {
            val storageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            java.io.File.createTempFile("ANNOTATE_${System.currentTimeMillis()}_", ".jpg", storageDir)
        } catch (e: Exception) { null }
    }
    val photoUri = remember(photoFile) {
        photoFile?.let { androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
    }
    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { success ->
        if (success && photoFile != null) viewModel.loadAnnotationImageFromFile(context, photoFile) else viewModel.showMessage("拍照失败")
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) photoUri?.let { cameraLauncher.launch(it) } else viewModel.showMessage("需要相机权限")
    }

    val defectClasses = listOf(
        "chongkong" to "冲孔",
        "hanfeng" to "焊缝",
        "yueyawan" to "月牙弯",
        "shuiban" to "水斑",
        "youban" to "油斑",
        "siban" to "丝斑",
        "yiwu" to "异物",
        "yahen" to "压痕",
        "zhehen" to "折痕",
        "yaozhe" to "腰折"
    )

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("人工标注模式", fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
            Row {
                Button(
                    onClick = { dataViewModel.switchMode(com.example.steeldefectdetector.model.annotation.AnnotationMode.VIEW_PAN_ZOOM) },
                    modifier = Modifier.padding(end = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentMode == com.example.steeldefectdetector.model.annotation.AnnotationMode.VIEW_PAN_ZOOM) com.example.steeldefectdetector.ui.theme.PrimaryBlue else com.example.steeldefectdetector.ui.theme.SurfaceLight,
                        contentColor = if (currentMode == com.example.steeldefectdetector.model.annotation.AnnotationMode.VIEW_PAN_ZOOM) com.example.steeldefectdetector.ui.theme.SurfaceWhite else com.example.steeldefectdetector.ui.theme.OnSurfaceVariant
                    )
                ) { Text("🔍 拖拽", maxLines = 1) }

                Button(
                    onClick = { dataViewModel.switchMode(com.example.steeldefectdetector.model.annotation.AnnotationMode.DRAW_BBOX) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentMode == com.example.steeldefectdetector.model.annotation.AnnotationMode.DRAW_BBOX) com.example.steeldefectdetector.ui.theme.PrimaryBlue else com.example.steeldefectdetector.ui.theme.SurfaceLight,
                        contentColor = if (currentMode == com.example.steeldefectdetector.model.annotation.AnnotationMode.DRAW_BBOX) com.example.steeldefectdetector.ui.theme.SurfaceWhite else com.example.steeldefectdetector.ui.theme.OnSurfaceVariant
                    )
                ) { Text("✏️ 绘制", maxLines = 1) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("当前绘制标签类别 (单选)", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp), maxLines = 1)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(defectClasses.size) { index ->
                val (enName, cnName) = defectClasses[index]
                val isSelected = selectedClassId == index
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedClassId = index
                        selectedClassName = cnName
                    },
                    label = { Text(cnName, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = com.example.steeldefectdetector.ui.theme.PrimaryBlue,
                        selectedLabelColor = com.example.steeldefectdetector.ui.theme.SurfaceWhite,
                        containerColor = com.example.steeldefectdetector.ui.theme.SurfaceLight,
                        labelColor = com.example.steeldefectdetector.ui.theme.OnSurfaceVariant
                    ),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth().height(400.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                uiState.annotationImage?.let { bitmap ->
                    com.example.steeldefectdetector.ui.datacollection.components.InteractiveAnnotationCanvas(
                        bitmap = bitmap.asImageBitmap(),
                        mode = currentMode,
                        annotations = annotations,
                        currentLabelId = selectedClassId,
                        currentLabelName = selectedClassName,
                        onAddAnnotation = { dataViewModel.addAnnotation(it) }
                    )
                } ?: run {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Crop, contentDescription = "Draw Box", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("请导入图片后进行标注", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), maxLines = 1)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { dataViewModel.clearLastAnnotation() }, enabled = annotations.isNotEmpty()) {
                Text("撤销上一个框", maxLines = 1)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                onClick = { if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) photoUri?.let { cameraLauncher.launch(it) } else permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp), enabled = photoUri != null
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("拍照", maxLines = 1)
            }
            OutlinedButton(
                onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("选择图片", maxLines = 1)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 【UI 1】：底部操作按钮引入 FlowRow 防止硬换行，加入 maxLines=1
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val path = context.getExternalFilesDir(null)?.absolutePath + "/yolo_dataset"
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("DatasetPath", path)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(context, "路径已复制: $path", android.widget.Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("📁 目录", maxLines = 1) }

            OutlinedButton(
                onClick = { dataViewModel.clearAllAnnotations() },
                modifier = Modifier.height(50.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = annotations.isNotEmpty()
            ) { Text("清空", maxLines = 1) }

            Button(
                onClick = {
                    uiState.annotationImage?.let { bitmap ->
                        dataViewModel.saveToDataset(context, bitmap.asImageBitmap())
                    }
                },
                modifier = Modifier.height(50.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isExporting && annotations.isNotEmpty() && uiState.annotationImage != null
            ) {
                if (isExporting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("保存 (${annotations.size})", maxLines = 1)
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun NoDefectResultItem() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = com.example.steeldefectdetector.ui.theme.SuccessGreen.copy(alpha = 0.1f),
            contentColor = com.example.steeldefectdetector.ui.theme.OnSurface
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "无缺陷",
                modifier = Modifier.size(48.dp),
                tint = com.example.steeldefectdetector.ui.theme.SuccessGreen
            )
            Text(
                text = "✅ 未检测到缺陷",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = com.example.steeldefectdetector.ui.theme.OnSurface
            )
            Text(
                text = "该图片经模型检测，未发现钢材表面缺陷",
                fontSize = 14.sp,
                color = com.example.steeldefectdetector.ui.theme.OnSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun DetectionResultItem(result: DetectionResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = com.example.steeldefectdetector.ui.theme.SurfaceLight,
            contentColor = com.example.steeldefectdetector.ui.theme.OnSurface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = result.getChineseName(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = when (result.getSeverity()) {
                        "严重" -> MaterialTheme.colorScheme.error
                        "中等" -> MaterialTheme.colorScheme.primary
                        "轻微" -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        result.confidence >= 0.8 -> MaterialTheme.colorScheme.primaryContainer
                        result.confidence >= 0.6 -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    }
                ) {
                    Text(
                        text = "${(result.confidence * 100).toInt()}%",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            result.confidence >= 0.8 -> MaterialTheme.colorScheme.onPrimaryContainer
                            result.confidence >= 0.6 -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onTertiaryContainer
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "位置: (${result.x1.toInt()}, ${result.y1.toInt()}) - (${result.x2.toInt()}, ${result.y2.toInt()})", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "严重程度: ${result.getSeverity()}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 14.sp)
            if (result.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = result.description, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), fontSize = 14.sp)
            }
        }
    }
}