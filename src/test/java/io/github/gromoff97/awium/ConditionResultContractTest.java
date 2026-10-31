package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.function.CheckedFunction;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.Unsatisfied;
import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.result.ConditionResult.*;
import static io.github.gromoff97.awium.Conditions.captured;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static io.github.gromoff97.awium.CollectionConditions.firstElement;
import static io.github.gromoff97.awium.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.conditionPerWait;
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static io.github.gromoff97.awium.MapConditions.singleEntry;
import static io.github.gromoff97.awium.MapConditions.allEntries;
import static io.github.gromoff97.awium.MapConditions.hasEntry;
import static io.github.gromoff97.awium.MapConditions.hasNoEntry;
import static io.github.gromoff97.awium.OptionalConditions.hasValue;

class ConditionResultContractTest {

    private final FakeTime time = new FakeTime(0);

    private record User(int id) {}

    @Test
    void mappingChangesOnlySatisfiedValues() {
        var mapped = satisfied("abc").mapValue(String::length);
        assertEquals(3, assertInstanceOf(Satisfied.class, mapped).value());
        var cause = new IllegalStateException("not ready");
        var mismatch = ConditionResult.<String>unsatisfied("retry", cause).mapValue(value -> {
            throw new AssertionError("mismatches must not be mapped");
        });
        assertEquals("retry", assertInstanceOf(Unsatisfied.class, mismatch).mismatch());
        assertSame(cause, mismatch.cause());
    }

    @Test
    void callbacksChooseTheResultWithoutExplicitTypeArguments() {
        var actual = new User(42);
        var id = await(() -> actual).until(condition("user id", user -> satisfied(user.id())));
        var lazyId = await(() -> actual).until(conditionPerWait("user id", () -> user -> satisfied(user.id())));

        assertEquals(42, id.intValue());
        assertEquals(42, lazyId.intValue());
        assertEquals(42, await(() -> Optional.of(actual)).until(hasValue(condition("id", user -> satisfied(user.id())))).intValue());
        assertEquals(42, await(() -> List.of(actual)).until(singleElement(condition("id", user -> satisfied(user.id())))).intValue());
        assertEquals(42, await(() -> Map.of("user", actual)).until(valueFor("user", condition("id", user -> satisfied(user.id())))).intValue());
        assertEquals(42, await(() -> Map.of("user", actual)).until(singleEntry(condition("id", entry -> satisfied(entry.getValue().id())))).intValue());
    }

    @Test
    void selectedResultsKeepTheOriginalObservationsAndMismatchInHistory() {
        var pending = new User(0);
        var saved = new User(42);
        var source = List.of(pending, saved).iterator();
        var check = condition("positive id", (User user) -> user.id() > 0
                ? satisfied(user.id()) : unsatisfied("id must be positive")).because("user must be saved");
        AwaitResult<User, Integer> result = tryAwait(source::next).usingTime(time, time).every(ofNanos(1)).until(check);

        assertEquals(42, result.orThrow());
        assertThat(attempts(result)).extracting(AwaitTestAccess::observation).containsExactly(pending, saved);
        assertThat(attempts(result).getFirst()).isInstanceOf(io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation.class);
        assertThat(attempts(result).getLast()).isInstanceOf(io.github.gromoff97.awium.result.Attempt.SatisfiedObservation.class);
        assertThat(result.conditions().getFirst().toString()).contains("id must be positive", "user must be saved");
        assertSame(check, result.conditions().getFirst().condition());
    }

    @Test
    void capturedKeepsSourceObservationsWhenStagesSelectDifferentTypes() {
        var actual = new User(42);
        var check = captured(condition("id", (User user) -> satisfied(user.id())),
                condition("label", (User user) -> satisfied("user-" + user.id())),
                condition("user", (User user) -> satisfied(user)));
        List<User> observed = await(() -> actual).usingTime(time, time).every(ofNanos(1)).until(check);
        AwaitResult<User, List<User>> result = tryAwait(() -> actual).usingTime(time, time).every(ofNanos(1)).until(check);

        assertEquals(List.of(actual, actual, actual), observed);
        assertEquals(observed, result.orThrow());
        assertThat(result.conditions()).hasSize(3);
        assertThat(attempts(result)).extracting(AwaitTestAccess::observation).containsExactlyElementsOf(observed);
    }

    @Test
    void nullIsAValidSelectedValueEvenWhenTheSourceIsNotNull() {
        var actual = new User(42);
        Condition.Typed<User, String> check = condition("no label", user -> satisfied(null));
        String label = await(() -> actual).until(check);
        AwaitResult<User, String> result = tryAwait(() -> actual).until(check);

        assertNull(label);
        assertNull(result.orThrow());
        assertInstanceOf(AwaitResult.Succeeded.class, result);
        assertSame(actual, AwaitTestAccess.observation(attempts(result).getFirst()));
    }

