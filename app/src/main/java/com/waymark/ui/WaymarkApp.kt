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
import com.waymark.ui.add.AddFlightScreen
import com.waymark.ui.add.AddFlightViewModel
import com.waymark.ui.add.AddPlanScreen
import com.waymark.ui.add.AddPlanViewModel
import com.waymark.ui.analytics.AnalyticsScreen
import com.waymark.ui.insights.InsightsScreen
import com.waymark.ui.packing.PackingScreen
import com.waymark.ui.pass.BoardingPassScreen
import com.waymark.ui.segment.SegmentScreen
import com.waymark.ui.trip.TripScreen
import com.waymark.ui.trip.TripViewModel
import com.waymark.ui.trips.TripsScreen
import com.waymark.ui.trips.TripsViewModel

object Routes {
    const val TRIPS = "trips"
    const val TRIP = "trip/{tripId}"
    const val ADD_FLIGHT = "trip/{tripId}/add-flight"
    const val ADD_PLAN = "trip/{tripId}/add-plan"
    const val SEGMENT = "trip/{tripId}/segment/{segmentId}"
    const val PASS = "trip/{tripId}/pass/{passId}"
    const val INSIGHTS = "trip/{tripId}/insights"
    const val PACKING = "trip/{tripId}/packing"
    const val ANALYTICS = "trip/{tripId}/numbers"

    fun trip(tripId: String) = "trip/$tripId"
    fun addFlight(tripId: String) = "trip/$tripId/add-flight"
    fun addPlan(tripId: String) = "trip/$tripId/add-plan"
    fun segment(tripId: String, segmentId: String) = "trip/$tripId/segment/$segmentId"
    fun pass(tripId: String, passId: String) = "trip/$tripId/pass/$passId"
    fun insights(tripId: String) = "trip/$tripId/insights"
    fun packing(tripId: String) = "trip/$tripId/packing"
    fun analytics(tripId: String) = "trip/$tripId/numbers"
}

@Composable
fun WaymarkApp(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.TRIPS) {

        composable(Routes.TRIPS) {
            val viewModel: TripsViewModel = viewModel(
                factory = factory { TripsViewModel(container.tripRepository) }
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
                onAddPlan = { navController.navigate(Routes.addPlan(tripId)) },
                onOpenSegment = { navController.navigate(Routes.segment(tripId, it)) },
                onOpenPass = { navController.navigate(Routes.pass(tripId, it)) },
                onOpenInsights = { navController.navigate(Routes.insights(tripId)) },
                onOpenPacking = { navController.navigate(Routes.packing(tripId)) },
                onOpenAnalytics = { navController.navigate(Routes.analytics(tripId)) },
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
                        vault = container.vaultRepository,
                    )
                }
            )
            AddFlightScreen(
                viewModel = viewModel,
                onDone = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.ADD_PLAN,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType }),
        ) { entry ->
            val tripId = entry.requireTripId()
            val viewModel: AddPlanViewModel = viewModel(
                factory = factory {
                    AddPlanViewModel(
                        tripId = tripId,
                        trips = container.tripRepository,
                        vault = container.vaultRepository,
                    )
                }
            )
            AddPlanScreen(viewModel = viewModel, onDone = { navController.popBackStack() })
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
                onOpenPass = { navController.navigate(Routes.pass(tripId, it)) },
            )
        }

        composable(
            route = Routes.PASS,
            arguments = listOf(
                navArgument("tripId") { type = NavType.StringType },
                navArgument("passId") { type = NavType.StringType },
            ),
        ) { entry ->
            val tripId = entry.requireTripId()
            val passId = entry.arguments?.getString("passId").orEmpty()
            BoardingPassScreen(
                passId = passId,
                viewModel = viewModel(factory = tripFactory(container, tripId)),
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.PACKING,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType }),
        ) { entry ->
            PackingScreen(
                viewModel = viewModel(factory = tripFactory(container, entry.requireTripId())),
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
 * The trip view model is keyed by route, so the five tabs, the segment screen
 * and the pass screen each get one bound to the same trip id.
 */
private fun tripFactory(container: AppContainer, tripId: String): ViewModelProvider.Factory =
    factory {
        TripViewModel(
            tripId = tripId,
            trips = container.tripRepository,
            vault = container.vaultRepository,
            flights = container.flightRepository,
            ideas = container.ideaRepository,
            preparations = container.preparationRepository,
        )
    }

private inline fun <reified VM : ViewModel> factory(
    crossinline create: () -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer { create() }
}
