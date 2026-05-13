async function parseError(response) {
  try {
    const data = await response.json();
    return data.message || response.statusText;
  } catch (e) {
    return response.statusText;
  }
}

export async function apiGet(url) {
  const resp = await fetch(url);
  if (!resp.ok) {
    throw new Error(await parseError(resp));
  }
  return resp.json();
}

export async function apiPost(url, payload) {
  const resp = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload || {})
  });
  if (!resp.ok) {
    throw new Error(await parseError(resp));
  }
  return resp.json();
}
