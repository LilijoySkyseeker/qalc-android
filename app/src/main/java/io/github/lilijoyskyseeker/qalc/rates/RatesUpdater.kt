package io.github.lilijoyskyseeker.qalc.rates

import io.github.lilijoyskyseeker.qalc.engine.RateSource
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Throws on failure. */
fun interface Fetcher {
    fun fetch(url: String): ByteArray
}

/**
 * Downloads the exchange-rate files libqalculate reads (it names the URLs and
 * paths itself), then has the engine reload them.
 */
class RatesUpdater(
    private val sources: suspend () -> List<RateSource>,
    private val reload: suspend () -> Boolean,
    private val fetch: Fetcher = HttpFetcher,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Returns true if any source was refreshed. */
    suspend fun updateIfStale(): Boolean {
        val all = sources()
        val first = File(all.first().path)
        if (first.exists() && now() - first.lastModified() < MAX_AGE_MS) return false
        val refreshed = withContext(Dispatchers.IO) { all.count { download(it) } }
        if (refreshed == 0) return false
        reload()
        return true
    }

    private fun download(source: RateSource): Boolean {
        val target = File(source.path)
        val tmp = File(source.path + ".tmp")
        return try {
            target.parentFile?.mkdirs()
            tmp.writeBytes(fetch.fetch(source.url))
            if (!tmp.renameTo(target)) throw IOException("rename failed: $tmp")
            true
        } catch (e: Exception) {
            tmp.delete()
            false
        }
    }

    companion object {
        const val MAX_AGE_MS = 24L * 60 * 60 * 1000
    }
}

object HttpFetcher : Fetcher {
    override fun fetch(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode} for $url")
            }
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}
