package com.nuvio.tv.core.media.provider.host

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface ProviderInstallState {
    data object Idle : ProviderInstallState
    data class Downloading(val providerId: String, val progress: Float) : ProviderInstallState
    data object Verifying : ProviderInstallState
    data object Installing : ProviderInstallState
    data class Installed(val packageName: String, val versionName: String) : ProviderInstallState
    data class Failed(val reason: ProviderInstallFailureReason) : ProviderInstallState
}

sealed interface ProviderInstallFailureReason {
    data class Network(val causeType: String) : ProviderInstallFailureReason
    data object DigestMismatch : ProviderInstallFailureReason
    data class InstallerRejected(val reason: ProviderInstallerRejectionReason) : ProviderInstallFailureReason
    data object Cancelled : ProviderInstallFailureReason
}

data class ProviderInstallRequest(
    val providerId: String,
    val apkUrl: String,
    val expectedApkSha256: String,
    val packageName: String,
    val versionName: String
)

/**
 * Orchestrates download -> digest verification -> installer commit for one provider APK.
 * Pure JVM: Android specifics live in the downloader and installer bridge implementations.
 *
 * Contract highlights:
 * - A digest mismatch deletes the downloaded file and never touches the installer bridge.
 * - Cancelling the owning scope or [cancel] cleans every partial file.
 */
class ProviderInstallCoordinator(
    private val downloader: ProviderApkDownloader,
    private val verifier: ProviderArtifactVerifier,
    private val installerBridge: PackageInstallerBridge,
    private val downloadDir: File,
    private val externalScope: CoroutineScope,
    private val deleteApkOnSuccess: Boolean = true
) {
    private val mutableState = MutableStateFlow<ProviderInstallState>(ProviderInstallState.Idle)
    val state: StateFlow<ProviderInstallState> = mutableState.asStateFlow()

    private var activeJob: Job? = null

    fun launchInstall(request: ProviderInstallRequest): Job {
        check(activeJob?.isActive != true) { "An install is already in progress" }
        val job = externalScope.launch { install(request) }
        activeJob = job
        job.invokeOnCompletion {
            if (activeJob === job) activeJob = null
        }
        return job
    }

    fun cancel() {
        activeJob?.cancel()
    }

    suspend fun install(request: ProviderInstallRequest): ProviderInstallState {
        val destination = File(downloadDir, "${request.providerId}-${request.expectedApkSha256}.apk")
        val partialFile = File(downloadDir, destination.name + OkHttpProviderApkDownloader.PARTIAL_SUFFIX)
        downloadDir.mkdirs()
        var succeeded = false

        try {
            mutableState.value = ProviderInstallState.Downloading(request.providerId, 0f)
            try {
                downloader.download(request.apkUrl, destination) { progress ->
                    mutableState.value = ProviderInstallState.Downloading(
                        providerId = request.providerId,
                        progress = progress.coerceIn(0f, 1f)
                    )
                }
            } catch (error: ProviderApkDownloadException) {
                return fail(ProviderInstallFailureReason.Network(error.javaClass.simpleName))
            }

            mutableState.value = ProviderInstallState.Verifying
            when (verifier.verify(destination, request.expectedApkSha256)) {
                is ProviderArtifactVerificationResult.Verified -> Unit
                is ProviderArtifactVerificationResult.DigestMismatch ->
                    return fail(ProviderInstallFailureReason.DigestMismatch)
                is ProviderArtifactVerificationResult.InvalidExpectedDigest ->
                    return fail(ProviderInstallFailureReason.DigestMismatch)
                is ProviderArtifactVerificationResult.ReadError ->
                    return fail(ProviderInstallFailureReason.DigestMismatch)
            }

            mutableState.value = ProviderInstallState.Installing
            when (val result = installerBridge.install(destination, request.packageName)) {
                is ProviderInstallerResult.Installed -> {
                    val installed = ProviderInstallState.Installed(
                        packageName = result.packageName.ifBlank { request.packageName },
                        versionName = request.versionName
                    )
                    succeeded = true
                    mutableState.value = installed
                    return installed
                }
                is ProviderInstallerResult.Rejected ->
                    return fail(ProviderInstallFailureReason.InstallerRejected(result.reason))
            }
        } catch (error: CancellationException) {
            mutableState.value = ProviderInstallState.Failed(ProviderInstallFailureReason.Cancelled)
            throw error
        } finally {
            // Digest mismatches, cancellations, rejections and (by default) successful
            // installs all remove the downloaded artifact and any partial sibling.
            partialFile.delete()
            if (deleteApkOnSuccess || !succeeded) {
                destination.delete()
            }
        }
    }

    private fun fail(reason: ProviderInstallFailureReason): ProviderInstallState {
        mutableState.value = ProviderInstallState.Failed(reason)
        return ProviderInstallState.Failed(reason)
    }
}
