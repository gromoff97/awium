# Awium

Awium is a zero-dependency Java 21 library that waits for a condition in the
calling thread and returns the result from the same successful observation.

```java
import static io.github.gromoff97.awium.await.Await.await;
import static io.github.gromoff97.awium.conditions.OptionalConditions.present;

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
library itself.

## The four condition forms

The condition supplied to `until(...)` determines both success and the
terminal result type.

### Preserve the observed value

Most built-in conditions return the exact object obtained from the source:

```java
import static io.github.gromoff97.awium.conditions.Conditions.equalTo;

Payment payment = await(paymentRepository::load).until(equalTo(expectedPayment));
```

Collection, map, string, and comparable conditions preserve their source in
the same way.

### Select a value

Selection conditions return a value contained in the observation:

```java
import static io.github.gromoff97.awium.conditions.CollectionConditions.single;
import static io.github.gromoff97.awium.conditions.MapConditions.singleEntry;
import static io.github.gromoff97.awium.conditions.OptionalConditions.present;

Payment payment = await(paymentRepository::find).until(present);
Payment onlyPayment = await(paymentRepository::findAll).until(single);
Map.Entry<String, Payment> entry = await(paymentRepository::index).until(singleEntry);
```

`present`, `single`, `first`, `last`, and `singleEntry` are fields, so they do
not need parentheses. `isNull` and `absent` return `Void` and are normally used
as statements.

### Assert or transform

`asserted(...)` preserves the observed value. An `AssertionError` means that
the condition is currently unsatisfied, so polling continues:

```java
import static io.github.gromoff97.awium.conditions.Conditions.asserted;

Payment payment = await(paymentRepository::load).until(asserted(actual -> {
    if (!actual.isComplete()) {
        throw new AssertionError("payment was not complete");
    }
}));
```

`yields(...)` returns the callback result instead:

```java
import static io.github.gromoff97.awium.conditions.Conditions.yields;

Receipt receipt = await(paymentRepository::load).until(yields(Payment::receipt));
```

`asserted(...)` accepts a `CheckedConsumer`; `yields(...)`, `condition(...)`,
and `preserving(...)` accept a `CheckedFunction`. These callbacks may throw
checked exceptions, so existing methods declaring `throws Exception` can be
passed directly. Callback failures stop polling immediately and retain the
original cause; only `AssertionError` inside `asserted(...)` means retry.
Interruption retains its normal cancellation semantics.

For callbacks already stored in JDK `Function` or `Consumer` variables, pass
`function::apply` or `consumer::accept`. Predicates still use the JDK interfaces.

### Ordered states

Pass several compatible conditions to `until(...)` to wait for them in order.
Each stage captures a value from a separate observation; the result is a list
in stage order. `tryUntil(...)` returns the same list inside `AwaitResult`:

```java
List<Payment> payments = await(paymentRepository::load).until(
        payment -> payment.status() == CREATED,
        payment -> payment.status() == PENDING,
        payment -> payment.status() == FINISHED);

AwaitResult<String, List<String>> statuses = await(paymentRepository::status).tryUntil(
        equalTo("created").because("The order must be created"),
        equalTo("paid").because("Payment must complete"));
```

With `persisting(...)`, only the final stage is re-evaluated. Earlier captured
values remain unchanged. Each factory-backed stage gets its own fresh evaluator
for every terminal call. One condition still returns one value; zero conditions
are invalid. A comma between terminal arguments always means ordered capture.
The stages must belong to a compatible condition family; a selection followed
by a value check belongs inside the selection condition instead.

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

## Business importance

Every condition supports `because(...)`. It returns an immutable copy with
diagnostic business importance, preserving the condition's type and evaluation.
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
When `matches(...)` is followed immediately by `because(...)`, declare the
lambda parameter type:

```java
await(paymentRepository::load).until(matches((Payment payment) -> payment.status() == FINISHED).because(
        "Settlement requires a finished payment"));
```

## Selection and checking

Put a predicate or nested condition inside the selection:

```java
Payment payment = await(paymentRepository::findAll).until(single(Payment::paid));
Payment paid = await(paymentRepository::findAll).until(single(Payment::paid).because(
        "Settlement requires a paid payment"));
