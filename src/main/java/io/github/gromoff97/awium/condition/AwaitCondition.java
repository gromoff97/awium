package io.github.gromoff97.awium.condition;

import io.github.gromoff97.awium.condition.Condition.*;

/** Common type for all supported condition families. */
public sealed interface AwaitCondition permits Condition, PreservingCondition, ExpectedCondition, NarrowingCondition, SelectedCondition {
}
