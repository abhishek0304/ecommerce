> Docker, Kubernetes, Redis, resilience, tracing, and CI/CD: see [INFRASTRUCTURE.md](INFRASTRUCTURE.md).

HTTP clients use Spring Cloud OpenFeign. See [client configuration and migration notes](docs/FEIGN.md).

# Ecommerce services

## Postman API collection

Import the [collection](docs/postman/Ecommerce.postman_collection.json) and [local environment](docs/postman/Ecommerce.local.postman_environment.json). The [run guide](docs/postman/README.md) explains the automated customer checkout/cancellation flow, optional admin shipping/return/refund flow, all service endpoints and sample responses, and provider prerequisites.

## Customer storefront and admin dashboard

Open `http://localhost:8081/index.html` after starting the services. Product discovery and images, customer shopping pages, an admin dashboard, verified reviews, wishlists, coupons, and full-order returns are available. See [commerce setup, endpoints, and business rules](COMMERCE.md).

Requires JDK 21 or later. Maven is provided by the wrapper.

From the project root, build and test all eight services:

```powershell
.\mvnw.cmd clean verify
```

Tests use an in-memory H2 database and do not require MySQL, Kafka, Eureka, or a Config Server. Maven needs internet access on the first build to download dependencies.

The user and product services use explicit Java constructors and accessors so Eclipse does not require Lombok integration to compile them. After importing or updating the project in Eclipse/STS, refresh the projects and use Maven > Update Project, followed by Project > Clean.

For application startup, provide MySQL databases `userdb`, `productdb`, `cartdb`, `orderdb`, and `notificationdb` with credentials matching the configuration, and Kafka at `localhost:9092` (or set `KAFKA_BOOTSTRAP_SERVERS`).

To start local Kafka, open Docker Desktop and run from the project root:

```powershell
docker compose up -d --wait kafka
```

This uses the [official Apache Kafka image](https://kafka.apache.org/39/getting-started/docker/). The user service creates `user-events` at startup (one partition, replication factor one for local Kafka) and fails startup if Kafka is unavailable. Topic provisioning uses Spring Kafka's [NewTopic support](https://docs.spring.io/spring-kafka/reference/kafka/configuring-topics.html). Keep Kafka running while registering users; registration publishes events to this topic. Use `docker compose stop kafka` to stop it without deleting its container.

If registration reports `Topic user-events not present in metadata after 60000 ms`, start Docker Desktop, run the command above, and restart the user service. Verify the topic with:

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic user-events
```

If using a separate Kafka installation, set `KAFKA_BOOTSTRAP_SERVERS` to its reachable address before starting the user service and ensure the broker advertises addresses reachable from Windows. The service needs permission to create the topic, or it must be provisioned beforehand.

Start each service from its own directory with `.\mvnw.cmd spring-boot:run`, in this order:

1. `service-registry/service-registry` (port 8761)
2. `config-server/config-server` (port 8080)
3. `user-service/user-service` (port 8082) and `product-service` (port 8083)
4. `cart-service` (port 8084)
5. `order-service` (port 8085)
6. `notification-service` (port 8086)
7. `api-gateway` (port 8081)

The Config Server uses its configured Git repository by default. To use the configuration files in this workspace, run the following from `config-server/config-server`:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=native"
```

When starting from a different working directory, set `CONFIG_REPO_LOCATION` to the absolute file URI of `ecommerce-config-repo`, including the trailing slash.

Set `JWT_SECRET` for deployed environments. The bundled default is for development.


## Cart Service

See [Cart Service setup and endpoints](cart-service/README.md) for database setup, JWT authentication, request examples, and behavior. The gateway exposes the five cart endpoints at `http://localhost:8081/api/cart`; direct access uses port `8084`.

## Default products

On startup, product-service inserts six active sample products with prices and stock if the `products` table is empty. Existing products are left untouched, and restarting does not duplicate the samples. Set `PRODUCT_SEED_ENABLED=false` to disable this behavior. If all products are deleted, the next startup seeds them again while this setting is enabled.

The workspace configuration connects to `productdb` (singular), not `productsdb`. The initializer writes to whichever database the running service's `spring.datasource.url` selects. After restarting product-service, view the records with `GET http://localhost:8083/api/v1/products` and use the returned IDs in cart requests.


## Order Service

See [Order Service setup and checkout](order-service/README.md) for idempotent cash-on-delivery and Razorpay test payments. Create `orderdb`, restart product-service and cart-service for their internal checkout APIs, then start order-service and restart the gateway. Online checkout requires Razorpay test credentials; COD does not.

## Order lifecycle and notifications

See [expiration, refunds, fulfillment, and Kafka notifications](order-service/LIFECYCLE.md). Create `notificationdb`, start notification-service on port 8086, and restart product-service, order-service, and the gateway. Online payments expire after 15 minutes by default; late payments are refunded. Fulfillment changes require an admin token.

## Product API authorization

Product creation (`POST /api/v1/products`), editing (`PUT /api/v1/products/{id}`), and deletion (`DELETE /api/v1/products/{id}`) require a user-service access token containing `ROLE_ADMIN`. Set the same `JWT_SECRET` in user-service and product-service. Missing, invalid, or expired credentials return 401; valid customer tokens return 403. Product GET/HEAD requests remain public so product browsing and cart price lookup continue to work.

After restarting product-service, log in as a user who has the admin role and send `Authorization: Bearer YOUR_ACCESS_TOKEN` in Postman. Log in again after changing roles to obtain a fresh token. Internal inventory operations still require `X-Service-Key`; that key does not grant product administration privileges.
