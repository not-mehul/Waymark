package com.waymark.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.waymark.BuildConfig
import com.waymark.data.share.BookingText
import com.waymark.di.AppContainer
import com.waymark.di.Inbox
import com.waymark.ui.add.AddFlightScreen
import com.waymark.ui.add.AddFlightViewModel
import com.waymark.ui.add.FlightDraft
import com.waymark.ui.analytics.AnalyticsScreen
import com.waymark.ui.components.ProvideReminderPermission
import com.waymark.ui.insights.InsightsScreen
import com.waymark.ui.map.MapScreen
import com.waymark.ui.people.PeopleScreen
import com.waymark.ui.people.PeopleViewModel
import com.waymark.ui.segment.SegmentScreen
import com.waymark.ui.share.ImportModal
import com.waymark.ui.share.ProblemModal
import com.waymark.ui.share.ShareViewModel
import com.waymark.ui.share.SharedFlightsModal
import com.waymark.ui.trip.TripScreen
import com.waymark.ui.trip.TripViewModel
import com.waymark.ui.trips.TripsScreen
import com.waymark.ui.trips.TripsViewModel
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.Serializable

/**
 * Where the app can be, as types rather than strings: a destination is a
 * class, its arguments are its fields, and a typo is a compile error rather
 * than a screen that opens with an empty id.
 *
 * Every screen of one trip lives inside [TripGraph], which owns the trip's
 * id and its one [TripViewModel]. The screens inside take the id from the
 * graph rather than each carrying a copy.
 */
object Routes {
    @Serializable
    data object Shelf

    @Serializable
    data object People

    @Serializable
    data class TripGraph(val tripId: String)

    @Serializable
    data object TripHome

    /** Add flight, optionally started from a flight read elsewhere. Dates and times as ISO text. */
    @Serializable
    data class AddFlight(
        val designator: String? = null,
        val date: String? = null,
        val origin: String? = null,
        val destination: String? = null,
        val departs: String? = null,
        val arrives: String? = null,
        val reference: String? = null,
    ) {
        fun draft(): FlightDraft? = if (designator == null && origin == null) {
            null
        } else {
            FlightDraft(
                designator = designator,
                date = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                origin = origin,
                destination = destination,
                departs = departs?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
                arrives = arrives?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
                confirmationCode = reference,
            )
        }

        companion object {
            fun of(found: BookingText.FoundFlight, reference: String?) = AddFlight(
                designator = found.designator,
                date = found.date?.toString(),
                origin = found.origin,
                destination = found.destination,
                departs = found.departs?.toString(),
                arrives = found.arrives?.toString(),
                reference = reference,
            )
        }
    }

    @Serializable
    data class Booking(val segmentId: String)

    @Serializable
    data object Insights

    @Serializable
    data object Numbers

    @Serializable
    data object TripMap
}

