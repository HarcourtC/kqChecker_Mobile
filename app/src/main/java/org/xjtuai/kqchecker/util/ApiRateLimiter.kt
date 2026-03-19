package org.xjtuai.kqchecker.util

import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory sliding window limiter per API key.
 * Current policy: 5 requests per 10 minutes for each API key.
 */
object ApiRateLimiter {
    private const val MAX_REQUESTS = 5
    private const val WINDOW_MILLIS = 10 * 60 * 1000L

    private data class Bucket(
        val timestamps: ArrayDeque<Long> = ArrayDeque(),
        val lock: Any = Any()
    )

    private val buckets = ConcurrentHashMap<String, Bucket>()

    fun tryAcquire(apiKey: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val bucket = buckets.computeIfAbsent(apiKey) { Bucket() }
        synchronized(bucket.lock) {
            while (bucket.timestamps.isNotEmpty()) {
                val head = bucket.timestamps.first()
                if (nowMillis - head >= WINDOW_MILLIS) {
                    bucket.timestamps.removeFirst()
                } else {
                    break
                }
            }
            if (bucket.timestamps.size >= MAX_REQUESTS) {
                return false
            }
            bucket.timestamps.addLast(nowMillis)
            return true
        }
    }
}
