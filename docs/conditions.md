# Conditions

[README](../README.md) · [Usage](usage.md) · [Diagnostics](diagnostics.md)

Checks return the exact observed object unless **→** indicates otherwise.
Constants such as `present` need no parentheses. Qualify colliding names:
`CollectionConditions.nonEmpty` and `MapConditions.nonEmpty`.

## Shared rules

Optional, collection, map, and text conditions do not match null observations.

### Expected values

Bulk factories read an iterable or map once and make a shallow copy, preserving
encounter order, duplicates, and supported nulls. They reject null inputs and
report reading failures before polling. Elements, keys, and values are not
deep-copied. Single-value operands retain references, including containers.
`StringConditions` freeze text operands at construction.

Use `singletonList(array)` for one array in an expected set;
`List.of(array)` expands reference arrays. Likewise, wrap a collection or array
when it is one candidate for `in`/`notIn`.

For non-null observations:

| Empty expectation | Outcome |
| --- | --- |
| `containsAll`, `doesNotContainAny`, including map key/value/entry forms | Satisfied |
| `containsAny`, `doesNotContainAll`, including entry forms | Unsatisfied |
| `containsOnly`, `containsOnlyKeys`, exact-content checks | Only empty observations match; negative exact checks have the opposite result |
| Ordered prefix/suffix/sequence/subsequence | Satisfied; negative sequence/subsequence checks are unsatisfied |

Checks with empty fragment sets read no characters. Empty ordered patterns need
neither size nor elements. A non-empty pattern longer than the collection fails
by size without reading elements. Every non-null text contains `""`, so a set with
that fragment behaves differently from an empty set.

### Size and length

Collections and maps have `hasSize(n)`, `hasSizeNotEqualTo(n)`, `hasSizeGreaterThan(n)`,
`hasSizeAtLeast(n)`, `hasSizeLessThan(n)`, `hasSizeAtMost(n)`, and inclusive
`hasSizeBetween(min, max)`. Text uses the same suffixes with `hasLength`.

### Callback types

Callbacks use `CheckedPredicate`, `CheckedBiPredicate`, `CheckedConsumer`, or
`CheckedFunction`, permitting checked throwables.
For stored JDK callbacks, use `predicate::test`, `consumer::accept`, or `function::apply`.
Negate checked predicates with `CheckedPredicate.not` or `CheckedBiPredicate.not`.

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
| `matches(predicate)`, `asserted(assertion)` | Boolean predicate or assertion |
| `condition(name, callback)`, `conditionPerWait(name, factory)` | → Chosen result |

Single bounds need not be comparable: the source must implement `Comparable<BOUND>`.
Range bounds must also compare with each other. Empty `in` never matches;
empty `notIn` matches anything, including null.
[Signatures](../src/main/java/io/github/gromoff97/awium/Conditions.java).

### Predicates and assertions

`matches` uses a boolean result. `asserted` succeeds on normal return and converts
`AssertionError` to a mismatch. It discards a method reference's return value:
returning `false` does not fail the assertion.

### Custom conditions

```java
String receipt = await(paymentRepository::load).until(condition("payment has a receipt", actual ->
        actual.receipt() == null ? unsatisfied("receipt was absent") : satisfied(actual.receipt())));
```

Use the static factories of `ConditionResult<RESULT>`:

| Factory | Meaning |
| --- | --- |
| `satisfied(value)` | Chosen result, including null; pass `actual` to return the observation |
| `unsatisfied()`, `unsatisfied(message)`, `unsatisfied(message, cause)` | Mismatch with optional diagnostics |

There is no no-argument success or user-returnable uncontrolled state. The declared
callback result determines the terminal type: `ConditionResult<CharSequence>`
yields `CharSequence` even with a `String` source.

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

## Reasons and lambda types

Every condition has `because(nonBlankReason)`, which returns a copy with a reason
shown in diagnostics. Calling it again replaces the reason; an outer reason overrides
an inherited nested reason.

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
needed by `until`.

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

## OptionalConditions

| Condition | Meaning |
| --- | --- |
| `present` | Present → contained value |
| `absent` | Empty optional |
| `hasValue(check)` | Present → checked value or chosen result |

[Signatures](../src/main/java/io/github/gromoff97/awium/OptionalConditions.java).

## StringConditions

These checks accept any `CharSequence` without converting it to `String`.

