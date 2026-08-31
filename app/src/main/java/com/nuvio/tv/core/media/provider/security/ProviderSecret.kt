package com.nuvio.tv.core.media.provider.security

/**
 * Opaque holder for a provider secret (API key) owned by [ProviderCredentialVault].
 *
 * Hard rules enforced by this type:
 *  - there is no property, getter, `toString`, `equals` or `hashCode` output that
 *    exposes the plaintext bytes;
 *  - the bytes are only ever handed to the [use] callback as a defensive copy,
 *    which is zeroed after the callback returns (best-effort hygiene — the
 *    callback itself is trusted first-party code);
 *  - construction copies the caller's array defensively, so later mutation of
 *    the source array does not change the stored secret.
 */
@JvmInline
value class ProviderSecret private constructor(
    private val bytes: ByteArray,
) {
    /** Runs [block] with a zeroed-after-use copy of the plaintext bytes. */
    fun <R> use(block: (ByteArray) -> R): R {
        val copy = bytes.copyOf()
        try {
            return block(copy)
        } finally {
            copy.fill(0)
        }
    }

    override fun toString(): String = "ProviderSecret(length=${bytes.size}, redacted)"

    companion object {
        /** Creates a caller-owned [ProviderSecret] from a defensive copy. */
        fun copyOf(bytes: ByteArray): ProviderSecret = ProviderSecret(bytes.copyOf())

        /**
         * Wraps a vault-owned plaintext buffer without another long-lived copy.
         * The vault zeroes this exact buffer as soon as its operation callback
         * returns, so a [ProviderSecret] accidentally returned from that callback
         * becomes unusable as secret material outside the operation scope.
         */
        internal fun wrapVaultBuffer(bytes: ByteArray): ProviderSecret = ProviderSecret(bytes)
    }
}
