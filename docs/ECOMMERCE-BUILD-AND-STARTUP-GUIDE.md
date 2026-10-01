# Ecommerce Application Build and Startup Guide

This guide explains how to build and run the Spring Boot microservices and React frontend in this repository. It covers the development sequence, infrastructure, API integration, exact startup order, and a first customer checkout. Commands target Windows PowerShell and the workspace `D:\spring projects\ecommerce`.

Start infrastructure first, then service discovery and configuration, then the domain services, then the gateway, and finally React. Wait for each dependency to become ready before starting the next stage. The order for **implementing** features is different from the order for **running** services.

## 1 Architecture and service responsibilities

The browser sends requests to the API gateway. The gateway routes each request to the appropriate service. Each domain service owns its database; services communicate through HTTP APIs and Kafka events rather than querying one another's tables.

```text
React browser application                 port 5173 during development
              |
              | /api requests through the Vite proxy
              v
API gateway                              port 8081
  |-- User service                       port 8082 --> userdb
  |-- Product service                    port 8083 --> productdb
  |-- Cart service                       port 8084 --> cartdb
  |-- Order service                      port 8085 --> orderdb
  `-- Notification service               port 8086 --> notificationdb

Service registry                         port 8761
Config Server                            port 8080 in IDE development
Kafka                                    port 9092 for host clients
Redis                                    optional product cache
```

| Component | Repository directory | Responsibility |
|---|---|---|
| Service registry | `service-registry/service-registry` | Eureka registration and discovery |
| Config Server | `config-server/config-server` | Central configuration for IDE runs |
| API gateway | `api-gateway` | Public API routes and bundled web pages |
| User service | `user-service/user-service` | Registration, passwords, JWT login, refresh tokens, profiles and addresses |
| Product service | `product-service` | Product catalog, search, admin changes and inventory reservations |
| Cart service | `cart-service` | Customer cart, quantities and price estimates |
| Order service | `order-service` | Checkout, payment, order recovery, fulfillment, coupons, reviews and returns |
| Notification service | `notification-service` | Event-driven notifications and configured delivery providers |
| React storefront | `frontend` | Customer browsing, account access, cart, addresses and COD checkout |
| Shared configuration | `ecommerce-config-repo` | Local Config Server property files |

The React storefront supports cash on delivery and Razorpay test payment, account settings, wishlists, reviews, returns, and administration. See `docs/ECOMMERCE-FEATURES.md` for the latest extensions and rollout instructions. The existing storefront at `/index.html` remains available.

## 2 Tools and infrastructure

Install or configure these prerequisites:

- JDK 21 or later. Confirm `java -version` and ensure `JAVA_HOME` points to the JDK.
- The Maven wrapper supplied in the repository. A separate Maven installation is unnecessary.
- Node.js and npm for React. This workspace was built with Node 22.12.0.
- MySQL for IDE development, or Docker Desktop with Linux containers for container development.
- Kafka. User service provisions its topic and fails startup when Kafka is unavailable.
- Git and an IDE such as IntelliJ IDEA, Eclipse or STS for development.

Run version checks from the project root:

```powershell
Set-Location 'D:\spring projects\ecommerce'
java -version
.\mvnw.cmd -version
node --version
npm --version
docker version
```

### Databases for IDE development

Create the five databases on the MySQL instance used by the service configuration:

```sql
CREATE DATABASE IF NOT EXISTS userdb;
CREATE DATABASE IF NOT EXISTS productdb;
CREATE DATABASE IF NOT EXISTS cartdb;
CREATE DATABASE IF NOT EXISTS orderdb;
CREATE DATABASE IF NOT EXISTS notificationdb;
```

The local user and product property files currently use MySQL on `localhost:3306` with the development account `root` and password `1234`. Update those files to match your local credentials. Cart, order and notification configurations also support their documented database environment overrides. Do not assume an environment override works unless its property file references it.

Flyway owns schema migrations and Hibernate validates the resulting schema. For empty databases, migrations create the tables. An existing populated database needs the reviewed baseline procedure in `docs/OPERATIONS.md`; changing Hibernate to auto-create tables is not the migration procedure.

### Shared settings

Keep `JWT_SECRET` consistent across services that validate user tokens. Keep `INTERNAL_SERVICE_KEY` consistent across services that authenticate internal calls. Use the same values in every relevant IDE run configuration or terminal. Environment variables set in one terminal do not update terminals that are already open.

The Docker `.env` file is read by Compose. Plain Maven/IDE runs do not automatically load that file. Docker databases use separate containers and volumes; their credentials and stored data are independent of an existing host MySQL installation.

## 3 Recommended implementation order

Use the existing Maven modules and source files as the working examples. Build a small usable journey first, then add the advanced commerce features.

### Step 1 Establish the project structure

Create the root Maven aggregator, then create the eight Spring modules listed above. Match the JDK, Spring Boot and Spring Cloud versions already declared in the project POM files. Give each application a unique name and port. Separate HTTP controllers, validation DTOs, business services and repositories.

For each database-backed module, configure MySQL, create versioned Flyway migrations and enable schema validation. Decide who owns each record before implementing cross-service calls.

### Step 2 Configure discovery and configuration

Implement Eureka in service-registry. Implement Config Server and configuration files for each service. Register the domain services with Eureka. Use the native Config Server profile while working with this checkout so changes to local property files are loaded instead of the configured remote Git repository.

Containers use `application-container.properties` and disable remote Config Server loading. Maintain container settings alongside IDE settings when adding a new configuration value.

### Step 3 Implement users and authentication

Implement registration with input validation and password hashing, login, short-lived JWT access tokens and refresh-token rotation. Add profile and address APIs, enforcing ownership on the server. Registration must never grant an administrator role.

The main customer contracts are `POST /api/users/register`, `POST /api/auth/login`, `POST /api/auth/refresh`, `GET /api/users/me`, and `GET/POST /api/addresses`. Verify authentication and ownership before connecting the frontend.

### Step 4 Implement catalog and inventory

Create the product schema and DTOs, including ID, SKU, name, description, price, stock quantity, category, active flag and image URL. Add public product listing/detail APIs, search, sorting and pagination. Protect product creation, editing and deletion with `ROLE_ADMIN`.

Implement inventory reservations separately from normal catalog updates. Repeating a reservation or release must not deduct or restore stock twice. The existing initializer seeds six sample products only when the products table is empty.

### Step 5 Implement the cart

Store the cart by authenticated customer ID. Add item creation, quantity updates, removal and clearing. Resolve product information through product-service and enforce valid quantities and availability. Return server-calculated totals. Maintain a cart version so checkout can distinguish a later cart from an earlier purchase.

### Step 6 Implement checkout and orders

Read the customer's address and cart through service APIs. Reserve stock in product-service and save an immutable snapshot of items, prices and shipping address on the order. Accept cash on delivery first; it requires no payment-provider credentials.

Require an `Idempotency-Key` on order creation. Persist order progress and recovery state so an outage cannot silently lose a checkout. Clear only the cart version purchased. Add cancellation and stock release before adding online payment, refunds and fulfillment.

### Step 7 Implement events and notifications

Use Kafka for user and order events. Record order events in a durable outbox before publishing so a database commit and a temporary broker outage can be recovered. Handle duplicate delivery safely. Add notification providers only after configuring their accounts; an event being stored is not proof that an email or SMS was delivered.

### Step 8 Configure the gateway

Route `/api/v1/products` to product-service; `/api/auth`, `/api/users` and `/api/addresses` to user-service; `/api/cart` to cart-service; `/api/orders` and commerce endpoints to order-service; and `/api/notifications` to notification-service. Domain services must enforce token validity, roles and ownership themselves.

### Step 9 Build and connect React

Implement the customer screens and API client described in the next section. Complete registration → login → cart → address → COD order before adding optional payment screens or administrative pages.

### Step 10 Add advanced features and deployment checks

Extend the system with coupons, wishlists, delivered-item reviews, returns, Razorpay test payment and shipping integration. Add Redis caching, circuit breakers, tracing, health checks and deployment configuration. Verify provider integrations and production settings separately from local unit tests.

## 4 React frontend implementation

The frontend uses React, Vite and Lucide icons. These files are the starting points:

| File | Purpose |
|---|---|
| `frontend/src/App.jsx` | Storefront, search, product dialog, login, cart, checkout and order screens |
| `frontend/src/api.js` | JSON HTTP client, bearer headers, token refresh, session storage and errors |
| `frontend/src/styles.css` | Responsive layout, catalog cards and dialog styles |
| `frontend/vite.config.js` | Development proxy and production asset settings |
| `frontend/scripts/sync-gateway.js` | Copies the production build into the gateway |
| `frontend/tests/api.test.js` | API client request, refresh, retry and error tests |

### Install and start

```powershell
Set-Location 'D:\spring projects\ecommerce\frontend'
npm install
npm run dev
```

Open `http://localhost:5173`. The browser requests `/api/...` from Vite, which forwards requests to `http://localhost:8081`. This arrangement uses the same browser origin and avoids development CORS changes.

