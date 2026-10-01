import React, { useEffect, useRef, useState } from 'react';

import { ArrowUpRight, ArrowRight, Search, ShoppingBag, UserRound, X, Plus, Minus, Package, ShieldCheck, Leaf, Truck, ChevronLeft, ChevronRight, LogOut } from 'lucide-react';
import { api, money, readSession, saveSession } from './api';
import './styles.css';
import AccountPanel,{PasswordRecovery} from './components/AccountPanel';
import {ProductExtras,Wishlist,OrderExtras} from './components/ShoppingExtras';
import SupportPage from './components/SupportPage';
import AdminDashboard from './components/AdminDashboard';
import {guestCart,addGuest,updateGuest,mergeGuest,guestMergePending} from './guestCart';
import {payOrder} from './payments';

function ProductImage({ product }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [product.imageUrl]);
  const safe = /^(https?:\/\/|\/)/i.test(product.imageUrl || '');
  return safe && !failed ? <img src={product.imageUrl} alt={product.name} loading="lazy" onError={() => setFailed(true)} /> : <div className="placeholder"><Package size={56} strokeWidth={1}/><span>{product.category || 'Everyday essential'}</span></div>;
}
function Modal({ title, close, children }) {
  const box = useRef(null);
  const closeRef=useRef(close);closeRef.current=close;
  useEffect(() => {
    const previous = document.activeElement;
    const oldOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    box.current?.querySelector('button,input,select')?.focus();
    function key(e) {
      if (e.key === 'Escape') closeRef.current();
      if (e.key === 'Tab') {
        const nodes = [...box.current.querySelectorAll('button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href]')];
        const first = nodes[0], last = nodes.at(-1);
        if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last?.focus(); }
        else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first?.focus(); }
      }
    }
    document.addEventListener('keydown', key);
    return () => { document.body.style.overflow = oldOverflow; document.removeEventListener('keydown', key); previous?.focus(); };
  }, [title]);
  return <div className="overlay" onMouseDown={e => e.target === e.currentTarget && close()}><section ref={box} className="modal" role="dialog" aria-modal="true" aria-label={title}><div className="modal-head"><h2>{title}</h2><button className="icon-button" onClick={close} aria-label="Close dialog"><X/></button></div>{children}</section></div>;
}
export default function App() {
  const [session, setSession] = useState(readSession);
  const [view, setView] = useState('shop');
  const [products, setProducts] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [query, setQuery] = useState(''); const [search, setSearch] = useState('');
  const [category, setCategory] = useState(''); const [sort, setSort] = useState('createdAt,desc'); const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [reload, setReload] = useState(0);
  const [cart, setCart] = useState(null); const [orders, setOrders] = useState(null); const [addresses, setAddresses] = useState([]);
  const [modal, setModal] = useState(null); const [selected, setSelected] = useState(null); const [register, setRegister] = useState(false);
  const [busy, setBusy] = useState(false); const [formError, setFormError] = useState(''); const [toast, setToast] = useState('');
  const [addressId, setAddressId] = useState(''); const [coupon, setCoupon] = useState('');
  const [paymentMethod,setPaymentMethod]=useState('CASH_ON_DELIVERY');
  const [deliveryQuote,setDeliveryQuote]=useState(null);const [couponQuote,setCouponQuote]=useState(null);const [supportOrder,setSupportOrder]=useState('');
  const [attemptState,setAttemptState]=useState(null);const [orderPage,setOrderPage]=useState(0);
  const checkoutFlight = useRef(false);
  const pendingActions=useRef(0);
  useEffect(() => { const fn = () => setSession(readSession()); window.addEventListener('session-changed', fn); return () => window.removeEventListener('session-changed', fn); }, []);
  useEffect(() => { const timer = setTimeout(() => { setSearch(query); setPage(0); }, 300); return () => clearTimeout(timer); }, [query]);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setError('');
    const params = new URLSearchParams({ page, size: 12, sort });
    if (search) params.set('query', search); if (category) params.set('category', category);
    api(`/api/v1/products?${params}`, { signal: controller.signal }).then(setProducts).catch(e => { if (e.name !== 'AbortError') setError(e.message); }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [search, category, sort, page, reload]);
  useEffect(() => {
    let active = true;
    setCart(session?null:guestCart()); setOrders(null); setAddresses([]);
    if (session) api('/api/cart').then(value => { if (active) setCart(value); }).catch(e => { if (active) setToast(e.message); });
    return () => { active = false; };
  }, [session?.user?.id]);
  useEffect(()=>{const fn=()=>{if(!readSession())setCart(guestCart());};window.addEventListener('guest-cart-changed',fn);return()=>window.removeEventListener('guest-cart-changed',fn);},[]);
  useEffect(()=>{
    let live=true;setDeliveryQuote(null);setCouponQuote(null);
    if(modal==='checkout'&&addressId&&cart){const address=addresses.find(a=>String(a.id)===addressId);if(address)api('/api/delivery/quote',{method:'POST',body:{postalCode:address.postalCode,subtotal:cart.totalPrice}}).then(q=>{if(live)setDeliveryQuote(q);}).catch(e=>{if(live)setFormError(e.message);});}
    return()=>{live=false;};
  },[modal,addressId,addresses,cart?.totalPrice,coupon]);
  useEffect(()=>{if(view==='orders'&&session)action(async()=>setOrders(await api(`/api/orders?page=${orderPage}&size=20`)));},[orderPage]);
  useEffect(() => { if (!toast) return; const timer = setTimeout(() => setToast(''), 6000); return () => clearTimeout(timer); }, [toast]);
  function open(type) { setFormError(''); setModal(type); }
  async function action(fn) {pendingActions.current++;setBusy(true);setFormError('');try{await fn();return true;}catch(e){setFormError(e.message);setToast(e.message);return false;}finally{pendingActions.current--;setBusy(pendingActions.current>0);}}
  function shop(cat = '') { setView('shop'); setCategory(cat); setPage(0); document.getElementById('collection')?.scrollIntoView({ behavior: 'smooth' }); }
  async function add(product) {
    if (!session) {setCart(addGuest(product));setToast(`${product.name} added to your guest bag`);return;}
    await action(async () => { setCart(await api('/api/cart/items', { method: 'POST', body: { productId: product.id, quantity: 1 } })); setToast(`${product.name} added to your bag`); });
  }
  async function showCart() { open('cart');if(!session){setCart(guestCart());return;} await action(async () => setCart(await api('/api/cart'))); }
  async function changeQuantity(item, quantity) {
    if(!session){await action(async()=>setCart(updateGuest(item.productId,quantity)));return;}
    await action(async () => {
      if (quantity < 1) { await api(`/api/cart/items/${item.productId}`, { method: 'DELETE' }); setCart(await api('/api/cart')); }
      else setCart(await api(`/api/cart/items/${item.productId}`, { method: 'PUT', body: { quantity } }));
    });
  }
  async function refreshOrders(){setOrders(await api(`/api/orders?page=${orderPage}&size=20`));}
  async function showOrders() { if (!session) return open('auth'); setView('orders'); await action(refreshOrders); }
  async function authenticate(e) {
    e.preventDefault(); const body = Object.fromEntries(new FormData(e.currentTarget));
    await action(async () => {
      if (register) await api('/api/users/register', { method: 'POST', body });
      const auth = await api('/api/auth/login', { method: 'POST', body: { email: body.email, password: body.password } });
      saveSession(auth);setModal(null);setToast(`Welcome, ${auth.user.name.split(' ')[0]}!`);
      try{setCart(await mergeGuest(api));}catch(e){setToast(`Signed in. Some guest items could not be merged: ${e.message}`);}
    });
  }
  async function startCheckout() {
    if(!session){setToast('Sign in or create an account to checkout. Your guest bag will be merged.');open('auth');return;}
    open('checkout'); await action(async () => {setCart(await api('/api/cart')); const list = await api('/api/addresses'); setAddresses(list);let attempt;try{attempt=JSON.parse(sessionStorage.getItem('everyday-react-checkout'));}catch{} setAddressId(String(attempt?.body.addressId||list.find(a => a.defaultAddress)?.id || list[0]?.id || ''));if(attempt){setCoupon(attempt.body.couponCode||'');setPaymentMethod(attempt.body.paymentMethod);} });
  }
  async function signOut(){await action(async()=>{await api('/api/auth/logout',{method:'POST',headers:{'X-Refresh-Token':session.refreshToken}});saveSession(null);setView('shop');setModal(null);setToast('You have signed out');});}
  async function pay(order){await action(async()=>{const result=await payOrder(order,session?.user);setToast(result?'Payment verified.':'Payment window closed. Your order remains available.');await refreshOrders();});}
  async function inspectAttempt(){await action(async()=>{let a;try{a=JSON.parse(sessionStorage.getItem('everyday-react-checkout'));}catch{}if(!a){setAttemptState(null);setToast('No pending checkout attempt');return;}const o=await api(`/api/orders/attempts/${a.key}`);setAttemptState(o);setToast(`Checkout status: ${o.status}`);});}
  function startNewAttempt(){if(!attemptState||!['FAILED','CANCELLED','EXPIRED'].includes(attemptState.status))return;sessionStorage.removeItem('everyday-react-checkout');setAttemptState(null);setToast('You can start a new checkout. Adjust your cart first to create a new cart version.');}
  async function addAddress(e) {
    e.preventDefault(); const body = Object.fromEntries(new FormData(e.currentTarget));
    await action(async () => { const address = await api('/api/addresses', { method: 'POST', body }); setAddresses(old => [...old, address]); setAddressId(String(address.id)); setToast('Delivery address saved'); });
  }
  async function checkout(e) {
    e.preventDefault(); if (checkoutFlight.current) return; checkoutFlight.current = true;
    try { await action(async () => {
      const body = { addressId: Number(addressId), paymentMethod, ...(coupon.trim() ? { couponCode: coupon.trim() } : {}) };
      let attempt;
      try { attempt = JSON.parse(sessionStorage.getItem('everyday-react-checkout')); } catch { /* start a new attempt */ }
      if (attempt && JSON.stringify(attempt.body) !== JSON.stringify(body)) throw new Error('An earlier checkout has not been confirmed. Restore its address and coupon to retry, or check Orders before starting another checkout.');
      attempt ||= { key: crypto.randomUUID(), body };
      sessionStorage.setItem('everyday-react-checkout', JSON.stringify(attempt));
      const order = await api('/api/orders', { method: 'POST', headers: { 'Idempotency-Key': attempt.key }, body: attempt.body });
      attempt.orderId=order.id;sessionStorage.setItem('everyday-react-checkout',JSON.stringify(attempt));
      if (!['CREATING', 'RESERVED', 'CANCELLING','FAILED','EXPIRED','CANCELLED'].includes(order.status)) sessionStorage.removeItem('everyday-react-checkout');
      setModal(null); setView('orders');await refreshOrders(); setCart(await api('/api/cart'));
      setToast(['CREATING', 'RESERVED'].includes(order.status) ? 'Your order is being processed. Refresh Orders to check its status.' : 'Order received. You can track it in your orders.');
    }); } finally { checkoutFlight.current = false; }
  }
  return <>
    <div className="announcement">Thoughtfully chosen. Made for everyday living. <span>Discover your next favourite <ArrowRight size={13}/></span></div>
    <header><a className="brand" href="#" onClick={e => { e.preventDefault(); shop(); }}>everyday<span>®</span></a><nav aria-label="Main navigation"><button className={view === 'shop' ? 'active' : ''} onClick={() => shop()}>Shop all</button><button onClick={() => shop('Electronics')}>Electronics</button><button onClick={() => shop('Home')}>Home & living</button><button onClick={showOrders}>My orders</button><button onClick={()=>session?setView("wishlist"):open("auth")}>Wishlist</button><button onClick={()=>session?setView("support"):open("auth")}>Support</button>{session?.user?.roles?.includes("ROLE_ADMIN")&&<button onClick={()=>setView("admin")}>Admin</button>}</nav><div className="header-actions"><button className="icon-button" aria-label="Search products" onClick={() => { shop(); document.getElementById('search')?.focus(); }}><Search size={21}/></button><button aria-label={session ? 'Your account' : 'Sign in'} className="account-button" onClick={() => open(session ? 'account' : 'auth')}><UserRound size={21}/><span>{session?.user?.name?.split(' ')[0] || 'Sign in'}</span></button><button className="bag-button" onClick={showCart} aria-label={`Shopping bag, ${cart?.totalQuantity || 0} items`}><ShoppingBag size={21}/><span>{cart?.totalQuantity || 0}</span></button></div></header>
    <main>
    {view === 'admin' && session?.user?.roles?.includes('ROLE_ADMIN') ? <AdminDashboard action={action} busy={busy}/> : view === 'wishlist' && session ? <Wishlist action={action} busy={busy} onAdd={add} onSelect={p=>{setSelected(p);open('product');}}/> : view === 'support' && session ? <SupportPage key={supportOrder} orderId={supportOrder} action={action} busy={busy}/> : view === 'shop' ? <>
      <section className="hero"><div className="hero-copy"><div className="eyebrow"><span/> THE EVERYDAY EDIT</div><h1>Good things.<br/>For your <em>everyday.</em></h1><p>A little more comfort. A little more possibility.<br/>Explore essentials you’ll love coming back to.</p><button className="primary" onClick={() => shop()}>Explore the collection <ArrowUpRight size={19}/></button><div className="hero-note"><Leaf size={16}/> Carefully curated, simply loved.</div></div><div className="hero-art"><div className="art-label">LESS, BUT BETTER. <span>01 / EVERYDAY ESSENTIALS</span></div><div className="still-life"><div className="sun-disc"/><div className="vase"><i/><i/><i/></div><div className="speaker"><div/><span>everyday</span></div><div className="book">THE ART<br/>OF SLOW<br/>LIVING</div><div className="cup"><span/></div><div className="pedestal"/></div><div className="art-caption">Objects that make the ordinary feel extraordinary. <ArrowUpRight size={19}/></div></div></section>
      <section className="benefits" aria-label="Shopping benefits"><span><Package/> Thoughtfully selected</span><span><ShieldCheck/> Secure checkout</span><span><Truck/> Delivery to your doorstep</span><span><Leaf/> Essentials with purpose</span></section>
      <section id="collection" className="collection"><div className="section-heading"><div className="eyebrow">FIND YOUR EVERYDAY</div><h2>A few good essentials.</h2><p>Considered choices for the way you live.</p></div><div className="catalog-tools"><div className="tabs">{[['', 'All essentials'], ['Electronics', 'Electronics'], ['Home', 'Home & living'], ['Accessories', 'Accessories']].map(([value, label]) => <button key={value} className={category === value ? 'selected' : ''} onClick={() => { setCategory(value); setPage(0); }}>{label}</button>)}</div><div className="filters"><label className="search-box"><Search size={17}/><input id="search" value={query} onChange={e => setQuery(e.target.value)} placeholder="Find something good" aria-label="Search products"/></label><select aria-label="Sort products" value={sort} onChange={e => { setSort(e.target.value); setPage(0); }}><option value="createdAt,desc">Newest first</option><option value="price,asc">Price: low to high</option><option value="price,desc">Price: high to low</option><option value="name,asc">Name: A–Z</option></select></div></div>
      {error ? <div className="empty" role="alert"><Package/><h3>The collection is taking a moment.</h3><p>{error}</p><button className="primary" onClick={() => setReload(n => n + 1)}>Try again</button></div> : loading ? <div className="product-grid" aria-busy="true" aria-label="Loading products">{Array.from({ length: 4 }, (_, i) => <div className="skeleton" key={i}><div/><span/><span/></div>)}</div> : products.content.length === 0 ? <div className="empty"><Search/><h3>No essentials found</h3><p>Try another search or explore all categories.</p><button onClick={() => { setQuery(''); setCategory(''); }}>Clear filters</button></div> : <><div className="product-grid">{products.content.map(product => <article className="product-card" key={product.id}><button className="product-visual" onClick={() => { setSelected(product); open('product'); }} aria-label={`View ${product.name}`}><ProductImage product={product}/><span className="stock-tag">{product.stockQuantity > 0 ? 'THE EVERYDAY COLLECTION' : 'OUT OF STOCK'}</span><span className="view-arrow"><ArrowUpRight size={20}/></span></button><div className="product-info"><span className="product-category">{product.category || 'Essentials'}</span><button className="product-title" onClick={() => { setSelected(product); open('product'); }}>{product.name}</button><div className="product-bottom"><strong>{money(product.price)}</strong><button className="add-button" disabled={busy || product.stockQuantity < 1} onClick={() => add(product)} aria-label={`Add ${product.name} to bag`}><Plus size={18}/></button></div></div></article>)}</div><div className="catalog-footer"><span>{products.totalElements} thoughtfully selected essentials</span>{products.totalPages > 1 && <div className="pagination"><button disabled={page === 0} onClick={() => setPage(p => p - 1)} aria-label="Previous page"><ChevronLeft/></button><span>{page + 1} / {products.totalPages}</span><button disabled={page + 1 >= products.totalPages} onClick={() => setPage(p => p + 1)} aria-label="Next page"><ChevronRight/></button></div>}</div></>}
      </section><section className="manifesto"><Leaf size={30} strokeWidth={1}/><div><div className="eyebrow">OUR LITTLE PHILOSOPHY</div><h2>Everyday things. Lasting favourites.</h2><p>We believe the things you surround yourself with should make life a little better.<br/>So we bring together useful, beautiful essentials — all in one place.</p></div><button onClick={() => shop()} aria-label="Browse collection"><ArrowUpRight size={28}/></button></section>
    </> : <section className="orders-page"><button className="text-button" onClick={() => shop()}><ChevronLeft size={17}/> Back to the shop</button><div className="section-heading"><div className="eyebrow">YOUR EVERYDAY</div><h2>Your orders.</h2><p>All your good finds, in one place.</p></div><div className="row-actions"><button disabled={busy} onClick={showOrders}>Refresh orders</button><button disabled={busy} onClick={inspectAttempt}>Check saved checkout</button>{attemptState&&<span className="badge">{attemptState.status}</span>}{attemptState&&["FAILED","CANCELLED","EXPIRED"].includes(attemptState.status)&&<button onClick={startNewAttempt}>Start a new attempt</button>}</div>{!orders ? <p>{busy ? 'Loading your orders…' : 'Could not load your orders. Please try again.'}</p> : orders.content.length === 0 ? <div className="empty"><ShoppingBag/><h3>Your next favourite is waiting.</h3><p>Your orders will appear here once you checkout.</p><button className="primary" onClick={() => shop()}>Explore the collection</button></div> : orders.content.map(order => <article className="order-card" key={order.id}><div className="order-heading"><div><strong>Order {order.id.slice(0, 8)}</strong><p>{new Date(order.createdAt).toLocaleDateString('en-IN')}</p></div><span className="badge">{order.status.replaceAll('_', ' ')}</span><strong>{money(order.totalPrice)}</strong></div>{order.items.map(item => <p key={item.productId}>{item.name} <span>× {item.quantity}</span></p>)}<p>Payment: {order.paymentMethod.replaceAll('_', ' ')} · {order.paymentStatus}</p>{order.trackingNumber && <p>Tracking: {order.carrier} / {order.trackingNumber}</p>}{order.failureReason && <p className="form-error">{order.failureReason}</p>}{['CREATING','RESERVED','PENDING_PAYMENT','CONFIRMED','PROCESSING'].includes(order.status) && <button disabled={busy} onClick={() => action(async () => { await api(`/api/orders/${order.id}/cancel`, { method: 'POST' }); await refreshOrders(); setToast('Cancellation requested'); })}>Cancel order</button>}<OrderExtras order={order} action={action} busy={busy} onRefresh={refreshOrders} onPay={pay} onSupport={o=>{setSupportOrder(o.id);setView("support");}}/></article>)}{orders?.totalPages>1&&<div className="pagination"><button disabled={orderPage===0||busy} onClick={()=>setOrderPage(p=>p-1)}>Previous</button><span>{orderPage+1} / {orders.totalPages}</span><button disabled={orderPage+1>=orders.totalPages||busy} onClick={()=>setOrderPage(p=>p+1)}>Next</button></div>}</section>}
    </main><footer><div className="brand">everyday<span>®</span></div><p>Good things, every day.</p><div>Thoughtfully chosen essentials. <span>© {new Date().getFullYear()} Everyday</span></div></footer>
    {toast && <div className="toast" role="status">{toast}<button onClick={() => setToast('')} aria-label="Dismiss notification"><X size={16}/></button></div>}
    {modal && <Modal title={{ auth: register ? 'Make yourself at home.' : 'Welcome back.', account: 'Your account', cart: 'Your shopping bag', checkout: 'The final little step.', product: selected?.name, recovery: 'Recover your account' }[modal]} close={() => { if (!busy) setModal(null); }}>
      {formError && <p className="form-error" role="alert">{formError}</p>}
      {modal === 'auth' && <><p className="muted">{register ? 'Create an account to save your favourites and start shopping.' : 'Sign in to pick up where you left off.'}</p><form onSubmit={authenticate}>{register && <><label>Your name<input name="name" autoComplete="name" required maxLength={100}/></label><label>Phone number<input name="phone" type="tel" autoComplete="tel" pattern="[+]?[0-9]{7,15}" required/></label></>}<label>Email address<input name="email" type="email" autoComplete="email" required/></label><label>Password<input name="password" type="password" autoComplete={register ? 'new-password' : 'current-password'} minLength={register ? 8 : undefined} maxLength={100} required/></label><button className="primary full" disabled={busy}>{busy ? 'Please wait…' : register ? 'Create account' : 'Sign in'}<ArrowRight size={18}/></button></form><button className="text-button" disabled={busy} onClick={() => { setRegister(!register); setFormError(''); }}>{register ? 'Already have an account? Sign in' : 'New here? Create an account'}</button><button className="text-button" onClick={()=>open('recovery')}>Forgot password?</button></>}{modal==='recovery'&&<PasswordRecovery action={action} busy={busy}/>}
      {modal==='account'&&session&&<AccountPanel session={session} action={action} busy={busy} onOrders={()=>{setModal(null);showOrders();}} onAdmin={()=>{setModal(null);setView('admin');}} onLogout={signOut}/>}
      {modal === 'product' && selected && <><div className="detail-image"><ProductImage product={selected}/></div><div className="detail-price"><span>{selected.category}</span><strong>{money(selected.price)}</strong></div><p className="muted">{selected.description || 'A thoughtfully selected everyday essential.'}</p><p>{selected.stockQuantity > 0 ? `${selected.stockQuantity} available` : 'Currently out of stock'}</p><button className="primary full" disabled={busy || selected.stockQuantity < 1} onClick={() => add(selected)}>Add to bag <Plus size={18}/></button><ProductExtras product={selected} onSelect={setSelected} session={session} action={action} busy={busy}/></>}
      {modal === 'cart' && <>{session&&guestCart().items.length>0&&<section className="guest-recovery"><h3>Remaining guest items</h3><p className="muted">{guestMergePending()?"An earlier merge is unresolved. Retry it before editing its items.":"Correct unavailable items or quantities, then retry merging."}</p>{guestCart().items.map(i=><div className="row-actions" key={i.productId}><span>{i.name} × {i.quantity}</span><button disabled={busy||!!guestMergePending()} onClick={()=>action(async()=>updateGuest(i.productId,i.quantity-1))}>Decrease</button><button disabled={busy||!!guestMergePending()} onClick={()=>action(async()=>updateGuest(i.productId,0))}>Remove guest item</button></div>)}<button disabled={busy} onClick={()=>action(async()=>setCart(await mergeGuest(api)))}>Retry guest merge</button></section>}{!cart ? <p>{busy ? 'Loading your bag…' : 'Could not load your bag. Close and try again.'}</p> : !cart.items.length ? <div className="empty"><ShoppingBag/><h3>A little room for something good.</h3><button className="primary" onClick={() => { setModal(null); shop(); }}>Explore the collection</button></div> : <><div className="cart-items">{cart.items.map(item => <div className="cart-item" key={item.productId}><div><strong>{item.name}</strong><p>{money(item.unitPrice)} each {!item.available && <span className="form-error">· Unavailable</span>}</p><div className="quantity"><button aria-label={`Decrease ${item.name}`} disabled={busy} onClick={() => changeQuantity(item, item.quantity - 1)}><Minus size={14}/></button><span>{item.quantity}</span><button aria-label={`Increase ${item.name}`} disabled={busy} onClick={() => changeQuantity(item, item.quantity + 1)}><Plus size={14}/></button></div></div><div><strong>{money(item.subtotal)}</strong><button className="text-button" disabled={busy} onClick={() => changeQuantity(item, 0)}>Remove</button></div></div>)}</div><div className="total"><span>Subtotal</span><strong>{money(cart.totalPrice)}</strong></div><p className="muted">{cart.guest?"Your guest bag is saved in this tab. Sign in to merge it and complete checkout. Prices are estimates.":"Final pricing and availability are confirmed at checkout."}</p>{session&&guestCart().items.length>0&&<button disabled={busy} onClick={()=>action(async()=>setCart(await mergeGuest(api)))}>Retry merging remaining guest items</button>}<button className="primary full" disabled={busy || cart.items.some(i => !i.available)} onClick={startCheckout}>Continue to checkout <ArrowRight size={18}/></button></>}</>}
      {modal==='checkout'&&<><p className="muted">Choose your delivery address and payment method.</p>{addresses.length>0?<form onSubmit={checkout}><label>Delivery address<select value={addressId} onChange={e=>setAddressId(e.target.value)} required>{addresses.map(a=><option key={a.id} value={a.id}>{a.line1}, {a.city}, {a.postalCode}</option>)}</select></label><label>Payment method<select value={paymentMethod} onChange={e=>setPaymentMethod(e.target.value)}><option value="CASH_ON_DELIVERY">Cash on delivery</option><option value="RAZORPAY">Razorpay online test payment</option></select></label><label>Coupon code (optional)<input value={coupon} onChange={e=>setCoupon(e.target.value)} pattern="[A-Za-z0-9_-]{3,40}" placeholder="Enter your code"/></label><button type="button" disabled={busy||!coupon.trim()} onClick={()=>action(async()=>setCouponQuote(await api('/api/coupons/quote',{method:'POST',body:{code:coupon.trim(),subtotal:cart.totalPrice}})))}>Check coupon</button><div className="order-summary"><span>Subtotal {money(cart?.totalPrice)}</span><span>Discount −{money(couponQuote?.discount)}</span><span>Delivery {deliveryQuote?money(deliveryQuote.fee):'Calculating…'}</span>{deliveryQuote&&<span>Estimated arrival in {deliveryQuote.minDays}–{deliveryQuote.maxDays} days after confirmation</span>}</div><div className="total"><span>Estimated total</span><strong>{money(Number(couponQuote?.total??cart?.totalPrice??0)+Number(deliveryQuote?.fee??0))}</strong></div><p className="muted">Final stock, delivery charges and discount are confirmed by the server. Online payment opens from My orders once payment setup is ready.</p><button className="primary full" disabled={busy||!addressId||!cart?.items?.length||!deliveryQuote}>{busy?'Confirming your order…':'Place order'}<ArrowRight size={18}/></button></form>:<form onSubmit={addAddress}><h3>Where should we send your order?</h3>{[['line1','Street address'],['line2','Apartment, suite (optional)'],['city','City'],['state','State'],['postalCode','Postal code'],['country','Country']].map(([name,label])=><label key={name}>{label}<input name={name} required={name!=='line2'} defaultValue={name==='country'?'India':''}/></label>)}<button className="primary full" disabled={busy}>Save delivery address <ArrowRight size={18}/></button></form>}<div className="row-actions"><button disabled={busy} onClick={inspectAttempt}>Check saved checkout</button>{attemptState&&<span className="badge">{attemptState.status}</span>}{attemptState&&['FAILED','CANCELLED','EXPIRED'].includes(attemptState.status)&&<button onClick={startNewAttempt}>Start new checkout after adjusting your bag</button>}</div></>}
    </Modal>}
  </>;
}
