# Awium

Awium is a zero-dependency Java 21 library that waits for a condition in the
calling thread and returns the result from the same successful observation.

```java
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.OptionalConditions.present;

Payment payment = await(() -> paymentRepository.findById(order.paymentId())).until(present.because("Checkout cannot continue without the payment"));
```

## Installation

The current snapshot is not published to an artifact repository. Clone Awium
next to the consuming Gradle build and include it from `settings.gradle.kts`:

```kotlin
includeBuild("../awium")
```

Then use its coordinates normally:

```kotlin
dependencies {
    testImplementation("io.github.gromoff97:awium:0.1.0-SNAPSHOT")
}
```

Awium has no compile or runtime dependencies. JUnit is used only to test the
library itself. All public types are in `io.github.gromoff97.awium`.

## Conditions and results

The condition supplied to `until(...)` determines both success and the
terminal result type.

### Preserve the observed value

Most built-in conditions return the exact object obtained from the source:

```java
import static io.github.gromoff97.awium.Conditions.equalTo;

Payment payment = await(paymentRepository::load).until(equalTo(expectedPayment));
```

Collection, map, string, and comparable conditions preserve their source in
the same way.

### Select a value

Selection conditions return a value contained in the observation:

```java
import static io.github.gromoff97.awium.CollectionConditions.single;
import static io.github.gromoff97.awium.MapConditions.singleEntry;
import static io.github.gromoff97.awium.OptionalConditions.present;

Payment payment = await(paymentRepository::find).until(present);
Payment onlyPayment = await(paymentRepository::findAll).until(single);
Map.Entry<String, Payment> entry = await(paymentRepository::index).until(singleEntry);
```

`present`, `single`, `first`, `last`, and `singleEntry` are fields, so they do
not need parentheses. `isNull` and `absent` preserve the source type: their
successful results are `null` and the original empty `Optional`, respectively.

### Use an assertion

`asserted(...)` preserves the observed value. An `AssertionError` means that
the condition is currently unsatisfied, so polling continues:

```java
import static io.github.gromoff97.awium.Conditions.asserted;

Payment payment = await(paymentRepository::load).until(asserted(actual -> {
    if (!actual.isComplete()) {
        throw new AssertionError("payment was not complete");
    }
}));
```

`asserted(...)` accepts a `CheckedConsumer`; `condition(...)` accepts a
`CheckedFunction`. Both callbacks may declare `throws Throwable`, so existing
methods declaring `throws Exception` or `throws Throwable` can be passed directly.
Callback failures stop polling immediately and retain the
original cause; only `AssertionError` inside `asserted(...)` means retry.

For callbacks already stored in JDK `Function` or `Consumer` variables, pass
`function::apply` or `consumer::accept`. Predicates still use the JDK interfaces.

### Ordered states

Pass several compatible conditions to `until(...)` to wait for them in order.
Each stage captures a value from a separate observation; the result is a list
in stage order. `tryUntil(...)` returns the same list inside `AwaitResult`:

```java
List<Payment> payments = await(paymentRepository::load).until(
        matches(Payment::created),
        matches(Payment::pending),
        matches(Payment::finished));

AwaitResult<String, List<String>> statuses = await(paymentRepository::status).tryUntil(
        equalTo("created").because("The order must be created"),
        equalTo("paid").because("Payment must complete"));
```

With `persisting(...)`, only the final stage is re-evaluated. Earlier captured
values remain unchanged. Each factory-backed stage gets its own fresh evaluator
for every terminal call. One condition still returns one value; zero conditions
are invalid. A comma between terminal arguments always means ordered capture.
The stages must belong to a compatible condition family; preserving conditions
and comparisons cannot be mixed. A selection followed by a value check belongs
inside the selection condition instead.

Type-narrowing stages and nested type selections can also be captured in order:

```java
List<Payment> payments = await(repository::findAll).until(
        single(instanceOf(Payment.class)).because("First payment"),
        single(instanceOf(Payment.class)).because("Next payment"));
```

These stages must declare the same result type. Mixing `instanceOf(String.class)`
with `instanceOf(Integer.class)` is rejected instead of inferring `List<Object>`.

An array of compatible conditions can be passed directly. It is copied before
polling and always produces a captured list, including for a one-element array.
An empty array is rejected before invoking the source.

## Timing

`every`, `upTo`, and `persisting` are optional, accept `Duration`, may appear in
any order, and may be repeated. The last supplied value wins:

```java
Payment payment = await(source).every(POLL_INTERVAL).upTo(TIMEOUT).persisting(STABILITY).until(present);
```

Defaults are:

