const STORAGE_KEY = 'authUser';

const parseError = async (response) => {
  try {
    const data = await response.json();
    return data.message || response.statusText;
  } catch (e) {
    return response.statusText;
  }
};

const getAuthHeader = () => {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return {};
  try {
    const parsed = JSON.parse(raw);
    if (parsed?.accessToken) {
      return { Authorization: `Bearer ${parsed.accessToken}` };
    }
  } catch {
    // ignore
  }
  return {};
};

const saveTokens = (payload) => {
  const raw = localStorage.getItem(STORAGE_KEY);
  let stored = {};
  if (raw) {
    try { stored = JSON.parse(raw); } catch { /* ignore */ }
  }
  if (payload.accessToken) stored.accessToken = payload.accessToken;
  if (payload.refreshToken) stored.refreshToken = payload.refreshToken;
  if (payload.expiresIn) stored.tokenExpiresAt = Date.now() + payload.expiresIn * 1000;
  if (payload.id || payload.userId) {
    stored.id = payload.id || payload.userId;
    stored.username = payload.username;
    stored.phone = payload.phone;
    stored.email = payload.email;
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(stored));
};

const clearTokens = () => {
  localStorage.removeItem(STORAGE_KEY);
};

export async function apiGet(url) {
  const resp = await fetch(url, {
    headers: { ...getAuthHeader() }
  });
  if (!resp.ok) {
    const msg = await parseError(resp);
    if (resp.status === 401) {
      clearTokens();
      window.location.href = '/login?redirect=' + encodeURIComponent(window.location.pathname);
    }
    throw new Error(msg);
  }
  return resp.json();
}

export async function apiPost(url, payload) {
  const resp = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...getAuthHeader() },
    body: JSON.stringify(payload || {})
  });
  if (!resp.ok) {
    const msg = await parseError(resp);
    if (resp.status === 401) {
      clearTokens();
      window.location.href = '/login?redirect=' + encodeURIComponent(window.location.pathname);
    }
    throw new Error(msg);
  }
  const data = await resp.json();
  if (data?.accessToken) {
    saveTokens(data);
  }
  return data;
}

export async function apiPut(url, payload) {
  const resp = await fetch(url, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...getAuthHeader() },
    body: JSON.stringify(payload || {})
  });
  if (!resp.ok) {
    const msg = await parseError(resp);
    if (resp.status === 401) {
      clearTokens();
      window.location.href = '/login?redirect=' + encodeURIComponent(window.location.pathname);
    }
    throw new Error(msg);
  }
  return resp.json();
}

export async function apiDelete(url) {
  const resp = await fetch(url, {
    method: 'DELETE',
    headers: { ...getAuthHeader() }
  });
  if (!resp.ok) {
    const msg = await parseError(resp);
    if (resp.status === 401) {
      clearTokens();
      window.location.href = '/login?redirect=' + encodeURIComponent(window.location.pathname);
    }
    throw new Error(msg);
  }
  if (resp.status === 204) return null;
  return resp.json();
}

export { saveTokens, clearTokens };
