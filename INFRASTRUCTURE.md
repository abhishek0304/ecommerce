# Ecommerce infrastructure

The project includes eight Spring services, separate MySQL databases, Kafka, Redis,
circuit breakers, Docker, Kubernetes, CI/CD, and OpenTelemetry tracing with Tempo and a local Grafana UI.
Actuator health checks and structured console logs remain available.

## Local startup

Run commands from D:\spring projects\ecommerce using PowerShell and Docker Desktop
with Linux containers. On an 8 GB PC, stop duplicate IntelliJ service processes and use:

```powershell
.\scripts\start-core.ps1
```

This starts databases and backend services sequentially, waits for health, and leaves
the optional Tempo/Grafana services and unused container Config Server stopped. Use
`.\scripts\start-core.ps1 -WithTracing` to enable tracing. It preserves
all database volumes. The complete backend still needs several GB of available RAM.

For machines with sufficient memory, start all application services:

```powershell
docker compose --profile app up -d --build --wait --wait-timeout 600
```

The gateway is at http://localhost:8081. Routes include /api/v1/products, /api/auth/**,
/api/users/**, /api/addresses, /api/cart, /api/orders, and /api/notifications.
Product creation, editing, and deletion require an admin JWT.

Generate credentials only if .env does not exist:

```powershell
.\scripts\init-env.ps1
```

The script refuses to overwrite existing credentials. Add Razorpay test keys and webhook
secret to .env for online payments. COD does not need these keys. Keep .env private.
Changing an initialized database's password in .env does not rotate its MySQL account.

Each domain owns its database and volume: userdb, productdb, cartdb, orderdb, notificationdb.
The Compose databases are separate from any MySQL databases previously used by IDE services.
They are not automatically migrated or deleted. Development limits are 384 MB per MySQL
container with a 64 MB buffer pool and disabled performance schema; application containers
are capped at 512 MB with small connection pools.

Kafka uses localhost:9092 for IDE clients and kafka:19092 for containers.
The old Kafka-only setup did not declare persistent storage. Preserve needed queued events
before replacing such an older container with the new persistent-volume configuration.

## Cache and resilience

Product-by-ID reads use Redis keys containing product ID and the current JPA version.
A database version query remains on each read; inventory/admin changes make older entries
unusable. Entries expire after two minutes. Redis timeouts are 300 ms, with database fallback.
Product lists, inventory writes, carts, authentication, and orders are not cached.
Existing IDE runs leave caching disabled unless PRODUCT_CACHE_ENABLED=true is configured.

Cart and order HTTP calls, including Razorpay, use circuit breakers per downstream authority:
20-call window, minimum 10 calls, 50% failure threshold, 30-second open interval, and three
half-open probes. Network errors, HTTP 5xx and 429 count as failures. Other business 4xx
do not. Existing durable order recovery handles dependency failures; the breaker does
not add automatic payment/refund retries.

## Health, logs, and optional tracing

Actuator health/info use internal management port 9090. Health probes remain in Compose
and Kubernetes. They are not exposed through the API gateway.
Read application logs with:

```powershell
docker compose logs --tail 100 order-service
```

HTTP requests and instrumented OpenFeign calls propagate W3C trace context. Kafka
producer/listener observations continue it through messaging. Order outbox rows persist
`traceparent` and `tracestate`, so delayed publication and retries retain the originating
trace. Gateway responses include `X-Trace-Id` for lookup. Start the collector and UI:

```powershell
docker compose --profile observability up -d tempo grafana
```

Open http://localhost:3000/explore, select Tempo, choose Trace ID and paste the response's
`X-Trace-Id`. The local UI allows anonymous Editor access for Explore; the UI and collector
ports bind to loopback. Configure authentication before exposing Grafana beyond your machine.
Tempo accepts OTLP HTTP at `tempo:4318/v1/traces` inside Docker and
`http://localhost:4318/v1/traces` from IDE processes, with 24-hour retention.
Rebuild/recreate application containers after these code/config changes:

```powershell
$env:MANAGEMENT_TRACING_ENABLED = 'true'
docker compose --profile app --profile observability up -d --build
.\scripts\smoke-tracing.ps1
```

The read-only smoke check requests the catalog and verifies that Tempo contains both
gateway and product-service spans with the returned trace ID. Export is asynchronous;
the check waits up to 60 seconds. Local sampling defaults to 100%. Set
`TRACING_SAMPLE_RATE=0.1` for a busier deployment; unsampled requests still have IDs but
are not stored in Tempo. `OTLP_TRACES_ENDPOINT` overrides the exporter destination.
Set `MANAGEMENT_TRACING_ENABLED=false` to disable tracing without breaking requests.
IDE services use the same settings from their bundled application.properties.

Existing structured application logs include trace/span correlation when a span is active:
`docker compose logs --since 10m | Select-String 'YOUR_TRACE_ID'`.
Logs are still console-only; Grafana is configured for traces, not centralized logs.
Do not put credentials, request bodies, addresses, or payment details in span tags.

The order outbox adds nullable `trace_parent` (256) and `trace_state` (512) columns.
The current Hibernate `ddl-auto=update` setup adds these automatically; deployments
using managed migrations must add both VARCHAR columns before starting the new version.
Existing rows without context still publish. Background order recovery can start a new
trace; use the order ID to correlate business work across separate recovery attempts.
Kubernetes keeps its existing Tempo collector; the Grafana UI here is Docker-only.

Implementation follows [Spring Boot tracing](https://docs.spring.io/spring-boot/3.5/reference/actuator/tracing.html)
and [reactive context propagation](https://docs.spring.io/spring-boot/3.5/reference/actuator/observability.html).

Container startup imports the bundled application-container.properties through
CONFIG_SERVER_IMPORT; remote Config Server loading is disabled in container environments.
IDE defaults still use the existing Config Server configuration.

## Kubernetes

A development Kubernetes context and a default StorageClass are required. No public
ingress is included. The base defines single-replica services, database/Kafka StatefulSets,
PVCs, probes, resource limits, and a namespace ingress policy.

```powershell
docker compose --profile app build
kubectl create namespace ecommerce
kubectl -n ecommerce create secret generic ecommerce-secrets --from-env-file=.env
kubectl apply -k infra/k8s/base
kubectl -n ecommerce get pods,pvc
kubectl -n ecommerce port-forward service/api-gateway 8081:8081
```

Use infra/k8s/observability instead of base to include Tempo. Load local images into your
cluster when required by kind/minikube; remote clusters require registry images.
For private GHCR images, configure imagePullSecrets before deployment.
NetworkPolicy enforcement requires a compatible CNI.

Removing resources from manifests does not delete previously applied Kubernetes objects.
This workspace has not deployed a Kubernetes cluster.

## CI/CD

GitHub Actions runs Maven tests, infrastructure checks, Kubernetes schema validation,
and a disposable product/cart MySQL/Redis smoke test before publishing SHA-tagged GHCR
images on main/master or manual runs. The smoke test checks cache freshness, Redis outage
fallback, and readiness, then deletes only its own temporary containers and volumes.

For deployment, configure the GitHub development environment, its deployment protections,
and KUBE_CONFIG_BASE64 secret. Provision ecommerce-secrets in the cluster separately.
Run the workflow manually with deploy=true. Deployment waits for application rollouts.
No remote workflow or Kubernetes deployment is triggered by editing these files.

## Verification and shutdown

```powershell
.\mvnw.cmd verify
python scripts/validate-infra.py
docker compose --profile app --profile observability config --quiet
kubectl kustomize infra/k8s/observability
.\scripts\smoke-compose.ps1
```

The smoke test requires rebuilt product/cart images and uses ports 18083/19090/19091.

Stop services without deleting persistent data:

```powershell
docker compose --profile app --profile observability down
```

Do not add -v unless you intend to delete database/tracing data.
Previously created monitoring data volumes are retained but no longer referenced by Compose.

This is a development deployment. Production needs TLS, secret management, migrations
instead of ddl-auto=update, backups/restore tests, and appropriate database/Kafka topology.
