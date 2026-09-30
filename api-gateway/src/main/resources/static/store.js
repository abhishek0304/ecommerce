/* Everyday storefront. All prices and permissions are enforced by the services. */
'use strict';
const app = document.querySelector('#app');
const modal = document.querySelector('#modal');
const money = value => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(value ?? 0);
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const date = value => value ? new Date(value).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '—';
function stored(key, fallback = null) { try { return JSON.parse(sessionStorage.getItem(key)) ?? fallback; } catch { return fallback; } }
let auth = stored('everyday-auth');
let refreshFlight;
let renderVersion = 0;
let toastTimer;
let catalog = new Map();
let currentCart;
let currentOrders = new Map();
let shopQuery = new URLSearchParams();
let adminTab = 'products';
const pages = { shop: 0, wishlist: 0, orders: 0, products: 0, adminOrders: 0, coupons: 0, reviews: 0, inbox: 0, deliveries: 0 };
const isAdmin = () => auth?.user?.roles?.includes('ROLE_ADMIN');
function setAuth(value) {
  auth = value;
  if (value) sessionStorage.setItem('everyday-auth', JSON.stringify(value));
  else { sessionStorage.removeItem('everyday-auth'); sessionStorage.removeItem('everyday-checkout'); }
  document.querySelector('#account-link').textContent = value?.user?.name?.split(' ')[0] || 'Sign in';
  document.querySelector('#admin-link').hidden = !isAdmin();
}
function toast(message, error = false) {
  const node = document.querySelector('#toast');
  node.textContent = message; node.className = error ? 'error' : ''; node.hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => { node.hidden = true; }, error ? 10000 : 4500);
}
async function api(path, options = {}, retry = true) {
  const headers = { ...options.headers };
  if (auth?.accessToken) headers.Authorization = `Bearer ${auth.accessToken}`;
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  let response;
  try { response = await fetch(path, { ...options, headers, body: options.body === undefined ? undefined : JSON.stringify(options.body), signal: AbortSignal.timeout(20000) }); }
  catch { throw new Error('The request could not be confirmed. Check your connection. For checkout, retry with the saved attempt or check Orders.'); }
  if (response.status === 401 && retry && auth?.refreshToken && !path.startsWith('/api/auth/')) {
    if (!refreshFlight) refreshFlight = api('/api/auth/refresh', { method: 'POST', body: { refreshToken: auth.refreshToken } }, false)
      .then(setAuth).catch(error => { setAuth(null); throw error; }).finally(() => { refreshFlight = null; });
    await refreshFlight;
    return api(path, options, false);
  }
  const text = await response.text();
  let body;
  try { body = text ? JSON.parse(text) : null; } catch { body = null; }
  if (!response.ok) {
    const fields = body?.fieldErrors || body?.errors || {};
    const detail = typeof fields === 'object' ? Object.entries(fields).map(([k, v]) => `${k}: ${v}`).join('; ') : '';
    const error = new Error(body?.detail || body?.message || body?.failureReason || detail || ({ 401: 'Please sign in to continue.', 403: 'This action requires an administrator.', 404: 'This item is no longer available.' }[response.status]) || `Request failed (${response.status}).`);
    error.status = response.status; error.body = body; throw error;
  }
  return body;
}
const field = (label, name, type = 'text', value = '', attributes = '') => `<label>${esc(label)}<input name="${esc(name)}" type="${type}" value="${esc(value)}" ${attributes}></label>`;
const empty = (title, text, action = '<a class="button" href="#shop">Explore the shop</a>') => `<div class="empty"><h2>${esc(title)}</h2><p>${esc(text)}</p>${action}</div>`;
const pager = (page, key) => page.totalPages > 1 ? `<div class="pagination"><button class="secondary small" data-action="page" data-key="${key}" data-page="${page.number - 1}" ${page.first ? 'disabled' : ''}>Previous</button><span>${page.number + 1} / ${page.totalPages}</span><button class="secondary small" data-action="page" data-key="${key}" data-page="${page.number + 1}" ${page.last ? 'disabled' : ''}>Next</button></div>` : '';
function productImage(p) {
  const safe = typeof p.imageUrl === 'string' && p.imageUrl.startsWith('https://');
  return `<div class="product-image">${safe ? `<img src="${esc(p.imageUrl)}" alt="${esc(p.name)}" loading="lazy" referrerpolicy="no-referrer">` : `<span class="product-placeholder" aria-hidden="true">${esc(p.name?.slice(0, 1) || 'E')}</span>`}</div>`;
}
function card(p, saved = false) {
  catalog.set(String(p.id), p);
  return `<article class="product-card"><a href="#product/${p.id}" aria-label="View ${esc(p.name)}">${productImage(p)}</a><p class="product-category">${esc(p.category || 'Essentials')}</p><h3><a href="#product/${p.id}">${esc(p.name)}</a></h3><div class="product-meta"><span>${money(p.price)}</span><span class="stock">${p.stockQuantity > 0 ? 'In stock' : 'Out of stock'}</span></div><div class="card-actions"><button data-action="${saved ? 'move' : 'add'}" data-id="${p.id}" ${p.stockQuantity < 1 ? 'disabled' : ''}>${saved ? 'Move to bag' : 'Add to bag'} <span aria-hidden="true">↗</span></button><button class="secondary save" data-action="${saved ? 'unsave' : 'save'}" data-id="${p.id}" aria-label="${saved ? 'Remove saved' : 'Save'} ${esc(p.name)}">${saved ? '×' : '♡'}</button></div></article>`;
}
async function updateCartCount() {
  try { const cart = auth ? await api('/api/cart') : null; document.querySelector('#cart-count').textContent = cart?.totalQuantity || 0; } catch { /* A count failure must not block browsing. */ }
}
async function shop() {
  const params = new URLSearchParams(shopQuery); params.set('page', pages.shop); params.set('size', 12);
  const products = await api(`/api/v1/products?${params}`);
  return `<section class="hero"><div><span class="eyebrow">Good things, for everyday living</span><h1>Small upgrades.<br>A better everyday.</h1><p>Discover useful finds for your home, your work, and everything in between.</p><a href="#catalog" class="button" data-action="browse">Find your essentials <span aria-hidden="true">↗</span></a></div><div class="hero-art" aria-hidden="true"><div class="hero-circle"></div><div class="hero-bag">everyday.</div><div class="hero-tag">Made for your daily rotation.</div></div></section><section id="catalog"><div class="section-head"><div><span class="eyebrow">The collection</span><h2>Your next great find</h2></div><span class="muted">${products.totalElements} products</span></div><form id="search-form" class="filters">${field('Search products', 'query', 'search', shopQuery.get('query'), 'placeholder="What are you looking for?" maxlength="150"')}${field('Category', 'category', 'text', shopQuery.get('category'), 'placeholder="All categories" maxlength="100"')}${field('Min price', 'minPrice', 'number', shopQuery.get('minPrice'), 'min="0" step="0.01" placeholder="₹ 0"')}${field('Max price', 'maxPrice', 'number', shopQuery.get('maxPrice'), 'min="0" step="0.01" placeholder="No limit"')}<label>Sort by<select name="sort"><option value="createdAt,desc">Newest first</option><option value="price,asc">Price: low to high</option><option value="price,desc">Price: high to low</option><option value="name,asc">Name: A–Z</option></select></label><button>Search</button><label class="check"><input type="checkbox" name="inStock" ${shopQuery.get('inStock') === 'true' ? 'checked' : ''}> Only show products in stock</label></form><div class="grid">${products.content.map(p => card(p)).join('') || empty('No matches yet', 'Try another search or a wider price range.', '<button class="secondary" data-action="reset-search">Clear filters</button>')}</div>${pager(products, 'shop')}</section>`;
}
async function detail(id) {
  const [p, reviews] = await Promise.all([api(`/api/v1/products/${id}`), api(`/api/reviews/products/${id}?page=${pages.reviews}&size=5`)]);
  catalog.set(String(p.id), p);
  return `<a class="text-button" href="#shop">← Back to the collection</a><section class="detail" style="margin-top:24px">${productImage(p)}<div><p class="eyebrow">${esc(p.category || 'Essentials')}</p><h1>${esc(p.name)}</h1><p><span class="stars">★</span> ${reviews.averageRating.toFixed(1)} <span class="muted">(${reviews.reviewCount} reviews)</span></p><p class="price">${money(p.price)}</p><p style="white-space:pre-wrap">${esc(p.description || 'An everyday essential, ready for your daily routine.')}</p><p class="muted">${p.stockQuantity > 0 ? `${p.stockQuantity} available` : 'Currently out of stock'}</p><div class="form-actions"><button data-action="add" data-id="${p.id}" ${p.stockQuantity < 1 ? 'disabled' : ''}>Add to bag ↗</button><button class="secondary" data-action="save" data-id="${p.id}">♡ Save for later</button></div><p class="notice">Choose cash on delivery or online payment at checkout. Returns can be requested within 30 days of delivery.</p></div></section><section style="margin-top:45px"><h2>From our customers</h2><p class="muted">Reviews are available after a purchased item is delivered. Write yours from Orders.</p>${reviews.reviews.content.map(r => `<article class="review"><span class="stars" aria-label="${r.rating} out of 5">${'★'.repeat(r.rating)}${'☆'.repeat(5 - r.rating)}</span> <span class="badge">Verified purchase</span><p>${esc(r.comment)}</p><small class="muted">${date(r.createdAt)}</small></article>`).join('') || '<p class="muted">No reviews yet.</p>'}${pager(reviews.reviews, 'reviews')}</section>`;
}
async function wishlist() {
  const result = await api(`/api/wishlist?page=${pages.wishlist}&size=12`);
  const products = await Promise.all(result.content.map(async entry => {
    try { return card(await api(`/api/v1/products/${entry.productId}`), true); }
    catch (error) { if (error.status !== 404) throw error; return `<article class="panel"><h3>Product unavailable</h3><p class="muted">This saved product is no longer listed.</p><button class="secondary" data-action="unsave" data-id="${entry.productId}">Remove</button></article>`; }
  }));
  return `<div class="section-head"><div><span class="eyebrow">Keep the good ones</span><h1>Saved for later</h1></div></div><div class="grid">${products.join('') || empty('Your wishlist is waiting', 'Tap the heart on a product to keep it here.')}</div>${pager(result, 'wishlist')}`;
}
const addressText = a => [a.line1, a.line2, a.city, a.state, a.postalCode, a.country].filter(Boolean).join(', ');
async function cartPage() {
  const [cart, addresses] = await Promise.all([api('/api/cart'), api('/api/addresses')]);
  currentCart = cart;
  if (!cart.items.length) return `<h1>Your bag</h1>${empty('Room for something good', 'Your shopping bag is currently empty.')}`;
  const unavailable = cart.items.some(i => !i.available);
  return `<div class="section-head"><div><span class="eyebrow">Almost yours</span><h1>Your shopping bag</h1></div><a class="text-button" href="#shop">Continue shopping</a></div><div class="two-column"><section class="panel">${cart.items.map(i => `<div class="row"><div><h3><a href="#product/${i.productId}">${esc(i.name)}</a></h3><p>${money(i.unitPrice)} each ${!i.available ? '· Unavailable — remove before checkout' : ''}</p><button class="text-button" data-action="remove-cart" data-id="${i.productId}">Remove</button></div><label>Quantity<input type="number" min="1" max="10000" value="${i.quantity}" data-quantity="${i.productId}" aria-label="Quantity for ${esc(i.name)}"></label><strong>${money(i.subtotal)}</strong></div>`).join('')}</section><aside><form id="checkout-form" class="panel"><h2>Checkout</h2><div class="row"><span>Subtotal</span><strong>${money(cart.totalPrice)}</strong></div><p class="muted">Final prices and availability are checked when you place your order.</p><label>Delivery address<select name="addressId" required><option value="">Select your address</option>${addresses.map(a => `<option value="${a.id}">${esc(addressText(a))}</option>`).join('')}</select></label><button type="button" class="text-button" data-action="address">+ Add a delivery address</button><label style="margin-top:16px">Payment method<select name="paymentMethod"><option value="CASH_ON_DELIVERY">Cash on delivery</option><option value="RAZORPAY">Online payment (Razorpay test)</option></select></label><div style="margin-top:16px">${field('Coupon code (optional)', 'couponCode', 'text', '', 'maxlength="40" placeholder="Enter a code"')}<button type="button" class="text-button" data-action="quote">Check coupon</button><p id="coupon-quote" class="muted" aria-live="polite"></p></div><button class="full" style="width:100%" ${unavailable ? 'disabled' : ''}>Place order ↗</button><p class="muted" style="font-size:11px">If a request times out, retry without changing the checkout details. Your saved attempt prevents duplicate orders.</p></form></aside></div>`;
}
function orderCard(o, admin = false) {
  currentOrders.set(o.id, o);
  const ret = o.returnRequest || { status: 'NONE' };
  const customerActions = `<button class="secondary small" data-action="refresh-order" data-id="${o.id}">Refresh</button>${o.checkout ? `<button class="small" data-action="pay" data-id="${o.id}">Pay ${money(o.totalPrice)}</button>` : ''}${['CREATING', 'RESERVED', 'PENDING_PAYMENT', 'CONFIRMED', 'PROCESSING'].includes(o.status) ? `<button class="secondary small" data-action="cancel-order" data-id="${o.id}">Cancel order</button>` : ''}${o.status === 'DELIVERED' && ret.status === 'NONE' ? `<button class="secondary small" data-action="return" data-id="${o.id}">Request return</button>` : ''}${o.deliveredAt ? `<button class="secondary small" data-action="review" data-id="${o.id}">Write a review</button>` : ''}`;
  const nextStatus = { CONFIRMED: 'PROCESSING', PROCESSING: 'SHIPPED', SHIPPED: 'DELIVERED' }[o.status];
  const adminActions = `${nextStatus ? `<button class="small" data-action="fulfill" data-id="${o.id}" data-status="${nextStatus}">Mark ${nextStatus.toLowerCase()}</button>` : ''}${o.refund?.status === 'FAILED' ? `<button class="secondary small" data-action="retry-refund" data-id="${o.id}">Retry refund</button>` : ''}${ret.status === 'REQUESTED' ? `<button class="small" data-action="decide-return" data-id="${o.id}">Review return</button>` : ''}${ret.status === 'APPROVED' ? `<button class="small" data-action="receive-return" data-id="${o.id}">Record returned items</button>` : ''}${o.refund?.status === 'MANUAL_REQUIRED' ? `<button class="small" data-action="manual-refund" data-id="${o.id}">Record COD refund</button>` : ''}`;
  return `<article class="panel"><div class="order-head"><div><p class="order-id">${esc(o.id)}</p><small class="muted">Placed ${date(o.createdAt)}</small></div><span class="badge">${esc(o.status.replaceAll('_', ' '))}</span></div><div class="order-items">${o.items.map(i => `${esc(i.name)} × ${i.quantity} <span class="muted">· ${money(i.price)}</span>`).join('<br>')}</div>${o.failureReason ? `<p class="notice">${esc(o.failureReason)}</p>` : ''}<div class="order-summary"><div>Order total<strong>${money(o.totalPrice)}</strong>${Number(o.discount) > 0 ? `<small class="discount">Saved ${money(o.discount)} · ${esc(o.couponCode)}</small>` : ''}</div><div>Payment<strong>${esc(o.paymentStatus.replaceAll('_', ' '))}</strong><small>${o.paymentMethod === 'RAZORPAY' ? 'Razorpay' : 'Cash on delivery'}</small></div>${o.trackingNumber ? `<div>Tracking<strong>${esc(o.carrier)}</strong>${esc(o.trackingNumber)}</div>` : ''}${o.refund?.status !== 'NONE' ? `<div>Refund<strong>${esc(o.refund?.status?.replaceAll('_', ' '))}</strong></div>` : ''}</div><p class="address muted">${esc(addressText(o.shippingAddress || {}))}</p>${ret.status !== 'NONE' ? `<p class="notice"><strong>Return ${esc(ret.status.toLowerCase())}</strong><br>${esc(ret.reason)}${ret.decisionNote ? `<br>Admin: ${esc(ret.decisionNote)}` : ''}${ret.manualRefundReference ? `<br>Refund reference: ${esc(ret.manualRefundReference)}` : ''}</p>` : ''}${o.refund?.failure ? `<p class="error">${esc(o.refund.failure)}</p>` : ''}<div class="form-actions">${admin ? adminActions : customerActions}<button class="secondary small" data-action="shipments" data-id="${o.id}">Shipments</button></div></article>`;
}
async function orders() {
  const result = await api(`/api/orders?page=${pages.orders}&size=10`);
  return `<div class="section-head"><div><span class="eyebrow">From checkout to your doorstep</span><h1>Your orders</h1></div><button class="secondary" data-action="reload">Refresh orders</button></div>${result.content.map(o => orderCard(o)).join('') || empty('Your first find awaits', 'Once you place an order, you can follow its journey here.')}${pager(result, 'orders')}`;
}
async function account() {
  if (!auth) return `<div class="section-head"><div><span class="eyebrow">Make yourself at home</span><h1>Your everyday account</h1></div></div><div class="two-column"><form id="login-form" class="panel"><h2>Welcome back</h2><div class="form-grid">${field('Email', 'email', 'email', '', 'required autocomplete="username" class="full"')}${field('Password', 'password', 'password', '', 'required autocomplete="current-password"')}</div><div class="form-actions"><button>Sign in ↗</button></div></form><form id="register-form" class="panel"><h2>New here?</h2><p class="muted">Save favorites and keep track of every order.</p><div class="form-grid">${field('Full name', 'name', 'text', '', 'required maxlength="100" autocomplete="name"')}${field('Phone', 'phone', 'tel', '', 'required pattern="[+]?[0-9]{7,15}" autocomplete="tel"')}${field('Email', 'email', 'email', '', 'required autocomplete="email"')}${field('Password', 'password', 'password', '', 'required minlength="8" maxlength="100" autocomplete="new-password"')}</div><div class="form-actions"><button>Create account</button></div></form></div>`;
  const addresses = await api('/api/addresses');
  return `<div class="section-head"><div><span class="eyebrow">Your space</span><h1>Hello, ${esc(auth.user.name)}</h1><p class="muted">${esc(auth.user.email)}</p></div><button class="secondary" data-action="logout">Sign out</button></div><section class="panel"><div class="section-head"><h2>Delivery addresses</h2><button class="secondary" data-action="address">+ Add address</button></div>${addresses.map(a => `<div class="row"><p class="address">${esc(addressText(a))}</p><button class="secondary small" data-action="delete-address" data-id="${a.id}">Remove</button></div>`).join('') || '<p class="muted">Add an address to make checkout easier.</p>'}</section>`;
}
async function security() {
  const email = auth?.user?.email || '';
  const emailField = () => field('Email', 'email', 'email', email, 'required autocomplete="email"');
  const otpField = () => field('Six digit code', 'otp', 'text', '', 'required pattern="[0-9]{6}" inputmode="numeric" autocomplete="one-time-code"');
  return `<div class="section-head"><h1>Email and password help</h1></div><p class="muted">Codes expire after 10 minutes. Wait at least 60 seconds before requesting another code.</p><div class="two-column">
    <form id="forgot-form" class="panel"><h2>Forgot your password?</h2>${emailField()}<button>Send reset code</button></form>
    <form id="reset-form" class="panel"><h2>Reset password</h2>${emailField()}${otpField()}${field('New password', 'newPassword', 'password', '', 'required minlength="8" maxlength="100" autocomplete="new-password"')}<button>Reset password</button></form>
    <form id="resend-verification-form" class="panel"><h2>Request verification email</h2>${emailField()}<button>Send verification code</button></form>
    <form id="verify-form" class="panel"><h2>Verify your email</h2>${emailField()}${otpField()}<button>Verify email</button></form></div>`;
}
async function messages() {
  const [preferences, inbox, deliveries] = await Promise.all([
    api('/api/users/preferences'), api(`/api/notifications?page=${pages.inbox}&size=10`),
    api(`/api/notifications/deliveries?page=${pages.deliveries}&size=10`)
  ]);
  const checkbox = (key, label) => `<label class="check"><input type="checkbox" name="${key}" ${preferences[key] ? 'checked' : ''}> ${label}</label>`;
  return `<div class="section-head"><h1>Your messages</h1><button class="secondary" data-action="reload">Refresh</button></div>
    <form id="message-preferences-form" class="panel"><h2>Order update preferences</h2><p>Choose how you want to hear about your orders. SMS and WhatsApp use your account phone number, including its country code.</p>
    ${checkbox('emailNotifications','Email')}${checkbox('smsNotifications','SMS text messages')}${checkbox('whatsAppNotifications','WhatsApp messages')}
    <p class="muted">Verification and password-reset emails are still sent when you request them.</p><button>Save preferences</button></form>
    <section class="panel"><h2>Inbox</h2>${inbox.content.map(n => `<article class="row"><div><p>${esc(n.message)}</p><small class="muted">${date(n.occurredAt)}</small></div></article>`).join('') || '<p>No messages yet.</p>'}${pager(inbox,'inbox')}</section>
    <section class="panel"><h2>Delivery activity</h2><p class="muted">Accepted means the email or messaging provider accepted the message; it does not confirm it was read. Pending messages are waiting to be sent.</p>
    ${deliveries.content.map(m => `<article class="row"><div><strong>${esc(m.channel)}</strong><p>${esc(m.type === 'ACCOUNT_SECURITY' ? 'Account security email' : m.type)}</p><small>${date(m.createdAt)}</small></div><span class="badge">${esc(m.status)}</span></article>`).join('') || '<p>No delivery activity yet.</p>'}${pager(deliveries,'deliveries')}</section>`;
}
async function admin() {
  if (!isAdmin()) return empty('Administrator access required', 'Sign in with an administrator account to manage the store.', '<a class="button" href="#account">Your account</a>');
  let content;
  if (adminTab === 'products') {
    const result = await api(`/api/v1/products/admin/catalog?page=${pages.products}&size=20`);
    result.content.forEach(p => catalog.set(String(p.id), p));
    content = `<div class="section-head"><h2>Product catalog</h2><button data-action="edit-product">+ Add product</button></div><div class="panel table-wrap"><table class="admin-table"><thead><tr><th>Product</th><th>Price</th><th>Available stock</th><th>Visibility</th><th>Actions</th></tr></thead><tbody>${result.content.map(p => `<tr><td><strong>${esc(p.name)}</strong><br><small class="muted">${esc(p.sku)}</small></td><td>${money(p.price)}</td><td>${p.stockQuantity}</td><td>${p.active ? 'Active' : 'Hidden'}</td><td><button class="secondary small" data-action="edit-product" data-id="${p.id}">Edit</button> <button class="danger small" data-action="delete-product" data-id="${p.id}">Delete</button></td></tr>`).join('')}</tbody></table>${!result.content.length ? '<p>No products yet.</p>' : ''}</div>${pager(result, 'products')}`;
  } else if (adminTab === 'orders') {
    const result = await api(`/api/admin/orders?page=${pages.adminOrders}&size=10`);
    content = `<div class="section-head"><h2>Orders & returns</h2><button class="secondary" data-action="reload">Refresh</button></div>${result.content.map(o => orderCard(o, true)).join('') || '<p class="muted">No orders yet.</p>'}${pager(result, 'adminOrders')}`;
  } else {
    const result = await api(`/api/admin/coupons?page=${pages.coupons}&size=20`);
    content = `<div class="section-head"><h2>Coupons</h2><button data-action="new-coupon">+ Create coupon</button></div><div class="panel table-wrap"><table class="admin-table"><thead><tr><th>Code</th><th>Discount</th><th>Minimum spend</th><th>Usage</th><th>Expires</th><th>Status</th></tr></thead><tbody>${result.content.map(c => `<tr><td><strong>${esc(c.code)}</strong></td><td>${c.type === 'PERCENT' ? `${c.value}%` : money(c.value)}</td><td>${money(c.minimumSpend)}</td><td>${c.usedCount} / ${c.usageLimit}</td><td>${date(c.expiresAt)}</td><td><button class="secondary small" data-action="toggle-coupon" data-code="${esc(c.code)}" data-active="${!c.active}">${c.active ? 'Deactivate' : 'Activate'}</button></td></tr>`).join('')}</tbody></table>${!result.content.length ? '<p>No coupons yet.</p>' : ''}</div><p class="muted">Coupons apply to the full order. A minimum of ₹1 remains payable. Usage is released for failed, cancelled, or expired orders.</p>${pager(result, 'coupons')}`;
  }
  return `<div class="section-head"><div><span class="eyebrow">Store management</span><h1>Admin workspace</h1></div><span class="badge">Administrator</span></div><div class="tabs">${['products', 'orders', 'coupons'].map(t => `<button class="secondary ${t === adminTab ? 'active' : ''}" data-action="admin-tab" data-tab="${t}">${{ products: 'Products', orders: 'Orders & returns', coupons: 'Coupons' }[t]}</button>`).join('')}</div>${content}`;
}
async function render() {
  const version = ++renderVersion;
  const [route = 'shop', id] = location.hash.slice(1).split('/');
  document.querySelectorAll('nav a').forEach(a => a.setAttribute('aria-current', a.hash === `#${route}` ? 'page' : 'false'));
  if (['cart', 'wishlist', 'orders', 'messages'].includes(route) && !auth) {
    app.innerHTML = empty('Make it yours', 'Sign in to save products, manage your bag, and track your orders.', '<a class="button" href="#account">Sign in</a>'); return;
  }
  app.innerHTML = '<p class="loading" role="status">Getting things ready…</p>';
  try {
    const html = route === 'product' && /^\d+$/.test(id) ? await detail(id) : await ({ shop, wishlist, cart: cartPage, orders, account, admin, messages, security }[route] || shop)();
    if (version !== renderVersion) return;
    app.innerHTML = html;
    if (route === 'account') app.insertAdjacentHTML('afterbegin', '<p><a href="#security">Verify email or reset your password</a></p>');
    const sort = app.querySelector('[name=sort]'); if (sort) sort.value = shopQuery.get('sort') || 'createdAt,desc';
  } catch (error) {
    if (version === renderVersion) app.innerHTML = empty('We couldn’t load this page', error.message, '<button class="secondary" data-action="reload">Try again</button>');
  }
}
function openDialog(title, content) {
  document.querySelector('#modal-content').innerHTML = `<h2 id="modal-title">${esc(title)}</h2>${content}`;
  if (!modal.open) modal.showModal();
}
function addressDialog() {
  openDialog('Add a delivery address', `<form id="address-form"><div class="form-grid">${field('Address line 1', 'line1', 'text', '', 'required maxlength="200" autocomplete="address-line1"')}${field('Address line 2 (optional)', 'line2', 'text', '', 'maxlength="200" autocomplete="address-line2"')}${field('City', 'city', 'text', '', 'required autocomplete="address-level2"')}${field('State', 'state', 'text', '', 'required autocomplete="address-level1"')}${field('Postal code', 'postalCode', 'text', '', 'required autocomplete="postal-code"')}${field('Country', 'country', 'text', 'India', 'required autocomplete="country-name"')}</div><div class="form-actions"><button>Save address</button></div></form>`);
}
function productDialog(id) {
  const p = catalog.get(id) || {};
  openDialog(id ? 'Edit product' : 'Add a product', `<form id="product-form" data-id="${id || ''}"><div class="form-grid">${field('Name', 'name', 'text', p.name, 'required maxlength="150"')}${field('SKU', 'sku', 'text', p.sku, 'required pattern="[A-Za-z0-9_-]+" maxlength="64"')}${field('Price (INR)', 'price', 'number', p.price, 'required min="0.01" step="0.01"')}${field('Available stock', 'stockQuantity', 'number', p.stockQuantity ?? 0, 'required min="0" max="1000000"')}${field('Category', 'category', 'text', p.category, 'maxlength="100"')}${field('Image URL (HTTPS)', 'imageUrl', 'url', p.imageUrl, 'pattern="https://.*" maxlength="2048"')}<label class="full">Description<textarea name="description" maxlength="2000">${esc(p.description)}</textarea></label><label class="check full"><input type="checkbox" name="active" ${p.active !== false ? 'checked' : ''}> Visible to customers</label></div><div class="form-actions"><button>Save product</button></div></form>`);
}
function orderDialog(action, id, status) {
  const order = currentOrders.get(id);
  const forms = {
    return: ['Request a return', `<p class="muted">This requests a return of the whole order. You’ll be refunded after approval and receipt.</p><label>Reason<textarea name="reason" required maxlength="1000"></textarea></label>`],
    review: ['Review your purchase', `<label>Product<select name="productId">${order.items.map(i => `<option value="${i.productId}">${esc(i.name)}</option>`).join('')}</select></label><label style="margin-top:14px">Rating<select name="rating">${[5, 4, 3, 2, 1].map(r => `<option value="${r}">${r} star${r > 1 ? 's' : ''}</option>`).join('')}</select></label><label style="margin-top:14px">Your review<textarea name="comment" required maxlength="2000"></textarea></label>`],
    fulfill: [`Mark order ${status?.toLowerCase()}`, status === 'SHIPPED' ? `<div class="form-grid">${field('Carrier', 'carrier', 'text', order.carrier || '', 'required maxlength="100"')}${field('Tracking number', 'trackingNumber', 'text', order.trackingNumber || '', 'required maxlength="150"')}</div>` : status === 'DELIVERED' && order.paymentMethod === 'CASH_ON_DELIVERY' ? '<label class="check"><input type="checkbox" name="cashCollected" required> I confirm payment was collected from the customer.</label>' : '<p>Confirm this fulfillment update.</p>'],
    'decide-return': ['Review return request', `<p>${esc(order.returnRequest.reason)}</p><label>Decision<select name="decision"><option value="APPROVED">Approve</option><option value="REJECTED">Reject</option></select></label><label style="margin-top:14px">Note to customer<textarea name="note" required maxlength="1000"></textarea></label>`],
    'receive-return': ['Record returned items', '<p>Confirm all items have been received and inspected. This begins the refund.</p><label>Inventory decision<select name="restock"><option value="true">Restock all returned items</option><option value="false">Do not restock (damaged or unusable)</option></select></label>'],
    'manual-refund': ['Record a completed COD refund', `<p>Refund ${money(order.totalPrice)} to the customer, then record the payment reference below.</p>${field('Refund reference', 'reference', 'text', '', 'required maxlength="150"')}`]
  };
  const [title, content] = forms[action];
  openDialog(title, `<form id="order-action-form" data-action="${action}" data-id="${id}" data-status="${status || ''}">${content}<div class="form-actions"><button>Confirm</button></div></form>`);
}
let razorpayLoading;
async function pay(id) {
  const order = await api(`/api/orders/${id}`);
  if (!order.checkout) { toast('Payment is not currently available for this order. Refresh Orders for its status.'); await render(); return; }
  if (!window.Razorpay) {
    if (!razorpayLoading) razorpayLoading = new Promise((resolve, reject) => {
      const script = document.createElement('script'); script.src = 'https://checkout.razorpay.com/v1/checkout.js';
      const timeout = setTimeout(() => reject(new Error('Payment window timed out. Retry from Orders.')), 20000);
      script.onload = () => { clearTimeout(timeout); resolve(); }; script.onerror = () => { clearTimeout(timeout); reject(new Error('Payment window could not load. Retry from Orders.')); };
      document.head.append(script);
    }).catch(error => { razorpayLoading = null; throw error; });
    await razorpayLoading;
  }
  const widget = new window.Razorpay({ key: order.checkout.keyId, order_id: order.checkout.razorpayOrderId, amount: order.checkout.amount, currency: order.checkout.currency, name: 'Everyday', description: 'Order payment',
    handler: async result => {
      try { await api(`/api/orders/${id}/payments/verify`, { method: 'POST', body: result }); toast('Payment verified. Your order is confirmed.'); }
      catch (error) { toast(`Verification is pending: ${error.message} Check Orders before attempting another payment.`, true); }
      location.hash = '#orders'; await render();
    }, modal: { ondismiss: () => toast('You can resume this payment from Orders before it expires.') }
  }); widget.open();
}
async function action(button, event) {
  const { action: kind, id } = button.dataset;
  if (kind === 'close') { modal.close(); return; }
  if (kind === 'browse') { event.preventDefault(); document.querySelector('#catalog')?.scrollIntoView({ behavior: 'smooth' }); return; }
  if (['add', 'save', 'move', 'unsave'].includes(kind) && !auth) { location.hash = '#account'; toast('Sign in to keep shopping.'); return; }
  switch (kind) {
    case 'reload': await render(); break;
    case 'reset-search': shopQuery = new URLSearchParams(); pages.shop = 0; await render(); break;
    case 'page': pages[button.dataset.key] = Number(button.dataset.page); await render(); break;
    case 'admin-tab': adminTab = button.dataset.tab; await render(); break;
    case 'add': await api('/api/cart/items', { method: 'POST', body: { productId: Number(id), quantity: 1 } }); toast('Added to your bag.'); await updateCartCount(); break;
    case 'save': await api(`/api/wishlist/${id}`, { method: 'PUT' }); toast('Saved for later.'); break;
    case 'unsave': await api(`/api/wishlist/${id}`, { method: 'DELETE' }); await render(); break;
    case 'move': {
      const cart = await api('/api/cart');
      // A retry after a lost add response keeps the existing cart quantity.
      if (!cart.items.some(i => i.productId === Number(id))) await api('/api/cart/items', { method: 'POST', body: { productId: Number(id), quantity: 1 } });
      await api(`/api/wishlist/${id}`, { method: 'DELETE' }); toast('Moved to your bag.'); await updateCartCount(); await render(); break;
    }
    case 'remove-cart': await api(`/api/cart/items/${id}`, { method: 'DELETE' }); await updateCartCount(); await render(); break;
    case 'address': addressDialog(); break;
    case 'delete-address': if (confirm('Remove this delivery address?')) { await api(`/api/addresses/${id}`, { method: 'DELETE' }); await render(); } break;
    case 'logout': {
      try { await api('/api/auth/logout', { method: 'POST', headers: { 'X-Refresh-Token': auth.refreshToken } }); }
      finally { setAuth(null); document.querySelector('#cart-count').textContent = '0'; await render(); }
      break;
    }
    case 'quote': {
      const code = document.querySelector('[name=couponCode]').value.trim();
      if (!code) throw new Error('Enter a coupon code first.');
      const quote = await api('/api/coupons/quote', { method: 'POST', body: { code, subtotal: currentCart.totalPrice } });
      document.querySelector('#coupon-quote').textContent = `Estimated savings ${money(quote.discount)} · Total ${money(quote.total)}. Rechecked at checkout.`; break;
    }
    case 'refresh-order': await render(); break;
    case 'pay': await pay(id); break;
    case 'cancel-order': if (confirm('Cancel this order?')) { await api(`/api/orders/${id}/cancel`, { method: 'POST' }); toast('Cancellation recorded. Refunds, if needed, are shown on the order.'); await render(); } break;
    case 'return': case 'review': case 'fulfill': case 'decide-return': case 'receive-return': case 'manual-refund': orderDialog(kind, id, button.dataset.status); break;
    case 'retry-refund': await api(`/api/admin/orders/${id}/refund/retry`, { method: 'POST' }); await render(); break;
    case 'edit-product': productDialog(id); break;
    case 'delete-product': if (confirm('Delete this product? You can hide it by editing it instead.')) { await api(`/api/v1/products/${id}`, { method: 'DELETE' }); await render(); } break;
    case 'new-coupon': openDialog('Create a coupon', `<form id="coupon-form"><div class="form-grid">${field('Code', 'code', 'text', '', 'required pattern="[A-Za-z0-9_-]{3,40}" maxlength="40"')}<label>Discount type<select name="type"><option value="PERCENT">Percentage</option><option value="FIXED">Fixed INR amount</option></select></label>${field('Discount value', 'value', 'number', '', 'required min="0.01" step="0.01"')}${field('Minimum spend', 'minimumSpend', 'number', 0, 'required min="0" step="0.01"')}${field('Expires (your local time)', 'expiresAt', 'datetime-local', '', 'required')}${field('Total usage limit', 'usageLimit', 'number', 100, 'required min="1" max="1000000"')}</div><div class="form-actions"><button>Create coupon</button></div></form>`); break;
    case 'toggle-coupon': await api(`/api/admin/coupons/${encodeURIComponent(button.dataset.code)}`, { method: 'PATCH', body: { active: button.dataset.active === 'true' } }); await render(); break;
  }
}
document.addEventListener('click', async event => {
  const button = event.target.closest('button[data-action],a[data-action]');
  if (!button || button.disabled) return;
  if (button.tagName === 'BUTTON') button.disabled = true;
  try { await action(button, event); } catch (error) { toast(error.message, true); }
  finally { button.disabled = false; }
});
document.addEventListener('change', async event => {
  const input = event.target;
  if (!input.dataset.quantity) return;
  if (!input.reportValidity()) return;
  input.disabled = true;
  try { await api(`/api/cart/items/${input.dataset.quantity}`, { method: 'PUT', body: { quantity: Number(input.value) } }); await updateCartCount(); await render(); }
  catch (error) { toast(error.message, true); await render(); }
  finally { input.disabled = false; }
});
document.addEventListener('submit', async event => {
  if (['shipment-book-form', 'shipment-pickup-form'].includes(event.target.id)) return;
  event.preventDefault();
  const form = event.target;
  if (form.dataset.busy) return;
  const data = Object.fromEntries(new FormData(form));
  form.dataset.busy = 'true';
  const submit = form.querySelector('button:not([type=button])');
  if (submit) submit.disabled = true;
  try {
    switch (form.id) {
      case 'search-form': {
        shopQuery = new URLSearchParams();
        Object.entries(data).forEach(([key, value]) => { if (value) shopQuery.set(key, key === 'inStock' ? 'true' : value); });
        pages.shop = 0; await render(); document.querySelector('#catalog')?.scrollIntoView(); break;
      }
      case 'login-form': setAuth(await api('/api/auth/login', { method: 'POST', body: data }, false)); location.hash = '#shop'; await updateCartCount(); break;
      case 'register-form': await api('/api/users/register', { method: 'POST', body: data }); toast('Account created. Check your email for a verification code, then sign in.'); form.reset(); break;
      case 'forgot-form': await api('/api/auth/forgot-password', {method:'POST',body:data}, false); toast('Reset email requested. Check your inbox.'); break;
      case 'resend-verification-form': await api('/api/auth/resend-verification', {method:'POST',body:data}, false); toast('Verification email requested. Check your inbox.'); break;
      case 'verify-form': await api('/api/auth/verify-email', {method:'POST',body:data}, false); toast('Email verified.'); form.reset(); break;
      case 'reset-form': await api('/api/auth/reset-password', {method:'POST',body:data}, false); setAuth(null); form.reset(); toast('Password reset. Sign in with your new password.'); location.hash='#account'; break;
      case 'message-preferences-form': await api('/api/users/preferences', {method:'PUT',body:{emailNotifications:!!data.emailNotifications,smsNotifications:!!data.smsNotifications,whatsAppNotifications:!!data.whatsAppNotifications}}); toast('Message preferences saved.'); break;
      case 'address-form': await api('/api/addresses', { method: 'POST', body: data }); modal.close(); toast('Address saved.'); await render(); break;
      case 'checkout-form': {
        const request = { addressId: Number(data.addressId), paymentMethod: data.paymentMethod, ...(data.couponCode.trim() ? { couponCode: data.couponCode.trim().toUpperCase() } : {}) };
        const signature = JSON.stringify({ userId: auth.user.id, cartVersion: currentCart.version, items: currentCart.items.map(i => [i.productId, i.quantity]).sort((a, b) => a[0] - b[0]), request });
        let attempt = stored('everyday-checkout');
        if (attempt?.signature !== signature) { attempt = { key: crypto.randomUUID(), signature, request }; sessionStorage.setItem('everyday-checkout', JSON.stringify(attempt)); }
        const order = await api('/api/orders', { method: 'POST', headers: { 'Idempotency-Key': attempt.key }, body: attempt.request });
        toast(['CREATING', 'RESERVED', 'CANCELLING'].includes(order.status) ? 'Checkout is being recovered. Track its progress in Orders.' : 'Order saved. Track it in Orders.');
        location.hash = '#orders'; await updateCartCount(); break;
      }
      case 'product-form': {
        const body = { ...data, price: Number(data.price), stockQuantity: Number(data.stockQuantity), active: !!data.active, imageUrl: data.imageUrl || null };
        await api(`/api/v1/products${form.dataset.id ? `/${form.dataset.id}` : ''}`, { method: form.dataset.id ? 'PUT' : 'POST', body });
        modal.close(); toast('Product saved.'); await render(); break;
      }
      case 'coupon-form': await api('/api/admin/coupons', { method: 'POST', body: { ...data, value: Number(data.value), minimumSpend: Number(data.minimumSpend), usageLimit: Number(data.usageLimit), expiresAt: new Date(data.expiresAt).toISOString() } }); modal.close(); toast('Coupon created.'); await render(); break;
      case 'order-action-form': {
        const { id, action: kind, status } = form.dataset;
        const actions = {
          return: [`/api/orders/${id}/returns`, 'POST', data],
          review: [`/api/orders/${id}/reviews`, 'POST', { ...data, productId: Number(data.productId), rating: Number(data.rating) }],
          fulfill: [`/api/admin/orders/${id}/status`, 'PATCH', { ...data, status, cashCollected: !!data.cashCollected }],
          'decide-return': [`/api/admin/orders/${id}/return`, 'PATCH', data],
          'receive-return': [`/api/admin/orders/${id}/return/receive`, 'POST', { restock: data.restock === 'true' }],
          'manual-refund': [`/api/admin/orders/${id}/refund/manual`, 'POST', data]
        };
        const [path, method, body] = actions[kind]; await api(path, { method, body }); modal.close(); toast('Saved successfully.'); await render(); break;
      }
    }
  } catch (error) { toast(error.message, true); }
  finally { delete form.dataset.busy; if (submit) submit.disabled = false; }
});
window.addEventListener('hashchange', () => { modal.close(); pages.reviews = 0; render(); window.scrollTo(0, 0); });
setAuth(auth);
render();
updateCartCount();
