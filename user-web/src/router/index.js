import { createRouter, createWebHistory } from 'vue-router';
import Home from '../views/Home.vue';
import ItemDetail from '../views/ItemDetail.vue';
import Favorites from '../views/Favorites.vue';
import Cart from '../views/Cart.vue';
import Login from '../views/Login.vue';
import Register from '../views/Register.vue';

const routes = [
  { path: '/', name: 'home', component: Home },
  { path: '/login', name: 'login', component: Login },
  { path: '/register', name: 'register', component: Register },
  { path: '/item/:id', name: 'item', component: ItemDetail },
  { path: '/favorites', name: 'favorites', component: Favorites },
  { path: '/cart', name: 'cart', component: Cart }
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 };
  }
});

export default router;
