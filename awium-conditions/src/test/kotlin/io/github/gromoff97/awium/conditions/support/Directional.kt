package io.github.gromoff97.awium.conditions.support

class Directional(private val answer: Boolean) {
    @JvmField var equalsCalls = 0

    override fun equals(other: Any?): Boolean {
        equalsCalls++
        return answer
    }

    override fun hashCode(): Int = throw AssertionError("hashCode must not be called")
}
