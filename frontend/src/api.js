const key = 'everyday-react-auth';
export function readSession() {
  try { return JSON.parse(sessionStorage.getItem(key)) || null; } catch { return null; }
}
export function saveSession(session) {
  if (session) sessionStorage.setItem(key, JSON.stringify(session));
  else { sessionStorage.removeItem(key); sessionStorage.removeItem('everyday-react-checkout'); }
  window.dispatchEvent(new Event('session-changed'));
}
let refreshing;
export async function api(path, options = {}, retry = true) {
  const session = readSession();
  const headers = { ...options.headers };
  if (session?.accessToken) headers.Authorization = `Bearer ${session.accessToken}`;
  if(path==='/api/auth/logout'&&session?.refreshToken)headers['X-Refresh-Token']=session.refreshToken;
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  let response;
  try {
    response = await fetch(`${import.meta.env?.VITE_API_BASE_URL || ''}${path}`, {
      ...options, headers, body: options.body === undefined ? undefined : JSON.stringify(options.body), signal: options.signal || AbortSignal.timeout(20000),
    });
  } catch (error) {
    if (error.name === 'AbortError') throw error;
    throw new Error('Could not reach the store. Check that the backend is running and try again. For checkout, check your orders before retrying.');
  }
  if (response.status === 401 && retry && session?.refreshToken && !['/api/auth/login','/api/auth/refresh','/api/auth/forgot-password','/api/auth/reset-password'].includes(path)) {
    refreshing ||= api('/api/auth/refresh', { method: 'POST', body: { refreshToken: session.refreshToken } }, false)
      .then(saveSession).catch(error => { saveSession(null); throw error; }).finally(() => { refreshing = null; });
    await refreshing;
    return api(path, options, false);
  }
  const raw = await response.text();
  let body;
  try { body = raw ? JSON.parse(raw) : null; } catch { body = null; }
  if (!response.ok) {
    const fields = body?.fieldErrors || body?.errors;
    const detail = fields && typeof fields === 'object' ? Object.entries(fields).map(([name, value]) => `${name}: ${value}`).join('; ') : '';
    const error = new Error(body?.detail || body?.message || body?.failureReason || detail || (response.status === 401 ? 'Please sign in to continue.' : `Request failed (${response.status}). Please try again.`));
    error.status = response.status; error.body = body; throw error;
  }
  return body;
}
export const money = value => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 }).format(value ?? 0);
