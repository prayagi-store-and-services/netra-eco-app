package com.prayagi.netraeco

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class EcoActionsTest {
    @Test fun twoEntryPointsShareBusyAndErrorUntilWorkCompletes() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val actions = EcoInstallActions()
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        actions.start(scope, "same.apk") { calls++; gate.await(); error("fixture") }
        assertTrue(actions.state("same.apk").busy)
        actions.start(scope, "same.apk") { calls++ }
        assertEquals(1, calls)
        gate.complete(Unit); yield()
        assertFalse(actions.state("same.apk").busy)
        assertNotNull(actions.state("same.apk").error)
        scope.cancel()
    }
    @Test fun updateResultRemainsInScreenHolderAfterDeferredCompletion() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val check = EcoUpdateCheck()
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        check.start(scope) { calls++; gate.await(); "Up to date" to null }
        check.start(scope) { calls++; "Wrong second result" to null }
        assertTrue(check.busy); assertEquals(1, calls)
        gate.complete(Unit); yield()
        assertFalse(check.busy); assertEquals("Up to date", check.message)
        scope.cancel()
    }
}
