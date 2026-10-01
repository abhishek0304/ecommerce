import {test,beforeEach} from 'node:test';
import assert from 'node:assert/strict';
import {guestCart,addGuest,updateGuest,mergeGuest} from '../src/guestCart.js';
const values=new Map();globalThis.sessionStorage={getItem:k=>values.get(k)??null,setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k)};globalThis.window=new EventTarget();
beforeEach(()=>values.clear());
test('guest bag supports additions quantities and removal',()=>{addGuest({id:1,name:'Mouse',price:10});addGuest({id:1,name:'Mouse',price:10});assert.equal(guestCart().totalPrice,20);updateGuest(1,3);assert.equal(guestCart().totalQuantity,3);updateGuest(1,0);assert.equal(guestCart().items.length,0);});
test('uncertain merge retains the same key and body on retry',async()=>{addGuest({id:1,name:'Mouse',price:10});let first;const request=async(path,options)=>{assert.equal(path,'/api/cart/merge');if(!first){first=options;throw new Error('lost response');}assert.equal(options.headers['Idempotency-Key'],first.headers['Idempotency-Key']);assert.deepEqual(options.body,first.body);return {totalQuantity:1};};await assert.rejects(mergeGuest(request),/lost response/);assert.equal(guestCart().totalQuantity,1);await mergeGuest(request);assert.equal(guestCart().totalQuantity,0);});
test('items added while merging remain in the guest bag',async()=>{addGuest({id:1,name:'Mouse',price:10});await mergeGuest(async()=>{addGuest({id:2,name:'Keyboard',price:20});return {};});assert.deepEqual(guestCart().items.map(i=>i.productId),[2]);});
test('definite stock rejection permits correcting the guest bag with a new attempt',async()=>{
  addGuest({id:1,name:'Mouse',price:10});let originalKey;
  await assert.rejects(mergeGuest(async(path,options)=>{originalKey=options.headers['Idempotency-Key'];const e=new Error('Insufficient stock');e.status=409;throw e;}));
  updateGuest(1,2);await mergeGuest(async(path,options)=>{assert.notEqual(options.headers['Idempotency-Key'],originalKey);assert.equal(options.body.items[1],2);return {};});assert.equal(guestCart().totalQuantity,0);
});
