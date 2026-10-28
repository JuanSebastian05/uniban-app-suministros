package co.edu.udea.uniban.suministros.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.udea.uniban.suministros.data.InventoryRepository
import co.edu.udea.uniban.suministros.data.local.HomeRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeRecordsState(
    val loading: Boolean = true,
    val records: List<HomeRecord> = emptyList(),
    val error: Boolean = false,
)

class HomeViewModel(repository: InventoryRepository, connectivity: Flow<Boolean?>) : ViewModel() {
    val records = repository.homeRecords.map { HomeRecordsState(loading = false, records = it) }
        .catch { emit(HomeRecordsState(loading = false, error = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeRecordsState())
    val online = connectivity.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
