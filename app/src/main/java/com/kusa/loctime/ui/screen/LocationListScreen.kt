package com.kusa.loctime.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kusa.loctime.data.entity.LocationEntity
import com.kusa.loctime.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationListScreen(
    viewModel: MainViewModel,
    onLocationClick: (Int) -> Unit,
    onAddClick: () -> Unit
) {
    val locations by viewModel.locations.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("LocTime") }) },
        floatingActionButton = {
            if (locations.size < 10) {
                FloatingActionButton(onClick = onAddClick) {
                    Icon(Icons.Default.Add, contentDescription = "場所を追加")
                }
            }
        }
    ) { padding ->
        if (locations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("場所を追加してください", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(contentPadding = padding) {
                items(locations, key = { it.id }) { location ->
                    LocationItem(
                        location = location,
                        onClick = { onLocationClick(location.id) },
                        onDelete = { viewModel.deleteLocation(location) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun LocationItem(
    location: LocationEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(location.name) },
        supportingContent = {
            Text(
                "%.6f, %.6f  半径 ${location.radiusMeters.toInt()}m"
                    .format(location.latitude, location.longitude)
            )
        },
        trailingContent = {
            IconButton(onClick = { showConfirm = true }) {
                Icon(Icons.Default.Delete, contentDescription = "削除")
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("削除の確認") },
            text = { Text("「${location.name}」を削除しますか？\n関連する時刻設定もすべて削除されます。") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showConfirm = false }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("キャンセル") }
            }
        )
    }
}
