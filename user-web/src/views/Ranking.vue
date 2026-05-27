<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import ItemCard from '../components/ItemCard.vue';
import { apiGet, apiPost } from '../api/client.js';
import { useUser } from '../composables/useUser.js';

const router = useRouter();
const { userId, isLoggedIn } = useUser();

const rankings = ref([]);
const loading = ref(true);
const status = ref('');

const openItem = (item) => {
  router.push({ name: 'item', params: { id: item.id } });
};

const favoriteItem = async (item) => {
  if (!isLoggedIn.value) {
    router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } });
    return;
  }
  try {
    await apiPost('/api/behaviors/events', {
      userId: userId.value,
      itemId: item.id,
      action: 'favorite'
    });
    status.value = '已收藏';
  } catch (err) {
    status.value = err.message;
  }
};

const cartItem = async (item) => {
  if (!isLoggedIn.value) {
    router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } });
    return;
  }
  try {
    await apiPost('/api/behaviors/events', {
      userId: userId.value,
      itemId: item.id,
      action: 'cart'
    });
    status.value = '已加购';
  } catch (err) {
    status.value = err.message;
  }
};

const loadRanking = async () => {
  loading.value = true;
  try {
    const data = await apiGet('/api/ranking/favorites?n=50');
    rankings.value = (data || []).map((item, index) => ({
      ...item,
      rank: index + 1
    }));
  } catch (err) {
    status.value = '排行榜加载失败: ' + err.message;
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadRanking();
});
</script>

<template>
  <div class="ranking-page">
    <section class="ranking-hero">
      <div>
        <h2>🏆 收藏排行榜</h2>
        <p>基于用户收藏数据实时生成，反映最受欢迎的商品</p>
      </div>
      <div class="ranking-badge">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
        </svg>
        <span>Redis ZSet 实时排行</span>
      </div>
    </section>

    <div v-if="loading" class="loading-state">
      <div class="spinner"></div>
      <span>排行榜生成中...</span>
    </div>

    <div v-else-if="rankings.length === 0" class="empty-state">
      暂无排行数据，快去收藏你喜欢的商品吧！
    </div>

    <div v-else class="ranking-list">
      <div
        v-for="item in rankings"
        :key="item.id"
        class="ranking-item"
        :class="{ 'top-three': item.rank <= 3 }"
        @click="openItem(item)"
      >
        <div class="rank-number" :class="'rank-' + item.rank">
          <span v-if="item.rank <= 3" class="rank-medal">{{ item.rank === 1 ? '🥇' : item.rank === 2 ? '🥈' : '🥉' }}</span>
          <span v-else class="rank-text">{{ item.rank }}</span>
        </div>

        <div class="item-image">
          <img :src="item.imageUrl || 'https://picsum.photos/seed/' + item.id + '/100/100'" :alt="item.name" />
        </div>

        <div class="item-info">
          <h3 class="item-name">{{ item.name }}</h3>
          <div class="item-meta">
            <span class="item-category">{{ item.category }}</span>
            <span class="item-price">¥{{ item.price }}</span>
          </div>
        </div>

        <div class="item-stats">
          <span class="favorite-count">❤️ {{ item.favoriteCount }}</span>
        </div>

        <div class="item-actions">
          <button class="btn-action" @click.stop="favoriteItem(item)" title="收藏">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
            </svg>
          </button>
        </div>
      </div>
    </div>

    <div class="status-line" v-if="status">{{ status }}</div>
  </div>
</template>

<style scoped>
.ranking-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px 20px;
}

.ranking-hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 32px;
  padding-bottom: 20px;
  border-bottom: 2px solid var(--line-soft);
}

.ranking-hero h2 {
  font-size: 28px;
  margin: 0 0 6px;
  color: var(--primary);
}

.ranking-hero p {
  color: var(--muted);
  font-size: 14px;
  margin: 0;
}

.ranking-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(79, 124, 255, 0.08);
  color: var(--accent);
  font-size: 12px;
  font-weight: 600;
}

.ranking-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ranking-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  background: var(--bg-secondary);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid transparent;
}

.ranking-item:hover {
  transform: translateX(4px);
  border-color: var(--accent);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.ranking-item.top-three {
  background: linear-gradient(135deg, var(--bg-secondary) 0%, rgba(79, 124, 255, 0.08) 100%);
}

.ranking-item.top-three:first-child {
  background: linear-gradient(135deg, var(--bg-secondary) 0%, rgba(255, 215, 0, 0.15) 100%);
  border-color: rgba(255, 215, 0, 0.3);
}

.rank-number {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 56px;
  height: 56px;
  font-size: 24px;
  font-weight: 700;
  color: var(--muted);
  background: var(--bg-primary);
  border-radius: 12px;
}

.rank-number.rank-1 {
  background: linear-gradient(135deg, #ffd700, #ffb347);
  color: #7d5800;
  font-size: 28px;
  box-shadow: 0 4px 12px rgba(255, 215, 0, 0.3);
}

.rank-number.rank-2 {
  background: linear-gradient(135deg, #c0c0c0, #a8a8a8);
  color: #555;
  font-size: 26px;
  box-shadow: 0 4px 12px rgba(192, 192, 192, 0.3);
}

.rank-number.rank-3 {
  background: linear-gradient(135deg, #cd7f32, #b8720e);
  color: #fff;
  font-size: 26px;
  box-shadow: 0 4px 12px rgba(205, 127, 50, 0.3);
}

.rank-medal {
  font-size: 32px;
}

.rank-text {
  font-size: 20px;
  font-weight: 800;
}

.item-image {
  width: 72px;
  height: 72px;
  border-radius: 8px;
  overflow: hidden;
  flex-shrink: 0;
}

.item-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-meta {
  display: flex;
  gap: 12px;
  font-size: 13px;
  color: var(--muted);
}

.item-category {
  padding: 2px 8px;
  background: var(--bg-primary);
  border-radius: 4px;
}

.item-price {
  color: var(--accent);
  font-weight: 600;
}

.item-stats {
  text-align: center;
  min-width: 80px;
}

.favorite-count {
  font-size: 14px;
  font-weight: 600;
  color: #e74c3c;
  white-space: nowrap;
}

.item-actions {
  display: flex;
  gap: 8px;
}

.btn-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: none;
  background: var(--bg-primary);
  border-radius: 8px;
  color: var(--muted);
  cursor: pointer;
  transition: all 0.2s;
}

.btn-action:hover {
  background: var(--accent);
  color: white;
}

.status-line {
  text-align: center;
  margin-top: 16px;
  font-size: 13px;
  color: var(--accent);
}

@media (max-width: 768px) {
  .ranking-page {
    padding: 16px 12px;
  }

  .ranking-hero {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .ranking-item {
    padding: 12px;
    gap: 12px;
  }

  .rank-number {
    min-width: 44px;
    height: 44px;
    font-size: 18px;
  }

  .rank-number.rank-1 {
    font-size: 22px;
  }

  .rank-number.rank-2,
  .rank-number.rank-3 {
    font-size: 20px;
  }

  .item-image {
    width: 56px;
    height: 56px;
  }

  .item-name {
    font-size: 14px;
  }

  .item-stats {
    display: none;
  }

  .item-actions {
    display: none;
  }
}
</style>
