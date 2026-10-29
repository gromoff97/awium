# Awium

Awium is a zero-dependency Java 21 library that waits for a condition in the
calling thread and returns the result from the same successful observation.

```java
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.OptionalConditions.present;

Payment payment = await(() -> paymentRepository.findById(order.paymentId())).until(present);
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
library itself. Fluent entry points and conditions are in `io.github.gromoff97.awium`.
Results, attempts, and failures are in `io.github.gromoff97.awium.result`;
checked callback interfaces are in `io.github.gromoff97.awium.function`.

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
import static io.github.gromoff97.awium.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.MapConditions.singleEntry;
import static io.github.gromoff97.awium.OptionalConditions.present;

Payment payment = await(paymentRepository::find).until(present);
Payment onlyPayment = await(paymentRepository::findAll).until(singleElement);
Map.Entry<String, Payment> entry = await(paymentRepository::index).until(singleEntry);
```

`present`, `singleElement`, `firstElement`, `lastElement`, and `singleEntry` are fields, so they do
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
`CheckedFunction`. Boolean checks use `CheckedPredicate` or `CheckedBiPredicate`.
These callbacks may declare `throws Throwable`, so existing methods declaring
`throws Exception` or `throws Throwable` can be passed directly, including to
`matches(...)`, collection predicates, and map predicates.
Callback failures stop polling immediately and retain the
original cause; only `AssertionError` inside `asserted(...)` means retry.

Use `matches(Payment::paid)` for a boolean check. Java discards a value returned
by a method reference adapted to `asserted(...)`, so returning `false` does not
make that assertion fail.

For callbacks already stored in JDK `Function`, `Consumer`, `Predicate`, or
`BiPredicate` variables, pass `function::apply`, `consumer::accept`,
`predicate::test`, or `entryPredicate::test`.
Negate a checked callback with `CheckedPredicate.not(check)` or `CheckedBiPredicate.not(check)`.
For example, `matches(CheckedPredicate.not(Payment::paid))`
keeps the method reference's input type and also accepts checks that throw checked exceptions.

### Ordered states

Pass `captured(...)` to `until(...)` to wait for compatible conditions in order.
Each successful stage captures the complete source observation; the result is a list
in stage order. The terminal accepts exactly one condition in both wait modes.
`tryAwait(...).until(...)` returns the same list inside `AwaitResult`:

```java
import static io.github.gromoff97.awium.Conditions.captured;
import static io.github.gromoff97.awium.Await.tryAwait;

List<Payment> payments = await(paymentRepository::load).until(captured(
        matches(Payment::created),
        matches(Payment::pending),
        equalTo(expectedPayment)));

var statuses = tryAwait(paymentRepository::status).until(captured(
        equalTo("created"),
        equalTo("paid")));
List<String> capturedStatuses = statuses.orThrow();
```

With `persisting(...)`, only the final stage is re-evaluated. Earlier captured
values remain unchanged. Each factory-backed stage invokes its factory separately
for every terminal call. `await(...).until(condition)` returns that condition's result;
`until(captured(condition))` returns a one-element list.
`captured(...)` describes a terminal sequence; its stages are individual checks,
not other capture sequences.
Checks and selections can be mixed freely when they accept the source type.
Every stage receives a fresh source observation. A selection does not pass its
extracted value into the following stage, and its result type does not change
what `captured` collects:

```java
List<List<Payment>> observations = await(paymentRepository::findAll).until(captured(
        CollectionConditions.hasSize(1),
        singleElement(equalTo(expectedPayment)),
        singleElement(Payment::paid),
        singleElement(instanceOf(Payment.class))));
```

A source returning `Payment` produces `List<Payment>`; a source returning
`Optional<Payment>` produces `List<Optional<Payment>>`. Conditions with different
declared results, such as `instanceOf(String.class)` and `instanceOf(Integer.class)`,
can be successive stages for an `Object` source. Only their accepted input types
must agree with the source. The original observations are retained by reference;
copy mutable containers in the source callback if independent snapshots are needed.

Capture definitions retain input and comparison constraints, independently of the
source's concrete result type. For example, `captured(present, present).because("available twice")`
can be stored and reused with different optional element types; each wait returns
its own correctly typed observations.
Chaining `because` onto combinations of nested comparisons and predicates can
still need an explicit source type on `captured`: for example,
`Conditions.<List<Payment>>captured(...)`,
`Conditions.<Optional<Payment>>captured(...)`, or
`Conditions.<Map<String, Payment>>captured(...)`. This also applies to `because`
on combinations of selectors requiring different container interfaces. The hint
specifies the source type once for the entire sequence; incompatible checks are
still rejected. If the same sequence also compares the complete source, the
factory can need both source and expected-value type hints, such as
`Conditions.<List<Payment>, List<Payment>>captured(...)`.

An array of compatible conditions can be passed to `captured(conditions)`.
The factory copies the array immediately, so later edits to the array cannot change
the sequence. An empty array, a null array, or a null stage is rejected at construction.
The resulting condition can be reused across waits, and `.because(...)` can explain
either the whole sequence or an individual stage.

## Timing

`every`, `upTo`, and `persisting` are optional, accept `Duration`, may appear in
any order, and may be repeated. The last supplied value wins:

