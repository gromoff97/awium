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
Mockito uses interface proxies without a Java agent.
Integration tests cover platform and virtual callers, cancellation, and concurrent
execution of custom conditions. Core tests have no dependency on built-in conditions.

## Responsibilities

| Component | Owns |
| --- | --- |
| `Condition`, `AwaitCondition` families | Immutable definitions, type constraints, and creation of independent runs |
| `ConditionRun` | Executing a condition, recording history, and assembling the terminal result |
| `AttemptHistory` | Invocation numbering and grouping within each history |
| `Await`; fluent await classes | Source entry points and configuration |
| `AwaitSupport` | Passing source and configuration to the engine |
| `AwaitAttempt.bind`; internal `ConditionExecution` | Lazy evaluator initialization and nested check invocation |
| `WaitEngine` | Running waits: polling, deadlines, persistence, and completion |
| `AttemptExecution`; `ExecutionStep` | Source/check calls, failures, and attempt timing |
| `FailureDiagnostics`; `DiagnosticText` | Stop reason, attempt number, timing, cause, and guarded rendering |

Implement `description()` and `probe(actual)` directly; no base constructor is required.
The default `because` returns a separate definition and preserves custom lifecycle methods.
For state shared across attempts, override `newRun()` and return a fresh `Condition`.
Its `probe` returns `unsatisfied(...)` to keep waiting or `satisfied(value)` to finish,
including when nested. `newAttempt()` can instead create a lazy evaluator.
Mutable state stays within a run; definitions stay immutable.

Checks return `Diagnostic` data in their `AttemptResult`; `diagnostics(attempt)`
can add final context. Core adds the stop reason, timing, and cause, retaining
rendering failures separately. `historyResult` defaults to the probe result;
composites can report a stage's outcome without completing the whole wait.
`historyConditions(definition)` lists the reported conditions; `definition` retains
the original `because` reason. `historySlot(attempt)` selects a condition by its
1-based position in that list.
These hooks belong to `Condition`; no lifecycle base class is needed.
`ProbeFailure` adds a nested location while preserving the original cause.

## Type and module boundaries

`ApplicableCondition` constrains inputs and comparisons; `InputCondition` fixes
the comparison constraint for input-only checks. `CollectingCondition` declares a
source-observation list result; its default `stages()` returns the condition itself
as the only stage. `PreservesObservation` shares a terminal overload
across preserving checks and comparisons. Selector implementations must return the
corresponding source element, entry, or value; typed `Condition` declares its output.
These families preserve result inference through `because`; all use the same run contract.

The Java module `io.github.gromoff97.awium.core` exports `io.github.gromoff97.awium`,
`.result`, `.function`, and `.execution`.
The unexported `.internal` package owns run coordination, polling, history recording,
and diagnostic rendering. `.execution` exposes only nested failure locations.
No `permits` list names an external condition.
