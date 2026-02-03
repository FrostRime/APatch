package me.bmax.apatch.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import me.bmax.apatch.Natives

object Su {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    internal suspend inline fun <T> exec(crossinline block: suspend () -> T): T {
        return withContext(scope.coroutineContext) {
            Natives.su()
            block()
        }
    }
}
