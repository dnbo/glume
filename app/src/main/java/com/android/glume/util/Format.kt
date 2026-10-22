package com.android.glume.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** Display formatting in the design's style ("Saturday 12 July 2023", "2:05", "Oct 2023"). */
object Format {
    private val locale = Locale.ENGLISH

    fun dateLong(d: LocalDate): String =
        "${d.dayOfWeek.getDisplayName(TextStyle.FULL, locale)} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.FULL, locale)} ${d.year}"

    fun dayMonth(d: LocalDate): String = "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, locale)}"

    fun monthYearShort(d: LocalDate): String = "${d.month.getDisplayName(TextStyle.SHORT, locale)} ${d.year}"

    fun monthYear(year: Int, month: Int): String =
        "${java.time.Month.of(month).getDisplayName(TextStyle.FULL, locale)} $year"

    fun duration(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    fun clock(t: LocalTime = LocalTime.now()): String =
        "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"

    fun today(d: LocalDate = LocalDate.now()): String =
        "${d.dayOfWeek.getDisplayName(TextStyle.FULL, locale)} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.FULL, locale)}"

    fun greeting(name: String, hour: Int = LocalTime.now().hour): String {
        val g = when {
            hour < 5 -> "Good evening"
            hour < 12 -> "Good morning"
            hour < 18 -> "Good afternoon"
            else -> "Good evening"
        }
        return "$g, $name"
    }

    fun count(n: Int, one: String, many: String = one + "s") = "%,d %s".format(locale, n, if (n == 1) one else many)
}
