# HTTP communication with OpenFeign

Cart, order, notification, and user services enable Spring Cloud OpenFeign. Each HTTP API
is an annotated interface; existing service classes retain validation and business rules.

| Module | Feign interface | Destination |
| --- | --- | --- |
| cart-service | ProductApi | Product catalog |
| order-service | CartApi, InventoryApi, UserApi | Cart, inventory, customer addresses |
| order-service | RazorpayApi | Payment and refund API |
| notification-service | UserContactApi | Customer contact and preferences |
| notification-service | TwilioApi | SMS and WhatsApp provider |
| user-service | NotificationApi | Durable account-message delivery queue |

Existing URL properties and environment overrides are retained (`product-service.url`,
`cart-service.url`, `user-service.url`, `services.user-url`, `services.notification-url`,
`razorpay.base-url`, and `messages.twilio.base-url`). Docker uses service DNS addresses;
IDE defaults use localhost. Named clients can use discovery when their URL is explicitly
empty, provided discovery/load balancing is enabled. Gateway routing remains unchanged.

`FeignConfiguration` defines a 3-second connection timeout and 5-second read timeout.
Provider-specific configurations give Razorpay and Twilio 8-second reads. Redirects are
disabled. `Retryer.NEVER_RETRY` leaves retries to the existing order recovery and message
delivery logic; an uncertain payment or send must not be blindly repeated.

Cart/order retain circuit breakers grouped by downstream authority. HTTP 429, 5xx, and
transport failures count toward opening a circuit; ordinary business 4xx responses do
not. `app.circuit-breaker.enabled=false` still disables this capability. The synchronous
Feign capability preserves thread context and does not introduce another execution pool.

Internal keys, customer bearer tokens, provider Basic authentication, and refund
idempotency headers are explicit parameters on the relevant API methods. No global
authentication interceptor can leak credentials to another service. Keep Feign logging
at its default NONE level because bodies and headers may contain account/payment data.

The explicit `feign-micrometer` dependency enables Spring Cloud's
`MicrometerObservationCapability` with the application's ObservationRegistry. HTTP client
spans and W3C headers therefore continue the current trace; Kafka/outbox tracing is unchanged.
See [Spring Cloud OpenFeign reference](https://docs.spring.io/spring-cloud-openfeign/reference/spring-cloud-openfeign.html).

Verification:

```powershell
.\mvnw.cmd -pl cart-service,order-service,notification-service,user-service/user-service test
```

Tests exercise checkout, uncertain payment/refund responses, authentication, cart/product
errors, provider form encoding, circuit breaker recovery, and Feign trace propagation.
