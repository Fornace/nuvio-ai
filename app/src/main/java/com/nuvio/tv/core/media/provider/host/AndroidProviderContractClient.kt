package com.nuvio.tv.core.media.provider.host

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Explicitly discovers, binds and negotiates one registry-pinned provider service. */
class AndroidProviderContractClient(
    private val context: Context,
    private val packageScanner: ProviderPackageScanner,
    private val validator: ProviderContractValidator,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) : ProviderContractClient {
    override suspend fun negotiate(
        registryEntry: ProviderRegistryEntry,
        packageName: String,
    ): ProviderContractResult = withContext(Dispatchers.Main.immediate) {
        val installedBefore = packageScanner.installedProviders(listOf(packageName))[packageName]
        validator.validatePreflight(registryEntry, packageName, installedBefore)?.let { reason ->
            return@withContext rejected(reason)
        }
        val serviceResult = resolveService(packageName)
        if (serviceResult is ServiceResolution.Failed) return@withContext rejected(serviceResult.reason)
        val service = (serviceResult as ServiceResolution.Found).descriptor

        when (val exchange = exchangeWithService(service)) {
            is ExchangeResult.Failed -> rejected(exchange.reason)
            is ExchangeResult.Reply -> validator.validate(
                entry = registryEntry,
                requestedPackage = packageName,
                installed = installedBefore,
                service = service,
                payload = exchange.payload,
                installedAfterReply = packageScanner.installedProviders(listOf(packageName))[packageName],
            )
        }
    }

    private fun resolveService(packageName: String): ServiceResolution {
        val intent = Intent(ExternalProviderContract.BIND_ACTION)
            .addCategory(ExternalProviderContract.BIND_CATEGORY)
            .setPackage(packageName)
        val services = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentServices(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentServices(intent, PackageManager.MATCH_ALL)
            }
        } catch (_: RuntimeException) {
            emptyList()
        }.mapNotNull { resolved ->
            val info = resolved.serviceInfo ?: return@mapNotNull null
            ProviderServiceDescriptor(
                packageName = info.packageName,
                className = info.name,
                exported = info.exported,
                permission = info.permission,
            )
        }
        return when (services.size) {
            0 -> ServiceResolution.Failed(ProviderContractFailure.SERVICE_NOT_FOUND)
            1 -> ServiceResolution.Found(services.single())
            else -> ServiceResolution.Failed(ProviderContractFailure.MULTIPLE_SERVICES)
        }
    }

    private suspend fun exchangeWithService(service: ProviderServiceDescriptor): ExchangeResult {
        val completion = CompletableDeferred<ExchangeResult>()
        val replyHandler = Handler(Looper.getMainLooper()) { message ->
            if (message.what == ExternalProviderContract.MSG_NEGOTIATE) {
                completion.complete(ExchangeResult.Reply(message.data.toNegotiationPayload()))
            }
            true
        }
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (name.packageName != service.packageName || name.className != service.className) {
                    completion.complete(ExchangeResult.Failed(ProviderContractFailure.PACKAGE_NAME_MISMATCH))
                    return
                }
                val request = Message.obtain(null, ExternalProviderContract.MSG_NEGOTIATE).apply {
                    replyTo = Messenger(replyHandler)
                }
                try {
                    Messenger(binder).send(request)
                } catch (_: RemoteException) {
                    completion.complete(ExchangeResult.Failed(ProviderContractFailure.REMOTE_ERROR))
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                completion.complete(ExchangeResult.Failed(ProviderContractFailure.BINDER_DIED))
            }

            override fun onBindingDied(name: ComponentName) {
                completion.complete(ExchangeResult.Failed(ProviderContractFailure.BINDER_DIED))
            }

            override fun onNullBinding(name: ComponentName) {
                completion.complete(ExchangeResult.Failed(ProviderContractFailure.BIND_REJECTED))
            }
        }
        val intent = Intent(ExternalProviderContract.BIND_ACTION)
            .setComponent(ComponentName(service.packageName, service.className))
        val bound = try {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (_: SecurityException) {
            false
        }
        if (!bound) return ExchangeResult.Failed(ProviderContractFailure.BIND_REJECTED)

        return try {
            withTimeout(timeoutMs) { completion.await() }
        } catch (_: TimeoutCancellationException) {
            ExchangeResult.Failed(ProviderContractFailure.BIND_TIMEOUT)
        } finally {
            runCatching { context.unbindService(connection) }
        }
    }

    private fun Bundle.toNegotiationPayload() = ProviderNegotiationPayload(
        protocol = getInt(ExternalProviderContract.KEY_PROTOCOL, Int.MIN_VALUE),
        providerId = getString(ExternalProviderContract.KEY_PROVIDER_ID).orEmpty(),
        packageName = getString(ExternalProviderContract.KEY_PACKAGE_NAME).orEmpty(),
        versionName = getString(ExternalProviderContract.KEY_VERSION_NAME).orEmpty(),
        capabilities = getStringArrayList(ExternalProviderContract.KEY_CAPABILITIES).orEmpty(),
        engineStatus = getString(ExternalProviderContract.KEY_ENGINE_STATUS).orEmpty(),
        hostMinVersionCode = getInt(ExternalProviderContract.KEY_HOST_MIN_VERSION_CODE, Int.MAX_VALUE),
        error = getString(ExternalProviderContract.KEY_ERROR),
    )

    private fun rejected(reason: ProviderContractFailure) = ProviderContractResult.Rejected(reason)

    private sealed interface ServiceResolution {
        data class Found(val descriptor: ProviderServiceDescriptor) : ServiceResolution
        data class Failed(val reason: ProviderContractFailure) : ServiceResolution
    }

    private sealed interface ExchangeResult {
        data class Reply(val payload: ProviderNegotiationPayload) : ExchangeResult
        data class Failed(val reason: ProviderContractFailure) : ExchangeResult
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 8_000L
    }
}
