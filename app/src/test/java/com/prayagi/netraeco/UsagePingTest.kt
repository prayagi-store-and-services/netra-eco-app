package com.prayagi.netraeco

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class UsagePingTest {
    private val day = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { clear(); set(2026, Calendar.OCTOBER, 3, 12, 0, 0) }.time

    @Test fun dayIdIsUtcYearMonthDay() = assertEquals("20261003", UsagePing.dayId(day))

    @Test fun monthIdIsUtcYearMonth() = assertEquals("202610", UsagePing.monthId(day))

    @Test fun appIdIsNetraEco() = assertEquals("netra-eco", UsagePing.APP_ID)
}
