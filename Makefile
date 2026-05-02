.PHONY: help dev logs seed test teardown clean validate \
        gcloud-install gcp-preflight \
        judge0-provision judge0-deploy judge0-tunnel judge0-ssh judge0-stop judge0-teardown \
        execution-db-up execution-db-down execution-db-reset execution-run execution-build \
        app-db-up app-db-down app-db-reset app-run app-build app-seed

GCP_PROJECT_ID ?=
GCP_ZONE       ?= asia-south1-a

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-22s\033[0m %s\n", $$1, $$2}'

validate: ## Validate the parent POM and module wiring
	mvn -B validate

# ─── Phase 2: Judge0 on GCP VM 2 (Path A) ──────────────────────────────
gcloud-install: ## Phase 2: install gcloud CLI on Fedora (one-time)
	bash infra/gcp/install-gcloud-fedora.sh

gcp-preflight: ## Phase 2: enable GCP APIs (requires GCP_PROJECT_ID)
	@test -n "$(GCP_PROJECT_ID)" || (echo "Set GCP_PROJECT_ID — see infra/gcp/setup-preflight.sh" && exit 1)
	GCP_PROJECT_ID=$(GCP_PROJECT_ID) bash infra/gcp/setup-preflight.sh

judge0-provision: ## Phase 2: provision GCP VM 2 with Docker + cgroup v1 fix
	@test -n "$(GCP_PROJECT_ID)" || (echo "Set GCP_PROJECT_ID" && exit 1)
	GCP_PROJECT_ID=$(GCP_PROJECT_ID) GCP_ZONE=$(GCP_ZONE) bash infra/gcp/provision-vm2-early.sh

judge0-deploy: ## Phase 2: scp compose + start Judge0 stack on VM 2
	@test -n "$(GCP_PROJECT_ID)" || (echo "Set GCP_PROJECT_ID" && exit 1)
	GCP_PROJECT_ID=$(GCP_PROJECT_ID) GCP_ZONE=$(GCP_ZONE) bash infra/gcp/deploy-judge0-vm2.sh

judge0-tunnel: ## Phase 2: open IAP tunnel localhost:2358 -> VM 2 (foreground)
	gcloud compute start-iap-tunnel codearena-vm2 2358 \
		--local-host-port=localhost:2358 \
		--zone=$(GCP_ZONE)

judge0-ssh: ## Phase 2: SSH into VM 2 via IAP
	gcloud compute ssh codearena-vm2 --zone=$(GCP_ZONE) --tunnel-through-iap

judge0-stop: ## Phase 2: stop Judge0 containers on VM 2 (data preserved)
	gcloud compute ssh codearena-vm2 --zone=$(GCP_ZONE) --tunnel-through-iap \
		--command="cd ~ && sudo docker compose -f docker-compose.judge0.yml down"

judge0-teardown: ## Phase 2: DESTRUCTIVE — delete VM 2 + VPC (stops billing)
	@test -n "$(GCP_PROJECT_ID)" || (echo "Set GCP_PROJECT_ID" && exit 1)
	GCP_PROJECT_ID=$(GCP_PROJECT_ID) GCP_ZONE=$(GCP_ZONE) bash infra/gcp/teardown-vm2.sh

# ─── Phase 3: Execution Service ────────────────────────────────────────
execution-db-up: ## Phase 3: start Postgres for the Execution Service
	docker compose -f infra/docker/docker-compose.execution.yml up -d

execution-db-down: ## Phase 3: stop Postgres (data preserved)
	docker compose -f infra/docker/docker-compose.execution.yml down

execution-db-reset: ## Phase 3: stop Postgres AND wipe volume (forces init.sql to re-run)
	docker compose -f infra/docker/docker-compose.execution.yml down -v

execution-build: ## Phase 3: compile execution-service (regenerates gRPC stubs)
	mvn -B -pl execution-service -am package -DskipTests

execution-run: ## Phase 3: run execution-service locally (requires DB up + judge0 tunnel)
	mvn -pl execution-service spring-boot:run

# ─── Phase 4: App Service ──────────────────────────────────────────────
app-db-up: ## Phase 4: start MongoDB for the App Service
	docker compose -f infra/docker/docker-compose.app.yml up -d

app-db-down: ## Phase 4: stop MongoDB (data preserved)
	docker compose -f infra/docker/docker-compose.app.yml down

app-db-reset: ## Phase 4: stop MongoDB AND wipe volume (forces init.js to re-run)
	docker compose -f infra/docker/docker-compose.app.yml down -v

app-build: ## Phase 4: compile app-service (regenerates gRPC stubs)
	mvn -B -pl app-service -am package -DskipTests

app-run: ## Phase 4: run app-service locally (requires Mongo + auth-service + execution-service up)
	mvn -pl app-service spring-boot:run

app-seed: ## Phase 4: seed ~10 sample problems into app_db (idempotent)
	mongosh "mongodb://localhost:27018/app_db" infra/scripts/seed-problems.js

# ─── Future phases (placeholders) ──────────────────────────────────────
dev: ## TODO(phase-7): bring up the full local stack via docker compose
	@echo "Not implemented yet — wired up in Phase 7."

logs: ## TODO(phase-7): tail logs for all services
	@echo "Not implemented yet — wired up in Phase 7."

seed: app-seed ## Phase 4: alias for app-seed

test: ## TODO(phase-1+): run all module tests
	@echo "Not implemented yet — wired up per service from Phase 1 onwards."

teardown: ## TODO(phase-7): docker compose down (preserves volumes)
	@echo "Not implemented yet — wired up in Phase 7."

clean: ## TODO(phase-7): docker compose down -v + mvn clean (DESTROYS data)
	@echo "Not implemented yet — wired up in Phase 7."