```java
Payment payment = await(source::call).every(POLL_INTERVAL).upTo(TIMEOUT).persisting(STABILITY).until(present);
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
wait operation for one fluent configuration. It works with both `await` and
`tryAwait`, preserves source/result types, and leaves earlier configurations
independent:

```java
var nanos = new java.util.concurrent.atomic.AtomicLong();
Payment payment = await(source::call).usingTime(nanos::get, delay -> nanos.addAndGet(delay)).until(present);
```

The example advances virtual time instead of sleeping. The clock and wait
operation must use the same time base; the clock must be monotonic and return
normally. The wait operation receives a duration in nanoseconds and may return
early. Custom waiting failures use the normal waiting-failure handling; clock
failures propagate directly. Defaults remain `System.nanoTime` and
`LockSupport.parkNanos`.

## Inline predicates

Pass predicates directly inside `until` so Java can infer their input types from
the source:

```java
Payment payment = await(paymentRepository::load).until(matches(value -> value.finished()));
List<Payment> payments = await(paymentRepository::findAll).until(all(value -> value.paid()));
Payment found = await(paymentRepository::find).until(hasValue(value -> value.paid()));
```

A standalone declaration such as `var check = matches(value -> value.finished())`
has no source type to infer from. Use a method reference such as
`matches(Payment::finished)` when a condition needs to be stored and reused.

## Explaining a condition

Every condition supports `because(String)`. It returns an immutable copy with a
non-blank reason, shown as `Because` in failure diagnostics. Repeating `because`
replaces the reason on the new copy; the original condition stays unchanged.

```java
Payment found = await(paymentRepository::find).until(present.because("Payment must exist after checkout"));
Payment paid = await(paymentRepository::findAll).until(singleElement(Payment::paid).because("One paid payment is required"));
```

Calling `because` after a generic factory can prevent Java from inferring a
lambda parameter's type from `until`. For example,
`all(value -> value.paid()).because("Paid payments required")` needs a typed
lambda parameter or a method reference:

```java
List<Payment> payments = await(paymentRepository::findAll)
        .until(all((Payment value) -> value.paid()).because("Paid payments required"));
List<Payment> checked = await(paymentRepository::findAll)
        .until(all(Payment::paid).because("Paid payments required"));
```

Without `because`, the inline forms above still infer their parameter types.
Composed conditions inherit the nested condition's reason; an explicit outer
`because` replaces it. Each stage in an ordered sequence retains its own reason,
displayed alongside its stage number when the sequence fails.

For wildcard entry predicates, even a typed callback may leave the map's exact
type unknown until `until`. For example, `allEntries(positive).because(...)`
can infer `Map<String, Number>` when the source returns `Map<String, Integer>`.
Put the reason on the nested check to keep that inference available:

```java
CheckedPredicate<Map.Entry<String, ? extends Number>> positive =
        entry -> entry.getValue().intValue() > 0;
Map<String, Integer> counts = Map.of("ready", 1);
Map<String, Integer> checked = await(() -> counts).until(
        MapConditions.allEntries(matches(positive).because("Positive counts required")));
```

The same form works with `hasEntry` and `hasNoEntry` and preserves the nested reason.

## Selection and checking

Put a check inside the selection:

```java
Payment payment = await(paymentRepository::findAll).until(singleElement(Payment::paid));
Payment found = await(paymentRepository::find).until(hasValue(Payment::paid));
```

`singleElement` requires a collection containing exactly one element.
`singleElement(condition)` first requires exactly one element, then checks that element.
An empty collection, several elements, or a failed nested check cause a retry.
`singleEntry(condition)` follows the same rule for a map containing exactly one entry.
Selectors that accept nested conditions also accept a predicate directly:
`singleElement(check)`, `hasValue(check)`, and `valueFor(key, check)`. Use a full condition
for a type check, assertion adapter, or per-wait callback state.
`singleEntry(entry -> ...)` checks the sole entry directly and retains its key
and value types. The two-argument form `singleEntry((key, value) -> ...)` is also available.
Nested entry conditions can accept exact `Map.Entry<KEY, VALUE>` parameters,
whose key and value types match the source;
read-only wildcard entry checks are also supported.

Map checks with explicit expected keys, values, or entry maps compare each key
and value with `Objects.deepEquals`, with the observed operand first. Key selections
use the same key comparison. This includes array content and applies to maps with
comparators or identity keys as well.
Nested conditions retain their own comparison rules: `hasEntry(equalTo(expectedEntry))`
uses the entry's equality. Standard `Map.Entry` equality compares array keys and
values by identity, whereas `hasEntry(key, value)` compares their contents.
`valueFor` and `entryFor` take the first entry whose key matches.
`entryFor(key)` returns that entry. To check and return its value, use
`valueFor(key, check)`, which accepts a value predicate or a nested condition.
A failed check causes a retry; it does not search for a different entry in the same observation.
Entry selections return the original entry object. Whether it remains connected to
the map depends on that map's entry implementation.

`all`, `any`, `none`, and the map entry/key/value quantifiers also accept a nested
condition, for example `all(matches(Payment::paid))`.
Collection element and map key/value quantifiers also accept selectors:
`all(present)` checks a collection of optionals, and `allValues(singleElement)`
checks that each collection in a map has one element. They return the original
collection or map; the values extracted by the nested checks are discarded.
They preserve the complete observed container. A nested type check tests each
candidate; it does not change the container's declared element type. Evaluation
short-circuits on the first decisive candidate. Nested state is created lazily
and retained across candidates and retries, separately for each wait and stage.
Each candidate is evaluated as a nested condition. An interrupt flag left set
by its callback stops the wait before another candidate is checked.
Composite descriptions retain the nested check's name, including custom names
from `condition(...)` whose callback returns `unsatisfied()` without a message.
An unsatisfied quantifier reports `(checked N)`: the number of candidates evaluated
in iteration order. `all` and `none` stop at the first decisive candidate; a failed
`any` has exhausted its candidates. This count is not a polling count or necessarily
the container's size. It preserves traversal progress in history without repeating
the predicate or retaining intermediate elements.

Every condition family exposes `description()` and `reference()` through the
common immutable `Condition` model.

The source is read once per attempt. `hasValue(...)` checks an optional value.

Structural single-value selections use one iterator to distinguish zero, one,
and several values, without counting the whole collection. This also avoids
using a stale size when a concurrent collection changes before iteration.
`firstElement` and `lastElement` select through forward and reverse traversal; an empty
traversal causes a retry. All three selectors accept `SequencedCollection`, including
lists, deques, and sequenced sets. `elementAt(index)` traverses up to the requested position,
so its cost grows with the index even for an array-backed list. These selections
use the values seen in one traversal instead of a separate size check and read.
The observation remains a caller-owned object, not an atomic snapshot.

The nested condition can preserve the selected value (`matches`, `asserted`,
`condition`), compare it with an expected value (`equalTo`), or narrow its type
(`instanceOf`). Extraction failures report the selector; nested failures report
the nested condition and its expected value. Callback failures stop evaluation
and retain their original cause. If a callback sets the thread's interrupt flag,
the wait stops. A nested factory initializes lazily after successful extraction
and keeps its state across retries within that stage. The nested check runs at
most once per attempt.

Selections such as bare `present`, `singleElement`, and `valueFor(key)` obtain their
result type from the source at the terminal. They can be checked by collection element
and map key/value quantifiers, but cannot be nested inside another selection:
`singleElement(present)` and `singleElement(singleElement)` are not supported. Nested selections with
a declared result type, such as `singleElement(instanceOf(Payment.class))`, can be used
as conditions inside another selection or quantifier.

Type selection uses the same composition:

```java
Payment payment = await(repository::findAll).until(singleElement(instanceOf(Payment.class)));
Payment found = await(repository::find).until(hasValue(instanceOf(Payment.class)));
Payment firstPayment = await(repository::findAll).until(firstElement(instanceOf(Payment.class)));
Payment lastPayment = await(repository::findAll).until(lastElement(instanceOf(Payment.class)));
Payment indexedPayment = await(repository::findAll).until(elementAt(0, instanceOf(Payment.class)));
Payment paid = await(paymentRepository::findAll).until(firstElement(Payment::paid));
```

`firstElement`, `lastElement`, and `elementAt` accept nested conditions. Checks such as
`firstElement(Payment::paid)` and comparisons such as `lastElement(equalTo(expectedPayment))`
retain the source's element type. Conditions such as `instanceOf` return their
declared result type, including when the source contains `Object` values.
Each selector first extracts its value, then applies the nested check:
`firstElement` checks the first element, `lastElement` the last, and `elementAt` the
indexed element. A mismatch causes a retry; it never makes the selector search
for another element in the same observation.

To retain the source observations at successive selection stages, use `captured(...)`:

```java
List<List<Payment>> observations = await(paymentRepository::findAll).until(captured(
        singleElement(Payment::created),
        singleElement(Payment::paid)));