| Condition | Meaning |
| --- | --- |
| `empty`, `nonEmpty`, `blank`, `nonBlank` | Empty or whitespace-only text |
| `contentEquals(text)` | Equal characters across text implementations |
| `equalToIgnoringCase(text)`, `notEqualToIgnoringCase(text)` | Case-insensitive equality |
| `contains(text)`, `doesNotContain(text)`, `containsIgnoringCase(text)` | One fragment |
| `containsAll(xs)`, `doesNotContainAny(xs)` | An iterable of fragments |
| `startsWith(text)`, `doesNotStartWith(text)`, `endsWith(text)`, `doesNotEndWith(text)` | Prefix or suffix |
| `matchesRegex(regex)`, `doesNotMatchRegex(regex)` | JDK regex, supplied as text or `Pattern` |
| `hasLength...` | Text length |

Text operands accept `CharSequence`. Blankness uses `Character.isWhitespace` on
Unicode code points. Unlike `contentEquals`, general `equalTo` may reject distinct
`StringBuilder` objects with identical characters.
[Signatures](../src/main/java/io/github/gromoff97/awium/StringConditions.java).

## CollectionConditions

These checks require `Collection`. Ordered operations require `SequencedCollection`
(lists, deques, sequenced sets). `xs` is an iterable of expected elements.

| Condition | Meaning |
| --- | --- |
| `empty`, `nonEmpty`, `containsNull`, `doesNotContainNull`, `containsOnlyNulls`, `hasNoDuplicates` | Contents |
| `hasSize...` | Collection size |
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

Selections accept nested checks, e.g. `elementAt(0, check)`.
`elementAt` requires a non-negative index and traverses to it even in array-backed
lists. First/last traverse forward/backward; single selection uses one iterator to
distinguish zero, one, or multiple elements. These reads are not atomic snapshots.
For ordered map views, use `sequencedKeySet()`, `sequencedValues()`, or
`sequencedEntrySet()`; plain `values()` exposes only `Collection`.
[Signatures](../src/main/java/io/github/gromoff97/awium/CollectionConditions.java).

## MapConditions

| Condition | Meaning |
| --- | --- |
| `empty`, `nonEmpty`, `hasSize...` | Contents or size |
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

Expected keys and values use `Objects.deepEquals`, observed operand first, regardless
of map comparators or identity semantics. Nested checks keep their own comparison:
`hasEntry(equalTo(entry))` follows entry equality (array keys/values compare by
identity in standard entries). Selected entries may remain connected to the map.
[Signatures](../src/main/java/io/github/gromoff97/awium/MapConditions.java).

### Map entry inference

Entry callbacks can call `setValue`, so they require compatible map types. For
covariant views, use a key/value predicate with known parameter types. A reusable
typed-result callback inside `singleEntry` needs an input such as
`Entry<? extends String, ? extends Payment>`; inline callbacks infer it.

For wildcard entry predicates, even typed callbacks can lose the map type with
an outer `because`. Put the reason on the inner check:

```java
CheckedPredicate<Entry<String, ? extends Number>> positive =
        entry -> entry.getValue().intValue() > 0;
Map<String, Integer> checked = await(() -> Map.of("ready", 1))
        .until(allEntries(matches(positive).because("Positive counts required")));
```

The same applies to `hasEntry` and `hasNoEntry`.

## Ordered states

`captured(first, second, ...)` waits for checks in order, collecting the complete
source observation at each success:

```java
List<Payment> states = await(paymentRepository::load).until(captured(
        matches(Payment::created), matches(Payment::paid)));

List<List<Payment>> batches = await(paymentRepository::findAll).until(captured(
        hasSize(1), singleElement(Payment::paid)));
```

Each stage gets a fresh source observation, not the previous stage's extracted value.
Stages may return different types but must all accept the source. Captured stages
keep individual `because` reasons. An optional source produces `List<Optional<Payment>>`;
one captured check still produces a one-element list.

`persisting` rechecks only the final stage; earlier captures stay unchanged.

Capture sequences cannot be nested. `captured(conditions)` copies its argument
array and rejects empty/null arrays and null stages. Definitions such as
`captured(present, present).because("available twice")` work across optional types.

For covariant sources, typed views preserve each observation's wildcard type.
Mixed selectors, predicates, or comparisons followed by `because` may need a source
hint: `Conditions.<List<Payment>>captured(...)` (likewise `Optional<Payment>` or
`Map<String, Payment>`). Comparing the complete source as well can require two hints:
`Conditions.<List<Payment>, List<Payment>>captured(...)`. Incompatible checks remain
compile-time errors.
