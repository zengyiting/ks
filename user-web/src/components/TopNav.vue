<script setup>
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { useUser } from '../composables/useUser.js';

const router = useRouter();
const { currentUser, isLoggedIn, userId, logout } = useUser();

const maskPhone = (phone) => {
  if (!phone) return '';
  if (phone.length <= 4) return phone;
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`;
};

const displayName = computed(() => {
  if (!isLoggedIn.value) return '未登录';
  return currentUser.value?.username || maskPhone(currentUser.value?.phone) || `用户 ${userId.value}`;
});

const displayMeta = computed(() => {
  if (!isLoggedIn.value) return '浏览无需登录';
  return currentUser.value?.phone ? `手机号 ${maskPhone(currentUser.value.phone)}` : '已登录';
});

const handleLogout = () => {
  logout();
  router.push('/');
};
</script>

<template>
  <header class="top-nav">
    <div class="brand">
      <div class="brand-mark"></div>
      <div>
        <h1 class="brand-title">轻选集</h1>
        <div class="brand-subtitle">Fresh Picks · Light Commerce</div>
      </div>
    </div>

    <nav class="nav-links">
      <RouterLink to="/">首页</RouterLink>
      <RouterLink to="/favorites">收藏</RouterLink>
      <RouterLink to="/cart">购物袋</RouterLink>
    </nav>

    <div class="auth-panel">
      <div>
        <div class="auth-label">当前状态</div>
        <div class="auth-name">{{ displayName }}</div>
        <div class="auth-meta">{{ displayMeta }}</div>
      </div>
      <div class="auth-actions">
        <RouterLink v-if="!isLoggedIn" class="btn-ghost" to="/register">注册</RouterLink>
        <RouterLink v-if="!isLoggedIn" class="btn-primary" to="/login">登录</RouterLink>
        <RouterLink v-else class="btn-ghost" to="/login">切换</RouterLink>
        <button v-if="isLoggedIn" class="btn-ghost" type="button" @click="handleLogout">退出</button>
      </div>
    </div>
  </header>
</template>