```

## Custom conditions

Use `condition(...)` for a named check with an explicit satisfaction result:

```java
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.Conditions.condition;

Payment payment = await(paymentRepository::load).until(condition(
        "payment has a receipt",
        actual -> actual.receipt() == null
                ? unsatisfied("receipt was absent")
                : satisfied()));
```

Custom conditions preserve the observed object. Use `satisfied()` to signal
success, `unsatisfied()` when no extra mismatch text is needed, or
`unsatisfied(message, cause)` to attach
diagnostics. A condition returns only `Satisfied` or `Unsatisfied`; any thrown
`Throwable` becomes an uncontrolled attempt and stops the wait.
`ConditionResult` has no type parameters or value payload. The callback input
constrains the observed value, and the terminal returns that original value.
The same satisfaction or mismatch status is available in each attempt record;
`AttemptStatus` additionally permits an `Uncontrolled` failure.

`condition(description, callback)` reuses the supplied callback.
`conditionPerWait(description, factory)` accepts a JDK `Callable` supplying a
`CheckedFunction`. Create mutable state inside the factory to isolate waits and stages:

```java
import static io.github.gromoff97.awium.Conditions.conditionPerWait;

await(paymentRepository::load).until(conditionPerWait("two paid observations", () -> {
    int[] paidObservations = {0};
    return (Payment actual) -> actual.paid() && ++paidObservations[0] >= 2
            ? satisfied()
            : unsatisfied();
}));
```

The factory runs lazily, at most once per use of the condition in a wait;
ordered stages invoke it separately. A shared callback or state captured outside
the factory remains shared. Both creation and evaluation may throw checked exceptions;
either failure stops the wait with its original cause. To observe a derived value, project it in
the source, for example `await(() -> paymentRepository.load().receipt())`.

## Condition catalogues

Import only the catalogue used by a test. Shared names such as `empty`,
`nonEmpty`, `contains`, and `hasSize` are intentionally domain-specific.

| Provider | Conditions | Successful result |
| --- | --- | --- |
| `Conditions` | `condition`, `conditionPerWait`, `asserted`, object equality and identity, type checks, `matches`, comparable ranges, and `captured` | observed or narrowed value, or captured list |
| `OptionalConditions` | `present`, `absent`, `hasValue` | contained or narrowed value; original optional for `absent` |
| `StringConditions` | empty/blank checks, content, prefix, suffix, regex, case-insensitive equality, and `hasLength...` | observed text, including `StringBuilder` and `CharBuffer` |
| `CollectionConditions` | `singleElement`, empty/null/duplicate checks, quantifiers, membership, exact content, sequences, `firstElement`, `lastElement`, `elementAt`, `sorted`, and `hasSize...` | observed collection or selected element |
| `MapConditions` | `singleEntry`, empty checks, entry/key/value quantifiers and membership, exact content, `valueFor`, `entryFor`, and `hasSize...` | observed map, selected entry, or value |

Collection and map sizes use `hasSize(n)`, `hasSizeNotEqualTo(n)`,
`hasSizeGreaterThan(n)`, `hasSizeAtLeast(n)`, `hasSizeLessThan(n)`,
`hasSizeAtMost(n)`, and `hasSizeBetween(min, max)`.
Text uses the same suffixes with `hasLength`, for example `hasLength(5)` and
`hasLengthAtLeast(1)`. These checks preserve the observed object.

All collection checks and selections require an observed `Collection`.
Expected sets of values can come from any `Iterable`:

```java
Collection<Payment> payments = List.of(expectedPayment);
Collection<Payment> observed = await(() -> payments).until(all(Payment::paid));
List<Payment> batch = await(paymentRepository::findAll).until(
        containsOnly(payments));
