# Order Platform — Backend Test Automation

A production-shaped backend test automation module for a Java 21 service. The
repository is laid out as a monorepo: the application lives in
`services/order-service`, while all test code lives in `qa/backend-test-automation`
and interacts with the service only over its public contracts (HTTP, PostgreSQL
and Kafka). The QA module does not import application classes.

This is intentionally more than a set of API tests:

- REST Assured clients with typed models, request correlation IDs and redacted
  Allure HTTP attachments.
- Awaitility for every asynchronous assertion.
- Read-only PostgreSQL verification through a HikariCP pool.
- Local Kafka/Redpanda event-driven tests: valid projection, duplicate delivery,
  malformed JSON, missing fields and unsupported event types.
- Layered properties files for `local`, `dev` and `staging`, with CI/secret
  overrides and no credentials in the repository.
- JUnit 5 parallel execution with thread-safe clients, unique data and a
  configurable worker count.
- Reusable GitHub Actions workflows for post-merge staging regression, nightly
  runs and ad-hoc/test-selection runs.
- Allure reports enriched with environment metadata, safe HTTP exchanges, SQL
  results, Kafka messages and failure diagnostics.

---

## 1. Monorepo layout

```text
order-platform/
├── pom.xml                              # aggregator; no QA dependency on the app
├── docker-compose.yml                   # local Postgres + Redpanda + service
├── services/
│   └── order-service/                   # reference application module (SUT)
│       ├── Dockerfile
│       └── src/main/...
├── qa/
│   └── backend-test-automation/         # this framework, reusable as a module
│       ├── pom.xml
│       ├── src/main/java/...            # framework: config, clients, db, kafka, reporting
│       └── src/test/java/...            # suite: smoke, regression, e2e, event
├── scripts/
│   ├── local-stack-up.sh
│   ├── local-stack-down.sh
│   ├── wait-for-health.sh
│   └── run-backend-tests.sh             # single CI entry point
├── .github/workflows/
│   ├── _backend-tests.yml               # reusable runner
│   ├── backend-regression.yml           # post-merge staging
│   ├── backend-nightly.yml              # daily staging + local event suite
│   └── backend-adhoc.yml                # workflow_dispatch with tag presets
└── docs/test-strategy.md
```

The `order-service` module is a small reference SUT included so the local and
event-driven suites are immediately runnable. To use this framework against the
real monorepo service, keep `qa/backend-test-automation` as a sibling module,
update the paths in `.github/workflows`, and adapt the models/endpoints/topics in
the framework. Nothing in the test module depends on Spring or application code.

---

## 2. Local quick start

Prerequisites: JDK 21, Maven 3.9+, Docker with Compose v2.

```bash
./scripts/local-stack-up.sh          # Postgres + Redpanda + order-service
mvn -f qa/backend-test-automation/pom.xml test -Plocal
mvn -f qa/backend-test-automation/pom.xml allure:report
open qa/backend-test-automation/target/site/allure-maven-plugin/index.html
```

`local-stack-up.sh` waits for the API health endpoint. Redpanda Console is at
<http://localhost:8081> and the API is at <http://localhost:8080>.

```bash
./scripts/local-stack-down.sh        # removes the containers and volumes
```

### What the local stack contains

| Component | Address | Purpose |
|---|---|---|
| order-service | `http://localhost:8080` | REST API and Kafka consumer |
| PostgreSQL | `localhost:5432/orders` | `orders` and `order_event_audit` |
| Redpanda | `localhost:9092` | `order-events.v1`, `order-events.v1.dlq` |
| Redpanda Console | `http://localhost:8081` | inspect topics and messages |

The local Compose credentials are intentionally dev-only. They are never reused
outside the local environment.

---

## 3. Test selection — tags and Maven profiles

Every suite has a JUnit 5 tag. Tags are the source of truth; Maven profiles are
just convenient aliases.

