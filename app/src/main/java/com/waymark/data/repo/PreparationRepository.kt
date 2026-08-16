package com.waymark.data.repo

import com.waymark.data.local.Mappers
import com.waymark.data.local.WaymarkDatabase
import com.waymark.domain.model.DocumentKind
import com.waymark.domain.model.TravelDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

/**
 * The documents that have to be valid before a trip can happen: passports,
 * visas, insurance.
 *
 * These used to be half of a "preparation" layer whose other half was the
 * packing list, and their numbers were sealed behind the vault's cipher. Both
 * are gone; what is left is a small table of records with dates the app can
 * check against the trip.
 */
class PreparationRepository(database: WaymarkDatabase) {

    private val documents = database.documentDao()

    fun observeDocuments(): Flow<List<TravelDocument>> =
        documents.observeAll().map { rows -> rows.map(Mappers::toDocument) }

    fun observeDocumentsFor(travelerIds: List<String>): Flow<List<TravelDocument>> =
        documents.observeFor(travelerIds).map { rows -> rows.map(Mappers::toDocument) }

    suspend fun saveDocument(document: TravelDocument) =
        documents.upsert(Mappers.toEntity(document))

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

    private fun newId(prefix: String): String =
        "$prefix-${UUID.randomUUID().toString().take(8)}"
}
