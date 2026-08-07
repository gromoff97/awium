# Assertility

Assertility is a Java 21 library for polling an AssertJ-style expectation and
returning the useful value from the same successful observation. It uses
Awaitility for polling and AssertJ for diagnostics.

The Maven coordinate is
`io.github.gromoff97:assertility:0.1.0-SNAPSHOT`. The project requires Java 21.
Awaitility and AssertJ are part of the public runtime API and are therefore
regular compile dependencies.

Examples below assume these imports:

```java
import static io.github.gromoff97.assertility.Assertility.*;
import static org.assertj.core.api.Assertions.assertThat;
```

## Await and retrieve

Use `awaitUntil(source)` for the default Awaitility configuration. The terminal
method states the expectation and determines the return type:

```java
Payment payment = awaitUntil(paymentDao::loadPayments)
        .as("payment %s", transactionId)
        .single(payment -> payment.transactionId().equals(transactionId));
```

`as` adds business context to a thrown `AwaitFailure`. It is available only
immediately after a source on the throwing path. `as(String)` treats its value
literally; `as(String, Object...)` uses `String.format` semantics. It is not
available on a `tryAwait` path.

Use a `ConditionFactory` when the wait needs a custom timeout, poll interval,
delay, stability window, ignored exceptions, alias, or other Awaitility policy:

```java
ConditionFactory factory = org.awaitility.Awaitility.await()
        .pollInterval(Duration.ofMillis(50))
        .atMost(Duration.ofSeconds(2))
        .during(Duration.ofMillis(100));

Payment payment = await(factory)
        .until(paymentDao::loadPayment)
        .as("payment %s reaches the final state", transactionId)
        .returns(Status.COMPLETED, Payment::status);
```

Assertility does not duplicate `ConditionFactory` configuration methods in its
own chain.

## Result mode

`tryAwaitUntil(source)` and `tryAwait(factory).until(source)` use the same
polling engine and terminal API without throwing an expected final wait
failure:

```java
AwaitResult<Payment> result = tryAwait(factory)
        .until(paymentDao::loadPayments)
        .single(Payment::transactionId, transactionId);

if (result.isSuccess()) {
    Payment payment = result.get();
} else {
    AwaitFailure failure = result.failure().orElseThrow();
}
```

`AwaitResult.get()` returns the value on success and rethrows the stored
`AwaitFailure` on failure. `failure()` is empty on success.

## Common terminals

Object terminals include `isNull`, `isNotNull`, `isEqualTo`, `isNotEqualTo`,
`returns`, `matches`, and `satisfies`. `returns(expected, extractor)` compares
the extracted value but returns the original object:

```java
Payment payment = awaitUntil(paymentDao::loadPayment)
        .returns(Status.COMPLETED, Payment::status);
```

Use `satisfies` for several assertions on one observation or for custom AssertJ
configuration:

```java
Payment payment = awaitUntil(paymentDao::loadPayment)
        .satisfies(actual -> {
            assertThat(actual.status()).isEqualTo(Status.COMPLETED);
            assertThat(actual.completedAt()).isNotNull();
        });
```

An `AssertionError` from `satisfies` is an unmet observation and is retried.
Predicate overloads of `matches`, Optional selectors, and collection selectors
also have a description form for clearer timeout diagnostics.

Specialized facades add `isTrue`/`isFalse`, comparable bounds, and String
emptiness/content checks.

## Optional and collections

Positive Optional terminals return the contained value. Collection state and
content terminals preserve the concrete source collection, while selectors
return selected elements:

```java
Payment optionalPayment = awaitUntil(paymentDao::findPayment)
        .contains(expectedPayment);

Payment singlePayment = awaitUntil(paymentDao::loadPayments)
        .single(Payment::transactionId, transactionId);
Payment anyCompleted = awaitUntil(paymentDao::loadPayments)
        .any(payment -> payment.status() == Status.COMPLETED);
List<Payment> twoCompleted = awaitUntil(paymentDao::loadPayments)
        .exactly(2, Payment::status, Status.COMPLETED);
```

`single` requires exactly one match. `any` chooses randomly from every match in
the final successful observation, so it never promises encounter order.
`exactly` requires a count of at least two and returns an unmodifiable
`List<E>` snapshot of all matches. `all` is non-vacuous and returns the source
collection; `none` allows an empty collection and also returns the source.

