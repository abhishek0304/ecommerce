# Notification Service

Consumes `order-events` from Kafka and persists each supported event once in a user inbox, together with queued email, SMS, and WhatsApp deliveries. Also accepts email-only account-security messages from user-service over an internal HTTP API. Runs on port 8086 with MySQL database `notificationdb`.

Create `notificationdb`, then run `./mvnw.cmd -pl notification-service spring-boot:run` from the ecommerce root. Keep Kafka, the Config Server, and Eureka running. Database settings can be overridden with `NOTIFICATION_DB_URL`, `NOTIFICATION_DB_USERNAME`, and `NOTIFICATION_DB_PASSWORD`. Set `JWT_SECRET` to the same value as user-service and `KAFKA_BOOTSTRAP_SERVERS` to your broker.

Set the same `INTERNAL_SERVICE_KEY` in notification-service and user-service. Order deliveries fetch the customer's current contact details, active status, and channel preferences from user-service using `USER_SERVICE_URL` (default `http://localhost:8082`). Account-security emails use the recipient supplied by user-service and do not depend on order-notification preferences.

Use `GET /api/notifications?page=0&size=20` with the user-service Bearer access token, directly on port 8086 or through the gateway on 8081. Each user can only read their own inbox. Email through SMTP and SMS/WhatsApp through Twilio are also implemented, but disabled until configured. See [provider setup and delivery verification](PROVIDERS.md).

Delivery history is separate from the in-app inbox: `GET /api/notifications/deliveries?page=0&size=20` lists the customer's delivery attempts, and `GET /api/notifications/deliveries/{id}` retrieves one. Responses include channel, event type, order ID, status, attempt count, failure summary, and creation time; they omit recipients, message bodies, and provider IDs. Another customer's delivery returns 404.

The service-only `PUT /internal/messages/{id}` endpoint requires `X-Service-Key` and a UUID message ID. Its JSON body contains `userId`, `channel` (`EMAIL` only), `recipient`, `subject`, `body`, and `expiresAt` (in the future, at most one day away). It returns 202 with the queued status, not proof of sending. Reusing the ID with identical content returns the existing status; different content returns 409.

See [complete lifecycle documentation](../order-service/LIFECYCLE.md) for event delivery, setup, and testing.
