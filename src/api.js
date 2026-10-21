const BASE = import.meta.env.VITE_API_BASE_URL || '/api';
function cookie(name) { return document.cookie.split('; ').find(row => row.startsWith(`${name}=`))?.split('=')[1]; }
async function request(path, options = {}) {
  const method = options.method || 'GET';
  const headers = { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(method !== 'GET' && cookie('XSRF-TOKEN') ? { 'X-XSRF-TOKEN': decodeURIComponent(cookie('XSRF-TOKEN')) } : {}), ...options.headers };
  let response;
  try { response = await fetch(`${BASE}${path}`, { ...options, method, headers, credentials: 'include' }); }
  catch { throw new Error('Unable to reach RailEase. Check your connection and try again.'); }
  if (response.status === 204) return null;
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(payload.message || payload.error || `Request failed (${response.status})`);
  return payload;
}
export const api = {
  csrf: () => request('/auth/csrf'),
  login: credentials => request('/auth/login', { method: 'POST', body: JSON.stringify(credentials) }),
  register: credentials => request('/auth/register', { method: 'POST', body: JSON.stringify(credentials) }),
  logout: () => request('/auth/logout', { method: 'POST' }),
  me: () => request('/auth/me'),
  train: number => request(`/trains/${encodeURIComponent(number)}`),
  book: reservation => request('/reservations', { method: 'POST', body: JSON.stringify(reservation) }),
  createPaymentOrder: reservation => request('/payments/order', { method: 'POST', body: JSON.stringify(reservation) }),
  verifyPayment: payment => request('/payments/verify', { method: 'POST', body: JSON.stringify(payment) }),
  fetchBooking: pnr => request(`/reservations/${encodeURIComponent(pnr)}`),
  cancel: pnr => request(`/reservations/${encodeURIComponent(pnr)}`, { method: 'DELETE' })
};
