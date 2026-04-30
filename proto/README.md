# Protobuf Contracts

Shared `.proto` files for gRPC services in CodeArena. Single source of truth — every consuming service points its `protobuf-maven-plugin` at this directory via `<protoSourceRoot>${project.basedir}/../proto</protoSourceRoot>`.

## Files

| File | Owner | Consumers |
|---|---|---|
| `common.proto` | shared | all services |
| `auth.proto` | Auth Service | App Service, Execution Service |
| `execution.proto` | Execution Service | App Service |

## Regeneration

Stubs are generated at compile time per service. There is no separate publish step — running `mvn -pl <service> -am compile` regenerates the Java sources for that service.

## Style

- `package codearena.<domain>.v1;` — version every package, never break wire compat
- `option java_multiple_files = true;` — one Java class per message
- `option java_package = "dev.codearena.proto.<domain>.v1";` — matches the proto package
- Field numbers are stable. Never renumber. Add new fields at the end.

See `docs/contracts.md` §2 for the rationale behind each RPC.
