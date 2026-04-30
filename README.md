# CodeArena

A self-hosted code execution platform with multi-language support, custom test cases, and real-time execution feedback. Built as a microservices architecture demonstrating gRPC, polyglot persistence, and horizontal scaling of code execution workers via [Judge0](https://judge0.com).

> Status: **Phase 0 — project skeleton.** See [docs/roadmap.md](docs/roadmap.md) for the full implementation plan.

## Stack

**Backend:** Spring Boot 3.3 · Java 21 · Spring Cloud Gateway · gRPC (`net.devh`) · MongoDB · PostgreSQL · Redis
**Frontend:** Next.js 15 · TypeScript · Tailwind CSS 4 · Monaco Editor
**Infrastructure:** Judge0 1.13 · Docker · Nginx · Google Cloud Platform · GitHub Actions (OIDC)

## Architecture Highlights

- **4 microservices** with clean ownership boundaries — Gateway, Auth, App, Execution
- **gRPC for internal sync calls**, REST for the edge, REST + webhook for the Judge0 integration
- **Polyglot persistence** — MongoDB for user-facing data (users, submissions, problems), PostgreSQL for execution metadata
- **Stateless Judge0 workers** with shared Redis + Postgres for true horizontal scaling
- **JWT auth at the edge**, `X-User-Id` trusted context propagated inward
- **Cgroup v1 fix** documented and automated for the Judge0 hosts

## Repository layout

```
codearena/
├── api-gateway/         # Spring Cloud Gateway
├── auth-service/        # Spring Boot 3.3 / Java 21 / MongoDB
├── app-service/         # Spring Boot 3.3 / Java 21 / MongoDB
├── execution-service/   # Spring Boot 3.3 / Java 21 / Postgres
├── frontend/            # Next.js 15 / TypeScript
├── proto/               # Shared gRPC .proto files (single source of truth)
├── infra/
│   ├── docker/          # docker-compose files for VM 1, VM 2, VM 3
│   ├── gcp/             # provisioning + teardown scripts
│   └── scripts/         # cgroup-v1-fix, seed-problems, etc.
├── http/                # IntelliJ HTTP Client request files
├── docs/                # Architecture, contracts, roadmap, devops, frontend, databases
├── pom.xml              # Maven parent POM
├── Makefile             # `make dev`, `make seed`, `make teardown`
└── docker-compose.local.yml   # (added in Phase 7)
```

## Quick Start

> The local stack is wired up in **Phase 7**. Until then, individual services are runnable in isolation per the roadmap.

```bash
git clone git@github.com:Amitjakhar001/CodeArena.git
cd CodeArena
mvn validate     # Phase 0: confirms parent POM is well-formed
make help        # See available targets
```

## Documentation

- [Architecture Blueprint](docs/architecture.md) — what we're building and why
- [API & gRPC Contracts](docs/contracts.md) — protos, REST endpoints, schemas
- [Implementation Roadmap](docs/roadmap.md) — hour-by-hour build plan
- [Frontend Spec](docs/frontend.md) — Next.js 15 component spec
- [Databases Guide](docs/databases.md) — Mongo + Postgres setup and operation
- [DevOps Guide](docs/devops.md) — Git, GitHub Actions, GCP, demo playbook

## License

MIT
