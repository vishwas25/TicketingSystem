SHELL := /bin/bash
COMPOSE := docker compose
MVN := ./mvnw

.DEFAULT_GOAL := help
.PHONY: help up down restart ps logs psql redis-cli verify test-unit test-it build run

help:
	@printf '%s\n' 'make up          Start PostgreSQL and Redis; wait for health checks' \
	  'make down        Stop the stack; retain database volume' \
	  'make restart     Restart the stack' \
	  'make ps          Show stack status' \
	  'make logs        Follow stack logs' \
	  'make psql        Open the development database' \
	  'make redis-cli   Open the Redis shell' \
	  'make verify      Compile, unit tests, and real PostgreSQL integration tests' \
	  'make test-unit   Unit tests only; Docker not required' \
	  'make test-it     Integration tests only; Docker required' \
	  'make build       Package the application without tests' \
	  'make run         Run locally on port 8081'

up:
	$(COMPOSE) up -d --wait

down:
	$(COMPOSE) down

restart: down up

ps:
	$(COMPOSE) ps

logs:
	$(COMPOSE) logs -f --tail=100

psql:
	$(COMPOSE) exec postgres psql -U ticketing -d ticketing

redis-cli:
	$(COMPOSE) exec redis redis-cli

verify:
	cd backend && $(MVN) -B verify

test-unit:
	cd backend && $(MVN) -B test

test-it:
	cd backend && $(MVN) -B verify -DskipUnitTests=true

build:
	cd backend && $(MVN) -B package -DskipTests

run:
	set -a; if [[ -f .env ]]; then source .env; fi; set +a; \
	  cd backend && $(MVN) spring-boot:run -Dspring-boot.run.profiles=local