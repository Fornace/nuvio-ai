package com.nuvio.tv.core.media.provider.host

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test
import java.io.File

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProviderInstallCoordinatorTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val apkBytes = byteArrayOf(0x50, 0x4b, 0x03, 0x04, 7, 8, 9)
    private val apkSha256 = HostCrypto.sha256Hex(apkBytes)
    private val mismatchSha256 = "f".repeat(64)

    private fun request(expectedSha256: String = apkSha256) = ProviderInstallRequest(
        providerId = "nuvio-subtitles",
        apkUrl = "https://nuvio-extensions.fornace.net/artifacts/nuvio-subtitles.apk",
        expectedApkSha256 = expectedSha256,
        packageName = "com.nuvio.tv.provider.subtitles",
        versionName = "0.1.0-preview1"
    )

    private fun TestScope.harness(
        downloadError: ProviderApkDownloadException? = null,
        installerResult: ProviderInstallerResult = ProviderInstallerResult.Installed(
            "com.nuvio.tv.provider.subtitles"
        ),
        hangDownload: CompletableDeferred<Unit>? = null
    ): Triple<ProviderInstallCoordinator, FakeDownloader, FakeInstallerBridge> {
        val downloader = FakeDownloader(apkBytes, downloadError, hangDownload)
        val bridge = FakeInstallerBridge(installerResult)
        val coordinator = ProviderInstallCoordinator(
            downloader = downloader,
            verifier = ProviderArtifactVerifier(),
            installerBridge = bridge,
            downloadDir = tempFolder.newFolder(),
            externalScope = this
        )
        return Triple(coordinator, downloader, bridge)
    }

    private fun downloadsDir(): File =
        tempFolder.root.listFiles()!!.filter { it.isDirectory }.single()

    private fun assertDownloadsDirEmpty() =
        assertEquals(emptyList<File>(), downloadsDir().listFiles()?.toList())

    @Test
    fun `happy path downloads verifies and installs`() = runTest {
        val (coordinator, _, bridge) = harness()
        val recorded = ArrayList<ProviderInstallState>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.state.toList(recorded)
        }

        val finalState = coordinator.install(request())
        // Let the state collector drain the final emission before asserting on it.
        runCurrent()

        val installed = ProviderInstallState.Installed(
            "com.nuvio.tv.provider.subtitles",
            "0.1.0-preview1"
        )
        assertEquals(installed, finalState)
        assertEquals(installed, coordinator.state.value)
        assertEquals(1, bridge.installCount)
        // The APK existed with its full content when the installer consumed it.
        assertEquals(apkBytes.size.toLong(), bridge.lastApkSize)
        assertEquals("com.nuvio.tv.provider.subtitles", bridge.lastPackageName)
        assertTrue(bridge.fileExistedAtInstall)
        // The artifact is removed again once the install finished.
        assertFalse(bridge.lastApkFile!!.exists())
        assertDownloadsDirEmpty()
        // Progress was observed through the state flow and nothing failed.
        assertEquals(ProviderInstallState.Idle, recorded.first())
        assertEquals(installed, recorded.last())
        assertTrue(recorded.contains(ProviderInstallState.Downloading("nuvio-subtitles", 1f)))
        assertTrue(recorded.none { it is ProviderInstallState.Failed })
        collector.cancel()
    }

    @Test
    fun `digest mismatch deletes file and never invokes installer`() = runTest {
        val (coordinator, _, bridge) = harness()

        val finalState = coordinator.install(request(expectedSha256 = mismatchSha256))

        assertEquals(
            ProviderInstallState.Failed(ProviderInstallFailureReason.DigestMismatch),
            finalState
        )
        assertEquals(finalState, coordinator.state.value)
        assertEquals(0, bridge.installCount)
        assertDownloadsDirEmpty()
    }

    @Test
    fun `installer rejection maps to InstallerRejected and still cleans up`() = runTest {
        val (coordinator, _, bridge) = harness(
            installerResult = ProviderInstallerResult.Rejected(
                ProviderInstallerRejectionReason.USER_CANCELLED
            )
        )

        val finalState = coordinator.install(request())

        assertEquals(
            ProviderInstallState.Failed(
                ProviderInstallFailureReason.InstallerRejected(
                    ProviderInstallerRejectionReason.USER_CANCELLED
                )
            ),
            finalState
        )
        assertEquals(1, bridge.installCount)
        assertFalse(bridge.lastApkFile!!.exists())
        assertDownloadsDirEmpty()
    }

    @Test
    fun `download failure maps to Network and skips verification and installer`() = runTest {
        val (coordinator, _, bridge) = harness(
            downloadError = ProviderApkDownloadException("http 500")
        )

        val finalState = coordinator.install(request())

        assertEquals(
            ProviderInstallState.Failed(ProviderInstallFailureReason.Network("ProviderApkDownloadException")),
            finalState
        )
        assertEquals(0, bridge.installCount)
        assertDownloadsDirEmpty()
    }

    @Test
    fun `cancellation cleans partial file and reports Cancelled`() = runTest {
        val hang = CompletableDeferred<Unit>()
        val (coordinator, _, bridge) = harness(hangDownload = hang)

        val job = launch { coordinator.install(request()) }
        runCurrent()
        job.cancelAndJoin()

        assertEquals(
            ProviderInstallState.Failed(ProviderInstallFailureReason.Cancelled),
            coordinator.state.value
        )
        assertEquals(0, bridge.installCount)
        assertDownloadsDirEmpty()
    }

    @Test
    fun `launchInstall runs on the external scope and cancel aborts`() = runTest {
        val hang = CompletableDeferred<Unit>()
        val (coordinator, _, bridge) = harness(hangDownload = hang)

        val job = coordinator.launchInstall(request())
        runCurrent()
        assertTrue(job.isActive)
        coordinator.cancel()
        runCurrent()
        assertFalse(job.isActive)
        assertEquals(
            ProviderInstallState.Failed(ProviderInstallFailureReason.Cancelled),
            coordinator.state.value
        )
        assertEquals(0, bridge.installCount)
        assertDownloadsDirEmpty()
    }

    private inner class FakeDownloader(
        private val bytes: ByteArray,
        private val downloadError: ProviderApkDownloadException?,
        private val hang: CompletableDeferred<Unit>?
    ) : ProviderApkDownloader {
        override suspend fun download(
            apkUrl: String,
            destination: File,
            onProgress: (Float) -> Unit
        ): File {
            if (downloadError != null) throw downloadError
            onProgress(0f)
            destination.writeBytes(bytes)
            onProgress(1f)
            // Let the state collector observe the download progress before continuing.
            yield()
            hang?.await()
            return destination
        }
    }

    private inner class FakeInstallerBridge(
        private val result: ProviderInstallerResult
    ) : PackageInstallerBridge {
        var installCount = 0
            private set
        var lastApkFile: File? = null
            private set
        var lastApkSize: Long = 0
            private set
        var lastPackageName: String? = null
            private set
        var fileExistedAtInstall = false
            private set

        override suspend fun install(
            apkFile: File,
            packageName: String?,
            statusListener: ProviderInstallerStatusListener?
        ): ProviderInstallerResult {
            installCount += 1
            lastApkFile = apkFile
            lastApkSize = apkFile.length()
            lastPackageName = packageName
            fileExistedAtInstall = apkFile.exists()
            return result
        }
    }
}
