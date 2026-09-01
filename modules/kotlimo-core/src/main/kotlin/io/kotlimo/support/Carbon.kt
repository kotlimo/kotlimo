package io.kotlimo.support

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class Carbon private constructor(private val dateTime: LocalDateTime) {
    fun toDateTime(): LocalDateTime = dateTime

    fun toEpochSecond(): Long = dateTime.atZone(ZoneId.systemDefault()).toEpochSecond()

    fun format(pattern: String = "yyyy-MM-dd HH:mm:ss"): String =
        dateTime.format(DateTimeFormatter.ofPattern(pattern))

    fun toDateString(): String = format("yyyy-MM-dd")

    fun toDateTimeString(): String = format("yyyy-MM-dd HH:mm:ss")

    fun addDays(days: Long): Carbon = Carbon(dateTime.plusDays(days))

    fun subDays(days: Long): Carbon = Carbon(dateTime.minusDays(days))

    fun addHours(hours: Long): Carbon = Carbon(dateTime.plusHours(hours))

    fun addMinutes(minutes: Long): Carbon = Carbon(dateTime.plusMinutes(minutes))

    fun isPast(): Boolean = dateTime.isBefore(LocalDateTime.now())

    fun isFuture(): Boolean = dateTime.isAfter(LocalDateTime.now())

    fun diffInSeconds(other: Carbon = now()): Long =
        ChronoUnit.SECONDS.between(dateTime, other.dateTime)

    override fun toString(): String = toDateTimeString()

    override fun equals(other: Any?): Boolean = other is Carbon && other.dateTime == dateTime

    override fun hashCode(): Int = dateTime.hashCode()

    companion object {
        fun now(): Carbon = Carbon(LocalDateTime.now())

        fun parse(value: String): Carbon = Carbon(LocalDateTime.parse(value.replace(" ", "T")))

        fun of(dateTime: LocalDateTime): Carbon = Carbon(dateTime)

        fun ofEpoch(epochSecond: Long): Carbon =
            Carbon(LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), ZoneId.systemDefault()))
    }
}
