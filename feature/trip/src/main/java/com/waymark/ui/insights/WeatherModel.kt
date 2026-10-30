package com.waymark.ui.insights

import com.waymark.data.local.OnlineStore
import com.waymark.domain.logic.WeatherPlan
import com.waymark.domain.model.TripDossier
import com.waymark.domain.model.WeatherDay
import com.waymark.online.WeatherRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WeatherUiState(
    val online: Boolean = false,
    val stops: List<WeatherRepository.StopWeather> = emptyList(),
    val note: String? = null,
    val updating: Boolean = false,
) {
    /** Each day's weather, wherever the trip is that day. */
    val byDay: Map<LocalDate, WeatherDay> get() = stops.flatMap { it.days }.associateBy { it.date }
}

/**
 * The weather where the trip will be, day by day: kept on the phone, and
 * brought up to date when the traveler has let the app go online.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WeatherModel(
    private val scope: CoroutineScope,
    dossier: Flow<TripDossier?>,
    private val repository: WeatherRepository,
    private val online: OnlineStore,
) {
    private val note = MutableStateFlow<String?>(null)
    private val updating = MutableStateFlow(false)

    private val stops: Flow<List<WeatherPlan.Stop>> = dossier
        .map { trip -> trip?.let { WeatherPlan.stops(it, LocalDate.now()) }.orEmpty() }
        .distinctUntilChanged()

    val state: StateFlow<WeatherUiState> = combine(
        stops.flatMapLatest(repository::observe),
        online.enabled,
        note,
        updating,
    ) { weather, allowed, message, busy ->
        WeatherUiState(online = allowed, stops = weather, note = message, updating = busy)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), WeatherUiState())

    fun refresh() {
        scope.launch {
            val wanted = stops.first()
            if (wanted.isEmpty()) return@launch
            updating.value = true
            note.value = when (val outcome = repository.refresh(wanted, LocalDate.now(), System.currentTimeMillis())) {
                WeatherRepository.Outcome.Updated, WeatherRepository.Outcome.NotAllowed -> null

                is WeatherRepository.Outcome.Failed ->
                    "Could not update the weather (${outcome.reason}). What was fetched before is kept."
            }
            updating.value = false
        }
    }

    fun setOnline(enabled: Boolean) {
        scope.launch {
            online.set(enabled)
            if (enabled) refresh() else note.value = null
        }
    }
}
