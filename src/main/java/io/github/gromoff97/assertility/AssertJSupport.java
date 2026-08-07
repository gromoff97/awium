package io.github.gromoff97.assertility;

import static org.assertj.core.api.Assertions.assertThat;

final class AssertJSupport {
    private AssertJSupport() {
    }

    static void assertNotNull(Object actual) {
        assertThat(actual)
                .as("source subject must be non-null before evaluating this terminal")
                .isNotNull();
    }

    static void assertRecursiveEqual(Object actual, Object expected) {
        if (actual == null || expected == null) {
            assertThat(actual).isEqualTo(expected);
            return;
        }
        assertThat(actual)
                .usingRecursiveComparison()
                .withStrictTypeChecking()
                .isEqualTo(expected);
    }

    static void assertRecursiveNotEqual(Object actual, Object expected) {
        if (actual == null || expected == null) {
            assertThat(actual).isNotEqualTo(expected);
            return;
        }
        assertThat(actual)
                .usingRecursiveComparison()
                .withStrictTypeChecking()
                .isNotEqualTo(expected);
    }
}
