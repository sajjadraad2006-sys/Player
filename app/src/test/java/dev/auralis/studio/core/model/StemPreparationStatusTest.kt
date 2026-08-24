package dev.auralis.studio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StemPreparationStatusTest {
    @Test fun unavailableNeverInventsProgress() {
        val state = StemPreparationStatus.unavailable("model missing")
        assertEquals(StemRuntimeState.UNAVAILABLE, state.state)
        assertNull(state.progress)
    }

    @Test fun workerProgressIsClamped() {
        assertEquals(100, StemPreparationStatus.preparing(140).progress)
        assertEquals(0, StemPreparationStatus.preparing(-5).progress)
    }
}