| Tag | Meaning |
|---|---|
| `smoke` | Fast, critical-path checks; safe for a post-deploy gate |
| `regression` | Broader API, contract, database and event coverage |
| `e2e` | Cross-layer journeys, including API → Kafka → DB |
| `localonly` | Requires local Kafka/Redpanda and the local stack; auto-skips elsewhere |
| `event` | Kafka/event-driven capability |
| `db` | PostgreSQL assertions |
| `contract` | JSON schema/contract checks |
| `api` | HTTP-layer tests |

```bash
# Default: all non-localonly tests against the local environment
mvn -f qa/backend-test-automation/pom.xml test

# Convenience profiles
mvn -f qa/backend-test-automation/pom.xml test -Psmoke
mvn -f qa/backend-test-automation/pom.xml test -Pregression
mvn -f qa/backend-test-automation/pom.xml test -Pe2e -DexcludedGroups=
mvn -f qa/backend-test-automation/pom.xml test -Pcontract
mvn -f qa/backend-test-automation/pom.xml test -Pdb
mvn -f qa/backend-test-automation/pom.xml test -Pevent -DexcludedGroups=

# Local event-driven suite (duplicates, malformed JSON, DLQ, schema failures)
mvn -f qa/backend-test-automation/pom.xml test -Plocalonly

# Everything, including localonly, against local
mvn -f qa/backend-test-automation/pom.xml test -Plocal
# or
mvn -f qa/backend-test-automation/pom.xml test -Pall
```

Direct JUnit tag expressions work too:

```bash
mvn -f qa/backend-test-automation/pom.xml test \
  -Dgroups="smoke | (regression & !event)" \
  -DexcludedGroups="localonly"

# Against a deployed environment
mvn -f qa/backend-test-automation/pom.xml test \
  -Denv=staging \
  -Dgroups="smoke | regression" \
  -DexcludedGroups="localonly"
```

`localonly` is excluded by default. `-Plocal`, `-Pall`, `-Plocalonly` and
`-Pe2e -DexcludedGroups=` clear that exclusion. The `LocalOnly` execution
condition still disables the tests on `dev`/`staging` so selecting the tag in an
ad-hoc staging run produces skipped tests, not false infrastructure failures.

### Parallel execution

```bash
# Default: 4 JUnit workers
mvn -f qa/backend-test-automation/pom.xml test -Plocal

# Configurable
mvn -f qa/backend-test-automation/pom.xml test -Plocal -Dthreads=8
mvn -f qa/backend-test-automation/pom.xml test -Plocal -Dparallel=false
```

`junit-platform.properties` provides IDE defaults. Surefire passes `threads` and
`parallel` as JUnit platform system properties, so CI and local runs use the
same mechanism. Tests are safe in parallel because:

- no mutable static Rest Assured state is used; every request gets a fresh spec;
- each test gets a unique correlation ID and unique data;
- database access is read-only and queries by unique identifiers;
- each Kafka assertion creates a unique consumer group and starts at the topic
  end before publishing;
- Awaitility is configured to poll on the test thread.

---

## 4. Environments and properties

Configuration is loaded from classpath properties files in this order, with the
later source winning:

1. `config/default.properties`
2. `config/<env>.properties`
3. environment variables (`API_BASE_URI`, `DB_URL`, ...)
4. JVM system properties (`-Dapi.base.uri=...`)

The active environment is selected by `-Denv=local|dev|staging` or `TEST_ENV`.
The default is `local`.

```text
qa/backend-test-automation/src/test/resources/config/
├── default.properties
├── local.properties
├── dev.properties
└── staging.properties
```

Common environment variables:

| Variable | Maps to | Notes |
|---|---|---|
| `API_BASE_URI` | `api.base.uri` | Absolute http(s) URI |
| `API_BASE_PATH` | `api.base.path` | Default `/api/v1` |
| `API_TOKEN` | `api.token` | Optional bearer token; never committed |
| `API_TIMEOUT_MS` | `api.timeout.ms` | Connection/socket timeout |
| `API_TLS_INSECURE` | `api.tls.insecure` | Only for trusted test environments |
| `DB_URL` | `db.url` | PostgreSQL JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `db.username` / `db.password` | Use a read-only account in shared environments |
| `DB_POOL_SIZE` | `db.pool.size` | Hikari pool size |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka.bootstrap.servers` | Broker list |
| `KAFKA_SECURITY_PROTOCOL` | `kafka.security.protocol` | e.g. `SASL_SSL` |
| `KAFKA_SASL_MECHANISM` | `kafka.sasl.mechanism` | e.g. `SCRAM-SHA-512` |
| `KAFKA_SASL_USERNAME` / `KAFKA_SASL_PASSWORD` | `kafka.sasl.*` | CI secrets only |
| `WAIT_TIMEOUT_SECONDS` | `wait.timeout.seconds` | Awaitility default budget |
| `TEST_RUN_ID` | run prefix | Optional CI override; unique by default |

The framework never logs tokens, DB passwords or raw authorization headers. The
Allure HTTP filter redacts `Authorization`, cookies, API-key headers and common
secret query parameters.

`dev.properties` and `staging.properties` contain placeholders only. Configure
real values as GitHub **environment** variables and secrets (see section 7).

To add another environment (for example `uat`), add `config/uat.properties`, a
`TestEnvironment` enum value, a GitHub environment named `uat`, and `uat` to the
ad-hoc environment choices. No test code changes are required.

---

## 5. Database verification

The test DB pool is:

- lazy — API smoke tests do not need DB access;
- read-only — there is no test code path that mutates the SUT schema or data;
- bounded by `db.pool.size` and `db.connection.timeout.ms`;
- used only through `Database` / `OrderDbRepository`; tests never build JDBC
  connections or leak SQL into test classes.

Example checks:

- `orders` row exists with the expected customer, amount, currency, status and
  correlation ID;
- exactly one order exists for the test correlation ID;
- `order_event_audit` has exactly one row for an event ID after a duplicate
  delivery;
- the projected payload contains the original event ID.

All eventual-consistency checks use `Waits.await(config)` (Awaitility). Failures
attach the final SQL result to Allure. For shared environments, always query by
unique IDs generated in the run; never truncate or update shared tables.

---

## 6. Event-driven local tests

The reference service publishes `order.created.v1` on `order-events.v1` after a
successful create, then consumes that topic and writes an idempotent projection
to `order_event_audit`. Rejected messages go to `order-events.v1.dlq` with an
envelope containing the original payload, error type and correlation ID.

The framework provides:

- `KafkaSupport` — thread-safe producer, admin topic preflight, unique consumers.
- `KafkaEventConsumer` — starts at topic end (or beginning), polls with a
  deadline, filters by event ID/correlation ID and attaches matched messages to
  Allure.
- `EventPayloads` — raw JSON builders for valid, malformed and schema-invalid
  events.
- `EventDrivenLocalTest` — five resilience scenarios:
  1. valid injected event is projected exactly once;
  2. duplicate event ID is idempotent (stable count check over a 2-second window);
  3. malformed JSON reaches the DLQ and the service remains healthy;
  4. missing required fields reach the DLQ as `VALIDATION_ERROR`;
  5. unsupported event type reaches the DLQ as `UNSUPPORTED_EVENT_TYPE`.
- `OrderEventE2ETest` — creates an order through HTTP, observes the event on the
  topic, validates its schema, then waits for the consumer's DB projection.

Event tests are tagged `localonly` and require the local stack. They are skipped
automatically on dev/staging, where Kafka may not be exposed to test runners.

```bash
./scripts/local-stack-up.sh
mvn -f qa/backend-test-automation/pom.xml test -Plocalonly
```

---

## 7. CI/CD workflows

| Workflow | Trigger | What it runs |
|---|---|---|
| `backend-regression.yml` | push to `main` touching service/QA paths (after merge) | `smoke \| regression \| e2e` against staging, localonly excluded |
| `backend-nightly.yml` | daily 02:00 UTC (and manual) | staging full regression plus a local-stack event-driven suite |
| `backend-adhoc.yml` | manual `workflow_dispatch` | chosen environment + tag preset + thread count + parallel toggle |
| `_backend-tests.yml` | reusable | checkout, optional local stack, preflight, Maven, Allure, artifacts, checks, teardown |

### Ad-hoc selections

The manual workflow exposes:

- `environment`: `local`, `dev`, `staging`
- `tag_preset`: `smoke`, `regression`, `e2e`, `smoke+regression`, `all`,
  `localonly`, `custom`
- `tag_expression`: full JUnit expression when `custom` is selected
- `threads`: 1–16
- `parallel`: true/false
- `ref`: branch/tag/SHA to test
- `notify_slack`: optional

`localonly` requires `environment=local`. `all` on staging excludes nothing at
the Maven level, but the `LocalOnly` condition marks event tests as skipped.

### GitHub environment configuration

Create GitHub environments named `dev` and `staging` (and optionally `local`).
For each remote environment define:

**Variables**

| Name | Example |
|---|---|
| `API_BASE_URI` | `https://staging-api.example.com` |
| `HEALTH_URL` | `https://staging-api.example.com/actuator/health` |
| `DB_URL` | `jdbc:postgresql://staging-db:5432/orders?sslmode=require` |
| `DB_USERNAME` | `qa_readonly` |
| `KAFKA_BOOTSTRAP_SERVERS` | `staging-kafka:9092` |
| `KAFKA_SECURITY_PROTOCOL` | `SASL_SSL` (optional) |
| `KAFKA_SASL_MECHANISM` | `SCRAM-SHA-512` (optional) |
| `API_TLS_INSECURE` | `false` (do not enable for production-like systems) |

