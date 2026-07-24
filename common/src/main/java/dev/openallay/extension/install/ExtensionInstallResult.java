package dev.openallay.extension.install;

import java.nio.file.Path;
import java.util.Optional;

public record ExtensionInstallResult(
        String extensionId,
        ExtensionInstallState state,
        String diagnostic,
        Optional<Path> stagedArtifact) {
    public ExtensionInstallResult {
        extensionId = extensionId == null ? "" : extensionId;
        java.util.Objects.requireNonNull(state, "state");
        diagnostic = diagnostic == null ? "" : diagnostic;
        stagedArtifact = java.util.Objects.requireNonNull(stagedArtifact, "stagedArtifact");
    }
}
