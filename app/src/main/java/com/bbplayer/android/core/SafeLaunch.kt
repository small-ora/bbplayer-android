package com.bbplayer.android.core

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Launches a UI coroutine that can never crash the app: failures are logged and optionally
 * surfaced to the caller (e.g. as a snackbar / inline error).
 */
fun CoroutineScope.safeLaunch(
    onError: ((Throwable) -> Unit)? = null,
    block: suspend CoroutineScope.() -> Unit,
): Job = launch {
    try {
        block()
    } catch (ce: kotlinx.coroutines.CancellationException) {
        throw ce
    } catch (t: Throwable) {
        Log.w("BBPlayer", "background action failed", t)
        onError?.invoke(t)
    }
}
