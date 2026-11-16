# Backend Test Strategy — Order Platform

## 1. Purpose

Provide fast, trustworthy feedback on the backend contracts that carry the most
business risk: order creation, persistence, asynchronous event publication, and
consumer idempotency/error handling. The strategy is deliberately API-first;
UI coverage is a separate concern.

## 2. Risk model

| Risk | Impact | Test response |
|---|---|---|
| Order accepted but not persisted | Lost revenue, support load | API smoke + read-only DB projection check |
| Duplicate Kafka delivery creates duplicate projections | Financial/data corruption | Duplicate event test with stable count over time |
| Malformed producer payload crashes/poisons the consumer | Pipeline outage | DLQ test + health check after rejection |
| Schema drift between producer and consumer | Silent data loss | JSON schema validation on API and event payloads |
| Validation regressions return the wrong status/error contract | Clients cannot recover | Negative API + error schema tests |
| Staging/production configuration mismatch | False confidence or accidental prod access | Layered properties, explicit environment selection, GitHub environment secrets |
| Test flakiness from shared state or fixed sleeps | Slow, ignored failures | Unique IDs, read-only DB, Awaitility, parallel-safe clients |

## 3. Test pyramid for this service

```text
              /\
             /  \   localonly event resilience (Kafka in/out, DLQ)
            /----\  e2e (API + Kafka + DB)
           /      \ regression (validation, contract, DB consistency)
          /--------\ smoke (health + create/read lifecycle)
```

- **Smoke** is the minimum post-deploy gate. It must be fast and independent of
  database/Kafka test credentials where possible.
- **Regression** expands coverage of validation, contracts and persistence.
- **E2E** verifies the asynchronous path end to end, including correlation IDs.
- **localonly** exercises failure modes that require direct broker access and
  should not be attempted from every deployed environment.

## 4. Environment strategy

| Environment | Purpose | Data rule |
|---|---|---|
| `local` | Full stack, event tests, debugging | Disposable Docker volumes; builders inject unique IDs |
| `dev` | Shared integration environment | Unique IDs only; read-only DB account; no destructive cleanup |
| `staging` | Post-merge regression and nightly validation | Unique IDs only; read-only DB account; credentials via GitHub environment secrets |

Never point `localonly` tests at a deployed environment. The execution condition
enforces this even if a tag is selected accidentally.

## 5. Data strategy

- Generate IDs and business keys with UUIDs per test.
- Prefix run-level identifiers with `TEST_RUN_ID` (or an automatically generated
  run ID) to distinguish parallel CI runs.
- Query by the unique IDs created in the test; never use `SELECT ... LIMIT 1` or
  table-wide counts in shared environments.
- Do not write to the SUT database from tests. Seed through public APIs or the
  local stack reset script.
- Local-only cleanup is `./scripts/local-stack-down.sh` (removes volumes).
- For staging, rely on unique data plus a TTL/retention process owned by the
  platform; if cleanup is required, implement it as an explicit, environment-
  gated maintenance job, not inside test bodies.

## 6. Asynchronous test rules

- Use Awaitility with a bounded timeout and a short polling interval; configure
  both through properties.
- Assert on a stable identity (event ID, correlation ID, order ID), never on
  "some row appeared".
- For duplicate/idempotency checks, wait for the expected state and then verify
  it remains stable for a configured observation window.
- Consumers must start before the stimulus. The framework seeks to the topic end
  and resolves positions before the test publishes.
- Never use `Thread.sleep`; it both slows the suite and hides ordering bugs.

## 7. Parallelism and isolation

JUnit 5 runs methods and classes concurrently by default, with a configurable
worker count. Isolation is achieved through:

- immutable per-request Rest Assured specs;
- no global Rest Assured base URI mutation;
- one test context per JVM with lazy, bounded DB/Kafka resources;
- unique consumer groups and unique event/order IDs;
- no shared mutable collections or singletons in tests.

The Hikari pool size must be at least the expected number of concurrent DB
assertions. Tune with `DB_POOL_SIZE` and `-Dthreads`.

## 8. Quality gates

A change is ready to merge when:

1. The application module compiles and its own tests pass.
2. The QA module compiles and smoke tests pass against the local stack.
3. Regression and contract tests pass.
4. Event tests pass locally (including duplicate/malformed cases) for changes to
   producer/consumer code or event schemas.
5. The Allure artifact is attached and shows no infrastructure failures.
6. There are no new secrets in source, properties, logs or reports.

Post-merge staging regression is an additional gate; nightly runs provide trend
visibility and catch environmental drift.

## 9. Reporting and triage

- Allure is the investigation surface; Surefire XML is the CI integration
  surface.
- Product defects and infrastructure failures are categorized separately so a
  broker outage is not treated as a code regression.
- Every failure carries the environment, run ID, correlation ID, thread and HTTP
  exchange/SQL/Kafka evidence where available.
- Correlation IDs are propagated from test → HTTP header → order row → event →
  audit row, making distributed tracing possible without a tracing backend.

## 10. Future improvements

- Schema Registry contract tests for event evolution.
- Testcontainers-based ephemeral databases/brokers for PR pipelines.
- Outbox and replay tests once the service adopts a transactional outbox.
- Load/performance tests in a separate module, using the same client layer.
- Flake quarantine dashboard and historical pass-rate SLOs.
- OpenAPI-generated client stubs to prevent model drift.
