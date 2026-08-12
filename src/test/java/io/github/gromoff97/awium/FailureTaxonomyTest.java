package io.github.gromoff97.awium;

import io.github.gromoff97.awium.exceptions.*;

import static java.lang.reflect.Modifier.isAbstract;
import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.util.List;
import org.junit.jupiter.api.Test;

class FailureTaxonomyTest {

    @Test
    void exposesTheControlledFailureHierarchy()
            throws ReflectiveOperationException {
        assertTrue(isAbstract(AwaitFailure.class.getModifiers()));
        assertEquals(AssertionError.class, AwaitFailure.class.getSuperclass());

        assertConcreteChild(AwaitTimeoutException.class, AwaitFailure.class);
        assertConcreteChild(AwaitStabilizationException.class, AwaitFailure.class);
        assertPackagePrivateMessageCauseConstructor(AwaitFailure.class);
    }

    @Test
    void exposesTheUncontrolledFailureHierarchy()
            throws ReflectiveOperationException {
        assertTrue(isAbstract(AwaitUncontrolledException.class.getModifiers()));
        assertEquals(RuntimeException.class,
                AwaitUncontrolledException.class.getSuperclass());

        for (Class<?> type : List.of(AwaitSourceRetrievalException.class,
                AwaitConditionEvaluationException.class,
                AwaitInterruptedException.class, AwaitUnhandledException.class)) {
            assertConcreteChild(type, AwaitUncontrolledException.class);
        }
        assertPackagePrivateMessageCauseConstructor(AwaitUncontrolledException.class);
    }

    @Test
    void exposesNoStructuredFailureAccessors() {
        for (Class<?> type : List.of(AwaitFailure.class,
                AwaitTimeoutException.class, AwaitStabilizationException.class,
                AwaitUncontrolledException.class,
                AwaitSourceRetrievalException.class,
                AwaitConditionEvaluationException.class,
                AwaitInterruptedException.class, AwaitUnhandledException.class)) {
            assertFalse(List.of(type.getDeclaredMethods()).stream()
                    .anyMatch(method -> isPublic(method.getModifiers())
                            && !isStatic(method.getModifiers())), type.getName());
        }
    }

    private static void assertConcreteChild(Class<?> type, Class<?> parent)
            throws ReflectiveOperationException {
        assertTrue(isFinal(type.getModifiers()), type.getName());
        assertEquals(parent, type.getSuperclass());
        Constructor<?> constructor = type.getDeclaredConstructor(
                String.class, Throwable.class);
        assertEquals(1, type.getDeclaredConstructors().length, type.getName());
        assertTrue(isPublic(constructor.getModifiers()), type.getName());
    }

    private static void assertPackagePrivateMessageCauseConstructor(Class<?> type)
            throws ReflectiveOperationException {
        Constructor<?> constructor = type.getDeclaredConstructor(
                String.class, Throwable.class);
        assertEquals(1, type.getDeclaredConstructors().length, type.getName());
        assertEquals(0, constructor.getModifiers(), type.getName());
    }
}