**Secrets**

| Name | Purpose |
|---|---|
| `API_TOKEN` | Optional bearer token |
| `DB_PASSWORD` | Read-only QA DB password |
| `KAFKA_SASL_USERNAME` / `KAFKA_SASL_PASSWORD` | Optional broker credentials |
| `SLACK_WEBHOOK` | Optional failure notification |

The reusable workflow sets `environment: ${{ inputs.environment }}` on its job,
so environment-scoped GitHub variables/secrets are available without duplicating
workflow logic per environment. Do not attach a manual-approval protection rule
to the environment used for automatic post-merge regression, or use a separate
`staging-auto` environment for that workflow.

> **Deployment ordering:** the regression workflow runs on merge to `main`. If
> your deploy is a separate workflow, use the `workflow_run` trigger shown at
> the top of `backend-regression.yml` so regression starts only after a
> successful staging deployment. The optional `HEALTH_URL` wait already guards
> against a not-yet-ready service.

### Security and reliability in CI

- Least-privilege `permissions` on every workflow.
- Concurrency groups prevent overlapping staging/nightly runs.
- Timeouts on the reusable job.
- Local stack logs captured on failure and uploaded.
- Allure raw results and HTML report uploaded for every run.
- JUnit XML published as a GitHub check (`EnricoMi/publish-unit-test-result-action`).
- Optional Slack notification on failure.
- Dependabot keeps Maven and Actions dependencies current.

---

## 8. Reporting

Allure is the primary report. Surefire XML is kept as the CI-native contract.

```bash
mvn -f qa/backend-test-automation/pom.xml test -Plocal
mvn -f qa/backend-test-automation/pom.xml allure:report
# qa/backend-test-automation/target/site/allure-maven-plugin/index.html
```

Use `mvn clean test` for a report containing only the current run; Allure results
are append-only between Maven invocations.

The report includes:

- `environment.properties` — environment, redacted API/DB/Kafka endpoints, run
  ID, Java/OS, git commit;
- HTTP request/response attachments with credentials redacted and bodies capped;
- SQL query result attachments on DB assertion failures;
- Kafka publish/consume attachments with topic, partition, offset and payload;
- failure diagnostics with thread, correlation ID and test identity;
- categories for product defects, infrastructure and environment-gated skips;
- JUnit 5 display names, epics, features, stories, severity and owner labels.

