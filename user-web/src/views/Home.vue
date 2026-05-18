<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import ItemCard from '../components/ItemCard.vue';
import { apiGet, apiPost } from '../api/client.js';
import { useUser } from '../composables/useUser.js';

const router = useRouter();
const { userId, isLoggedIn, currentUser } = useUser();

console.debug('[Home] Component mounted, isLoggedIn:', isLoggedIn.value, 'userId:', userId.value);

const RECOMMEND_INITIAL_BATCH = 20;
const RECOMMEND_LOAD_BATCH = 30;
const RECOMMEND_MAX_ITEMS = 150;
const EXPLORE_INITIAL_BATCH = 24;
const EXPLORE_LOAD_BATCH = 40;
const EXPLORE_MAX_ITEMS = 260;

const algorithm = ref('hybrid');
const recommendations = ref([]);
const exploreItems = ref([]);
const favorites = ref([]);
const cartItems = ref([]);
const loading = ref(false);
const status = ref('');

const favoriteSet = ref(new Set());
const cartSet = ref(new Set());
const recommendationVisibleCount = ref(RECOMMEND_INITIAL_BATCH);
const exploreVisibleCount = ref(EXPLORE_INITIAL_BATCH);
const recommendationScroller = ref(null);
const exploreScroller = ref(null);
const sidebarImageErrors = ref(new Set());
const exploreLoaded = ref(false);

const getSafeImage = (item) => {
  if (sidebarImageErrors.value.has(item.id)) return '/images/placeholder.svg';
  if (item.imageUrl) {
    if (item.imageUrl.startsWith('/images/') || item.imageUrl.startsWith('http')) {
      return item.imageUrl;
    }
    return `https://picsum.photos/seed/${item.id}/100/100`;
  }
  return `https://picsum.photos/seed/${item.id}/100/100`;
};

const handleSidebarImageError = (itemId) => {
  sidebarImageErrors.value.add(itemId);
};

