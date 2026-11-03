package io.github.ehb54.quiz.data

import androidx.annotation.VisibleForTesting
import io.github.ehb54.quiz.engine.SampleCatalog
import io.github.ehb54.quiz.engine.SampleFile
import io.github.ehb54.quiz.engine.parseCatalog
import io.github.ehb54.quiz.engine.readLimited
import io.github.ehb54.quiz.engine.sha256
import io.github.ehb54.quiz.engine.toJson
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * Reads the sample library from the download site: catalog.json and the
 * sample files next to it. Keeps a copy of the catalog for offline use and of
 * every file it downloads (by fingerprint), and refuses a download whose
 * fingerprint does not match the catalog.
 */
class SampleLibrary(cacheRoot: File, private val baseUrl: String = Companion.baseUrl) {
    private val cache = File(cacheRoot, "samples")

    /** The current catalog from the site; throws when the site cannot be reached. */
    fun fetchCatalog(): SampleCatalog {
        val catalog = parseCatalog(read("catalog.json").toString(Charsets.UTF_8))
        write(File(cache, "catalog.json"), catalog.toJson().toByteArray())
        return catalog
    }

    /** The catalog as last fetched, for offline use. */
    fun cachedCatalog(): SampleCatalog? =
        File(cache, "catalog.json").takeIf { it.exists() }?.let { runCatching { parseCatalog(it.readText()) }.getOrNull() }

    /** A sample file's contents: from the cache, otherwise downloaded and checked against the catalog. */
    fun download(file: SampleFile): ByteArray {
        val cached = File(cache, file.sha256)
        if (cached.exists()) {
            val bytes = cached.readBytes()
            if (sha256(bytes) == file.sha256) return bytes
        }
        val bytes = read(file.file)
        if (sha256(bytes) != file.sha256) throw IOException("${file.file} does not match the catalog")
        write(cached, bytes)
        return bytes
    }

    private fun read(relative: String): ByteArray {
        val url = URI(baseUrl).resolve(URI(null, null, relative, null)).toURL()
        val connection = url.openConnection().apply {
            connectTimeout = 15_000
            readTimeout = 60_000
        }
        if (connection is HttpURLConnection && connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("HTTP ${connection.responseCode} for $url")
        }
        return connection.getInputStream().use { readLimited(it) }
    }

    private fun write(target: File, bytes: ByteArray) {
        cache.mkdirs()
        val temporary = File(cache, "${target.name}.tmp")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(target)) throw IOException("cannot write ${target.name}")
    }

    companion object {
        /** Where the download site keeps the samples (the Quiz page's samples/ folder). */
        const val SITE = "https://ehb54.github.io/android-apps/quiz/samples/"

        /** The library address; tests point it at a local folder. */
        @VisibleForTesting
        var baseUrl: String = SITE
    }
}
