# Everyday React storefront

React customer frontend for the existing Spring services. Products, authentication, cart, addresses, and orders use real backend endpoints. No sample data is substituted when a service is unavailable.

## Run locally

Start the backend services using the repository README, including the API gateway on port 8081. Then:

```powershell
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. Vite proxies `/api` to http://localhost:8081, so local development does not require CORS changes. Set the `API_GATEWAY_URL` environment variable before starting Vite to use a different gateway.

If your Windows npm launcher is broken, use the npm CLI shipped alongside Node (on this workstation: `node D:/nodejs/node_modules/npm/bin/npm-cli.js install`).

## Build and serve with Spring

```powershell
npm run build:gateway
```

Restart/rebuild the gateway and open http://localhost:8081/react/index.html. Relative asset paths support the `/react/` directory. The original storefront and admin dashboard remain at `/index.html`.

For a separately hosted production frontend, set `VITE_API_BASE_URL` at build time to an API origin with explicitly configured CORS, or preferably configure your web server to proxy `/api` to the gateway. Never place backend secrets in Vite environment variables.

## Features

- Responsive catalog with debounced server search, categories, sorting, pagination, details, stock status, and image fallbacks.
- Registration/login, tab-scoped session storage, automatic token refresh, and sign out.
- Server-owned bag quantities/prices, item removal, address creation/selection, optional checkout coupon.
- Cash-on-delivery and Razorpay test checkout, coupon previews, delivery pricing, and persisted idempotency keys with checkout recovery.
- Order history, tracking information, refresh, and cancellation for eligible statuses.
- Wishlist, verified purchase reviews, full-order return requests and support tickets.
- Profile/address management, change password, OTP password recovery and server-side logout.
- Guest bag with atomic idempotent merge after login. Sign-in is required to place orders.
- React admin dashboard for products, variants, stock movements, low-stock alerts, fulfillment, returns/refunds, coupons, delivery rules, support and analytics.
- Loading, empty, error and retry states, accessible controls, focus-trapped dialogs, keyboard dismissal.

Use your catalog's exact category names when adjusting the category tabs in `src/App.jsx`. See [the feature guide](../docs/ECOMMERCE-FEATURES.md) for configuration, new routes, rollout and feature limits.

`npm test` checks API requests, refresh/logout, uncertain guest merges and payment callback behavior. `npm run build` checks JSX and the production bundle. Live checkout requires the full backend and its databases/Kafka; frontend tests do not prove those services are running. The backend V3 migrations must be applied by restarting the rebuilt product/cart/order services.
