package io.github.gromoff97.awium.conditions

import io.github.gromoff97.awium.AwaitCondition
import io.github.gromoff97.awium.AwaitCondition.ComparisonCondition
import io.github.gromoff97.awium.AwaitCondition.PreservingCondition
import io.github.gromoff97.awium.AwaitCondition.Reference
import io.github.gromoff97.awium.Condition
import io.github.gromoff97.awium.execution.ConditionExecution
import io.github.gromoff97.awium.execution.ConditionRun
import io.github.gromoff97.awium.execution.DefinedCondition
import io.github.gromoff97.awium.execution.ProbeFailure
import io.github.gromoff97.awium.function.AwaitAttempt
import io.github.gromoff97.awium.result.Attempt
import io.github.gromoff97.awium.result.AwaitAssertionError
import io.github.gromoff97.awium.result.AwaitExecutionException
import io.github.gromoff97.awium.result.AwaitFailure
import io.github.gromoff97.awium.result.AwaitFailure.Reason
import io.github.gromoff97.awium.result.AwaitResult
import io.github.gromoff97.awium.result.ConditionResult
import io.github.gromoff97.awium.result.ConditionResult.Satisfied
import io.github.gromoff97.awium.result.ConditionResult.Unsatisfied
import io.github.gromoff97.awium.result.ConditionResult.satisfied
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.function.Executable

object ConditionAssertions {
    @JvmStatic
    fun <INPUT> probe(run: ConditionRun<INPUT, *>, actual: INPUT): ConditionResult<*> {
        run.beginAttempt()
        return run.probe(actual)
    }

    @JvmStatic
    fun evaluate(condition: AwaitCondition, actual: Any?): ConditionResult<*> =
        ConditionExecution.bind<Any?, Any?>(condition).probe(actual)

    @JvmStatic
    fun rejection(condition: AwaitCondition, actual: Any?): Unsatisfied<*> =
        assertInstanceOf(Unsatisfied::class.java, evaluate(condition, actual))

    @JvmStatic
    fun <INPUT> rejection(condition: AwaitCondition.InputCondition<in INPUT>, actual: INPUT): Unsatisfied<*> =
        rejection(condition as AwaitCondition, actual)

    @JvmStatic
    fun thrown(condition: AwaitCondition, actual: Any?): Throwable {
        val failure = assertThrows(Throwable::class.java) { evaluate(condition, actual) }
        return if (failure is ProbeFailure) failure.cause!! else failure
    }

    @JvmStatic
    fun diagnosticCondition(description: String, reference: Reference<*>?): Condition<Any?, Any?> =
        DefinedCondition(description, reference, AwaitAttempt { satisfied(it) })

    @JvmStatic
    fun <SOURCE, RESULT> evaluate(condition: Condition<in SOURCE, out RESULT>, actual: SOURCE): ConditionResult<out RESULT> =
        ConditionExecution.bind(condition).probe(actual)

    @JvmStatic
    fun <SOURCE> evaluate(condition: PreservingCondition<in SOURCE>, actual: SOURCE): ConditionResult<out SOURCE> =
        ConditionExecution.bind<SOURCE, SOURCE>(condition).probe(actual)

    @JvmStatic
    fun <SOURCE> evaluate(condition: ComparisonCondition<out SOURCE>, actual: SOURCE): ConditionResult<out SOURCE> =
        ConditionExecution.bind<SOURCE, SOURCE>(condition).probe(actual)

    @JvmStatic
    fun assertUnsatisfied(evaluation: ConditionResult<*>) {
        assertInstanceOf(Unsatisfied::class.java, evaluation)
    }

    @JvmStatic
    fun <RESULT> result(evaluation: ConditionResult<out RESULT>): RESULT =
        if (evaluation is Satisfied<out RESULT>) evaluation.value() else fail("condition was not satisfied")

    @JvmStatic
    fun mismatch(evaluation: ConditionResult<*>): String? =
        assertInstanceOf(Unsatisfied::class.java, evaluation).mismatch()

    @JvmStatic
    fun failure(result: AwaitResult<*, *>): AwaitFailure =
        assertInstanceOf(AwaitResult.Failed::class.java, result).failure()

    @JvmStatic
    fun failure(expected: Reason, result: AwaitResult<*, *>): AwaitFailure =
        failure(result).also { assertEquals(expected, it.reason()) }

    @JvmStatic
    fun <OBSERVED> attempts(result: AwaitResult<OBSERVED, *>): List<Attempt<OBSERVED>> =
        result.conditions().flatMap { it.attempts().values }

    @JvmStatic
    fun <OBSERVED> observation(attempt: Attempt<OBSERVED>): OBSERVED = when (attempt) {
        is Attempt.SatisfiedObservation -> attempt.observed()
        is Attempt.UnsatisfiedObservation -> attempt.observed()
        is Attempt.FailedAfterObservation -> attempt.observed()
        is Attempt.FailedBeforeObservation -> fail("expected an observation")
    }

    @JvmStatic
    fun uncontrolled(operation: Attempt.Operation, attempt: Attempt<*>): Attempt.Failure<*> =
        assertInstanceOf(Attempt.Failure::class.java, attempt).also { assertEquals(operation, it.operation()) }

    @JvmStatic
    fun assertFailure(reason: Reason, executable: Executable): Throwable {
        val thrown = when (reason) {
            Reason.TIMEOUT, Reason.PERSISTENCE_FAILED -> assertThrows(AwaitAssertionError::class.java, executable)
            else -> assertThrows(AwaitExecutionException::class.java, executable)
        }
        val failure = if (thrown is AwaitAssertionError) thrown.failure() else (thrown as AwaitExecutionException).failure()
        assertEquals(reason, failure.reason())
        return thrown
    }
}
