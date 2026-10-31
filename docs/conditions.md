# Conditions

[README](../README.md) · [Usage](usage.md) · [Diagnostics](diagnostics.md)

Checks return the exact observed object unless **→** indicates otherwise.
Constants such as `present` need no parentheses. Qualify colliding imports:
`CollectionConditions.nonEmpty` and `MapConditions.nonEmpty`.

## Conditions

| Condition | Meaning |
| --- | --- |
| `isNull`, `isNotNull` | Nullness |
| `equalTo(x)`, `notEqualTo(x)` | `Objects.deepEquals`: object equality or array contents |
| `sameAs(x)`, `notSameAs(x)` | Reference identity |
| `in(xs)`, `notIn(xs)` | Membership in an iterable |
| `greaterThan(x)`, `atLeast(x)`, `lessThan(x)`, `atMost(x)` | Comparable bound |
| `between(min, max)`, `strictlyBetween(min, max)` | Inclusive or exclusive range |
| `instanceOf(Type.class)`, `exactlyInstanceOf(Type.class)` | Compatible or exact runtime class → `Type` |
| `matches(predicate)`, `asserted(assertion)` | [Callbacks](#callbacks) |
| `condition(name, callback)`, `conditionPerWait(name, factory)` | → [Custom result](#custom-conditions) |
| `captured(first, second, ...)` | → [Ordered states](usage.md#ordered-states) |

Single bounds need not be comparable: the source must implement `Comparable<BOUND>`.
Range bounds must also compare with each other. Empty `in` never matches;
empty `notIn` matches anything, including null.
[Signatures](../src/main/java/io/github/gromoff97/awium/Conditions.java).

## OptionalConditions

| Condition | Meaning |
| --- | --- |
| `present` | Present → contained value |
| `absent` | Empty optional |
| `hasValue(check)` | Present → [checked value or chosen result](#nested-checks) |

[Signatures](../src/main/java/io/github/gromoff97/awium/OptionalConditions.java).

## StringConditions

Accepts any `CharSequence`, checked without conversion to `String`.

| Condition | Meaning |
| --- | --- |
| `empty`, `nonEmpty`, `blank`, `nonBlank` | Empty or whitespace-only text |
| `contentEquals(text)` | Equal characters across text implementations |
| `equalToIgnoringCase(text)`, `notEqualToIgnoringCase(text)` | Case-insensitive equality |
| `contains(text)`, `doesNotContain(text)`, `containsIgnoringCase(text)` | One fragment |
| `containsAll(xs)`, `doesNotContainAny(xs)` | An iterable of fragments |
| `startsWith(text)`, `doesNotStartWith(text)`, `endsWith(text)`, `doesNotEndWith(text)` | Prefix or suffix |
| `matchesRegex(regex)`, `doesNotMatchRegex(regex)` | JDK regex, supplied as text or `Pattern` |
| `hasLength...` | [Length bounds](#size-and-length) |

Text operands accept `CharSequence`. Blankness uses `Character.isWhitespace` on
Unicode code points. Unlike `contentEquals`, general `equalTo` may reject distinct
`StringBuilder` objects with identical characters.
[Signatures](../src/main/java/io/github/gromoff97/awium/StringConditions.java).

## CollectionConditions

Requires `Collection`; ordered operations require `SequencedCollection` (lists,
deques, sequenced sets). `xs` below is an iterable of [expected elements](#expectations-and-empty-inputs).

| Condition | Meaning |
| --- | --- |
| `empty`, `nonEmpty`, `containsNull`, `doesNotContainNull`, `containsOnlyNulls`, `hasNoDuplicates` | Contents |
| `hasSize...` | [Size bounds](#size-and-length) |
| `all(check)`, `any(check)`, `none(check)` | Every, some, or no element matches |
| `contains(x)`, `doesNotContain(x)` | One element, including an array or collection |
| `containsAll(xs)`, `containsAny(xs)`, `doesNotContainAll(xs)`, `doesNotContainAny(xs)` | Membership |
| `containsOnly(xs)`, `subsetOf(xs)` | Same distinct elements, or only elements from `xs` |
| `containsExactly(xs)`, `doesNotContainExactly(xs)` | Exact order and multiplicity |
| `containsExactlyInAnyOrder(xs)`, `doesNotContainExactlyInAnyOrder(xs)` | Exact multiplicity, any order |
| `startsWith(xs)`, `endsWith(xs)` | Ordered prefix or suffix |
| `containsSequence(xs)`, `doesNotContainSequence(xs)` | Consecutive ordered elements |
| `containsSubsequence(xs)`, `doesNotContainSubsequence(xs)` | Ordered elements, allowing gaps |
| `sorted(comparator)` | Comparator order |
| `singleElement` | Exactly one element → element |
| `firstElement`, `lastElement`, `elementAt(index)` | Ordered position → element |

Selections accept [nested checks](#nested-checks), e.g. `elementAt(0, check)`.
`elementAt` requires a non-negative index and traverses to it even in array-backed
lists. First/last traverse forward/backward; single selection uses one iterator to
distinguish zero, one, or multiple elements. These reads are not atomic snapshots.
For ordered map views, use `sequencedKeySet()`, `sequencedValues()`, or
`sequencedEntrySet()`; plain `values()` exposes only `Collection`.
[Signatures](../src/main/java/io/github/gromoff97/awium/CollectionConditions.java).

## MapConditions

| Condition | Meaning |
| --- | --- |
| `empty`, `nonEmpty`, `hasSize...` | Contents or [size](#size-and-length) |
| `hasEntry(key, value)`, `hasNoEntry(key, value)` | Expected pair present or absent |
| `allEntries(check)`, `hasEntry(check)`, `hasNoEntry(check)` | Every, some, or no entry matches |
| `allKeys(check)`, `anyKey(check)`, `noKey(check)` | Key quantifier |
| `allValues(check)`, `anyValue(check)`, `noValue(check)` | Value quantifier |
| `containsKey(key)`, `doesNotContainKey(key)`, `containsValue(value)`, `doesNotContainValue(value)` | One key or value |
| `containsAllKeys(xs)`, `doesNotContainAnyKeys(xs)`, `containsOnlyKeys(xs)` | Key membership |
| `containsAllValues(xs)`, `doesNotContainAnyValues(xs)` | Value membership |
| `containsAllEntries(map)`, `containsAnyEntries(map)`, `doesNotContainAllEntries(map)`, `doesNotContainAnyEntries(map)` | Entry membership |
| `containsExactlyEntries(map)`, `doesNotContainExactlyEntries(map)` | Same entries, regardless of order, or its opposite |
| `singleEntry` | Exactly one entry → original entry |
| `entryFor(key)` | First matching key → original entry |
| `valueFor(key)` | First matching key → value |

`singleEntry`, `allEntries`, `hasEntry`, and `hasNoEntry` accept an entry predicate,
a `(key, value)` predicate, or a nested condition. `valueFor(key, check)` tests the
selected value. Key/value sets accept iterables; entry sets accept maps.

Expected keys/values use `Objects.deepEquals`, observed operand first, regardless
of map comparators or identity semantics. Nested checks keep their own comparison:
`hasEntry(equalTo(entry))` follows entry equality (array keys/values compare by
identity in standard entries). Selected entries may remain connected to the map.
[Signatures](../src/main/java/io/github/gromoff97/awium/MapConditions.java).

### Map entry inference

Entry callbacks can call `setValue`, so they require compatible map types. For
covariant views, use a key/value predicate with known parameter types. A reusable
typed-result callback inside `singleEntry` needs an input such as
`Map.Entry<? extends String, ? extends Payment>`; inline callbacks infer it.

For wildcard entry predicates, even typed callbacks can lose the map type with
an outer `because`. Put the reason on the inner check:

```java
CheckedPredicate<Map.Entry<String, ? extends Number>> positive =
        entry -> entry.getValue().intValue() > 0;
Map<String, Integer> counts = Map.of("ready", 1);
Map<String, Integer> checked = await(() -> counts).until(
        MapConditions.allEntries(matches(positive).because("Positive counts required")));
```

The same applies to `hasEntry` and `hasNoEntry`. General
[`because` and inference rules](usage.md#reasons-and-lambda-types) apply elsewhere.

## Size and length

Collections/maps have `hasSize(n)`, `hasSizeNotEqualTo(n)`, `hasSizeGreaterThan(n)`,
`hasSizeAtLeast(n)`, `hasSizeLessThan(n)`, `hasSizeAtMost(n)`, and inclusive
`hasSizeBetween(min, max)`. Text uses the same suffixes with `hasLength`.

## Expectations and empty inputs

Bulk iterables/maps are traversed once and shallow-copied at construction,
preserving encounter order, duplicates, and supported nulls. Null inputs are
rejected; reading failures occur before polling. Elements, keys, and values are
not deep-copied. Single-value operands retain references, including containers.
`StringConditions` freeze text operands.

Use `Collections.singletonList(array)` for one array in an expected set;
`List.of(array)` expands reference arrays. Likewise, wrap a collection or array
when it is one candidate for `in`/`notIn`.

Optional, collection, map, and text conditions do not match null observations.
For non-null observations:

| Empty expectation | Outcome |
| --- | --- |
| `containsAll`, `doesNotContainAny`, including map key/value/entry forms | Satisfied |
| `containsAny`, `doesNotContainAll`, including entry forms | Unsatisfied |
| `containsOnly`, `containsOnlyKeys`, exact-content checks | Only empty observations match; negative exact checks have the opposite result |
| Ordered prefix/suffix/sequence/subsequence | Satisfied; negative sequence/subsequence checks are unsatisfied |

Empty fragment sets read no characters; empty ordered patterns read neither size
nor elements. Longer non-empty patterns are rejected by size without reading
elements. A set containing `""` differs from an empty set: all non-null text contains it.

## Nested checks

```java
Payment payment = await(paymentRepository::findAll)
        .until(singleElement(instanceOf(Payment.class)));
```

Selectors extract first and check at most once per attempt. Missing values, wrong
cardinality, or rejected selections are mismatches. A positional/key selector
never searches another candidate after a failed check. Predicates and preserving
checks return the selection; typed conditions return their chosen result.

Quantifiers accept predicates or conditions, including `all(present)` and
`allValues(singleElement)`. Nested results are discarded; narrowing does not
change the container's element type. `all`/`none` match empty containers, `any`
does not. Evaluation stops at the first decisive candidate.

Bare selectors cannot be nested in selectors: `singleElement(present)` and
`singleElement(singleElement)` are unsupported. Selectors with a declared result,
e.g. `singleElement(instanceOf(Payment.class))`, can be nested again.

Wildcard views retain comparison constraints. For `List<? extends Payment>`,
`singleElement(equalTo(expectedPayment))` may be rejected; use
`singleElement(expectedPayment::equals)`, also inside `captured`. Upper-bounded
map values have the same restriction.

## Callbacks

`matches` uses a boolean result. `asserted` accepts normal return and converts
`AssertionError` to a mismatch. A method reference's return value is discarded
by `asserted`: returning `false` does **not** fail the assertion.

Callbacks use `CheckedPredicate`, `CheckedBiPredicate`, `CheckedConsumer`, or
`CheckedFunction`, permitting checked throwables. See [failure handling](diagnostics.md#failures-and-interruption).
For stored JDK callbacks, use `predicate::test`, `consumer::accept`, or `function::apply`.
Negate checked predicates with `CheckedPredicate.not` or `CheckedBiPredicate.not`.

## Custom conditions

```java
String receipt = await(paymentRepository::load).until(condition("payment has a receipt", actual ->
        actual.receipt() == null ? unsatisfied("receipt was absent") : satisfied(actual.receipt())));
```

Use the static factories of `io.github.gromoff97.awium.result.ConditionResult<RESULT>`:

| Factory | Meaning |
| --- | --- |
| `satisfied(value)` | Chosen result, including null; pass `actual` to return the observation |
| `unsatisfied()`, `unsatisfied(message)`, `unsatisfied(message, cause)` | Mismatch with optional diagnostics |

There is no no-argument success or user-returnable uncontrolled state. The declared
callback result determines the terminal type: `ConditionResult<CharSequence>`
yields `CharSequence` even with a `String` source. See [lambda inference](usage.md#reasons-and-lambda-types).

`condition` reuses its callback. `conditionPerWait` takes a `Callable` creating one
callback per wait and captured position, on first evaluation. Nested factories wait
for successful extraction. State persists across candidates and retries. Only state
created inside the factory is isolated; external state remains shared.

```java
var check = conditionPerWait("two paid observations", () -> {
    int[] paidObservations = {0};
    return (Payment actual) -> actual.paid() && ++paidObservations[0] >= 2
            ? satisfied(actual) : unsatisfied();
});
```
