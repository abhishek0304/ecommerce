# Database migrations and operations verification

The five persistence services now use Flyway, with Hibernate `ddl-auto=validate`.
`V1__initial_schema.sql` creates the current schema; `V2__operational_indexes.sql`
adds lookup/recovery indexes. Each service owns its own database and history table.
Checksums are validated at startup, automatic baselining is disabled, and Flyway
clean is disabled. Never edit a migration after it has been applied; add the next
numbered migration. Use expand/contract changes so the previous release can still
run during a rollback. MySQL DDL is not generally transactional: a failed migration
requires inspection of applied statements, not a blind restart or `repair`.

## Existing databases: required adoption procedure

**Do not deploy these changes over a populated unmanaged database without this
procedure.** Startup deliberately fails instead of inventing a baseline. No existing
application database was baselined or modified by the verification tools.

1. Pause checkout/account writes and all workers. Record release/image versions,
   database versions, Kafka offsets and provider reconciliation references.
2. Back up all five databases at the same application-quiesced boundary and verify
   restoration on an isolated MySQL instance. Per-database transaction snapshots
   alone do not guarantee consistency across services.
3. Restore a clone and compare its schema to that service's V1 SQL: columns, types,
   nullability, keys, foreign keys and indexes. Bring older schemas to V1 with a
   reviewed, data-preserving upgrade script. In particular, order snapshots/outbox
   payloads must support LONGTEXT and the shipping tables/columns must exist.
   Do not run V1 CREATE statements against populated tables.
4. Only after that comparison succeeds, explicitly run Flyway `baseline` at version
   **1** on the clone, then `migrate` and `validate` using that service's migration
   directory. Supply the URL/user/password through secure Flyway environment
   configuration, not tracked files. Start the service with Hibernate validation.
5. Rehearse the application workflow and rollback on the clone. Repeat the approved
   procedure on the target during the maintenance window. Keep automatic baseline
   disabled in the application. Never use `clean` on an application database.

Fresh empty databases need no baseline command; application startup applies V1 and
V2. The Config Server repository was updated too; publish that repository's change
before using the remote configuration profile. A locally edited config checkout
does not update a remotely hosted Config Server repository.

Flyway's [baseline behavior](https://documentation.red-gate.com/fd/flyway-baseline-on-migrate-setting-277578974.html)
explains why enabling automatic baseline can hide a wrong-database selection.

## Backups and restore checks

Requirements: MySQL 8 client tools on PATH; `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_USER`
and `MYSQL_PWD` supplied securely to the process. Backups contain customer data.
Use an encrypted, access-controlled destination and a separate off-host copy.
The scripts do not configure encryption, remote storage or retention for you.

```powershell
# Pause all application writers first, including scheduled workers.
python scripts/mysql-backup.py backup --database orderdb --output .backups/orderdb.sql --writers-paused
# Use a separate isolated restore server/account through MYSQL_* variables.
python scripts/mysql-backup.py restore-check --backup .backups/orderdb.sql
```

Backup uses a transactional dump, includes routines/triggers/events, writes a SHA-256
manifest, and compares table content/schema before and after. Restore-check verifies
the file hash, creates a unique `ec_restore_*` database, imports the trusted dump,
compares every table's row counts/content digest and CREATE TABLE digest, then drops
only that newly created database. It refuses to overwrite backup files. Do not feed
it untrusted SQL; dumps can contain executable objects. It does not verify routine
or event behavior. Run production restore drills on an isolated instance, particularly
when events or triggers exist. Inventory hashing holds each table in memory and is
intended for bounded drills; use chunked primary-key verification for large datasets.

`python scripts/verify-mysql-operations.py` checks all five current schemas using
synthetic data, including Unicode, binary values, decimals, NULLs and tampered files.
It writes `.deployment-evidence/mysql-restore-drill.json`. This is not evidence of
production-data restoration. The catalog capacity drill additionally restores real
seeded product rows and Flyway history.

Before launch, agree RPO/RTO with the operator, configure scheduled encrypted
backups and binlog/PITR if the RPO requires it, define retention and off-host storage,
and time a full-system restore at representative data volume. Database dumps alone
do not back up Kafka retained events, provider state, uploaded assets, secrets or
deployment configuration. Redis is currently a disposable product cache.

## Monitoring and alerts

The container profile exposes Prometheus on the separate management listener
(`9090`), not the business listener. Compose keeps management ports unpublished;
Kubernetes keeps them inside the namespace. Do not route `/actuator` publicly.
`MetricsSecurity` permits GET scrapes only on the configured management port.

```powershell
docker compose --profile app --profile observability up -d prometheus alertmanager alert-receiver
kubectl kustomize infra/k8s/observability
```

Prometheus: `http://localhost:9091`; Alertmanager: `http://localhost:9093`;
local received-alert evidence: `http://localhost:9094/alerts` for Compose.
Grafana has a Prometheus datasource alongside Tempo. The Kubernetes overlay adds
Prometheus/Alertmanager persistent volume claims and an internal receiver service.
Cluster storage provisioning and real scrape reachability still need deployment
verification. These single-replica monitoring components are not highly available.

