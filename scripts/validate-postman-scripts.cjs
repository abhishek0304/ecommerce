// Script syntax and deterministic workflow contract tests. No HTTP/provider calls.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const collection = JSON.parse(fs.readFileSync(path.join(root, 'docs/postman/Ecommerce.postman_collection.json'), 'utf8'));
const template = JSON.parse(fs.readFileSync(path.join(root, 'docs/postman/Ecommerce.local.postman_environment.json'), 'utf8'));
const items = collection.item.flatMap(f => f.item);
let scripts = 0;
for (const item of items) for (const e of item.event) { new vm.Script(e.script.exec.join('\n')); scripts++; }

function harness(admin=false) {
  const values = new Map(template.values.map(v => [v.key,v.value]));
  values.set('enableAdminFlow', String(admin));
  const state = { values, skipped:false, next:undefined, assertions:0 };
  state.run = (item, kind, body={}, code=200) => {
    state.skipped=false; state.next=undefined;
    const pm = {
      environment:{ get:k=>values.get(k), set:(k,v)=>values.set(k,v), unset:k=>values.delete(k) },
      variables:{ replaceIn:s=>s.replace('{{$guid}}','44444444-4444-4444-8444-444444444444') },
      info:{requestId:item.id,requestName:item.name},
      execution:{skipRequest:()=>{state.skipped=true;},setNextRequest:x=>{state.next=x;}},
      response:{code,json:()=>body},
      test:(name,fn)=>{try{fn(); state.assertions++;}catch(e){throw new Error(item.name+' / '+name+': '+e.message);}},
      expect:v=>({to:{equal:other=>assert.deepEqual(v,other),be:{oneOf:arr=>assert.ok(arr.includes(v))}}})
    };
    for(const e of item.event.filter(e=>e.listen===kind)) vm.runInNewContext(e.script.exec.join('\n'),{pm,console:{info:()=>{}}},{timeout:1000});
  };
  return state;
}
function responseFor(item,h) {
  const name=item.name, v=h.values;
  const sample=item.response[0];
  let body=sample.body && sample._postman_previewlanguage==='json'?JSON.parse(sample.body):{};
  if(name==='01 Initialize run and check gateway') body={content:[{id:5,stockQuantity:10}]};
  if(/^(03|04)/.test(name)) body={accessToken:'test-access',refreshToken:'test-refresh',user:{id:7,roles:['ROLE_USER']}};
  if(name.startsWith('02 ')) body={id:7};
  if(name.startsWith('05 ')) body={id:7};
  if(name.startsWith('07 ')) body={id:9};
  if(name.startsWith('08 ')) body={content:[{id:5,stockQuantity:10}]};
  if(/^(11|12)/.test(name)) body={version:4};
  if(/^(13|14|15)/.test(name)) body={id:'11111111-1111-4111-8111-111111111111',status:'CONFIRMED'};
  if(name.startsWith('16 ')) body={items:[]};
  if(name.startsWith('17 ')) body={stockQuantity:9};
  if(/^(18|19)/.test(name)) body={status:'CANCELLED'};
  if(name.startsWith('20 ')||name.startsWith('A16 ')) body={stockQuantity:10};
  if(name.startsWith('22 ')) body={content:[{orderId:v.get('orderId')}]};
  if(name.startsWith('23 ')) body={content:[{orderId:v.get('orderId'),id:'33333333-3333-4333-8333-333333333333'}]};
  if(name.startsWith('A01 ')) body={accessToken:'test-admin',refreshToken:'test-admin-refresh',user:{roles:['ROLE_ADMIN']}};
  if(name.startsWith('A05 ')||name.startsWith('A06 ')) body={id:'22222222-2222-4222-8222-222222222222',status:'CONFIRMED'};
  return [body,sample.code];
}
for(const admin of [false,true]) {
  const h=harness(admin); let sent=0,skipped=0;
  for(const item of items) {
    h.run(item,'prerequest');
    if(h.skipped){skipped++;continue;}
    const raw=JSON.stringify(item.request);
    for(const [,key] of raw.matchAll(/\{\{([^}]+)\}\}/g)) {
      if(admin && ['adminEmail','adminPassword'].includes(key)) h.values.set(key,'test-admin-input');
      assert.notEqual(h.values.get(key),undefined,`Undefined ${key}`);
      assert.notEqual(h.values.get(key),'',`Empty workflow variable ${key} in ${item.name}`);
    }
    const [body,code]=responseFor(item,h); h.run(item,'test',body,code); sent++;
    assert.notEqual(h.next,null,'Unexpected workflow stop');
  }
  assert.equal(sent,admin?43:25);
  console.log(`PASS: simulated ${admin?'admin + customer':'customer'} workflow, ${sent} executed, ${skipped} skipped, ${h.assertions} assertions.`);
}
const h=harness();
const poll=items.find(x=>x.name.startsWith('15 '));
h.run(poll,'test',{status:'CREATING'},200); assert.equal(h.next,poll.id);
h.values.set('pollCount',29);
assert.throws(()=>h.run(poll,'test',{status:'RESERVED'},200),/Lifecycle reached target/);
h.values.set('pollCount',0);
assert.throws(()=>h.run(poll,'test',{status:'FAILED'},200),/Lifecycle reached CONFIRMED/);
const reference=items.find(x=>x.name.startsWith('REF User service | Read your profile'));
h.values.set('referenceRequest',reference.name);
for(const item of items) {
  h.run(item,'prerequest');
  assert.equal(h.skipped,item.id!==reference.id,'Exact reference selection gate');
}
console.log(`PASS: ${scripts} scripts compile; polling retries, limits, terminal failures and exact reference gating verified. These are simulations, not live-service tests.`);
