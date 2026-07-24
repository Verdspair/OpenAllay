package dev.openallay.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;

final class CommunityCatalogCodecTest {
    private final CommunityCatalogCodec codec = new CommunityCatalogCodec();

    @Test
    void decodesStrictSchemaOneSkillCatalogInDeterministicOrder() {
        CommunityCatalogManifest manifest = codec.decode("""
                {
                  "schemaVersion": 1,
                  "kind": "skill",
                  "generatedAt": "2026-07-25T00:00:00Z",
                  "packages": [
                    {
                      "id": "zeta",
                      "version": "1.0.0",
                      "archive": "https://example.test/zeta.zip",
                      "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                      "compatibility": {"minecraft": "26.2", "openallayApi": "0.2"},
                      "source": "https://example.test/zeta"
                    },
                    {
                      "id": "alpha",
                      "version": "2.0.0",
                      "archive": "https://example.test/alpha.zip",
                      "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                      "compatibility": {"minecraft": "26.2", "openallayApi": "0.2"},
                      "source": "https://example.test/alpha"
                    }
                  ]
                }
                """);

        assertEquals(Instant.parse("2026-07-25T00:00:00Z"), manifest.generatedAt());
        assertEquals(java.util.List.of("alpha", "zeta"),
                manifest.packages().stream().map(CommunityCatalogManifest.PackageEntry::id).toList());
        assertEquals(URI.create("https://example.test/alpha.zip"),
                manifest.packages().getFirst().archive());
    }

    @Test
    void rejectsUnknownFieldsVersionsDuplicateIdsAndUnsafeUris() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid()
                .replace("\"packages\"", "\"unknown\":true,\"packages\"")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid()
                .replace("\"schemaVersion\":1", "\"schemaVersion\":2")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid()
                .replace("https://example.test/a.zip", "http://example.test/a.zip")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(valid()
                .replace("]", "," + validEntry("alpha") + "]")));
    }

    private static String valid() {
        return """
                {"schemaVersion":1,"kind":"skill","generatedAt":"2026-07-25T00:00:00Z",
                 "packages":[%s]}
                """.formatted(validEntry("alpha"));
    }

    private static String validEntry(String id) {
        return """
                {"id":"%s","version":"1.0.0","archive":"https://example.test/a.zip",
                 "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                 "compatibility":{"minecraft":"26.2","openallayApi":"0.2"},
                 "source":"https://example.test/a"}
                """.formatted(id);
    }
}
