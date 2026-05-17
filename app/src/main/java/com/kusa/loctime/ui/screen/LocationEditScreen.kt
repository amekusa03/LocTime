package com.kusa.loctime.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.activity.compose.BackHandler
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

// 場所の追加・編集画面。locationId=-1 なら新規追加、それ以外なら既存の場所を編集する。
// 画面の構成:
//   - 場所情報カード（場所名・住所検索・緯度経度・半径・現在地取得）
//   - 時刻設定セクション（場所情報が入力済みのとき表示）
//   - 保存ボタン
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationEditScreen(
    locationId: Int,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    // 新規作成時に「+」を押すと場所を自動保存してIDが確定するため、mutableState で管理する
    var currentLocationId by remember { mutableIntStateOf(locationId) }
    val isNew = currentLocationId == -1
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("200") }
    var originalName by remember { mutableStateOf("") }
    var originalLat by remember { mutableStateOf("") }
    var originalLon by remember { mutableStateOf("") }
    var originalRadius by remember { mutableStateOf("200") }
    var gettingLocation by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    // 未保存の変更があるかどうか。新規は何か入力していれば dirty、既存は初期値と差分があれば dirty とする。
    val isDirty = if (isNew) {
        name.isNotBlank() || lat.isNotBlank() || lon.isNotBlank()
    } else {
        name != originalName || lat != originalLat || lon != originalLon || radius != originalRadius
    }
    var address by remember { mutableStateOf("") }
    var addressSearching by remember { mutableStateOf(false) }
    var addressError by remember { mutableStateOf("") }

    // 既存の場所の場合、DBから取得した値をフォームに初期表示する。
    // name.isEmpty() チェックにより、初回のみ読み込んでユーザー入力を上書きしないようにする。
    val locations by viewModel.locations.collectAsState()
    LaunchedEffect(locations) {
        if (!isNew && name.isEmpty()) {
            locations.find { it.id == currentLocationId }?.let {
                name = it.name
                lat = it.latitude.toString()
                lon = it.longitude.toString()
                radius = it.radiusMeters.toInt().toString()
                originalName = it.name
                originalLat = it.latitude.toString()
                originalLon = it.longitude.toString()
                originalRadius = it.radiusMeters.toInt().toString()
            }
        }
    }

    val timeEntries by viewModel.getTimeEntriesFlow(currentLocationId).collectAsState(emptyList())
    var showTimeDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<TimeEntryEntity?>(null) }

    BackHandler(enabled = isDirty) {
        showDiscardDialog = true
    }

    // 入力内容を保存して前の画面に戻る。lat/lon が数値でない場合は何もしない。
    fun saveAndBack() {
        val latD = lat.toDoubleOrNull() ?: return
        val lonD = lon.toDoubleOrNull() ?: return
        val radF = radius.toFloatOrNull() ?: 200f
        val entity = LocationEntity(
            id = if (currentLocationId == -1) 0 else currentLocationId,
            name = name.trim(),
            latitude = latD,
            longitude = lonD,
            radiusMeters = radF
        )
        viewModel.saveLocation(entity) { onBack() }
    }

    // 住所文字列をジオコーダで緯度経度に変換してフォームに反映する。
    fun searchAddress() {
        addressSearching = true
        addressError = ""
        scope.launch {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val results = withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(address.trim(), 1)
                }
                if (!results.isNullOrEmpty()) {
                    lat = results[0].latitude.toString()
                    lon = results[0].longitude.toString()
                } else {
                    addressError = "住所が見つかりませんでした"
                }
            } catch (e: Exception) {
                addressError = "検索に失敗しました"
            } finally {
                addressSearching = false
            }
        }
    }

    // FusedLocationProvider でGPS位置情報を取得してフォームに反映する。
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
                // 権限なし（ここでは無視。権限リクエストは onGetCurrentLocation で行う）
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
                    IconButton(onClick = {
                        if (isDirty) showDiscardDialog = true else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "場所情報",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("場所名") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = address,
                                onValueChange = { address = it; addressError = "" },
                                label = { Text("住所") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                isError = addressError.isNotEmpty()
                            )
                            OutlinedButton(
                                onClick = { searchAddress() },
                                enabled = address.isNotBlank() && !addressSearching
                            ) {
                                if (addressSearching) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("検索")
                                }
                            }
                        }
                        if (addressError.isNotEmpty()) {
                            Text(
                                addressError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
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
                        OutlinedTextField(
                            value = radius,
                            onValueChange = { radius = it },
                            label = { Text("半径 (m)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        FilledTonalButton(
                            onClick = { onGetCurrentLocation() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !gettingLocation
                        ) {
                            if (gettingLocation) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("現在地を取得")
                        }
                    }
                }
            }

            val saveEnabled = name.isNotBlank() && lat.isNotBlank() && lon.isNotBlank()
            if (!isNew || saveEnabled) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "時刻設定",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalIconButton(
                            onClick = {
                                if (isNew) {
                                    // 新規場所はIDが未確定のため、時刻追加の前に場所を先に保存する。
                                    // 保存完了後に currentLocationId を更新し、ダイアログを開く。
                                    val latD = lat.toDoubleOrNull() ?: return@FilledTonalIconButton
                                    val lonD = lon.toDoubleOrNull() ?: return@FilledTonalIconButton
                                    val radF = radius.toFloatOrNull() ?: 200f
                                    val entity = LocationEntity(0, name.trim(), latD, lonD, radF)
                                    viewModel.saveLocation(entity) { savedId ->
                                        currentLocationId = savedId.toInt()
                                        // originalXxx を更新して isDirty が false になるようにする
                                        originalName = name
                                        originalLat = lat
                                        originalLon = lon
                                        originalRadius = radius
                                        editingEntry = null
                                        showTimeDialog = true
                                    }
                                } else {
                                    editingEntry = null
                                    showTimeDialog = true
                                }
                            },
                            enabled = timeEntries.size < 10
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "時刻を追加")
                        }
                    }
                    if (timeEntries.size >= 10) {
                        Text(
                            "時刻は最大10件まで",
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

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { saveAndBack() },
                        enabled = name.isNotBlank() && lat.isNotBlank() && lon.isNotBlank()
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }

    if (showTimeDialog) {
        TimeEntryDialog(
            entry = editingEntry,
            locationId = currentLocationId,
            onDismiss = { showTimeDialog = false },
            onSave = { entry ->
                viewModel.saveTimeEntry(entry)
                showTimeDialog = false
            }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("確認") },
            text = { Text("保存されていません。設定値を破棄しますか？") },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onBack() }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("いいえ") }
            }
        )
    }
}

// 時刻一覧の1行分のUI。カードをタップすると編集ダイアログが開く。
@Composable
private fun TimeEntryItem(
    entry: TimeEntryEntity,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "%02d:%02d".format(entry.hour, entry.minute),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
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

// 時刻の追加・編集ダイアログ。entry=null なら新規追加、それ以外なら既存の編集。
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
