# Ecommerce feature guide

The React storefront now connects the customer and administrator flows to the Spring services. Product-service, cart-service and order-service have new V3 migrations. Rebuild and restart these services and the gateway before using the new pages.

## Features and entry points

| Feature | Where to use it |
|---|---|
| Wishlist | Header Wishlist; save products from the product dialog |
| Verified reviews | Product dialog shows reviews; delivered orders expose Review a purchase |
| Coupons | Check coupon at checkout; Admin → coupons creates and enables codes |
| Full-order returns | Delivered orders expose Request full-order return; Admin → orders handles decisions and receipt |
| Online payment | Select Razorpay at checkout; pending orders expose Pay online |
| Account settings | Account button → profile, addresses and password tabs |
| Password recovery | Sign in → Forgot password; enter the delivered OTP and new password |
| Server logout | Sign out revokes the current refresh token and clears the local session |
| Checkout recovery | My orders or checkout → Check saved checkout; terminal failures permit a new attempt |
| React administration | Admin header button for an existing ROLE_ADMIN user |
| Variants | Admin → products → Create variant; product dialog provides variant selection |
| Delivery charges and estimates | Checkout address selects the postal-code rule; Admin → delivery configures rules |
| Low-stock alerts | Admin → inventory, with a configurable threshold |
| Replenishment and audit trail | Admin → inventory → stock adjustment/history |
| Guest shopping | Add products before login; the bag merges into the account after login |
| Order support | My orders → Get order support, or header Support |
| Sales analytics | Admin → analytics, with period selection and daily sales |

Guest shopping requires sign-in or registration to place an order. It implements the proposed guest-cart and account-merge journey; anonymous payment and anonymous order ownership are not introduced.

## Run and rebuild

```powershell
Set-Location 'D:\spring projects\ecommerce'
.\mvnw.cmd -pl product-service,cart-service,order-service,api-gateway test
Set-Location frontend
npm test
npm run dev
```

Use the startup guide for the backend service sequence. To serve the React application through Spring:

```powershell
npm run build:gateway
```

Then rebuild/restart api-gateway and open `http://localhost:8081/react/index.html`. Docker images must also be rebuilt after backend changes. Existing populated databases still require the repository's reviewed Flyway adoption procedure if they have not already adopted migrations; do not baseline or delete tables automatically.

## Variants and inventory

A variant is a separately purchasable product linked to a parent. Each variant has a separate product ID, unique SKU, price and available quantity. Existing cart, reservation and order APIs therefore work unchanged. Size, color and a descriptive label are saved on the variant link.

Edit a variant through its own product entry in Admin → products. Variants and parents with variant links must be hidden rather than deleted. A hidden variant is omitted from public variant selection. Parent and variant entries can both appear in the catalog.

Inventory adjustments validate the resulting stock and require a supplier or adjustment reference. Stock history records product creation/admin edits, replenishment, reservation deductions, cancellation releases and return restocking. Existing seeded or historical stock does not receive invented historical movements. Repeating an inventory reservation/release does not duplicate its movement records. Low-stock alerts are visible in the admin dashboard; outbound stock-alert emails are not configured by this change.

## Delivery pricing

Admin delivery rules define a numeric postal prefix, delivery fee, merchandise subtotal for free delivery, minimum/maximum delivery days and active state. The longest active matching prefix wins. For example, prefix `411` overrides prefix `4` for postal code `411001`.

With no matching rule, delivery is free with a 5–7 day estimate. These are merchant estimates, not carrier-confirmed promises. Rules currently match postal codes, without a separate country/weight dimension.

Checkout displays an estimate from the cart. Order-service recalculates using the reserved merchandise subtotal, applies the coupon to merchandise, and adds delivery fees to the order total. The saved fee and estimate remain unchanged on retries. The payment-provider amount includes delivery charges. Existing full-order refunds refund the actual saved total.

Example admin rule:

```json
{"postalPrefix":"411","fee":49,"freeAbove":500,"minDays":2,"maxDays":4,"active":true}
```

