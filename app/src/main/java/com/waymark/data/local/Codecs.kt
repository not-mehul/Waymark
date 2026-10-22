package com.waymark.data.local

import com.waymark.domain.model.Place

/**
 * Small, explicit encoders for the composite values held in single columns.
 *
 * These exist instead of a JSON dependency: the shapes are fixed, the volumes
 * are tiny, and a hand-written codec keeps the database file legible in a
 * shell. Field separators are control characters, which cannot occur in the
 * user-entered text they delimit.
 */
internal object Codecs {

    private const val UNIT = '\u001F'
    private const val RECORD = '\u001E'

    fun encodePlace(place: Place?): String = place?.let {
        listOf(
            it.name, it.code.orEmpty(), it.city, it.country,
            it.latitude?.toString().orEmpty(), it.longitude?.toString().orEmpty(),
            it.timeZoneId, it.address.orEmpty(),
        ).joinToString(UNIT.toString())
    }.orEmpty()

    fun decodePlace(encoded: String?): Place? {
        if (encoded.isNullOrBlank()) return null
        val parts = encoded.split(UNIT)
        if (parts.size < 7) return null
        // Rows written before coordinates could be absent stored "unknown" as
        // 0.0, 0.0 — a point in the Gulf of Guinea no booking has ever been.
        val coordinates = parts[4].toDoubleOrNull()?.let { latitude ->
            parts[5].toDoubleOrNull()?.let { longitude -> latitude to longitude }
        }?.takeUnless { it.first == 0.0 && it.second == 0.0 }
        return Place(
            name = parts[0],
            code = parts[1].ifBlank { null },
            city = parts[2],
            country = parts[3],
            latitude = coordinates?.first,
            longitude = coordinates?.second,
            timeZoneId = parts[6].ifBlank { "UTC" },
            address = parts.getOrNull(7)?.ifBlank { null },
        )
    }

    fun encodeIds(ids: Collection<String>): String = ids.filter { it.isNotBlank() }.joinToString(",")

    fun decodeIds(encoded: String?): Set<String> =
        encoded?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    fun encodeList(values: List<String>): String = values.joinToString(UNIT.toString())

    fun decodeList(encoded: String?): List<String> =
        encoded?.split(UNIT)?.filter { it.isNotBlank() } ?: emptyList()

    fun encodeMap(values: Map<String, String>): String =
        values.entries.joinToString(RECORD.toString()) { "${it.key}$UNIT${it.value}" }

    fun decodeMap(encoded: String?): Map<String, String> {
        if (encoded.isNullOrBlank()) return emptyMap()
        return encoded.split(RECORD).mapNotNull { entry ->
            val parts = entry.split(UNIT)
            if (parts.size == 2 && parts[0].isNotBlank()) parts[0] to parts[1] else null
        }.toMap()
    }
}
