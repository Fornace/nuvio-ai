package com.nuvio.tv.domain.repository

import com.nuvio.tv.domain.model.Subtitle

/** Stable classification for a failed per-addon subtitle lookup. */
enum class SubtitleLookupErrorKind(val value: String) {
    NETWORK("network"),
    TIMEOUT("timeout"),
    HTTP_STATUS("http-status"),
    PARSE("parse")
}

/** A failure from one addon; other addons may still have succeeded. */
data class SubtitleLookupFailure(
    val addonId: String,
    val addonName: String,
    val kind: SubtitleLookupErrorKind,
    val httpStatus: Int? = null,
    val message: String? = null
)

/** Partial-success result for a parallel subtitle addon lookup. */
data class SubtitleLookupResult(
    val subtitles: List<Subtitle>,
    val failures: List<SubtitleLookupFailure>
)

interface SubtitleRepository {
    /**
     * Fetches subtitles from all installed addons that support subtitles.
     *
     * This compatibility method returns successful records only. Use
     * [lookupSubtitlesDetailed] when per-addon failures must be surfaced.
     */
    suspend fun getSubtitles(
        type: String,
        id: String,
        videoId: String? = null,
        videoHash: String? = null,
        videoSize: Long? = null,
        filename: String? = null,
        onProgress: ((completed: Int, total: Int, addonName: String?) -> Unit)? = null,
        onSubtitlesEmitted: ((List<Subtitle>) -> Unit)? = null
    ): List<Subtitle>

    /**
     * Fetches all subtitle addons in parallel with a 20 second timeout per
     * addon, preserving successful records and structured per-addon failures.
     */
    suspend fun lookupSubtitlesDetailed(
        type: String,
        id: String,
        videoId: String? = null,
        videoHash: String? = null,
        videoSize: Long? = null,
        filename: String? = null,
        onProgress: ((completed: Int, total: Int, addonName: String?) -> Unit)? = null,
        onSubtitlesEmitted: ((List<Subtitle>) -> Unit)? = null
    ): SubtitleLookupResult
}
