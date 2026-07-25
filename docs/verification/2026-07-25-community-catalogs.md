# OpenAllay community catalog verification

This report retains a public, anonymous-download verification of the Skill and
Extension communities used by OpenAllay 0.2. It was captured on 2026-07-25.
No Minecraft client or model-provider request was involved.

## Public repositories and CI

| Community | Repository | Verified `main` | Latest quality run |
| --- | --- | --- | --- |
| Skills | `nkanf-dev/OpenAllay-Skills` | `9a9ec6ea0e4c68d36673e7e1e6563384963185ae` | `30137128480` — success |
| Extensions | `nkanf-dev/OpenAllay-Extensions` | `1ab238d2989ca65cc492c377bc4430855fc7dcde` | `30133278040` — success |

Both repositories were reported by GitHub as `PUBLIC`, used `main` as their
default branch, and were readable without a repository credential.

## Catalogs

| Community | Schema | Anonymous URL | SHA-256 |
| --- | --- | --- | --- |
| Skills | 2 | `https://raw.githubusercontent.com/nkanf-dev/OpenAllay-Skills/main/catalog.json` | `f51896f5411c3778e556e2bd79a59ec19b4b861f493b47488f1f9bb2d2253288` |
| Extensions | 2 | `https://raw.githubusercontent.com/nkanf-dev/OpenAllay-Extensions/main/catalog.json` | `df6638ee29b0622739b028241ca1eb54e3a2487e2de461b2c9a6b5bb40b8eed1` |

The product defaults in `SkillSettingsBackend` and
`ExtensionSettingsBackend` point to these exact URLs.

## Skill packages

Every catalog package downloaded anonymously, matched its catalog SHA-256, and
contained a package-root `SKILL.md`.

| Skill | Version | SHA-256 | Extra references |
| --- | --- | --- | --- |
| `diagnose-missing-recipe` | 0.2.1 | `58f0b2bc901d65d5efebfb675b50806f66ed163a3ab2b501f6f8bb8afd87ec4b` | — |
| `explain-machine-usage` | 0.2.1 | `b242545d18734f06663092a5b548bbc499ed08be9395eda5419e310c30110d61` | — |
| `guide-ftb-progression` | 0.2.1 | `cb5f9dd8b23f3ef9cedaa6618fc3594a13769fe7e4ebcaecdf5736516817e996` | — |
| `inspect-game-state` | 0.2.2 | `25906732beb9249c66e1ea078e2c2ebdcdf0649873af2d6968c3577fdf513414` | — |
| `run-game-commands` | 0.2.1 | `a70e0681a3ccf8dbcf449c4def102b2693cbd211d3dabb8028199c2988c07c19` | `references/commands.md` |
| `search-guide-books` | 0.2.1 | `0bd11c5d557d63ddedefa5af29f6324d3fb0341dbe0c5d434c1950c5aa0cd9c2` | — |

## Extension artifacts

Both loader-specific Hello Extension artifacts downloaded anonymously, matched
their catalog SHA-256, and exposed `example:hello` from the embedded strict
`META-INF/openallay-extension.json` manifest.

| Loader | Artifact SHA-256 |
| --- | --- |
| Fabric | `8cf09c60224ca54530e3b0a4c9d814d3c79e8117ae0b6e9b776a11c8d51ebf0d` |
| NeoForge | `2a0bd0ab7993ca49e98a22e19632d752c58b8422abb1405966ca4cded04bb702` |

This proves catalog reachability and package integrity at the recorded
generations. In-game installation, restart activation, and live Agent behavior
remain covered by their separate deterministic or opt-in runtime evidence.
