package com.example.steeldefectdetector

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.steeldefectdetector.ui.ExportScreen
import com.example.steeldefectdetector.ui.HistoryScreen
import com.example.steeldefectdetector.ui.MainScreen
import com.example.steeldefectdetector.ui.MainViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object Export : Screen("export")
    object History : Screen("history")  // 新增历史记录页面
}

@Composable
fun SteelDefectDetectorApp(
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()
    
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route,
        modifier = modifier
    ) {
        composable(Screen.Main.route) {
            MainScreen(
                onNavigateToExport = {
                    navController.navigate(Screen.Export.route)
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                },
                viewModel = viewModel
            )
        }
        
        composable(Screen.Export.route) {
            ExportScreen(
                onBack = { navController.navigateUp() },
                viewModel = viewModel
            )
        }
        
        composable(Screen.History.route) {
            HistoryScreen(
                onBack = { navController.navigateUp() },
                onViewDetails = { history ->
                    // 选择历史记录，将在主页面显示对话框
                    viewModel.selectHistory(history)
                    // 导航回主页面显示详情
                    navController.navigateUp()
                },
                viewModel = viewModel
            )
        }
    }
}