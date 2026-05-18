import { computed, ref } from 'vue';
import { apiPost, apiGet, saveTokens, clearTokens } from '../api/client.js';

const STORAGE_KEY = 'authUser';

const readStoredUser = () => {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    if (parsed && Number.isFinite(parsed.id)) {
      return parsed;
    }
  } catch {
    localStorage.removeItem(STORAGE_KEY);
  }
  return null;
};

const user = ref(readStoredUser());

const userId = computed(() => {
  if (!user.value) return null;
  const id = user.value.id || user.value.userId;
  return Number.isFinite(id) ? id : null;
});

const isLoggedIn = computed(() => {
  const id = userId.value;
  return Number.isFinite(id) && id > 0;
});

const currentUser = computed(() => user.value);

const hasValidToken = () => {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return false;
  try {
    const parsed = JSON.parse(raw);
    if (!parsed.accessToken) return false;
    if (parsed.tokenExpiresAt && Date.now() > parsed.tokenExpiresAt) {
      return false;
    }
    return true;
  } catch {
    return false;
  }
};

const saveUser = (payload) => {
  const userData = {
    id: payload.id || payload.userId,
    username: payload.username,
    phone: payload.phone,
    email: payload.email,
    accessToken: payload.accessToken,
    refreshToken: payload.refreshToken,
    tokenExpiresAt: payload.expiresIn ? Date.now() + payload.expiresIn * 1000 : undefined
  };
  user.value = userData;
  saveTokens(payload);
};

const loginWithPhone = async (phone, password) => {
  const data = await apiPost('/api/auth/login', { phone, password });
  if (!data || !data.userId) {
    throw new Error('登录失败：未返回用户信息');
  }
  saveUser(data);
  return data;
};

const loginWithEmail = async (email, password) => {
  const data = await apiPost('/api/auth/login-email', { email, password });
  if (!data || !data.userId) {
    throw new Error('登录失败：未返回用户信息');
  }
  saveUser(data);
  return data;
};

const loginWithUsername = async (username, password) => {
  const data = await apiPost('/api/auth/login-username', { username, password });
  if (!data || !data.userId) {
    throw new Error('登录失败：未返回用户信息');
  }
  saveUser(data);
  return data;
};

const loginWithSms = async (phone, code) => {
  const data = await apiPost('/api/auth/login/sms', { phone, code });
  if (!data || !data.userId) {
    throw new Error('登录失败：未返回用户信息');
  }
  saveUser(data);
  return data;
};

const sendSmsCode = async (phone) => {
  return apiPost('/api/auth/send-sms-code', { phone });
};

const sendEmailCode = async (email) => {
  return apiPost('/api/auth/send-email-code', { email });
};

const registerByPhone = async (phone, username, password) => {
  const data = await apiPost('/api/auth/register', { phone, username, password });
  if (!data || !data.userId) {
    throw new Error('注册失败：未返回用户信息');
  }
  saveUser(data);
  return data;
};

const registerByEmail = async (email, code, username, password) => {
  const data = await apiPost('/api/auth/register-email', { email, code, username, password });
  if (!data || !data.userId) {
    throw new Error('注册失败：未返回用户信息');
  }
  saveUser(data);
  return data;
};

const refreshToken = async () => {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) throw new Error('无刷新令牌');
  const parsed = JSON.parse(raw);
  if (!parsed.refreshToken) throw new Error('无刷新令牌');
  const data = await apiPost('/api/auth/refresh', { refreshToken: parsed.refreshToken });
  saveUser(data);
  return data;
};

const logout = async () => {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (raw) {
    try {
      const parsed = JSON.parse(raw);
      if (parsed.accessToken) {
        await fetch('/api/auth/logout', {
          method: 'POST',
          headers: { Authorization: `Bearer ${parsed.accessToken}` }
        });
      }
    } catch {
      // ignore
    }
  }
  user.value = null;
  clearTokens();
};

const fetchCurrentUser = async () => {
  if (!hasValidToken()) return null;
  try {
    const data = await apiGet('/api/auth/me');
    if (data) {
      user.value = {
        ...user.value,
        id: data.id,
        username: data.username,
        phone: data.phone,
        email: data.email
      };
    }
    return data;
  } catch {
    return null;
  }
};

export function useUser() {
  return {
    userId,
    currentUser,
    isLoggedIn,
    hasValidToken,
    loginWithPhone,
    loginWithEmail,
    loginWithUsername,
    loginWithSms,
    sendSmsCode,
    sendEmailCode,
    registerByPhone,
    registerByEmail,
    refreshToken,
    logout,
    fetchCurrentUser
  };
}
