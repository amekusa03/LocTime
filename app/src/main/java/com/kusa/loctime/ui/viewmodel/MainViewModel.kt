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

// アプリ全体の状態管理とビジネスロジックを担うViewModel。
// 画面回転などの構成変更でも状態が保持される。
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = LocationRepository(AppDatabase.getInstance(application))

    // 登録済み場所リスト。WhileSubscribed(5000) で画面非表示から5秒後にFlowの収集を停止し省電力化。
    val locations: StateFlow<List<LocationEntity>> = repo.getLocationsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 指定した場所IDに紐づく時刻リストをFlowで返す。Composeの collectAsState() と組み合わせて使う。
    fun getTimeEntriesFlow(locationId: Int): Flow<List<TimeEntryEntity>> =
        repo.getTimeEntriesFlow(locationId)

    // 場所を保存し、完了時に onResult(保存されたID) を呼び出す。
    fun saveLocation(location: LocationEntity, onResult: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.saveLocation(location)
            onResult(id)
        }
    }

    // 場所を削除する。削除前に関連する有効なアラームをすべてキャンセルする。
    // DBの CASCADE により time_entries は自動削除される。
    fun deleteLocation(location: LocationEntity) {
        viewModelScope.launch {
            val entries = repo.getAllEnabledEntries().filter { it.locationId == location.id }
            entries.forEach { AlarmScheduler.cancel(getApplication(), it) }
            repo.deleteLocation(location)
        }
    }

    // 時刻エントリを保存し、有効なら翌日分のアラームをセット、無効ならキャンセルする。
    fun saveTimeEntry(entry: TimeEntryEntity) {
        viewModelScope.launch {
            val savedId = repo.saveTimeEntry(entry)
            // 新規保存の場合（id=0）は採番されたIDを使ってアラームを登録する
            val saved = entry.copy(id = if (entry.id == 0) savedId.toInt() else entry.id)
            if (saved.isEnabled) {
                AlarmScheduler.schedule(getApplication(), saved)
            } else {
                AlarmScheduler.cancel(getApplication(), saved)
            }
        }
    }

    // 時刻エントリを削除し、対応するアラームもキャンセルする。
    fun deleteTimeEntry(entry: TimeEntryEntity) {
        viewModelScope.launch {
            AlarmScheduler.cancel(getApplication(), entry)
            repo.deleteTimeEntry(entry)
        }
    }
}
