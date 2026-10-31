package com.waymark.ui.nav

import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras

/**
 * One view-model store per open trip, kept for as long as any screen of that
 * trip is on the back stack.
 *
 * Navigation 3 gives each entry its own store, which is right for a screen
 * that owns its state and wrong for the screens of a trip: the timeline, a
 * booking, the map and the numbers are views of the same dossier, and should
 * read one subscription to it rather than each opening their own. This keeps
 * that one, survives rotation with the activity's store, and lets go of a
 * trip once the last of its screens has been popped.
 */
class TripStores : ViewModel() {

    private val stores = mutableMapOf<String, ViewModelStore>()

    /** An owner for [tripId]'s screens; everything but the store comes from [host]. */
    fun owner(tripId: String, host: ViewModelStoreOwner): ViewModelStoreOwner =
        TripOwner(stores.getOrPut(tripId) { ViewModelStore() }, host)

    /** Clear every trip that is no longer on the back stack. */
    fun keepOnly(tripIds: Set<String>) {
        val gone = stores.keys - tripIds
        gone.forEach { stores.remove(it)?.clear() }
    }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }

    private class TripOwner(
        override val viewModelStore: ViewModelStore,
        private val host: ViewModelStoreOwner,
    ) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {

        override val defaultViewModelProviderFactory: ViewModelProvider.Factory
            get() = (host as HasDefaultViewModelProviderFactory).defaultViewModelProviderFactory

        override val defaultViewModelCreationExtras: CreationExtras
            get() = MutableCreationExtras((host as HasDefaultViewModelProviderFactory).defaultViewModelCreationExtras)
                .apply { set(VIEW_MODEL_STORE_OWNER_KEY, this@TripOwner) }
    }
}
