package com.waymark.online

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads a URL as text. Every request the app makes goes through here. */
fun interface Fetch {
    suspend operator fun invoke(url: String): String
}

/** The real thing: HTTPS, short timeouts, no cookies, nothing sent but the URL. */
object Http : Fetch {

    private const val TIMEOUT_MILLIS = 10_000

    override suspend fun invoke(url: String): String = withContext(Dispatchers.IO) {
        require(url.startsWith("https://")) { "Waymark only goes online over HTTPS" }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("HTTP $status from ${URL(url).host}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
