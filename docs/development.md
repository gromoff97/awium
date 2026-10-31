# Development

[README](../README.md)

## Build and checks

```sh
./gradlew check
./gradlew javadoc
```

`check` covers behavior, fluent compilation, packaged-module boundaries, and zero
product compile/runtime dependencies. Tests use JUnit 6, AssertJ, Mockito,
compile-testing, ArchUnit, and Commons Lang via Gradle `testImplementation`.
Mockito uses JDK proxies without a Java agent.

PIT uses `ALL`: run `./gradlew pitest` **only on explicit user request**, never
through `build`, `check`, or `test`.

Behavior tests use virtual time; integration tests cover platform/virtual callers,
cancellation, and concurrent state isolation. Ordinary compiled tests demonstrate
valid API use; negative compilation tests protect incompatible-type rejection.

## Responsibilities

| Component | Owns |
| --- | --- |
| `Condition` families | Definitions and type constraints |
| `Conditions`, domain catalogs | Public factories |
| `ConditionSupport`, `ValueMatching` | Construction/composition and shared comparisons |
| `Await`; `ValueAwait`, `SelectionAwait`, `MapAwait` | Source entry points; fluent configuration |
| `DiagnosticAwait`, `DiagnosticSelectionAwait`, `DiagnosticMapAwait` | Diagnostic facade |
| `AwaitSupport` | Shared execution and terminal handling |
| `AttemptFactory` | Evaluator initialization and observation binding; [callback lifecycle](conditions.md#custom-conditions) |
| `BoundAttempt.call()` | Check invocation, returning internal `AttemptResult` |
| `SequenceExecution` | Stage transitions and retained selected/captured values |
| `WaitEngine` | Polling, deadlines, persistence |
| `AttemptExecution`; transient `ExecutionStep` | Source/attempt calls; timing and execution context |
| `AttemptHistory` | Invocation numbering and [history grouping](diagnostics.md#what-history-retains) |
| `FailureDiagnostics`, `DiagnosticText` | Terminal formatting and guarded rendering outside polling |

`BoundAttempt` stores no previous result, timing, number, or progress.
`AttemptResult` adds uncontrolled failure to the public
[condition outcomes](conditions.md#custom-conditions); public record shapes belong
to [diagnostics](diagnostics.md).

## Type and module boundaries

`Condition.Check` represents one check; `Condition.Captured`, a sequence.
`ApplicableCondition` carries input/comparison constraints; `InputCondition`,
input-only constraints. `PreservesObservation` lets preserving checks and comparisons
share a terminal overload. Selectors infer results from source types;
`Condition.Typed<INPUT, RESULT>` declares them. User-facing inference rules are in
[usage](usage.md#reasons-and-lambda-types).

The module exports `io.github.gromoff97.awium`, `.result`, and `.function`.
`.internal` is unsupported and unexported, even if reachable on the classpath;
binding and value-matching helpers remain package-private.
Artifact tests forbid execution dependencies on fluent facades, catalogs, or
formatting, and catalog dependencies on execution classes.