Extractor-based collection overloads use the order `(extractor, expected)`.
That order avoids ambiguity with `(String description, Predicate)` when the
expected property is a String.

Ordered content methods such as `containsExactly` are exposed only for a source
declared as a Java 21 `SequencedCollection`. General collections still expose
unordered content methods such as `containsExactlyInAnyOrder`.

## Map, Future, and executable sources

```java
Map<String, Payment> indexed = awaitUntil(paymentDao::indexPayments)
        .containsEntry(transactionId, expectedPayment);

CompletableFuture<Payment> completed = awaitUntil(paymentDao::requestPayment)
        .isDone();
Payment response = completed.join();

awaitUntil(paymentDao::refresh).doesNotThrowAnyException();
```

Map terminals preserve the concrete map. Map keys deliberately follow the Map
`equals`/`hashCode` contract; entry values use Assertility recursive equality.
`isDone` only polls `Future.isDone()` and returns the same Future—it never calls
`get()` during polling. An executable retries ordinary exceptions and
`AssertionError` until one invocation succeeds; its throwing result is `void`
and its result-mode type is `AwaitResult<Void>`.

## Return values

| Terminal family | Throwing path | Result path |
| --- | --- | --- |
| Object/common assertion on `T` | exact `T` | `AwaitResult<T>` |
| `isNull()` on `T` | `T`, whose runtime value is `null` | `AwaitResult<T>` |
| Boolean/String/comparable assertion | exact source | `AwaitResult<source type>` |
| Collection state/content/`all`/`none` on `C` | exact `C` | `AwaitResult<C>` |
| Collection `single` or `any` | selected `E` | `AwaitResult<E>` |
| Collection `exactly` | selected `List<E>` | `AwaitResult<List<E>>` |
| Optional `isEmpty` | exact `Optional<T>` | `AwaitResult<Optional<T>>` |
| Positive Optional terminal | contained `T` | `AwaitResult<T>` |
| Map assertion on `M` | exact `M` | `AwaitResult<M>` |
| Future `isDone` on `F` | exact `F` | `AwaitResult<F>` |
| Executable success | `void` | `AwaitResult<Void>` |

The source is invoked once per poll. A successful value is derived from that
same observation; Assertility does not make an extra source call to retrieve
it. With Awaitility `during`, the returned value comes from the final successful
observation after the stability window.

## Equality and failures

Built-in value equality uses AssertJ recursive comparison with strict type
checking. This applies to object equality and `returns`, Optional `contains`,
collection elements and extractor-based selectors/quantifiers, and map entry
values. Map keys are the intentional exception described above. Use
`satisfies` when comparison needs ignored fields, custom comparators, non-strict
types, or another assertion engine.

An unmet terminal, timeout, stability/minimum-time failure, exhausted ignored
exception, or factory fail-fast outcome becomes `AwaitFailure`. Throwing paths
throw it; result paths store it. Its cause preserves the original Awaitility
failure and the lower assertion diagnostic, while `as` supplies the outer
business context.

Invalid arguments fail before polling. Unexpected unchecked failures from a
value source, predicate, extractor, or callback propagate immediately. An
unexpected checked exception is wrapped in `AwaitExecutionException` unless
the supplied factory explicitly ignores it. Interruption restores the thread
interrupt flag and propagates immediately; fatal JVM errors are never retried.

## Type inference

`var` works for every non-void throwing result and every `AwaitResult`. It
cannot resolve an ambiguous source expression: overload selection happens
before the terminal result is inferred. Give fundamentally ambiguous lambdas a
typed source when necessary:

```java
AwaitSources.OptionalSource<String> empty = Optional::empty;
AwaitSources.StringSource nullable = () -> null;
AwaitSources.Executable failing = () -> {
    throw new IOException("not ready");
};
```

## Deliberate boundary

Assertility is stateless across polling iterations. It cannot accumulate
records from a consumptive event source that returns only newly received
batches. Such a wait needs an explicit stateful design for ownership,
deduplication, ordering, replay, memory bounds, diagnostics, and return value;
this initial API does not mutate external state to simulate it.

Assertility is licensed under the [Apache License 2.0](LICENSE).