## Checkout recovery and guest merging

The client persists a checkout key and request body before sending. Check saved checkout calls the owner-scoped attempt lookup. An unknown attempt must be retried with its existing details. Processing attempts stay saved. A confirmed terminal FAILED/CANCELLED/EXPIRED attempt can be cleared through Start a new attempt; adjust the cart before ordering again because the backend protects against duplicate purchases of the same cart version.

Guest merge uses `/api/cart/merge` with its own persisted idempotency key. All requested products are validated in a single transaction. A lost response is retried with the same key/body, and a stored receipt prevents duplicate additions. Merge keys are scoped to the signed-in user. An invalid product/quantity fails the whole merge; guest items remain available for correction and retry. If a merge is unresolved, retry it before editing the attempted items.

## Accounts and payments

Password reset uses the existing OTP delivery pipeline. Configure notification providers before expecting emails to arrive. Account changes are validated by the user service. Signing out calls the server logout endpoint; expired access credentials are refreshed first and the rotated refresh token is revoked.

Razorpay uses the existing backend test-mode integration. Set `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` and `RAZORPAY_WEBHOOK_SECRET` only on the backend. The React widget receives the public key, provider order ID, currency and amount from the order response. Callback verification happens on the server. Closing the widget does not mark an order paid, and payment failures remain retryable through My orders. Live-key acceptance and production provider verification are outside this test-mode integration.

## Support and analytics

Customers can create general or order-linked tickets. An order link must belong to the customer. Customers see and reply to their own tickets; admins can see all tickets, reply and mark OPEN/IN_PROGRESS/RESOLVED. A customer reply reopens a ticket. Each ticket supports up to 100 messages. This is an in-application support queue, not an email or live-chat service.

Analytics uses orders created within the selected rolling period. Booked sales includes confirmed through delivered orders and their delivery fees. Net collected revenue includes paid/collected orders and refunds still pending or failed; completed refunds are excluded and reported separately. Cancelled/expired/failed orders form the cancellation-rate numerator. Popular products count units from booked orders and show merchandise value before coupons and delivery. Daily buckets use UTC.

Reports allow 1–365 days and at most 10,000 orders per report. A larger cohort returns a request to narrow the period, rather than silently truncating the data. The report is an operational dashboard, not an accounting ledger.

## New API routes

| Method | Route | Access |
|---|---|---|
| GET | `/api/v1/products/{id}/variants` | Public active variants |
| POST | `/api/v1/products/admin/{id}/variants` | Admin |
| POST | `/api/v1/products/admin/{id}/stock` | Admin |
| GET | `/api/v1/products/admin/{id}/movements?page=0` | Admin |
| GET | `/api/v1/products/admin/low-stock?threshold=5&page=0` | Admin |
| POST | `/api/cart/merge` | Customer, Idempotency-Key required |
| GET | `/api/orders/attempts/{key}` | Checkout owner |
| POST | `/api/delivery/quote` | Signed-in customer |
| GET / PUT | `/api/admin/delivery` | Admin |
| GET | `/api/admin/analytics?days=30` | Admin |
| GET / POST | `/api/support` | Customer |
| POST | `/api/support/{id}/messages` | Ticket owner or admin |
| GET | `/api/admin/support` | Admin |
| PATCH | `/api/admin/support/{id}` | Admin |

## Verification

Backend tests cover variant stock isolation, role restrictions, idempotent movement records, guest merge retries and rollback, longest-prefix delivery matching, snapshotted checkout fees, attempt ownership, support ownership/admin replies and analytics calculations. Existing checkout, payment, cancellation, review, return and migration suites continue to run.

Frontend tests cover request serialization, refresh/retry, logout rotation, guest-cart merging after uncertain responses, and provider callback verification/dismissal. Browser checks use isolated disposable fixtures for visual and interaction verification; they do not substitute for running the complete MySQL/Kafka stack or verifying real payment/notification providers.
