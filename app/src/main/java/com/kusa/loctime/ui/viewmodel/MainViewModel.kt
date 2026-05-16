package com.kusa.loctime.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kusa.loctime.data.db.AppDatabase
import com.kusa.loctime.data.entity.LocationEntity
import com.kusa.loctime.data.entity.TimeEntryEntity
import com.kusa.loctime.data.repository.LocationRepository
import com.kusa.loctime.service.AlarmScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = LocationRepository(AppDatabase.getInstance(application))

    val locations: StateFlow<List<LocationEntity>> = repo.getLocationsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getTimeEntriesFlow(locationId: Int): Flow<List<TimeEntryEntity>> =
        repo.getTimeEntriesFlow(locationId)

    fun saveLocation(location: LocationEntity, onResult: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.saveLocation(location)
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
                AlarmScheduler.schedule(getApplication(), saved)
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