- `every`: 100 milliseconds
- `upTo`: 10 seconds
- `persisting`: zero, which disables persistence checking

Unless the caller is already interrupted, the first observation is invoked
unconditionally with respect to the timeout as soon as the engine starts;
`upTo` does not cancel that initial source invocation. Later observations start
only before the acquisition deadline, and every observation must also complete
before it to satisfy the wait. A late observation is retained for diagnostics
but cannot satisfy the wait. Once the condition first succeeds, `persisting`
may extend the total call beyond `upTo`. Any persistence mismatch fails
immediately.

Each duration is validated when supplied. The interval may equal or exceed
the timeout: the first observation still runs immediately, and an unsatisfied
observation waits only for the remaining acquisition time before timing out.
For example, `upTo(Duration.ofMillis(50))` works with the default interval.

`usingTime(clock, parker)` replaces the monotonic nanosecond clock and the
wait operation for one fluent configuration. It works with both `until` and
`tryUntil`, preserves source/result types, and leaves earlier configurations
independent:

```java
var nanos = new java.util.concurrent.atomic.AtomicLong();
Payment payment = await(source).usingTime(nanos::get, delay -> nanos.addAndGet(delay)).until(present);
```

The example advances virtual time instead of sleeping. The clock and wait
operation must use the same time base; the clock must be monotonic and return
normally. The wait operation receives a duration in nanoseconds and may return
early. Custom waiting failures use the normal waiting-failure handling; clock
failures propagate directly. Defaults remain `System.nanoTime` and
`LockSupport.parkNanos`.

## Explain the condition

Every condition supports `because(...)`. It returns an immutable copy with
an explanation for diagnostics, preserving the condition's type and evaluation.
A repeated `because(...)` replaces the reason; the original condition and shared
constants remain unchanged:

```java
// Avoid: repeats the condition.
nonEmpty.because("The collection must not be empty");

// Prefer: explains the business consequence.
nonEmpty.because("Settlement requires at least one eligible payment");
```

The formatting overload is eager and uses `Locale.ROOT`:

```java
await(paymentRepository::load).until(equalTo(expectedPayment).because(
        "Refund processing requires payment %s in the replica", paymentId));
```

Java does not propagate a target type through a chained generic factory call.
When `matches(...)` is followed immediately by `because(...)`, a method
reference supplies the input type. For a lambda expression, declare its
parameter type:

```java
await(paymentRepository::load).until(matches(Payment::finished).because(
        "Settlement requires a finished payment"));
await(paymentRepository::load).until(matches((Payment payment) -> payment.status() == FINISHED).because(
        "Settlement requires a finished payment"));
```

For emptiness, use `empty.because(...)` or `nonEmpty.because(...)` from the
appropriate catalogue. A generic factory cannot infer its operand type from
an untyped empty `List.of()`, an empty varargs list, or a `null` literal before
a chained `because(...)`. Use `isNull` for a null check.

## Selection and checking

Put a condition inside the selection:

```java
Payment payment = await(paymentRepository::findAll).until(single(matches(Payment::paid)));
Payment paid = await(paymentRepository::findAll).until(single(matches(Payment::paid)).because(
        "Settlement requires a paid payment"));
Payment found = await(paymentRepository::find).until(hasValue(matches(Payment::paid)));
```

`single` requires a collection containing exactly one element.
`single(condition)` requires exactly one matching element among all elements;
other elements may be present. No matches or several matches cause a retry.
The source is read once per attempt. `singleEntry(...)` applies the same rule to
map entries; `hasValue(...)` checks an optional value.

Structural single-value selections use one iterator to distinguish zero, one,
and several values, without counting the whole collection. This also avoids
using a stale size when a concurrent collection changes before iteration.
`first` and `last` select through forward and reverse traversal; an empty
traversal causes a retry. `element(index)` traverses up to the requested position,
so its cost grows with the index even for an array-backed list. These selections
use the values seen in one traversal instead of a separate size check and read.
The observation remains a caller-owned object, not an atomic snapshot.

The nested condition can preserve the selected value (`matches`, `asserted`,
`condition`), compare it with an expected value (`equalTo`), or narrow its type
(`instanceOf`). Its explanation and expected value are
retained in diagnostics. Callback failures stop evaluation and retain their
original cause. If a callback sets the thread's interrupt flag, selection stops
before invoking the next callback. A nested factory initializes lazily on the first candidate and
keeps its state across candidates and retries within that stage.

Type selection uses the same composition:

```java
Payment payment = await(repository::findAll).until(single(instanceOf(Payment.class)));
Payment found = await(repository::find).until(hasValue(instanceOf(Payment.class)));
```

