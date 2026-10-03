package com.falcon.tripingly.core.network

import kotlin.uuid.Uuid

/**
 * Marks a content-creating POST as safe to retry: for 24 hours the server answers
 * a retry with the same key and body with the first response instead of creating
 * a duplicate. Declare it on the Ktorfit function:
 * `@Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String`.
 */
const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"

/** A new key per user action; reuse it when retrying that same action. */
fun newIdempotencyKey(): String = Uuid.random().toString()
