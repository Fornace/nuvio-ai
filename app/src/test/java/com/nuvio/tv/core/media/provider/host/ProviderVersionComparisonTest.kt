package com.nuvio.tv.core.media.provider.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderVersionComparisonTest {
    @Test
    fun `dotted versions compare numerically per segment`() {
        assertTrue(compareSemverishVersions("0.1.0", "0.2.0") < 0)
        assertTrue(compareSemverishVersions("0.2.0", "0.10.0") < 0)
        assertTrue(compareSemverishVersions("0.10.0", "0.2.0") > 0)
        assertEquals(0, compareSemverishVersions("0.1.0", "0.1.0"))
        assertTrue(compareSemverishVersions("1.0.0", "0.99.99") > 0)
        assertEquals(0, compareSemverishVersions("0.1", "0.1.0"))
    }

    @Test
    fun `release versions are newer than their pre releases`() {
        assertTrue(compareSemverishVersions("0.1.0-preview1", "0.1.0") < 0)
        assertTrue(compareSemverishVersions("0.1.0-preview1", "0.1.0-preview2") < 0)
        assertTrue(compareSemverishVersions("0.1.0", "0.1.1-preview1") < 0)
        assertEquals(0, compareSemverishVersions("0.1.0", "0.1.0.0"))
    }

    @Test
    fun `installed info newness uses version name then version code`() {
        val current = InstalledProviderInfo("0.10.0", 12L, emptySet())

        assertTrue(current.isNewer(InstalledProviderInfo("0.2.0", 99L, emptySet())))
        assertFalse(current.isNewer(InstalledProviderInfo("0.10.0", 99L, emptySet())))
        assertTrue(current.isNewer(InstalledProviderInfo("0.2.0", 12L, emptySet())))

        val sameNameLowerCode = InstalledProviderInfo("0.10.0", 3L, emptySet())
        assertFalse(sameNameLowerCode.isNewer(current))
        assertTrue(current.isNewer(sameNameLowerCode))

        assertFalse(InstalledProviderInfo("0.1.0", 1L, emptySet()).isNewer(InstalledProviderInfo("0.1.0", 1L, emptySet())))
    }
}