If a different gateway port is needed, set its target before starting Vite:

```powershell
$env:API_GATEWAY_URL = 'http://localhost:8081'
npm run dev
```

If the npm launcher on this workstation reports a missing `npm-cli.js`, use the CLI shipped alongside Node:

```powershell
node D:/nodejs/node_modules/npm/bin/npm-cli.js install
node D:/nodejs/node_modules/npm/bin/npm-cli.js run dev
```

### Backend contracts used by React

| Action | Method and endpoint | Request or response |
|---|---|---|
| Browse | `GET /api/v1/products` | Paginated `content`, `totalElements`, `totalPages`; query/category/sort/page parameters |
| Register | `POST /api/users/register` | `{name, email, phone, password}` |
| Login | `POST /api/auth/login` | `{email, password}` → `{accessToken, refreshToken, user}` |
| Refresh | `POST /api/auth/refresh` | `{refreshToken}` → rotated credentials |
| Read bag | `GET /api/cart` | Items, quantities, totalQuantity and totalPrice |
| Add item | `POST /api/cart/items` | `{productId, quantity}` |
| Change quantity | `PUT /api/cart/items/{productId}` | `{quantity}` |
| Remove item | `DELETE /api/cart/items/{productId}` | Empty successful response |
| Delivery addresses | `GET/POST /api/addresses` | Create with `{line1, line2, city, state, postalCode, country}` |
| Place order | `POST /api/orders` | Idempotency header and checkout body shown below |
| Order history | `GET /api/orders` | Customer's paginated orders |
| Cancel | `POST /api/orders/{id}/cancel` | Eligible order cancellation |

