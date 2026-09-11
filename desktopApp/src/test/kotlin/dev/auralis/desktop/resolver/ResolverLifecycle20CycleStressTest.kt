package dev.auralis.desktop.resolver

import dev.auralis.studio.resolver.api.ResolverRequest
import dev.auralis.studio.resolver.api.ResolverResult
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ResolverLifecycle20CycleStressTest {

    @Test
    fun test20CycleResolverLifecycleStability() = runBlocking {
        println("=== STARTING 20-CYCLE RESOLVER LIFECYCLE STRESS TEST ===")

        val latencies = mutableListOf<Long>()
        val pids = mutableListOf<Long>()
        val pythonRamReadings = mutableListOf<Long>()

        for (cycle in 1..20) {
            val resolver = DesktopMediaResolver()
            assertEquals(ResolverLifecycleState.STOPPED, resolver.state.value)

            val elapsed = measureTimeMillis {
                val health = resolver.deepHealthCheck()
                assertTrue(health.pythonReady, "Cycle $cycle: Python should be ready")
                assertTrue(health.ytDlpReady, "Cycle $cycle: yt-dlp should be available")
            }
            latencies.add(elapsed)

            // Inspect the running worker
            val currentPid = getWorkerPidViaReflection(resolver)
            assertNotNull(currentPid, "Cycle $cycle: Worker process PID should exist")
            pids.add(currentPid)

            val workerRam = getProcessWorkingSetBytes(currentPid)
            if (workerRam > 0) {
                pythonRamReadings.add(workerRam)
            }

            // Test an inspection request through worker
            val dummyReq = ResolverRequest(
                sourceUrl = "https://example.com/audio.mp3",
                sourceId = "cycle-$cycle",
            )
            val result = resolver.inspect(dummyReq)
            // Even if offline/dummy, resolver must respond with Success or Failure (no hanging)
            assertNotNull(result)

            // Shutdown worker
            resolver.shutdownWorker()
            assertEquals(ResolverLifecycleState.STOPPED, resolver.state.value)

            // Verify process is dead
            val isAlive = isProcessAlive(currentPid)
            assertFalse(isAlive, "Cycle $cycle: Worker PID $currentPid must be terminated")

            println("Cycle $cycle/20: PID=$currentPid, Latency=${elapsed}ms, ChildRAM=${workerRam / (1024 * 1024)}MB - OK")
        }

        // Verify all 20 cycles ran
        assertEquals(20, latencies.size)
        assertEquals(20, pids.size)

        // Verify worker PIDs changed across cycles (new process spawned per cycle)
        val uniquePids = pids.toSet()
        assertEquals(20, uniquePids.size, "Each cycle must spawn a fresh isolated process")

        // Verify zero orphan python processes from the resolver test remain
        val remainingSpawnedPids = pids.filter { pid ->
            ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
        }
        println("Remaining spawned worker processes after 20 cycles: ${remainingSpawnedPids.size}")
        assertEquals(0, remainingSpawnedPids.size, "None of the spawned worker processes may remain alive")

        val avgLatency = latencies.average()
        val minLatency = latencies.minOrNull() ?: 0L
        val maxLatency = latencies.maxOrNull() ?: 0L
        val avgChildRamMb = if (pythonRamReadings.isNotEmpty()) {
            pythonRamReadings.average() / (1024.0 * 1024.0)
        } else {
            0.0
        }

        println("=== 20-CYCLE TEST SUMMARY ===")
        println("Cycles Completed: 20/20")
        println("Orphan Processes: 0")
        println("Min Latency: ${minLatency}ms")
        println("Avg Latency: ${avgLatency.toInt()}ms")
        println("Max Latency: ${maxLatency}ms")
        println("Avg Child Worker RAM: ${"%.2f".format(avgChildRamMb)} MB")
        println("=============================")
    }

    private fun getWorkerPidViaReflection(resolver: DesktopMediaResolver): Long? {
        return try {
            val field = DesktopMediaResolver::class.java.getDeclaredField("workerProcess")
            field.isAccessible = true
            val proc = field.get(resolver) as? Process
            proc?.pid()
        } catch (_: Exception) {
            null
        }
    }

    private fun isProcessAlive(pid: Long): Boolean {
        return try {
            ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
        } catch (_: Exception) {
            false
        }
    }

    private fun getProcessWorkingSetBytes(pid: Long): Long {
        return try {
            val pb = ProcessBuilder("powershell", "-NoProfile", "-Command", "(Get-Process -Id $pid -ErrorAction SilentlyContinue).WorkingSet64")
            val proc = pb.start()
            val text = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            text.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun countRunningPythonProcesses(): Int {
        return try {
            val pb = ProcessBuilder("powershell", "-NoProfile", "-Command", "@(Get-Process -Name 'python' -ErrorAction SilentlyContinue).Count")
            val proc = pb.start()
            val text = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            text.toIntOrNull() ?: 0
        } catch (_: Exception) {
            0
        }
    }
}
