package com.waymark.data.repo

import com.waymark.data.local.Mappers
import com.waymark.data.local.SecretCipher
import com.waymark.data.local.WaymarkDatabase
import com.waymark.domain.logic.PackingContext
import com.waymark.domain.logic.PackingPlanner
import com.waymark.domain.model.DocumentKind
import com.waymark.domain.model.PackingCategory
import com.waymark.domain.model.PackingItem
import com.waymark.domain.model.TravelDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

/**
 * The things a traveler sorts out before leaving: the documents that have to
 * be valid, and the bag that has to be packed.
 *
 * Document numbers are sealed like every other secret. Expiry dates are not,
 * because the app has to be able to warn about them without asking anyone to
 * authenticate first.
 */
class PreparationRepository(
    database: WaymarkDatabase,
    private val cipher: SecretCipher,
) {

    private val documents = database.documentDao()
    private val packing = database.packingDao()

    // — Documents —————————————————————————————————————————————————————————

    fun observeDocuments(): Flow<List<TravelDocument>> =
        documents.observeAll().map { rows -> rows.map { Mappers.toDocument(it, cipher) } }

    fun observeDocumentsFor(travelerIds: List<String>): Flow<List<TravelDocument>> =
        documents.observeFor(travelerIds).map { rows ->
            rows.map { Mappers.toDocument(it, cipher) }
        }

    suspend fun saveDocument(document: TravelDocument) =
        documents.upsert(Mappers.toEntity(document, cipher))

    suspend fun addDocument(
        travelerId: String,
        kind: DocumentKind,
        label: String,
        number: String,
        issuer: String?,
        issuedOn: LocalDate?,
        expiresOn: LocalDate?,
        note: String?,
    ): TravelDocument {
        val document = TravelDocument(
            id = newId("doc"),
            travelerId = travelerId,
            kind = kind,
            label = label.trim().ifBlank { kind.label },
            number = number.trim(),
            issuer = issuer?.trim()?.ifBlank { null },
            issuedOn = issuedOn,
            expiresOn = expiresOn,
            note = note?.trim()?.ifBlank { null },
        )
        saveDocument(document)
        return document
    }

    suspend fun deleteDocument(id: String) = documents.delete(id)

    // — Packing ———————————————————————————————————————————————————————————

    fun observePacking(tripId: String): Flow<List<PackingItem>> =
        packing.observeForTrip(tripId).map { rows -> rows.map(Mappers::toPackingItem) }

    suspend fun addPackingItem(
        tripId: String,
        travelerId: String?,
        title: String,
        category: PackingCategory,
        quantity: Int = 1,
        essential: Boolean = false,
        note: String? = null,
    ): PackingItem {
        val item = PackingItem(
            id = newId("pack"),
            tripId = tripId,
            travelerId = travelerId,
            title = title.trim(),
            category = category,
            quantity = quantity.coerceAtLeast(1),
            essential = essential,
            note = note?.trim()?.ifBlank { null },
        )
        packing.upsert(Mappers.toEntity(item))
        return item
    }

    suspend fun savePackingItem(item: PackingItem) = packing.upsert(Mappers.toEntity(item))

    suspend fun setPacked(itemId: String, packed: Boolean) = packing.setPacked(itemId, packed)

    suspend fun deletePackingItem(itemId: String) = packing.delete(itemId)

    /** Start the next trip with the same list, everything unticked. */
    suspend fun unpackAll(tripId: String) = packing.unpackAll(tripId)

    /**
     * Write the planner's draft for one traveler, skipping anything they
     * already have. Suggested ids are stable, so running this twice adds
     * nothing the second time.
     */
    suspend fun applySuggestions(
        context: PackingContext,
        travelerId: String?,
        existing: List<PackingItem>,
    ): List<PackingItem> {
        val fresh = PackingPlanner.newSuggestions(
            suggestions = PackingPlanner.suggest(context, travelerId),
            existing = existing.filter { it.travelerId == travelerId },
        )
        if (fresh.isEmpty()) return emptyList()
        packing.upsertAll(fresh.map(Mappers::toEntity))
        return fresh
    }

    private fun newId(prefix: String): String =
        "$prefix-${UUID.randomUUID().toString().take(8)}"
}
