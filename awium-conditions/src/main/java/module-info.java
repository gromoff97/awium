@SuppressWarnings("module")
module io.github.gromoff97.awium.conditions {
    requires transitive io.github.gromoff97.awium.core;
    exports io.github.gromoff97.awium.conditions.common;
    exports io.github.gromoff97.awium.conditions.collection;
    exports io.github.gromoff97.awium.conditions.map;
    exports io.github.gromoff97.awium.conditions.optional;
    exports io.github.gromoff97.awium.conditions.string;
    exports io.github.gromoff97.awium.function.checked;
}
