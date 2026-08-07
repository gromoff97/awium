package io.github.gromoff97.assertility;

@FunctionalInterface
interface Terminal<T, R> {
    Evaluation<R> evaluate(T actual);
}
