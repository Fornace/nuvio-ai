package com.nuvio.tv.core.media.provider.host

import java.math.BigInteger

interface ProviderPackageScanner {
    fun installedProviders(packages: Collection<String>): Map<String, InstalledProviderInfo>
}

data class InstalledProviderInfo(
    val versionName: String,
    val versionCode: Long,
    val signingCertSha256Set: Set<String>
) {
    fun isNewer(other: InstalledProviderInfo): Boolean {
        val byName = compareSemverishVersions(versionName, other.versionName)
        return byName > 0 || (byName == 0 && versionCode > other.versionCode)
    }
}

fun compareSemverishVersions(left: String, right: String): Int {
    val leftTokens = versionTokens(left)
    val rightTokens = versionTokens(right)
    val shared = minOf(leftTokens.size, rightTokens.size)
    for (index in 0 until shared) {
        val comparison = compareVersionToken(leftTokens[index], rightTokens[index])
        if (comparison != 0) return comparison
    }
    return when {
        leftTokens.size == rightTokens.size -> 0
        leftTokens.size < rightTokens.size -> compareMissingToRemainder(rightTokens.drop(shared))
        else -> -compareMissingToRemainder(leftTokens.drop(shared))
    }
}

private fun versionTokens(version: String): List<String> =
    Regex("[0-9]+|[A-Za-z]+").findAll(version.removePrefix("v")).map { it.value }.toList()

private fun compareVersionToken(left: String, right: String): Int {
    val leftNumber = left.firstOrNull()?.isDigit() == true
    val rightNumber = right.firstOrNull()?.isDigit() == true
    return when {
        leftNumber && rightNumber -> BigInteger(left).compareTo(BigInteger(right))
        leftNumber -> 1
        rightNumber -> -1
        else -> left.compareTo(right, ignoreCase = true)
    }
}

/** A missing suffix is newer than a pre-release word, and equal to trailing numeric zeroes. */
private fun compareMissingToRemainder(remainder: List<String>): Int = when {
    remainder.all { token -> token.all(Char::isDigit) && token.all { it == '0' } } -> 0
    remainder.firstOrNull()?.firstOrNull()?.isLetter() == true -> 1
    else -> -1
}