```

Ordered checks require `SequencedCollection`. These source requirements apply
equally to predicates, comparisons, and conditions with a declared result type.
For ordered views of a `SequencedMap`, use `sequencedKeySet()`, `sequencedValues()`,
or `sequencedEntrySet()`. They retain the ordering contract needed by `firstElement`,
`lastElement`, `elementAt(...)`, and `containsExactly(...)`; plain `values()` exposes only `Collection`.

Text conditions accept any `CharSequence` and preserve the source's concrete type
and instance, including inside container selections.
`empty`, `nonEmpty`, `blank`, `nonBlank`, `contentEquals`, and length checks do not
convert the observed text to a string. Blankness uses `Character.isWhitespace`
on Unicode code points, as `String.isBlank()` does, on the calling thread.
`matchesRegex`, `doesNotMatchRegex`, and `containsIgnoringCase` pass the original
`CharSequence` to the JDK regex engine.
Expected fragments, prefixes, suffixes, and regex text also accept `CharSequence`,
for example `StringConditions.startsWith(new StringBuilder("rea"))`.
`StringConditions.contains(fragment)` checks one fragment;
`StringConditions.containsAll(iterable)` checks a set of fragments.
The corresponding absence checks are `doesNotContain(fragment)` and
`doesNotContainAny(iterable)`, following the collection membership convention.
Their text is frozen when the condition is created, so later changes to an
expected `StringBuilder` or `CharBuffer` do not change the check.
An empty set of fragments satisfies both `containsAll(...)` and
`doesNotContainAny(...)` for non-null text without reading its characters or
rendering it as a string. A set containing `""` is different:
every string contains that fragment, so `doesNotContainAny(List.of(""))` is unsatisfied.
Use `StringConditions.contentEquals("ready")` to compare characters across
different text implementations. General `equalTo` follows object equality:
two distinct `StringBuilder` instances need not be equal despite identical text.

Single-bound checks use only the source's `Comparable<BOUND>` contract; the
bound itself need not implement `Comparable`. Range bounds must also compare
with each other. Both forms preserve the source's concrete type and instance.

Object membership uses `in(iterable)` and `notIn(iterable)`, for example
`in(List.of("created", "paid"))`. To treat a collection or array as one candidate,
wrap it in `Collections.singletonList(value)`. Empty candidate sets are allowed:
`in(...)` is unsatisfied, while `notIn(...)` is satisfied for any value, including null.

For collection membership, `contains(value)` and `doesNotContain(value)` compare
one element. A collection or array remains one element. To check a set of
expected elements, use `containsAll(iterable)` or `doesNotContainAny(iterable)`,
for example `containsAll(List.of("a", "b"))`. For null checks, use `containsNull`
and `doesNotContainNull`.

The other bulk membership and exact-content factories also take an iterable:
`containsAny(List.of("a", "b"))`, `doesNotContainAll(List.of("a", "b"))`,
`containsOnly(List.of("a", "b"))`, and the `containsExactly...` /
`doesNotContainExactly...` pairs. Each operation has one iterable overload.
For an array as one expected element, use `Collections.singletonList(array)`,
for example `containsOnly(Collections.singletonList(array))`.
`List.of(array)` expands a reference array into separate elements.

Collection membership accepts empty expected sets. For a non-null observed collection:

| Condition with an empty expected set | Result |
| --- | --- |
| `containsAll(...)`, `doesNotContainAny(...)` | Satisfied |
| `containsAny(...)`, `doesNotContainAll(...)` | Unsatisfied; polling continues |
| `containsExactly(...)`, `containsExactlyInAnyOrder(...)`, `containsOnly(...)` | Satisfied only when the observed collection is empty |

Expected sets are copied when the condition is created, including empty sets.
A null expected iterable is rejected; a null observed collection is unsatisfied.

Ordered expectations use the same iterable form: `startsWith(List.of("a", "b"))`,
`endsWith(...)`, `containsSequence(...)`, and `containsSubsequence(...)`, as do
`doesNotContainSequence(...)` and `doesNotContainSubsequence(...)`.
An empty pattern satisfies `startsWith(...)`, `endsWith(...)`, `containsSequence(...)`,
and `containsSubsequence(...)` for any non-null collection, including an empty one.
Their negative sequence checks are unsatisfied for an empty pattern.
These empty-pattern checks do not read the observed collection's size or elements.
When a non-empty pattern is longer than the collection, these checks decide from
the size without retrieving elements.
Use `Collections.singletonList(array)` when a reference array is one element of the pattern.

Maps follow the same rule: `containsKey(key)`, `doesNotContainKey(key)`,
`containsValue(value)` and `doesNotContainValue(value)` check one object.
`containsAllKeys(iterable)`, `doesNotContainAnyKeys(iterable)`,
`containsAllValues(iterable)`, `doesNotContainAnyValues(iterable)` and
`containsOnlyKeys(iterable)` accept iterables of expected keys or values.
Empty expected sets follow the same rules as collection membership:
`containsAllKeys(...)`, `containsAllValues(...)`, `doesNotContainAnyKeys(...)`,
and `doesNotContainAnyValues(...)` match any non-null map. Empty entry maps are
also accepted: `containsAllEntries(...)` and `doesNotContainAnyEntries(...)`
are satisfied, while `containsAnyEntries(...)` and `doesNotContainAllEntries(...)`
are unsatisfied. `containsExactlyEntries(...)` requires the same entries, regardless
of encounter order; an empty expected map requires the observed map to be empty.
`doesNotContainExactlyEntries(...)` is its opposite for a non-null observed map.

`hasEntry(key, value)` checks for an expected key-value pair.
`hasEntry(entry -> ...)` applies a predicate to each `Map.Entry`; use
`hasEntry(condition)` for a nested condition. Either check succeeds when at
least one entry matches. All forms return the original map, preserving its
concrete type. Use `singleEntry(condition)` when the map must contain exactly
one entry and that entry must pass the check and be returned.
`allEntries(entry -> ...)` checks every entry and returns the original map;
it also succeeds for an empty map. `allEntries`, `hasEntry`, `hasNoEntry`, and
`singleEntry` all accept an entry predicate, a `(key, value)` predicate, or a nested condition.

```java
import io.github.gromoff97.awium.Await.MapSource;
import static io.github.gromoff97.awium.Conditions.equalTo;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.MapConditions.hasEntry;
import static io.github.gromoff97.awium.MapConditions.hasNoEntry;
import static io.github.gromoff97.awium.MapConditions.allEntries;
import static io.github.gromoff97.awium.MapConditions.entryFor;
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static io.github.gromoff97.awium.MapConditions.singleEntry;

