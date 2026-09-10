package com.kusa.loctime

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
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
                var showExactAlarmDialog by remember { mutableStateOf(false) }
                var showBackgroundLocationDialog by remember { mutableStateOf(false) }

                // 権限チェック
                LaunchedEffect(Unit) {
                    // Android 12以上での正確なアラーム権限チェック
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val alarmManager = getSystemService(AlarmManager::class.java)
                        if (!alarmManager.canScheduleExactAlarms()) {
                            showExactAlarmDialog = true
                        }
                    }

                    // Android 10以上でのバックグラウンド位置情報権限チェック
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val hasBackgroundLocation = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        
                        if (!hasBackgroundLocation) {
                            showBackgroundLocationDialog = true
                        }
                    }
                }

                if (showExactAlarmDialog) {
                    AlertDialog(
                        onDismissRequest = { showExactAlarmDialog = false },
                        title = { Text(stringResource(R.string.dialog_exact_alarm_title)) },
                        text = { Text(stringResource(R.string.dialog_exact_alarm_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                showExactAlarmDialog = false
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.fromParts("package", packageName, null)
                                }
                                startActivity(intent)
                            }) { Text(stringResource(R.string.open_settings)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExactAlarmDialog = false }) { Text(stringResource(R.string.cancel)) }
                        }
                    )
                }

                if (showBackgroundLocationDialog) {
                    AlertDialog(
                        onDismissRequest = { showBackgroundLocationDialog = false },
                        title = { Text(stringResource(R.string.dialog_bg_location_title)) },
                        text = { Text(stringResource(R.string.dialog_bg_location_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                showBackgroundLocationDialog = false
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", packageName, null)
                                }
                                startActivity(intent)
                            }) { Text(stringResource(R.string.open_settings)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBackgroundLocationDialog = false }) { Text(stringResource(R.string.cancel)) }
                        }
                    )
                }

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
