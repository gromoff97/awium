# Awium

Awium is a Java 21 await-and-assert library with no compile or runtime
dependencies. It polls a checked source in the calling thread and returns the
natural result from the same successful observation.

```java
import static io.github.gromoff97.awium.await.Await.await;
import static io.github.gromoff97.awium.conditioning.providers.OptionalConditionProvider.present;

import java.time.Duration;

Payment payment = await(paymentRepository::findById)
        .every(Duration.ofMillis(100))
        .upTo(Duration.ofSeconds(10))
        .stableFor(Duration.ofSeconds(5))
        .until(present.because("Checkout cannot continue without the payment"));
```

## Installation

```kotlin
dependencies {
    testImplementation("io.github.gromoff97:awium:0.1.0-SNAPSHOT")
}
```

The library requires Java 21. Its published compile and runtime dependency
graphs are empty; JUnit and OpenRewrite are used only to test Awium itself.

Examples below assume:

```java
import static io.github.gromoff97.awium.await.Await.await;
import static io.github.gromoff97.awium.conditioning.conditions.StructuralCondition.*;
import static io.github.gromoff97.awium.conditioning.providers.CollectionConditionProvider.*;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;
import static io.github.gromoff97.awium.conditioning.providers.MapConditionProvider.*;
import static io.github.gromoff97.awium.conditioning.providers.ObjectConditionProvider.*;
import static io.github.gromoff97.awium.conditioning.providers.OptionalConditionProvider.*;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.sources.OptionalSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
```

## Sources and timing

`await(...)` accepts a repeatedly invokable source, never a direct value.
Concrete-return lambdas and method references normally select the right
category. The checked-exception-capable `Source<T>`, `OptionalSource<T>`,
`CollectionSource<C>`, and `MapSource<M>` interfaces are needed only to type an
otherwise ambiguous source explicitly:

```java
OptionalSource<Payment> source = () -> null;
await(source).until(absent);
```

`every`, `upTo`, and `stableFor` are optional and may be called in any order or
repeated. Each call returns a new immutable stage; the last value supplied for a
setting is used by `until(...)`. Defaults are:

- `every`: 100 milliseconds
- `upTo`: 10 seconds
- `stableFor`: zero (disabled)

Each duration is validated when supplied. The final `every < upTo` relationship
is validated by `until(...)` before polling, so a temporarily conflicting
intermediate configuration may be repaired by a later call.

The first observation is immediate. `every` is a fixed delay from the end of
one observation to the start of the next. `upTo` limits acquisition only. Once
the condition first succeeds, a configured `stableFor` period may extend the
total call beyond `upTo`; success then returns the final satisfied boundary
observation. A stability mismatch fails immediately instead of restarting
acquisition.

`until(...)` starts the wait. The source runs once per observation, and success
never invokes it again just to obtain the return value. A retained stage may be
used for multiple sequential waits; each call gets fresh timing and attempt
state while retaining the caller-owned source and condition objects.

## Result typing and conditions

The condition determines the terminal result type:

- preserving conditions such as `equalTo`, `nonEmpty`, and `containsEntry`
  return the exact observed source value;
- `present` returns the contained `T` from `Optional<T>`;
- custom `Condition<S, R>` instances return their selected `R`;
- `isNull` and `absent` return `Void` and are normally used as statements.

```java
Payment payment = await(paymentRepository::load)
        .until(equalTo(expectedPayment));

Receipt receipt = await(paymentRepository::load)
        .until(condition("payment has a receipt", payment ->
                payment.receipt() == null
                        ? Evaluation.unsatisfied("receipt was absent")
                        : Evaluation.satisfied(payment.receipt())));
```

Every raw condition form supports one eager `because(...)` explanation. It is
included in terminal diagnostics without changing evaluation or result typing.
`because` states why the business requires the condition; it should not merely
repeat what the condition checks:

```java
// Avoid: repeats nonEmpty.
nonEmpty.because("The collection must not be empty");

// Prefer: states the business consequence.
nonEmpty.because("Settlement requires at least one eligible payment");

await(paymentRepository::load)
        .until(equalTo(expectedPayment)
                .because("Refund processing requires payment %s in the replica",
                        paymentId));
```

Sources and callback adapters may throw checked exceptions. `asserted(...)`
retries an `AssertionError` and preserves the observed source; `passed(...)`
returns the callback result:

```java
Payment payment = await(paymentRepository::loadChecked)
        .until(asserted(actual -> {
    if (!actual.isComplete()) {
        throw new AssertionError("payment was not complete");
    }
}));

Receipt receipt = await(paymentRepository::loadChecked)
        .until(passed(Payment::loadReceiptChecked));
```

Other checked or unchecked callback failures stop immediately and preserve the
original cause.

## Collection and Map examples

Collection state and membership conditions preserve the concrete collection:

```java
List<Payment> payments = await(paymentRepository::findAll)
        .until(nonEmpty.because(
                "Settlement requires at least one eligible payment"));

List<Payment> exact = await(paymentRepository::findAll)
        .until(containsExactly(firstPayment, secondPayment));

Set<Payment> accepted = await(paymentRepository::findAccepted)
        .until(containsAll(firstPayment, secondPayment));
```

Ordered exactness is available only when the source return type is a Java 21
`SequencedCollection`. General collections provide membership and any-order
exactness through conditions such as `containsExactlyInAnyOrder(...)`.

Map conditions likewise return the concrete map:

```java
Map<String, Payment> indexed = await(paymentRepository::index)
        .until(containsEntry(paymentId, expectedPayment));

Map<String, Payment> completeIndex = await(paymentRepository::index)
        .until(containsExactlyEntriesOf(expectedIndex)
                .because("Reconciliation requires the complete payment index"));
```

## Threading and interruption

Polling, source retrieval, and condition evaluation all run on the exact
platform or virtual thread that calls `until(...)`. Awium creates no
worker, executor, scheduler, or virtual thread, so caller `ThreadLocal` values
remain visible.

This first release supports one-thread use only. Another thread may interrupt
the caller as an external cancellation controller, but it must not access or
mutate the stage, source, condition, expected values, or observed in-memory
objects. Awium restores the interrupt flag and throws
`AwaitInterruptedException`. Because callbacks run in the caller, Awium
cannot preempt a source or condition that blocks indefinitely.

## Failures

Expected completed waits are assertion failures:

```text
AwaitFailure extends AssertionError
├── AwaitTimeoutException
└── AwaitStabilizationException
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
