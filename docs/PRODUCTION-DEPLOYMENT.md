# Production deployment handoff

Status on 2026-09-30: **not deployed or publicly verified by this task**. No target hosting account, server/cluster, domain, DNS zone, or production secret store has been supplied. The local Kubernetes configuration has no current context. Existing manifests and image workflows establish configuration intent, not a running deployment.

## Required deployment target

Supply the hosting provider/project or server, deployment region, owned domain/subdomain and DNS provider, and the supported way to access that account (authenticated CLI/session or secure credentials store). Do not put secret values in chat or tracked files. The hosting choice also determines load balancer, certificate provisioning, database storage/backups and secret injection. No paid hosting or domain has been provisioned here.

## Current configuration and required changes

| Area | Existing implementation | Work needed for the selected production target |
|---|---|---|
| Hosting | Docker Compose and development Kubernetes manifests; single-replica databases/Kafka | Select capacity and persistent storage; provision host/cluster and backups; restore-test before accepting customer data |
| Public access | Gateway only; no public Kubernetes ingress | Provision a supported ingress/load balancer/reverse proxy; expose only gateway traffic, not databases, registry, config or management ports |
| Domain | No verified domain or DNS zone | Configure A/AAAA or provider-required CNAME; verify public resolution; only publish AAAA when IPv6 routing works |
| HTTPS | No certificate/renewal setup | Provision trusted hostname certificate and automatic renewal; configure HTTP-to-HTTPS redirect and proxy forwarding; verify from outside the host |
| Network policy | Base permits same-namespace ingress only | Permit the chosen ingress controller to reach api-gateway:8081; preserve isolation of management port 9090 and internal services |
| Secrets | Local ignored `.env`; Kubernetes `ecommerce-secrets` references | Generate independent production credentials in the selected secret manager and bind least-privilege workload access; do not copy local secrets |
| Schema | Flyway V1/V2 and Hibernate validation in each persistence service | Follow [reviewed baseline adoption](OPERATIONS.md) for populated databases; fresh empty schemas migrate automatically. Rehearse target-version upgrades on restored clones |
| Images | GHCR commit-SHA images produced by CI | Verify build/test run and image availability; deploy the selected immutable revision with registry pull access |
| CI deployment | Existing workflow targets GitHub environment `development` | Configure a separate production environment, cluster identity and deployment target; do not retarget development silently |
| Notifications | SMTP/Twilio implemented; Kubernetes base wires the dedicated ecommerce-notification-providers Secret | Inject notification provider configuration and customer preferences, then verify queue acceptance and real test-recipient receipt separately |
| Payments | `RazorpayClient.requireConfigured()` rejects keys other than `rzp_test_...` | Live payments require an explicit code change and provider verification; adding live keys to deployment secrets will currently produce 503 |
| Authorization | Profile-by-ID lookup now enforces ownership or ROLE_ADMIN with regression coverage | Deploy the fix and verify it against the hosted application |

## Production secret contract

The existing Kubernetes base expects Secret `ecommerce-secrets` in its deployment namespace. These are **names**, not provisioned production values:

- `JWT_SECRET`: independent cryptographically random signing secret of at least 32 bytes, consistent across validating services. Rotation needs a session/token rollout plan.
- `INTERNAL_SERVICE_KEY`: independent random shared internal API credential.
- `USER_DB_PASSWORD`, `PRODUCT_DB_PASSWORD`, `CART_DB_PASSWORD`, `ORDER_DB_PASSWORD`, `NOTIFICATION_DB_PASSWORD`: distinct database credentials matching the actual database accounts.
- `REDIS_PASSWORD`: independent cache credential.
- `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`: provider-issued/configured values. Current integration is test-mode only. The base references these keys even when online payments are unused, so provision empty values for disabled test payments if retaining that base.

Notification-service also needs the settings documented in [PROVIDERS.md](../notification-service/PROVIDERS.md): EMAIL_FROM and SMTP configuration for email; Twilio account/token/senders/template for SMS/WhatsApp. Keep channel flags false until the selected provider and recipients are ready. SMTP/Twilio credentials must come from those accounts; random strings cannot substitute for provider-issued values.

Provision the secret store and workload bindings only after the host/project is selected. Do not render a Secret manifest containing real values into Git or logs. Updating a secret does not rotate an already initialized MySQL account; rotate the database account and workload credential together. Record secret **versions/identifiers** as deployment evidence, never their values.

## Evidence required before calling the deployment live

Keep the deployment provider resource IDs, release SHA/image digests, completed rollout results, DNS records, certificate status/renewal configuration, and public checks. A successful manifest render is only an offline check. A successful pod rollout does not prove DNS or HTTPS.

After a real public origin exists, run from outside the hosting network:

```powershell
python scripts/verify-public-deployment.py --url https://YOUR-OWNED-DOMAIN --output .deployment-evidence/public-check.json
```

This read-only check resolves DNS, validates the trusted TLS certificate and hostname, checks HTTP redirects to HTTPS, and fetches the storefront and gateway catalog. It fails on an unexpected status/content type or invalid certificate. It records no response bodies or credentials. Review the address results against the actual hosting destination; an unrelated site with the same API shape is not proof of the intended release.

Then run the [Postman journey](postman/README.md) against that origin with dedicated test accounts, including admin fulfillment/returns where configured. Verify payment capture/refund and SMTP/Twilio delivery against provider records. Shiprocket and Delhivery adapters now exist, but MOCK results are only application-state evidence. Verify booking, tracking, pickup and applicable labels against provider records using the [shipping guide](../order-service/SHIPPING.md). Confirm backup restoration and certificate renewal separately; the public checker does not test either.

Do not mark hosting, DNS, HTTPS, production secrets, migrations, provider integration or deployment complete until each has its corresponding live evidence.
