# Vendored folicular API contract

This directory holds a copy of the folicular backend's API contract so that a
plain `git clone` of Luteal builds and tests with no other checkout present.

| Path | Purpose |
| --- | --- |
| `openapi.yaml` | The API specification. `app/build.gradle.kts` feeds it to the OpenAPI generator, which emits the `fr.luteal.core.network.contract` DTOs that `FolicularApiClient`, `ContractMappers` and `CycleSyncEngine` compile against. |
| `conformance/*.json` | Golden response bodies. `ConformanceFixturesTest` decodes them to prove the client parses exactly what the server's own `internal/contract` fixtures test proves the server produces. |

## Upstream is still the source of truth

The folicular repository owns the contract. The files here are a snapshot,
never the place to make a change. To edit the contract, edit it upstream, then
refresh this copy.

## Refreshing

```sh
./scripts/sync-contract.sh                 # sibling checkout at ~/Projects/folicular
./scripts/sync-contract.sh /path/to/folicular
```

Then rebuild so the DTOs regenerate, and run the suite so the fixtures are
checked against them:

```sh
./gradlew testDebugUnitTest
```

## Building against a working copy instead

While changing both sides at once, skip the vendored snapshot entirely:

```sh
./gradlew assembleDebug \
    -Pfolicular.spec=$HOME/Projects/folicular/openapi/openapi.yaml \
    -Pfolicular.conformance=$HOME/Projects/folicular/conformance
```

Sync the snapshot back in before committing, so the repository stays
self-contained.
