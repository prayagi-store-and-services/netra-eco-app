package com.prayagi.netraeco

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlainFailureTest {
    @Test fun ownMessageIsKept() {
        assertEquals("Allow installs, then tap again.", plainFailure(IllegalStateException("Allow installs, then tap again."), "x"))
    }
    @Test fun networkErrorHidesSystemText() {
        val m = plainFailure(UnknownHostException("Unable to resolve host \"github.com\""), "x")
        assertFalse(m.contains("resolve host"))
        assertEquals(m, plainFailure(SocketTimeoutException("timeout"), "x"))
    }
    @Test fun unknownUsesFallback() {
        assertEquals("Update failed.", plainFailure(RuntimeException("boom"), "Update failed."))
    }
}