For authenticated endpoints, the client sends `Authorization: Bearer ACCESS_TOKEN`. Sessions are stored per browser tab, and an expired token triggers a refresh before retrying the original request. React sign-out revokes the current refresh token on the server and clears the local session.

Checkout sends the selected address and payment choice, not browser-supplied prices:

```http
POST /api/orders
Authorization: Bearer ACCESS_TOKEN
Idempotency-Key: a-persisted-uuid-for-this-checkout
Content-Type: application/json

{"addressId":1,"paymentMethod":"CASH_ON_DELIVERY"}
```

An optional `couponCode` can be added. The server calculates authoritative prices, discounts and delivery fees. After a timeout or processing response, reuse the same key and body or check Orders. React provides Check saved checkout and permits a new attempt after a confirmed terminal failure. Adjust the cart before starting a new purchase so its version changes.

### Serve React from Spring

```powershell
Set-Location 'D:\spring projects\ecommerce\frontend'
npm run build:gateway
```

This builds `frontend/dist` and copies it into `api-gateway/src/main/resources/static/react`. Rebuild/restart the gateway, then open `http://localhost:8081/react/index.html`. The generated gateway directory is Git-ignored, so regenerate it before packaging a fresh checkout. The original storefront remains at `http://localhost:8081/index.html`.

