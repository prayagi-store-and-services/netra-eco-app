package com.prayagi.netraeco

import android.content.Context

/** The public brand is now fixed and works offline. Older remote/stored names never override it. */
object NameGate {
    const val PUBLIC_NAME = "Netra Ecosystem"
    fun decide(serverNowMs: Long?, remoteName: String?, saved: String?): String = PUBLIC_NAME
    fun displayName(context: Context): String = PUBLIC_NAME
    fun refresh(context: Context) { /* No network or date gate for the public name. */ }
}
