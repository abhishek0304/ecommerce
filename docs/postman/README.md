# Ecommerce Postman collection

Import **Ecommerce.postman_collection.json** and **Ecommerce.local.postman_environment.json** into Postman, then select **Ecommerce local** as the active environment. No secrets from the repository `.env` are included.

## Run the automated customer journey

1. Start the application using the root README or INFRASTRUCTURE.md. The gateway, all five domain services, their databases, Kafka, and Eureka must be healthy. Config Server is required for the normal local profile, but the Compose application profile disables it. At least one active product must have stock.
2. Keep `baseUrl=http://localhost:8081`, or change it to your test gateway. Leave `referenceRequest` empty and `enableAdminFlow=false`.
3. Choose **Run collection**, select all requests, set **1 iteration**, **2000 ms delay**, and a **30000 ms request timeout**. Keep the folders in their saved order. Run against an idle test catalog because stock assertions assume no concurrent purchases.
4. Review the assertions in Runner. Reference/operations requests and the optional admin folder are intentionally skipped. A skip is not verification of that endpoint.

The default run creates unique customer credentials, logs in, rotates the refresh token, saves an address, discovers stock, exercises wishlist/cart, places COD checkout, retries the same idempotency key, verifies one stock deduction and cart clearing, cancels the order, verifies stock restoration, waits for its Kafka inbox event, reads delivery history, and logs out. IDs and tokens are captured automatically in the selected environment. Polling is bounded by `maxPollAttempts` (30 by default); at 2 seconds per request that allows roughly a minute per asynchronous stage.

Expected-status failures stop the Runner. Lifecycle and inventory mismatches also stop it. A network error can prevent a response script from executing; stop the run and fix connectivity before rerunning if Postman reports a transport error. Rerunning creates a fresh customer and checkout key. A partially completed earlier run may leave a reserved order: inspect it before starting more runs.

Registration always queues a verification email. Use disabled external channels or a local SMTP capture server for synthetic `example.invalid` recipients. The collection disables the generated customer's order-notification preferences, but those do not disable account-security email. No genuine phone number or personal email is generated. Order notifications are still persisted in the inbox and delivery queue.

The generated customer/address and order/notification history remain for inspection. The default run cancels its order and restores stock. It does not delete existing customers or catalog data. Exporting the environment after running can expose tokens: keep the exported file private.

## Optional shipping, delivery, return and refund run

Set `enableAdminFlow=true`, `adminEmail`, and `adminPassword` for an **existing test administrator**, then run the whole collection. Registration never grants ROLE_ADMIN; the admin login checks the returned role.

After the customer cancellation scenario, this folder creates a unique coupon and another COD order, advances PROCESSING → SHIPPED → DELIVERED, records simulated cash collection, submits a verified review and return, approves and restocks the return, records a simulated manual refund, verifies stock restoration, and deactivates the coupon. It leaves the review, coupon and order audit records behind.

This automated lifecycle folder uses manual shipping state changes. Separate Shipping reference requests exercise Shiprocket and Delhivery booking, tracking, labels and pickup; see [shipping configuration](../../order-service/SHIPPING.md). The default MOCK mode makes no carrier calls. Configure the provider and populate shipping variables before selecting an individual reference request. COD refund recording does not move money or validate a bank transfer. The `POSTMAN-SIMULATED-...` reference is only for a test installation. This run verifies the application's lifecycle, not physical shipping, cash collection or actual refunds.

## Every endpoint and its examples

The reference folders contain all **81 application-controller route variants**, including `/auth/...`, `/forgot-password`, and profile aliases, plus an additional Razorpay checkout example. Each request has its purpose, access rules, validation/preconditions, expected errors, implementation reference and a saved response example. Examples are illustrative source-derived payloads, not evidence that a live service returned them. IDs, dates, quantities and state vary in real responses.

The collection has **146 requests** total, including workflow repetitions and operational checks. `endpoint-coverage.json` maps every application route to its reference request. Health/info, gateway discovery/routes/storefront, Eureka registration lookup and Config Server configuration lookup supplement the application controllers. Framework actuator exposure depends on the profile; the collection does not claim every optional Spring framework endpoint is enabled.

To send one reference request:

1. Set `referenceRequest` to its **exact saved request name** (copy it from the sidebar).
2. Populate its variables and prerequisite state. For example, a cart request needs `accessToken` and `productId`; checkout also needs a saved `addressId`, nonempty cart, and an unused `checkoutKey`; returns need a delivered order. An admin request needs `adminAccessToken`. To obtain one manually, use the login reference with the admin credentials in the body and copy its returned access token to `adminAccessToken`.
3. Click **Send**, or run the collection: only that reference request will execute. Clear `referenceRequest` before running the automated journey again.

This selection gate prevents a whole-collection run from deleting accounts, resetting passwords, cancelling an already delivered order, releasing committed inventory, or submitting incompatible return decisions. Every reference request is editable and runnable when its documented prerequisites are satisfied. Account deletion and password operations affect the selected account; use generated test credentials. Login/refresh, registration, address/product creation, checkout, cart reads/writes and delivery listing capture their resulting tokens or IDs.

