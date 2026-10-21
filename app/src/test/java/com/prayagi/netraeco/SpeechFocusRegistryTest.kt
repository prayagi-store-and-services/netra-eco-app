package com.prayagi.netraeco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SpeechFocusRegistryTest {

    @Test
    fun `refused audio focus (null handle) does not crash and still finishes once`() {
        val r = SpeechFocusRegistry()
        var finished = 0
        r.register("TimeSpeak-1", null, { finished++ })
        assertEquals(1, r.size())
        val rel = r.release("TimeSpeak-1")
        assertNotNull(rel)
        assertNull(rel!!.handle)
        rel.onFinished?.invoke()
        assertEquals(1, finished)
        assertEquals(0, r.size())
    }

    @Test
    fun `granted focus handle comes back for abandoning`() {
        val r = SpeechFocusRegistry()
        val handle = Any()
        r.register("a", handle, null)
        val rel = r.release("a")
        assertSame(handle, rel!!.handle)
        assertNull(rel.onFinished)
    }

    @Test
    fun `release is once only and unknown ids are ignored`() {
        val r = SpeechFocusRegistry()
        r.register("a", null, null)
        assertNotNull(r.release("a"))
        assertNull(r.release("a"))
        assertNull(r.release("never-registered"))
    }

    @Test
    fun `many lines with and without focus coexist`() {
        val r = SpeechFocusRegistry()
        for (i in 0 until 50) r.register("id$i", if (i % 2 == 0) null else Any(), null)
        assertEquals(50, r.size())
        for (i in 0 until 50) assertNotNull(r.release("id$i"))
        assertEquals(0, r.size())
    }
}
