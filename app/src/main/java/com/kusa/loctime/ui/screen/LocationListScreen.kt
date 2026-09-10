package com.kusa.loctime.ui.screen

import android.location.Location
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kusa.loctime.R
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
    val currentLocation by viewModel.currentLocation.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.updateCurrentLocation()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("LocTime", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)) },
                actions = {
                    IconButton(onClick = { 
                        isRefreshing = true
                        viewModel.updateCurrentLocation()
                        isRefreshing = false 
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh_location)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (locations.size < 10) {
                ExtendedFloatingActionButton(
                    onClick = onAddClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_location))
                }
            }
        }
    ) { padding ->
        if (locations.isEmpty()) {
            EmptyState(padding)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(locations, key = { it.id }) { location ->
                    LocationCard(
                        location = location,
                        currentLocation = currentLocation,
                        onClick = { onLocationClick(location.id) },
                        onDelete = { viewModel.deleteLocation(location) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                modifier = Modifier.size(120.dp)
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.padding(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                stringResource(R.string.empty_locations_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(R.string.empty_locations_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LocationCard(
    location: LocationEntity,
    currentLocation: Location?,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }
    val results = remember(currentLocation, location) {
        if (currentLocation != null) {
            val res = FloatArray(1)
            Location.distanceBetween(
                currentLocation.latitude, currentLocation.longitude,
                location.latitude, location.longitude,
                res
            )
            res[0]
        } else null
    }
    val isInRange = results?.let { it <= location.radiusMeters } ?: false
    
    val containerColor by animateColorAsState(
        targetValue = if (isInRange) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) 
                      else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        label = "cardColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = location.name,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text(stringResource(R.string.radius_fmt, location.radiusMeters.toInt())) },
                            icon = { Icon(Icons.Default.Info, null, Modifier.size(16.dp)) },
                            border = null,
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            )
                        )
                        if (location.offsetMinutes != 0) {
                            SuggestionChip(
                                onClick = {},
                                label = { 
                                    val off = location.offsetMinutes
                                    Text(stringResource(R.string.notify_fmt, if(off > 0) "+" else "", off)) 
                                },
                                icon = { Icon(Icons.Default.Notifications, null, Modifier.size(16.dp)) },
                                border = null,
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    labelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
                IconButton(
                    onClick = { showConfirm = true },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                }
            }

            AnimatedVisibility(visible = currentLocation != null) {
                currentLocation?.let { curr ->
                    val latDiff = curr.latitude - location.latitude
                    val lngDiff = curr.longitude - location.longitude
                    
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = if (isInRange) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (isInRange) Icons.Default.CheckCircle else Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.padding(8.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column {
                                Text(
                                    text = if (isInRange) stringResource(R.string.in_range) else stringResource(R.string.out_of_range),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isInRange) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.distance_fmt, results ?: 0f),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DiffInfoBox(
                                label = stringResource(R.string.lat_diff),
                                value = "%+.6f".format(latDiff),
                                modifier = Modifier.weight(1f)
                            )
                            DiffInfoBox(
                                label = stringResource(R.string.lng_diff),
                                value = "%+.6f".format(lngDiff),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.delete_dialog_title)) },
            text = { Text(stringResource(R.string.delete_dialog_message, location.name)) },
            confirmButton = {
                Button(
                    onClick = { onDelete(); showConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun DiffInfoBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
        border = null
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
        }
    }
}
