"""Generate the importable collection; uses handbook examples, corrected against current code."""
import copy
import importlib.util
import json
from pathlib import Path
import re
import uuid

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs' / 'postman'
OUT.mkdir(parents=True, exist_ok=True)
spec = importlib.util.spec_from_file_location('catalog', ROOT / 'docs/handbook-build/api_catalog.py')
c = importlib.util.module_from_spec(spec)
spec.loader.exec_module(c)
apis = copy.deepcopy(c.APIS)
for a in apis:
    a['rules'] = a['rules'].replace('Email delivery is not implemented.', 'An account-message outbox queues an email with the OTP through notification-service. Requests have a 60-second cooldown; SMTP must be enabled for receipt.')
    a['rules'] = a['rules'].replace('Flags store preferences; they do not configure an SMS/email provider or control the current notification consumer.', 'Order delivery workers read the current email/SMS/WhatsApp preferences and active status. Preferences do not enable provider configuration; account-security emails bypass these preferences.')

def add(group, name, method, route, body, sample, status=200, access='Bearer access token', rules='', source=''):
    apis.append(dict(group=group, name=name, method=method, route=route, request=body, response=sample,
                     status=status, access=access, rules=rules, errors='', source=source, aliases=[], headers={}))

add('User service', 'Resend email verification', 'POST', '/api/auth/resend-verification', {'email':'asha@example.com'}, None, 202, 'Public',
    'Queues an email-only verification code. Requires an existing unverified account; 60-second cooldown returns 400. Already verified accounts return without another message. OTP expires after ten minutes.', 'AuthController.resend')
apis[-1]['aliases'] = ['POST /auth/resend-verification']
delivery = dict(id='33333333-3333-4333-8333-333333333333',channel='EMAIL',type='OrderConfirmed',orderId=c.OID,status='PENDING',attempts=0,failure=None,createdAt=c.T)
add('Notification service','List external deliveries','GET','/api/notifications/deliveries?page=0&size=20',None,c.page(delivery),rules='Own deliveries only. ACCEPTED means provider acceptance, not recipient delivery. UNKNOWN requires provider inspection; there are no provider delivery-status callbacks. Recipients, bodies and provider IDs are omitted.',source='DeliveryController.list')
add('Notification service','Read external delivery','GET','/api/notifications/deliveries/{{deliveryId}}',None,delivery,rules='UUID; missing or foreign delivery returns 404.',source='DeliveryController.get')
add('Internal APIs','Read customer contact and preferences','GET','/internal/users/1/contact',None,dict(active=True,email='asha@example.com',phone='+919876543210',emailNotifications=True,smsNotifications=True,whatsAppNotifications=False),access='X-Service-Key on user-service port 8082',rules='Trusted service only. Returns current contact data, active status and channel preferences; not exposed through the explicit public gateway routes.',source='ContactController.get')
add('Internal APIs','Queue an account email','PUT','/internal/messages/{{messageId}}',dict(userId=1,channel='EMAIL',recipient='{{recipientEmail}}',subject='Postman test account message',body='Requested test message; no action required.',expiresAt='{{messageExpiresAt}}'),dict(id='33333333-3333-4333-8333-333333333333',status='PENDING'),202,'X-Service-Key on notification-service port 8086',
    'May send real email when enabled. UUID message ID; EMAIL only; expiry in the future and at most one day away. Same ID/content is idempotent; changed content returns 409. Account messages bypass order preferences.', 'DeliveryController.send')

DESCRIPTIONS = {
 'User service':'Accounts, login/refresh sessions, passwords, email verification, profiles, addresses and notification preferences. Port 8082; owns userdb.',
 'Product service':'Catalog discovery and administration; atomic inventory reservations, commits and return restocking. Port 8083; owns productdb; optional Redis cache.',
 'Cart service':'Per-customer cart quantities and versioned checkout snapshots. Cart additions do not reserve inventory. Port 8084; owns cartdb.',
 'Order service':'Durable idempotent checkout, Razorpay test payments, cancellation, shipping state and refunds. Port 8085; owns orderdb; publishes Kafka outbox events.',
 'Reviews and wishlist':'Order-service APIs for verified purchase reviews and saved products (port 8085).',
 'Coupons':'Order-service discount rules and authoritative checkout discounts (port 8085).',
 'Returns':'Order-service full-order return approval, receipt/restocking, online refund and manual COD refund records (port 8085).',
 'Notification service':'Kafka order-event inbox plus durable SMTP email and Twilio SMS/WhatsApp queue. Account-security emails arrive through internal HTTP. Port 8086; owns notificationdb.',
 'Internal APIs':'Trusted service-to-service endpoints; sent directly to the owning port using X-Service-Key. Inventory mutations bypass order business rules: use only dedicated test fixtures.',
}
def event(kind, code):
    return {'listen':kind,'script':{'type':'text/javascript','exec':code.strip().splitlines()}}