To collect selected values in order, pass several selections to the terminal:

```java
List<Payment> payments = await(paymentRepository::findAll).until(
        single(matches(Payment::created)).because("Creation"),
        single(matches(Payment::paid)).because("Payment"));
```

## Custom conditions

Use `condition(...)` for a named check with an explicit satisfaction result:

```java
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.Conditions.condition;

Payment payment = await(paymentRepository::load).until(condition(
        "payment has a receipt",
        actual -> actual.receipt() == null
                ? unsatisfied("receipt was absent")
                : satisfied(actual)));
```

Custom conditions preserve the observed object. Use `unsatisfied()` when no
extra mismatch text is needed, or `unsatisfied(message, cause)` to attach
diagnostics. A condition returns only `Satisfied` or `Unsatisfied`; any thrown
`Throwable` becomes an uncontrolled attempt and stops the wait.

`condition(description, callback)` reuses the supplied callback. For state that
must start fresh for each wait, use the overload accepting a JDK `Callable`
that constructs a `CheckedFunction`:

```java
await(paymentRepository::load).until(condition("two paid observations", () -> {
    int[] paidObservations = {0};
    return (Payment actual) -> actual.paid() && ++paidObservations[0] >= 2
            ? satisfied(actual)
            : unsatisfied();
}).because("Payment must be observed as paid twice"));
```

Creation is lazy, once per wait when the condition is first evaluated. Both
creation and evaluation may throw checked exceptions; either failure stops
the wait with its original cause. To observe a derived value, project it in
the source, for example `await(() -> paymentRepository.load().receipt())`.

## Condition catalogues

Import only the catalogue used by a test. Shared names such as `empty`,
`nonEmpty`, `contains`, and `size` are intentionally domain-specific.

| Provider | Conditions | Successful result |
| --- | --- | --- |
| `Conditions` | `condition`, `asserted`, object equality and identity, type checks, `matches`, and comparable ranges | observed or narrowed value |
| `OptionalConditions` | `present`, `absent`, `hasValue` | contained or narrowed value; original optional for `absent` |
| `StringConditions` | empty/blank checks, content, prefix, suffix, regex, case-insensitive equality, and `length...` | observed string |
| `CollectionConditions` | `single`, empty/null/duplicate checks, quantifiers, membership, exact content, sequences, `first`, `last`, `element`, `sorted`, and `size...` | observed collection or selected element |
| `MapConditions` | `singleEntry`, empty checks, entry/key/value quantifiers and membership, exact content, `valueFor`, `entryFor`, `onlyValueFor`, and `size...` | observed map, selected entry, or value |

For collection membership, `contains(value)` and `doesNotContain(value)` compare
one element. A collection or array remains one element. To check a set of
expected elements, use `containsAll(collection)` or `doesNotContainAny(collection)`,
for example `containsAll(List.of("a", "b"))`. For null checks, use `containsNull`
and `doesNotContainNull`.

The other bulk membership and exact-content factories also take a collection:
`containsAny(List.of("a", "b"))`, `doesNotContainAll(List.of("a", "b"))`,
`containsOnly(List.of("a", "b"))`, and the `containsExactly...` /
`doesNotContainExactly...` pairs. Each operation has one collection overload.
For an array as one expected element, use `Collections.singletonList(array)`,
for example `containsOnly(Collections.singletonList(array))`.
`List.of(array)` expands a reference array into separate elements.

Ordered expectations use the same collection form: `startsWith(List.of("a", "b"))`,
`endsWith(...)`, `containsSequence(...)`, and `containsSubsequence(...)`, as do
`doesNotContainSequence(...)` and `doesNotContainSubsequence(...)`.
A pattern must not be empty. Use `Collections.singletonList(array)`
when a reference array is one element of the pattern.

Maps follow the same rule: `containsKey(key)`, `doesNotContainKey(key)`,
`containsValue(value)` and `doesNotContainValue(value)` check one object.
`containsKeys(collection)`, `doesNotContainKeys(collection)`,
`containsValues(collection)`, `doesNotContainValues(collection)` and
`containsOnlyKeys(collection)` accept collections of expected keys or values.

Factories accepting collections of expected elements, maps of expected entries,
or varargs candidates take a shallow snapshot when the condition is created.
Changing the original container afterwards does not change the condition or its
diagnostic reference. Encounter order, duplicates and supported nulls are retained.
Non-empty requirements apply to the captured snapshot.
Reading these inputs happens during construction; a failure to read them is
thrown there, before polling starts.

