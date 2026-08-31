package com.nuvio.tv.core.media.provider

import kotlinx.coroutines.CoroutineDispatcher

/** Explicit provider execution lane; tests supply a deterministic test dispatcher. */
class MediaProviderDispatchers(val processing: CoroutineDispatcher)
