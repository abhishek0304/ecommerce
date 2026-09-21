# Notification Service

Consumes `order-events` from Kafka and persists each event once in a user inbox. Runs on port 8086 with MySQL database `notificationdb`.

Create `notificationdb`, then run `./mvnw.cmd -pl notification-service spring-boot:run` from the ecommerce root. Keep Kafka, the Config Server, and Eureka running. Database settings can be overridden with `NOTIFICATION_DB_URL`, `NOTIFICATION_DB_USERNAME`, and `NOTIFICATION_DB_PASSWORD`. Set `JWT_SECRET` to the same value as user-service and `KAFKA_BOOTSTRAP_SERVERS` to your broker.

Use `GET /api/notifications?page=0&size=20` with the user-service Bearer access token, directly on port 8086 or through the gateway on 8081. Each user can only read their own inbox. Notifications are in-app; no email or SMS provider is configured.

See [complete lifecycle documentation](../order-service/LIFECYCLE.md) for event delivery, setup, and testing.
