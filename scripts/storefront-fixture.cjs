// Isolated browser-test fixture. Never used by the real gateway or application.
// Run: node scripts/storefront-fixture.cjs (http://127.0.0.1:8189/index.html)
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../api-gateway/src/main/resources/static');
const products = [
  { id: 1, sku: 'HEADPHONES', name: 'Studio wireless headphones', category: 'Electronics', price: 2499, stockQuantity: 12 },
  { id: 2, sku: 'LAMP', name: 'Arc desk lamp', category: 'Home', price: 1899, stockQuantity: 8 },
  { id: 3, sku: 'BAG', name: 'Daily carry backpack', category: 'Accessories', price: 1599, stockQuantity: 15 },
  { id: 4, sku: 'BOTTLE', name: 'Insulated steel bottle', category: 'Lifestyle', price: 799, stockQuantity: 20 }
].map(p => ({ ...p, active: true, description: 'Designed for the way you live. A simple, useful addition to your everyday routine.', imageUrl: null }));
let cart = [], version = 1, saved = [], addresses = [{ id: 1, line1: '10 Test Road', city: 'Pune', state: 'Maharashtra', postalCode: '411001', country: 'India' }], coupons = [];
let orders = [{ id: '11111111-1111-4111-8111-111111111111', status: 'DELIVERED', paymentMethod: 'CASH_ON_DELIVERY', paymentStatus: 'COLLECTED', items: [{ productId: 1, name: products[0].name, quantity: 1, price: 2499 }], totalPrice: 2499, subtotal: 2499, discount: 0, shippingAddress: addresses[0], createdAt: new Date().toISOString(), deliveredAt: new Date().toISOString(), carrier: 'Test carrier', trackingNumber: 'TRACK-123', refund: { status: 'NONE' }, returnRequest: { status: 'NONE' } }];
const attempts = new Map();
const reviewList = [];
const paged = (list, url) => { const number = Number(url.searchParams.get('page') || 0), size = Number(url.searchParams.get('size') || 20); const totalPages = Math.ceil(list.length / size); return { content: list.slice(number * size, (number + 1) * size), number, totalElements: list.length, totalPages, first: number === 0, last: number >= totalPages - 1 }; };
const cartView = () => ({ userId: 1, version, items: cart.map(i => { const p = products.find(p => p.id === i.productId); return { ...i, name: p.name, unitPrice: p.price, subtotal: p.price * i.quantity, available: true }; }), totalPrice: cart.reduce((sum, i) => sum + products.find(p => p.id === i.productId).price * i.quantity, 0), totalQuantity: cart.reduce((sum, i) => sum + i.quantity, 0) });
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost'); const route = url.pathname;
  const send = (body, code = 200) => { res.writeHead(code, { 'Content-Type': 'application/json' }); res.end(body === undefined ? '' : JSON.stringify(body)); };
  try {
    if (!route.startsWith('/api/')) {
      const name = route === '/' ? 'index.html' : route.slice(1);
      if (!['index.html', 'store.js', 'store.css'].includes(name)) return send({}, 404);
      res.writeHead(200, { 'Content-Type': { 'index.html': 'text/html', 'store.js': 'text/javascript', 'store.css': 'text/css' }[name] }); return fs.createReadStream(path.join(root, name)).pipe(res);
    }
    let raw = ''; for await (const chunk of req) raw += chunk; const body = raw ? JSON.parse(raw) : {};
    if (route === '/api/auth/login' || route === '/api/auth/refresh') return send({ accessToken: 'browser-test-token', refreshToken: 'browser-test-refresh', user: { id: 1, name: 'Test Customer', email: 'test@example.com', roles: ['ROLE_USER', 'ROLE_ADMIN'] } });
    if (route === '/api/auth/logout' || route === '/api/users/register') return send({});
    if (route === '/api/addresses') { if (req.method === 'POST') addresses.push({ ...body, id: addresses.length + 1 }); return send(addresses); }
    if (route.startsWith('/api/addresses/')) { addresses = addresses.filter(a => a.id !== Number(route.split('/').pop())); return send(undefined, 204); }
    if (route === '/api/v1/products' || route === '/api/v1/products/admin/catalog') {
      if (req.method === 'POST') { const p = { ...body, id: products.length + 1 }; products.push(p); return send(p); }
      let list = [...products];
      const q = url.searchParams.get('query'), category = url.searchParams.get('category');
      if (q) list = list.filter(p => p.name.toLowerCase().includes(q.toLowerCase()));
      if (category) list = list.filter(p => p.category.toLowerCase() === category.toLowerCase());
      return send(paged(list, url));
    }
    if (route.startsWith('/api/v1/products/')) { const index = products.findIndex(p => p.id === Number(route.split('/').pop())); if (index < 0) return send({}, 404); if (req.method === 'PUT') Object.assign(products[index], body); if (req.method === 'DELETE') return send(products.splice(index, 1)); return send(products[index]); }
    if (route === '/api/cart') return send(cartView());
    if (route.startsWith('/api/cart/items')) {
      const id = Number(body.productId || route.split('/').pop()); const existing = cart.find(i => i.productId === id);
      if (req.method === 'DELETE') cart = cart.filter(i => i.productId !== id);
      else if (existing) existing.quantity = req.method === 'PUT' ? body.quantity : existing.quantity + body.quantity;
      else cart.push({ productId: id, quantity: body.quantity });
      version++; return send(cartView());
    }
    if (route === '/api/wishlist') return send(paged(saved, url));
    if (route.startsWith('/api/wishlist/')) { const id = Number(route.split('/').pop()); if (req.method === 'DELETE') saved = saved.filter(i => i.productId !== id); else if (!saved.some(i => i.productId === id)) saved.push({ productId: id }); return send({}); }
    if (route.startsWith('/api/reviews/products/')) { const list = reviewList.filter(r => r.productId === Number(route.split('/').pop())); return send({ averageRating: list.length ? list.reduce((sum, r) => sum + r.rating, 0) / list.length : 0, reviewCount: list.length, reviews: paged(list, url) }); }
    if (route === '/api/coupons/quote') return send({ subtotal: body.subtotal, discount: body.subtotal * .1, total: body.subtotal * .9 });
    if (route === '/api/admin/coupons') { if (req.method === 'POST') { coupons.push({ ...body, active: true, usedCount: 0 }); return send(body); } return send(paged(coupons, url)); }
    if (route.startsWith('/api/admin/coupons/')) { Object.assign(coupons.find(c => c.code === route.split('/').pop()), body); return send({}); }
    if (route === '/api/orders' && req.method === 'POST') {
      const key = req.headers['idempotency-key']; if (attempts.has(key)) return send(attempts.get(key));
      const snapshot = cartView(); const o = { ...orders[0], id: require('node:crypto').randomUUID(), status: 'CONFIRMED', paymentStatus: 'UNPAID', paymentMethod: body.paymentMethod, items: snapshot.items.map(i => ({ ...i, price: i.unitPrice })), subtotal: snapshot.totalPrice, discount: body.couponCode ? snapshot.totalPrice * .1 : 0, couponCode: body.couponCode, deliveredAt: null, trackingNumber: null, carrier: null, returnRequest: { status: 'NONE' }, refund: { status: 'NONE' } }; o.totalPrice = o.subtotal - o.discount; orders.unshift(o); attempts.set(key, o); cart = []; version++; return send(o);
    }
    if (route === '/api/orders' || route === '/api/admin/orders') return send(paged(orders, url));
    const match = route.match(/^\/api\/(?:admin\/)?orders\/([^/]+)(.*)$/);
    if (match) {
      const o = orders.find(o => o.id === match[1]); if (!o) return send({}, 404);
      switch (match[2]) {
        case '/reviews': reviewList.push({ ...body, createdAt: new Date().toISOString(), verifiedPurchase: true }); return send(body, 201);
        case '/returns': o.returnRequest = { status: 'REQUESTED', reason: body.reason }; break;
        case '/return': Object.assign(o.returnRequest, { status: body.decision, decisionNote: body.note }); break;
        case '/return/receive': o.returnRequest.status = 'RECEIVED'; o.status = 'RETURNED'; o.refund.status = 'MANUAL_REQUIRED'; break;
        case '/refund/manual': o.refund.status = 'PROCESSED'; o.paymentStatus = 'REFUNDED'; o.returnRequest.manualRefundReference = body.reference; break;
        case '/cancel': o.status = 'CANCELLED'; break;
        case '/status': Object.assign(o, body); if (body.status === 'DELIVERED') { o.deliveredAt = new Date().toISOString(); o.paymentStatus = 'COLLECTED'; } break;
      }
      return send(o);
    }
    send({ detail: 'No fixture handler for this request' }, 404);
  } catch (error) { send({ detail: error.message }, 500); }
});
server.listen(8189, '127.0.0.1', () => console.log('Isolated browser-test fixture: http://127.0.0.1:8189/index.html'));