MapSource<Map<String, String>> statuses = () -> Map.of("payment", "READY");

Map<String, String> exact = await(statuses).until(
        hasEntry("payment", "READY").because("Payment processing must finish"));
Map<String, String> checked = await(statuses).until(
        hasEntry(entry -> entry.getKey().startsWith("pay") && entry.getValue().startsWith("READY")));
Map<String, String> nested = await(statuses).until(
        hasEntry(matches(entry -> entry.getValue().equals("READY"))));
Map<String, String> absentPair = await(statuses).until(
        hasNoEntry("payment", "FAILED"));
Map<String, String> absentMatch = await(statuses).until(
        hasNoEntry(entry -> entry.getValue().startsWith("FAIL")));
Map<String, String> all = await(statuses).until(
        allEntries(entry -> entry.getValue().equals("READY")));
Map.Entry<String, String> only = await(statuses).until(
        singleEntry(entry -> entry.getValue().equals("READY")));
Map.Entry<String, String> paymentEntry = await(statuses).until(
        entryFor("payment"));
String paymentStatus = await(statuses).until(
        valueFor("payment", equalTo("READY")));
```

As with other generic conditions, appending `because` to a predicate or nested condition may
require a typed lambda parameter or a method reference, for example
`hasEntry((Map.Entry<String, String> entry) -> entry.getValue().equals("READY")).because("A ready entry is required")`.
The two-argument predicate `hasEntry((key, value) -> ...)` is also available.
Entry callbacks may call `setValue`, so they require compatible entry types.
For a covariant map view, use the read-only key/value predicate with known
parameter types. With an exact map type, putting `because` on the nested check
inside `singleEntry(...)` also keeps the source available for type inference.

`hasNoEntry` accepts the same argument forms: a key-value pair, a predicate on
one entry, a predicate on its key and value, or a nested condition. It succeeds
when no entry matches, including for an empty map, and returns the original map.
Exceptions from a predicate or nested condition remain failures.

`containsAllEntries(map)` and `containsAnyEntries(map)` check all or any of the supplied entries;
`hasNoEntry(key, value)`, `doesNotContainAllEntries(map)`, and
`doesNotContainAnyEntries(map)` express the corresponding absence checks.

Diagnostics retain a single operand as one value and a bulk expectation as a
snapshot of its elements or entries. Positive checks label the operand
`Expected`; absence checks label it `Unexpected`.

Factories accepting iterables of expected elements or maps of expected entries
take a shallow snapshot when the condition is created.
Changing the original input afterwards does not change the condition or its
diagnostic reference. Encounter order, duplicates and supported nulls are retained.
Each iterable is traversed once during construction, on the calling thread;
a failure to read these inputs is thrown there, before polling starts.

Elements, keys, values, and callbacks remain caller-owned; they are not deeply
copied. Single-value operands such as `equalTo(value)`, `sameAs(value)`,
`contains(value)` and `doesNotContain(value)`
as well as the single-key and single-value map conditions
retain the supplied object, even if it is itself a container. This also applies
when they are nested inside a selection such as `hasValue(equalTo(value))`.
Observed values and successful results also retain their original identity.
Optional value conditions require a present `Optional`; use `absent` when
emptiness itself is the expectation.

Qualify a provider when a test needs colliding catalogues:

```java
await(paymentRepository::findAll).until(CollectionConditions.nonEmpty);
await(paymentRepository::index).until(MapConditions.nonEmpty);
```

## Sources

Import one entry point for all source categories:

```java
import static io.github.gromoff97.awium.Await.await;

