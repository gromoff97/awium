package io.github.gromoff97.assertility;

import org.awaitility.core.ConditionFactory;

record AwaitSpec<T>(ConditionFactory factory, AwaitSources.Source<T> source, String description) {
    AwaitSpec {
        Validation.factory(factory);
        Validation.source(source);
    }

    AwaitSpec<T> describedAs(String value) {
        return new AwaitSpec<>(factory, source, value);
    }
}
