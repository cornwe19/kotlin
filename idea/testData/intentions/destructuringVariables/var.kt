data class XY(val x: Int, val y: Int)

fun create() = XY(1, 2)

fun use(): Int {
    var <caret>xy = create()
    return xy.x + xy.y
}