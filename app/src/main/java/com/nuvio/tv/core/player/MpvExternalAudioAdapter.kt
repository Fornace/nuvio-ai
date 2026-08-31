package com.nuvio.tv.core.player

import java.net.URI

/**
 * Sink through which mpv commands are issued; returns mpv's success flag. Top-level so
 * player-runtime callers can implement it without referencing the adapter class.
 */
fun interface MpvCommandSink {
    fun command(vararg args: String): Boolean
}

/**
 * Pure-Kotlin builder for mpv external-audio track commands (Milestone 2, translated-voice
 * overlay). Commands are emitted through an injected [MpvCommandSink] so behavior is fully
 * testable on the JVM with no mpv instance and no Android framework classes.
 *
 * Header safety (round-0 voice/player transport review, probe P7): translated audio is
 * served from a credential-free tokenized `http://127.0.0.1` URL, so attaching it must
 * never require — and this adapter never performs — any mutation of mpv's *global*
 * `http-header-fields` property. That property governs every HTTP fetch mpv makes
 * (external tracks included) and the primary playback path seeds it with the video
 * origin's `Authorization`/`Cookie`; mutating it here would forward those credentials to
 * the loopback artifact host or corrupt primary fetches. Everything this adapter sends is
 * per-file: the URL, title and lang are arguments of the `audio-add` command itself,
 * scoped by mpv to the added track only. No global player state is read or written.
 *
 * The adapter refuses URLs that are not exactly `http://127.0.0.1:<port>/...` (no other
 * host, no DNS names, no userinfo, no query, no fragment) and validates title/lang with
 * [sanitizeHeaderValue] before anything reaches the command sink, so a value like
 * `"x\r\nHost: evil"` can never reach mpv's header serialization.
 */
class MpvExternalAudioAdapter(private val commandSink: MpvCommandSink) {

    /**
     * Adds [url] as an external audio track and selects it, emitting exactly
     * `audio-add <url> select <title> <lang>`. Returns mpv's success flag.
     *
     * [title] is the human-readable track label and [lang] the track language tag
     * (e.g. `it`); both are validated with [sanitizeHeaderValue] first. Per-file options
     * only — no global `http-header-fields` state is touched.
     */
    fun attach(url: String, title: String, lang: String): Boolean {
        require(isLoopbackArtifactUrl(url)) {
            "external audio URL must be a credential-free http://127.0.0.1 URL"
        }
        sanitizeHeaderValue(title)
        sanitizeHeaderValue(lang)
        return commandSink.command("audio-add", url, "select", title, lang)
    }

    /** Removes the external audio track with mpv id [trackId]: `audio-remove <trackId>`. */
    fun remove(trackId: Int): Boolean {
        require(trackId > 0) { "trackId must be a positive mpv track id" }
        return commandSink.command("audio-remove", trackId.toString())
    }

    /** Reloads the external audio track with mpv id [trackId]: `audio-reload <trackId>`. */
    fun reload(trackId: Int): Boolean {
        require(trackId > 0) { "trackId must be a positive mpv track id" }
        return commandSink.command("audio-reload", trackId.toString())
    }
}

/**
 * Validates a value that could reach mpv's HTTP layer (per-file option or header
 * serialization) and returns it unchanged. Rejects CR, LF, NUL, DEL and every other ISO
 * control character (U+0000-U+001F, U+007F-U+009F) by throwing [IllegalArgumentException],
 * which blocks header-injection payloads such as `"x\r\nHost: evil"`. Printable Unicode
 * (titles with accents, non-Latin scripts) is allowed. The failure message carries the
 * offending code point only, never the raw value, so secrets cannot leak through logs.
 */
fun sanitizeHeaderValue(value: String): String {
    value.forEachIndexed { index, ch ->
        require(!ch.isISOControl()) {
            "value contains a control character (U+" +
                ch.code.toString(16).uppercase().padStart(4, '0') +
                " at index $index)"
        }
    }
    return value
}

/**
 * True only for `http` URLs with host `127.0.0.1`, no userinfo, no query and no fragment —
 * the exact shape produced by [LocalMediaArtifactServer]. DNS names (including
 * `localhost`) are rejected so no resolver trick can redirect a track fetch off the
 * loopback interface.
 */
fun isLoopbackArtifactUrl(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    if (!uri.scheme.equals("http", ignoreCase = true)) return false
    if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return false
    return uri.host == LOOPBACK_HOST
}

private const val LOOPBACK_HOST = "127.0.0.1"