def raw_json(body):
    raw=json.dumps(body,indent=2,ensure_ascii=False)
    # These variables are numeric in Java request DTOs, not JSON strings.
    return re.sub(r'(:\s*)"(\{\{(?:userId|productId|addressId|cartVersion)\}\})"',r'\1\2',raw)

def req(name, method, route, body=None, sample=None, status=200, desc='', base='baseUrl', auth='customer', pre='', test='', codes=None):
    headers=[{'key':'Accept','value':'application/json'}]
    request={'method':method,'header':headers,'url':'{{'+base+'}}'+route,'description':desc+'\n\nSaved responses are illustrative examples derived from code, not captured live responses.'}
    if auth=='none': request['auth']={'type':'noauth'}
    elif auth=='internal':
        request['auth']={'type':'noauth'}
        headers.append({'key':'X-Service-Key','value':'{{internalServiceKey}}'})
    else: request['auth']={'type':'bearer','bearer':[{'key':'token','value':'{{'+('adminAccessToken' if auth=='admin' else 'accessToken')+'}}','type':'string'}]}
    if body is not None:
        headers.append({'key':'Content-Type','value':'application/json'})
        request['body']={'mode':'raw','raw':raw_json(body),'options':{'raw':{'language':'json'}}}
    allowed=codes or [status]
    checks='''const allowed = %s;
pm.test('Expected HTTP status: ' + allowed.join('/'), () => pm.expect(pm.response.code).to.be.oneOf(allowed));
if (!allowed.includes(pm.response.code)) { pm.execution.setNextRequest(null); }
else {
%s
}
''' % (json.dumps(allowed), test)
    item={'id':str(uuid.uuid5(uuid.NAMESPACE_URL,'ecommerce-postman/'+name)), 'name':name,'request':request,
          'event':[event('prerequest',pre)] if pre else []}
    item['event'].append(event('test',checks))
    labels={200:'OK',201:'Created',202:'Accepted',204:'No Content',400:'Bad Request',401:'Unauthorized',403:'Forbidden',404:'Not Found',409:'Conflict',503:'Service Unavailable'}
    item['response']=[{'name':f'{status} — illustrative example','originalRequest':copy.deepcopy(request),'status':labels.get(status,''),'code':status,
        '_postman_previewlanguage':'json' if not isinstance(sample,str) else 'text',
        'header':[] if sample is None else [{'key':'Content-Type','value':'text/plain' if isinstance(sample,str) else 'application/json'}],
        'body':'' if sample is None else sample if isinstance(sample,str) else json.dumps(sample,indent=2,ensure_ascii=False)}]
    return item

def capture(key, expr):
    return f"pm.environment.set('{key}', {expr});"
def check(expr, label):
    return f"pm.test({json.dumps(label)}, () => {{ pm.expect({expr}).to.equal(true); }}); if (!({expr})) pm.execution.setNextRequest(null);"
def folder(name,description,items): return dict(name=name,description=description,item=items)

SETUP='''
if (pm.environment.get('referenceRequest')) { pm.execution.skipRequest(); }
else {
const runId = Date.now().toString() + Math.floor(Math.random()*100000).toString();
pm.environment.set('runId', runId);
pm.environment.set('customerEmail', 'postman.' + runId + '@example.invalid');
pm.environment.set('customerPhone', '+999' + runId.slice(-11));
pm.environment.set('customerPassword', 'PmTest!' + runId);
['accessToken','refreshToken','userId','addressId','productId','orderId','deliveryId','cartVersion','initialStock','adminAccessToken','adminRefreshToken','pollCount','notificationPollCount'].forEach(k => pm.environment.unset(k));
pm.environment.set('checkoutKey', 'pm-' + runId);
pm.environment.set('adminCheckoutKey', 'pm-admin-' + runId);
pm.environment.set('couponCode', 'PM' + runId);
pm.environment.set('productSku', 'PM-' + runId);
pm.environment.set('messageId', pm.variables.replaceIn('{{$guid}}'));
pm.environment.set('reservationId', pm.variables.replaceIn('{{$guid}}'));
pm.environment.set('messageExpiresAt', new Date(Date.now()+600000).toISOString());
pm.environment.set('couponExpiresAt', new Date(Date.now()+86400000).toISOString());
}
'''
SKIP_REFERENCE="if (pm.environment.get('referenceRequest')) { pm.execution.skipRequest(); }"
ADMIN=SKIP_REFERENCE+"\nif (String(pm.environment.get('enableAdminFlow')) !== 'true') { console.info('SKIP optional admin flow'); pm.execution.skipRequest(); }"
smoke=[]
def flow(name,method,route,body=None,sample=None,status=200,**kw):
    item=req(name,method,route,body,sample,status,pre=kw.pop('pre',SKIP_REFERENCE),**kw); smoke.append(item); return item
