package com.prayagi.netraeco

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Screen-owned action state, shared by every installer entry point and retained across tabs. */
class EcoInstallActions {
    data class Result(val busy: Boolean = false, val error: String? = null)
    private val results = mutableStateMapOf<String, Result>()
    fun state(key: String): Result = results[key] ?: Result()
    fun start(scope: CoroutineScope, key: String, action: suspend () -> Unit) {
        if (state(key).busy) return
        results[key] = Result(busy = true)
        scope.launch {
            try { action(); results[key] = Result() }
            catch (e: CancellationException) { results[key] = Result(); throw e }
            catch (e: Exception) { results[key] = Result(error = plainFailure(e, "Install failed.")) }
        }
    }
    fun install(scope: CoroutineScope, context: Context, file: File) =
        start(scope, file.name) { Net.install(context, file) }
}

/** The explicit Eco check keeps its result even while the Current tab is not composed. */
class EcoUpdateCheck {
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    var release by mutableStateOf<LatestRelease?>(null); private set
    fun start(scope: CoroutineScope, check: suspend () -> Pair<String, LatestRelease?>) {
        if (busy) return
        busy = true
        message = null
        release = null
        scope.launch {
            try { val result = check(); message = result.first; release = result.second }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = plainFailure(e, "Check failed.") }
            finally { busy = false }
        }
    }
}
