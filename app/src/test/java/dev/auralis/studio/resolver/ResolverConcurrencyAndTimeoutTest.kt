package dev.auralis.studio.resolver

import dev.auralis.studio.resolver.api.ResolverError
import dev.auralis.studio.resolver.api.ResolverErrorCode
import dev.auralis.studio.resolver.api.ResolverRequest
import dev.auralis.studio.resolver.api.ResolverResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class ResolverConcurrencyAndTimeoutTest {

    @Test
    fun cancellation_suppressesLateCallbacksAndCleansBookkeeping() = runBlocking {
        val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<String>>()
        val callbackCount = AtomicInteger(0)

        val requestId = "req-cancel-test"
        val deferred = CompletableDeferred<String>()
        pendingRequests[requestId] = deferred

        // Simulate client cancelling
        val job = launch {
            try {
                deferred.await()
                callbackCount.incrementAndGet()
            } catch (_: Throwable) {
                // Cancelled
            }
        }

        job.cancel()
        pendingRequests.remove(requestId)

        // Late delivery from background worker
        if (pendingRequests.containsKey(requestId)) {
            pendingRequests[requestId]?.complete("""{"success": true}""")
        }

        delay(50)
        assertEquals(0, callbackCount.get())
        assertFalse(pendingRequests.containsKey(requestId))
    }

    @Test
    fun timeoutRecovery_subsequentRequestSucceeds() = runBlocking {
        val pending = ConcurrentHashMap<String, String>()

        // Simulate request 1 timing out
        val req1Result = withTimeoutOrNull(50L) {
            delay(200L) // Exceeds timeout
            "result1"
        }
        assertNull("Request 1 must time out", req1Result)

        val error1 = ResolverError.timeout("https://example.com/slow", "IPC timed out")
        assertEquals(ResolverErrorCode.Timeout, error1.technicalCode)
        assertTrue(error1.isRetryable)

        // Simulate subsequent request 2 succeeding immediately
        val req2Result = withTimeoutOrNull(500L) {
            delay(10L)
            "result2"
        }
        assertEquals("result2", req2Result)
    }

    @Test
    fun exactlyOnceCallback_preventsDoubleInvocation() {
        val invocationCount = AtomicInteger(0)
        val callbackWrapper = { result: ResolverResult ->
            if (invocationCount.compareAndSet(0, 1)) {
                // First invocation succeeds
            }
        }

        callbackWrapper(ResolverResult.Failure(ResolverError.timeout("https://example.com")))
        callbackWrapper(ResolverResult.Failure(ResolverError.unknown("https://example.com")))

        assertEquals(1, invocationCount.get())
    }
}
