package com.nuvio.tv.provider.subtitles

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException

/**
 * Contract-shell service. The NuvioTV host binds this by explicit intent
 * with action [ProviderContract.BIND_ACTION] and speaks the Messenger
 * protocol described by [ProviderContract]. No engine, no network, no
 * credentials, no logging — the later engine version replaces the payloads.
 */
class ProviderContractService : Service() {

    private var workerThread: HandlerThread? = null
    private var messenger: Messenger? = null

    override fun onCreate() {
        super.onCreate()
        val thread = HandlerThread(ProviderContract.THREAD_NAME).apply { start() }
        workerThread = thread
        messenger = Messenger(Handler(thread.looper) { msg -> handleMessage(msg) })
    }

    override fun onBind(intent: Intent): IBinder? {
        if (intent.action != ProviderContract.BIND_ACTION) return null
        return messenger?.binder
    }

    private fun handleMessage(msg: Message): Boolean {
        val reply = ProviderContract.replyFor(msg.what, packageName, versionName())
        val payload = when (reply) {
            is ProviderContract.Reply.Negotiate -> negotiationBundle(reply.info)
            is ProviderContract.Reply.Pong -> Bundle().apply {
                putInt(ProviderContract.KEY_PONG, reply.pong)
            }
            is ProviderContract.Reply.Error -> Bundle().apply {
                putString(ProviderContract.KEY_ERROR, reply.error)
            }
        }
        val replyTo = msg.replyTo ?: return true
        val response = Message.obtain(null, msg.what, payload)
        try {
            replyTo.send(response)
        } catch (_: RemoteException) {
            // Host disappeared mid-reply; nothing to do. Never log.
        }
        return true
    }

    private fun negotiationBundle(info: ProviderContract.NegotiationInfo): Bundle = Bundle().apply {
        putInt(ProviderContract.KEY_PROTOCOL, info.protocol)
        putString(ProviderContract.KEY_PROVIDER_ID, info.providerId)
        putString(ProviderContract.KEY_PACKAGE_NAME, info.packageName)
        putString(ProviderContract.KEY_VERSION_NAME, info.versionName)
        putStringArrayList(ProviderContract.KEY_CAPABILITIES, ArrayList(info.capabilities))
        putString(ProviderContract.KEY_ENGINE_STATUS, info.engineStatus)
        putInt(ProviderContract.KEY_HOST_MIN_VERSION_CODE, info.hostMinVersionCode)
    }

    private fun versionName(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName
                ?: ProviderContract.VERSION_NAME
        } catch (_: Exception) {
            ProviderContract.VERSION_NAME
        }
    }

    override fun onDestroy() {
        workerThread?.quitSafely()
        workerThread = null
        messenger = null
        super.onDestroy()
    }
}
