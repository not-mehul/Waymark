package com.waymark.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.waymark.di.AppContainer
import com.waymark.ui.components.ProvideReminderPermission
import com.waymark.ui.add.AddFlightScreen
import com.waymark.ui.add.AddFlightViewModel
import com.waymark.ui.analytics.AnalyticsScreen
import com.waymark.ui.insights.InsightsScreen
import com.waymark.ui.map.MapScreen
import com.waymark.ui.segment.SegmentScreen
import com.waymark.ui.trip.TripScreen
import com.waymark.ui.trip.TripViewModel
import com.waymark.ui.trips.TripsScreen
import com.waymark.ui.trips.TripsViewModel

object Routes {
    const val TRIPS = "trips"
    const val TRIP = "trip/{tripId}"
    const val ADD_FLIGHT = "trip/{tripId}/add-flight"
    const val SEGMENT = "trip/{tripId}/segment/{segmentId}"
    const val INSIGHTS = "trip/{tripId}/insights"
    const val ANALYTICS = "trip/{tripId}/numbers"
    const val MAP = "trip/{tripId}/map"

    fun trip(tripId: String) = "trip/$tripId"
    fun addFlight(tripId: String) = "trip/$tripId/add-flight"
    fun segment(tripId: String, segmentId: String) = "trip/$tripId/segment/$segmentId"
    fun insights(tripId: String) = "trip/$tripId/insights"
    fun analytics(tripId: String) = "trip/$tripId/numbers"
    fun map(tripId: String) = "trip/$tripId/map"
}

@Composable
fun WaymarkApp(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) = ProvideReminderPermission {
    NavHost(navController = navController, startDestination = Routes.TRIPS) {

        composable(Routes.TRIPS) {
            val viewModel: TripsViewModel = viewModel(
                factory = factory {
                    TripsViewModel(container.tripRepository, container.seeder)
                }
            )
            TripsScreen(
                viewModel = viewModel,
                onOpenTrip = { navController.navigate(Routes.trip(it)) },
            )
        }

        composable(
            route = Routes.TRIP,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType }),
        ) { entry ->
            val tripId = entry.requireTripId()
            TripScreen(
                viewModel = viewModel(factory = tripFactory(container, tripId)),
                onBack = { navController.popBackStack() },
                onAddFlight = { navController.navigate(Routes.addFlight(tripId)) },
                onOpenSegment = { navController.navigate(Routes.segment(tripId, it)) },
                onOpenInsights = { navController.navigate(Routes.insights(tripId)) },
                onOpenAnalytics = { navController.navigate(Routes.analytics(tripId)) },
                onOpenMap = { navController.navigate(Routes.map(tripId)) },
            )
        }

        composable(
            route = Routes.ADD_FLIGHT,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType }),
        ) { entry ->
            val tripId = entry.requireTripId()
            val viewModel: AddFlightViewModel = viewModel(
                factory = factory {
                    AddFlightViewModel(
                        tripId = tripId,
                        trips = container.tripRepository,
                    )
                }
            )
            AddFlightScreen(
                viewModel = viewModel,
                onDone = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.SEGMENT,
            arguments = listOf(
                navArgument("tripId") { type = NavType.StringType },
                navArgument("segmentId") { type = NavType.StringType },
            ),
        ) { entry ->
            val tripId = entry.requireTripId()
            val segmentId = entry.arguments?.getString("segmentId").orEmpty()
            SegmentScreen(
                segmentId = segmentId,
                viewModel = viewModel(factory = tripFactory(container, tripId)),
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.ANALYTICS,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType }),
        ) { entry ->
            AnalyticsScreen(
                viewModel = viewModel(factory = tripFactory(container, entry.requireTripId())),
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.MAP,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType }),
        ) { entry ->
            val tripId = entry.requireTripId()
            MapScreen(
                viewModel = viewModel(factory = tripFactory(container, tripId)),
                onBack = { navController.popBackStack() },
                onOpenSegment = { navController.navigate(Routes.segment(tripId, it)) },
            )
        }

        composable(
            route = Routes.INSIGHTS,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType }),
        ) { entry ->
            InsightsScreen(
                viewModel = viewModel(factory = tripFactory(container, entry.requireTripId())),
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun androidx.navigation.NavBackStackEntry.requireTripId(): String =
    arguments?.getString("tripId").orEmpty()

/**
 * The trip view model is keyed by route, so the three tabs and the segment
 * screen each get one bound to the same trip id.
 */
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
