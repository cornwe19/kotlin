// WITH_RUNTIME
// WITH_COROUTINES
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

suspend fun suspendHere(): Any = suspendCoroutineOrReturn { x -> }

fun builder(c: suspend () -> Unit) {
    var exception: Throwable? = null

    c.createCoroutine(object : Continuation<Unit> {
        override val context = EmptyContext

        override fun resume(data: Unit) {
        }

        override fun resumeWithException(e: Throwable) {
            exception = e
        }
    }).resumeWithException(RuntimeException("OK"))

    if (exception?.message != "OK") {
        throw RuntimeException("Unexpected result: ${exception?.message}")
    }
}

fun box(): String {
    var result = "OK"
    builder {
        suspendHere()
        result = "fail 1"
    }

    builder {
        result = "fail 2"
    }

    return result
}
