package com.waymark.ui.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.waymark.data.repo.TripRepository
import com.waymark.domain.model.Traveler
import com.waymark.ui.components.EmptyLine
import com.waymark.ui.components.Footnote
import com.waymark.ui.components.Panel
import com.waymark.ui.components.PartyMark
import com.waymark.ui.components.ScreenScaffold
import com.waymark.ui.theme.Waymark
import com.waymark.ui.theme.WaymarkSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class Person(val traveler: Traveler, val trips: Int)

@HiltViewModel
class PeopleViewModel @Inject constructor(private val trips: TripRepository) : ViewModel() {

    val people: StateFlow<List<Person>?> = combine(
        trips.observeAllTravelers(),
        trips.observeTripCounts(),
    ) { travelers, counts ->
        travelers.map { Person(it, counts[it.id] ?: 0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(traveler: Traveler) {
        viewModelScope.launch { trips.saveTraveler(traveler) }
    }
}

/**
 * Everyone you travel with, across every trip.
 *
 * A traveler is one person however many trips they are on, so their seat and
 * meal preferences, loyalty numbers and phone number are set once, here or
 * from any trip's party, and every trip sees the change. Somebody on no trip
 * at all is not kept: removing a person from their last trip removes them.
 */
@Composable
fun PeopleScreen(viewModel: PeopleViewModel, onBack: () -> Unit) {
    val people by viewModel.people.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val colors = Waymark.colors

    ScreenScaffold(title = "People", onBack = onBack, spacing = WaymarkSpacing.small) {
        val list = people
        when {
            list == null -> EmptyLine("Reading…")

            list.isEmpty() -> EmptyLine("Nobody yet. Travelers are added from a trip's Party tab.")

            else -> {
                list.forEach { person ->
                    Panel(onClick = { editing = person.traveler.id }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(WaymarkSpacing.small),
                        ) {
                            PartyMark(initials = person.traveler.initials, size = 28.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = person.traveler.fullName,
                                    style = Waymark.type.bodySmall,
                                    color = colors.textBody,
                                )
                                Text(
                                    text = listOfNotNull(
                                        if (person.trips == 1) "1 trip" else "${person.trips} trips",
                                        profileSummary(person.traveler)?.takeIf { it.isNotBlank() },
                                    ).joinToString(" · "),
                                    style = Waymark.type.hint,
                                    color = colors.textDim,
                                )
                            }
                        }
                    }
                }
                Footnote("A change here shows on every trip the person is on.")
            }
        }
    }

    val traveler = people?.firstOrNull { it.traveler.id == editing }?.traveler
    if (traveler != null) {
        TravelerProfileModal(
            traveler = traveler,
            onDismiss = { editing = null },
            onSave = {
                viewModel.save(it)
                editing = null
            },
        )
    }
}
