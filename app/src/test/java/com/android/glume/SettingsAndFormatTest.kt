package com.android.glume

import com.android.glume.data.Prefs
import com.android.glume.data.SettingKey
import com.android.glume.util.Format
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SettingsAndFormatTest {

    @Test
    fun `cycle settings wrap in both directions`() {
        val p = Prefs(intervalSec = 20)
        assertEquals(3, SettingKey.Interval.next(p, 1))
        assertEquals(10, SettingKey.Interval.next(p, -1))
        assertEquals("20 seconds", SettingKey.Interval.display(p))
    }

    @Test
    fun `toggles flip`() {
        assertEquals(false, SettingKey.Shuffle.next(Prefs(shuffle = true), 1))
        assertEquals("On", SettingKey.Shuffle.display(Prefs(shuffle = true)))
    }

    @Test
    fun `dates and durations use the design's format`() {
        assertEquals("Wednesday 12 July 2023", Format.dateLong(LocalDate.of(2023, 7, 12)))
        assertEquals("2:05", Format.duration(125))
        assertEquals("0:00", Format.duration(-3))
        assertEquals("Good morning, Alex", Format.greeting("Alex", 9))
        assertEquals("Good evening, Alex", Format.greeting("Alex", 2))
    }
}