@Composable
fun WaymarkApp(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) = ProvideReminderPermission {
    val share: ShareViewModel = viewModel(factory = factory { ShareViewModel(container.tripRepository) })

    /** Into a trip, from anywhere: one trip graph on the stack at a time, above the shelf. */
    fun openTrip(tripId: String, then: Any? = null) {
        navController.navigate(Routes.TripGraph(tripId)) { popUpTo(Routes.Shelf) }
        then?.let { navController.navigate(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Routes.Shelf) {
            composable<Routes.Shelf> {
                val viewModel: TripsViewModel = viewModel(
                    factory = factory { TripsViewModel(container.tripRepository, container.seeder) }
                )
                val context = LocalContext.current
                TripsScreen(
                    viewModel = viewModel,
                    versionName = BuildConfig.VERSION_NAME,
                    onOpenTrip = { openTrip(it) },
                    onOpenPeople = { navController.navigate(Routes.People) },
                    onOpenFile = { uri: Uri -> share.openFile(context, uri) },
                    onOpenCode = share::openCode,
                )
            }

            composable<Routes.People> {
                val viewModel: PeopleViewModel = viewModel(
                    factory = factory { PeopleViewModel(container.tripRepository) }
                )
                PeopleScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }

            navigation<Routes.TripGraph>(startDestination = Routes.TripHome) {
                composable<Routes.TripHome> { entry ->
                    val trip = tripViewModel(navController, entry, container)
                    TripScreen(
                        viewModel = trip,
                        onBack = { navController.popBackStack(Routes.Shelf, inclusive = false) },
                        onAddFlight = { navController.navigate(Routes.AddFlight()) },
                        onOpenSegment = { navController.navigate(Routes.Booking(it)) },
                        onOpenInsights = { navController.navigate(Routes.Insights) },
                        onOpenAnalytics = { navController.navigate(Routes.Numbers) },
                        onOpenMap = { navController.navigate(Routes.TripMap) },
                        segmentPane = { segmentId, onClose ->
                            SegmentScreen(segmentId = segmentId, viewModel = trip, onBack = onClose)
                        },
                    )
                }

                composable<Routes.AddFlight> { entry ->
                    val tripId = tripIdOf(navController, entry)
                    val route = entry.toRoute<Routes.AddFlight>()
                    val viewModel: AddFlightViewModel = viewModel(
                        factory = factory {
                            AddFlightViewModel(
                                tripId = tripId,
                                trips = container.tripRepository,
                                initial = route.draft(),
                            )
                        }
                    )
                    AddFlightScreen(viewModel = viewModel, onDone = { navController.popBackStack() })
                }

                composable<Routes.Booking> { entry ->
                    SegmentScreen(
                        segmentId = entry.toRoute<Routes.Booking>().segmentId,
                        viewModel = tripViewModel(navController, entry, container),
                        onBack = { navController.popBackStack() },
                    )
                }

                composable<Routes.Numbers> { entry ->
                    AnalyticsScreen(
                        viewModel = tripViewModel(navController, entry, container),
                        onBack = { navController.popBackStack() },
                    )
                }

                composable<Routes.TripMap> { entry ->
                    MapScreen(
                        viewModel = tripViewModel(navController, entry, container),
                        onBack = { navController.popBackStack() },
                        onOpenSegment = { navController.navigate(Routes.Booking(it)) },
                    )
                }

                composable<Routes.Insights> { entry ->
                    InsightsScreen(
                        viewModel = tripViewModel(navController, entry, container),
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }

        Incoming(share, onOpenTrip = { openTrip(it) }, onCheckFlight = { tripId, route -> openTrip(tripId, route) })
    }

    // Whatever arrived from outside — a tapped reminder, a shared email, a
    // file — is taken once and acted on here, above every screen.
    val request by container.inbox.requests.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(request) {
        when (val taken = container.inbox.take()) {
            is Inbox.Request.OpenBooking -> openTrip(taken.tripId, Routes.Booking(taken.segmentId))
            is Inbox.Request.SharedText -> share.openText(taken.text)
            is Inbox.Request.ImportFile -> share.openFile(context, taken.uri.toUri())
            is Inbox.Request.ImportCode -> share.openCode(taken.payload)
            null -> Unit
        }
    }
}

/** The sheet for whatever is waiting to be looked at, if anything is. */
@Composable
private fun Incoming(
    share: ShareViewModel,
    onOpenTrip: (String) -> Unit,
    onCheckFlight: (tripId: String, Routes.AddFlight) -> Unit,
) {
    val incoming by share.incoming.collectAsStateWithLifecycle()
    val busy by share.busy.collectAsStateWithLifecycle()
    val trips by share.openTrips.collectAsStateWithLifecycle()

    when (val shown = incoming) {
        is ShareViewModel.Incoming.Archive -> ImportModal(
            archive = shown,
            busy = busy,
            onImport = { share.importArchive { tripId -> tripId?.let(onOpenTrip) } },
            onDismiss = share::dismiss,
        )

        is ShareViewModel.Incoming.Flights -> SharedFlightsModal(
            flights = shown,
            trips = trips,
            busy = busy,
            onAddAll = { tripId -> share.addFlights(tripId) { onOpenTrip(tripId) } },
            onCheck = { tripId, flight ->
                share.dismiss()
                onCheckFlight(tripId, Routes.AddFlight.of(flight, shown.reading.reference))
            },
            onDismiss = share::dismiss,
        )

        is ShareViewModel.Incoming.Problem -> ProblemModal(problem = shown, onDismiss = share::dismiss)

        null -> Unit
    }
}

/** The trip graph's entry, which every screen of the trip shares. */
@Composable
private fun graphEntry(navController: NavHostController, entry: NavBackStackEntry): NavBackStackEntry =
    remember(entry) {
        val graph = requireNotNull(entry.destination.parent) { "A trip screen outside the trip graph" }
        navController.getBackStackEntry(graph.id)
    }

@Composable
private fun tripIdOf(navController: NavHostController, entry: NavBackStackEntry): String =
    graphEntry(navController, entry).toRoute<Routes.TripGraph>().tripId

/**
 * One view model per open trip, shared by every screen inside it — owned by
 * the trip graph's entry, so the booking screen, the map and the numbers read
 * the same dossier the timeline does instead of each subscribing again.
 */
@Composable
private fun tripViewModel(
    navController: NavHostController,
    entry: NavBackStackEntry,
    container: AppContainer,
): TripViewModel {
    val owner = graphEntry(navController, entry)
    val tripId = owner.toRoute<Routes.TripGraph>().tripId
    return viewModel(viewModelStoreOwner = owner, factory = tripFactory(container, tripId))
}

private fun tripFactory(container: AppContainer, tripId: String): ViewModelProvider.Factory =
    factory {
        TripViewModel(
            tripId = tripId,
            trips = container.tripRepository,
            alerts = container.alertRepository,
            ideas = container.ideaRepository,
            reminders = container.reminderStore,
        )
    }

private inline fun <reified VM : ViewModel> factory(
    crossinline create: () -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer { create() }
}
