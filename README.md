# Awium

Awium is a zero-dependency Java 21 library that waits for a condition in the
calling thread and returns the result from the same successful observation.

```java
import static io.github.gromoff97.awium.fluent.Await.await;
import static io.github.gromoff97.awium.fluent.OptionalConditions.present;

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
import static io.github.gromoff97.awium.fluent.Conditions.equalTo;

Payment payment = await(paymentRepository::load).until(equalTo(expectedPayment));
```

Collection, map, string, and comparable conditions preserve their source in
the same way.

### Select a value

Selection conditions return a value contained in the observation:

```java
import static io.github.gromoff97.awium.fluent.CollectionConditions.single;
import static io.github.gromoff97.awium.fluent.MapConditions.singleEntry;
import static io.github.gromoff97.awium.fluent.OptionalConditions.present;

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
import static io.github.gromoff97.awium.fluent.Conditions.asserted;

Payment payment = await(paymentRepository::load).until(asserted(actual -> {
    if (!actual.isComplete()) {
        throw new AssertionError("payment was not complete");
    }
}));
```

`yields(...)` returns the callback result instead:

```java
import static io.github.gromoff97.awium.fluent.Conditions.yields;

Receipt receipt = await(paymentRepository::load).until(yields(Payment::receipt));
```

Public callbacks use JDK functional interfaces. Callers must handle or convert
checked callback exceptions themselves. Unchecked callback failures are
uncontrolled failures and stop polling immediately.

### Capture ordered states

`captured(...)` accepts at least two predicates or compatible conditions. It
evaluates one stage at a time and returns one captured result per stage:

```java
import static io.github.gromoff97.awium.fluent.Conditions.captured;

List<Payment> lifecycle = await(paymentRepository::load).until(captured(
        payment -> payment.status() == CREATED,
        payment -> payment.status() == PENDING,
        payment -> payment.status() == FINISHED));
```

With `persisting(...)`, only the final stage is re-evaluated. Earlier captured
values remain unchanged.

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

Each duration is validated when supplied. The final `every < upTo`
relationship is validated by `until(...)` before polling.

## Business importance

Every raw condition supports one `because(...)`. It adds diagnostic business
importance without changing evaluation or result typing:

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
var finished = matches((Payment payment) -> payment.status() == FINISHED).because(
        "Settlement requires a finished payment");
```

## Custom conditions

Use `condition(...)` when neither a predicate, `asserted(...)`, nor
`yields(...)` describes the result:

```java
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.satisfied;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.unsatisfied;
import static io.github.gromoff97.awium.fluent.Conditions.condition;

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
preserving and stateful. This also lets `captured(...)` recognize it as a
preserving stage.

## Condition catalogues

Import only the catalogue used by a test. Shared names such as `empty`,
`nonEmpty`, `contains`, and `size` are intentionally domain-specific.

| Provider | Conditions | Successful result |
| --- | --- | --- |
| `Conditions` | custom condition and preserving factories, `asserted`, `yields`, `captured`, object equality and identity, type checks, `matches`, and comparable ranges | observed, narrowed, transformed, or captured value |
| `OptionalConditions` | `present`, `absent`, `hasValue`, `doesNotHaveValue`, `containsInstanceOf` | contained, transformed, or narrowed value; `Void` for `absent` |
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
Use the corresponding marker when selection is needed:

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

`until(...)` starts the wait. Success never invokes the source again merely to
obtain the return value. A retained stage may be reused sequentially; every
wait gets fresh timing. Built-in and factory-backed conditions also get fresh
evaluation state. State in a callback passed directly to `condition(...)` or
`preserving(...)` remains owned by the caller.

## Diagnostic waits

`tryAwait(...)` has the same fluent grammar and evaluation semantics as
`await(...)`, but returns one `AwaitResult<S, R>` for both success and failure:

```java
import static io.github.gromoff97.awium.fluent.Await.tryAwait;

AwaitResult<Optional<Payment>, Payment> result =
        tryAwait(paymentRepository::find).upTo(TIMEOUT).until(present);
```

`AwaitResult.Satisfied` contains the terminal result. `AwaitResult.Failed`
contains the failure. Both expose retained `AwaitAttempt` history and the total
attempt count. Each adjacent run of equivalent attempts is represented by its
latest attempt, retaining the endpoint number and timing without retaining the
whole run. Equivalence is deliberately identity-based for observed and result
objects (plus equal built-in diagnostic text and context); Awium never invokes
user equality merely to compress history. Fresh value-equal objects therefore
remain separate attempts. There is intentionally no history limit: memory use
grows with the number and reachable object graphs of non-equivalent
observations, so choose `every` and `upTo` accordingly for diagnostic waits.

## Threading and interruption

Polling, source retrieval, and condition evaluation run on the exact platform
or virtual thread that calls `until(...)`. Awium creates no worker, executor,
scheduler, or virtual thread, so caller `ThreadLocal` values remain visible.

This release supports one-thread use only. Another thread may interrupt the
caller as an external cancellation controller, but it must not access or mutate
the stage, source, condition, expected values, or observed objects. Awium
restores the interrupt flag. `await(...)` throws `AwaitInterruptedException`;
`tryAwait(...)` returns it in `AwaitResult.Failed`. Because callbacks run in the
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

Invalid sources, conditions, durations, and cross-field timing configuration
fail before polling. `VirtualMachineError` and `ThreadDeath` are rethrown
unchanged.

Awium is licensed under the [Apache License 2.0](LICENSE).
