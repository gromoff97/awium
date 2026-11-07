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
| `Condition<INPUT, RESULT>` | Check, metadata, run creation, history and diagnostic policy |
| `AwaitRun` | One wait's source, condition, selected value, history and terminal result |
| `DefaultAwaitHandler` | Default history grouping and snapshots |
| `Await`; fluent await classes | Source entry points and configuration |
| `AwaitSupport` | Passing source, configuration, and handler factory to the engine |
| `AwaitAttempt.bind`; internal `GuardedAttempt` | Lazy evaluator initialization and guarded check invocation, including nested checks |
| `WaitEngine` | Polling a supplied run, deadlines, persistence, and completion |
| `ExecutionStep` | Immutable condition verdict, reported attempt, failures and completion time |
| `DiagnosticReport` | Report layout, buffer, and secondary failures |
| `DiagnosticText` | Safe scalar, cause, and duration formatting |

A wait calls `newRun()` before reading the source, then creates its evaluator through
`newAttempt()` on the first observation. Later probes reuse that evaluator. Return a
fresh condition or evaluator for per-wait state, including when a run is rebound as a
definition. Nested `bind` initializes on its first probe.

Checks return `Diagnostic` data in their `AttemptResult`; `diagnostics(attempt)`
can add final context. Core adds the stop reason, timing, and cause.
`historyResult` defaults to the probe result. A composite can report a successful
stage while the whole condition is still unsatisfied: `ExecutionStep.reportedAttempt`
contains that report, and `conditionSatisfied` controls waiting.
`historyConditions(definition)` lists the reported conditions; `definition` retains
the original `because` reason. `historySlot(attempt)` selects a condition by its
1-based position in that list.
These hooks belong to `Condition`; no lifecycle base class is needed.
`ProbeFailure` adds a nested location while preserving the original cause.

## Pattern choices

| Approach | Application |
| --- | --- |
| Thin Glue; Inline Class | Remove a layer only when its responsibility disappears; merging must not spread its dependencies. |
| Functional Core, Imperative Shell | Keep decisions over completed data separate from source, probe, clock and parking effects. Do not add adapters just to make the split formal. |
| Single state owner; immutability | `ExecutionStep` owns its verdict and completion time; polling derives the next interval from the previous step. Mutable run, evaluator, history and rendering state stays local to one execution. Final fields do not freeze observed objects. |
| State machine | `ACQUISITION` → optional `PERSISTENCE` → completion. Separate loops preserve different deadline rules: acquisition rejects late success; persistence requires a final probe. |
| Result types | Sealed outcomes distinguish mismatch, execution failure and absent observations. User `Condition` implementations remain open. |
| Pure dependency injection | `WaitEngine.execute` creates one `AwaitRun`; the engine constructor receives that run and configuration. No container or service locator. |
| Policy vs mechanism | Conditions own check, stage and diagnostic policy; core enforces invocation and waiting contracts. |
| Architectural fitness functions | `CoreArchitectureTest` checks dependency boundaries, verdict access and allowed owners of mutable fields. |
| Strategy | `Condition` and `AwaitAttempt` supply checks; `AwaitHandler` supplies history collection. |
| Decorator | `ExplainedCondition` adds immutable `because` metadata without changing the input or result. |
| Facade | Typed await builders keep execution details behind the fluent API. |

## Type and module boundaries

Core has one open condition interface and one `until(Condition)` per wait mode.
`AwaitAttempt.bind(condition)` retains its declared input and result types.
Container selectors and source-dependent fluent typing belong to consumers of
this contract. They do not add condition families or casts to the engine.

The public builders provide protected copy constructors for fluent extensions;
copies retain source, timing, and handler factory. Their final `until` methods
enforce the same execution contract.

The Java module `io.github.gromoff97.awium.core` exports `io.github.gromoff97.awium`,
`.result`, `.function`, and `.execution`.
The unexported `.internal` package owns run coordination, polling, history recording,
and diagnostic rendering. `.execution` exposes wait handlers, their events, and nested failure locations.
No `permits` list names an external condition.
