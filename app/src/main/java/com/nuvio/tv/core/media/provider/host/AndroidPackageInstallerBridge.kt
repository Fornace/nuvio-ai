package com.nuvio.tv.core.media.provider.host

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException
import java.util.UUID

sealed interface ProviderInstallerResult {
    data class Installed(val packageName: String) : ProviderInstallerResult
    data class Rejected(val reason: ProviderInstallerRejectionReason) : ProviderInstallerResult
}

enum class ProviderInstallerRejectionReason {
    UNKNOWN_SOURCES_PERMISSION_REQUIRED,
    SIGNATURE_CONFLICT,
    USER_CANCELLED,
    INVALID_APK,
    STORAGE,
    TIMEOUT,
    SECURITY,
    IO,
    OTHER,
}

sealed interface ProviderInstallerEvent {
    data object AwaitingUserConfirmation : ProviderInstallerEvent
    data object Accepted : ProviderInstallerEvent
    data class Rejected(val reason: ProviderInstallerRejectionReason) : ProviderInstallerEvent
}

fun interface ProviderInstallerStatusListener {
    fun onEvent(event: ProviderInstallerEvent)
}

interface PackageInstallerBridge {
    /**
     * Writes the APK into a PackageInstaller session and commits it, which triggers the
     * system's user-confirmation prompt. Suspends until the install finishes or is
     * rejected; reports status transitions through [statusListener].
     */
    suspend fun install(
        apkFile: File,
        packageName: String?,
        statusListener: ProviderInstallerStatusListener? = null
    ): ProviderInstallerResult
}

/** Android bridge around [PackageInstaller]; carries all Android imports for the coordinator. */
class AndroidPackageInstallerBridge(
    private val context: Context,
    private val confirmationTimeoutMs: Long = DEFAULT_CONFIRMATION_TIMEOUT_MS
) : PackageInstallerBridge {
    override suspend fun install(
        apkFile: File,
        packageName: String?,
        statusListener: ProviderInstallerStatusListener?
    ): ProviderInstallerResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            statusListener?.onEvent(
                ProviderInstallerEvent.Rejected(
                    ProviderInstallerRejectionReason.UNKNOWN_SOURCES_PERMISSION_REQUIRED
                )
            )
            return ProviderInstallerResult.Rejected(
                ProviderInstallerRejectionReason.UNKNOWN_SOURCES_PERMISSION_REQUIRED
            )
        }
        val installer = context.packageManager.packageInstaller
        val sessionId = installer.createSession(
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        )
        val action = "${ACTION_INSTALL_STATUS}.${UUID.randomUUID()}"
        val completion = CompletableDeferred<Int>()
        val receiver = StatusReceiver(action, packageName, statusListener, completion)

        // Exported: the status PendingIntent is dispatched by the system on behalf of this
        // app; the randomized action plus package scoping pins it to this install attempt.
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_EXPORTED
        } else {
            0
        }
        context.registerReceiver(receiver, IntentFilter(action), flags)

        try {
            installer.openSession(sessionId).use { session ->
                val apkBytes = apkFile.readBytes()
                session.openWrite(APK_SESSION_KEY, 0, apkBytes.size.toLong()).use { output ->
                    output.write(apkBytes)
                    output.flush()
                }
                val statusIntent = Intent(action).setPackage(context.packageName)
                val senderFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                val statusSender = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    statusIntent,
                    senderFlags
                ).intentSender
                session.commit(statusSender)
            }
            return when (val status = withTimeout(confirmationTimeoutMs) { completion.await() }) {
                PackageInstaller.STATUS_SUCCESS ->
                    ProviderInstallerResult.Installed(receiver.finalPackageName ?: "")
                else -> ProviderInstallerResult.Rejected(receiver.rejectionReason)
            }
        } catch (_: TimeoutCancellationException) {
            return ProviderInstallerResult.Rejected(ProviderInstallerRejectionReason.TIMEOUT)
        } catch (_: IOException) {
            return ProviderInstallerResult.Rejected(ProviderInstallerRejectionReason.IO)
        } catch (_: SecurityException) {
            return ProviderInstallerResult.Rejected(ProviderInstallerRejectionReason.SECURITY)
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
            runCatching { installer.abandonSession(sessionId) }
        }
    }

    private class StatusReceiver(
        private val action: String,
        private val expectedPackageName: String?,
        private val statusListener: ProviderInstallerStatusListener?,
        private val completion: CompletableDeferred<Int>
    ) : BroadcastReceiver() {
        var rejectionReason: ProviderInstallerRejectionReason = ProviderInstallerRejectionReason.OTHER
            private set

        var finalPackageName: String? = null
            private set

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != action) return
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
            if (status == Int.MIN_VALUE) return
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    statusListener?.onEvent(ProviderInstallerEvent.AwaitingUserConfirmation)
                    val confirmation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
                    } ?: return completeAsRejected()
                    confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirmation) }
                        .onFailure { completeAsRejected() }
                }
                PackageInstaller.STATUS_SUCCESS -> {
                    finalPackageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
                        ?: expectedPackageName
                    statusListener?.onEvent(ProviderInstallerEvent.Accepted)
                    completion.complete(PackageInstaller.STATUS_SUCCESS)
                }
                else -> completeAsRejected(mapRejectionReason(status))
            }
        }

        private fun completeAsRejected(
            reason: ProviderInstallerRejectionReason = ProviderInstallerRejectionReason.OTHER
        ) {
            rejectionReason = reason
            statusListener?.onEvent(ProviderInstallerEvent.Rejected(reason))
            completion.complete(PackageInstaller.STATUS_FAILURE)
        }

        private fun mapRejectionReason(status: Int): ProviderInstallerRejectionReason = when (status) {
            PackageInstaller.STATUS_FAILURE_CONFLICT -> ProviderInstallerRejectionReason.SIGNATURE_CONFLICT
            PackageInstaller.STATUS_FAILURE_INVALID -> ProviderInstallerRejectionReason.INVALID_APK
            PackageInstaller.STATUS_FAILURE_STORAGE -> ProviderInstallerRejectionReason.STORAGE
            PackageInstaller.STATUS_FAILURE_ABORTED -> ProviderInstallerRejectionReason.USER_CANCELLED
            PackageInstaller.STATUS_FAILURE_BLOCKED ->
                ProviderInstallerRejectionReason.UNKNOWN_SOURCES_PERMISSION_REQUIRED
            else -> ProviderInstallerRejectionReason.OTHER
        }
    }

    companion object {
        private const val APK_SESSION_KEY = "provider-apk"
        private const val ACTION_INSTALL_STATUS =
            "com.nuvio.tv.core.media.provider.host.INSTALL_STATUS"
        private const val DEFAULT_CONFIRMATION_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
