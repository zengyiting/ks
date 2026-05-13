<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ItemCard from '../components/ItemCard.vue';
import RatingStars from '../components/RatingStars.vue';
import { apiGet, apiPost } from '../api/client.js';
import { useUser } from '../composables/useUser.js';

const route = useRoute();
const router = useRouter();
const { userId, isLoggedIn } = useUser();

const item = ref(null);
const related = ref([]);
const status = ref('');
const ratingValue = ref(0);
const favoriteSet = ref(new Set());
const cartSet = ref(new Set());

const reasonText = computed(() => route.query.reason || '');

const isFavorite = (id) => favoriteSet.value.has(id);
const isInCart = (id) => cartSet.value.has(id);

const ensureLogin = () => {
  if (isLoggedIn.value) return true;
  router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } });
  return false;
};

const recordEvent = async (action, itemId) => {
  if (!userId.value || !itemId) return;
  try {
    await apiPost('/api/behaviors/events', {
      userId: userId.value,
      itemId,
      action
    });
  } catch (err) {
    status.value = err.message;
  }
};

const loadFlags = async () => {
  if (!userId.value) {
    favoriteSet.value = new Set();
    cartSet.value = new Set();
    return;
  }
  try {
    const rows = await apiGet(`/api/catalog/users/${userId.value}/flags`);
    const fav = new Set();
    const cart = new Set();
    (rows || []).forEach((row) => {
      if (row.favorite) fav.add(row.itemId);
      if (row.inCart) cart.add(row.itemId);
    });
    favoriteSet.value = fav;
    cartSet.value = cart;
  } catch (err) {
    status.value = err.message;
  }
};

const loadItem = async () => {
  try {
    const data = await apiGet(`/api/catalog/items/${route.params.id}`);
    item.value = data;
    if (item.value) {
      await recordEvent('view', item.value.id);
    }
  } catch (err) {
    status.value = err.message;
  }
};

const loadRelated = async () => {
  if (!userId.value) {
    related.value = [];
    return;
  }
  try {
    const data = await apiGet(`/api/recommendations/${userId.value}?n=6&algo=hybrid`);
    related.value = (data || []).filter((row) => row.itemId !== Number(route.params.id));
  } catch (err) {
    status.value = err.message;
  }
};

const submitRating = async (value) => {
  if (!ensureLogin() || !item.value) return;
  ratingValue.value = value;
  try {
    await apiPost('/api/behaviors/ratings', {
      userId: userId.value,
      itemId: item.value.id,
      score: value
    });
    status.value = '评分已保存';
  } catch (err) {
    status.value = err.message;
  }
};

const favoriteItem = async () => {
  if (!ensureLogin() || !item.value) return;
  await recordEvent('favorite', item.value.id);
  await loadFlags();
};

const cartItem = async () => {
  if (!ensureLogin() || !item.value) return;
  await recordEvent('cart', item.value.id);
  await loadFlags();
};

const favoriteRelated = async (itemId) => {
  if (!ensureLogin()) return;
  await recordEvent('favorite', itemId);
  await loadFlags();
};

const cartRelated = async (itemId) => {
  if (!ensureLogin()) return;
  await recordEvent('cart', itemId);
  await loadFlags();
};

const openItem = async (rec) => {
  await recordEvent('click', rec.itemId);
  router.push({ name: 'item', params: { id: rec.itemId }, query: { reason: rec.reason || '' } });
};

watch(
  () => route.params.id,
  () => {
    loadItem();
    loadRelated();
  }
);

watch(userId, () => {
  loadFlags();
  loadRelated();
});

onMounted(() => {
  loadItem();
  loadFlags();
  loadRelated();
});
</script>

<template>
  <div v-if="!item" class="empty-state">商品加载中...</div>
  <div v-else class="detail">
    <section class="detail-card">
      <img class="detail-cover" :src="item.imageUrl || '/images/placeholder.svg'" :alt="item.name" />
      <h2>{{ item.name }}</h2>
      <div class="detail-meta">
        <span class="meta-pill">分类 {{ item.category || '未分类' }}</span>
        <span class="meta-pill">评分 {{ item.avgScore.toFixed(1) }}</span>
        <span class="meta-pill">人气 {{ item.ratingCount }}</span>
      </div>
      <p v-if="reasonText" class="item-reason">推荐理由：{{ reasonText }}</p>
      <div class="detail-actions">
        <button class="btn-ghost" :disabled="!isLoggedIn" @click="favoriteItem">
          {{ isFavorite(item.id) ? '已收藏' : '收藏' }}
        </button>
        <button class="btn-plant" :disabled="!isLoggedIn" @click="cartItem">
          {{ isInCart(item.id) ? '已加购' : '加购' }}
        </button>
      </div>
      <div>
        <div class="section-subtitle">你的评分</div>
        <RatingStars :model-value="ratingValue" :readonly="!isLoggedIn" @update:model-value="submitRating" />
        <div v-if="!isLoggedIn" class="login-callout compact">
          <span>登录后可收藏、加购和评分。</span>
          <RouterLink class="link-primary" to="/login">去登录</RouterLink>
        </div>
      </div>
      <div class="status-line" v-if="status">{{ status }}</div>
    </section>

    <aside class="side-panel">
      <div class="panel-card">
        <h3>再逛逛</h3>
        <div v-if="!isLoggedIn" class="empty-state">登录后可查看个性化推荐。</div>
        <div v-else-if="related.length === 0" class="empty-state">暂无推荐</div>
        <div class="card-grid">
          <ItemCard
            v-for="(rec, index) in related"
            :key="rec.itemId"
            :item="{ id: rec.itemId, name: rec.name, category: rec.category, imageUrl: rec.imageUrl }"
            :reason="rec.reason"
            meta-label="匹配度"
            :meta-value="rec.score.toFixed(2)"
            :favorite="isFavorite(rec.itemId)"
            :in-cart="isInCart(rec.itemId)"
            :actions-disabled="!isLoggedIn"
            :style="{ '--delay': `${index * 40}ms` }"
            @open="openItem(rec)"
            @favorite="favoriteRelated(rec.itemId)"
            @cart="cartRelated(rec.itemId)"
          />
        </div>
      </div>
    </aside>
  </div>
</template>