flow('01 Initialize run and check gateway','GET','/api/v1/products?page=0&size=1',sample=c.page(c.PROD),auth='none',pre=SETUP,test=check("Array.isArray(pm.response.json().content)",'Gateway routes to product service'),desc='Creates unique test customer credentials and clears stale run state. Uses a public domain API because Docker puts actuator on a separate management port. Reserved .invalid email and synthetic phone prevent using a real customer identity. Registration still queues a verification email; use disabled channels or an SMTP capture server for this run.')
flow('02 Register test customer','POST','/api/users/register',dict(name='Postman Test Customer',email='{{customerEmail}}',phone='{{customerPhone}}',password='{{customerPassword}}'),c.USER,201,auth='none',test=capture('userId','pm.response.json().id'))
authbody=dict(email='{{customerEmail}}',password='{{customerPassword}}')
tokens="const j=pm.response.json(); pm.environment.set('accessToken',j.accessToken); pm.environment.set('refreshToken',j.refreshToken);"+check("typeof j.accessToken === 'string' && typeof j.refreshToken === 'string'",'Access and refresh tokens returned')
flow('03 Login customer','POST','/api/auth/login',authbody,c.AUTH,auth='none',test=tokens)
flow('04 Rotate refresh token','POST','/api/auth/refresh',dict(refreshToken='{{refreshToken}}'),c.AUTH,auth='none',test=tokens)
flow('05 Read own profile','GET','/api/users/me',sample=c.USER,test=check('String(pm.response.json().id) === String(pm.environment.get("userId"))','Own profile returned'))
flow('06 Disable external order notifications for test customer','PUT','/api/users/preferences',dict(emailNotifications=False,smsNotifications=False,whatsAppNotifications=False),dict(c.PREF,emailNotifications=False,smsNotifications=False,whatsAppNotifications=False),desc='Keeps order updates in the in-app inbox without attempting external delivery. Verification emails are separate account messages.')
flow('07 Save checkout address','POST','/api/addresses',c.ADDR_REQ,c.ADDR,201,test=capture('addressId','pm.response.json().id'))
flow('08 Discover an in-stock product','GET','/api/v1/products?inStock=true&page=0&size=20',sample=c.page(c.PROD),auth='none',test="const p=pm.response.json().content.find(p=>p.stockQuantity>=1); pm.test('At least one active product is stocked',()=>pm.expect(Boolean(p)).to.equal(true)); if(!p) pm.execution.setNextRequest(null); else { pm.environment.set('productId',p.id); pm.environment.set('initialStock',p.stockQuantity); }",desc='Uses an existing seeded product; requires at least one active item in stock. Default COD flow restores inventory by cancellation.')
flow('09 Save wishlist product','PUT','/api/wishlist/{{productId}}',sample=c.WISH)
flow('10 List wishlist','GET','/api/wishlist',sample=c.page(c.WISH))
flow('11 Add one item to cart','POST','/api/cart/items',dict(productId='{{productId}}',quantity=1),c.CART,test=capture('cartVersion','pm.response.json().version'))
flow('12 Set quantity to one','PUT','/api/cart/items/{{productId}}',dict(quantity=1),c.CART,test=capture('cartVersion','pm.response.json().version'))
flow('13 Checkout COD','POST','/api/orders',dict(addressId='{{addressId}}',paymentMethod='CASH_ON_DELIVERY'),c.ORDER,codes=[200,202],test=capture('orderId','pm.response.json().id')+" pm.environment.set('pollCount',0);",desc='Server snapshots prices/address and reserves stock. Reuses one Idempotency-Key for the retry.')['request']['header'].append({'key':'Idempotency-Key','value':'{{checkoutKey}}'})
flow('14 Retry same checkout key','POST','/api/orders',dict(addressId='{{addressId}}',paymentMethod='CASH_ON_DELIVERY'),c.ORDER,codes=[200,202],test=check('pm.response.json().id === pm.environment.get("orderId")','Same order returned on retry'))['request']['header'].append({'key':'Idempotency-Key','value':'{{checkoutKey}}'})