Rules cover unavailable scrapes, 5xx ratios, p95 latency, heap pressure, background
work older than 15 minutes and stale queue-metric collection. User/order/notification
services sample backlog metrics every 30 seconds. UNKNOWN shipping/delivery outcomes
need operator inspection; do not automatically resend them to silence an alert.
Terminal failed notifications remain visible and require an operational resolution
policy. Prometheus data here is not a complete database/Kafka/host monitoring system;
add infrastructure exporters and external HTTPS checks for the chosen hosting target.

The default Alertmanager receiver is **a local drill sink**: it proves HTTP delivery
and retains the last 100 event names/statuses in memory. It sends no email, SMS or
on-call notification. Replace its route with the approved production receiver and
secure credentials before calling on-call delivery operational.

```powershell
docker run --rm --entrypoint promtool -v "${PWD}/infra/monitoring:/etc/prometheus:ro" prom/prometheus:v3.5.0 check config /etc/prometheus/prometheus.yml
promtool test rules infra/monitoring/alerts.test.yml
amtool check-config infra/monitoring/alertmanager.yml
python scripts/verify-monitoring.py --prometheus PATH_TO_PROMETHEUS --alertmanager PATH_TO_ALERTMANAGER
```

Run config validation inside the container with `/etc/prometheus` mounted, or make
a temporary local copy with absolute rule paths. The integration drill uses synthetic
metrics, loopback processes and shortened delays, and verifies firing **and resolved**
delivery. The promtool rule tests use production delays. See the official
[alerting rule documentation](https://prometheus.io/docs/prometheus/latest/configuration/alerting_rules/).

## Recovery runbook

| Failure | Action and success evidence |
|---|---|
| Database unavailable | Pause write traffic; inspect storage/connections. Restore connectivity first. Require readiness, Hibernate validation and a read/write smoke check before reopening. Never switch to `ddl-auto=update`. |
| Migration failure | Stop rollout. Inspect Flyway history and partial DDL on a clone. Restore/reconcile and apply a reviewed fix; use `repair` only after the physical schema has been verified. |
| Product/cart outage during checkout | Restore the dependency, then let durable order recovery resume. Retry the original checkout idempotency key. Verify one stock reservation/commit and one order. |
| Kafka unavailable | Restore Kafka and its storage. Inspect order outbox backlog and consumer lag; verify queued events reach notification inboxes once. Do not truncate the outbox or reset offsets to hide backlog. |
| Refund pending/failed | Inspect the provider's payment/refund record before retrying. Use the existing admin refund retry where eligible; confirm the same amount and no duplicate refund. |
| Shipping UNKNOWN/BOOKING/CREATED | Inspect carrier records by local order reference. Cancellation/reconciliation is still manual; do not book another shipment to bypass the guard. |
| Notification UNKNOWN | Inspect provider records before any resend. ACCEPTED is not proof of customer receipt. Disabled channels and expired OTPs require different treatment from provider outages. |
| Process restart | Require readiness, unchanged data and migration history, successful gateway reads, and resumed background workers. The local catalog drill verifies this for product-service only. |
| Full restore | Keep workers and external channels off; restore all five databases at the chosen consistency point plus required Kafka/configuration data. Reconcile external payments and shipments, then reopen incrementally. |

The existing order/notification regression suites exercise durable recovery and
duplicate protection with controlled dependencies. They do not establish full-system
recovery time or prove real provider recovery. Record production incident/drill
timestamps, backlog convergence, data reconciliation and operator decisions.

## Load and restart verification

```powershell
python scripts/load-check.py --base-url http://localhost:8081 --concurrency 5 --seconds 30
# First package product-service and api-gateway. Set JAVA_HOME and MYSQL_* securely.
python scripts/verify-local-capacity.py
```

The generic load check is read-only, bounds concurrency to 50 and duration to five
minutes, records p95/error ratio/throughput and fails its exit code above thresholds.
The local capacity drill starts real gateway/product jars on private loopback ports,
uses a new MySQL database, runs 1/5/10 concurrent catalog clients, checks metrics,
stops/restarts product-service, checks data persistence and verifies a catalog backup.
It stops only its own processes and drops only its own database. JSON evidence goes
under `.deployment-evidence`; credentials are not written to the report.

This is a six-product local catalog workload with cache/discovery disabled. It does
not establish checkout capacity, stock contention under peak traffic, multi-replica
behavior, provider rate limits or a production SLA. Repeat on the intended hosting
size with representative data and mixed authenticated workloads, plus a sustained
soak test. Define capacity targets before accepting results. Do not load-test real
payment/shipping operations without a bounded test-account plan.

CI runs H2 regressions, separate real-MySQL migration validation, synthetic restore
checks, the catalog drill, and Prometheus/Alertmanager config/rule validation. The
MySQL migration tests create unique databases, verify idempotent reruns/checksum
rejection and Hibernate schema compatibility, then remove only their fixtures.
