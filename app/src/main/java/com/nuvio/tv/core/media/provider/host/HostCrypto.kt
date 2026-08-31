package com.nuvio.tv.core.media.provider.host

import java.security.MessageDigest

object HostCrypto {
    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toLowerHex()

    internal fun ByteArray.toLowerHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
}
