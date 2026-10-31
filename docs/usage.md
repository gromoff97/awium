# Usage

[README](../README.md) · [Conditions](conditions.md) · [Diagnostics](diagnostics.md)

Examples use static imports from `Await`, the relevant condition catalog, and `java.time.Duration`.

## The chain

```java
Payment paid = await(paymentRepository::load)
        .every(ofMillis(100)).upTo(ofSeconds(5))
        .until(matches(Payment::paid));
```

`until` accepts one condition and starts execution. Each attempt reads the source
once; success returns without another source call. Conditions determine the result:

| Source | Condition | Result |
| --- | --- | --- |
| `Payment` | `equalTo(expected)` | The observed `Payment` |
| `Optional<Payment>` | `present` | The contained `Payment` |
| `List<Payment>` | `singleElement` | The only `Payment` |
| `Map<String, Payment>` | `singleEntry` | Its only `Map.Entry<String, Payment>` |
| Any compatible source | `captured(first, second)` | A list of source observations |

`tryAwait` accepts the same sources and timing configuration, but returns an
[`AwaitResult`](diagnostics.md#results). Configurations and conditions are immutable
and reusable; each terminal call starts fresh timing and built-in evaluation state.

Polling, source calls, and callbacks execute on the thread calling `until`, whether
platform or virtual. No worker is created; `ThreadLocal` values remain visible.
Shared callbacks and mutable values still need any synchronization their owner requires.
Awium cannot preempt a blocked callback. See [interruption](diagnostics.md#failures-and-interruption).

## Sources

Pass a lambda or method reference to `await` or `tryAwait`; the compiler chooses
the source overload. Sources may throw checked exceptions and run only at `until`.
Direct observed values are not accepted.

A stored JDK `Callable` selects the general value overload. To select from its
container result, pass `source::call`:

```java
Callable<Optional<Payment>> source = paymentRepository::find;
Payment found = await(source::call).until(present);
```

Alternatively, store an `Await.OptionalSource<VALUE>`,
`Await.CollectionSource<VALUES>`, or `Await.MapSource<ENTRIES>` and pass it directly.
These functional interfaces extend `Callable`.

### Inference limits

| Source expression | What to do |
| --- | --- |
| Overloaded constructor or generic reference, e.g. `StringBuilder::new`, `Optional::empty` | Use a lambda: `await(() -> new StringBuilder())`. |
| A source returning only `null` | Declare its category, e.g. `Await.OptionalSource<Payment> source = () -> null`. |
| A wildcard-returning method reference selecting the general overload | Try a lambda, e.g. `await(() -> repository.findAllView())`, or a typed view below. |

Typed views retain both a wildcard container type and its selected bound without
copying observations:

```java
var source = new Await.CollectionViewSource<Payment, List<? extends Payment>>(
        paymentRepository::findAllView);
Payment payment = await(source).until(singleElement);

var index = new Await.MapViewSource<String, Payment, Map<? extends String, ? extends Payment>>(
        paymentRepository::indexView);
Map.Entry<? extends String, ? extends Payment> entry = await(index).until(singleEntry);
```

Wildcard compatibility still applies: `singleElement(equalTo(expectedPayment))`
may be rejected for `List<? extends Payment>`. Use
`singleElement(expectedPayment::equals)` for that view, including inside `captured`.
Map value comparisons have the same restriction. Typed views also keep mutable
covariant observations independent of Java's lambda capture inference.

## Timing

| Setting | Default | Accepted value |
| --- | --- | --- |
| `every(interval)` | 100 ms | Positive `Duration` |
| `upTo(timeout)` | 10 s | Positive `Duration` |
| `persisting(duration)` | Zero: disabled | Non-negative `Duration` |

Settings may appear in any order; the last value wins. Invalid durations fail immediately.

- Unless already interrupted, the first attempt starts immediately, regardless of
  the timeout. `upTo` does not cancel it. Later attempts must start **and finish**
  before the acquisition deadline; a late result is retained in diagnostics but cannot satisfy the wait.
- The interval may exceed the timeout: after an unsuccessful attempt, waiting is
  limited to the time remaining.
- After the first success, `persisting` keeps checking for the specified duration,
  possibly beyond `upTo`. Any mismatch during persistence fails immediately.

`usingTime(clock, parker)` replaces time for that configuration, in either wait mode:

```java
var nanos = new java.util.concurrent.atomic.AtomicLong();
Payment payment = await(paymentRepository::find)
        .usingTime(nanos::get, delay -> nanos.addAndGet(delay))
        .until(present);
```

The clock must return monotonic nanoseconds normally; the parker receives a
nanosecond duration and may return early. Both must use the same time base.
Defaults are `System.nanoTime` and `LockSupport.parkNanos`. Parker failures become
wait failures; clock failures propagate directly. Earlier configurations stay independent.

## Reasons and lambda types

Every condition supports `because(nonBlankReason)`. It returns a copy; repeating
it replaces that copy's reason. The reason appears as `Because` in diagnostics.

```java
Payment payment = await(paymentRepository::find)
        .until(present.because("Payment must exist after checkout"));
```

Inline predicates can infer their input from the source:
`until(all(value -> value.paid()))`. Storing that condition in `var`, or appending
`because` to its factory, can remove the inference context. Use a method reference
or a typed parameter:

```java
var check = all(Payment::paid).because("Paid payments required");
var typed = all((Payment value) -> value.paid()).because("Paid payments required");
```

Nested conditions pass their reason to the composition; an outer `because`
replaces it. Each captured stage retains its own reason. Keep a stored condition's
inferred or specific type: declaring it as base `Condition` loses the constraints
needed by `until`.

For wildcard entry predicates, an outer `because` may still infer the wrong map
type. Put the reason on the nested check so the source can constrain the map:

```java
CheckedPredicate<Map.Entry<String, ? extends Number>> positive =
        entry -> entry.getValue().intValue() > 0;
Map<String, Integer> counts = Map.of("ready", 1);
Map<String, Integer> checked = await(() -> counts).until(
        MapConditions.allEntries(matches(positive).because("Positive counts required")));
```

This also works with `hasEntry` and `hasNoEntry`.

## Ordered states

`captured` waits for checks in order and collects the **complete source observation**
at each success:

```java
List<Payment> states = await(paymentRepository::load).until(captured(
        matches(Payment::created),
        matches(Payment::paid)));

List<List<Payment>> batches = await(paymentRepository::findAll).until(captured(
        CollectionConditions.hasSize(1),
        singleElement(Payment::paid)));
```

Each stage receives a new source observation, not the previous stage's extracted
value. Checks may return different types as long as they accept the source.
An optional source therefore produces `List<Optional<Payment>>`; a single captured
check still produces a one-element list. `tryAwait` exposes that list via `orThrow()`.

`persisting` rechecks only the final stage; earlier captures remain unchanged.
Each [factory-backed stage](conditions.md#custom-conditions) gets its own callback
for each terminal call. Nested capture sequences are not supported.

`captured(conditions)` copies its argument array; empty arrays, null arrays, and
null stages are rejected. Definitions can be reused, including
`captured(present, present).because("available twice")` across different optional types.
Captured values retain their original references. Copy mutable observations in
the source if independent snapshots are needed; see [what history retains](diagnostics.md#what-history-retains).

For mixed selectors, comparisons, and predicates followed by `because`, Java may
need the source type explicitly: `Conditions.<List<Payment>>captured(...)`,
`Conditions.<Optional<Payment>>captured(...)`, or
`Conditions.<Map<String, Payment>>captured(...)`. A sequence also comparing the
whole source may require both source and expected types:
`Conditions.<List<Payment>, List<Payment>>captured(...)`. These hints retain
compile-time rejection of incompatible checks.
