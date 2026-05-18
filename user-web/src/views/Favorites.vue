<script setup>
import { onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import ItemCard from '../components/ItemCard.vue';
import { apiGet, apiPost, apiDelete } from '../api/client.js';
import { useUser } from '../composables/useUser.js';

const { userId, isLoggedIn } = useUser();
const router = useRouter();
const items = ref([]);
const status = ref('');
const loading = ref(true);

const loadFavorites = async () => {
  if (!userId.value) {
    items.value = [];
    loading.value = false;
    return;
  }
  loading.value = true;
  try {
    items.value = await apiGet(`/api/catalog/users/${userId.value}/favorites`);
  } catch (err) {
    status.value = err.message;
  } finally {
    loading.value = false;
  }
};

const removeItem = async (item) => {
  try {
    await apiDelete(`/api/catalog/users/${userId.value}/favorites/${item.id}`);
    items.value = items.value.filter(i => i.id !== item.id);
    if (window.$toast) window.$toast.success('已取消收藏');
  } catch (err) {
    status.value = err.message;
  }
};

const openItem = async (item) => {
  try {
    await apiPost('/api/behaviors/events', {
      userId: userId.value,
      itemId: item.id,
      action: 'click'
    });
  } catch (err) {
    status.value = err.message;
  }
  router.push({ name: 'item', params: { id: item.id } });
};

watch(userId, () => {
  loadFavorites();
});

onMounted(() => {
  loadFavorites();
});
</script>

<template>
  <section class="section">
    <div class="section-header">
      <div>
        <h3 class="section-title">我的收藏</h3>
        <div class="section-subtitle">收藏的商品会持续优化推荐结果</div>
      </div>
    </div>

    <div v-if="!isLoggedIn" class="login-callout">
      <div>
        <h4>登录后查看收藏</h4>
        <p>收藏会影响推荐结果。</p>
      </div>
      <RouterLink class="btn-primary" to="/login">去登录</RouterLink>
    </div>
    <div v-else-if="loading" class="loading-state">
      <div class="spinner"></div>
      <span>加载中...</span>
    </div>
    <div v-else-if="items.length === 0" class="empty-state">暂无收藏，去首页挑选喜欢的商品吧。</div>
    <div v-else class="card-grid">
      <ItemCard
        v-for="(item, index) in items"
        :key="item.id"
        :item="item"
        meta-label="平均评分"
        :meta-value="item.avgScore.toFixed(1)"
        :favorite="true"
        :actions-disabled="!isLoggedIn"
        :style="{ '--delay': `${index * 40}ms` }"
        @open="openItem(item)"
        @favorite="() => {}"
        @cart="() => {}"
      />
    </div>

    <div class="status-line" v-if="status">{{ status }}</div>
  </section>
</template>

<style scoped>
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px;
  color: #6b7280;
}

.spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #e5e7eb;
  border-top-color: #4f7cff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
