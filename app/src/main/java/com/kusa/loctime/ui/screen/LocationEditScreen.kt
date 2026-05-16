package com.kusa.loctime.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.kusa.loctime.data.entity.LocationEntity
import com.kusa.loctime.data.entity.TimeEntryEntity
import com.kusa.loctime.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationEditScreen(
    locationId: Int,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val isNew = locationId == -1
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("200") }
    var savedLocationId by remember { mutableIntStateOf(locationId) }
    var locationSaved by remember { mutableStateOf(!isNew) }
    var gettingLocation by remember { mutableStateOf(false) }

    val locations by viewModel.locations.collectAsState()
    LaunchedEffect(locations) {
        if (!isNew && name.isEmpty()) {
            locations.find { it.id == locationId }?.let {
                name = it.name
                lat = it.latitude.toString()
                lon = it.longitude.toString()
                radius = it.radiusMeters.toInt().toString()
            }
        }
    }

    val timeEntries by viewModel.getTimeEntriesFlow(savedLocationId).collectAsState(emptyList())
    var showTimeDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<TimeEntryEntity?>(null) }

    fun fetchCurrentLocation() {
        gettingLocation = true
        scope.launch {
            try {
                val client = LocationServices.getFusedLocationProviderClient(context)
                val cts = CancellationTokenSource()
                val loc = client.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token
                ).await()
                loc?.let { lat = it.latitude.toString(); lon = it.longitude.toString() }
            } catch (e: SecurityException) {
                // 権限なし
            } finally {
                gettingLocation = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) fetchCurrentLocation()
    }

    fun onGetCurrentLocation() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) fetchCurrentLocation()
        else permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "場所を追加" else "場所を編集") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("場所情報", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
            }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("場所名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lat,
                        onValueChange = { lat = it },
                        label = { Text("緯度") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = lon,
                        onValueChange = { lon = it },
                        label = { Text("経度") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = radius,
                    onValueChange = { radius = it },
                    label = { Text("半径 (m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onGetCurrentLocation() },
                        modifier = Modifier.weight(1f),
                        enabled = !gettingLocation
                    ) {
                        if (gettingLocation) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("現在地を取得")
                    }
                    Button(
                        onClick = {
                            val latD = lat.toDoubleOrNull() ?: return@Button
                            val lonD = lon.toDoubleOrNull() ?: return@Button
                            val radF = radius.toFloatOrNull() ?: 200f
                            val entity = LocationEntity(
                                id = if (isNew) 0 else locationId,
                                name = name.trim(),
                                latitude = latD,
                                longitude = lonD,
                                radiusMeters = radF
                            )
                            viewModel.saveLocation(entity) { newId ->
                                if (isNew && !locationSaved) {
                                    savedLocationId = newId.toInt()
                                    locationSaved = true
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = name.isNotBlank() && lat.isNotBlank() && lon.isNotBlank()
                    ) {
                        Text("保存")
                    }
                }
            }

            if (locationSaved) {
                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "時刻設定",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { editingEntry = null; showTimeDialog = true },
                            enabled = timeEntries.size < 5
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "時刻を追加")
                        }
                    }
                    if (timeEntries.size >= 5) {
                        Text(
                            "時刻は最大5件まで",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                items(timeEntries, key = { it.id }) { entry ->
                    TimeEntryItem(
                        entry = entry,
                        onToggle = { viewModel.saveTimeEntry(entry.copy(isEnabled = !entry.isEnabled)) },
                        onEdit = { editingEntry = entry; showTimeDialog = true },
                        onDelete = { viewModel.deleteTimeEntry(entry) }
                    )
                }
            }
        }
    }

    if (showTimeDialog) {
        TimeEntryDialog(
            entry = editingEntry,
            locationId = savedLocationId,
            onDismiss = { showTimeDialog = false },
            onSave = { entry ->
                viewModel.saveTimeEntry(entry)
                showTimeDialog = false
            }
        )
    }
}

@Composable
private fun TimeEntryItem(
    entry: TimeEntryEntity,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "%02d:%02d".format(entry.hour, entry.minute),
                    style = MaterialTheme.typography.titleMedium
                )
                if (entry.message.isNotBlank()) {
                    Text(entry.message, style = MaterialTheme.typography.bodySmall)
                }
            }
            Switch(checked = entry.isEnabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "削除")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeEntryDialog(
    entry: TimeEntryEntity?,
    locationId: Int,
    onDismiss: () -> Unit,
    onSave: (TimeEntryEntity) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = entry?.hour ?: 8,
        initialMinute = entry?.minute ?: 0,
        is24Hour = true
    )
    var message by remember { mutableStateOf(entry?.message ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry == null) "時刻を追加" else "時刻を編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeInput(state = timePickerState)
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("通知メッセージ") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    TimeEntryEntity(
                        id = entry?.id ?: 0,
                        locationId = locationId,
                        hour = timePickerState.hour,
                        minute = timePickerState.minute,
                        message = message.trim(),
                        isEnabled = entry?.isEnabled ?: true
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}
