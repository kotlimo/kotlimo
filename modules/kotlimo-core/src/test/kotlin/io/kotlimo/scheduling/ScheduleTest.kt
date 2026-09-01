package io.kotlimo.scheduling

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ScheduleTest {
    @Test
    fun `hourly events are due at minute zero`() {
        val schedule = Schedule()
        var ran = 0
        schedule.call("tick") { ran++ }.hourly()
        val noon = LocalDateTime.of(2026, 9, 1, 12, 0)
        val half = LocalDateTime.of(2026, 9, 1, 12, 30)
        assertTrue(schedule.dueEvents(noon).isNotEmpty())
        assertFalse(schedule.dueEvents(half).isNotEmpty())
        assertEquals(1, schedule.runDue(noon))
        assertEquals(1, ran)
    }

    @Test
    fun `cron fields match comma lists`() {
        val event = ScheduledEvent("x") {}.cron("0,30 12 * * *")
        assertTrue(event.isDue(LocalDateTime.of(2026, 1, 1, 12, 30)))
        assertFalse(event.isDue(LocalDateTime.of(2026, 1, 1, 12, 15)))
    }
}
