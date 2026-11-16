SHELL := /bin/bash
QA := qa/backend-test-automation/pom.xml

.PHONY: help up down clean smoke regression e2e local localonly all report adhoc compile

help:
	@echo "Order Platform local commands"
	@echo "  make up          Start Postgres + Redpanda + order-service"
	@echo "  make down        Stop and remove the local stack and volumes"
	@echo "  make clean       Remove Maven output and Allure results"
	@echo "  make smoke       Smoke suite (API only, local stack)"
	@echo "  make regression  Regression suite against the local stack"
	@echo "  make e2e         E2E suite (includes localonly event e2e when stack is up)"
	@echo "  make local       Every suite including localonly event tests"
	@echo "  make localonly   Event-driven duplicate/malformed/error-path tests"
	@echo "  make all         Same as local (all tags, localonly included)"
	@echo "  make report      Generate the Allure HTML report"
	@echo "  make compile     Compile app + tests without running tests"

up:
	./scripts/local-stack-up.sh

down:
	./scripts/local-stack-down.sh

clean:
	mvn -B -f $(QA) clean

smoke:
	mvn -B -f $(QA) test -Psmoke

regression:
	mvn -B -f $(QA) test -Pregression

e2e:
	mvn -B -f $(QA) test -Pe2e -DexcludedGroups=

local:
	mvn -B -f $(QA) test -Plocal

localonly:
	mvn -B -f $(QA) test -Plocalonly

all:
	mvn -B -f $(QA) test -Pall

report:
	mvn -B -f $(QA) allure:report

adhoc:
	./scripts/run-backend-tests.sh

compile:
	mvn -B -DskipTests test-compile
