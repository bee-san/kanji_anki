package dev.bee.kanjianki

internal inline fun <T> List<T>.forEachTwoColumnRowIndexed(
    block: (rowIndex: Int, first: T, second: T?) -> Unit,
) {
    var rowIndex = 0
    var index = 0
    while (index < size) {
        val first = this[index]
        val second = if (index + 1 < size) this[index + 1] else null
        block(rowIndex, first, second)
        rowIndex += 1
        index += 2
    }
}