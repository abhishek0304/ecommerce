const key='everyday-guest-cart';
export function guestCart() {
  let items=[];try {items=JSON.parse(sessionStorage.getItem(key))||[];}catch{}
  items=items.filter(i=>Number.isInteger(i.productId)&&i.productId>0&&Number.isInteger(i.quantity)&&i.quantity>0);
  return {items:items.map(i=>({...i,subtotal:i.unitPrice*i.quantity,available:true})),totalQuantity:items.reduce((n,i)=>n+i.quantity,0),totalPrice:items.reduce((n,i)=>n+i.unitPrice*i.quantity,0),guest:true};
}
function write(items){sessionStorage.setItem(key,JSON.stringify(items));window.dispatchEvent(new Event('guest-cart-changed'));return guestCart();}
export function addGuest(product){const cart=guestCart();const old=cart.items.find(i=>i.productId===product.id);if(old)old.quantity++;else cart.items.push({productId:product.id,name:product.name,quantity:1,unitPrice:Number(product.price)});return write(cart.items);}
export function guestMergePending(){try{return JSON.parse(sessionStorage.getItem('everyday-guest-merge'));}catch{return null;}}
export function updateGuest(id,quantity){if(guestMergePending()?.items[id])throw new Error('Retry the unresolved guest merge before editing these items.');return write(guestCart().items.map(i=>i.productId===id?{...i,quantity}:i).filter(i=>i.quantity>0));}
export async function mergeGuest(api){
  const pendingKey='everyday-guest-merge';let attempt;try{attempt=JSON.parse(sessionStorage.getItem(pendingKey));}catch{}
  if(!attempt){const items=guestCart().items;if(!items.length)return api('/api/cart');attempt={key:crypto.randomUUID(),items:Object.fromEntries(items.map(i=>[i.productId,i.quantity]))};sessionStorage.setItem(pendingKey,JSON.stringify(attempt));}
  let cart;
  try{cart=await api('/api/cart/merge',{method:'POST',headers:{'Idempotency-Key':attempt.key},body:{items:attempt.items}});}catch(error){
    // These business errors are raised before commit. Uncertain responses keep the attempt key.
    if(error.status===400||error.status===404||(error.status===409&&/Insufficient stock|Product is inactive|Invalid quantity/.test(error.message)))sessionStorage.removeItem(pendingKey);
    throw error;
  }
  const remaining=guestCart().items.map(i=>({...i,quantity:i.quantity-(attempt.items[i.productId]||0)})).filter(i=>i.quantity>0);
  write(remaining);sessionStorage.removeItem(pendingKey);return cart;
}
