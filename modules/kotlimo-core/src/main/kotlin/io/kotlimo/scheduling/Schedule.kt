package io.kotlimo.scheduling

import java.time.LocalDateTime

class ScheduledEvent(val name: String, val callback: () -> Unit) {
    var expression: String = "* * * * *"
        private set

    fun everyMinute(): ScheduledEvent {
        expression = "* * * * *"
        return this
    }

    fun hourly(): ScheduledEvent {
        expression = "0 * * * *"
        return this
    }

    fun daily(): ScheduledEvent {
        expression = "0 0 * * *"
        return this
    }

    fun cron(expression: String): ScheduledEvent {
        this.expression = expression
        return this
    }

    fun isDue(now: LocalDateTime = LocalDateTime.now()): Boolean {
        val parts = expression.trim().split(Regex("\\s+"))
        if (parts.size != 5) return false
        val minute = now.minute
        val hour = now.hour
        val day = now.dayOfMonth
        val month = now.monthValue
        val weekday = now.dayOfWeek.value % 7
        return matches(parts[0], minute) &&
            matches(parts[1], hour) &&
            matches(parts[2], day) &&
            matches(parts[3], month) &&
            matches(parts[4], weekday)
    }

    fun run() = callback()

    private fun matches(field: String, value: Int): Boolean {
        if (field == "*") return true
        return field.split(',').any { piece ->
            when {
                piece.contains('/') -> {
                    val (range, stepRaw) = piece.split('/', limit = 2)
                    val step = stepRaw.toIntOrNull() ?: return@any false
                    val start = if (range == "*" || range.isEmpty()) 0 else range.substringBefore('-').toIntOrNull() ?: return@any false
                    value >= start && (value - start) % step == 0
                }
                piece.contains('-') -> {
                    val (from, to) = piece.split('-', limit = 2)
                    val start = from.toIntOrNull() ?: return@any false
                    val end = to.toIntOrNull() ?: return@any false
                    value in start..end
                }
                else -> piece.toIntOrNull() == value
            }
        }
    }
}

class Schedule {
    private val events = mutableListOf<ScheduledEvent>()

    fun events(): List<ScheduledEvent> = events.toList()

    fun call(name: String = "callback", callback: () -> Unit): ScheduledEvent {
        val event = ScheduledEvent(name, callback)
        events += event
        return event
    }

    fun dueEvents(now: LocalDateTime = LocalDateTime.now()): List<ScheduledEvent> =
        events.filter { it.isDue(now) }

    fun runDue(now: LocalDateTime = LocalDateTime.now()): Int {
        val due = dueEvents(now)
        due.forEach { it.run() }
        return due.size
    }
}
