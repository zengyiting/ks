<script setup>
import TopNav from './components/TopNav.vue';
</script>

<template>
  <div class="app">
    <div class="app-shell">
      <TopNav />
      <main class="page-content">
        <router-view />
      </main>
      <footer class="app-footer">
        <span>轻选集推荐实验室 · Spring Boot + Vue3</span>
      </footer>
    </div>
  </div>
</template>
