<script setup>
import { onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ItemCard from '../components/ItemCard.vue';
import { apiGet, apiPost } from '../api/client.js';
import { useUser } from '../composables/useUser.js';

const route = useRoute();
const router = useRouter();
const { userId, isLoggedIn } = useUser();

const searchQuery = ref(route.query.q || '');
const items = ref([]);
const loading = ref(false);
const hasSearched = ref(false);

const performSearch = async (keyword) => {
  if (!keyword || !keyword.trim()) return;
  loading.value = true;
  hasSearched.value = true;
  try {
    const data = await apiGet(`/api/catalog/items?keyword=${encodeURIComponent(keyword.trim())}&limit=100`);
    items.value = data || [];
  } catch (err) {
    console.error('搜索失败:', err);
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  if (searchQuery.value.trim()) {
    router.push({ name: 'search', query: { q: searchQuery.value.trim() } });
  }
};

const openItem = async (item) => {
  try {
    await apiPost('/api/behaviors/events', {
      userId: userId.value,
      itemId: item.id,
      action: 'click'
    });
  } catch {
    // ignore
  }
  router.push({ name: 'item', params: { id: item.id } });
};

watch(() => route.query.q, (newQ) => {
  if (newQ) {
    searchQuery.value = newQ;
    performSearch(newQ);
  }
});

onMounted(() => {
  if (route.query.q) {
    searchQuery.value = route.query.q;
    performSearch(route.query.q);
  }
});
</script>

<template>
  <section class="search-page">
    <div class="search-header">
      <h2 class="section-title">搜索商品</h2>
      <div class="search-bar">
        <input
          v-model="searchQuery"
          class="search-input"
          type="text"
          placeholder="输入商品名称或分类关键词..."
          @keyup.enter="handleSearch"
        />
        <button class="search-btn" :disabled="!searchQuery.trim()" @click="handleSearch">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"/>
            <path d="m21 21-4.35-4.35"/>
          </svg>
          搜索
        </button>
      </div>
    </div>

    <div v-if="loading" class="loading-state">
      <div class="spinner"></div>
      <span>搜索中...</span>
    </div>

    <div v-else-if="hasSearched && items.length === 0" class="empty-state">
      未找到与 "{{ searchQuery }}" 相关的商品，换个关键词试试。
    </div>

    <div v-else-if="items.length > 0" class="search-results">
      <div class="result-count">找到 {{ items.length }} 件相关商品</div>
      <div class="card-grid">
        <ItemCard
          v-for="(item, index) in items"
          :key="item.id"
          :item="item"
          meta-label="平均评分"
          :meta-value="item.avgScore.toFixed(1)"
          :favorite="false"
          :in-cart="false"
          :actions-disabled="!isLoggedIn"
          :style="{ '--delay': `${index * 40}ms` }"
          @open="openItem(item)"
          @favorite="() => {}"
          @cart="() => {}"
        />
      </div>
    </div>

    <div v-else class="search-tip">
      <p>输入关键词搜索商品名称或分类</p>
    </div>
  </section>
</template>

<style scoped>
.search-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
  animation: riseIn 0.6s ease both;
}

.search-header {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-bar {
  display: flex;
  gap: 10px;
  max-width: 560px;
}

.search-input {
  flex: 1;
  min-height: 48px;
  padding: 12px 18px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.85);
  color: var(--text);
  font-size: 15px;
  font-family: inherit;
  transition: var(--transition);
}

.search-input:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 4px rgba(79, 124, 255, 0.12);
}

.search-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 100px;
  padding: 12px 20px;
  border-radius: 999px;
  border: 1px solid var(--accent);
  background: var(--accent);
  color: white;
  font-size: 14px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: var(--transition);
}

.search-btn:hover:not(:disabled) {
  filter: brightness(1.08);
  transform: translateY(-1px);
}

.search-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.search-results {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.result-count {
  color: var(--muted);
  font-size: 14px;
}

.search-tip {
  padding: 40px;
  text-align: center;
  color: var(--muted);
  border-radius: 16px;
  border: 1px dashed var(--line);
  background: rgba(255, 255, 255, 0.4);
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 60px;
  color: var(--muted);
}

.spinner {
  width: 36px;
  height: 36px;
  border: 3px solid var(--line);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
