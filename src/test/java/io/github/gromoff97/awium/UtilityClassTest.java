package io.github.gromoff97.awium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.junit.jupiter.api.Test;

class UtilityClassTest {

    @Test
    void utilityConstructorsFailFast() throws ReflectiveOperationException {
        for (String name : List.of(
                "io.github.gromoff97.awium.Awium",
                "io.github.gromoff97.awium.conditioning.ValueEquality",
                "io.github.gromoff97.awium.conditioning.providers.ConditionProvider",
                "io.github.gromoff97.awium.conditioning.providers.ObjectConditionProvider",
                "io.github.gromoff97.awium.conditioning.providers.OptionalConditionProvider",
                "io.github.gromoff97.awium.conditioning.providers.CollectionConditionProvider",
                "io.github.gromoff97.awium.conditioning.providers.MapConditionProvider")) {
            Class<?> type = Class.forName(name);
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);

            InvocationTargetException failure = assertThrows(
                    InvocationTargetException.class, constructor::newInstance,
                    type.getName());
            AssertionError cause = assertInstanceOf(
                    AssertionError.class, failure.getCause(), type.getName());
            assertEquals("Utility class", cause.getMessage(), type.getName());
        }
    }
}