const maskPhone = (phone) => {
  if (!phone) return '';
  if (phone.length <= 4) return phone;
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`;
};

const greeting = computed(() => {
  if (!currentUser.value) return '欢迎来到轻选集';
  const name = currentUser.value.username || maskPhone(currentUser.value.phone) || `用户 ${userId.value}`;
  return `${name}，欢迎回来`;
});

const heroTip = computed(() => {
  if (!isLoggedIn.value) {
    return {
      title: '轻选提示',
      content: '登录后开启个性化推荐与评分，让算法更贴合你的节奏。'
    };
  }
  return {
    title: '推荐已开启',
    content: '你的个性化推荐已激活，系统将根据你的偏好持续优化推荐结果。'
  };
});

const recommendationTotal = computed(() => Math.min(recommendations.value.length, RECOMMEND_MAX_ITEMS));
const exploreTotal = computed(() => Math.min(exploreItems.value.length, EXPLORE_MAX_ITEMS));

const visibleRecommendations = computed(() =>
  recommendations.value.slice(0, Math.min(recommendationVisibleCount.value, recommendationTotal.value))
);
const visibleExploreItems = computed(() =>
  exploreItems.value.slice(0, Math.min(exploreVisibleCount.value, exploreTotal.value))
);

const canLoadMoreRecommendations = computed(() => visibleRecommendations.value.length < recommendationTotal.value);
const canLoadMoreExplore = computed(() => visibleExploreItems.value.length < exploreTotal.value);

const isFavorite = (id) => favoriteSet.value.has(id);
const isInCart = (id) => cartSet.value.has(id);

const ensureLogin = () => {
  if (isLoggedIn.value) return true;
  router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } });
  return false;
};

const resetScroller = async (target) => {
  await nextTick();
  if (target?.value) {
    target.value.scrollTop = 0;
  }
};

const loadMoreRecommendations = () => {
  if (!canLoadMoreRecommendations.value) return;
  recommendationVisibleCount.value = Math.min(
    recommendationVisibleCount.value + RECOMMEND_LOAD_BATCH,
    recommendationTotal.value
  );
};

const loadMoreExplore = () => {
  if (!canLoadMoreExplore.value) return;
  exploreVisibleCount.value = Math.min(exploreVisibleCount.value + EXPLORE_LOAD_BATCH, exploreTotal.value);
};

const onRecommendationScroll = () => {
  const el = recommendationScroller.value;
  if (!el || !canLoadMoreRecommendations.value) return;
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 120) {
    loadMoreRecommendations();
  }
};

const onExploreScroll = () => {
  const el = exploreScroller.value;
  if (!el || !canLoadMoreExplore.value) return;
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 120) {
    loadMoreExplore();
  }
};

const applyFlags = (rows) => {
  const fav = new Set();
  const cart = new Set();
  (rows || []).forEach((row) => {
    if (row.favorite) fav.add(row.itemId);
    if (row.inCart) cart.add(row.itemId);
  });
  favoriteSet.value = fav;
  cartSet.value = cart;
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

const loadRecommendations = async () => {
  if (!userId.value) {
    recommendations.value = [];
    recommendationVisibleCount.value = RECOMMEND_INITIAL_BATCH;
    loading.value = false;
    return;
  }
  loading.value = true;
  try {
    const data = await apiGet(`/api/recommendations/${userId.value}?n=${RECOMMEND_MAX_ITEMS}&algo=${algorithm.value}`);
    recommendations.value = (data || []).slice(0, RECOMMEND_MAX_ITEMS);
    recommendationVisibleCount.value = RECOMMEND_INITIAL_BATCH;
  } catch (err) {
    status.value = err.message;
  } finally {
    loading.value = false;
  }
};

const loadExplore = async () => {
  try {
    const data = await apiGet(`/api/catalog/items?limit=${EXPLORE_MAX_ITEMS}`);
    exploreItems.value = (data || []).slice(0, EXPLORE_MAX_ITEMS);
    exploreVisibleCount.value = EXPLORE_INITIAL_BATCH;
    exploreLoaded.value = true;
  } catch (err) {
    status.value = err.message;
  }
};

const loadFlags = async () => {
  if (!userId.value) return;
  try {
    const data = await apiGet(`/api/catalog/users/${userId.value}/flags`);
    applyFlags(data);
  } catch (err) {
    status.value = err.message;
  }
};

const loadFavorites = async () => {
  if (!userId.value) return;
  try {
    favorites.value = await apiGet(`/api/catalog/users/${userId.value}/favorites`);
  } catch (err) {
    status.value = err.message;
  }
};

const loadCart = async () => {
  if (!userId.value) return;
  try {
    cartItems.value = await apiGet(`/api/catalog/users/${userId.value}/cart`);
  } catch (err) {
    status.value = err.message;
  }
};

const openItem = async (item, reason) => {
  await recordEvent('click', item.id);
  router.push({
    name: 'item',
    params: { id: item.id },
    query: reason ? { reason } : {}
  });
};

const favoriteItem = async (item) => {
  if (!ensureLogin()) return;
  await recordEvent('favorite', item.id);
  await loadFlags();
  await loadFavorites();
};

const cartItem = async (item) => {
  if (!ensureLogin()) return;
  await recordEvent('cart', item.id);
  await loadFlags();
  await loadCart();
};

const clearUserData = () => {
  recommendations.value = [];
  favorites.value = [];
  cartItems.value = [];
  favoriteSet.value = new Set();
  cartSet.value = new Set();
  recommendationVisibleCount.value = RECOMMEND_INITIAL_BATCH;
  exploreVisibleCount.value = EXPLORE_INITIAL_BATCH;
  loading.value = false;
};

const loadUserData = async () => {
  await Promise.all([loadRecommendations(), loadFlags(), loadFavorites(), loadCart()]);
};

const loadAll = async () => {
  await loadExplore();
  if (userId.value) {
    await loadUserData();
  } else {
    clearUserData();
  }
  await resetScroller(exploreScroller);
};

watch(userId, async () => {
  if (!userId.value) {
    clearUserData();
    await resetScroller(recommendationScroller);
    await resetScroller(exploreScroller);
    return;
  }
  await loadUserData();
  await resetScroller(recommendationScroller);
});

watch(algorithm, async () => {
  if (userId.value) {
    await loadRecommendations();
    await resetScroller(recommendationScroller);
  }
});

onMounted(async () => {
  await loadAll();
});
</script>

<template>
  <div class="home">
    <section class="hero">
      <div>
        <h2>{{ greeting }}</h2>
        <p>简约、干净、轻松的选品体验。浏览无需登录，收藏与加购将沉淀你的偏好。</p>
      </div>
      <div class="hero-card">
        <h3>{{ heroTip.title }}</h3>
        <p>{{ heroTip.content }}</p>
        <div class="status-line" v-if="status">{{ status }}</div>
      </div>
    </section>

    <div class="layout">
      <div class="section home-main-section">
        <section class="feed-block">
          <div class="section-header">
            <div>
              <h3 class="section-title">轻选集推荐</h3>
              <div class="section-subtitle">Strong Relevance · 协同过滤优先高相似结果</div>
            </div>
            <div class="selector">
              <span>算法</span>
              <select v-model="algorithm" :disabled="!isLoggedIn">
                <option value="user">User-Based</option>
                <option value="item">Item-Based</option>
                <option value="behavior">Behavior-Based</option>
                <option value="hybrid">Hybrid</option>
              </select>
            </div>
          </div>

          <div class="feed-scroll feed-scroll-recommend" ref="recommendationScroller" @scroll.passive="onRecommendationScroll">
            <div v-if="!isLoggedIn" class="empty-state">
              登录后查看个性化推荐。
              <RouterLink class="link-primary" to="/login">立即登录</RouterLink>
            </div>
            <div v-else-if="loading" class="empty-state">推荐流正在生成...</div>
            <template v-else>
              <div v-if="visibleRecommendations.length === 0" class="empty-state">暂无推荐</div>
              <div v-else class="card-grid">
                <ItemCard
                  v-for="(rec, index) in visibleRecommendations"
                  :key="rec.itemId"
                  :item="{ id: rec.itemId, name: rec.name, category: rec.category, imageUrl: rec.imageUrl }"
                  :reason="rec.reason"
                  meta-label="匹配度"
                  :meta-value="rec.score.toFixed(2)"
                  :favorite="isFavorite(rec.itemId)"
                  :in-cart="isInCart(rec.itemId)"
                  :actions-disabled="!isLoggedIn"
                  :style="{ '--delay': `${index * 60}ms` }"
                  @open="openItem({ id: rec.itemId }, rec.reason)"
                  @favorite="favoriteItem({ id: rec.itemId })"
                  @cart="cartItem({ id: rec.itemId })"
                />
              </div>
              <div v-if="canLoadMoreRecommendations" class="load-more-tip load-more-action" @click="loadMoreRecommendations">
                继续下滑或点击加载更多推荐
              </div>
              <div v-else class="load-more-tip">暂无推荐</div>
            </template>
          </div>
        </section>

        <section class="feed-block">
          <div class="section-header" style="margin-top: 8px;">
            <div>
              <h3 class="section-title">探索更多</h3>
              <div class="section-subtitle">Weak Relevance · 扩展兴趣与多样性发现</div>
            </div>
          </div>

          <div class="feed-scroll feed-scroll-explore" ref="exploreScroller" @scroll.passive="onExploreScroll">
            <div v-if="visibleExploreItems.length === 0 && !exploreLoaded" class="loading-state">
              <div class="spinner"></div>
              <span>探索商品加载中...</span>
            </div>
            <div v-else-if="visibleExploreItems.length === 0" class="empty-state">暂无商品</div>
            <div v-else class="card-grid">
              <ItemCard
                v-for="(item, index) in visibleExploreItems"
                :key="item.id"
                :item="item"
                meta-label="平均评分"
                :meta-value="item.avgScore.toFixed(1)"
                :favorite="isFavorite(item.id)"
                :in-cart="isInCart(item.id)"
                :actions-disabled="!isLoggedIn"
                :style="{ '--delay': `${index * 40}ms` }"
                @open="openItem(item)"
                @favorite="favoriteItem(item)"
                @cart="cartItem(item)"
              />
            </div>
            <div v-if="canLoadMoreExplore" class="load-more-tip load-more-action" @click="loadMoreExplore">
              继续下滑或点击加载更多探索
            </div>
            <div v-else class="load-more-tip">暂无推荐</div>
          </div>
        </section>
      </div>

      <aside class="side-panel">
        <div class="panel-card">
          <h3>我的收藏</h3>
          <div v-if="!isLoggedIn" class="empty-state">
            登录后查看收藏。
            <RouterLink class="link-primary" to="/login">立即登录</RouterLink>
          </div>
          <div v-else-if="favorites.length === 0" class="empty-state">还没有收藏，去逛逛吧。</div>
          <div v-else class="panel-list">
            <div
              v-for="item in favorites.slice(0, 4)"
              :key="item.id"
              class="panel-item"
            >
              <img :src="getSafeImage(item)" :alt="item.name" @error="handleSidebarImageError(item.id)" />
              <div>
                <div>{{ item.name }}</div>
                <span>评分 {{ item.avgScore.toFixed(1) }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="panel-card">
          <h3>购物袋</h3>
          <div v-if="!isLoggedIn" class="empty-state">
            登录后查看购物袋。
            <RouterLink class="link-primary" to="/login">立即登录</RouterLink>
          </div>
          <div v-else-if="cartItems.length === 0" class="empty-state">购物袋为空，先收藏心仪商品。</div>
          <div v-else class="panel-list">
            <div
              v-for="item in cartItems.slice(0, 4)"
              :key="item.id"
              class="panel-item"
            >
              <img :src="getSafeImage(item)" :alt="item.name" @error="handleSidebarImageError(item.id)" />
              <div>
                <div>{{ item.name }}</div>
                <span>人气 {{ item.ratingCount }}</span>
              </div>
            </div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

