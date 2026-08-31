package com.nuvio.tv.core.media.provider.host

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface ProviderApkDownloader {
    /** Implementations write only to [destination] (or its sibling `.part` file). */
    @Throws(ProviderApkDownloadException::class)
    suspend fun download(
        apkUrl: String,
        destination: File,
        onProgress: (Float) -> Unit = {}
    ): File
}

class ProviderApkDownloadException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

/** OkHttp implementation writing to a `.part` file and atomically promoting it on success. */
class OkHttpProviderApkDownloader(
    private val httpClient: OkHttpClient
) : ProviderApkDownloader {
    override suspend fun download(
        apkUrl: String,
        destination: File,
        onProgress: (Float) -> Unit
    ): File {
        val request = try {
            Request.Builder().url(apkUrl).get().build()
        } catch (error: IllegalArgumentException) {
            throw ProviderApkDownloadException("Invalid artifact URL")
        }

        destination.parentFile?.mkdirs()
        val partialFile = File(destination.parentFile, destination.name + PARTIAL_SUFFIX)
        partialFile.delete()
        try {
            httpClient.newCall(request).awaitResponse().use { response ->
                if (!response.isSuccessful) {
                    throw ProviderApkDownloadException("Artifact HTTP status ${response.code}")
                }
                val body = response.body
                    ?: throw ProviderApkDownloadException("Artifact response has no body")
                val totalBytes = body.contentLength().takeIf { it > 0L }
                var received = 0L
                withContext(Dispatchers.IO) {
                    body.byteStream().use { input ->
                        partialFile.outputStream().buffered().use { output ->
                            val buffer = ByteArray(BUFFER_SIZE_BYTES)
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                if (read > 0) {
                                    output.write(buffer, 0, read)
                                    received += read
                                    totalBytes?.let {
                                        onProgress((received.toDouble() / it).toFloat().coerceIn(0f, 1f))
                                    }
                                }
                            }
                        }
                    }
                }
                if (received <= 0L) {
                    throw ProviderApkDownloadException("Downloaded artifact is empty")
                }
            }
            promote(partialFile, destination)
            onProgress(1f)
            return destination
        } catch (error: IOException) {
            coroutineContext.ensureActive()
            throw if (error is ProviderApkDownloadException) error else {
                ProviderApkDownloadException("Artifact download failed", error)
            }
        } finally {
            partialFile.delete()
        }
    }

    private fun promote(source: File, destination: File) {
        destination.delete()
        if (!source.renameTo(destination)) {
            source.copyTo(destination, overwrite = true)
            source.delete()
        }
    }

    private suspend fun Call.awaitResponse(): Response =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) {
                        continuation.resume(response)
                    } else {
                        response.close()
                    }
                }
            })
        }

    companion object {
        private const val BUFFER_SIZE_BYTES = 64 * 1024
        internal const val PARTIAL_SUFFIX = ".part"
    }
}
