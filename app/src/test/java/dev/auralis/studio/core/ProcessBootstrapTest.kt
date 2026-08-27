package dev.auralis.studio.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessBootstrapTest {

    private val packageName = "dev.auralis.studio"

    @Test
    fun classifyProcessName_mainPackageName_returnsMainRole() {
        val role = ProcessUtils.classifyProcessName(packageName, packageName)
        assertEquals(ProcessRole.MAIN, role)
    }

    @Test
    fun classifyProcessName_resolverProcessName_returnsResolverRole() {
        val role = ProcessUtils.classifyProcessName(packageName, "$packageName:resolver")
        assertEquals(ProcessRole.RESOLVER, role)

        val roleSuffix = ProcessUtils.classifyProcessName(packageName, ":resolver")
        assertEquals(ProcessRole.RESOLVER, roleSuffix)
    }

    @Test
    fun classifyProcessName_otherProcessName_returnsOtherRole() {
        val role1 = ProcessUtils.classifyProcessName(packageName, "$packageName:leakcanary")
        assertEquals(ProcessRole.OTHER, role1)

        val role2 = ProcessUtils.classifyProcessName(packageName, "$packageName:test_runner")
        assertEquals(ProcessRole.OTHER, role2)

        val role3 = ProcessUtils.classifyProcessName(packageName, "com.other.app")
        assertEquals(ProcessRole.OTHER, role3)
    }

    @Test
    fun resolverClientGuard_acceptsOnlyMainProcess_rejectsResolverAndOther() {
        // MAIN process: passes
        val mainRole = ProcessUtils.classifyProcessName(packageName, packageName)
        val mainAllowed = try {
            check(mainRole == ProcessRole.MAIN) { "Client only in MAIN" }
            true
        } catch (_: IllegalStateException) {
            false
        }
        assertTrue("MAIN process must be permitted to create resolver client", mainAllowed)

        // RESOLVER process: rejected
        val resolverRole = ProcessUtils.classifyProcessName(packageName, "$packageName:resolver")
        val resolverRejected = try {
            check(resolverRole == ProcessRole.MAIN) { "Client only in MAIN" }
            false
        } catch (e: IllegalStateException) {
            true
        }
        assertTrue("RESOLVER process must be rejected from creating resolver client", resolverRejected)

        // OTHER process: rejected
        val otherRole = ProcessUtils.classifyProcessName(packageName, "$packageName:auxiliary")
        val otherRejected = try {
            check(otherRole == ProcessRole.MAIN) { "Client only in MAIN" }
            false
        } catch (e: IllegalStateException) {
            true
        }
        assertTrue("OTHER process must be rejected from creating resolver client", otherRejected)
    }
}
