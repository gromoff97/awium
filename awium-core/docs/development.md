# Development

[README](../README.md)

## Build and checks

Run from `awium-core`:

```sh
./gradlew clean build
```

This build does not load the conditions project. Checks cover the engine, results,
history grouping, time configuration, and consumers using only the core JAR on the
classpath and module path. Product compile/runtime dependencies must be empty.
Tests use JUnit 6, AssertJ, Mockito, Commons Lang, compile-testing, and ArchUnit.

PIT uses `ALL`: run `./gradlew pitest` **only on explicit user request**.
It is separate from `build`, `check`, and `test`.

## Tests

All tests and their helpers live in `src/test`; none are exported to other builds.
Behavior tests share the product package and use package-private `usingTime(clock, parker)`
with `FakeTime`. This replaces time for one configuration. Both callbacks use the
same time base in nanoseconds: the clock is monotonic, and the parker may return early.
Clock failures propagate directly; parker failures stop the wait. Clock reads preserve
the interrupt flag. Production defaults are `System.nanoTime` and `LockSupport.parkNanos`.
Mockito uses JDK proxies and reflection without a Java agent.
Integration tests cover platform and virtual callers, cancellation, and concurrent
execution of custom conditions. Core tests have no dependency on built-in conditions.

## Responsibilities

| Component | Owns |
| --- | --- |
| `Condition`, `AwaitCondition` families | Immutable definitions, type constraints, and creation of independent runs |
| `AwaitCondition.CheckDefinition` | Evaluator storage and run creation |
| `execution.CheckRun` | One check's evaluation and final value |
| `execution.AbstractConditionRun` | History recording and terminal result assembly |
| `execution.AttemptHistory` | Invocation numbering and grouping within each history |
| `Await`; fluent await classes | Source entry points and configuration |
| `AwaitSupport` | Starting a run, passing it to the engine, and returning its result |
| `execution.ConditionRun` | Lifecycle contract without sequence or stage concepts |
| `execution.ConditionExecution` | Lazy evaluator initialization and nested check invocation |
| `internal.WaitEngine` | Polling, deadlines, and persistence from immutable step snapshots |
| `internal.AttemptExecution`; `ExecutionStep` | Source/check calls, failures, and attempt timing |
| `execution.FailureDiagnostics`; `execution.DiagnosticText` | Terminal formatting and guarded value rendering |

A run owns completion, history scopes, results, and diagnostic context. The engine
controls when it may run, persistence, and when waiting ends. Mutable state belongs
to one run; definitions remain immutable. `ProbeFailure` adds a nested location
without replacing the original cause.

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
