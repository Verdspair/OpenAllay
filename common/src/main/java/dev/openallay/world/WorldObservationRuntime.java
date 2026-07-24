package dev.openallay.world;

import dev.openallay.model.CancellationSignal;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Owns request-scoped world coordinators and drops them on every terminal path. */
public final class WorldObservationRuntime {
    private final ConcurrentMap<String, WorldObservationCoordinator> requests =
            new ConcurrentHashMap<>();

    public void capture(String correlationId, WorldObservationCoordinator coordinator) {
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        requests.put(correlationId, java.util.Objects.requireNonNull(coordinator, "coordinator"));
    }

    public Optional<JavascriptWorldBridge> bridge(
            String correlationId, CancellationSignal cancellation) {
        WorldObservationCoordinator coordinator = requests.get(correlationId);
        return coordinator == null
                ? Optional.empty()
                : Optional.of(new JavascriptWorldBridge(coordinator, cancellation));
    }

    public void closeRequest(String correlationId) {
        WorldObservationCoordinator coordinator = requests.remove(correlationId);
        if (coordinator != null) {
            coordinator.close();
        }
    }
}
