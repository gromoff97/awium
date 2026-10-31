# Development

[README](../README.md)

## Build and checks

```sh
./gradlew check
./gradlew javadoc
```

`check` runs behavior, fluent compilation, and packaged-module checks and verifies
that the product declares no compile/runtime dependencies. Tests use JUnit 6,
AssertJ, Mockito, compile-testing, ArchUnit, and Commons Lang, all declared through
Gradle `testImplementation`. Mockito uses JDK proxies and needs no Java agent.

Mutation testing uses PIT's `ALL` set. Run `./gradlew pitest` **only on explicit
user request**; never attach it to `build`, `check`, or `test`.

Behavior tests use virtual time. Integration tests cover platform/virtual caller
threads, external cancellation, and independent state in concurrent waits.
Positive API examples are ordinary compiled tests; negative compilation tests
protect incompatible-type rejection.

## Responsibilities

| Component | Responsibility |
| --- | --- |
| `Condition` and typed families | Immutable definition, references, explanation, input/result constraints |
| `Conditions` and domain catalogs | Public factories and composition |
| `ConditionSupport`, `ValueMatching` | Shared construction, composition, and comparisons |
| `Await`, `ValueAwait`, `SelectionAwait`, `MapAwait` | Sources and immutable fluent configuration |
| `DiagnosticAwait`, `DiagnosticSelectionAwait`, `DiagnosticMapAwait` | Same configuration, diagnostic terminal results |
| `AwaitSupport` | Shared execution and terminal result handling |
| `AttemptFactory` | One lazy evaluator per check use; binds observations to attempts |
| `BoundAttempt.call()` | Runs a check for its observation and returns `AttemptResult` |
| `SequenceExecution` | Stage transitions, selected results, captured observations |
| `WaitEngine` | Polling, deadlines, and persistence |
| `AttemptExecution`, transient `ExecutionStep` | Source retrieval, attempt invocation, timing and execution context |
| `AttemptHistory` | Invocation numbering and per-condition grouping; ordinary waits only count |
| `FailureDiagnostics`, `DiagnosticText` | Terminal failure formatting and guarded rendering outside polling |

Each terminal owns its execution state. Nested evaluators initialize only after
successful extraction and keep state across retries; captured positions have
separate evaluators. A bound attempt stores no previous result, timing, number,
or progress. Observations and callbacks themselves may be mutable.

Public [`ConditionResult<RESULT>`](conditions.md#custom-conditions) permits success
or mismatch; internal `AttemptResult` also permits uncontrolled failure. Public
[`Attempt<OBSERVED>` and `ConditionHistory`](diagnostics.md#histories) retain observations
and diagnostics; selected results and stage transitions remain in `SequenceExecution`.

## Type and module boundaries

`Condition.Check` models an individual check, `Condition.Captured` a sequence.
`ApplicableCondition` carries input/comparison constraints; `InputCondition` shares
input-only checks. `PreservesObservation` lets preserving checks and comparisons
share a terminal overload. Selectors derive their result from the source;
`Condition.Typed<INPUT, RESULT>` declares it. Captured definitions constrain inputs
independently of stage result types. See [inference limits](usage.md#reasons-and-lambda-types).

The module exports `io.github.gromoff97.awium`, `.result`, and `.function`.
`.internal` is not exported or supported API, even when accessible on the classpath;
condition binding and value-matching helpers remain package-private.
Artifact tests enforce that execution does not depend on the fluent facade,
catalogs, or diagnostic formatting, and that catalogs do not depend directly
on execution classes. Extend conditions through public factories.
