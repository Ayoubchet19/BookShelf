package fr.estiam.traillog.bookshelf.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.estiam.traillog.bookshelf.data.BookRepository
import fr.estiam.traillog.bookshelf.domain.StatisticsSummary
import fr.estiam.traillog.bookshelf.domain.buildStatistics
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

/**
 * Exposes the statistics screen state as a [StateFlow]. The ViewModel survives rotation
 * and WhileSubscribed(5s) keeps the Room flows alive during the configuration change,
 * so the screen is not reloaded. All arithmetic lives in domain/ReadingStats.kt.
 */
class StatisticsViewModel(
    repository: BookRepository,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val today: () -> LocalDate = { LocalDate.now(zone()) }
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Ready(val summary: StatisticsSummary) : UiState
    }

    val state: StateFlow<UiState> = combine(repository.allBooks, repository.allSessions) { books, sessions ->
        UiState.Ready(buildStatistics(books, sessions, today(), zone()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    class Factory(private val repository: BookRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = StatisticsViewModel(repository) as T
    }
}