For a separate production web host, proxy `/api` to the gateway or configure `VITE_API_BASE_URL` at build time and explicit backend CORS. Keep secrets out of Vite variables and frontend source.

## 5 Exact startup order for IDE development

Use this path when Spring services run in IntelliJ, STS or separate terminals and MySQL runs on the host.

| Stage | Start | Port | Wait for |
|---|---|---|---|
| 0 | Host MySQL and Kafka | 3306 and 9092 | Database connections and Kafka readiness |
| 1 | Service registry | 8761 | Eureka dashboard responds |
| 2 | Config Server with native profile | 8080 | Local configuration is returned |
| 3 | Product service and user service | 8083 and 8082 | Startup completes; both register with Eureka |
| 4 | Cart service | 8084 | Ready and registered; product lookup available |
| 5 | Order service | 8085 | Ready and registered; user/product/cart dependencies available |
| 6 | Notification service | 8086 | Ready and connected to Kafka |
| 7 | API gateway | 8081 | Catalog request through gateway succeeds |
| 8 | React frontend | 5173 | Vite ready; browser loads storefront |

Product and user services can start in either order once their databases, Kafka and configuration are ready. Start both before cart/order checkout tests. Notification delivery should be ready before testing the complete customer event flow.

### Prepare and build once

From the root, first build and test all eight Spring modules:

```powershell
Set-Location 'D:\spring projects\ecommerce'
.\mvnw.cmd clean verify
```

Start Docker Desktop if Kafka is hosted in Docker. With the current Compose file, generate `.env` only if it does not exist, then start Kafka:

```powershell
if (-not (Test-Path .env)) { .\scripts\init-env.ps1 }
docker compose up -d --wait kafka
```

### Open one terminal for each service

Run the following commands **separately**, in order. Each Spring command occupies its terminal. Commands use the root Maven wrapper and `-pl` module selection, avoiding reliance on wrappers inside every module.

**Terminal 1 — service registry**

```powershell
Set-Location 'D:\spring projects\ecommerce'
.\mvnw.cmd -pl service-registry/service-registry spring-boot:run
```

**Terminal 2 — Config Server**

```powershell
Set-Location 'D:\spring projects\ecommerce'
$env:CONFIG_REPO_LOCATION = 'file:///D:/spring%20projects/ecommerce/ecommerce-config-repo/'
.\mvnw.cmd -pl config-server/config-server spring-boot:run "-Dspring-boot.run.profiles=native"
```

The absolute URI makes configuration loading independent of the current directory. The native profile uses the local configuration directory; without it, Config Server uses its configured remote Git repository. Restart dependent services after changing their configuration.

**Terminal 3 — product service**

```powershell
Set-Location 'D:\spring projects\ecommerce'
.\mvnw.cmd -pl product-service spring-boot:run
```

**Terminal 4 — user service**

```powershell
Set-Location 'D:\spring projects\ecommerce'
.\mvnw.cmd -pl user-service/user-service spring-boot:run
```

**Terminal 5 — cart service**

```powershell
Set-Location 'D:\spring projects\ecommerce'
.\mvnw.cmd -pl cart-service spring-boot:run
```

**Terminal 6 — order service**

```powershell
Set-Location 'D:\spring projects\ecommerce'
.\mvnw.cmd -pl order-service spring-boot:run
```

**Terminal 7 — notification service**

