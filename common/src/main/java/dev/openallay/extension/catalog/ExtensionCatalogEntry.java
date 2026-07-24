package dev.openallay.extension.catalog;

import dev.openallay.extension.OpenAllayExtensionDescriptor;
import java.net.URI;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** One immutable Extension package entry from a schema-1 catalog. */
public record ExtensionCatalogEntry(
        String id,
        String name,
        String version,
        String provider,
        String summary,
        Set<String> loaders,
        String minecraftVersionRange,
        String openAllayApiVersionRange,
        URI artifact,
        String sha256,
        Set<String> modIds,
        String source) {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern MOD_ID = Pattern.compile("[a-z0-9_.-]+");

    public ExtensionCatalogEntry {
        OpenAllayExtensionDescriptor descriptor = new OpenAllayExtensionDescriptor(
                id,
                name,
                version,
                provider,
                summary,
                loaders,
                minecraftVersionRange,
                openAllayApiVersionRange,
                source);
        id = descriptor.id();
        name = descriptor.name();
        version = descriptor.version();
        provider = descriptor.provider();
        summary = descriptor.summary();
        loaders = descriptor.loaders();
        minecraftVersionRange = descriptor.minecraftVersionRange();
        openAllayApiVersionRange = descriptor.openAllayApiVersionRange();
        artifact = secureUri(artifact);
        if (sha256 == null || !SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("Extension SHA-256 must be 64 lowercase hex digits");
        }
        TreeSet<String> normalizedModIds = new TreeSet<>();
        for (String modId : Set.copyOf(modIds)) {
            if (modId == null || !MOD_ID.matcher(modId).matches()) {
                throw new IllegalArgumentException("Invalid Extension mod ID: " + modId);
            }
            normalizedModIds.add(modId);
        }
        if (normalizedModIds.isEmpty()) {
            throw new IllegalArgumentException("Extension package must declare at least one mod ID");
        }
        modIds = Set.copyOf(normalizedModIds);
        source = descriptor.source();
    }

    public ExtensionCatalogEntry(
            String id,
            String name,
            String version,
            String provider,
            String summary,
            Set<String> loaders,
            String minecraftVersionRange,
            String openAllayApiVersionRange,
            String artifact,
            String sha256,
            Set<String> modIds,
            String source) {
        this(
                id,
                name,
                version,
                provider,
                summary,
                loaders,
                minecraftVersionRange,
                openAllayApiVersionRange,
                URI.create(artifact),
                sha256,
                modIds,
                source);
    }

    public OpenAllayExtensionDescriptor descriptor() {
        return new OpenAllayExtensionDescriptor(
                id,
                name,
                version,
                provider,
                summary,
                loaders,
                minecraftVersionRange,
                openAllayApiVersionRange,
                source);
    }

    private static URI secureUri(URI uri) {
        java.util.Objects.requireNonNull(uri, "artifact");
        String host = uri.getHost();
        boolean loopback = host != null
                && (host.equalsIgnoreCase("localhost")
                        || host.equals("127.0.0.1")
                        || host.equals("::1"));
        if (!"https".equalsIgnoreCase(uri.getScheme())
                && !("http".equalsIgnoreCase(uri.getScheme()) && loopback)) {
            throw new IllegalArgumentException(
                    "Extension artifact URI must use HTTPS or loopback HTTP");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    "Extension artifact URI must not contain credentials");
        }
        return uri;
    }
}
