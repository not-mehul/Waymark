package com.waymark.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.waymark.BuildConfig
import com.waymark.di.Inbox
import com.waymark.ui.add.AddFlightScreen
import com.waymark.ui.add.AddFlightViewModel
import com.waymark.ui.analytics.AnalyticsScreen
import com.waymark.ui.components.ProvideReminderPermission
import com.waymark.ui.insights.InsightsScreen
import com.waymark.ui.map.MapScreen
import com.waymark.ui.nav.Screen
import com.waymark.ui.nav.TripStores
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

/**
 * The app's navigation: a back stack of [Screen]s, owned here and edited as
 * the list it is, shown by [NavDisplay] with the system's predictive back.
 *
 * A screen that owns its state gets a view model of its own, scoped to its
 * entry; the screens of a trip share the trip's, through [TripStores].
 */
@Composable
fun WaymarkApp(inbox: Inbox) = ProvideReminderPermission {
    val backStack = rememberNavBackStack(Screen.Shelf)
    val host = checkNotNull(LocalViewModelStoreOwner.current) { "WaymarkApp needs a view-model store" }
    val tripStores: TripStores = viewModel()
    val share: ShareViewModel = hiltViewModel()

    // A trip's view model lives exactly as long as one of its screens is on
    // the stack.
    LaunchedEffect(backStack) {
        snapshotFlow { backStack.mapNotNullTo(mutableSetOf()) { (it as? Screen.OfTrip)?.tripId } }
            .collect(tripStores::keepOnly)
    }

    /** Into a trip, from anywhere: one trip on the stack at a time, above the shelf. */
    fun openTrip(tripId: String, then: Screen? = null) {
        backStack.popTo(Screen.Shelf)
        backStack.add(Screen.Trip(tripId))
        then?.let(backStack::add)
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            onBack = ::back,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<Screen.Shelf> {
                    val viewModel: TripsViewModel = hiltViewModel()
                    val context = LocalContext.current
                    TripsScreen(
                        viewModel = viewModel,
                        versionName = BuildConfig.VERSION_NAME,
                        onOpenTrip = { openTrip(it) },
                        onOpenPeople = { backStack.add(Screen.People) },
                        onOpenFile = { uri: Uri -> share.openFile(context, uri) },
                        onOpenCode = share::openCode,
                    )
                }

                entry<Screen.People> {
                    val viewModel: PeopleViewModel = hiltViewModel()
                    PeopleScreen(viewModel = viewModel, onBack = ::back)
                }

                entry<Screen.Trip> { key ->
                    val trip = tripViewModel(tripStores, host, key.tripId)
                    TripScreen(
                        viewModel = trip,
                        onBack = { backStack.popTo(Screen.Shelf) },
                        onAddFlight = { backStack.add(Screen.AddFlight(key.tripId)) },
                        onOpenSegment = { backStack.add(Screen.Booking(key.tripId, it)) },
                        onOpenInsights = { backStack.add(Screen.Insights(key.tripId)) },
                        onOpenAnalytics = { backStack.add(Screen.Numbers(key.tripId)) },
                        onOpenMap = { backStack.add(Screen.TripMap(key.tripId)) },
                        segmentPane = { segmentId, onClose ->
                            SegmentScreen(segmentId = segmentId, viewModel = trip, onBack = onClose)
                        },
                    )
                }

                entry<Screen.AddFlight> { key ->
                    val viewModel = hiltViewModel<AddFlightViewModel, AddFlightViewModel.Factory>(
                        creationCallback = { it.create(key.tripId, key.draft()) },
                    )
                    AddFlightScreen(viewModel = viewModel, onDone = ::back)
                }

                entry<Screen.Booking> { key ->
                    SegmentScreen(
                        segmentId = key.segmentId,
                        viewModel = tripViewModel(tripStores, host, key.tripId),
                        onBack = ::back,
                    )
                }

                entry<Screen.Numbers> { key ->
                    AnalyticsScreen(viewModel = tripViewModel(tripStores, host, key.tripId), onBack = ::back)
                }

                entry<Screen.TripMap> { key ->
                    MapScreen(
                        viewModel = tripViewModel(tripStores, host, key.tripId),
                        onBack = ::back,
                        onOpenSegment = { backStack.add(Screen.Booking(key.tripId, it)) },
                    )
                }

                entry<Screen.Insights> { key ->
                    InsightsScreen(viewModel = tripViewModel(tripStores, host, key.tripId), onBack = ::back)
                }
            },
        )

        Incoming(share, onOpenTrip = { openTrip(it) }, onCheckFlight = { tripId, screen -> openTrip(tripId, screen) })
    }

    // Whatever arrived from outside — a tapped reminder, a shared email, a
    // file — is taken once and acted on here, above every screen.
    val request by inbox.requests.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(request) {
        when (val taken = inbox.take()) {
            is Inbox.Request.OpenBooking -> openTrip(taken.tripId, Screen.Booking(taken.tripId, taken.segmentId))
            is Inbox.Request.SharedText -> share.openText(taken.text)
            is Inbox.Request.ImportFile -> share.openFile(context, taken.uri.toUri())
            is Inbox.Request.ImportCode -> share.openCode(taken.payload)
            null -> Unit
        }
    }
}

/** Back down to [screen], which stays; the shelf is always at the bottom. */
private fun NavBackStack<NavKey>.popTo(screen: Screen) {
    while (size > 1 && last() != screen) removeAt(lastIndex)
}

/** The sheet for whatever is waiting to be looked at, if anything is. */
@Composable
private fun Incoming(
    share: ShareViewModel,
    onOpenTrip: (String) -> Unit,
    onCheckFlight: (tripId: String, Screen.AddFlight) -> Unit,
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
                onCheckFlight(tripId, Screen.AddFlight.of(tripId, flight, shown.reading.reference))
            },
            onDismiss = share::dismiss,
        )

        is ShareViewModel.Incoming.Problem -> ProblemModal(problem = shown, onDismiss = share::dismiss)

        null -> Unit
    }
}

/**
 * The one view model of an open trip, shared by every screen inside it — so
 * the booking screen, the map and the numbers read the same dossier the
 * timeline does instead of each subscribing again.
 */
@Composable
private fun tripViewModel(stores: TripStores, host: ViewModelStoreOwner, tripId: String): TripViewModel {
    val owner = remember(stores, host, tripId) { stores.owner(tripId, host) }
    return hiltViewModel<TripViewModel, TripViewModel.Factory>(
        viewModelStoreOwner = owner,
        key = "trip:$tripId",
        creationCallback = { it.create(tripId) },
    )
}
