package io.github.gromoff97.awium;

import io.github.gromoff97.awium.exception.*;

import static java.lang.reflect.Modifier.isAbstract;
import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isPrivate;
import static java.lang.reflect.Modifier.isProtected;
import static java.lang.reflect.Modifier.isPublic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FailureTaxonomyTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesTheControlledFailureHierarchy()
            throws ReflectiveOperationException {
        assertTrue(isPublic(AwaitFailure.class.getModifiers()));
        assertTrue(isAbstract(AwaitFailure.class.getModifiers()));
        assertEquals(AssertionError.class, AwaitFailure.class.getSuperclass());

        assertConcreteChild(AwaitTimeoutException.class, AwaitFailure.class);
        assertConcreteChild(AwaitStabilizationException.class, AwaitFailure.class);
        assertPackagePrivateMessageCauseConstructor(AwaitFailure.class);
    }

    @Test
    void exposesTheUncontrolledFailureHierarchy()
            throws ReflectiveOperationException {
        assertTrue(isPublic(AwaitUncontrolledException.class.getModifiers()));
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
        for (Class<?> type : failureTypes()) {
            assertFalse(List.of(type.getDeclaredMethods()).stream()
                    .anyMatch(FailureTaxonomyTest::isPublicInstance), type.getName());
        }
    }

    @Test
    void rejectsExternalSubclassing() throws Exception {
        String source = """
                package external;

                import io.github.gromoff97.awium.exception.AwaitFailure;

                final class Contract extends AwaitFailure {
                    Contract() {
                        super("failure", null);
                    }
                }
                """;

        assertFalse(CompilationSupport.compiles(temporaryDirectory, source));
    }

    @Test
    void allowsExternalLeafConstruction() throws Exception {
        String source = """
                package external;

                import io.github.gromoff97.awium.exception.*;

                final class Contract {
                    void construct() {
                        new AwaitConfigurationConflictException("failure");
                        new AwaitTimeoutException("failure", null);
                        new AwaitStabilizationException("failure", null);
                        new AwaitSourceRetrievalException("failure", null);
                        new AwaitConditionEvaluationException("failure", null);
                        new AwaitInterruptedException("failure", null);
                        new AwaitUnhandledException("failure", null);
                    }
                }
                """;

        assertTrue(CompilationSupport.compiles(temporaryDirectory, source));
    }

    @Test
    void hasNoPluralConditionEvaluationAlias() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(
                "io.github.gromoff97.awium."
                        + "AwaitConditionsEvaluationException"));
    }

    private static void assertConcreteChild(Class<?> type, Class<?> parent)
            throws ReflectiveOperationException {
        assertTrue(isPublic(type.getModifiers()), type.getName());
        assertTrue(isFinal(type.getModifiers()), type.getName());
        assertEquals(parent, type.getSuperclass());
        assertPublicMessageCauseConstructor(type);
    }

    private static void assertPackagePrivateMessageCauseConstructor(Class<?> type)
            throws ReflectiveOperationException {
        Constructor<?> constructor = type.getDeclaredConstructor(
                String.class, Throwable.class);
        assertEquals(1, type.getDeclaredConstructors().length, type.getName());
        assertFalse(isPublic(constructor.getModifiers()), type.getName());
        assertFalse(isProtected(constructor.getModifiers()), type.getName());
        assertFalse(isPrivate(constructor.getModifiers()), type.getName());
    }

    private static void assertPublicMessageCauseConstructor(Class<?> type)
            throws ReflectiveOperationException {
        Constructor<?> constructor = type.getDeclaredConstructor(
                String.class, Throwable.class);
        assertEquals(1, type.getDeclaredConstructors().length, type.getName());
        assertTrue(isPublic(constructor.getModifiers()), type.getName());
    }

    private static boolean isPublicInstance(Method method) {
        return isPublic(method.getModifiers())
                && !Modifier.isStatic(method.getModifiers());
    }

    private static List<Class<?>> failureTypes() {
        return List.of(AwaitFailure.class, AwaitTimeoutException.class,
                AwaitStabilizationException.class, AwaitUncontrolledException.class,
                AwaitSourceRetrievalException.class,
                AwaitConditionEvaluationException.class,
                AwaitInterruptedException.class, AwaitUnhandledException.class);
    }
}
