package io.github.gromoff97.awium.conditions.support

class ThrowingEquals(private val beforeEquality: Runnable) {
    override fun equals(other: Any?): Boolean {
        beforeEquality.run()
        return other is ThrowingEquals
    }

    override fun hashCode(): Int = throw AssertionError("hashCode must not be called")
}
