package com.prayagi.netraeco

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSortTest {
    @Test fun netraPrefixIsDropped() {
        assertEquals("intruder", AppSort.key("Netra Intruder"))
        assertEquals("battery sentinel pro", AppSort.key("Netra Battery Sentinel Pro"))
    }

    @Test fun netraXIsDroppedToo() {
        assertEquals("trikal", AppSort.key("NETRA x TRIKAL"))
    }

    @Test fun dictionaryOrderLetterByLetter() {
        val names = listOf("Netra Email", "Netra Ecosystem", "Netra Battery Sentinel Pro")
        assertEquals(listOf("Netra Battery Sentinel Pro", "Netra Ecosystem", "Netra Email"), AppSort.sorted(names) { it })
    }

    @Test fun futureAppSlotsIntoPlace() {
        assertEquals(listOf("Netra Hub", "Netra Intruder", "Netra KBC"), AppSort.sorted(listOf("Netra KBC", "Netra Hub", "Netra Intruder")) { it })
    }

    @Test fun caseInsensitiveOrder() {
        assertEquals(listOf("Netra Email", "OPEN APP STORE", "Netra Pulse"), AppSort.sorted(listOf("Netra Pulse", "OPEN APP STORE", "Netra Email")) { it })
    }

    @Test fun comingSoonAndLiveShareOneOrder() {
        val names = listOf("Netra Pulse", "Netra Intruder", "Netra Email")
        assertEquals(listOf("Netra Email", "Netra Intruder", "Netra Pulse"), AppSort.sorted(names) { it })
    }
}
