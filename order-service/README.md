# Order Service: COD and Razorpay test checkout

Runs on **8085**, registers with Eureka, and stores orders in **orderdb**.

Shiprocket/Delhivery booking, pickup, tracking and return handling are documented in [Shipping integration](SHIPPING.md). Shipping defaults to explicit local simulation; provider credentials and account verification are separate from payments.

## Start

Create the database in MySQL:

```sql
CREATE DATABASE IF NOT EXISTS orderdb;
```

Start the Config Server, Eureka, user-service, product-service, and cart-service. Restart product-service and cart-service after importing these changes: checkout uses their new internal inventory and cart APIs. Restart the gateway for the new routes.

From the ecommerce root in PowerShell:

```powershell
# Required only for online checkout. Use your Razorpay TEST credentials locally.
$env:RAZORPAY_KEY_ID = "rzp_test_YOUR_KEY_ID"
$env:RAZORPAY_KEY_SECRET = "YOUR_TEST_KEY_SECRET"
$env:RAZORPAY_WEBHOOK_SECRET = "YOUR_WEBHOOK_SECRET"
.\mvnw.cmd -pl order-service spring-boot:run
```

Cash on delivery works without Razorpay keys. Online checkout returns 503 until test keys are configured. Live Razorpay keys are deliberately rejected in this test-mode integration. Never put the secret key in frontend code or commit it.

Other overrides: `ORDER_DB_URL`, `ORDER_DB_USERNAME`, `ORDER_DB_PASSWORD`, `USER_SERVICE_URL`, `PRODUCT_SERVICE_URL`, `CART_SERVICE_URL`. Defaults use localhost, MySQL root/1234, and existing service ports.

Set the same `JWT_SECRET` in user-service, cart-service, and order-service. Set the same `INTERNAL_SERVICE_KEY` in product-service, cart-service, and order-service. Local development defaults are provided. Internal endpoints reject ordinary user tokens and require the service key.

The bundled configuration works with the existing Config Server; native configuration is also provided in `ecommerce-config-repo/order-service.properties`.

## Before checkout

1. Log in using `POST http://localhost:8082/api/auth/login`.
2. Add a delivery address through `POST http://localhost:8082/api/addresses` and get its ID from `GET /api/addresses`, using the login access token.
3. Add products to your cart through `POST http://localhost:8084/api/cart/items`.

The address must belong to the logged-in user. Order creation copies that address and the product prices into the order.

## Endpoints

Use **http://localhost:8085** directly or **http://localhost:8081** through the gateway.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | /api/orders | Create or resume checkout |
| GET | /api/orders?page=0&size=20 | List your orders |
| GET | /api/orders/{id} | View your order |
| POST | /api/orders/{id}/cancel | Cancel an eligible COD or online order |
| POST | /api/orders/{id}/payments/verify | Verify Razorpay Checkout callback |
| POST | /api/payments/razorpay/webhook | Receive signed Razorpay events |
| PATCH | /api/admin/orders/{id}/status | Admin fulfillment update |
| POST | /api/admin/orders/{id}/refund/retry | Admin retry of a failed refund |

All order endpoints require `Authorization: Bearer YOUR_ACCESS_TOKEN`. The webhook instead requires `X-Razorpay-Signature`.

Create a COD order:

```http
POST http://localhost:8085/api/orders
Authorization: Bearer YOUR_ACCESS_TOKEN
Idempotency-Key: checkout-2026-001
Content-Type: application/json

{"addressId": 1, "paymentMethod": "CASH_ON_DELIVERY"}
```

For online payment, use `"paymentMethod": "RAZORPAY"`. Items and prices are read on the server; do not send them.

**Retries must use the same Idempotency-Key and request body.** Keys accept 8–128 ASCII letters, digits, underscores, or hyphens. Generate one key per checkout, save it on the client before sending, and reuse it after a timeout or 202. A UUID is suitable.

Successful calls return 200 with the order and a Location header. While dependencies are unavailable, a durable checkout can return 202 with CREATING or RESERVED status. Poll GET or resend the same POST. A terminal stock rejection returns 409 with status FAILED; retrying its key returns the same failed order. To try again after fixing stock, edit/refill the cart and use a new key.

## Idempotency and stock