def poll(state, counter='pollCount'):
    return """const j=pm.response.json();
if(j.status !== %s && ['CREATING','RESERVED','CANCELLING','RETURN_RECEIVING'].includes(j.status)) {
 const n=Number(pm.environment.get(%s)||0)+1; pm.environment.set(%s,n);
 if(n<Number(pm.environment.get('maxPollAttempts')||30)) pm.execution.setNextRequest(pm.info.requestId);
 else { pm.test('Lifecycle reached target before poll limit',()=>pm.expect(j.status).to.equal(%s)); pm.execution.setNextRequest(null); }
} else {
 pm.environment.set(%s,0);
 pm.test('Lifecycle reached '+%s,()=>pm.expect(j.status).to.equal(%s));
 if(j.status!==%s) pm.execution.setNextRequest(null);
}
""" % tuple(json.dumps(x) for x in [state,counter,counter,state,counter,state,state,state])
flow('15 Wait for COD confirmation','GET','/api/orders/{{orderId}}',sample=c.ORDER,test=poll('CONFIRMED'))
flow('16 Assert checkout cleared cart','GET','/api/cart',sample=c.EMPTY_CART,test=check('pm.response.json().items.length === 0','Cart is empty after checkout'))
flow('17 Assert one unit reserved','GET','/api/v1/products/{{productId}}',sample=c.PROD,auth='none',test=check('pm.response.json().stockQuantity === Number(pm.environment.get("initialStock"))-1','Stock deducted exactly once'),desc='Requires an otherwise idle test catalog; concurrent shoppers can change stock and invalidate the equality assertion.')
flow('18 Cancel COD order','POST','/api/orders/{{orderId}}/cancel',sample=c.order(status='CANCELLED'),codes=[200,202],test="pm.environment.set('pollCount',0);")
flow('19 Wait for cancellation','GET','/api/orders/{{orderId}}',sample=c.order(status='CANCELLED'),test=poll('CANCELLED'))
flow('20 Assert stock restored','GET','/api/v1/products/{{productId}}',sample=c.PROD,auth='none',test=check('pm.response.json().stockQuantity === Number(pm.environment.get("initialStock"))','Cancellation restored stock'))
flow('21 Remove wishlist item','DELETE','/api/wishlist/{{productId}}',status=204)
flow('22 Wait for order notification','GET','/api/notifications?size=100',sample=c.page(c.NOTICE),test="const rows=pm.response.json().content; const found=rows.some(x=>x.orderId===pm.environment.get('orderId')); const n=Number(pm.environment.get('notificationPollCount')||0)+1; pm.environment.set('notificationPollCount',n); if(!found && n<Number(pm.environment.get('maxPollAttempts')||30)) pm.execution.setNextRequest(pm.info.requestId); else { pm.test('Kafka notification exists for this order',()=>pm.expect(found).to.equal(true)); if(!found) pm.execution.setNextRequest(null); }")
flow('23 List queued external delivery history','GET','/api/notifications/deliveries?size=100',sample=c.page(delivery),test="const rows=pm.response.json().content; const d=rows.find(x=>x.orderId===pm.environment.get('orderId')); pm.test('Order channel deliveries were queued',()=>pm.expect(Boolean(d)).to.equal(true)); if(d) pm.environment.set('deliveryId',d.id); else pm.execution.setNextRequest(null);")
flow('24 Read one delivery','GET','/api/notifications/deliveries/{{deliveryId}}',sample=delivery)

admin=[]
def af(name,method,route,body=None,sample=None,status=200,**kw):
    item=req(name,method,route,body,sample,status,pre=ADMIN,**kw); admin.append(item); return item
