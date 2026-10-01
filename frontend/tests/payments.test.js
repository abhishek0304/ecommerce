import {test,beforeEach} from 'node:test';
import assert from 'node:assert/strict';
import {payOrder} from '../src/payments.js';
const values=new Map();globalThis.sessionStorage={getItem:k=>values.get(k)??null,setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k)};
const order={id:'test-order',checkout:{keyId:'rzp_test_fixture',razorpayOrderId:'order_fixture',amount:50000,currency:'INR'}};
beforeEach(()=>{globalThis.window={};values.clear();});
test('uses the server checkout amount and verifies the provider callback with backend',async()=>{
  let received;window.Razorpay=class{constructor(options){this.options=options;assert.equal(options.amount,50000);assert.equal(options.order_id,'order_fixture');}on(){}open(){this.options.handler({razorpay_order_id:'order_fixture',razorpay_payment_id:'pay_fixture',razorpay_signature:'signature'});}};
  globalThis.fetch=async(path,options)=>{assert.equal(path,'/api/orders/test-order/payments/verify');received=JSON.parse(options.body);return Response.json({status:'CONFIRMED'});};
  assert.equal((await payOrder(order,{name:'Test'})).status,'CONFIRMED');assert.equal(received.razorpay_payment_id,'pay_fixture');
});
test('dismissing payment resolves without marking the order paid',async()=>{window.Razorpay=class{constructor(options){this.options=options;}on(){}open(){this.options.modal.ondismiss();}};assert.equal(await payOrder(order),null);});
test('does not open payments before backend payment setup is ready',async()=>{await assert.rejects(payOrder({id:'pending'}),/not ready/);});
