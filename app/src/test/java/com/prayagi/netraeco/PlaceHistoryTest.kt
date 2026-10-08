package com.prayagi.netraeco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlaceHistoryTest {
    private fun c(n: String, s: String) = City(n, s, "IN", 0.0, 0.0, "Asia/Kolkata", 0, emptyList())
    @Test fun ranchi1990IsBihar() = assertEquals("Ranchi, Bihar", PlaceHistory.asOf(c("Ranchi", "Jharkhand"), LocalDate.of(1990, 5, 15)).label)
    @Test fun ranchi2005IsJharkhand() = assertEquals("Ranchi, Jharkhand", PlaceHistory.asOf(c("Ranchi", "Jharkhand"), LocalDate.of(2005, 5, 15)).label)
    @Test fun mumbai1990IsBombay() = assertEquals("Bombay, Maharashtra", PlaceHistory.asOf(c("Mumbai", "Maharashtra"), LocalDate.of(1990, 1, 1)).label)
    @Test fun mumbai1950IsBombayState() = assertEquals("Bombay, Bombay State", PlaceHistory.asOf(c("Mumbai", "Maharashtra"), LocalDate.of(1955, 1, 1)).label)
    @Test fun mumbai2000IsMumbai() = assertEquals("Mumbai, Maharashtra", PlaceHistory.asOf(c("Mumbai", "Maharashtra"), LocalDate.of(2000, 1, 1)).label)
    @Test fun renameYearIsFlagged() = assertTrue(PlaceHistory.asOf(c("Mumbai", "Maharashtra"), LocalDate.of(1995, 6, 1)).note.contains("exact date"))
    @Test fun bengaluruExactDate() {
        assertEquals("Bangalore, Karnataka", PlaceHistory.asOf(c("Bengaluru", "Karnataka"), LocalDate.of(2014, 10, 31)).label)
        assertEquals("Bengaluru, Karnataka", PlaceHistory.asOf(c("Bengaluru", "Karnataka"), LocalDate.of(2014, 11, 1)).label)
    }
    @Test fun telanganaBefore2014() = assertEquals("Hyderabad, Andhra Pradesh", PlaceHistory.asOf(c("Hyderabad", "Telangana"), LocalDate.of(2010, 1, 1)).label)
    @Test fun before1950StateUnavailable() { val p = PlaceHistory.asOf(c("Patna", "Bihar"), LocalDate.of(1940, 1, 1)); assertEquals("", p.state); assertTrue(p.note.contains("Unavailable")) }
}
