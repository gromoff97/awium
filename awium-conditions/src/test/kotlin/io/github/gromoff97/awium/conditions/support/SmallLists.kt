package io.github.gromoff97.awium.conditions.support

fun smallLists(): List<List<String?>> = buildList {
    var level = listOf(emptyList<String?>())
    addAll(level)
    repeat(3) {
        level = level.flatMap { prefix -> listOf(null, "a", "b").map { prefix + it } }
        addAll(level)
    }
}
