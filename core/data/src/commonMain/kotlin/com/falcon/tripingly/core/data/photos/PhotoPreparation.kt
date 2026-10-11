package com.falcon.tripingly.core.data.photos

import kotlin.math.max
import kotlin.math.min

/**
 * Shrinks a picked image so its longest edge is at most [maxEdge] pixels, turns it upright and encodes it as
 * JPEG with [quality]. Only the pixels are kept: no location, camera or other metadata leaves the phone.
 * Null when the bytes aren't an image the platform can read.
 */
internal expect fun prepareJpeg(image: ByteArray, maxEdge: Int, quality: Int): ByteArray?

/** The scale that fits [width] × [height] into [maxEdge]; never enlarges. */
internal fun fitScale(width: Int, height: Int, maxEdge: Int): Float = min(1f, maxEdge.toFloat() / max(width, height))

/** The largest power of two an image can be decoded at that still keeps its longest edge at [maxEdge] or more. */
internal fun decodeSampleSize(width: Int, height: Int, maxEdge: Int): Int {
    var sample = 1
    while (max(width, height) / (sample * 2) >= maxEdge) sample *= 2
    return sample
}