String status = await(paymentRepository::find).until(hasValue(yields(Payment::status)));
```

`single` requires a collection containing exactly one element. `single(predicate)`
and `single(condition)` require exactly one matching element among all elements;
other elements may be present. No matches or several matches cause a retry.
The source is read once per attempt. `singleEntry(...)` applies the same rule to
map entries; `hasValue(...)` checks an optional value.

The nested condition can preserve the selected value (`matches`, `asserted`),
compare it with an expected value (`equalTo`), narrow its type (`instanceOf`),
or transform it (`yields`, `condition`). Its explanation and expected value are
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
        single(Payment::created).because("Creation"),
        single(Payment::paid).because("Payment"));
```

## Custom conditions

Use `condition(...)` when neither a predicate, `asserted(...)`, nor
`yields(...)` describes the result:

```java
import static io.github.gromoff97.awium.condition.ConditionEvaluation.satisfied;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.unsatisfied;
import static io.github.gromoff97.awium.conditions.Conditions.condition;

Receipt receipt = await(paymentRepository::load).until(condition(
        "payment has a receipt",
        payment -> payment.receipt() == null
                ? unsatisfied("receipt was absent")
                : satisfied(payment.receipt())));
```

`condition(...)` reuses the supplied callback. For a stateful evaluator that
must start fresh for each wait, supply its construction through
`conditionFactory(...)` instead. Use `preserving(...)` when a custom condition
returns the observed type, or `preservingFactory(...)` when it is both
preserving and stateful. It can then participate in an ordered wait alongside
other preserving conditions.

Both factories accept a JDK `Callable` returning a `CheckedFunction`, so
creating the evaluator may also throw a checked exception. Creation is lazy,
once per wait when the condition is first evaluated; a creation failure uses
the same failure handling as an evaluation failure.

## Condition catalogues

Import only the catalogue used by a test. Shared names such as `empty`,
`nonEmpty`, `contains`, and `size` are intentionally domain-specific.

| Provider | Conditions | Successful result |
| --- | --- | --- |
| `Conditions` | custom condition and preserving factories, `asserted`, `yields`, object equality and identity, type checks, `matches`, and comparable ranges | observed, narrowed, or transformed value |
| `OptionalConditions` | `present`, `absent`, `hasValue`, `doesNotHaveValue` | contained, transformed, or narrowed value; `Void` for `absent` |
| `StringConditions` | empty/blank checks, content, prefix, suffix, regex, case-insensitive equality, and `length...` | observed string |
| `CollectionConditions` | `single`, empty/null/duplicate checks, quantifiers, membership, exact content, sequences, `first`, `last`, `element`, `sorted`, and `size...` | observed collection or selected element |
| `MapConditions` | `singleEntry`, empty checks, entry/key/value quantifiers and membership, exact content, `valueFor`, `entryFor`, `onlyValueFor`, and `size...` | observed map, selected entry, or value |

Expected objects and aggregates remain caller-owned: conditions retain their
references and read their current contents on every evaluation. Optional value
conditions, including negative ones such as `doesNotHaveValue`, require a
present `Optional`; use `absent` when emptiness itself is the expectation.

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
import io.github.gromoff97.awium.sources.Source.OptionalSource;

OptionalSource<Payment> source = () -> null;
await(source).until(isNull);
```

A variable declared as plain `Source<List<Payment>>` or
`Source<Map<String, Payment>>` does not retain its selected element family.
Pass its method reference to recover the family without another source type:

```java
Source<Optional<Payment>> payment = paymentRepository::find;
Source<List<Payment>> payments = paymentRepository::findAll;

Payment found = await(payment::get).until(present.because("The payment is required"));
Payment onlyPayment = await(payments::get).until(single);
```

Alternatively, declare the corresponding marker when selection is needed:

```java
import io.github.gromoff97.awium.sources.Source.CollectionSource;
import io.github.gromoff97.awium.sources.Source.MapSource;

CollectionSource<List<Payment>> payments = paymentRepository::findAll;
MapSource<Map<String, Payment>> index = paymentRepository::index;