```powershell
Set-Location 'D:\spring projects\ecommerce'
.\mvnw.cmd -pl notification-service spring-boot:run
```

**Terminal 8 — gateway**

```powershell
Set-Location 'D:\spring projects\ecommerce'
.\mvnw.cmd -pl api-gateway spring-boot:run
```

**Terminal 9 — React**

```powershell
Set-Location 'D:\spring projects\ecommerce\frontend'
npm install
npm run dev
```

Install dependencies on the first run or when the dependency files change. On subsequent runs, `npm run dev` is enough. Wait for a successful startup message and dependency readiness, rather than using a fixed delay. Eureka registration and route discovery may take a little longer than the initial startup message.

## 6 Docker startup path

Use this path when Docker manages the backend and its databases. Stop duplicate IDE processes using the same exposed ports. Do not combine host database settings with container database URLs.

### First build and startup

```powershell
Set-Location 'D:\spring projects\ecommerce'
if (-not (Test-Path .env)) { .\scripts\init-env.ps1 }
docker compose --profile app build
.\scripts\start-core.ps1
```

The explicit build prepares the local application images before the sequential startup script, which does not itself use `--build`. After changing backend source, rebuild the affected images before running the script again.

The startup script follows this exact sequence:

```text
user-db → product-db → cart-db → order-db → notification-db
→ redis → kafka → service-registry
→ product-service → user-service → cart-service → order-service
→ notification-service → api-gateway
```

It waits for health at each step and preserves database volumes. Config Server is left stopped because container applications load bundled configuration and disable remote Config Server imports. Tracing services are stopped by default; use `-WithTracing` to enable Tempo and Grafana.

On machines with enough memory, Compose can start the full application profile and resolve its health-based dependencies:

```powershell
docker compose --profile app up -d --build --wait --wait-timeout 600
```

This is an alternative to sequential startup, not a command you must run after it. The full profile includes Config Server even though container applications do not need it for configuration loading.

### Connect React to the Docker backend

For frontend development, run `npm run dev` in `frontend` after the gateway is ready. The gateway remains accessible at `http://localhost:8081`.

To package the React build in the Docker gateway image, first run `npm run build:gateway`, then rebuild/recreate the gateway:

```powershell
Set-Location 'D:\spring projects\ecommerce\frontend'
npm run build:gateway
Set-Location 'D:\spring projects\ecommerce'
docker compose --profile app up -d --build --no-deps api-gateway
```

Open `http://localhost:8081/react/index.html`. There is no separate React container in the current Compose configuration.

### Inspect and stop

```powershell
docker compose --profile app ps
docker compose logs --tail 100 order-service
docker compose --profile app stop
```

`stop` preserves containers and stored data. Do not use volume deletion as a routine restart. Container management health runs on port 9090 inside each container; do not assume every service exposes this port on the host.

## 7 Verify the complete customer journey

### Check discovery and public APIs

For IDE runs, open Eureka at `http://localhost:8761` and confirm that the domain services and gateway appear. For Docker runs, use Compose health/status and container logs because the registry port is not necessarily published to the host.

Check native configuration in IDE mode:

```powershell
Invoke-RestMethod 'http://localhost:8080/product-service/default'
```

Check catalog routing through the gateway:

```powershell
$catalog = Invoke-RestMethod 'http://localhost:8081/api/v1/products?page=0&size=20'
$catalog.content | Select-Object id, name, price, stockQuantity
```

An empty product database is seeded at product-service startup unless `PRODUCT_SEED_ENABLED=false`. Use returned IDs in cart calls; do not assume that product IDs or address IDs start at 1.

### Test from the React browser