Payment loaded = await(paymentRepository::load).until(equalTo(expectedPayment));
Payment found = await(paymentRepository::find).until(present);
Payment onlyPayment = await(paymentRepository::findAll).upTo(TIMEOUT).every(POLL_INTERVAL).until(singleElement);
Map.Entry<String, Payment> entry = await(paymentRepository::index).until(singleEntry);
```

The compiler chooses an `await` or `tryAwait` overload from the lambda or method reference.
Both entry points accept the same source types. Sources may throw checked exceptions
and are invoked only when `until` starts the wait. Direct observed values are not accepted.

A plain JDK `Callable` uses the general value overload. For container selections,
pass its `call` method reference or wrap the call in a lambda:

```java
import java.util.concurrent.Callable;

Callable<Optional<Payment>> payment = paymentRepository::find;
Callable<List<Payment>> payments = paymentRepository::findAll;
Callable<Map<String, Payment>> index = paymentRepository::index;

Payment found = await(payment::call).until(present);
Payment onlyPayment = await(payments::call).until(singleElement);
Map.Entry<String, Payment> onlyEntry = await(index::call).until(singleEntry);
```

Alternatively, declare `Await.OptionalSource<VALUE>`,
`Await.CollectionSource<VALUES>`, or `Await.MapSource<ENTRIES>` when storing a
source for later container selection. These functional interfaces extend
`Callable` and can be passed directly to `await`.

Overloaded constructor and generic method references can be ambiguous:
`await(StringBuilder::new)` and `await(Optional::empty)` are examples.
Use a lambda such as `await(() -> new StringBuilder())` or
`await(() -> Optional.empty())`. A source that only returns `null` also needs
an explicit category:

```java
import io.github.gromoff97.awium.Await.OptionalSource;

OptionalSource<Payment> source = () -> null;
await(source).until(isNull);
```

Wildcard-returning sources can use a lambda such as
`await(() -> paymentRepository.findAllView()).until(singleElement)`.
A direct method reference to that method may choose the general value overload.

For covariant collection and map results, typed views establish both the complete
container type and its selected bound. The view is an immutable source description;
the factory uses its delegate without copying the observed data:

```java
import io.github.gromoff97.awium.Await.CollectionViewSource;
import io.github.gromoff97.awium.Await.MapViewSource;

var payments = new CollectionViewSource<Payment, List<? extends Payment>>(paymentRepository::findAllView);
var index = new MapViewSource<String, Payment, Map<? extends String, ? extends Payment>>(paymentRepository::indexView);

Payment payment = await(payments).until(singleElement);
Map.Entry<? extends String, ? extends Payment> entry = await(index).until(singleEntry);
```

A view retains its source's wildcard type for condition compatibility. A nested
comparison such as `singleElement(equalTo(expectedPayment))` can therefore be
rejected for `List<? extends Payment>`, although bare `singleElement` returns a
`Payment`. Use `singleElement(expectedPayment::equals)` for that view; the same
condition works directly and inside `captured`. Map value comparisons have the
same restriction when their value type has an upper-bounded wildcard.

Use typed views when collecting mutable covariant values across observations,
too: they keep the observations' wildcard types independent despite
[Java's lambda capture limitation](https://bugs.openjdk.org/browse/JDK-8170887).

`until(...)` starts the wait in both modes. Success never invokes the source again merely to
obtain the return value. A retained stage may be reused sequentially; every
wait gets fresh timing and built-in evaluation state. Callback factories are
invoked again; state captured outside them or passed directly to `condition(...)`
remains owned by the caller.

## Diagnostic waits

Use `tryAwait` to handle a wait's outcome without catching its failure and to
inspect the retained attempts, their outcomes, timing, and sequence progress.

Start with `tryAwait(source)` and finish with `until(condition)` to return an
`AwaitResult<OBSERVED, RESULT>` for both success and failure. Timing settings
work just as they do with `await(source)`. History is grouped automatically, with no tail limit to configure.
`OBSERVED` is the complete source value and `RESULT` is the final result.
For example, selecting `present` from an optional gives
`AwaitResult<Optional<Payment>, Payment>`. Capturing those selections in order
gives `AwaitResult<Optional<Payment>, List<Optional<Payment>>>`:

```java
import static io.github.gromoff97.awium.Await.tryAwait;

