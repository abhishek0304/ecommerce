/* Carrier operations share the storefront's API client and authentication. */
'use strict';
async function shipmentDialog(id) {
  const rows = await api(`/api/orders/${id}/shipments`);
  const order = currentOrders.get(id);
  const admin = isAdmin();
  const cards = rows.map(s => `<section class="panel"><strong>${esc(s.direction)} · ${esc(s.provider)}</strong>
    <p>${esc(s.status)} · ${esc(s.mode)}${s.mode === 'MOCK' ? ' — simulation only; no carrier was contacted' : ''}</p>
    <p>${esc(s.carrier)} ${esc(s.awb || '')}</p><p>Carrier status: ${esc(s.trackingStatus || 'Not refreshed')} · ${date(s.trackedAt)}</p>
    ${s.pickupStatus ? `<p>Pickup: ${esc(s.pickupStatus)}</p>` : ''}${s.failure ? `<p class="notice">${esc(s.failure)}</p>` : ''}
    ${['BOOKING', 'CREATED', 'UNKNOWN'].includes(s.status) ? '<p class="notice">Inspect this booking with the carrier before taking further action. Repeating it will not create another shipment.</p>' : ''}
    ${admin && s.status === 'BOOKED' ? `<div class="form-actions"><button class="secondary small" data-action="shipment-track" data-id="${id}" data-direction="${s.direction}">Refresh carrier status</button><button class="secondary small" data-action="shipment-label" data-id="${id}" data-direction="${s.direction}">Get label</button>${!s.pickupStatus ? `<button class="secondary small" data-action="shipment-pickup" data-id="${id}" data-direction="${s.direction}">Request pickup</button>` : ''}</div>` : ''}</section>`).join('');
  const directions = [];
  if (admin && order && ['CONFIRMED', 'PROCESSING'].includes(order.status) && !rows.some(s => s.direction === 'FORWARD')) directions.push('FORWARD');
  if (admin && order?.status === 'DELIVERED' && order.returnRequest?.status === 'APPROVED' && !rows.some(s => s.direction === 'RETURN')) directions.push('RETURN');
  const form = directions.length ? `<form id="shipment-book-form" data-id="${id}"><h3>Book a shipment</h3>
    <p>The saved order address and prices will be used. Booking uses the server's configured mode; outside MOCK it contacts the carrier. A booking blocks ordinary customer cancellation.</p>
    <div class="form-grid"><label>Provider<select name="provider"><option>SHIPROCKET</option><option>DELHIVERY</option></select></label>
    <label>Direction<select name="direction">${directions.map(d => `<option>${d}</option>`).join('')}</select></label>
    ${field('Recipient name', 'recipientName', 'text', '', 'required maxlength="100"')}
    ${field('Recipient phone (+91)', 'recipientPhone', 'tel', '', 'required pattern="\\+91[6-9][0-9]{9}"')}
    ${field('Recipient email', 'recipientEmail', 'email', '', 'required maxlength="254"')}
    ${field('Weight (kg)', 'weightKg', 'number', '', 'required min="0.01" max="50" step="0.01"')}
    ${['length', 'breadth', 'height'].map(n => field(`${n} (cm)`, `${n}Cm`, 'number', '', 'required min="0.1" max="200" step="0.1"')).join('')}
    ${field('HSN (same code for all items)', 'hsn', 'text', '', 'pattern="[0-9]{4,8}"')}
    ${field('E-waybill if required', 'ewaybill', 'text', '', 'pattern="[0-9]{12}"')}</div>
    <div class="form-actions"><button>Book shipment</button></div></form>` : '';
  openDialog('Shipments', (cards || '<p>No carrier booking has been recorded.</p>') + form);
}
document.addEventListener('click', async event => {
  const button = event.target.closest('[data-action]');
  if (!button || !['shipments', 'shipment-track', 'shipment-label', 'shipment-pickup'].includes(button.dataset.action)) return;
  const { action, id, direction } = button.dataset;
  button.disabled = true;
  try {
    if (action === 'shipments') await shipmentDialog(id);
    if (action === 'shipment-track') {
      await api(`/api/admin/orders/${id}/shipments/${direction}/tracking`, { method: 'POST' }); await shipmentDialog(id);
    }
    if (action === 'shipment-label') {
      const label = await api(`/api/admin/orders/${id}/shipments/${direction}/label`, { method: 'POST' });
      const url = label.url && new URL(label.url);
      openDialog('Shipping label', `<p>${esc(label.status)}</p><p>${esc(label.note)}</p>${url?.protocol === 'https:' ? `<a href="${esc(url.href)}" target="_blank" rel="noopener noreferrer">Open provider label</a>` : ''}`);
    }
    if (action === 'shipment-pickup') {
      openDialog('Request pickup', `<form id="shipment-pickup-form" data-id="${id}" data-direction="${direction}"><p>Delhivery forward pickup uses this date and time (India time). Shiprocket chooses its pickup slot; Delhivery return pickup is automatic. This does not confirm collection.</p><div class="form-grid">${field('Pickup date', 'date', 'date', '', 'required')}${field('Pickup time', 'time', 'time', '', 'required step="1"')}</div><button>Request pickup</button></form>`);
    }
  } catch (error) { toast(error.message, true); }
  finally { button.disabled = false; }
});
document.addEventListener('submit', async event => {
  const form = event.target;
  if (!['shipment-book-form', 'shipment-pickup-form'].includes(form.id)) return;
  event.preventDefault();
  if (form.dataset.busy) return;
  form.dataset.busy = 'true';
  const button = form.querySelector('button'); button.disabled = true;
  try {
    const data = Object.fromEntries(new FormData(form));
    let direction = form.dataset.direction;
    let suffix = '/pickup';
    if (form.id === 'shipment-book-form') {
      direction = data.direction; delete data.direction; suffix = '';
      ['weightKg', 'lengthCm', 'breadthCm', 'heightCm'].forEach(k => { data[k] = Number(data[k]); });
      if (!data.hsn) delete data.hsn; if (!data.ewaybill) delete data.ewaybill;
    } else if (data.time.length === 5) data.time += ':00';
    const result = await api(`/api/admin/orders/${form.dataset.id}/shipments/${direction}${suffix}`, { method: 'POST', body: data });
    toast(`Shipment: ${result.status}${result.mode === 'MOCK' ? ' (simulation)' : ''}`);
    await render(); await shipmentDialog(form.dataset.id);
  } catch (error) { toast(error.message, true); }
  finally { button.disabled = false; delete form.dataset.busy; }
});
