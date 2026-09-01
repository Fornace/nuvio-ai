package com.nuvio.tv.core.profile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Test seam for consumers that only need the active-profile flow. */
interface ActiveProfileProvider {
    val activeProfileId: StateFlow<Int>
}

@JvmSuppressWildcards
fun ProfileManager.asActiveProfileProvider(): ActiveProfileProvider = object : ActiveProfileProvider {
    override val activeProfileId: StateFlow<Int> = this@asActiveProfileProvider.activeProfileId
}

/** In-memory fake used by JVM unit tests. */
class FakeActiveProfileProvider(initial: Int = 1) : ActiveProfileProvider {
    val mutable = MutableStateFlow(initial)
    override val activeProfileId: StateFlow<Int> = mutable
}
