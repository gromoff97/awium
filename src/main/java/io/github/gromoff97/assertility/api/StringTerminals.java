package io.github.gromoff97.assertility.api;

public interface StringTerminals<R> extends ComparableTerminals<String, R> {
    R isEmpty();

    R isNotEmpty();

    R contains(CharSequence... values);

    R doesNotContain(CharSequence... values);
}
