import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { api, saveSession, readSession } from '../src/api.js';

const values = new Map();
globalThis.sessionStorage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) };
globalThis.window = new EventTarget();
beforeEach(() => values.clear());

test('sends a bearer token and serializes cart requests', async () => {
  saveSession({ accessToken: 'access', user: { id: 1 } });
  globalThis.fetch = async (path, options) => {
    assert.equal(path, '/api/cart/items');
    assert.equal(options.headers.Authorization, 'Bearer access');
    assert.equal(options.headers['Content-Type'], 'application/json');
    assert.deepEqual(JSON.parse(options.body), { productId: 2, quantity: 1 });
    return Response.json({ items: [{ productId: 2 }], totalQuantity: 1 });
  };
  assert.equal((await api('/api/cart/items', { method: 'POST', body: { productId: 2, quantity: 1 } })).totalQuantity, 1);
});

test('refreshes expired credentials and preserves checkout idempotency key on retry', async () => {
  saveSession({ accessToken: 'expired', refreshToken: 'refresh', user: { id: 1 } });
  let calls = 0;
  globalThis.fetch = async (path, options) => {
    calls++;
    if (path === '/api/auth/refresh') {
      assert.deepEqual(JSON.parse(options.body), { refreshToken: 'refresh' });
      return Response.json({ accessToken: 'new', refreshToken: 'next', user: { id: 1 } });
    }
    assert.equal(options.headers['Idempotency-Key'], 'same-attempt');
    return options.headers.Authorization === 'Bearer expired' ? new Response('', { status: 401 }) : Response.json({ id: 'order' });
  };
  assert.equal((await api('/api/orders', { method: 'POST', headers: { 'Idempotency-Key': 'same-attempt' }, body: { addressId: 1 } })).id, 'order');
  assert.equal(calls, 3);
  assert.equal(readSession().accessToken, 'new');
});

test('supports empty DELETE responses and reports backend validation failures', async () => {
  globalThis.fetch = async () => new Response(null, { status: 204 });
  assert.equal(await api('/api/cart/items/1', { method: 'DELETE' }), null);
  globalThis.fetch = async () => Response.json({ fieldErrors: { quantity: 'must be positive' } }, { status: 400 });
  await assert.rejects(api('/api/cart'), /quantity: must be positive/);
});

test('failed refresh clears credentials instead of retrying indefinitely', async () => {
  saveSession({ accessToken: 'old', refreshToken: 'bad' });
  let calls = 0;
  globalThis.fetch = async () => { calls++; return new Response('', { status: 401 }); };
  await assert.rejects(api('/api/cart'), /sign in/);
  assert.equal(readSession(), null);
  assert.equal(calls, 2);
});

test('network failures produce a useful connection error', async () => {
  globalThis.fetch = async () => { throw new TypeError('Failed to fetch'); };
  await assert.rejects(api('/api/v1/products'), /backend is running/);
});

test('logout refreshes expired access and revokes the rotated refresh token',async()=>{
  saveSession({accessToken:'old',refreshToken:'previous'});let revoked;
  globalThis.fetch=async(path,options)=>{if(path==='/api/auth/refresh')return Response.json({accessToken:'new',refreshToken:'rotated'});if(options.headers.Authorization==='Bearer old')return new Response('',{status:401});revoked=options.headers['X-Refresh-Token'];return new Response(null,{status:204});};
  await api('/api/auth/logout',{method:'POST',headers:{'X-Refresh-Token':'previous'}});assert.equal(revoked,'rotated');
});
