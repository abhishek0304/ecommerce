# Shiprocket and Delhivery shipping

Order-service now supports selecting `SHIPROCKET` or `DELHIVERY` for one domestic Indian package covering all items in an order. Forward booking, approved return booking, pickup scheduling, tracking refresh and labels are exposed through the gateway. The storefront's **Shipments** button shows saved results; administrators can book, refresh tracking, request pickup and retrieve labels.

This implementation has automated local tests. **No real carrier account, staging booking, live booking, pickup or label has been verified in this workspace.** Mock responses are explicitly marked and are not proof of carrier integration success.

## Modes and credentials

Copy needed settings from [shipping.env.example](shipping.env.example) to the ignored root `.env`, or set the environment variables in your IDE/deployment. Existing `.env` files are not overwritten. Restart/rebuild order-service and the gateway after deploying the code.

| Mode | Shiprocket | Delhivery |
|---|---|---|
| `MOCK` (default) | Local simulation; no API calls | Local simulation; no API calls |
| `STAGING` | Rejected; no officially documented sandbox was established | Uses `https://staging-express.delhivery.com` with account-specific staging token |
| `LIVE` | Requires `SHIPPING_LIVE_ENABLED=true` and Shiprocket API token | Requires explicit opt-in, live token, and `DELHIVERY_BASE_URL=https://track.delhivery.com` |

`MOCK` is not a carrier sandbox. Its AWBs start with `MOCK-`, tracking returns `SIMULATED_BOOKED`, and label requests return `SIMULATED` with no URL. Saved bookings retain their original mode: changing the deployment mode does not convert a simulated shipment into a real one. Use a new test order for a new mode.

Shiprocket configuration requires an API-user bearer token, exact registered pickup location, and a channel ID for reverse orders. Obtain/refresh tokens using the provider account's authentication workflow; automatic token renewal is not implemented. Delhivery requires the appropriate environment's token, exact registered warehouse/pickup location and client name. Their API Playground may use a different account from your own staging token.

Set warehouse contact/address fields and GSTIN from your real registered warehouse. The booking form requires the consignee name/email and an Indian mobile in `+91` format; order addresses do not currently include recipient contact fields, so an administrator supplies those details explicitly. Delivery address and financial values always come from the saved order, not from the booking request. HSN is required outside MOCK and must apply to all items in this one-package implementation; split/mixed-HSN consignments are not supported. Supply an e-waybill when required by the provider. Check your carrier account's additional tax/compliance requirements before real use.

Do not commit provider credentials, addresses or personal test recipients. No real credentials or recipient defaults are included in the sample file or Postman environment.

## API

All writes require `ROLE_ADMIN`. `{direction}` is `FORWARD` or `RETURN`; `{id}` is the local order UUID.

| Method | Route | Purpose |
|---|---|---|
| POST | `/api/admin/orders/{id}/shipments/{direction}` | Create carrier order/manifest and assign AWB |
| GET | `/api/orders/{id}/shipments` | Read saved shipments as order owner or admin |
| POST | `/api/admin/orders/{id}/shipments/{direction}/tracking` | Refresh stored carrier tracking status |
| POST | `/api/admin/orders/{id}/shipments/{direction}/label` | Request provider label information |
| POST | `/api/admin/orders/{id}/shipments/{direction}/pickup` | Request collection; never confirms physical pickup |

Booking example (illustrative test recipient, not a live-delivery target):

```json
{
  "provider": "SHIPROCKET",
  "recipientName": "Test Customer",
  "recipientPhone": "+919999999999",
  "recipientEmail": "test@example.invalid",
  "weightKg": 0.5,
  "lengthCm": 10,
  "breadthCm": 10,
  "heightCm": 10,
  "hsn": "1234"
}
```

Forward booking requires CONFIRMED or PROCESSING and a captured online payment when applicable. Return booking requires DELIVERED plus an APPROVED full-order return. Provider creation is not physical shipment: use the existing fulfillment action only after the appropriate real-world handoff. The carrier/AWB from a completed forward booking are copied onto the order and prefilled in the admin fulfillment dialog; conflicting manual shipment details are rejected.

