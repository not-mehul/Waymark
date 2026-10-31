package com.waymark

import android.app.Application
import android.util.Log
import com.waymark.alerts.DepartureWatch
import com.waymark.alerts.Notifications
import com.waymark.alerts.ReminderCheck
import com.waymark.data.local.ReminderStore
import com.waymark.data.repo.TripRepository
import com.waymark.di.Directories
import com.waymark.di.graph
import com.waymark.migration.HandoverSnapshot
import com.waymark.widget.NextUpWidget
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@HiltAndroidApp
class WaymarkApplication : Application() {

    @Inject
    lateinit var trips: TripRepository

    @Inject
    lateinit var reminderStore: ReminderStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        DepartureWatch.schedule(this)
        scope.launch {
            // The directory first: a traveler who opens the app to add a flight
            // should find the field already able to resolve their airport.
            Directories.loadAirports()
            // Nothing is written on first run: the worked example is offered
            // on the empty shelf rather than installed on somebody's behalf.
            // This only tidies what earlier builds left behind.
            trips.repair()
            Directories.loadCities()
            NextUpWidget.publishPreview(this@WaymarkApplication)
        }
        scope.launch {
            // Any change to a booking, a trip or a reminder setting moves the
            // next reminder; look again, once the burst of writes has settled.
            combine(trips.observeItineraryChanges(), reminderStore.observe()) { _, _ -> }
                .debounce(SETTLE_MILLIS)
                .collect {
                    ReminderCheck.run(this@WaymarkApplication, graph)
                    NextUpWidget.refresh(this@WaymarkApplication)
                }
        }
        scope.launch {
            // A copy of every trip for the next version of the app to import
            // (see HandoverSnapshot): written now, then after each change.
            trips.observeArchiveChanges()
                .debounce(SETTLE_MILLIS)
                .collect {
                    runCatching { HandoverSnapshot.write(this@WaymarkApplication, trips) }
                        .onFailure { Log.w(TAG, "Could not write the handover snapshot", it) }
                }
        }
    }

    private companion object {
        const val SETTLE_MILLIS = 1_000L
        const val TAG = "Waymark"
    }
}
