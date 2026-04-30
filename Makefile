.PHONY: help dev logs seed test teardown clean validate

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

validate: ## Validate the parent POM and module wiring
	mvn -B validate

dev: ## TODO(phase-7): bring up the full local stack via docker compose
	@echo "Not implemented yet — wired up in Phase 7."

logs: ## TODO(phase-7): tail logs for all services
	@echo "Not implemented yet — wired up in Phase 7."

seed: ## TODO(phase-4): seed sample problems into app_db
	@echo "Not implemented yet — wired up in Phase 4."

test: ## TODO(phase-1+): run all module tests
	@echo "Not implemented yet — wired up per service from Phase 1 onwards."

teardown: ## TODO(phase-7): docker compose down (preserves volumes)
	@echo "Not implemented yet — wired up in Phase 7."

clean: ## TODO(phase-7): docker compose down -v + mvn clean (DESTROYS data)
	@echo "Not implemented yet — wired up in Phase 7."
