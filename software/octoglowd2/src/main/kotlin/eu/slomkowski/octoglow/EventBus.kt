package eu.slomkowski.octoglow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext


object EventBus {
    val bus: BroadcastChannel<Any> = ConflatedBroadcastChannel<Any>()

    suspend fun CoroutineScope.sendEvent(o: Any) = launch { bus.send(o) }

    inline fun <reified T> asChannel(): ReceiveChannel<T> {
        return bus.openSubscription().filter { it is T }.map { it as T }
    }

    fun close() {
        bus.cancel()
    }

    inline fun <reified T> registerHandler(coroutineContext: CoroutineContext, crossinline callback: (T) -> Unit) {
        CoroutineScope(coroutineContext).launch {
            for (owr in EventBus.asChannel<T>()) {
                callback(owr)
            }
        }
    }
}