Pickup body is `{"date":"YYYY-MM-DD","time":"12:00:00"}`. Delhivery forward pickup uses the India-local date/time at the registered warehouse, for one package. Avoid submitting another request if the warehouse already has a scheduled pickup. Shiprocket chooses the available slot. Delhivery reverse pickup is automatically arranged after reverse booking, so the endpoint returns `AUTOMATIC_REVERSE_PICKUP` without making another request.

Shiprocket return AWB assignment includes `is_return=1`; its label endpoint can produce a PDF link. Delhivery reverse shipments use payment mode `Pickup`, no COD collection, and the registered return warehouse. Delhivery says return pickups do not require a packing slip: the return label endpoint reports `NOT_REQUIRED`. Forward Delhivery labels use its PDF packing-slip API and require a `pdf_download_link` in the response; response differences need verification with the selected account. The application does not download arbitrary provider label URLs server-side.

## Durable attempts, retries and limitations

One shipment per order/direction is persisted with a fingerprint of the original request. Identical retries return the saved result; changed contact, dimensions or provider return 409. The order is locked before eligibility checks and request creation. External network calls happen after that transaction commits, and HTTP retries are disabled.

Status progresses through `BOOKING` → `CREATED` → `BOOKED`. A timeout, provider rejection or unusable response after an attempt becomes `UNKNOWN`, preserving any known provider IDs. Process crashes can leave BOOKING/CREATED. **None of these uncertain states are automatically rebooked. HTTP 200 is not enough: inspect the status.** Pickup similarly persists REQUESTING before the call and never retries an uncertain request.

There is currently no automatic reconciliation or carrier-cancellation endpoint. For UNKNOWN or crash-left attempts, an operator must inspect the carrier dashboard using `<local-order-uuid>-F` or `-R` and resolve the operation before further action. Do not create another order merely to retry an uncertain external shipment. Customer cancellation is blocked once a forward provider booking has been requested, including an uncertain one, to prevent stock/refund changes while a carrier may still collect the parcel. Automated reconciliation/cancellation remains a follow-up integration capability.

Tracking refresh stores the provider's status and timestamp. Enable `SHIPPING_TRACKING_ENABLED=true` to poll up to 25 due non-MOCK shipments per worker run, with a 15-minute per-shipment interval. Last good tracking state survives provider errors. Terminal Delivered/Cancelled states are no longer polled in the normal interval. No unauthenticated carrier webhook is added.

Tracking deliberately does not mark COD money collected, receive returned inventory, or issue refunds. Those existing actions still require the store's explicit delivery/payment or return-inspection confirmation. Tracking data is visible under Shipments; existing order notifications continue to follow fulfillment events.

## Deployment

Compose forwards all settings in the sample to order-service. Kubernetes base and observability overlays reference the dedicated optional Secret `ecommerce-shipping-providers` only on order-service. Without it, defaults stay MOCK/live-disabled/tracking-disabled. Populate the same keys securely in the selected namespace and restart order-service after changing Secret-backed environment variables. Do not place shipping credentials into the notification-provider Secret.

The Flyway baseline includes `order_shipments` and `purchase_orders.provider_shipment_requested`. Existing databases require the [reviewed adoption procedure](../docs/OPERATIONS.md); Hibernate only validates schemas. No production database was migrated here.

The Postman collection now contains a **Shipping** reference folder with every route and sample response. Select a reference request as explained in its README; these requests do not run in the default COD-cancellation workflow, because carrier booking prevents ordinary cancellation.

## Validation and provider references

```powershell
.\mvnw.cmd -pl order-service,api-gateway test
python scripts/validate-infra.py
node --check api-gateway/src/main/resources/static/shipping.js
```

Tests cover ownership/admin boundaries, concurrent booking attempts, lost create/AWB/pickup responses, stale tracking preservation, mock/live separation, staging host restrictions, reversed addresses/COD values, and real local HTTP form encoding. They use mocks/local servers, not live carrier credentials.

Contracts were checked against [Shiprocket's official API collection](https://apidocs.shiprocket.in/), [Delhivery shipment creation](https://delhivery-express-api-doc.readme.io/reference/order-creation-api), [tracking](https://one.delhivery.com/developer-portal/document/b2c/detail/order-tracking), [pickup request](https://delhivery-express-api-doc.readme.io/reference/testpickup-request), and [reverse-pickup/label guidance](https://one.delhivery.com/developer-portal/document/b2c/detail/faq). Account-specific staging verification is still required.
