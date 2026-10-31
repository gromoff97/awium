package io.github.gromoff97.awium.conditions.support

class Characters(value: String) : CharSequence by value {
    override fun toString(): String = throw AssertionError("matching must not render the observed text")
}
