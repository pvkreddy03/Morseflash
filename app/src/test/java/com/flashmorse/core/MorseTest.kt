package com.flashmorse.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MorseTest {
    @Test fun sos() { assertEquals("... --- ...", Morse.encode("SOS")) }
    @Test fun roundTrip() { assertEquals("HELLO 123", Morse.decode(Morse.encode("HELLO 123"))) }
    @Test fun timing() {
        assertEquals(
            listOf(1,1,1,1,1,3,3,1,3,1,3,3,1,1,1,1,1),
            Morse.pulses("SOS").map { it.units }
        )
    }
    @Test fun pulseDecoder() {
        val decoder = LightDecoder(200)
        var now = 1000L
        decoder.accept(false, now)
        Morse.pulses("SOS").forEach { pulse ->
            decoder.accept(pulse.on, now)
            now += pulse.units * 200L
            decoder.accept(pulse.on, now)
        }
        decoder.accept(false, now)
        now += 1500
        decoder.accept(false, now)
        decoder.finish()
        assertEquals("SOS", decoder.text)
    }
}
