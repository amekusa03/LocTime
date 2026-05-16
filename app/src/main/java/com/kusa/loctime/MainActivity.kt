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

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
