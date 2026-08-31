@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.tv.ui.screens.player.subtitle

import androidx.media3.common.text.Cue

/**
 * Immutable, append-only interval index over timed cues, shared by [StaticFileCueSource] and
 * [MutableTimedCueStore].
 *
 * Structure: a treap (randomized balanced binary search tree) keyed by `startUs` with the classic
 * interval-tree augmentation — every node stores the maximum `endUs` in its subtree. A stabbing
 * query at `positionUs` costs `O(log n + k)` expected time (k = active cues at that position),
 * and finds its answers without ever scanning the cue list linearly.
 *
 * `epoch` is part of the index key (cues of different epochs never compare equal) so that
 * [MutableTimedCueStore.activeCues] can filter by seek epoch inside the index itself. The
 * caller-facing window semantics are `[startUs, endUs)`: active means
 * `startUs <= positionUs < endUs`.
 *
 * Thread-safety model: this class is not internally synchronized. The owning store either
 * builds it single-threaded before publication (see [StaticFileCueSource]) or guards it with
 * an external lock (see [MutableTimedCueStore]).
 */
internal class CueIntervalIndex {

    private class Node(
        val cue: TimedCue,
        val renderedCue: Cue,
        val priority: Int
    ) {
        var left: Node? = null
        var right: Node? = null

        /** Max `endUs` in this subtree (including this node). */
        var subtreeMaxEnd = cue.endUs
    }

    private var root: Node? = null
    private var size = 0

    val cueCount: Int get() = size

    /**
     * Inserts [cue], keeping the tree keyed by `(startUs, epoch, id)` with max-end augmentation.
     * Duplicate keys (same epoch + id) replace the previous node instead of adding a second one.
     */
    fun add(cue: TimedCue, renderedCue: Cue = cue.toMedia3Cue()) {
        val key = Key(cue.startUs, cue.epoch, cue.id)
        val (without, removed) = removeNode(root, key)
        if (removed != null) size--
        root = insert(without, Node(cue, renderedCue, tieBreaker.next()), key)
        size++
    }

    /** Removes [cue]'s exact `(startUs, epoch, id)` key; returns whether it existed. */
    fun remove(cue: TimedCue): Boolean {
        val (next, removed) = removeNode(root, Key(cue.startUs, cue.epoch, cue.id))
        root = next
        if (removed == null) return false
        size--
        return true
    }

    /**
     * Cues active at `positionUs` under `epoch`, ordered by start time then id.
     * `O(log n + k)` expected.
     */
    fun activeCues(positionUs: Long, epoch: Long): List<Cue> {
        val out = ArrayList<Cue>(4)
        collectActive(root, positionUs, epoch, out)
        return out
    }

    /** Read-only snapshot in index order (start time, epoch, id); allocates a fresh list. */
    fun snapshot(): List<TimedCue> {
        val out = ArrayList<TimedCue>(size)
        collectSnapshot(root, out)
        return out
    }

    // ---------------------------------------------------------------- internals

    private data class Key(val startUs: Long, val epoch: Long, val id: String)

    private class SplitResult(val left: Node?, val middle: Node?, val right: Node?)

    /** Splits `node` into (< key, == key, > key) by the treap priority-heap merge trick. */
    private fun split(node: Node?, key: Key): SplitResult {
        if (node == null) return SplitResult(null, null, null)
        return if (compare(nodeKey(node), key) < 0) {
            val sub = split(node.right, key)
            node.right = sub.left
            update(node)
            SplitResult(node, sub.middle, sub.right)
        } else if (compare(nodeKey(node), key) > 0) {
            val sub = split(node.left, key)
            node.left = sub.right
            update(node)
            SplitResult(sub.left, sub.middle, node)
        } else {
            val left = node.left
            val right = node.right
            node.left = null
            node.right = null
            update(node)
            SplitResult(left, node, right)
        }
    }

    private fun merge(left: Node?, right: Node?): Node? {
        if (left == null) return right
        if (right == null) return left
        return if (left.priority >= right.priority) {
            left.right = merge(left.right, right)
            update(left)
            left
        } else {
            right.left = merge(left, right.left)
            update(right)
            right
        }
    }

    private fun insert(node: Node?, fresh: Node, key: Key): Node? {
        val parts = split(node, key)
        // parts.middle can only be non-null when the caller did not remove the key first.
        return merge(merge(parts.left, fresh), merge(parts.middle, parts.right))
    }

    private fun removeNode(node: Node?, key: Key): Pair<Node?, Node?> {
        val parts = split(node, key)
        return merge(parts.left, parts.right) to parts.middle
    }

    private fun update(node: Node) {
        var maxEnd = node.cue.endUs
        node.left?.let { if (it.subtreeMaxEnd > maxEnd) maxEnd = it.subtreeMaxEnd }
        node.right?.let { if (it.subtreeMaxEnd > maxEnd) maxEnd = it.subtreeMaxEnd }
        node.subtreeMaxEnd = maxEnd
    }

    private fun collectActive(node: Node?, positionUs: Long, epoch: Long, out: MutableList<Cue>) {
        if (node == null || node.subtreeMaxEnd <= positionUs) return
        // Node and its right subtree start after the probe; only the left subtree can match.
        if (node.cue.startUs > positionUs) {
            collectActive(node.left, positionUs, epoch, out)
            return
        }
        collectActive(node.left, positionUs, epoch, out)
        if (node.cue.epoch == epoch && positionUs < node.cue.endUs) {
            out.add(node.renderedCue)
        }
        collectActive(node.right, positionUs, epoch, out)
    }

    private fun collectSnapshot(node: Node?, out: MutableList<TimedCue>) {
        if (node == null) return
        collectSnapshot(node.left, out)
        out.add(node.cue)
        collectSnapshot(node.right, out)
    }

    private fun compare(a: Key, b: Key): Int {
        if (a.startUs != b.startUs) return if (a.startUs < b.startUs) -1 else 1
        if (a.epoch != b.epoch) return if (a.epoch < b.epoch) -1 else 1
        return a.id.compareTo(b.id)
    }

    private fun nodeKey(node: Node) = Key(node.cue.startUs, node.cue.epoch, node.cue.id)

    private object tieBreaker {
        private val random = java.util.Random(0x4E5556494FL) // "NUVIO"

        fun next(): Int = random.nextInt()
    }
}
