package dev.openallay.world;

import dev.openallay.model.CancellationSignal;
import java.util.concurrent.CompletionStage;

/**
 * Request-scoped bridge that schedules owning-thread capture and publishes detached values only.
 */
public interface WorldObservationCoordinator extends AutoCloseable {
    CompletionStage<BlockObservation> inspect(
            WorldObservationRequest request, CancellationSignal cancellation);

    CompletionStage<EntityObservation> entities(
            WorldObservationRequest request, CancellationSignal cancellation);

    CompletionStage<WorldEntitySnapshot> entity(
            String observationId, CancellationSignal cancellation);

    @Override
    default void close() {}
}
