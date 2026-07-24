package dev.openallay.platform;

import java.util.List;

public interface PlatformService {
    String platformName();

    /** Exact running Minecraft version, supplied by the loader after game bootstrap. */
    String gameVersion();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    /** Complete public loader metadata, detached and sorted by mod id. */
    default List<InstalledModMetadata> installedMods() {
        throw new UnsupportedOperationException("Installed mod metadata is unavailable");
    }

}
