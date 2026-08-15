package com.waymark.data.local

import com.waymark.domain.model.Place
import com.waymark.domain.model.Secret
import com.waymark.domain.model.SecretField

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
            it.latitude.toString(), it.longitude.toString(),
            it.timeZoneId, it.address.orEmpty(),
        ).joinToString(UNIT.toString())
    }.orEmpty()

    fun decodePlace(encoded: String?): Place? {
        if (encoded.isNullOrBlank()) return null
        val parts = encoded.split(UNIT)
        if (parts.size < 7) return null
        return Place(
            name = parts[0],
            code = parts[1].ifBlank { null },
            city = parts[2],
            country = parts[3],
            latitude = parts[4].toDoubleOrNull() ?: 0.0,
            longitude = parts[5].toDoubleOrNull() ?: 0.0,
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

    /** Secrets are encoded first, then the whole blob is sealed as one unit. */
    fun encodeSecrets(secrets: List<Secret>): String =
        secrets.joinToString(RECORD.toString()) { secret ->
            listOf(secret.field.name, secret.value, secret.travelerId.orEmpty())
                .joinToString(UNIT.toString())
        }

    fun decodeSecrets(encoded: String?): List<Secret> {
        if (encoded.isNullOrBlank()) return emptyList()
        return encoded.split(RECORD).mapNotNull { entry ->
            val parts = entry.split(UNIT)
            if (parts.size < 2) return@mapNotNull null
            val field = runCatching { SecretField.valueOf(parts[0]) }.getOrNull()
                ?: SecretField.OTHER
            Secret(
                field = field,
                value = parts[1],
                travelerId = parts.getOrNull(2)?.ifBlank { null },
            )
        }
    }
}
