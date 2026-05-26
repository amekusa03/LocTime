package com.kusa.loctime.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.kusa.loctime.data.db.AppDatabase
import com.kusa.loctime.data.entity.LocationEntity
import com.kusa.loctime.data.entity.TimeEntryEntity
import com.kusa.loctime.data.repository.LocationRepository
import com.kusa.loctime.service.AlarmScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = LocationRepository(AppDatabase.getInstance(application))
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

    val locations: StateFlow<List<LocationEntity>> = repo.getLocationsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    @SuppressLint("MissingPermission")
    fun updateCurrentLocation() {
        viewModelScope.launch {
            try {
                val cts = CancellationTokenSource()
                val location = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cts.token
                ).await()
                _currentLocation.value = location
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getTimeEntriesFlow(locationId: Int): Flow<List<TimeEntryEntity>> =
        repo.getTimeEntriesFlow(locationId)

    fun saveLocation(location: LocationEntity, onResult: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.saveLocation(location)
            
            // 場所の設定（オフセット等）が変わった可能性があるため、紐づく全アラームを更新
            val entries = repo.getTimeEntriesFlow(location.id.takeIf { it != 0 } ?: id.toInt()).first()
            entries.forEach { entry ->
                if (entry.isEnabled) {
                    AlarmScheduler.schedule(getApplication(), entry, location.offsetMinutes)
                }
            }
            
            onResult(id)
        }
    }

    fun deleteLocation(location: LocationEntity) {
        viewModelScope.launch {
            val entries = repo.getAllEnabledEntries().filter { it.locationId == location.id }
            entries.forEach { AlarmScheduler.cancel(getApplication(), it) }
            repo.deleteLocation(location)
        }
    }

    fun saveTimeEntry(entry: TimeEntryEntity) {
        viewModelScope.launch {
            val savedId = repo.saveTimeEntry(entry)
            val saved = entry.copy(id = if (entry.id == 0) savedId.toInt() else entry.id)
            
            if (saved.isEnabled) {
                // 保存時、親となる場所のオフセット時間を取得してスケジュールに反映
                val location = repo.getLocationById(saved.locationId)
                AlarmScheduler.schedule(getApplication(), saved, location?.offsetMinutes ?: 0)
            } else {
                AlarmScheduler.cancel(getApplication(), saved)
            }
        }
    }

    fun deleteTimeEntry(entry: TimeEntryEntity) {
        viewModelScope.launch {
            AlarmScheduler.cancel(getApplication(), entry)
            repo.deleteTimeEntry(entry)
        }
    }
}
