# Expiration, refunds, fulfillment, and notifications

## Start the updated services

Create the new notification database in MySQL:

```sql
CREATE DATABASE IF NOT EXISTS notificationdb;
```

Restart **product-service**, **order-service**, and **api-gateway** after rebuilding. Start the new service from the ecommerce root:

```powershell
.\mvnw.cmd -pl notification-service spring-boot:run
```

Notification Service runs on **8086**. Keep Kafka running (`docker compose up -d --wait kafka`), as well as the existing databases, Eureka, Config Server, user-service, and cart-service.

Order and Notification Services create the `order-events` topic with three partitions and one replica for local development. Use the same `KAFKA_BOOTSTRAP_SERVERS` on both services. The order API can continue during a Kafka outage: pending updates stay in `order_event_outbox` until publishing succeeds.

New tables and columns are added by the project's existing Hibernate `ddl-auto=update` setting. No existing orders are deleted. Existing unpaid online orders receive a deadline based on their original creation time and may expire on the first recovery run.

## Payment expiration

Online checkout expires **15 minutes after order creation** by default. Configure the Order Service with:

```powershell
$env:ORDER_PAYMENT_TIMEOUT = "15m"
```

The recovery worker checks every 30 seconds. Orders still in CREATING, RESERVED, or PENDING_PAYMENT at the deadline move through CANCELLING to EXPIRED. Inventory release is idempotent and retried after outages. COD and already confirmed orders do not expire.

Expiration closes the local order, even if Razorpay is unavailable. A later captured payment is reconciled and refunded; the expired order never becomes fulfillable. Payment confirmation must complete before the deadline. No attempt is made to pretend the Razorpay payment window itself has been invalidated.

GET `/api/orders/{id}` exposes `expiresAt`, order status, payment status, and refund progress.

## Cancellation and refunds

Customers use their access token:

```http
POST http://localhost:8085/api/orders/ORDER_UUID/cancel
Authorization: Bearer YOUR_ACCESS_TOKEN
```

Cancellation is allowed before shipping, including PROCESSING orders. Repeated cancellation is harmless. SHIPPED and DELIVERED orders return 409; returns after delivery are a separate workflow.

- Unpaid cancellations release stock and remain monitored for late payments.
- Captured online payments trigger a full Razorpay refund.
- COD cancellation releases stock without issuing a refund.
- Refunds use a persisted attempt UUID in `X-Refund-Idempotency`; retries preserve that key and body.
- Lost responses and provider outages are retried. An accepted refund may remain PENDING before reaching PROCESSED or FAILED.
- The order stays CANCELLED or EXPIRED while refund progress is tracked separately.

Example order response fields:

```json
{
  "status": "CANCELLED",
  "paymentStatus": "REFUND_PENDING",
  "refund": {
    "status": "PENDING",
    "razorpayRefundId": "rfnd_...",
    "failure": null
  }
}
```

Refund states: NONE, REQUESTED, PENDING, PROCESSED, FAILED, and REVIEW_REQUIRED. PROCESSED maps to paymentStatus REFUNDED. FAILED and REVIEW_REQUIRED map to REFUND_FAILED. External partial refunds require manual reconciliation; the service does not issue a second full refund on top of them.

A trusted administrator can retry a failed refund:

```http
POST http://localhost:8085/api/admin/orders/ORDER_UUID/refund/retry
Authorization: Bearer ADMIN_ACCESS_TOKEN
```

A confirmed failed provider refund gets a new attempt UUID. An uncertain request keeps its original UUID. Pending or processed refunds cannot be retried through this admin action.

Add **refund.created**, **refund.processed**, and **refund.failed** to the existing Razorpay webhook subscriptions, alongside payment.captured and order.paid. The endpoint remains `/api/payments/razorpay/webhook`. Signatures are verified against the raw request body; refund status is fetched from Razorpay before updating the order. Duplicate and delayed events do not repeat stock release or successful refunds.

This remains a **Razorpay test-mode integration**. Configure the existing test keys and webhook secret. The implementation follows Razorpay's [idempotent refund API](https://razorpay.com/docs/api/refunds/normal-refunds-idempotent/?preferred-country=IN) and [refund event documentation](https://razorpay.com/docs/webhooks/refunds/).

## Fulfillment

Only signed access tokens containing **ROLE_ADMIN** can use the admin endpoints. Normal customer tokens return 403. Assign the admin role using your trusted administration process, then log in again for a token containing that role.

Transitions are sequential:

`CONFIRMED → PROCESSING → SHIPPED → DELIVERED`

```http
PATCH http://localhost:8085/api/admin/orders/ORDER_UUID/status
Authorization: Bearer ADMIN_ACCESS_TOKEN
Content-Type: application/json

{"status": "PROCESSING"}
```

Ship with required carrier and tracking details:

```json
{
  "status": "SHIPPED",
  "carrier": "Example Carrier",
  "trackingNumber": "TRACK123"
}
```

Deliver an online order:

```json
{"status": "DELIVERED"}
```

Deliver a COD order only after collecting payment:

```json
{"status": "DELIVERED", "cashCollected": true}
```

Unpaid online orders cannot enter fulfillment. Skipped or backward transitions return 409. Repeating the same transition is idempotent; shipping retries must preserve the carrier and tracking number. COD delivery records paymentStatus COLLECTED.

Shipping commits the inventory reservation without deducting stock a second time. A committed reservation cannot be released. If the inventory call fails after the order reaches SHIPPED, recovery retries the commit and cancellation stays disabled.

Order responses include `carrier`, `trackingNumber`, `shippedAt`, and `deliveredAt`.

## Kafka notifications

Order changes and notification events are committed in the same database transaction. The outbox publisher sends them to **order-events**, keyed by order UUID, and marks them delivered only after a broker acknowledgment. A crash between acknowledgment and database commit can repeat an event; Notification Service deduplicates by its immutable eventId.

Notification Service persists an inbox per user. View it with:

```http
GET http://localhost:8086/api/notifications?page=0&size=20
Authorization: Bearer YOUR_ACCESS_TOKEN
```

Through the gateway, use `http://localhost:8081/api/notifications`. A user can only view their own notifications.

Updates cover confirmation, failure, pending/captured/collected payments, expiration, cancellation, refund progress, processing, shipping, and delivery. Shipping notifications include carrier and tracking information. Events exclude shipping addresses, credentials, and payment secrets.

This provides **in-app notifications**, not email or SMS delivery. Database failures keep Kafka messages unacknowledged so delivery can retry. Malformed events block their partition for inspection rather than being silently discarded; a future dead-letter workflow can handle those operational cases.

## Verification

```powershell
.\mvnw.cmd -pl product-service,order-service,notification-service,api-gateway test
```

Tests exercise expired/late payments, cancellation races, lost refund responses, refund retries and webhooks, role checks, fulfillment transitions, inventory release/commit, and outbox recovery. Notification tests use an embedded Kafka broker to verify delivery, deduplication, and user isolation. Payment tests use a local Razorpay stub and do not move real money.