- The database uniquely identifies a checkout by user ID and a SHA-256 digest of its key. Keys are case-sensitive and scoped to a user.
- Reusing a key with a different address or payment method returns 409.
- The cart version is also unique per user/order, so two keys cannot order the same unchanged cart.
- Inventory reservations use the persisted order UUID. Repeating a reservation or release does not deduct or restore stock twice.
- Products are locked in ID order while reserving. A multi-item stock failure rolls back all deductions.
- Order item prices are captured from product-service inside reservation. Cart prices are only estimates.
- Reserved products cannot be deleted. Stock shown by product-service is the quantity still available.
- Cart cleanup clears only the version used for checkout. If the user changes their cart meanwhile, it is preserved; `cartCleared: false` indicates it was not cleared (or cleanup is still pending).

The recovery worker runs every 30 seconds and resumes durable work after dependency outages or process restarts. `order.recovery.enabled=false` disables it.

## Razorpay test payment

A PENDING_PAYMENT order includes:

```json
{
  "checkout": {
    "keyId": "rzp_test_...",
    "razorpayOrderId": "order_...",
    "amount": 2100,
    "currency": "INR",
    "testMode": true
  }
}
```

The amount is in **paise**. This example is INR 21.00. Product prices in this checkout flow are treated as INR.

Use Razorpay Standard Checkout in your frontend. Load `https://checkout.razorpay.com/v1/checkout.js`, pass the server's keyId/orderId/amount/currency, and send the returned fields to the verification endpoint:

```javascript
// order is the response from POST /api/orders; accessToken is the user's login token.
const payment = new Razorpay({
  key: order.checkout.keyId,
  order_id: order.checkout.razorpayOrderId,
  amount: order.checkout.amount,
  currency: order.checkout.currency,
  name: "Ecommerce test checkout",
  handler: async function (result) {
    const response = await fetch("/api/orders/" + order.id + "/payments/verify", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": "Bearer " + accessToken
      },
      body: JSON.stringify(result)
    });
    if (!response.ok) throw new Error("Payment verification is pending or failed");
    const confirmedOrder = await response.json();
    console.log(confirmedOrder.status);
  }
});
payment.open();
```

This example assumes the frontend serves API requests through the same origin/gateway. This change adds backend APIs, not a frontend checkout page.

The verification request body has `razorpay_order_id`, `razorpay_payment_id`, and `razorpay_signature`. The backend validates HMAC using its stored Razorpay order ID, then checks the provider for a captured payment linked to that order with the exact amount and INR currency. It does not trust a client success flag. Configure automatic payment capture in the Razorpay test dashboard; an authorized-only payment is not treated as paid.

Online orders become CONFIRMED/PAID after verification. Duplicate callbacks are safe. The recovery worker queries Razorpay so a missed browser callback can still be reconciled.

Subscribe to `payment.captured`, `order.paid`, `refund.created`, `refund.processed`, and `refund.failed` webhooks using a reachable HTTPS endpoint pointing to `/api/payments/razorpay/webhook`, and configure the same webhook secret locally. HMAC is checked against the raw body before processing. The backend rechecks provider payment status rather than trusting webhook amounts. Duplicate events do not repeat stock changes.

If a Razorpay order-creation response is lost, the backend searches by its unique receipt (the local order UUID). It **does not issue another create call** after an uncertain attempt. If no provider order can be found, checkout remains RESERVED for reconciliation; inspect the Razorpay dashboard and application database before any manual correction. No automatic second payment order is created.

Online orders expire after 15 minutes by default. Eligible online cancellations and late payments trigger refunds. Orders remain closed after expiration or cancellation. See [Lifecycle and notification setup](LIFECYCLE.md) for deadlines, refunds, fulfillment, Kafka notifications, and test instructions.

## Tests

```powershell
.\mvnw.cmd -pl product-service,cart-service,order-service,api-gateway test
```

Tests use H2 and local HTTP provider stubs, not real Razorpay credentials or charges. They exercise concurrent checkout, stock locking, duplicate requests, lost responses, amount/signature validation, webhook retries, ownership, and conditional cart cleanup.

Provider references: [Standard Checkout and verification](https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/integration-steps/), [receipt lookup](https://razorpay.com/docs/api/orders/fetch-all/), and [payment-status guidance](https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/best-practices/).