af('A01 Login existing administrator','POST','/api/auth/login',dict(email='{{adminEmail}}',password='{{adminPassword}}'),c.AUTH,auth='none',test="const j=pm.response.json(); pm.environment.set('adminAccessToken',j.accessToken); pm.environment.set('adminRefreshToken',j.refreshToken);"+check("j.user.roles.includes('ROLE_ADMIN')",'Account has ROLE_ADMIN'),desc='Enable only on a test installation. Registration cannot grant admin. Supply an existing admin login; no JWT signing secret or role bypass is used.')
af('A02 Create test coupon','POST','/api/admin/coupons',dict(code='{{couponCode}}',type='PERCENT',value=10,minimumSpend=0,expiresAt='{{couponExpiresAt}}',usageLimit=1),c.COUPON,auth='admin')
af('A03 Quote coupon','POST','/api/coupons/quote',dict(code='{{couponCode}}',subtotal=100),dict(code='SAVE10',subtotal=100,discount=10,total=90))
af('A04 Refill customer cart','POST','/api/cart/items',dict(productId='{{productId}}',quantity=1),c.CART)
af('A05 Checkout discounted COD order','POST','/api/orders',dict(addressId='{{addressId}}',paymentMethod='CASH_ON_DELIVERY',couponCode='{{couponCode}}'),c.ORDER,codes=[200,202],test=capture('orderId','pm.response.json().id')+" pm.environment.set('pollCount',0);")['request']['header'].append({'key':'Idempotency-Key','value':'{{adminCheckoutKey}}'})
af('A06 Wait for confirmation','GET','/api/orders/{{orderId}}',sample=c.ORDER,test=poll('CONFIRMED'))
for index,state in enumerate(['PROCESSING','SHIPPED','DELIVERED'],7):
    body={'status':state}
    if state=='SHIPPED': body.update(carrier='Postman simulated carrier',trackingNumber='PM-{{runId}}')
    if state=='DELIVERED': body['cashCollected']=True
    af(f'A{index:02d} Mark {state}','PATCH','/api/admin/orders/{{orderId}}/status',body,c.order(status=state),auth='admin',test=check(f'pm.response.json().status === "{state}"',f'Status is {state}'),desc='Test-only fulfillment simulation. No carrier booking, physical shipment, or cash transfer is performed.')
af('A10 Review delivered purchase','POST','/api/orders/{{orderId}}/reviews',dict(productId='{{productId}}',rating=5,comment='Automated Postman test review'),c.REVIEW,201)
af('A11 Request full return','POST','/api/orders/{{orderId}}/returns',dict(reason='Automated test return'),c.order(status='DELIVERED',returnRequest=c.return_requested),test=check('pm.response.json().returnRequest.status === "REQUESTED"','Return requested'))
af('A12 Approve return','PATCH','/api/admin/orders/{{orderId}}/return',dict(decision='APPROVED',note='Automated test approval'),c.order(status='DELIVERED',returnRequest=c.return_approved),auth='admin')
af('A13 Receive and restock return','POST','/api/admin/orders/{{orderId}}/return/receive',dict(restock=True),c.order(status='RETURNED',returnRequest=dict(c.return_received,restock=True)),auth='admin',test="pm.environment.set('pollCount',0);")
af('A14 Wait for returned state','GET','/api/orders/{{orderId}}',sample=c.order(status='RETURNED'),test=poll('RETURNED'))
af('A15 Record simulated COD refund','POST','/api/admin/orders/{{orderId}}/refund/manual',dict(reference='POSTMAN-SIMULATED-{{runId}}'),c.order(status='RETURNED',paymentStatus='REFUNDED',refund=dict(status='PROCESSED',razorpayRefundId=None,failure=None)),auth='admin',test=check('pm.response.json().paymentStatus === "REFUNDED"','Manual refund recorded'),desc='Records a simulated refund only on the test order. This endpoint does not transfer or verify money. Never use this fixture to attest a real customer refund.')
af('A16 Check returned inventory','GET','/api/v1/products/{{productId}}',sample=c.PROD,auth='none',test=check('pm.response.json().stockQuantity === Number(pm.environment.get("initialStock"))','Return restored inventory'))
af('A17 Deactivate test coupon','PATCH','/api/admin/coupons/{{couponCode}}',dict(active=False),dict(c.COUPON,active=False),auth='admin')
af('A18 Logout administrator session','POST','/api/auth/logout',status=204,auth='admin')['request']['header'].append({'key':'X-Refresh-Token','value':'{{adminRefreshToken}}'})

