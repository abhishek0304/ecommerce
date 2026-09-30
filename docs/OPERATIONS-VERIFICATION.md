# Local operations verification — 2026-10-01

These are local results, not evidence of a production deployment.

- **139 Java tests passed**, with zero failures/errors/skips across six services.
  Coverage includes five real MySQL migration/validation tests, idempotent reruns,
  checksum guards, unmanaged-schema rejection and queue metrics. H2 API tests remain
  separate from MySQL schema validation.
- All five schemas passed synthetic dump/restore comparison: table definitions,
  row counts/content digests and rejection of tampered dumps. Each fixture contains
  two probe rows; these results do not establish production restore time.
- All six Prometheus rules passed tests. Prometheus → Alertmanager → local receiver
  delivered firing and resolved synthetic alerts using shortened integration-test delays.
- Four load-checker regression tests passed, including HTTP failures, incorrect
  JSON and excessive latency. Infrastructure checks and Kubernetes rendering passed.

## Real local catalog workload

Actual gateway → product-service → MySQL, six seeded products, 15 seconds per stage,
Redis/discovery disabled, two JVMs capped at 384 MiB each on the developer machine.

| Concurrent clients | Requests | Errors | p95 ms | Requests/second |
|---|---:|---:|---:|---:|
| 1 | 301 | 0 | 47 | 20.0 |
| 5 | 1,560 | 0 | 47 | 103.8 |
| 10 | 2,937 | 0 | 62 | 195.6 |

Both real management endpoints returned Prometheus HTTP metrics; business ports
rejected metrics access. Stopping product-service caused the expected gateway failure.
Restart restored the same catalog in approximately 20 seconds. Seeded product rows
and Flyway history survived a separate dump/restore check. Fixture processes and
databases were cleaned up; existing application databases were not changed.

## Remaining environment checks

- Existing database baseline adoption and publication of remote Config Server changes.
- Production backup scheduling, encryption/off-host retention, RPO/RTO and full-volume restore.
- Real cluster scrape reachability and approved on-call delivery; the default receiver is local.
- Representative checkout/stock-contention workloads, sustained soak and multi-replica tests.
- Full-system/Kafka/provider recovery drills; a product restart verifies a narrower case.

See [the runbook](OPERATIONS.md) for procedures. Detailed local JSON evidence is
stored in the ignored `.deployment-evidence` directory.
