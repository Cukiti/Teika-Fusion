package com.cukiti.teikafusion.ui

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class IrisSoundTimingTest {
    private suspend fun TestScope.withIris(test: suspend TestScope.(CoroutineScope) -> Unit) {
        var nanos = 0L
        val clock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                delay(16)
                nanos += 16_000_000
                return onFrame(nanos)
            }
        }
        val owner = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext[Job]) + clock)
        Iris.progress.snapTo(1f)
        Iris.scope = owner
        Iris.reduced = false
        try { test(owner) } finally {
            owner.cancel()
            advanceUntilIdle()
            Iris.scope = null
            Iris.onStart = null
            Iris.onOpen = null
            Iris.reduced = false
            Iris.progress.snapTo(1f)
        }
    }

    @Test fun cuesFollowActualCloseActionHoldAndOpen() = runTest {
        withIris {
            val events = mutableListOf<String>()
            Iris.onStart = { events += "close" }
            Iris.onOpen = {
                assertEquals(0f, Iris.progress.value)
                assertTrue(testScheduler.currentTime >= 470)
                events += "open"
            }
            Iris.run { events += "action" }
            advanceUntilIdle()
            assertEquals(listOf("close", "action", "open"), events)
            assertEquals(1f, Iris.progress.value)
        }
    }

    @Test fun doubleTapDoesNotDuplicateCuesOrNavigation() = runTest {
        withIris {
            val events = mutableListOf<String>()
            Iris.onStart = { events += "close" }
            Iris.onOpen = { events += "open" }
            Iris.run { events += "action" }
            Iris.run { events += "duplicate" }
            advanceUntilIdle()
            assertEquals(listOf("close", "action", "open"), events)
        }
    }

    @Test fun reducedEffectsRunsActionWithoutEitherCue() = runTest {
        withIris {
            var actions = 0
            Iris.reduced = true
            Iris.onStart = { error("Closing cue under reduced effects") }
            Iris.onOpen = { error("Opening cue under reduced effects") }
            Iris.run { actions++ }
            advanceUntilIdle()
            assertEquals(1, actions)
        }
    }

    @Test fun inactiveOwnerRunsActionWithoutSoundOrStuckBusy() = runTest {
        withIris { owner ->
            var actions = 0
            owner.cancel()
            Iris.onStart = { error("Closing cue for inactive owner") }
            Iris.onOpen = { error("Opening cue for inactive owner") }
            Iris.run { actions++ }
            assertEquals(1, actions)
        }
    }

    @Test fun cancellationDuringClosingKeepsActionAndSuppressesOpening() = runTest {
        withIris { owner ->
            var actions = 0
            var opened = 0
            Iris.onOpen = { opened++ }
            Iris.run { actions++ }
            runCurrent()
            owner.cancel()
            advanceUntilIdle()
            assertEquals(1, actions)
            assertEquals(0, opened)
            assertEquals(1f, Iris.progress.value)
            Iris.scope = null
            Iris.run { actions++ }
            assertEquals("Cancellation must release busy", 2, actions)
        }
    }

    @Test fun immediateDisposalBeforeFirstFrameCannotLeaveBusyStuck() = runTest {
        withIris { owner ->
            var actions = 0
            var opened = 0
            Iris.onOpen = { opened++ }
            Iris.run { actions++ }
            owner.cancel()
            advanceUntilIdle()
            assertEquals(1, actions)
            assertEquals(0, opened)
            assertEquals(1f, Iris.progress.value)
            Iris.scope = null
            Iris.run { actions++ }
            assertEquals(2, actions)
        }
    }

    @Test fun cancellationAfterActionDoesNotReplayItOrEmitLateOpening() = runTest {
        withIris { owner ->
            var actions = 0
            var opened = 0
            Iris.onOpen = { opened++ }
            Iris.run { actions++ }
            repeat(30) { if (actions == 0) { advanceTimeBy(16); runCurrent() } }
            assertEquals(1, actions)
            owner.cancel()
            advanceUntilIdle()
            assertEquals(1, actions)
            assertEquals(0, opened)
            assertEquals(1f, Iris.progress.value)
        }
    }

    @Test fun detachedOwnerCannotPlayOpeningCue() = runTest {
        withIris {
            var opened = 0
            Iris.onOpen = { opened++ }
            Iris.run { Iris.scope = null }
            advanceUntilIdle()
            assertEquals(0, opened)
        }
    }

    @Test fun reducingEffectsDuringHoldSuppressesOpeningCue() = runTest {
        withIris {
            var opened = 0
            Iris.onOpen = { opened++ }
            Iris.run { Iris.reduced = true }
            advanceUntilIdle()
            assertEquals(0, opened)
        }
    }
}
