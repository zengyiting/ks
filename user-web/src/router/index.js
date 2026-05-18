import { createRouter, createWebHistory } from 'vue-router';
import Home from '../views/Home.vue';
import ItemDetail from '../views/ItemDetail.vue';
import Favorites from '../views/Favorites.vue';
import Cart from '../views/Cart.vue';
import Login from '../views/Login.vue';
import Register from '../views/Register.vue';
import Search from '../views/Search.vue';
import Profile from '../views/Profile.vue';

const routes = [
  { path: '/', name: 'home', component: Home },
  { path: '/login', name: 'login', component: Login },
  { path: '/register', name: 'register', component: Register },
  { path: '/item/:id', name: 'item', component: ItemDetail },
  { path: '/favorites', name: 'favorites', component: Favorites, meta: { requiresAuth: true } },
  { path: '/cart', name: 'cart', component: Cart, meta: { requiresAuth: true } },
  { path: '/search', name: 'search', component: Search },
  { path: '/profile', name: 'profile', component: Profile, meta: { requiresAuth: true } }
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 };
  }
});

const isAuth = () => {
  const raw = localStorage.getItem('authUser');
  if (!raw) return false;
  try {
    const parsed = JSON.parse(raw);
    if (!parsed.accessToken) return false;
    if (parsed.tokenExpiresAt && Date.now() > parsed.tokenExpiresAt) return false;
    return Number.isFinite(parsed.id) && parsed.id > 0;
  } catch {
    return false;
  }
};

router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth && !isAuth()) {
    next({ name: 'login', query: { redirect: to.fullPath } });
  } else if ((to.name === 'login' || to.name === 'register') && isAuth()) {
    next({ name: 'home' });
  } else {
    next();
  }
});

export default router;
