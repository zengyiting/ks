import { createRouter, createWebHistory } from 'vue-router';
import Home from '../views/Home.vue';
import ItemDetail from '../views/ItemDetail.vue';
import Favorites from '../views/Favorites.vue';
import Cart from '../views/Cart.vue';
import Login from '../views/Login.vue';
import Register from '../views/Register.vue';
import Search from '../views/Search.vue';
import Profile from '../views/Profile.vue';
import Ranking from '../views/Ranking.vue';

const routes = [
  { path: '/', name: 'home', component: Home },
  { path: '/login', name: 'login', component: Login },
  { path: '/register', name: 'register', component: Register },
  { path: '/item/:id', name: 'item', component: ItemDetail },
  { path: '/favorites', name: 'favorites', component: Favorites, meta: { requiresAuth: true } },
  { path: '/cart', name: 'cart', component: Cart, meta: { requiresAuth: true } },
  { path: '/search', name: 'search', component: Search },
  { path: '/profile', name: 'profile', component: Profile, meta: { requiresAuth: true } },
  { path: '/ranking', name: 'ranking', component: Ranking }
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 };
  }
});

const getStoredUser = () => {
  const raw = localStorage.getItem('authUser');
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    if (!parsed.accessToken) return null;
    if (parsed.tokenExpiresAt && Date.now() > parsed.tokenExpiresAt) return null;
    if (!Number.isFinite(parsed.id) || parsed.id <= 0) return null;
    return parsed;
  } catch {
    return null;
  }
};

const validateToken = async () => {
  const user = getStoredUser();
  if (!user) return false;
  
  try {
    const resp = await fetch('/api/users/me', {
      headers: { Authorization: `Bearer ${user.accessToken}` }
    });
    if (!resp.ok) {
      localStorage.removeItem('authUser');
      return false;
    }
    return true;
  } catch {
    return false;
  }
};

router.beforeEach(async (to, from, next) => {
  if (to.meta.requiresAuth) {
    const user = getStoredUser();
    if (!user) {
      next({ name: 'login', query: { redirect: to.fullPath } });
      return;
    }
    
    const isValid = await validateToken();
    if (!isValid) {
      next({ name: 'login', query: { redirect: to.fullPath } });
      return;
    }
  } else if ((to.name === 'login' || to.name === 'register') && getStoredUser()) {
    next({ name: 'home' });
    return;
  }
  next();
});

export default router;
