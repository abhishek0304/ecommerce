# Storefront and commerce features

The customer storefront and admin dashboard are bundled in api-gateway. Rebuild and restart product-service, cart-service, order-service, notification-service, and api-gateway, then open **http://localhost:8081/index.html**. No Node build or separate frontend server is required. Existing authentication, carts, addresses, and payments are used through the gateway.

For Docker installations, use the normal project startup workflow in `INFRASTRUCTURE.md` to rebuild the services. For IDE installations, rebuild with `./mvnw.cmd clean verify` and restart those five services. Existing MySQL data is retained; the configured Hibernate `ddl-auto=update` adds the new tables/columns. Deployments that disable automatic schema updates must provision these changes before starting the new code.

## Customer journey

1. Browse without signing in. Search names, filter category/price/stock, sort, and paginate. Hidden products are excluded from customer listing and detail endpoints.
2. Create an account or sign in from the header. Save products using the heart, and move saved products to the bag. If an item is already in the bag, moving it preserves its quantity.
3. Adjust cart quantities, add an address, select COD or Razorpay test payment, and optionally check a coupon. The displayed coupon quote is an estimate; checkout recalculates using inventory's authoritative prices.
4. Place the order. The browser saves the idempotency key for that user, cart version/contents, and checkout request in session storage. Retry an uncertain request with the same details, or inspect Orders. The version distinguishes a later purchase of the same items. Pending online orders expose a Pay button. Razorpay test keys must be configured as described in `order-service/README.md`.
5. View order status, shipping carrier/tracking number, cancellation/refund status, and return decisions under Orders. Refresh to retrieve the latest status. Review delivered items from the same screen.

Authentication is stored in session storage for the current browser tab. The frontend refreshes expired access tokens through the existing refresh endpoint. Sign out revokes the refresh token and clears browser session credentials. Admin visibility in the UI is only a convenience: the backend enforces all roles and ownership independently.

## Administrator journey

Sign in with an existing `ROLE_ADMIN` account to access Admin. Registration never grants administrator access.

- **Products:** create/edit name, SKU, description, category, price, available stock, visibility, and an optional HTTPS image URL. Empty image URLs use a built-in placeholder. Products with reserved or committed stock must be hidden rather than deleted, preserving the ability to restock returns. A stock value is available stock, excluding units already reserved for checkout.
- **Orders & returns:** list all orders, advance confirmed → processing → shipped → delivered, enter carrier/tracking when shipping, and confirm cash collection for COD delivery. Retry failed online refunds and handle returns here.
- **Coupons:** create immutable percentage or fixed-INR discount rules with expiry, minimum spend, and a global usage limit. Activate/deactivate codes without changing past order totals.

## API additions

All routes below are exposed by the gateway. Product routes are implemented in product-service; other new commerce routes are in order-service. Existing order/customer endpoints are unchanged except for additive response fields and the optional checkout `couponCode`.

| Method | Endpoint | Access / purpose |
|---|---|---|
| GET | `/api/v1/products?query=phone&category=Electronics&minPrice=100&maxPrice=50000&inStock=true&page=0&size=20&sort=price,asc` | Public; combined discovery filters |
| GET | `/api/v1/products/admin/catalog` | Admin; includes hidden products |
| GET | `/api/reviews/products/{productId}?page=0&size=10` | Public; average, count, paginated verified reviews |
| POST | `/api/orders/{orderId}/reviews` | Owner; `{productId, rating, comment}` |
| GET | `/api/wishlist?page=0&size=20` | Customer's own saved product IDs |
| PUT / DELETE | `/api/wishlist/{productId}` | Save / remove own wishlist entry |
| POST | `/api/coupons/quote` | Signed-in estimate: `{code, subtotal}` |
| GET / POST | `/api/admin/coupons` | Admin; list / create coupons |
| PATCH | `/api/admin/coupons/{code}` | Admin; `{active: true}` |
| GET | `/api/admin/orders` and `/api/admin/orders/{id}` | Admin order listing / details |
| POST | `/api/orders/{id}/returns` | Owner; `{reason}` |
| PATCH | `/api/admin/orders/{id}/return` | Admin; `{decision: "APPROVED" or "REJECTED", note}` |
| POST | `/api/admin/orders/{id}/return/receive` | Admin; `{restock: true or false}` |
| POST | `/api/admin/orders/{id}/refund/manual` | Admin; `{reference}` for a completed COD refund |

Example coupon:

```json
{
  "code": "WELCOME10",
  "type": "PERCENT",
  "value": 10,
  "minimumSpend": 500,
  "expiresAt": "2027-12-31T23:59:59Z",
  "usageLimit": 100
}
```

Checkout accepts `{"addressId":1,"paymentMethod":"CASH_ON_DELIVERY","couponCode":"WELCOME10"}`. Response fields include `subtotal`, `discount`, `couponCode`, and `returnRequest`, alongside the existing `totalPrice` (the amount actually payable).

## Discount and return rules

- Codes are case-insensitive. One coupon per order; percentage discounts are rounded to two decimals. Discounts are capped to leave at least INR 1 payable, consistently for COD and online payments. Coupons cannot change after creation; create a new code for a new rule.
- Coupon validation and usage accounting hold a database lock in the same transaction as the order price snapshot. An idempotent retry does not spend another use. Failed, expired, and cancelled orders release their use; delivered/returned orders keep it consumed. Prices/discounts are retained on the order for refunds and history.
- If a coupon becomes invalid before stock reservation completes, checkout fails durably and releases the reserved stock through the recovery flow. The customer must edit/refill the cart and use a new checkout attempt; an old idempotency key keeps its original result.
- Reviews require an owned order with a delivery timestamp and the reviewed product in its saved items. Rating is 1–5, comment length 1–2000, and each customer can review a product once. Public review responses do not expose customer or order identifiers.
- Returns cover **all items in an order**, requested within **30 days of delivery**, one request per order. A return request does not immediately refund or restock anything. Admin approval, then physical receipt, are required. Rejected requests cannot be received.
- At receipt, admins choose whether all units are resalable. Restocking/discarding is idempotent at product-service and the decision cannot be changed on retry. Inventory outages leave a durable `RETURN_RECEIVING` state for the existing recovery worker.
- Online refunds reuse the existing Razorpay refund machinery and refund the discounted amount actually paid. COD refunds require an external payment by the store; recording its reference marks it refunded. The application does not initiate bank transfers for COD refunds.
- Return events use the existing outbox/Kafka notification pipeline. Carrier integration, automatic return labels, partial item returns/refunds, and moderation/editing of reviews are not included in this initial implementation.

## Verification

```powershell
./mvnw.cmd -pl product-service,order-service,api-gateway,notification-service test
node --check api-gateway/src/main/resources/static/store.js
```

The regression suites cover combined search, pagination, image validation, hidden products, role/ownership boundaries, coupon concurrency and cancellation accounting, verified reviews, wishlist isolation, return eligibility, duplicate inventory receipt, discarded returns, COD refund recording, and online refund recovery. Tests use H2 and mocked/local HTTP dependencies; they do not charge a payment provider.
