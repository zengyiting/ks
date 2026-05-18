<script setup>
import TopNav from './components/TopNav.vue';
import Toast from './components/Toast.vue';
</script>

<template>
  <div class="app">
    <div class="app-shell">
      <TopNav />
      <main class="page-content">
        <router-view />
      </main>
      <footer class="app-footer">
        <span>基于协同过滤的商品推荐系统 · Spring Boot + Vue3</span>
      </footer>
    </div>
    <Toast />
  </div>
</template>