groups={k:[] for k in DESCRIPTIONS}
manifest=[]
for a in apis:
    variants=[(a['method'],a['route'],False)]+[(x.split(' ',1)[0],x.split(' ',1)[1],True) for x in a['aliases']]
    for method,original,alias in variants:
        route=original.replace(c.OID,'{{orderId}}').replace('/SAVE10','/{{couponCode}}')
        for prefix,key in [('/api/users/','userId'),('/internal/users/','userId'),('/internal/carts/','userId'),('/api/addresses/','addressId'),('/api/v1/products/','productId'),('/api/cart/items/','productId'),('/api/wishlist/','productId'),('/api/reviews/products/','productId')]:
            route=route.replace(prefix+'1',prefix+'{{'+key+'}}')
        if route.startswith('/internal/inventory'): route=route.replace('{{orderId}}','{{reservationId}}')
        body=copy.deepcopy(a['request'])
        if isinstance(body,dict):
            for key,var in [('email','customerEmail'),('password','customerPassword'),('phone','customerPhone'),('refreshToken','refreshToken'),('addressId','addressId'),('productId','productId'),('userId','userId')]:
                if key in body: body[key]='{{'+var+'}}'
            if 'oldPassword' in body: body['oldPassword']='{{customerPassword}}'
            if 'newPassword' in body: body['newPassword']='{{newPassword}}'
            if 'otp' in body: body['otp']='{{resetOtp}}' if 'reset-password' in route else '{{verificationOtp}}'
            if 'couponCode' in body: body['couponCode']='{{couponCode}}'
            if 'code' in body: body['code']='{{couponCode}}'
            if 'sku' in body: body['sku']='{{productSku}}'
            if 'items' in body and isinstance(body['items'],dict): body['items']={'{{productId}}':1}
            if 'version' in body: body['version']='{{cartVersion}}'
            if 'expiresAt' in body and '/coupons' in route: body['expiresAt']='{{couponExpiresAt}}'
            for key,var in [('razorpay_order_id','razorpayOrderId'),('razorpay_payment_id','razorpayPaymentId'),('razorpay_signature','razorpaySignature')]:
                if key in body: body[key]='{{'+var+'}}'
        base='baseUrl'; auth='customer'
        if 'Public' in a['access'] or a['access'].startswith('Raw-body'): auth='none'
        if 'ROLE_ADMIN' in a['access']: auth='admin'
        if 'X-Service-Key' in a['access']:
            auth='internal'
            base=next(v for port,v in [('8082','userUrl'),('8083','productUrl'),('8084','cartUrl'),('8086','notificationUrl')] if port in a['access'])
        if route=='/hello' or route.startswith('/auth/') or route=='/forgot-password': base='userUrl'
        name=f"REF {a['group']} | {a['name']}"+(f' (alias {original})' if alias else '')
        pre="if (pm.environment.get('referenceRequest') !== pm.info.requestName) { pm.execution.skipRequest(); }"
        if route.startswith('/internal/messages/'):
            pre+="\nif(!pm.environment.get('messageId')) pm.environment.set('messageId',pm.variables.replaceIn('{{$guid}}')); if(!pm.environment.get('messageExpiresAt')) pm.environment.set('messageExpiresAt',new Date(Date.now()+600000).toISOString());"
        test=''
        if '/login' in route or '/refresh' in route: test=tokens
        if a['name']=='Register a customer': test=capture('userId','pm.response.json().id')
        if a['name']=='Add an address': test=capture('addressId','pm.response.json().id')
        if a['name']=='Create a product': test=capture('productId','pm.response.json().id')
        if a['name']=='Create or resume checkout': test=capture('orderId','pm.response.json().id')
        if a['name'] in ['Read your cart','Add quantity to your cart','Set a cart line quantity','Fetch a checkout cart snapshot']: test=capture('cartVersion','pm.response.json().version')
        if a['name']=='List external deliveries': test="const d=pm.response.json().content[0]; if(d) pm.environment.set('deliveryId',d.id);"
        if '/payments/verify' in route: test=check('pm.response.json().paymentStatus === "PAID"','Payment captured and verified')
        desc=f"{a['rules']}\n\nAccess: {a['access']}.\n\nExpected errors: {a['errors'] or 'Validation/authentication and dependency errors depend on request state.'}\n\nImplementation: {a['source']}.\n\nTo send this reference request, set referenceRequest to this request's exact name. Populate its IDs and credentials first. Incompatible lifecycle operations are intentionally not run as one sequence."
        item=req(name,method,route,body,a['response'],a['status'],desc,base,auth,pre,test,codes=[200,202] if a['name'] in ['Create or resume checkout','Cancel an eligible order'] else None)
        for k,v in a['headers'].items():
            value={'Idempotency-Key':'{{checkoutKey}}','X-Refresh-Token':'{{refreshToken}}','X-Razorpay-Signature':'{{webhookSignature}}'}.get(k,v)
            item['request']['header'].append({'key':k,'value':value})
        if 'webhook' in route:
            item['request']['body']['raw']=json.dumps(dict(event='payment.captured',payload=dict(payment=dict(entity=dict(id='{{razorpayPaymentId}}',order_id='{{razorpayOrderId}}')))),indent=2)
            item['request']['description']+='\n\nSet webhookSignature to the HMAC-SHA256 of the exact resolved raw body using the server webhook secret. Do not reuse a signature after changing whitespace or IDs. Real provider payment state is rechecked. No signature or successful provider payment is fabricated.'
        groups[a['group']].append(item)
        manifest.append(dict(method=method,path=original,request=name,source=a['source']))

