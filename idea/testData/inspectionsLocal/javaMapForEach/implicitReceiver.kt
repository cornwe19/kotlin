// RUNTIME_WITH_FULL_JDK
fun test(map: Map<Int, String>) {
    map.run {
        <caret>forEach { key, value ->
            foo(key, value)
        }
    }
}

fun foo(i: Int, s: String) {}