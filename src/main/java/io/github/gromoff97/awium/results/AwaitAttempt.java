package io.github.gromoff97.awium.results;

import io.github.gromoff97.awium.evaluation.ConditionEvaluation;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

/**
 * One polling attempt with its phase, timing, observation, and condition outcome.
 *
 * @param <Observed> complete value returned by the source
 * @param <Result> value produced by the condition
 * @param number one-based attempt number
 * @param phase acquisition or persistence phase
 * @param outcome terminal outcome of this attempt
 */
public record AwaitAttempt<Observed, Result>(long number, Phase phase,
        Outcome<Observed, Result> outcome) {

    public AwaitAttempt {
        if (number <= 0) {
            throw new IllegalArgumentException("attempt number must be positive");
        }
        requireNonNull(phase, "phase must not be null");
        requireNonNull(outcome, "outcome must not be null");
    }

    public enum Phase { ACQUISITION, PERSISTENCE }

    public sealed interface Outcome<Observed, Result> {

        Timing timing();

        record Satisfied<Observed, Result>(Timing.AfterObservation timing,
                Observed observed, Result result) implements Outcome<Observed, Result> {

            public Satisfied {
                requireNonNull(timing, "timing must not be null");
            }
        }

        record Unsatisfied<Observed, Result>(Timing.AfterObservation timing, Observed observed,
                String mismatch, AssertionError assertion,
                ConditionEvaluation.Context context) implements Outcome<Observed, Result> {

            public Unsatisfied {
                requireNonNull(timing, "timing must not be null");
                requireNonNull(mismatch, "mismatch must not be null");
                requireNonNull(context, "context must not be null");
            }
        }

        record WaitingFailed<Observed, Result>(Timing.BeforeRetrieval timing,
                Throwable failure) implements Outcome<Observed, Result> {

            public WaitingFailed {
                requireNonNull(timing, "timing must not be null");
                requireNonNull(failure, "failure must not be null");
            }
        }

        record SourceRetrievalFailed<Observed, Result>(Timing.BeforeObservation timing,
                Throwable failure) implements Outcome<Observed, Result> {

            public SourceRetrievalFailed {
                requireNonNull(timing, "timing must not be null");
                requireNonNull(failure, "failure must not be null");
            }
        }

        record SourceInterrupted<Observed, Result>(Timing.AfterObservation timing, Observed observed,
                InterruptedException failure) implements Outcome<Observed, Result> {

            public SourceInterrupted {
                requireNonNull(timing, "timing must not be null");
                requireNonNull(failure, "failure must not be null");
            }
        }

        record ConditionEvaluationFailed<Observed, Result>(Timing.AfterObservation timing,
                Observed observed, Throwable failure,
                ConditionEvaluation.Context context) implements Outcome<Observed, Result> {

            public ConditionEvaluationFailed {
                requireNonNull(timing, "timing must not be null");
                requireNonNull(failure, "failure must not be null");
                requireNonNull(context, "context must not be null");
            }
        }
    }

    public sealed interface Timing {

        Duration startOffset();

        Duration completionOffset();

        record BeforeRetrieval(Duration startOffset, Duration completionOffset) implements Timing {

            public BeforeRetrieval {
                requireNonNull(startOffset, "start offset must not be null");
                requireNonNull(completionOffset, "completion offset must not be null");
            }
        }

        record BeforeObservation(Duration startOffset, Duration retrievalOffset, Duration completionOffset) implements Timing {

            public BeforeObservation {
                requireNonNull(startOffset, "start offset must not be null");
                requireNonNull(retrievalOffset, "retrieval offset must not be null");
                requireNonNull(completionOffset, "completion offset must not be null");
            }
        }

        record AfterObservation(Duration startOffset, Duration retrievalOffset,
                Duration observationOffset, Duration completionOffset) implements Timing {

            public AfterObservation {
                requireNonNull(startOffset, "start offset must not be null");
                requireNonNull(retrievalOffset, "retrieval offset must not be null");
                requireNonNull(observationOffset, "observation offset must not be null");
                requireNonNull(completionOffset, "completion offset must not be null");
            }
        }
    }
}