    @Test
    void factoriesResetStateAndChooseTheirOwnResultInEachWait() {
        int[] factories = {0};
        var check = conditionPerWait("second id", () -> {
            factories[0]++;
            int[] calls = {0};
            return (User user) -> ++calls[0] == 2 ? satisfied(user.id()) : unsatisfied("one more");
        }).because("two observations are required");
        var waiting = await(() -> new User(42)).usingTime(time, time).every(ofNanos(1));

        assertEquals(0, factories[0]);
        assertEquals(42, waiting.until(check));
        AwaitResult<User, Integer> result = diagnostic(waiting).until(check);
        assertEquals(42, result.orThrow());
        assertThat(attempts(result)).hasSize(2);
        assertEquals(2, factories[0]);
    }

    @Test
    void entryQuantifiersAcceptTypedCallbacksAndKeepTheMap() {
        var users = Map.of("user", new User(42));
        var id = condition("positive id", (Map.Entry<String, User> entry) -> entry.getValue().id() > 0
                ? satisfied(entry.getValue().id()) : unsatisfied());

        assertSame(users, await(() -> users).until(allEntries(id)));
        assertSame(users, await(() -> users).until(hasEntry(id)));
        assertSame(users, await(() -> users).until(hasNoEntry(condition("missing id", entry -> entry.getValue().id() == 0
                ? satisfied(entry.getValue()) : unsatisfied()))));
    }

    @Test
    void customChecksCanReturnTheObservation() {
        CheckedFunction<User, ConditionResult<User>> status = user -> user.id() > 0
                ? satisfied(user) : unsatisfied("id must be positive");
        Callable<CheckedFunction<User, ConditionResult<User>>> factory = () -> status;
        var direct = condition("positive id", status).because("user must be saved");
        var lazy = conditionPerWait("positive id", factory).because("user must be saved");
        var actual = new User(42);
        var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(10));

        for (var check : List.of(direct, lazy)) {
            var value = waiting.until(check);
            assertEquals(42, value.id());
            assertSame(actual, value);
            AwaitResult<User, User> result = diagnostic(waiting).until(check);
            assertSame(actual, result.orThrow());
        }
        List<User> values = waiting.until(captured(direct, lazy));
        for (User value : values) assertSame(actual, value);
        assertSame(actual, await(() -> Optional.of(actual)).until(hasValue(direct)));
        assertEquals(42, await(() -> List.of(actual)).until(singleElement(lazy)).id());
        assertEquals(42, await(() -> Map.of("user", actual)).until(valueFor("user", direct)).id());

        for (var check : List.of(direct, lazy)) {
            var failed = tryAwait(() -> new User(0)).usingTime(time, time).upTo(ofNanos(1)).until(check);
            var failure = failure(AwaitFailure.Reason.TIMEOUT, failed);
            assertThat(failure.message()).contains("id must be positive", "Because: user must be saved");
        }
    }

    @Test
    void satisfactionReturnsTheExplicitObservationThroughNestedSelections() {
        ConditionResult<String> signal = satisfied("ready");
        assertInstanceOf(Satisfied.class, signal);
        assertNull(signal.cause());
        var actual = new User(42);
        var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));

        assertEquals(42, waiting.until(condition("ready", ignored -> satisfied(ignored))).id());
        AwaitResult<User, User> result = diagnostic(waiting).until(condition("ready", ignored -> satisfied(ignored)));
        switch (result) {
            case AwaitResult.Succeeded(var history, var user) -> assertSame(actual, user);
            case AwaitResult.Failed(var history, var failure) -> org.junit.jupiter.api.Assertions.fail(failure.message());
        }
        assertSame(actual, await(() -> java.util.Optional.of(actual)).usingTime(time, time)
                .until(hasValue(condition("present", ignored -> satisfied(ignored)))));
        assertEquals(42, await(() -> java.util.List.of(actual)).usingTime(time, time)
                .until(singleElement(condition("single", ignored -> satisfied(ignored)))).id());
        assertEquals(42, await(() -> java.util.List.of(actual)).usingTime(time, time)
                .until(firstElement(condition("first", ignored -> satisfied(ignored)))).id());
        assertEquals(42, await(() -> java.util.Map.of("user", actual)).usingTime(time, time)
                .until(valueFor("user", condition("mapped", ignored -> satisfied(ignored)))).id());
        User absent = null;
        assertNull(await(() -> absent).usingTime(time, time).until(condition("null is valid", ignored -> satisfied(ignored))));
    }

    @Test
    void conditionsCanFailWithoutDuplicatingTheirDescription() {
        AwaitResult<String, String> result = tryAwait(() -> "pending")
                .usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2))
                .until(condition("status is ready", value -> unsatisfied()));
        var failure = failure(AwaitFailure.Reason.TIMEOUT, result);
        assertThat(failure.message()).contains("Condition: status is ready", "Actual: \"pending\"").doesNotContain("Mismatch:");
    }

    @Test
    void unsatisfiedAcceptsOptionalDiagnostics() {
        var plain = unsatisfied();
        assertEquals(new Unsatisfied<>(null, null), plain);
        assertEquals(plain, unsatisfied(null));
        assertEquals(plain, unsatisfied(null, null));
        var cause = new IllegalStateException("details");
        var detailed = unsatisfied("not ready", cause);
        assertEquals("not ready", detailed.mismatch());
        assertSame(cause, detailed.cause());
        assertSame(cause, unsatisfied(null, cause).cause());
        assertEquals(new Unsatisfied<>("failed", null), unsatisfied("failed", null));
    }

}