For reference-only use without first running the default flow, fill `customerEmail`, `customerPhone`, `customerPassword`, `productSku`, `couponCode`, `couponExpiresAt`, `checkoutKey` and other relevant variables yourself. Numeric variables such as `productId`, `userId`, `addressId`, and `cartVersion` must contain numbers. UUID variables must contain valid UUIDs. Use a fresh `reservationId` for isolated inventory tests, not a real order's ID. Do not commit a reservation and then try to release it; commit → return and reserve → release are separate branches.

## Services and connectivity

| Service | Responsibility | Default direct URL |
|---|---|---|
| API Gateway | Routes public APIs and serves the storefront; exposes trace IDs | `http://localhost:8081` |
| User | Accounts, JWT/refresh sessions, profiles, addresses, preferences, email/reset OTPs | `http://localhost:8082` |
| Product | Catalog and stock reservations, release, commit and returns | `http://localhost:8083` |
| Cart | Customer quantities and versioned checkout snapshots | `http://localhost:8084` |
| Order | Checkout, Razorpay, fulfillment, coupons, reviews, wishlist and returns/refunds | `http://localhost:8085` |
| Notification | Kafka inbox and SMTP/Twilio delivery queue | `http://localhost:8086` |
| Config Server | Shared configuration from Git/native files; responses may contain secrets | `http://localhost:8080` |
| Eureka Registry | Discovery of registered service instances | `http://localhost:8761` |

Public business requests use `baseUrl`. To test a domain directly, run its reference requests with `baseUrl` set to that service's URL, then restore the gateway URL before running a cross-service workflow. Legacy user aliases, internal endpoints and operational requests already use their own URL variables.

The current Compose setup publishes only the gateway business port. Internal service and registry URLs are not automatically reachable from host Postman. Run the services locally or configure deliberate localhost port mappings/tunnels for direct reference requests. Container profiles move actuator to **container port 9090**, which is not published by default. For operational health/info requests, edit the request base URL to a reachable mapped management port. Gateway routes/discovery actuators may be disabled by the container profile; that is not an application-controller failure. Do not change `baseUrl` globally to a management port for the business workflow.

Internal endpoints require the configured `internalServiceKey`; no default key or signing secret is embedded. They are not exposed by the explicit public gateway routes. Kafka, MySQL and Redis are infrastructure protocols, not HTTP APIs; they cannot be exercised as ordinary Postman HTTP requests.

## OTP, Razorpay and notification providers

- **OTP:** request verification resend or password reset, read the actual code from your configured email delivery/capture system, and set `verificationOtp` or `resetOtp`. Codes expire after ten minutes; new requests have a 60-second cooldown. The HTTP API never returns the code. Do not use illustrative OTPs as actual credentials.
- **Razorpay:** configure test keys on order-service, refill the cart, select `REF Order service | Create Razorpay test checkout`, and use a fresh `checkoutKey`. It captures `orderId` and `razorpayOrderId`. Complete the interactive Razorpay test payment using the storefront/Checkout tied to that order, then supply the returned payment ID/signature to the verify request. The backend rechecks captured payment, order, amount and currency. Do not put the provider secret in this collection.
- **Refund:** cancel an eligible captured online order or complete its return; inspect the order's refund state until PROCESSED. The retry endpoint requires a genuinely failed refund. Saved examples do not create that prerequisite. Razorpay creation, payment and refund errors are not treated as success.
- **Webhook:** `webhookSignature` must be the HMAC-SHA256 of the exact resolved raw body, using the server's webhook secret. The sample is not pre-signed; provider state is still queried. Real provider webhook delivery cannot be established by sending an example yourself.
- **Notifications:** `ACCEPTED` means provider acceptance, not inbox/handset delivery. Disabled channels remain pending until expiry; opted-out enabled channels are skipped. To test a real recipient, configure the provider and preferences and supply your own test account. Internal account-email requests can send mail and bypass order preferences. No external messages were sent while building this collection.

## Regenerate and validate

From the repository root, with Python 3 and Node.js available:

```powershell
python scripts/build-postman.py
python scripts/validate-postman.py
node scripts/validate-postman-scripts.cjs
```

The generator reuses handbook sample data and explicitly corrects descriptions changed by the current code. The validator independently inventories controller mappings, checks complete reference coverage, saved examples and environment variables. The Node validator compiles every embedded script and simulates the workflow and polling/skip branches without contacting services. These checks do not establish live backend or provider success.

The collection uses the [Postman v2.1 format](https://schema.postman.com/json/collection/v2.1.0/docs/index.html) and [Runner workflow APIs](https://learning.postman.com/docs/tests-and-scripts/running-collections/building-workflows). Use a current Postman desktop version supporting `pm.execution.skipRequest`. For CI, use a compatible Postman CLI/Newman runtime with a 2000 ms request delay; older runtimes may not support skipping. Do not confuse skipped optional requests with coverage of a live provider.