CI uploads both `target/allure-results` (raw) and the generated HTML directory.
That allows a historical re-generation of the report later, not just a static
artifact.

---

## 9. Framework structure and guardrails

```text
qa/backend-test-automation/src/main/java/com/orderplatform/qa/
├── api/
│   ├── client/      # OrderClient, HealthClient, ApiResponse, ContractAssertions
│   ├── model/       # API DTOs used by tests
│   └── spec/        # per-request specs + sanitized Allure/HTTP filter
├── config/          # TestConfig, TestEnvironment, ConfigLoader
├── db/              # Database, OrderDbRepository, DatabaseAssertions
├── event/           # KafkaSupport, KafkaEventConsumer, payload factories
├── report/          # Allure environment writer, failure diagnostics
├── test/            # lazy TestContext
└── util/            # Json, Waits, TestIds, data factory
```

Test code is in `src/test/java`, layered as `base/`, `tags/`, `api/`, `db/` and
`event/`.

Non-negotiable conventions:

- No `Thread.sleep`; asynchronous assertions use Awaitility with a bounded
  timeout.
- No Rest Assured `given()` in test classes; all HTTP goes through clients.
- No JDBC in test classes; all DB reads go through repositories.
- No secrets or tokens in properties, logs, reports or source.
- No static mutable test data; unique IDs are generated per test/run.
- New tests must carry at least one suite tag (`smoke`, `regression`, `e2e` or
  `localonly`) plus any capability tags.
- Contract changes require updating the JSON schema and the typed models in the
  same change.

---

## 10. Test inventory

| Suite | Class | Tags |
|---|---|---|
| Health | `HealthSmokeTest` | smoke, regression, api |
| Order lifecycle | `OrderLifecycleSmokeTest` | smoke, regression, e2e, api |
| Validation and error contract | `OrderValidationRegressionTest` | regression, api |
| JSON schemas | `OrderContractTest` | regression, contract, api |
| DB persistence | `OrderPersistenceRegressionTest` | regression, db, api |
| API → Kafka → consumer → DB | `OrderEventE2ETest` | e2e, regression, event, localonly |
| Duplicate / malformed / invalid event | `EventDrivenLocalTest` | regression, event, localonly |

---

## 11. Troubleshooting

| Symptom | Check |
|---|---|
| `Cannot reach the test database` | `./scripts/local-stack-up.sh`; then `docker compose ps` |
| `Kafka is not reachable` / missing topics | `docker compose logs redpanda redpanda-init` |
| `localonly` tests skipped | environment must be `local`; use `-Plocal`, `-Plocalonly` or `-DexcludedGroups=` |
| Staging smoke fails before tests run | `API_BASE_URI` / `HEALTH_URL` vars in the GitHub environment |
| DB tests fail with auth errors | `DB_PASSWORD` secret and read-only user grants |
| Allure report missing environment info | run tests first; `allure:report` reads `target/allure-results` |
| Docker build fails on root pom modules | the service Dockerfile builds the service as a standalone Maven project |

---

## 12. Further hardening for a real platform

This reference intentionally keeps a few things simple. For a production
platform, consider:

- transactional outbox for service event publication instead of synchronous send;
- Confluent Schema Registry / Avro or Protobuf contracts for events;
- Testcontainers or ephemeral namespaces for isolated local/PR stacks;
- test-data TTL and cleanup jobs for long-lived staging databases;
- quarantine/flake tracking and trend dashboards in Allure TestOps or an
  equivalent;
- coverage gates for critical API/event paths;
- contract tests generated from OpenAPI and event schemas;
- SAST/DAST and dependency scanning in the same pipeline;
- Page/UI e2e tests as a separate module consuming the same clients where useful.

See `docs/test-strategy.md` for the risk model and exit criteria behind these
choices.
