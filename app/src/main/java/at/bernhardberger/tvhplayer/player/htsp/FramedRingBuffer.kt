package at.bernhardberger.tvhplayer.player.htsp

import kotlin.math.min

/** Byte ring that retains the boundaries of each framed HTSP message. */
internal class FramedRingBuffer(capacity: Int) {
    private data class Segment(
        var remaining: Int,
        var partiallyRead: Boolean = false,
    )

    private val buffer = ByteArray(capacity)
    private val segments = ArrayDeque<Segment>()
    private var head = 0
    private var tail = 0
    private var size = 0

    fun size(): Int = size

    fun free(): Int = buffer.size - size

    fun clear() {
        head = 0
        tail = 0
        size = 0
        segments.clear()
    }

    fun clearCompleteFramesForSeek() {
        val partial = segments.firstOrNull()?.takeIf { it.partiallyRead }
        if (partial == null) {
            clear()
            return
        }

        val retainedBytes = partial.remaining
        tail = (head + retainedBytes) % buffer.size
        size = retainedBytes
        segments.clear()
        segments.addLast(partial)
    }

    fun write(
        source: ByteArray,
        offset: Int,
        length: Int,
        spacePolicy: (needed: Int) -> Boolean,
    ): Boolean {
        if (length <= 0) return true
        if (length > buffer.size || !spacePolicy(length)) return false

        var remaining = length
        var sourcePosition = offset
        while (remaining > 0) {
            val chunk = min(remaining, buffer.size - tail)
            System.arraycopy(source, sourcePosition, buffer, tail, chunk)
            tail = (tail + chunk) % buffer.size
            size += chunk
            sourcePosition += chunk
            remaining -= chunk
        }
        segments.addLast(Segment(remaining = length))
        return true
    }

    fun read(destination: ByteArray, offset: Int, length: Int): Int {
        if (length <= 0 || size == 0) return 0
        val readable = readableBytes(length)

        var remaining = readable
        var destinationPosition = offset
        while (remaining > 0) {
            val chunk = min(remaining, buffer.size - head)
            System.arraycopy(buffer, head, destination, destinationPosition, chunk)
            head = (head + chunk) % buffer.size
            size -= chunk
            destinationPosition += chunk
            remaining -= chunk
        }
        consumeSegments(readable)
        return readable
    }

    private fun readableBytes(maximum: Int): Int {
        var total = 0
        for (segment in segments) {
            if (total == 0 && segment.remaining > maximum) return maximum
            if (total + segment.remaining > maximum) break
            total += segment.remaining
        }
        return total
    }

    private fun consumeSegments(byteCount: Int) {
        var remaining = byteCount
        while (remaining > 0) {
            val segment = segments.first()
            val consumed = min(remaining, segment.remaining)
            segment.remaining -= consumed
            remaining -= consumed
            if (segment.remaining == 0) {
                segments.removeFirst()
            } else {
                segment.partiallyRead = true
            }
        }
    }
}