Payment onlyPayment = await(payments).until(single);
Map.Entry<String, Payment> onlyEntry = await(index).until(singleEntry);
```

Covariant containers use the explicit view wrappers so the selected wildcard
types remain safe:

```java
import io.github.gromoff97.awium.sources.Source.CollectionViewSource;
import io.github.gromoff97.awium.sources.Source.MapViewSource;

var payments = new CollectionViewSource<Payment, List<? extends Payment>>(paymentRepository::findAllView);
var index = new MapViewSource<String, Payment, Map<? extends String, ? extends Payment>>(paymentRepository::indexView);

Payment payment = await(payments).until(single);
Map.Entry<? extends String, ? extends Payment> entry = await(index).until(singleEntry);
```

`until(...)` or `tryUntil(...)` starts the wait. Success never invokes the source again merely to
obtain the return value. A retained stage may be reused sequentially; every
wait gets fresh timing. Built-in and factory-backed conditions also get fresh
evaluation state. State in a callback passed directly to `condition(...)` or
`preserving(...)` remains owned by the caller.

## Diagnostic waits

Finish the same `await(...)` chain with `tryUntil(...)` to return an
`AwaitResult<S, R>` for both success and failure:

```java
import static io.github.gromoff97.awium.await.Await.await;

AwaitResult<Optional<Payment>, Payment> result =
        await(paymentRepository::find).upTo(TIMEOUT).tryUntil(present);
```

`AwaitResult.Satisfied` contains the terminal result. `AwaitResult.Failed`
contains the failure. Both expose retained `AwaitAttempt` history and the total
attempt count. Each adjacent run of equivalent attempts is represented by its
latest attempt, retaining the endpoint number and timing without retaining the
whole run. Equivalence is deliberately identity-based for observed and result
objects (plus equal built-in diagnostic text and context); Awium never invokes
user equality merely to compress history. Fresh value-equal objects therefore
remain separate attempts. History retains at most 256 entries: the first entry
and the latest 255, including the terminal attempt. `totalAttempts` remains the
complete uncompressed count.

## Threading and interruption

Polling, source retrieval, and condition evaluation run on the exact platform
or virtual thread that calls `until(...)` or `tryUntil(...)`. Awium creates no worker, executor,
scheduler, or virtual thread, so caller `ThreadLocal` values remain visible.

This release supports one-thread use only. Another thread may interrupt the
caller as an external cancellation controller, but it must not access or mutate
the stage, source, condition, expected values, or observed objects. Awium
restores the interrupt flag. `until(...)` throws `AwaitInterruptedException`;
`tryUntil(...)` returns it in `AwaitResult.Failed`. Because callbacks run in the
caller, Awium cannot preempt a source or condition that blocks indefinitely.

## Failures

Expected unsuccessful waits are assertion failures:

```text
AwaitFailure extends AssertionError
├── AwaitTimeoutException
└── AwaitPersistenceException
```

Broken execution is unchecked and preserves the exact cause:

```text
AwaitUncontrolledException extends RuntimeException
├── AwaitSourceRetrievalException
├── AwaitConditionEvaluationException
├── AwaitInterruptedException
└── AwaitUnhandledException
```

Invalid sources, conditions, and durations fail before polling.
`VirtualMachineError` and `ThreadDeath` are rethrown
unchanged.

## Development

Run `./gradlew check` for behavior, fluent compilation, and packaged-module
checks; `./gradlew pitest` runs mutation testing.

`internal.condition` owns condition construction, per-wait evaluation state,
and shared diagnostic metadata. Its references to the sealed condition API
are intentional. `WaitEngine` owns timing, `ObservationEvaluator` owns source
and callback execution, and `AttemptHistory` owns history retention and
compression. `FailureFactory` interprets outcomes and prepares diagnostic
data; `FailureMessageRenderer` formats it.

Condition composition shares extraction and per-wait session creation in
`ConditionSupport`. Failed extraction carries its own expectation, so missing
map keys remain distinguishable from mismatches in nested conditions. Successful
attempts retain diagnostic context too: a late success can still time out.

Catalog and fluent behavior tests use virtual time; dedicated real-time and
virtual-thread integration tests exercise the platform wait operation.

Internal packages are not exported by JPMS. On the classpath they remain
implementation details; custom conditions should use the factories in
`Conditions`.

Awium is licensed under the [Apache License 2.0](LICENSE).
