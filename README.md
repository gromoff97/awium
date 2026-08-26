# Awium

Awium is a Java 21 await-and-assert library with no compile or runtime
dependencies. It polls a checked source in the calling thread and returns the
natural result from the same successful observation.

```java
import static io.github.gromoff97.awium.await.Await.await;
import static io.github.gromoff97.awium.conditioning.conditions.OptionalCondition.present;

import java.time.Duration;

Payment payment = await(paymentRepository::findById).every(Duration.ofMillis(100)).upTo(Duration.ofSeconds(10)).persisting(Duration.ofSeconds(5)).until(
        present.because("Checkout cannot continue without the payment"));
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
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.*;
import static io.github.gromoff97.awium.conditioning.conditions.ComparableCondition.*;
import static io.github.gromoff97.awium.conditioning.conditions.MapCondition.*;
import static io.github.gromoff97.awium.conditioning.conditions.ObjectCondition.*;
import static io.github.gromoff97.awium.conditioning.conditions.OptionalCondition.*;
import static io.github.gromoff97.awium.conditioning.conditions.StringCondition.*;
import static io.github.gromoff97.awium.conditioning.conditions.Condition.*;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.sources.Source.OptionalSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
```

## Sources and timing

`await(...)` accepts a repeatedly invokable source, never a direct value.
Concrete-return lambdas and method references normally select the right
category. The checked-exception-capable `Source<T>`, `Source.OptionalSource<T>`,
`Source.CollectionSource<C>`, and `Source.MapSource<M>` interfaces are needed only to type an
otherwise ambiguous source explicitly:

```java
OptionalSource<Payment> source = () -> null;
await(source).until(absent);
```

`every`, `upTo`, and `persisting` are optional and may be called in any order or
repeated. Each call returns a new immutable stage; the last value supplied for a
setting is used by `until(...)`. Defaults are:

- `every`: 100 milliseconds
- `upTo`: 10 seconds
- `persisting`: zero (disabled)

Each duration is validated when supplied. The final `every < upTo` relationship
is validated by `until(...)` before polling, so a temporarily conflicting
intermediate configuration may be repaired by a later call.

The first observation is immediate. `every` is a fixed delay from the end of
one observation to the start of the next. `upTo` limits acquisition only. Once
the condition first succeeds, a configured `persisting` period may extend the
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
- `single` and `singleEntry` are fields, so no parentheses are needed;
  they return the sole collection element or map entry with its inferred type;
- `present` returns the contained `T` from `Optional<T>`;
- custom `Condition<S, R>` instances return their selected `R`;
- `isNull` and `absent` return `Void` and are normally used as statements.

```java
Payment payment = await(paymentRepository::load).until(equalTo(expectedPayment));

Receipt receipt = await(paymentRepository::load).until(condition("payment has a receipt", payment ->
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

await(paymentRepository::load).until(equalTo(expectedPayment).because(
        "Refund processing requires payment %s in the replica", paymentId));
```

Sources may throw checked exceptions. Public callbacks and predicates use the
JDK `Consumer`, `Function`, and `Predicate` interfaces, so callers must handle
or convert checked callback failures themselves. `asserted(...)` preserves the
observed source and treats `AssertionError` as unsatisfied so polling continues.
`yields(...)` selects its callback result; an `AssertionError` from it is an
uncontrolled condition failure and stops polling immediately:

```java
Payment payment = await(paymentRepository::loadChecked).until(asserted(actual -> {
    if (!actual.isComplete()) {
        throw new AssertionError("payment was not complete");
    }
}));

Receipt receipt = await(paymentRepository::loadChecked).until(yields(Payment::receipt));
```

Other unchecked callback failures also stop immediately and preserve the
original cause.

## Ordered capture

`caught(...)` captures at least two stages in strict order. It evaluates only
the current stage on each poll, captures at most one result, and returns the
captured results as a list:

```java
List<Payment> lifecycle = await(paymentRepository::load).until(caught(
        payment -> payment.status() == CREATED,
        payment -> payment.status() == FINISHED));
```

With `persisting(...)`, only the final stage is re-evaluated after acquisition;
earlier captures remain unchanged. Predicate stages infer their parameter type
without annotations. When `matches(...)` is chained immediately to
`because(...)`, type its generic lambda parameter explicitly:

```java
var finished = matches((Payment payment) -> payment.status() == FINISHED).because(
        "Settlement requires a finished payment");
```

## Built-in condition catalogue

Names stay close to AssertJ but omit redundant suffixes such as `Matching`.
Predicate overloads use the JDK `Predicate` type.

| Domain | Conditions | Successful result |
| --- | --- | --- |
| Object | `isNull`, `isNotNull`, `equalTo`, `notEqualTo`, `sameAs`, `notSameAs`, `instanceOf`, `exactInstanceOf`, `in`, `notIn`, `matches`, `extracting` | source value, narrowed type, or extracted value |
| Optional | `present`, `absent`, `hasValue`, `doesNotHaveValue`, `containsInstanceOf` | contained, transformed, or narrowed value; `Void` for `absent` |
| Comparable | `greaterThan`, `atLeast`, `lessThan`, `atMost`, `between`, `strictlyBetween` | source value |
| String | `empty`, `nonEmpty`, `blank`, `nonBlank`, `contains`, `doesNotContain`, `containsIgnoringCase`, `startsWith`, `doesNotStartWith`, `endsWith`, `doesNotEndWith`, `matchesRegex`, `doesNotMatchRegex`, `equalToIgnoringCase`, `notEqualToIgnoringCase`, and the `length...` family | source string |
| Collection | `single`, `empty`, `nonEmpty`, null and duplicate checks, `all`, `any`, `none`, membership and exact-content checks, prefixes, suffixes, sequences, subsequences, `first`, `last`, `element`, `sorted`, and the `size...` family | collection, or the selected element |
| Map | `singleEntry`, `empty`, `nonEmpty`, `allEntries`, `anyEntry`, `noEntry`, key/value quantifiers, key/value/entry membership, exact content, `valueFor`, `entryFor`, `onlyValueFor`, `singleKey`, `singleValue`, and the `size...` family | map, selected entry, key, or value |

Condition providers deliberately share domain vocabulary such as `empty`,
`nonEmpty`, and `size`. Qualify the provider class when wildcard imports make a
call ambiguous.

## Collection and Map examples

Collection state and membership conditions preserve the concrete collection:

```java
List<Payment> payments = await(paymentRepository::findAll).until(nonEmpty.because(
        "Settlement requires at least one eligible payment"));

Payment onlyPayment = await(paymentRepository::findAll).until(single);

List<Payment> exact = await(paymentRepository::findAll).until(
        containsExactly(firstPayment, secondPayment));

Set<Payment> accepted = await(paymentRepository::findAccepted).until(
        contains(firstPayment, secondPayment));
```

Ordered exactness is available only when the source return type is a Java 21
`SequencedCollection`. General collections provide membership and any-order
exactness through conditions such as `containsExactlyInAnyOrder(...)`.

Map conditions likewise return the concrete map:

```java
Map<String, Payment> populated = await(paymentRepository::index).until(MapCondition.nonEmpty);

Map.Entry<String, Payment> onlyEntry = await(paymentRepository::index).until(singleEntry);

Map<String, Payment> indexed = await(paymentRepository::index).until(
        containsEntry(paymentId, expectedPayment));

Map<String, Payment> completeIndex = await(paymentRepository::index).until(
        containsExactlyEntriesOf(expectedIndex).because(
                "Reconciliation requires the complete payment index"));
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
