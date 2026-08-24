package dev.auralis.studio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class OrbitMathTest {
    @Test fun clockwiseAndCounterClockwiseAreOpposite() {
        val phase = PI / 3.0
        assertEquals(-OrbitMath.pan(phase, OrbitDirection.CLOCKWISE), OrbitMath.pan(phase, OrbitDirection.COUNTER_CLOCKWISE), 0.0001f)
    }

    @Test fun pingPongTravelsLeftRightLeft() {
        assertEquals(-1f, OrbitMath.pan(0.0, OrbitDirection.PING_PONG), 0.0001f)
        assertEquals(1f, OrbitMath.pan(PI, OrbitDirection.PING_PONG), 0.0001f)
        assertEquals(-1f, OrbitMath.pan(2.0 * PI, OrbitDirection.PING_PONG), 0.0001f)
    }

    @Test fun rateControlsPeriod() {
        var phase = 0.0
        repeat(48_000) { phase = OrbitMath.advance(phase, 1f, 48_000) }
        assertTrue(phase < 0.001 || phase > 2.0 * PI - 0.001)
    }
}