1. Open `http://localhost:5173` or the bundled `/react/index.html` page.
2. Browse products without logging in. Try search, category filtering, sorting and product details.
3. Register with a valid email, a phone containing 7–15 digits and a password of at least 8 characters.
4. Sign in and add an in-stock product to the bag.
5. Change quantity, then remove an item and verify the server total updates.
6. Continue to checkout. Create a delivery address if none exists, or choose an existing address.
7. Place a cash-on-delivery order. Check the returned order in My orders.
8. Refresh Orders to check any processing response. Verify cart cleanup and stock changes.
9. Test cancellation on an eligible order. Confirm the final backend status and inventory restoration.
10. Sign out and confirm protected actions require sign-in again.

If using online payment through the original storefront, configure Razorpay **test** credentials and the webhook secret in the order service environment. COD does not require them. Shipping and notification providers also need their own credentials and live verification.

### Automated checks

```powershell
Set-Location 'D:\spring projects\ecommerce'
.\mvnw.cmd clean verify
Set-Location frontend
npm test
npm run build
```

Backend tests use H2 and mocked/local dependencies rather than your live MySQL/Kafka stack. Frontend tests cover the HTTP client; a successful build checks JSX and bundling. Run the browser journey against the running services to verify the complete integration. The Postman collection and local environment in `docs/postman` provide additional end-to-end API scenarios.

## 8 Common problems and fixes

| Symptom | What to check |
|---|---|
| React loads but no products appear | Confirm gateway 8081 and product-service are ready; inspect the failed `/api/v1/products` request and use Try again |
| Connection refused to Config Server | Start it before domain services; use native profile and the correct local configuration URI |
| Registration fails or Kafka topic times out | Start Kafka, confirm advertised host address, and restart user-service after broker readiness |
| MySQL access denied | Match service credentials to the database instance; Docker `.env` credentials do not configure host MySQL automatically |
| Flyway fails on existing tables | Follow `docs/OPERATIONS.md` for reviewed baseline adoption; preserve existing data |
| Gateway returns 503 | Check downstream readiness, Eureka registration, routing and network reachability |
| Authenticated endpoint returns 401 | Sign in again; check token expiry and matching JWT secrets across services |
| Product administration returns 403 | Use an existing `ROLE_ADMIN` account; ordinary registration creates a customer |
| Port already in use | Stop the duplicate IDE/container service using that port before restarting |
| Checkout times out or returns processing status | Check Orders or retry the same key/body; do not create arbitrary new attempts |
| Online checkout returns 503 | Configure order-service Razorpay test keys or use COD |
| Container changes are not visible | Rebuild affected images and recreate the services |
| Bundled React page returns 404 | Run `npm run build:gateway`, then rebuild/restart the gateway |
| npm reports missing CLI | Use the Node-adjacent npm CLI command shown in section 4 |

For IDE runs, normally stop React and the gateway first, then notification/order/cart/user/product, then configuration and discovery. Stop shared infrastructure after its clients have stopped. For Docker, use Compose stop and leave the data volumes intact.

## 9 Further project references

- `README.md` — repository overview, backend build and default products.
- `frontend/README.md` — React setup, build integration and feature scope.
- `INFRASTRUCTURE.md` — Docker, Redis, health, tracing and Kubernetes.
- `COMMERCE.md` — customer/admin features, coupons, reviews and returns.
- `cart-service/README.md` — cart API and behavior.
- `order-service/README.md` — checkout, idempotency and Razorpay test payment.
- `order-service/LIFECYCLE.md` — recovery, cancellation, fulfillment and notifications.
- `order-service/SHIPPING.md` — shipping providers and local simulation.
- `docs/postman/README.md` — Postman customer and admin API workflows.
- `docs/OPERATIONS.md` — schema adoption, backups and recovery.
- `docs/PRODUCTION-DEPLOYMENT.md` — production prerequisites and verification.

### Startup order to remember

**IDE:** MySQL and Kafka → Eureka → native Config Server → product and user → cart → order → notification → gateway → React.

**Docker sequential script:** five databases → Redis → Kafka → Eureka → product → user → cart → order → notification → gateway; then start React separately or serve its prebuilt files from the gateway.
