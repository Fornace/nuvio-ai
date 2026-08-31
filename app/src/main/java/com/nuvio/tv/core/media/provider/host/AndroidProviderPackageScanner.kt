package com.nuvio.tv.core.media.provider.host

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale

/** Android bridge for package and signing-certificate queries. */
class PackageManagerProviderPackageScanner(
    private val packageManager: PackageManager
) : ProviderPackageScanner {
    override fun installedProviders(packages: Collection<String>): Map<String, InstalledProviderInfo> =
        buildMap {
            packages.distinct().forEach { packageName ->
                val info = packageInfo(packageName) ?: return@forEach
                put(
                    packageName,
                    InstalledProviderInfo(
                        versionName = info.versionName.orEmpty(),
                        versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            info.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            info.versionCode.toLong()
                        },
                        signingCertSha256Set = info.signingCertificateBytes()
                            .map { HostCrypto.sha256Hex(it).uppercase(Locale.ROOT) }
                            .toSet()
                    )
                )
            }
        }

    @Suppress("DEPRECATION")
    private fun packageInfo(packageName: String): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            packageManager.getPackageInfo(packageName, flags)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.signingCertificateBytes(): List<ByteArray> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return signatures.orEmpty().map { it.toByteArray() }
        }
        val details = signingInfo ?: return emptyList()
        val signatures = if (details.hasMultipleSigners()) {
            details.apkContentsSigners
        } else {
            details.signingCertificateHistory
        }
        return signatures.orEmpty().map { it.toByteArray() }
    }
}
