# Development

[README](../README.md)

## Build and checks

From `awium-core`, build and measure ordinary test coverage:

```sh
./gradlew clean build jacocoTestReport
```

JaCoCo writes HTML and XML to `build/reports/jacoco/test`.

This independent build checks the engine, results, history grouping, timing, and
core-only JAR consumers on the classpath and module path. It rejects product
compile/runtime dependencies. Tests use JUnit 6, AssertJ, Mockito, Commons Lang,
compile-testing, and ArchUnit.

PIT uses `ALL`: run `./gradlew pitest` **only on explicit user request**.
It is separate from `build`, `check`, and `test`.

## Tests

Java tests in `src/test` group API contracts, waiting, execution, results, diagnostics,
and architecture. `AwaitTestAccess` injects `FakeTime` through package access;
fluent builders contain no clock-injection methods. Clock and parker use the same
nanosecond time base: the clock is monotonic, and parking may return early.
Test clocks must not clear the interrupt flag. Clock failures propagate directly;
parker failures stop the wait. Production uses `System.nanoTime` and `LockSupport.parkNanos`.
Mockito uses JDK proxies and reflection without a Java agent.
Integration tests cover platform and virtual callers, cancellation, and concurrent
execution of custom conditions. Core tests have no dependency on built-in conditions.

## Responsibilities

| Component | Owns |
| --- | --- |
| `Condition`, `AwaitCondition` families | Immutable definitions, type constraints, and creation of independent runs |
| `CheckDefinition` | Evaluator storage and run creation |
| `CheckRun` | One check's evaluation and final value |
| `AbstractConditionRun` | History recording and terminal result assembly |
| `AttemptHistory` | Invocation numbering and grouping within each history |
| `Await`; fluent await classes | Source entry points and configuration |
| `AwaitSupport` | Starting a run, passing it to the engine, and returning its result |
| `ConditionRun` | Lifecycle contract without sequence or stage concepts |
| `ConditionExecution` | Lazy evaluator initialization and nested check invocation |
| `WaitEngine` | Polling, deadlines, and persistence from immutable step snapshots |
| `AttemptExecution`; `ExecutionStep` | Source/check calls, failures, and attempt timing |
| `FailureDiagnostics`; `DiagnosticText` | Terminal formatting and guarded value rendering |

Override `Condition.newAttempt()` for a stateful evaluator, created lazily for each use.
For a custom lifecycle, override `newRun(definition, recording)` and return a fresh
`AbstractConditionRun`. Its `probe` checks observations, `complete` decides completion,
and `result` supplies the final value, including when nested. The supplied definition
carries the `because` reason; protected hooks customize history scopes and diagnostics.
Mutable state stays within a run; definitions stay immutable.
`ProbeFailure` adds a nested location while preserving the original cause.

## Type and module boundaries

`ApplicableCondition` constrains inputs and comparisons; `InputCondition` fixes
the comparison constraint for input-only checks. `CollectingCondition` declares a
source-observation list result. `PreservesObservation` shares a terminal overload
across preserving checks and comparisons. Selector implementations must return the
corresponding source element, entry, or value; typed `Condition` declares its output.

The Java module `io.github.gromoff97.awium.core` exports `io.github.gromoff97.awium`,
`.result`, `.function`, and `.execution`.
Its `.internal` package is unexported. No `permits` list names an external condition.
`WaitTiming` exposes durations without clock-injection hooks.
