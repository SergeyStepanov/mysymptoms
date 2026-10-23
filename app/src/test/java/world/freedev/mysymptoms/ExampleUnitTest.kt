package world.freedev.mysymptoms

import org.junit.Assert.*
import org.junit.Test

class TimeSelectionTest {
    @Test
    fun presetSubtractsTheRequestedMinutes() {
        val now = 1_700_000_000_000L

        assertEquals(now, timestampMinutesAgo(now, 0))
        assertEquals(now - 5 * 60_000L, timestampMinutesAgo(now, 5))
        assertEquals(now - 60 * 60_000L, timestampMinutesAgo(now, 60))
    }
}