Elements, keys, values, and callbacks remain caller-owned; they are not deeply
copied. Single-value operands such as `equalTo(value)`, `sameAs(value)`,
`contains(value)` and `doesNotContain(value)`
as well as the single-key and single-value map conditions
retain the supplied object, even if it is itself a container. This also applies
when they are nested inside a selection such as `hasValue(equalTo(value))`.
Observed values and successful results also retain their original identity.
Optional value conditions require a present `Optional`; use `absent` when
emptiness itself is the expectation.

Qualify a provider when a test genuinely needs colliding catalogues:

```java
await(paymentRepository::findAll).until(CollectionConditions.nonEmpty);
await(paymentRepository::index).until(MapConditions.nonEmpty);
```

## Sources

`await(...)` accepts a repeatedly invokable source, never a direct value.
Sources may throw checked exceptions. Concrete-return lambdas and method
references normally select the right source category automatically.

The marker interfaces are an escape hatch for an otherwise ambiguous source,
such as one that only returns `null`:

```java
import io.github.gromoff97.awium.Await.OptionalSource;

OptionalSource<Payment> source = () -> null;
await(source).until(isNull);
```

A variable declared as a plain JDK `Callable` uses scalar conditions, even
when its return type is an optional, collection, or map.
Pass its method reference to recover the family without another source type:

```java
import java.util.concurrent.Callable;

Callable<Optional<Payment>> payment = paymentRepository::find;
Callable<List<Payment>> payments = paymentRepository::findAll;

Payment found = await(payment::call).until(present.because("The payment is required"));
Payment onlyPayment = await(payments::call).until(single);
```

Alternatively, declare the corresponding marker when selection is needed:

```java
import io.github.gromoff97.awium.Await.CollectionSource;
import io.github.gromoff97.awium.Await.MapSource;

CollectionSource<List<Payment>> payments = paymentRepository::findAll;
MapSource<Map<String, Payment>> index = paymentRepository::index;

Payment onlyPayment = await(payments).until(single);
Map.Entry<String, Payment> onlyEntry = await(index).until(singleEntry);
```

For covariant containers, use a lambda to retain the selected wildcard bounds:

```java
Payment payment = await(() -> paymentRepository.findAllView()).until(single.because("One payment is required"));
Map.Entry<? extends String, ? extends Payment> entry = await(() -> paymentRepository.indexView()).until(singleEntry);
```

The lambda works without explicit type arguments. A direct method reference to
a wildcard-returning method can resolve to the scalar overload instead.
When a stable observed container type is needed, use the view wrappers to fix
both the container type and its selected bound. This matters for an explicitly
typed `AwaitResult` or comparison with another covariant container:

```java
import io.github.gromoff97.awium.Await.CollectionViewSource;
import io.github.gromoff97.awium.Await.MapViewSource;

var payments = new CollectionViewSource<Payment, List<? extends Payment>>(paymentRepository::findAllView);
var index = new MapViewSource<String, Payment, Map<? extends String, ? extends Payment>>(paymentRepository::indexView);

Payment payment = await(payments).until(single);
Map.Entry<? extends String, ? extends Payment> entry = await(index).until(singleEntry);
```

