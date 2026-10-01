import {api} from './api.js';
let loading;
async function load(){
  if(window.Razorpay)return;
  loading ||= new Promise((resolve,reject)=>{const script=document.createElement('script');script.src='https://checkout.razorpay.com/v1/checkout.js';script.onload=resolve;script.onerror=()=>{script.remove();loading=null;reject(new Error('Payment checkout could not load. Retry payment from My orders.'));};document.head.append(script);});
  await loading;if(!window.Razorpay)throw new Error('Payment checkout is unavailable.');
}
export async function payOrder(order,user){
  if(!order.checkout)throw new Error('Payment is not ready. Refresh your orders.');
  await load();const checkout=order.checkout;
  return new Promise((resolve,reject)=>{
    const widget=new window.Razorpay({key:checkout.keyId,order_id:checkout.razorpayOrderId,amount:checkout.amount,currency:checkout.currency,name:'Everyday',description:`Order ${order.id.slice(0,8)}`,prefill:{name:user?.name,email:user?.email,contact:user?.phone},
      handler:result=>api(`/api/orders/${order.id}/payments/verify`,{method:'POST',body:result}).then(resolve,reject),
      modal:{ondismiss:()=>resolve(null)}});
    widget.on('payment.failed',event=>reject(new Error(event.error?.description||'Payment failed. Check your order before retrying.')));widget.open();
  });
}
