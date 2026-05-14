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

const user = ref(readStoredUser());
const userId = computed(() => {
  if (!user.value) return null;
  const id = user.value.id || user.value.userId;
  return Number.isFinite(id) ? id : null;
});
const isLoggedIn = computed(() => {
  const id = userId.value;
  const result = Number.isFinite(id) && id > 0;
  console.debug('[useUser] isLoggedIn:', result, 'userId:', id);
  return result;
});
const currentUser = computed(() => user.value);

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
  saveUser({ id: data.userId, username: data.username, phone: data.phone, email: data.email });
  return data;
};

const loginWithEmail = async (email, password) => {
  const data = await apiPost('/api/auth/login-email', { email, password });
  console.debug('[useUser] Login response:', data);
  saveUser({ id: data.userId, username: data.username, phone: data.phone, email: data.email });
  return data;
};

const loginWithUsername = async (username, password) => {
  const data = await apiPost('/api/auth/login-username', { username, password });
  console.debug('[useUser] Login response:', data);
  saveUser({ id: data.userId, username: data.username, phone: data.phone, email: data.email });
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
