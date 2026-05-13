import { computed, ref } from 'vue';
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
      return parsed;
    }
  } catch (err) {
    return null;
  }
  return null;
};

const user = ref(readStoredUser());
const userId = computed(() => (user.value && Number.isFinite(user.value.id) ? user.value.id : null));
const isLoggedIn = computed(() => Number.isFinite(userId.value) && userId.value > 0);
const currentUser = computed(() => user.value);

const saveUser = (payload) => {
  user.value = payload;
  localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
};

const loginWithPhone = async (phone, password) => {
  const data = await apiPost('/api/auth/login', { phone, password });
  saveUser({ id: data.userId, username: data.username, phone: data.phone });
  return data;
};

const loginWithEmail = async (email, password) => {
  const data = await apiPost('/api/auth/login-email', { email, password });
  saveUser({ id: data.userId, username: data.username, email: data.email });
  return data;
};

const loginWithUsername = async (username, password) => {
  const data = await apiPost('/api/auth/login-username', { username, password });
  saveUser({ id: data.userId, username: data.username });
  return data;
};

const logout = () => {
  user.value = null;
  localStorage.removeItem(STORAGE_KEY);
};

export function useUser() {
  return { userId, currentUser, isLoggedIn, loginWithPhone, loginWithEmail, loginWithUsername, logout };
}