Use typed views when collecting mutable covariant values across observations,
too: they keep the observations' wildcard types independent despite
[Java's lambda capture limitation](https://bugs.openjdk.org/browse/JDK-8170887).

`until(...)` or `tryUntil(...)` starts the wait. Success never invokes the source again merely to
obtain the return value. A retained stage may be reused sequentially; every
wait gets fresh timing. Built-in and factory-backed conditions also get fresh
evaluation state. State in a callback passed directly to `condition(...)`
remains owned by the caller.

## Diagnostic waits

Finish the same `await(...)` chain with `tryUntil(...)` to return an
`AwaitResult<OBSERVED, RESULT>` for both success and failure:

```java
import static io.github.gromoff97.awium.Await.await;

AwaitResult<Optional<Payment>, Payment> result =
        await(paymentRepository::find).upTo(TIMEOUT).tryUntil(present);
```

`AwaitResult.Satisfied` contains the terminal result. `AwaitResult.Failed`
contains an `AwaitFailure` with `reason()`, `message()`, the original `cause()`
and immutable `suppressed()` causes. It does not construct a wrapper exception:

```java
if (result instanceof AwaitResult.Failed<?, ?> failed) {
    switch (failed.failure().reason()) {
        case TIMEOUT -> reportTimeout(failed.failure().message());
        default -> reportFailure(failed.failure());
    }
}
```

Both results expose `attempts()`, an immutable list of `AttemptRecord` values.
Each record has a one-based `number()`, an acquisition or persistence `phase()`,
and an `outcome()`. `AttemptRecord.Outcome.Evaluated` holds the observed value
and its `evaluation()`: `ConditionResult.Satisfied` or `Unsatisfied`. Thrown
condition failures use `ConditionFailed`. Waiting, source retrieval, and source
interruption have their own outcomes, so an absent observation is distinct
from a legitimate `null` value. All outcomes expose `cause()`.

Each outcome has one `AttemptRecord.Timing` with offsets from the start of the
terminal call. `startOffset()` marks the attempt's start before waiting;
`retrievalOffset()` marks the start of the source call, `observationOffset()`
its successful return, and `completionOffset()` the end of the attempt. A
retrieval or observation offset is `null` if that checkpoint was not reached.

History retains at most 256 entries: the first attempt and the latest 255,
including the terminal attempt. Repeated observations remain separate records.
`attempts().size()` is the retained count; `totalAttempts()` is the complete
count, obtained from the last record's number. Captured stage lists and the
history list are immutable; observed objects keep their original identity.

## Threading and interruption

Polling, source retrieval, and condition evaluation run on the exact platform
or virtual thread that calls `until(...)` or `tryUntil(...)`. Awium creates no worker, executor,
scheduler, or virtual thread, so caller `ThreadLocal` values remain visible.

Definitions and configurations are immutable, and each terminal call owns its
execution state. Callbacks and user-owned mutable values remain responsible
for any synchronization they need.

An interrupt flag set before or during the wait stops polling. Awium preserves
the flag when reading the clock and formatting diagnostics.
`until(...)` throws `AwaitExecutionException` with
`failure().reason() == INTERRUPTED`; `tryUntil(...)` returns that reason in
`AwaitResult.Failed`. A callback that throws `InterruptedException` after
clearing the flag is treated like any other thrown failure; Awium does not
restore the flag based on the exception type. Because callbacks run in the
caller, Awium cannot preempt a source or condition that blocks indefinitely.

## Failures

`until(...)` throws one of two final types. Both expose `failure()` with the
same structured data returned by `tryUntil(...)`:

| Exception | Failure reasons |
| --- | --- |
| `AwaitAssertionError extends AssertionError` | `TIMEOUT`, `PERSISTENCE_FAILED` |
| `AwaitExecutionException extends RuntimeException` | `SOURCE_FAILED`, `CONDITION_FAILED`, `INTERRUPTED`, `WAIT_FAILED` |

The exception's message and cause come from `AwaitFailure`. Ordinary mismatches
may have no cause; assertion mismatches retain the original assertion. If
formatting fails, the reason and original cause stay unchanged. Distinct
formatting failures are retained by identity in `suppressed()` and in
`getSuppressed()` on the thrown wrapper. Awium does not mutate the original
throwable to attach diagnostics.

Invalid sources, conditions, and durations fail before polling. Every thrown
`Throwable` from a source, condition, evaluator factory, or wait operation
stops polling and is retained as the cause. Only the explicit `asserted(...)`
adapter treats an `AssertionError` as an unsatisfied condition.

## Development

Run `./gradlew check` for behavior, fluent compilation, and packaged-module
checks; `./gradlew pitest` runs mutation testing.

`Condition` owns the immutable definition, explanation, and diagnostic reference.
Its typed families let terminal overloads preserve or select the source's type.
Each terminal call creates fresh `ConditionAwaitAttempt` instances, which
initialize callbacks lazily and convert thrown failures into internal
uncontrolled results. `SequenceExecution` owns ordered stage progress and
captured values. Public `ConditionResult` has only satisfaction and mismatch.

`ValueAwait` and `Await` retain immutable fluent configuration. `WaitEngine`
owns polling, source retrieval, and attempt timing; `ValueAwait` bounds the
history for `tryUntil`. `FailureDiagnostics` formats the terminal failure
without replacing its category or cause. A successful check can still finish
too late.

`ConditionSupport` shares condition construction, checks, extraction, and
composition. Failed map extraction reports the lookup key, including when
entry access throws. After extraction succeeds, diagnostics use the nested
condition's expectation.

Catalog and fluent behavior tests use virtual time. Integration tests verify
caller-thread execution and external cancellation on both platform and virtual
threads, plus independent condition state during concurrent waits.

The module exports one package. Implementation types are package-private on
both the module path and the classpath; custom conditions use the factories in
`Conditions`. Artifact checks also verify that execution classes do not depend
on the fluent facade, condition catalogs, or diagnostic formatting.

Awium is licensed under the [Apache License 2.0](LICENSE).
