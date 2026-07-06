// API Helper

// Parses a fetch Response, throwing an Error that carries the HTTP `status`
// (and raw `body`) so callers can branch on the status code instead of
// string-matching the message.
async function parseResponse(res) {
  if (!res.ok) {
    let body = '';
    try {
      body = await res.text();
    } catch (e) {
      // ignore: body may be empty/unavailable
    }
    const err = new Error(body || `HTTP ${res.status}`);
    err.status = res.status;
    err.body = body;
    throw err;
  }
  return res.status === 204 ? null : res.json();
}

export const api = {
  async get(url) {
    return parseResponse(await fetch(url));
  },
  async post(url, data) {
    return parseResponse(await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    }));
  },
  async put(url, data) {
    return parseResponse(await fetch(url, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    }));
  },
  async delete(url) {
    return parseResponse(await fetch(url, { method: 'DELETE' }));
  }
};
