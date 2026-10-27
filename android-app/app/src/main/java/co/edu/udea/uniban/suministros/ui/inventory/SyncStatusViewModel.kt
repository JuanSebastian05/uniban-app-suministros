package co.edu.udea.uniban.suministros.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.udea.uniban.suministros.data.InventoryRepository
import co.edu.udea.uniban.suministros.data.SyncIssue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class SyncStatusUiState(
    val loading: Boolean = true,
    val pendingCount: Int = 0,
    val syncingCount: Int = 0,
    val lastSuccessfulAtEpochMillis: Long? = null,
    val issues: List<SyncIssue> = emptyList(),
)

class SyncStatusViewModel(private val repository: InventoryRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(SyncStatusUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.pendingCount,
                repository.syncingCount,
                repository.lastSuccessfulSyncAt,
                repository.syncIssues,
            ) { pending, syncing, lastSuccessfulAt, issues ->
                SyncStatusUiState(
                    loading = false,
                    pendingCount = pending,
                    syncingCount = syncing,
                    lastSuccessfulAtEpochMillis = lastSuccessfulAt,
                    issues = issues,
                )
            }.collect { mutableState.value = it }
        }
    }

    fun retry() = repository.requestSync()
}
