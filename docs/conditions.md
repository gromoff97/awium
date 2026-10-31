# Conditions

[README](../README.md) · [Usage](usage.md) · [Diagnostics](diagnostics.md)

Checks return the exact observed object unless a selection or custom result is
specified below. Constants such as `present`, `singleElement`, and `nonEmpty`
need no parentheses. Qualify colliding names, e.g. `CollectionConditions.nonEmpty`
and `MapConditions.nonEmpty`; importing both makes an unqualified name ambiguous.

## Conditions

| Condition | Check | Result |
| --- | --- | --- |
| `isNull`, `isNotNull` | Nullness | Observation, including null |
| `equalTo(x)`, `notEqualTo(x)` | `Objects.deepEquals`, including array contents | Observation |
| `sameAs(x)`, `notSameAs(x)` | Reference identity | Observation |
| `in(values)`, `notIn(values)` | Membership in an iterable | Observation |
| `greaterThan(x)`, `atLeast(x)`, `lessThan(x)`, `atMost(x)` | Comparable bound | Observation |
| `between(min, max)`, `strictlyBetween(min, max)` | Inclusive or exclusive range | Observation |
| `instanceOf(Type.class)`, `exactlyInstanceOf(Type.class)` | Compatible or exact runtime class | Observation typed as `Type` |
| `matches(predicate)`, `asserted(assertion)` | [Callback](#callbacks) | Observation |
| `condition(name, callback)`, `conditionPerWait(name, factory)` | [Custom check](#custom-conditions) | Chosen value |
| `captured(first, second, ...)` | [Ordered states](usage.md#ordered-states) | List of source observations |

Single-bound comparisons require the source's `Comparable<BOUND>` contract;
the bound need not be comparable. Range bounds must also compare with each other.
An empty `in` never matches; empty `notIn` matches any value, including null.
[Full signatures](../src/main/java/io/github/gromoff97/awium/Conditions.java).

## OptionalConditions

| Condition | Check | Result |
| --- | --- | --- |
| `present` | Optional contains a value | Contained value |
| `absent` | Optional is empty | Original optional |
| `hasValue(predicateOrCondition)` | Present value passes the check | Value or nested condition's result |

A null optional satisfies none of these. [Full signatures](../src/main/java/io/github/gromoff97/awium/OptionalConditions.java).

## StringConditions

All checks accept `CharSequence` and preserve its concrete type and identity.

| Conditions | Check |
| --- | --- |
| `empty`, `nonEmpty`, `blank`, `nonBlank` | Empty or whitespace-only text |
| `contentEquals(text)` | Equal characters across text implementations |
| `equalToIgnoringCase(text)`, `notEqualToIgnoringCase(text)` | Case-insensitive equality |
| `contains(text)`, `doesNotContain(text)`, `containsIgnoringCase(text)` | One fragment |
| `containsAll(texts)`, `doesNotContainAny(texts)` | An iterable of fragments |
| `startsWith(text)`, `doesNotStartWith(text)`, `endsWith(text)`, `doesNotEndWith(text)` | Prefix or suffix |
| `matchesRegex(regex)`, `doesNotMatchRegex(regex)` | Regex text or a `Pattern` |
| `hasLength...` | [Length bounds](#size-and-length) |

Expected text accepts `CharSequence` and is frozen at construction. Observed text
is not converted to `String`; regex checks use the JDK regex engine. Blankness
uses `Character.isWhitespace` on Unicode code points. General `equalTo` follows
object equality: two `StringBuilder` objects may differ despite equal characters.
[Full signatures](../src/main/java/io/github/gromoff97/awium/StringConditions.java).

## CollectionConditions

The source must be a `Collection`; expected sets may be any `Iterable`.
Ordered operations require `SequencedCollection` (lists, deques, sequenced sets).

| Conditions | Check | Result |
| --- | --- | --- |
| `empty`, `nonEmpty`, `containsNull`, `doesNotContainNull`, `containsOnlyNulls`, `hasNoDuplicates` | Contents | Collection |
| `hasSize...` | [Size bounds](#size-and-length) | Collection |
| `all(check)`, `any(check)`, `none(check)` | Every, some, or no element matches | Collection |
| `contains(x)`, `doesNotContain(x)` | One element, even when `x` is an array or collection | Collection |
| `containsAll(xs)`, `containsAny(xs)`, `doesNotContainAll(xs)`, `doesNotContainAny(xs)` | Membership | Collection |
| `containsOnly(xs)`, `subsetOf(xs)` | Same distinct elements, or only elements from `xs` | Collection |
| `containsExactly(xs)`, `doesNotContainExactly(xs)` | Exact order and multiplicity | Collection |
| `containsExactlyInAnyOrder(xs)`, `doesNotContainExactlyInAnyOrder(xs)` | Exact multiplicity, any order | Collection |
| `startsWith(xs)`, `endsWith(xs)` | Ordered prefix or suffix | Collection |
| `containsSequence(xs)`, `doesNotContainSequence(xs)` | Consecutive ordered elements | Collection |
| `containsSubsequence(xs)`, `doesNotContainSubsequence(xs)` | Ordered elements, allowing gaps | Collection |
| `sorted(comparator)` | Ordered by the comparator | Collection |
| `singleElement` | Exactly one element | That element |
| `firstElement`, `lastElement`, `elementAt(index)` | An element at the specified ordered position | That element |

Selections also accept nested checks, e.g. `singleElement(Payment::paid)` or
`elementAt(0, instanceOf(Payment.class))`. `elementAt` requires a non-negative index
and traverses to it, even for an array-backed list. First/last selections traverse
forward/backward; single-value selection uses one iterator to distinguish zero,
one, or multiple elements. These reads are not atomic snapshots.
For ordered map views, use `sequencedKeySet()`, `sequencedValues()`, or
`sequencedEntrySet()`; plain `values()` exposes only `Collection`.
[Full signatures](../src/main/java/io/github/gromoff97/awium/CollectionConditions.java).

## MapConditions

| Conditions | Check | Result |
| --- | --- | --- |
| `empty`, `nonEmpty`, `hasSize...` | Contents or [size](#size-and-length) | Map |
| `hasEntry(key, value)`, `hasNoEntry(key, value)` | Expected pair present or absent | Map |
| `allEntries(check)`, `hasEntry(check)`, `hasNoEntry(check)` | Every, some, or no entry matches | Map |
| `allKeys(check)`, `anyKey(check)`, `noKey(check)` | Key quantifier | Map |
| `allValues(check)`, `anyValue(check)`, `noValue(check)` | Value quantifier | Map |
| `containsKey(key)`, `doesNotContainKey(key)`, `containsValue(value)`, `doesNotContainValue(value)` | One key or value | Map |
| `containsAllKeys(keys)`, `doesNotContainAnyKeys(keys)`, `containsOnlyKeys(keys)` | Key membership | Map |
| `containsAllValues(values)`, `doesNotContainAnyValues(values)` | Value membership | Map |
| `containsAllEntries(map)`, `containsAnyEntries(map)`, `doesNotContainAllEntries(map)`, `doesNotContainAnyEntries(map)` | Entry membership | Map |
| `containsExactlyEntries(map)`, `doesNotContainExactlyEntries(map)` | Same entries, regardless of order, or its opposite | Map |
| `singleEntry` | Exactly one entry | Original entry |
| `entryFor(key)` | A matching key exists | First matching original entry |
| `valueFor(key)` | A matching key exists | Its value |

`singleEntry`, `allEntries`, `hasEntry`, and `hasNoEntry` accept an entry predicate,
a `(key, value)` predicate, or a nested condition. `valueFor(key, check)` checks
only the selected value. For example:

```java
String status = await(() -> Map.of("payment", "READY"))
        .until(MapConditions.valueFor("payment", equalTo("READY")));
```

Expected keys and values use `Objects.deepEquals`, observed operand first,
regardless of the map's comparator or identity semantics. This includes array contents.
Nested checks keep their own comparison rules: `hasEntry(equalTo(entry))` follows
entry equality, which compares array keys/values by identity for standard entries.
Original entries may stay connected to the map, depending on its implementation.

Entry callbacks can call `setValue`, so their types must be compatible with the map.
For covariant views, prefer a key/value predicate with known parameter types.
A reusable typed-result callback inside `singleEntry` needs an input such as
`Map.Entry<? extends String, ? extends Payment>`; an inline callback infers it.
See [reason placement](usage.md#reasons-and-lambda-types) for wildcard entry checks.
[Full signatures](../src/main/java/io/github/gromoff97/awium/MapConditions.java).

## Size and length

Collections and maps use `hasSize(n)`, `hasSizeNotEqualTo(n)`,
`hasSizeGreaterThan(n)`, `hasSizeAtLeast(n)`, `hasSizeLessThan(n)`,
`hasSizeAtMost(n)`, and inclusive `hasSizeBetween(min, max)`.
Text uses the same suffixes with `hasLength`.

## Expectations and empty inputs

Bulk factories take one iterable or map and snapshot it shallowly at construction.
Encounter order, duplicates, and supported nulls are retained; reading failures
happen before polling. Null expected iterables/maps are rejected.
Single-value operands keep their original reference, even for containers and arrays.
Neither form deep-copies elements, keys, or values. Diagnostics label operands
`Expected` or `Unexpected`.

To put an array into an expected set as **one element**, use
`Collections.singletonList(array)`; `List.of(array)` expands a reference array.
For an object membership check, likewise wrap a collection or array that is one candidate.

Container and text checks do not match null observations. For non-null observations:

| Empty expectation | Outcome |
| --- | --- |
| `containsAll`, `doesNotContainAny`, and their map key/value/entry forms | Satisfied |
| `containsAny`, `doesNotContainAll`, including entry-map forms | Unsatisfied |
| `containsOnly`, `containsOnlyKeys`, exact-content checks | Satisfied only for an empty observation; negative exact checks have the opposite result |
| Ordered prefix, suffix, sequence, subsequence | Satisfied; negative sequence/subsequence checks are unsatisfied |

Empty text-fragment sets do not read characters; empty ordered patterns do not
read size or elements. Non-empty ordered patterns longer than the collection are
rejected by size without reading elements. A set containing `""` is not empty:
every non-null text contains that fragment.

## Nested checks

```java
Payment payment = await(paymentRepository::findAll)
        .until(singleElement(instanceOf(Payment.class)));
```

A selector first extracts, then checks at most once per attempt. A missing value,
wrong cardinality, or failed nested check retries the source. `firstElement`,
`lastElement`, `elementAt`, and `valueFor` never search for a different candidate
when their selected value fails. A nested typed condition returns its chosen
result; a predicate or preserving check returns the selected value.

Quantifiers return the complete original container and discard nested results.
They accept predicates or conditions, including `all(present)` and
`allValues(singleElement)`; narrowing checks do not narrow the container's element type.
`all`/`none` succeed on empty containers, `any` fails. Evaluation short-circuits
at the first decisive candidate. Nested callback state starts lazily and is retained
across candidates and retries, independently for each wait and captured stage.

Bare selectors derive their result type from the source at `until`; they cannot
be nested inside another selector: `singleElement(present)` and
`singleElement(singleElement)` are unsupported. A selector with a declared result,
such as `singleElement(instanceOf(Payment.class))`, can be nested again.

Conditions expose `description()` and immutable `references()`; compositions retain
lookup keys, expected operands, and nested check names. See [diagnostics](diagnostics.md#reading-diagnostics)
for failed extraction, iteration progress, and callback errors.

## Callbacks

- `matches(predicate)` uses a boolean result; `asserted(assertion)` succeeds on normal
  return and retries an `AssertionError`. A value returned by a method reference
  passed to `asserted` is discarded: returning `false` does **not** fail the assertion.
- Callbacks use `CheckedPredicate`, `CheckedBiPredicate`, `CheckedConsumer`, or
  `CheckedFunction`, all permitting checked throwables. An escaping throwable stops
  the wait; only the explicit `asserted` adapter retries `AssertionError`.
- For stored JDK callbacks, pass `predicate::test`, `consumer::accept`, or
  `function::apply`. Negate checked predicates with `CheckedPredicate.not(check)`
  or `CheckedBiPredicate.not(check)`.

## Custom conditions

```java
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

String receipt = await(paymentRepository::load).until(condition("payment has a receipt", actual ->
        actual.receipt() == null ? unsatisfied("receipt was absent") : satisfied(actual.receipt())));
```

The callback returns `ConditionResult<RESULT>`: `satisfied(value)` chooses the
result, including null; `unsatisfied()`, `unsatisfied(message)`, or
`unsatisfied(message, cause)` requests a retry. Use `satisfied(actual)` to return
the observation. There is no no-argument `satisfied()` or user-returnable uncontrolled state.
History and `captured` still keep source observations, not selected results.
Project in the source when history should instead observe the derived value.

The declared callback result determines the terminal type: a callback returning
`ConditionResult<CharSequence>` yields `CharSequence`, even with a `String` source.
Standalone callbacks and factories followed by `because` may need a typed parameter
such as `(Payment actual)`; see [type inference](usage.md#reasons-and-lambda-types).

`condition` reuses its callback. `conditionPerWait` takes a `Callable` creating
one callback per wait and captured position, lazily when first needed:

```java
var check = conditionPerWait("two paid observations", () -> {
    int[] paidObservations = {0};
    return (Payment actual) -> actual.paid() && ++paidObservations[0] >= 2
            ? satisfied(actual) : unsatisfied();
});
```

State created inside the factory is independent; state captured outside remains
shared. Both creation and evaluation may throw; either failure stops the wait.
