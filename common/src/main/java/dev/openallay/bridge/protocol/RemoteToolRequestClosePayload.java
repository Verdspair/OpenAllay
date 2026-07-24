package dev.openallay.bridge.protocol;

/** Releases server-side request workspaces after a client-hosted Agent reaches a terminal state. */
public record RemoteToolRequestClosePayload(int version, String requestId) {
    public RemoteToolRequestClosePayload {
        BridgeProtocol.requireVersion(version);
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }
    }
}
