package com.prayagi.netraeco

import java.time.LocalDateTime

/** The new look switches on at 11 Oct 2026 00:00 device-local time. Device clock only, no network. */
object RedesignGate {
    val SWITCH_AT: LocalDateTime = LocalDateTime.of(2026, 10, 11, 0, 0)
    fun isOn(now: LocalDateTime): Boolean = !now.isBefore(SWITCH_AT)
    fun isOn(): Boolean = isOn(LocalDateTime.now())
}
