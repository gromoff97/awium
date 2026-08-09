package io.github.gromoff97.assertility;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompilationContractTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void assertionAdaptersMayBeDecoratedOnce() throws IOException {
        assertTrue(compiles("""
                import static io.github.gromoff97.assertility.AwaitConditions.*;

                final class Contract {
                    record Payment(long id) {}

                    void check() {
                        asserted((Payment value) -> {}).because("first");
                        passed((Payment value) -> value).because("first");
                    }
                }
                """));
    }

    @Test
    void explainedAssertionAdapterCannotBeDecoratedAgain() throws IOException {
        assertFalse(compiles("""
                import static io.github.gromoff97.assertility.AwaitConditions.asserted;

                final class Contract {
                    record Payment(long id) {}

                    void check() {
                        asserted((Payment value) -> {}).because("first")
                                .because("second");
                    }
                }
                """));
    }

    @Test
    void conditionIsNotDirectlyLambdaAssignable() throws IOException {
        assertFalse(compiles("""
                import io.github.gromoff97.assertility.Condition;
                import io.github.gromoff97.assertility.Evaluation;

                final class Contract {
                    record Payment(long id) {}

                    Condition<Payment, Payment> condition =
                            value -> Evaluation.satisfied(value);
                }
                """));
    }

    @Test
    void literalBecauseCannotBeOverridden() throws IOException {
        assertFalse(compiles("""
                import io.github.gromoff97.assertility.Condition;
                import io.github.gromoff97.assertility.Evaluation;
                import io.github.gromoff97.assertility.ExplainedCondition;

                final class Contract extends Condition<Contract.Payment, Contract.Payment> {
                    record Payment(long id) {}

                    @Override
                    public Evaluation<Payment> evaluate(Payment actual) {
                        return Evaluation.satisfied(actual);
                    }

                    @Override
                    public ExplainedCondition<Payment, Payment> because(String value) {
                        return null;
                    }
                }
                """));
    }

    @Test
    void formattedBecauseCannotBeOverridden() throws IOException {
        assertFalse(compiles("""
                import io.github.gromoff97.assertility.Condition;
                import io.github.gromoff97.assertility.Evaluation;
                import io.github.gromoff97.assertility.ExplainedCondition;

                final class Contract extends Condition<Contract.Payment, Contract.Payment> {
                    record Payment(long id) {}

                    @Override
                    public Evaluation<Payment> evaluate(Payment actual) {
                        return Evaluation.satisfied(actual);
                    }

                    @Override
                    public ExplainedCondition<Payment, Payment> because(
                            String format, Object... arguments) {
                        return null;
                    }
                }
                """));
    }

    private boolean compiles(String source) throws IOException {
        return CompilationSupport.compiles(temporaryDirectory, source);
    }
}
