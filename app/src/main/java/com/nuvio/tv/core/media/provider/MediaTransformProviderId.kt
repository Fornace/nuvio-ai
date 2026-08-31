package com.nuvio.tv.core.media.provider

/** Stable identity for native media providers. Package name alone is never trusted. */
class MediaTransformProviderId(
    packageName: String,
    signingCertificateSha256: String
) {
    val packageName: String = packageName.trim()
    val signingCertificateSha256: String = signingCertificateSha256
        .replace(":", "")
        .lowercase()

    /** Human-readable identity that remains useful when two builds use the same package name. */
    val displayName: String
        get() = "$packageName@${signingCertificateSha256.take(DISPLAY_DIGEST_LENGTH)}"

    init {
        require(PACKAGE_NAME.matches(this.packageName)) { "Invalid provider package name" }
        require(SHA_256.matches(this.signingCertificateSha256)) {
            "Signing certificate digest must be a SHA-256 hex digest"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is MediaTransformProviderId &&
            packageName == other.packageName &&
            signingCertificateSha256 == other.signingCertificateSha256

    override fun hashCode(): Int = 31 * packageName.hashCode() + signingCertificateSha256.hashCode()

    override fun toString(): String = displayName

    private companion object {
        const val DISPLAY_DIGEST_LENGTH = 12
        val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
        val SHA_256 = Regex("[0-9a-f]{64}")
    }
}
