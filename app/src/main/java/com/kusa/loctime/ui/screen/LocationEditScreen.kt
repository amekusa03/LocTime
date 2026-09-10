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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.kusa.loctime.R
import com.kusa.loctime.data.entity.LocationEntity
import com.kusa.loctime.data.entity.TimeEntryEntity
import com.kusa.loctime.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationEditScreen(
    locationId: Int,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var currentLocationId by remember { mutableIntStateOf(locationId) }
    val isNew = currentLocationId == -1
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("200") }
    var offsetMinutes by remember { mutableFloatStateOf(0f) }
    
    var originalName by remember { mutableStateOf("") }
    var originalLat by remember { mutableStateOf("") }
    var originalLon by remember { mutableStateOf("") }
    var originalRadius by remember { mutableStateOf("200") }
    var originalOffset by remember { mutableIntStateOf(0) }
    
    var gettingLocation by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val isDirty = if (isNew) {
        name.isNotBlank() || lat.isNotBlank() || lon.isNotBlank() || offsetMinutes != 0f
    } else {
        name != originalName || lat != originalLat || lon != originalLon || radius != originalRadius || offsetMinutes.roundToInt() != originalOffset
    }
    
    var address by remember { mutableStateOf("") }
    var addressSearching by remember { mutableStateOf(false) }
    var addressError by remember { mutableStateOf("") }

    val locations by viewModel.locations.collectAsState()
    LaunchedEffect(locations) {
        if (!isNew && name.isEmpty()) {
            locations.find { it.id == currentLocationId }?.let {
                name = it.name
                lat = it.latitude.toString()
                lon = it.longitude.toString()
                radius = it.radiusMeters.toInt().toString()
                offsetMinutes = it.offsetMinutes.toFloat()
                
                originalName = it.name
                originalLat = it.latitude.toString()
                originalLon = it.longitude.toString()
                originalRadius = it.radiusMeters.toInt().toString()
                originalOffset = it.offsetMinutes
            }
        }
    }

    val timeEntries by viewModel.getTimeEntriesFlow(currentLocationId).collectAsState(emptyList())
    var showTimeDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<TimeEntryEntity?>(null) }

    BackHandler(enabled = isDirty) {
        showDiscardDialog = true
    }

    fun saveAndBack() {
        val latD = lat.toDoubleOrNull() ?: return
        val lonD = lon.toDoubleOrNull() ?: return
        val radF = radius.toFloatOrNull() ?: 200f
        val entity = LocationEntity(
            id = if (currentLocationId == -1) 0 else currentLocationId,
            name = name.trim(),
            latitude = latD,
            longitude = lonD,
            radiusMeters = radF,
            offsetMinutes = offsetMinutes.roundToInt()
        )
        viewModel.saveLocation(entity) { onBack() }
    }

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
                    addressError = context.getString(R.string.address_not_found)
                }
            } catch (e: Exception) {
                addressError = context.getString(R.string.search_failed)
            } finally {
                addressSearching = false
            }
        }
    }

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
                title = { Text(if (isNew) stringResource(R.string.add_location) else stringResource(R.string.edit_location)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isDirty) showDiscardDialog = true else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                    stringResource(R.string.location_info),
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
                            label = { Text(stringResource(R.string.location_name)) },
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
                                label = { Text(stringResource(R.string.address)) },
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
                                    Text(stringResource(R.string.search))
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
                                label = { Text(stringResource(R.string.latitude)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = lon,
                                onValueChange = { lon = it },
                                label = { Text(stringResource(R.string.longitude)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                        OutlinedTextField(
                            value = radius,
                            onValueChange = { radius = it },
                            label = { Text(stringResource(R.string.radius_m)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        // オフセット設定
                        Column {
                            val offsetInt = offsetMinutes.roundToInt()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.offset_time), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = if (offsetInt == 0) stringResource(R.string.no_offset) else stringResource(R.string.offset_minutes_fmt, if (offsetInt > 0) "+" else "", offsetInt),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    color = if (offsetInt == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = offsetMinutes,
                                onValueChange = { offsetMinutes = it },
                                valueRange = -30f..30f,
                                steps = 60
                            )
                            Text(
                                if (offsetInt < 0) stringResource(R.string.offset_desc_before, -offsetInt) else if (offsetInt > 0) stringResource(R.string.offset_desc_after, offsetInt) else stringResource(R.string.offset_desc_exact),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        FilledTonalButton(
                            onClick = { onGetCurrentLocation() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !gettingLocation
                        ) {
                            if (gettingLocation) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(stringResource(R.string.get_current_location))
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
                            stringResource(R.string.time_settings),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalIconButton(
                            onClick = {
                                if (isNew) {
                                    val latD = lat.toDoubleOrNull() ?: return@FilledTonalIconButton
                                    val lonD = lon.toDoubleOrNull() ?: return@FilledTonalIconButton
                                    val radF = radius.toFloatOrNull() ?: 200f
                                    val entity = LocationEntity(0, name.trim(), latD, lonD, radF, offsetMinutes.roundToInt())
                                    viewModel.saveLocation(entity) { savedId ->
                                        currentLocationId = savedId.toInt()
                                        originalName = name
                                        originalLat = lat
                                        originalLon = lon
                                        originalRadius = radius
                                        originalOffset = offsetMinutes.roundToInt()
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
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_time))
                        }
                    }
                    if (timeEntries.size >= 10) {
                        Text(
                            stringResource(R.string.time_limit_warning),
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
                        Text(stringResource(R.string.save))
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
            title = { Text(stringResource(R.string.discard_dialog_title)) },
            text = { Text(stringResource(R.string.discard_dialog_message)) },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onBack() }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text(stringResource(R.string.cancel)) }
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
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
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
        title = { Text(if (entry == null) stringResource(R.string.add_time) else stringResource(R.string.edit_time)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeInput(state = timePickerState)
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(stringResource(R.string.notification_message)) },
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
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