# A separate online checkout example avoids changing the default COD scenario.
online=req('REF Order service | Create Razorpay test checkout','POST','/api/orders',dict(addressId='{{addressId}}',paymentMethod='RAZORPAY'),c.order(status='PENDING_PAYMENT',paymentMethod='RAZORPAY',checkout=dict(keyId='rzp_test_EXAMPLE',razorpayOrderId='order_example',amount=180000,currency='INR',testMode=True)),desc='Requires a nonempty cart, a new checkoutKey and Razorpay test keys on order-service. Pay the returned provider order through Razorpay Checkout, then populate razorpayPaymentId/razorpaySignature and send the verify request. Postman cannot complete the interactive payment authorization. Subsequent cancellation triggers a provider refund for a captured payment.',pre="if(pm.environment.get('referenceRequest')!==pm.info.requestName) pm.execution.skipRequest();",test="const j=pm.response.json(); pm.environment.set('orderId',j.id); if(j.checkout) pm.environment.set('razorpayOrderId',j.checkout.razorpayOrderId);",codes=[200,202])
online['request']['header'].append({'key':'Idempotency-Key','value':'{{checkoutKey}}'})
groups['Order service'].append(online)

ops=[]
for label,base in [('Gateway','baseUrl'),('User','userUrl'),('Product','productUrl'),('Cart','cartUrl'),('Order','orderUrl'),('Notification','notificationUrl'),('Config server','configUrl'),('Eureka registry','registryUrl')]:
    for path in ['/actuator/health','/actuator/info']:
        name=f'OPS {label} | {path}'
        ops.append(req(name,'GET',path,sample={'status':'UP'} if path.endswith('health') else {},base=base,auth='none',pre="if(pm.environment.get('referenceRequest')!==pm.info.requestName) pm.execution.skipRequest();",desc='Direct operational endpoint. Exposure and authentication depend on the active profile; protected info endpoints may require a Bearer token. An empty info object is normal. Health is not proof of successful provider delivery.'))
for name,base,path,sample,desc in [
 ('OPS Gateway | Actuator discovery','baseUrl','/actuator',{'_links':{'self':{'href':'http://localhost:8081/actuator','templated':False}}},'Lists actuator endpoints actually exposed by the running profile; not every Spring actuator endpoint is enabled.'),
 ('OPS Gateway | Routes','baseUrl','/actuator/gateway/routes',[{'route_id':'products','uri':'lb://product-service','order':0}],'Read-only routing diagnostics. Gateway exposes customer APIs; it has no separate domain CRUD controllers.'),
 ('OPS Gateway | Storefront','baseUrl','/index.html','<!DOCTYPE html>\n<!-- Storefront HTML; abbreviated illustrative example -->','Serves the bundled storefront; response is HTML.'),
 ('OPS Eureka | Registered applications','registryUrl','/eureka/apps',{'applications':{'versions__delta':'1','apps__hashcode':'','application':[]}},'Eureka discovers service instances. Accept: application/json selects JSON. No business entities are managed here.'),
 ('OPS Config | Application configuration','configUrl','/{{configApplication}}/{{configProfile}}',{'name':'order-service','profiles':['default'],'label':None,'version':'example-git-revision','state':None,'propertySources':[]},'Spring Cloud Config serves properties from Git or the native repository. Responses can contain credentials: do not share/export a live response. Example omits properties intentionally. Config server may be omitted by the container startup profile.')]:
    ops.append(req(name,'GET',path,sample=sample,base=base,auth='none',pre="if(pm.environment.get('referenceRequest')!==pm.info.requestName) pm.execution.skipRequest();",desc=desc))

