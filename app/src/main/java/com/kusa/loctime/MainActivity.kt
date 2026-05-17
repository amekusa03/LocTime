package com.kusa.loctime

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kusa.loctime.service.NotificationHelper
import com.kusa.loctime.ui.screen.LocationEditScreen
import com.kusa.loctime.ui.screen.LocationListScreen
import com.kusa.loctime.ui.theme.LocTimeTheme
import com.kusa.loctime.ui.viewmodel.MainViewModel

// アプリのエントリポイント。Navigation Compose で画面遷移を管理する。
// 画面構成:
//   "locations"       → LocationListScreen（場所一覧）
//   "location/{id}"  → LocationEditScreen（場所編集）  id=-1 なら新規追加
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Android 13以上では通知権限（POST_NOTIFICATIONS）の実行時リクエストが必要
    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 通知チャンネルをアプリ起動時に作成する（Android 8.0以上で必須）
        NotificationHelper.createChannel(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            LocTimeTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "locations") {
                    composable("locations") {
                        LocationListScreen(
                            viewModel = viewModel,
                            onLocationClick = { id -> navController.navigate("location/$id") },
                            onAddClick = { navController.navigate("location/-1") }
                        )
                    }
                    composable("location/{id}") { backStack ->
                        val id = backStack.arguments?.getString("id")?.toIntOrNull() ?: -1
                        LocationEditScreen(
                            locationId = id,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
