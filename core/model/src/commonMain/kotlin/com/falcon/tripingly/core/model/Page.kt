package com.falcon.tripingly.core.model

/** One page of a list the server pages through; pass [nextCursor] for the next one, null when this was the last. */
data class Page<T>(val items: List<T>, val nextCursor: String?)