cleanup=[req('25 Logout test customer session','POST','/api/auth/logout',status=204,pre=SKIP_REFERENCE,desc='Revokes the generated refresh session. Test customer, address, cancelled order and notification history remain for inspection; no existing customer is deleted.')]
cleanup[0]['request']['header'].append({'key':'X-Refresh-Token','value':'{{refreshToken}}'})
collection=dict(info={'_postman_id':str(uuid.uuid5(uuid.NAMESPACE_URL,'ecommerce-all-services')),'name':'Ecommerce — automated flows and all service APIs','schema':'https://schema.getpostman.com/json/collection/v2.1.0/collection.json','description':'Import with Ecommerce.local.postman_environment.json. Run the entire collection with a 2000 ms request delay and one iteration. Default: unique customer → login/refresh → address → catalog/wishlist/cart → idempotent COD checkout → cancellation/stock restoration → Kafka inbox/delivery history → logout. Optional enableAdminFlow=true adds simulated shipping, delivery, return and manual refund with an existing administrator. Reference requests are skipped unless referenceRequest equals their exact name. Saved responses are illustrative, not live evidence. See README.md for prerequisites, provider limits, all eight service roles and verification.'},item=[folder('01 Automated customer COD flow','Runs by default against the gateway. Requires all domain services, Kafka, discovery and a stocked catalog.',smoke),folder('02 Optional admin shipping and return flow','Set enableAdminFlow=true and adminEmail/adminPassword. Test-only fulfillment/cash/refund simulation; restores stock but leaves review/order history.',admin),folder('03 End automated run','Ends the generated customer refresh session.',cleanup)]+[folder('Reference — '+k,v,groups[k]) for k,v in DESCRIPTIONS.items()]+[folder('Operations — Gateway, Config, Eureka and service health','No custom REST controllers exist in the three infrastructure services. These read-only framework endpoints complement the complete application-controller inventory. Other actuators depend on exposure/profile; use actuator discovery.',ops)])

defaults=dict(baseUrl='http://localhost:8081',userUrl='http://localhost:8082',productUrl='http://localhost:8083',cartUrl='http://localhost:8084',orderUrl='http://localhost:8085',notificationUrl='http://localhost:8086',configUrl='http://localhost:8080',registryUrl='http://localhost:8761',enableAdminFlow='false',adminEmail='',adminPassword='',referenceRequest='',maxPollAttempts='30',internalServiceKey='',recipientEmail='',verificationOtp='',resetOtp='',newPassword='ReplaceWithYourTestPassword123!',razorpayOrderId='',razorpayPaymentId='',razorpaySignature='',webhookSignature='',configApplication='order-service',configProfile='default')
for key in ['runId','customerEmail','customerPhone','customerPassword','accessToken','refreshToken','userId','addressId','productId','orderId','deliveryId','cartVersion','initialStock','adminAccessToken','adminRefreshToken','checkoutKey','adminCheckoutKey','couponCode','couponExpiresAt','productSku','messageId','messageExpiresAt','reservationId']:
    defaults[key]=''
env={'id':str(uuid.uuid5(uuid.NAMESPACE_URL,'ecommerce-local-environment')),'name':'Ecommerce local','values':[dict(key=k,value=v,enabled=True,type='secret' if any(x in k.lower() for x in ['password','token','otp','signature','servicekey']) else 'default') for k,v in defaults.items()],'_postman_variable_scope':'environment'}
# Workflow examples use one unit, matching the automated requests (reference examples
# retain their separately documented two-unit scenario).
for item in smoke+admin:
    example=item['response'][0]
    if not example['body'] or example['_postman_previewlanguage']!='json': continue
    body=json.loads(example['body'])
    if isinstance(body,dict) and 'paymentMethod' in body:
        discounted=item in admin
        body.update(items=[dict(c.ITEM,quantity=1)],subtotal=1000,discount=100 if discounted else 0,
                    totalPrice=900 if discounted else 1000,couponCode='SAVE10' if discounted else None)
        if body['status'] in ['DELIVERED','RETURNED']:
            body.update(deliveredAt=c.T,shippedAt=c.T,carrier='Postman simulated carrier',trackingNumber='PM-example')
            if body['paymentStatus']!='REFUNDED': body['paymentStatus']='COLLECTED'
        if body['status']=='RETURNED' and body['paymentStatus']!='REFUNDED':
            body.update(paymentStatus='REFUND_PENDING',refund=dict(status='MANUAL_REQUIRED',razorpayRefundId=None,failure=None),returnRequest=dict(c.return_received,restock=True))
    if isinstance(body,dict) and 'totalQuantity' in body and body['items']:
        body.update(totalQuantity=1,totalPrice=1000)
        body['items']=[dict(body['items'][0],quantity=1,subtotal=1000)]
    if item['name'].startswith('17 '): body['stockQuantity']=9
    example['body']=json.dumps(body,indent=2,ensure_ascii=False)
# Keep saved original requests consistent with headers added by workflow builders.
for f in collection['item']:
    for item in f['item']:
        for response in item['response']:
            response['originalRequest']=copy.deepcopy(item['request'])
for filename,data in [('Ecommerce.postman_collection.json',collection),('Ecommerce.local.postman_environment.json',env),('endpoint-coverage.json',manifest)]:
    (OUT/filename).write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print(f'Generated {sum(len(f["item"]) for f in collection["item"])} requests; {len(manifest)} controller route variants.')
