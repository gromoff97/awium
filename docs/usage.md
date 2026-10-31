# Usage

[README](../README.md) · [Conditions](conditions.md) · [Diagnostics](diagnostics.md)

Examples use static imports from `Await`, the relevant condition catalog, and `java.time.Duration`.

## The chain

```java
Payment paid = await(paymentRepository::load)
        .every(ofMillis(100)).upTo(ofSeconds(5))
        .until(matches(Payment::paid));
```

`until` accepts one [condition](conditions.md) and starts polling until it succeeds.
Each attempt reads the source once; returning the result needs no extra read.
For structured outcomes, use [`tryAwait`](diagnostics.md#results).

Configurations and conditions are immutable and reusable. Each terminal starts
fresh timing and execution state; [custom callback state](conditions.md#custom-conditions)
has its own sharing rules.

Execution stays on the calling platform or virtual thread, with its `ThreadLocal`
values. Awium creates no worker. Callers manage synchronization of shared mutable
values; see [interruption](diagnostics.md#failures-and-interruption).

## Sources

Pass a lambda or method reference, not a direct value. Sources may throw checked
exceptions. Both wait modes accept the same source types and timing settings.

A stored JDK `Callable` selects the general value overload. For container selection:

```java
Callable<Optional<Payment>> source = paymentRepository::find;
Payment found = await(source::call).until(present);
```

To pass a stored source directly, declare `Await.OptionalSource<VALUE>`,
`Await.CollectionSource<VALUES>`, or `Await.MapSource<ENTRIES>`; all extend `Callable`.

### Inference limits

| Source | Remedy |
| --- | --- |
| Ambiguous reference, e.g. `StringBuilder::new` or `Optional::empty` | Use a lambda: `await(() -> new StringBuilder())`. |
| Only returns null | Specify a category: `Await.OptionalSource<Payment> source = () -> null`. |
| Wildcard method reference selects the general overload | Use `await(() -> repository.findAllView())` or a typed view. |

Views declare the complete container type and selected bound without copying data:

```java
var source = new Await.CollectionViewSource<Payment, List<? extends Payment>>(
        paymentRepository::findAllView);
Payment payment = await(source).until(singleElement);

var index = new Await.MapViewSource<String, Payment, Map<? extends String, ? extends Payment>>(
        paymentRepository::indexView);
Map.Entry<? extends String, ? extends Payment> entry = await(index).until(singleEntry);
```

For captured covariant sources, views also preserve each observation's wildcard
type. [Nested selection constraints](conditions.md#nested-checks) still apply.

## Timing

| Setting | Default | Duration |
| --- | --- | --- |
| `every(interval)` | 100 ms | Positive |
| `upTo(timeout)` | 10 s | Positive |
| `persisting(duration)` | Zero: disabled | Non-negative |

Settings accept `Duration` in any order; the last value wins. Durations are validated immediately.

- Unless interrupted, the first attempt starts immediately. The timeout cannot
  cancel it. Later attempts must start and finish before the acquisition deadline;
  late results remain in diagnostics but cannot satisfy the wait.
- Intervals may exceed the timeout; parking is capped at the remaining acquisition time.
- After success, `persisting` keeps checking for its duration, possibly beyond `upTo`.
  Any persistence mismatch fails immediately. For [captured states](#ordered-states),
  only the final stage is rechecked; earlier captures stay unchanged.

`usingTime(clock, parker)` replaces time for one configuration. For virtual time:

```java
var nanos = new java.util.concurrent.atomic.AtomicLong();
Payment payment = await(paymentRepository::find)
        .usingTime(nanos::get, delay -> nanos.addAndGet(delay))
        .until(present);
```

The clock returns monotonic nanoseconds; the parker accepts nanoseconds and may
return early. Both must share a time base. Defaults are `System.nanoTime` and
`LockSupport.parkNanos`; [failure handling](diagnostics.md#failures-and-interruption)
differs for clocks and parkers.

## Reasons and lambda types

Every condition has `because(nonBlankReason)`, returning a copy with a reason
shown in diagnostics. Repeating it replaces the reason; an outer reason overrides
an inherited nested reason. Captured stages keep individual reasons.

```java
Payment payment = await(paymentRepository::find)
        .until(present.because("Payment must exist after checkout"));
```

Inline lambdas infer inputs from the source: `until(all(value -> value.paid()))`.
Storing a factory result in `var` or appending `because` may remove that context.
Use a method reference or typed parameter:

```java
var check = all(Payment::paid).because("Paid payments required");
var typed = all((Payment value) -> value.paid()).because("Paid payments required");
```

Keep a condition's inferred or specific type: base `Condition` loses the constraints
needed by `until`. See [map entry inference](conditions.md#map-entry-inference)
for cases needing the reason on the inner check.

## Ordered states

`captured` waits for checks in order, collecting the complete source observation
at each success:

```java
List<Payment> states = await(paymentRepository::load).until(captured(
        matches(Payment::created), matches(Payment::paid)));

List<List<Payment>> batches = await(paymentRepository::findAll).until(captured(
        CollectionConditions.hasSize(1), singleElement(Payment::paid)));
```

Each stage gets a fresh source observation, not the previous stage's extracted value.
Stages may return different types but must all accept the source. An optional source
produces `List<Optional<Payment>>`; one captured check still produces a one-element list.
See [result access](diagnostics.md#results) and [mutable observations](diagnostics.md#what-history-retains).

Capture sequences cannot be nested. `captured(conditions)` copies its argument
array and rejects empty/null arrays and null stages. Definitions such as
`captured(present, present).because("available twice")` work across optional types.
For stateful stages, use [callback factories](conditions.md#custom-conditions).

Mixed selectors, predicates, or comparisons followed by `because` may need a source
hint: `Conditions.<List<Payment>>captured(...)` (likewise `Optional<Payment>` or
`Map<String, Payment>`). Comparing the complete source as well can require two hints:
`Conditions.<List<Payment>, List<Payment>>captured(...)`. Incompatible checks remain
compile-time errors.
