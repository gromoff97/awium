# Usage

[README](../README.md) · [Conditions](conditions.md) · [Diagnostics](diagnostics.md)

## The chain

```java
Payment paid = await(paymentRepository::load)
        .every(ofMillis(100)).upTo(ofSeconds(5))
        .until(matches(Payment::paid));
```

`until` accepts one condition and starts polling until it succeeds.
Each attempt reads the source once; returning the result needs no extra read.

Configurations and conditions are immutable and reusable. Each terminal call
starts fresh timing and built-in execution state.

Execution stays on the calling platform or virtual thread, with its `ThreadLocal`
values. Awium creates no worker. Callers manage synchronization of shared mutable
values.

## Sources

Pass a lambda or method reference; direct values are not accepted. Sources may
throw checked exceptions. Both wait modes accept the same source types and timing settings.

Awium keeps source values by reference, without copying or freezing them. Return
copies from the source when you need independent snapshots.

A stored JDK `Callable` selects the general value overload. For container selection:

```java
Callable<Optional<Payment>> source = paymentRepository::find;
Payment found = await(source::call).until(present);
```

To pass a stored source directly, use one of `Await`'s source types:
`OptionalSource<VALUE>`, `CollectionSource<VALUES>`, or `MapSource<ENTRIES>`.
All extend `Callable`.

### Inference limits

| Source | Remedy |
| --- | --- |
| Ambiguous reference, e.g. `StringBuilder::new` or `Optional::empty` | Use a lambda: `await(() -> new StringBuilder())`. |
| Only returns null | Specify a category: `OptionalSource<Payment> source = () -> null`. |
| Wildcard method reference selects the general overload | Use `await(() -> repository.findAllView())` or a typed view. |

Views declare the complete container type and selected bound without copying data:

```java
var source = new CollectionViewSource<Payment, List<? extends Payment>>(
        paymentRepository::findAllView);
Payment payment = await(source).until(singleElement);

var index = new MapViewSource<String, Payment, Map<? extends String, ? extends Payment>>(
        paymentRepository::indexView);
Entry<? extends String, ? extends Payment> entry = await(index).until(singleEntry);
```

## Timing

| Setting | Default | Duration |
| --- | --- | --- |
| `every(interval)` | 100 ms | Positive |
| `upTo(timeout)` | 10 s | Positive |
| `persisting(duration)` | Zero: disabled | Non-negative |

Settings accept `Duration` in any order; the last value wins. Awium validates durations immediately.

- Unless interrupted, the first attempt starts immediately. The timeout cannot
  cancel it. Later attempts must start and finish before the acquisition deadline;
  late results remain in diagnostics but cannot satisfy the wait.
- Intervals may exceed the timeout; parking is capped at the remaining acquisition time.
- After success, `persisting` keeps checking for its duration, possibly beyond `upTo`.
  Any persistence mismatch fails immediately.

`usingTime(clock, parker)` replaces time for one configuration. For virtual time:

```java
var nanos = new AtomicLong();
Payment payment = await(paymentRepository::find)
        .usingTime(nanos::get, nanos::addAndGet)
        .until(present);
```

The clock returns monotonic nanoseconds; the parker accepts nanoseconds and may
return early. Both must share a time base. Defaults are `System.nanoTime` and
`LockSupport.parkNanos`.
