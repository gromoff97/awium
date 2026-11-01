package consumer.value;

import io.github.gromoff97.awium.result.ConditionResult;
import java.io.IOException;
import java.util.List;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.asserted;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.condition;
import static io.github.gromoff97.awium.conditions.common.CommonConditions.isNotNull;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static java.time.Duration.ofNanos;

public final class CheckedCallbackConsumer {
    private CheckedCallbackConsumer() {}

    public static List<Integer> checkedReferences() {
        var waiting = await(() -> "ready");
        return List.of(waiting.until(asserted(CheckedCallbackConsumer::verify)).length(),
                await(() -> length("ready")).until(isNotNull),
                waiting.until(condition("checked evaluation", CheckedCallbackConsumer::evaluate)).length());
    }

    public static int retriedAssertion() {
        int[] calls = {0};
        return await(() -> ++calls[0]).every(ofNanos(1)).until(asserted(CheckedCallbackConsumer::verifySecond));
    }

    private static void verify(String actual) throws Throwable {
        if (!actual.equals("ready")) throw new AssertionError("expected ready");
    }

    private static int length(String actual) throws IOException { return actual.length(); }

    private static ConditionResult<String> evaluate(String actual) throws Throwable { return satisfied(actual); }

    private static void verifySecond(int actual) throws Throwable {
        if (actual != 2) throw new AssertionError("expected the second observation");
    }
}