var result = tryAwait(paymentRepository::find).upTo(TIMEOUT).until(present);
```

`AwaitResult.Satisfied` contains the terminal result. `AwaitResult.Failed`
contains an `AwaitFailure` with `reason()`, `message()`, the original `cause()`
and immutable `suppressed()` causes. It does not construct a wrapper exception.
When failure should stop the calling code, `Payment payment = result.orThrow()`
returns the typed value or throws the same failure as `await(...).until(...)`. It keeps the
existing attempt history and does not run the source or conditions again.
For a captured result, `orThrow()` returns the list of source observations.
A successful `null` remains `null`.

Java 21 record patterns retain the result type: `payment` below is a `Payment`,
without casts or repeated generic arguments.

```java
switch (result) {
    case AwaitResult.Satisfied(var attempts, var payment) ->
            System.out.println(payment.receipt());
    case AwaitResult.Failed(var attempts, var failure) -> {
        switch (failure.reason()) {
            case TIMEOUT -> reportTimeout(failure.message());
            default -> reportFailure(failure);
        }
    }
}
```

Both results expose `attempts()`, an immutable `List<AttemptGroup<OBSERVED>>` in
chronological order. A group represents consecutive attempts with the same check,
phase, sequence progress, status, mismatch message, and exception instance.
Every change starts a new group. If an earlier outcome returns later, it starts
another group in its chronological position.
History records complete polling attempts. Individual candidates inside `all`,
`any`, or `none` do not create extra history entries.
For example, `none(asserted(check))` succeeds when every candidate produces an
assertion mismatch. History records that aggregate success, not each nested mismatch.
Nested selection failures retain the completed selection steps in their mismatch.
For example, `collection size was 0` and `single element: collection size was 0`
identify an empty outer collection and an empty selected collection as separate transitions.
Built-in checks retain the facts they already measured: size and length mismatches
report the actual size, type checks report the actual class or null, and `sorted`
reports the first out-of-order pair's zero-based indices. `hasNoDuplicates` reports
the first duplicate's zero-based iteration index. Quantifier failures retain their
checked-candidate count. These facts stay unchanged when an observed object
is later modified; no extra predicate or comparator calls are made to collect them.

Each group exposes `firstNumber()` and `lastNumber()` (one-based, inclusive),
`count()`, and its `first()` and `last()` invocations as `HistoryEntry<OBSERVED>`.
A group with one invocation uses the same record at both ends. For example,
`result.attempts().getLast().last()` returns the final invocation;
`result.attempts().size()` counts diagnostic groups, not polling invocations.

```java
for (var group : result.attempts()) {
    System.out.println(group);
    var firstObservation = group.first().observed();
    var lastObservation = group.last().observed();
}
```

Printing a group shows its number range, completion offsets, condition and reason,
outcome, and boundary observations. Shared fields appear once. If both boundaries
refer to the same observed object, its current value is printed once; this does not
mean the object was unchanged during the group. Exception stack
traces remain available through the structured records. Values are formatted only
when the group is printed, so this is a view of the retained references, not a
snapshot of their earlier state. Observation labels explicitly say `current value`;
condition reference values are also rendered at printing time. The mismatch and
status still describe the original check. A failing `toString()` produces a diagnostic
placeholder instead of preventing the rest of the group from being displayed.
`System.out.println(result)` prints the outcome followed by all history groups;
`result.attempts().forEach(System.out::println)` prints the history alone.
Individual `first()` and `last()` records use the same guarded formatting, without
an attempt number: numbering belongs to the group.
Printing a status safely renders its cause's class and message; printing an
`AwaitFailure` returns its saved diagnostic message without inspecting the cause again.

Each record has an acquisition or persistence `phase()`,
the associated `condition()`, sequence `progress()`, `observed()`, and `result()`.
For a capture sequence, progress holds its immutable `sequence()` definition,
the attempt's one-based `stageNumber()`, and `capturedStages()` after the attempt.
The total is `sequence().stages().size()`. Progress is `null` for a single check.
An attempt records the condition it checked, even when success advances the
sequence. If waiting or source retrieval fails before evaluation, the condition
and stage identify the intended check and the captured count stays unchanged.

`HistoryEntry<OBSERVED>` keeps the source observation and a non-generic
`AttemptStatus`: `AttemptStatus.Satisfied`, `AttemptStatus.Unsatisfied`, or
`AttemptStatus.Uncontrolled`. The first two also implement `ConditionResult`,
which is the restricted return type of a custom check. A mismatch
retains its explanation and optional cause; an uncontrolled result retains
its cause and `operation()` (`WAITING`, `SOURCE`, or `CONDITION`).
For nested failures, its optional `location()` records enclosing selections and
zero-based iteration indices, shown as `Within` in diagnostics. A traversal failure
records how many candidates were checked before the iterator or container view failed.
These locations are collected without formatting values or inspecting the exception.
The extracted value belongs to the final result, not to the diagnostic status.
Use `hasObservation()` to distinguish a legitimate `null` returned by the source
from a failure before a value was obtained. All attempt results expose `cause()`.

A successful intermediate stage remains `Satisfied` in history while the
overall sequence continues. For `captured(equalTo("created"), equalTo("paid"))`:

| Attempt | Observation | Stage | Evaluation | Captured stages |
| --- | --- | --- | --- | --- |
| 1 | `"created"` | 1 | `Satisfied` | 1 |
| 2 | `"pending"` | 2 | `Unsatisfied` | 1 |
| 3 | `"paid"` | 2 | `Satisfied` | 2 |

The final result is `["created", "paid"]`; individual attempts retain their
observations and statuses. Sequence failure messages show the attempted
stage as `Stage: 2 of 3`. A timeout after an intermediate success also identifies
the next stage separately. Persistence rechecks only the final stage.

Each entry exposes `timing()`, a `HistoryEntry.Timing` with offsets from the start of the
terminal call. `startOffset()` marks the attempt's start before waiting;
`retrievalOffset()` marks the start of the source call, `observationOffset()`
its successful return, and `completionOffset()` the end of the attempt. A
retrieval or observation offset is `null` if that checkpoint was not reached.

History never discards earlier groups. For example, 999 consecutive `matches`
failures followed by success produce two groups: attempts 1–999 and attempt 1000.
Only the first and last observations of the repeated failures are retained.
The predicate still runs 1000 times; grouping does not change polling or the
observations returned by `captured`.

Grouping describes repeated diagnostics, not equality of source values. Actual
objects may change between the boundaries without creating a new group. Awium
does not call their `equals`, `hashCode`, or `toString` to group them. It also does
not inspect exception messages or stack traces: every new exception instance
attached to an attempt's outcome starts a separate group, even if its message
matches an earlier exception.
Repeated invocations with the same exception instance can share a group.

Storage grows with diagnostic transitions and distinct errors, rather than with
every repeated outcome. If every attempt has a different mismatch or a new error,
each is retained. Grouping therefore has no fixed memory bound. Failure messages
still identify the absolute attempt number.
Captured stage lists and the attempt list are immutable. Observed objects retain
their original references; Awium does not copy or freeze them. If a source returns
the same mutable object repeatedly, changing it is visible through earlier
attempts too. Their recorded satisfaction statuses remain those of the original
checks. This also applies to `matches(predicate)`: history groups its outcomes, but does not capture the fields read by the predicate.

## Threading and interruption

Polling, source retrieval, and condition evaluation run on the exact platform
or virtual thread that calls `until(...)`. Awium creates no worker, executor,
scheduler, or virtual thread, so caller `ThreadLocal` values remain visible.

Definitions and configurations are immutable, and each terminal call owns its
execution state. Callbacks and user-owned mutable values remain responsible
for any synchronization they need.

An interrupt flag set before or during the wait stops polling. Awium preserves
the flag when reading the clock and formatting diagnostics. A cause already
returned by the condition is retained, including an assertion mismatch;
a service interruption cause is created only when no cause is available.
`await(...).until(...)` throws `AwaitExecutionException` with
`failure().reason() == INTERRUPTED`; `tryAwait(...).until(...)` returns that reason in
`AwaitResult.Failed`. A callback that throws `InterruptedException` after
clearing the flag is treated like any other thrown failure; Awium does not
restore the flag based on the exception type. Because callbacks run in the
caller, Awium cannot preempt a source or condition that blocks indefinitely.

## Failures

`await(...).until(...)` throws one of two final types. Both expose `failure()` with the
same structured data returned by `tryAwait(...).until(...)`:

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

Null sources and conditions, invalid durations, and invalid eager factory
arguments fail before polling. Every thrown
`Throwable` from a source, condition, evaluator factory, or wait operation
stops polling and is retained as the cause. Only the explicit `asserted(...)`
adapter treats an `AssertionError` as an unsatisfied condition.

## Development

Run `./gradlew check` for behavior, fluent compilation, and packaged-module
checks; `./gradlew pitest` runs mutation testing.

`Condition` owns the immutable definition and diagnostic reference. The base type
has no type parameters; input and result constraints belong to its typed families.
Keeping a check in a variable declared as `Condition` loses those constraints,
so that variable cannot be passed directly to `until`.
`Condition.Check` describes one check; `Condition.Captured` holds ordered checks.
Its typed families let terminal overloads preserve or select the source's type.
Preserving checks and comparisons share one terminal overload through the sealed,
non-generic `PreservesObservation` marker; selectors and narrowing checks cannot
enter that overload.
Checks share `ApplicableCondition`, which describes accepted inputs and expected-value
compatibility. Selectors have one input type parameter; their complete container bounds
also constrain nested checks. Direct waits and captured stages validate that same input. `Conditions.captured(...)` creates an explicit sequence
of any source-compatible checks and captures their original observations. Its
`Condition.CapturedSource<CHECK>` carries the stage checks' input and comparison
constraints independently of their result types. Its factories retain those
constraints when combining checks or adding `because`; mixed input shapes can
also be inferred from the terminal call. Input-only checks share
`InputCondition<INPUT>`, allowing reusable capture definitions without widening
the observations returned by a particular source.
Each terminal call creates fresh `AttemptFactory` instances, which lazily initialize
one evaluator per check. A factory binds an observation to an immutable `Attempt`.
Each `Attempt.call()` runs that check and returns a separate `AttemptResult<RESULT>`
with the selected value or failure and diagnostic condition. The attempt retains no
previous result, timing, attempt number, or sequence progress. Its observation and
callback may themselves be mutable; neither is copied.
`SequenceExecution` selects the next attempt and accepts its result, retaining stage
progress and captured values without executing checks itself. The non-generic
`ConditionResult` supplies satisfaction and mismatch statuses; `AttemptStatus` also
permits an uncontrolled failure. Public `HistoryEntry<OBSERVED>` records retain the
observation, status, timing, and sequence progress, without exposing an executable callback.

`Await` supplies the source factories. `ValueAwait<OBSERVED>` and
`SelectionAwait<OBSERVED, ELEMENT>` retain immutable fluent configuration;
`MapAwait<OBSERVED, ENTRY, VALUE>` also supports map-value selection. The corresponding
`DiagnosticAwait`, `DiagnosticSelectionAwait`, and `DiagnosticMapAwait` classes
return diagnostic results. `AwaitSupport` shares execution and result handling.
`AttemptHistory` numbers invocations and groups consecutive diagnostic repetitions,
retaining their first and last records as immutable `AttemptGroup` values.
Ordinary waits use its counting-only mode without retaining or comparing records.
`WaitEngine` schedules polling and persistence. Each wait creates an internal
`AttemptExecution` that retrieves source values, calls the selected attempt,
and records a `HistoryEntry` with its timing and diagnostics. Selected values and stage
transitions remain in `SequenceExecution`. `FailureDiagnostics` formats the terminal failure
without replacing its category or cause. It shares guarded value and field rendering
with history groups through `DiagnosticText`; formatting runs outside polling.
A successful check can still finish too late.

`ConditionSupport` shares condition construction, checks, extraction, and
composition; catalogues supply nested conditions without creating execution
attempts. Failed extraction reports its own condition: optional presence,
the requested collection position, or the map lookup key. Map entry access failures
also retain the lookup key. After extraction succeeds, diagnostics use the
nested condition's expectation. Interruption during extraction keeps the selector
context and skips the nested check.

Catalog and fluent behavior tests use virtual time. Integration tests verify
caller-thread execution and external cancellation on both platform and virtual
threads, plus independent condition state during concurrent waits.

The module exports the three public API packages. Execution lives in
`io.github.gromoff97.awium.internal`, which the module does not export. These classes
are accessible on the classpath but are not supported API. Condition binding and
value-matching helpers remain package-private. Custom conditions use the factories
in `Conditions`. Artifact checks also verify that execution classes do not depend
on the fluent facade, condition catalogs, or diagnostic formatting, and that
catalogues do not depend directly on execution classes.

Awium is licensed under the [Apache License 2.0](LICENSE).
