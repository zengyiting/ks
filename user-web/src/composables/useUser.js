import { computed, ref, watch } from 'vue';
import { apiPost } from '../api/client.js';

const STORAGE_KEY = 'authUser';

const readStoredUser = () => {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) {
    localStorage.removeItem('userId');
    return null;
  }
  try {
    const parsed = JSON.parse(raw);
    if (parsed && Number.isFinite(parsed.id)) {
      console.debug('[useUser] Loaded stored user:', parsed);
      return parsed;
    }
    if (parsed && Number.isFinite(parsed.userId)) {
      console.debug('[useUser] Converting old format userId to id:', parsed);
      return { id: parsed.userId, username: parsed.username, phone: parsed.phone, email: parsed.email };
    }
  } catch (err) {
    console.error('[useUser] Failed to parse stored user:', err);
    localStorage.removeItem(STORAGE_KEY);
  }
  return null;
};

console.debug('[useUser] Module loaded, initial user:', readStoredUser());
const user = ref(readStoredUser());
console.debug('[useUser] user ref initialized:', user.value);

const userId = computed(() => {
  if (!user.value) return null;
  const id = user.value.id || user.value.userId;
  const result = Number.isFinite(id) ? id : null;
  console.debug('[useUser] userId computed:', result);
  return result;
});

const isLoggedIn = computed(() => {
  const id = userId.value;
  const result = Number.isFinite(id) && id > 0;
  console.debug('[useUser] isLoggedIn computed:', result, 'userId:', id);
  return result;
});

const currentUser = computed(() => {
  console.debug('[useUser] currentUser computed:', user.value);
  return user.value;
});

const saveUser = (payload) => {
  console.debug('[useUser] Saving user:', payload);
  const userData = {
    id: payload.id || payload.userId,
    username: payload.username,
    phone: payload.phone,
    email: payload.email
  };
  user.value = userData;
  localStorage.setItem(STORAGE_KEY, JSON.stringify(userData));
};

const loginWithPhone = async (phone, password) => {
  const data = await apiPost('/api/auth/login', { phone, password });
  console.debug('[useUser] Login response:', data);
  if (!data || !data.userId) {
    throw new Error('登录失败：未返回用户信息');
  }
  saveUser({ id: data.userId, username: data.username, phone: data.phone, email: data.email });
  console.debug('[useUser] After login - isLoggedIn:', isLoggedIn.value, 'userId:', userId.value);
  return data;
};

const loginWithEmail = async (email, password) => {
  const data = await apiPost('/api/auth/login-email', { email, password });
  console.debug('[useUser] Login response:', data);
  if (!data || !data.userId) {
    throw new Error('登录失败：未返回用户信息');
  }
  saveUser({ id: data.userId, username: data.username, phone: data.phone, email: data.email });
  console.debug('[useUser] After login - isLoggedIn:', isLoggedIn.value, 'userId:', userId.value);
  return data;
};

const loginWithUsername = async (username, password) => {
  const data = await apiPost('/api/auth/login-username', { username, password });
  console.debug('[useUser] Login response:', data);
  if (!data || !data.userId) {
    throw new Error('登录失败：未返回用户信息');
  }
  saveUser({ id: data.userId, username: data.username, phone: data.phone, email: data.email });
  console.debug('[useUser] After login - isLoggedIn:', isLoggedIn.value, 'userId:', userId.value);
  return data;
};

const logout = () => {
  console.debug('[useUser] Logging out');
  user.value = null;
  localStorage.removeItem(STORAGE_KEY);
};

watch(user, (newVal) => {
  console.debug('[useUser] User state changed:', newVal);
}, { deep: true });

export function useUser() {
  return { userId, currentUser, isLoggedIn, loginWithPhone, loginWithEmail, loginWithUsername, logout };
}
