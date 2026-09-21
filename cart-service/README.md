# Cart Service

Stores each user's cart in MySQL. The user ID comes from the signed access token issued by user-service.

## Start locally

1. In MySQL, run `CREATE DATABASE IF NOT EXISTS cartdb;`.
2. Keep the Config Server, service registry, and product-service running.
3. From the ecommerce project root, run:

```powershell
.\mvnw.cmd -pl cart-service spring-boot:run
```

The service listens on **8084** and registers with Eureka as `cart-service`. Import `cart-service/pom.xml` as an Existing Maven Project in Eclipse/STS if needed.

Restart the API Gateway after rebuilding it to use **http://localhost:8081/api/cart**. The gateway preserves the path and Authorization header. Direct service access is **http://localhost:8084/api/cart**.

Local defaults are included in the service, so the existing Git-backed Config Server can still be used. The workspace config repository also includes `cart-service.properties`; use the Config Server's native profile to load it. See the root README for native-profile startup.

Environment overrides:

| Variable | Default |
| --- | --- |
| CART_DB_URL | jdbc:mysql://localhost:3306/cartdb |
| CART_DB_USERNAME | root |
| CART_DB_PASSWORD | 1234 |
| PRODUCT_SERVICE_URL | http://localhost:8083 |
| JWT_SECRET | Same development default as user-service |

When setting `JWT_SECRET`, use the **same value in user-service and cart-service**. Product requests have a 3-second connection timeout and a 5-second read timeout.

## Authentication

Log in with `POST http://localhost:8082/api/auth/login`, then use the response's **accessToken**, not refreshToken. In Postman, select Authorization > Bearer Token and paste it. All five endpoints require:

```http
Authorization: Bearer YOUR_ACCESS_TOKEN
```

## Endpoints

| Method | Endpoint | Purpose | Success |
| --- | --- | --- | --- |
| GET | /api/cart | View your cart | 200 |
| POST | /api/cart/items | Add a product (increment existing quantity) | 200 |
| PUT | /api/cart/items/{productId} | Replace quantity | 200 |
| DELETE | /api/cart/items/{productId} | Remove a product | 204 |
| DELETE | /api/cart | Clear the cart | 204 |

POST JSON body (replace 1 with an existing active product ID):

```json
{"productId": 1, "quantity": 2}
```

PUT JSON body:

```json
{"quantity": 3}
```

GET, POST, and PUT return the full cart:

```json
{
  "userId": 1,
  "items": [
    {
      "productId": 1,
      "name": "Example product",
      "quantity": 2,
      "unitPrice": 12.50,
      "subtotal": 25.00,
      "available": true
    }
  ],
  "totalQuantity": 2,
  "totalPrice": 25.00
}
```

Quantities and product IDs must be positive. Use DELETE to remove an item rather than setting quantity to zero. Deleting a missing item or an empty cart succeeds with 204. Updating an item absent from your cart returns 404.

Prices are fetched from the product service each time a cart is returned. Totals are estimates, not locked checkout prices. Adding or updating checks that the product is active and the resulting quantity is within current stock; it never changes or reserves stock.

Existing items with reduced stock or inactive products remain visible with `available: false`. A deleted product remains removable with a placeholder name, null price/subtotal, and is excluded from totalPrice. totalQuantity still includes all items. Checkout must revalidate availability and pricing.

Errors: **400** invalid request; **401** missing/invalid/expired token; **404** missing product or cart item; **409** inactive product, insufficient stock, or concurrent cart modification (reload and retry); **503** product-service failure. Failed mutations roll back. Removing and clearing items work even when the product service is down.

## Verify

From the project root:

```powershell
.\mvnw.cmd -pl cart-service,api-gateway test
```

Tests use H2 and a local HTTP product stub, with real JWT verification and HTTP client calls. They cover the cart lifecycle, totals and repricing, user isolation, invalid/expired tokens, validation, stock restrictions, rollback on dependency failure, deleted products, optimistic concurrency, and gateway forwarding.

Gateway routing follows Spring Cloud Gateway's [Java route configuration](https://spring.io/guides/gs/gateway/).
