<script setup>
import { computed, ref } from 'vue';

const props = defineProps({
  item: { type: Object, required: true },
  reason: { type: String, default: '' },
  metaLabel: { type: String, default: '' },
  metaValue: { type: [String, Number], default: '' },
  favorite: { type: Boolean, default: false },
  inCart: { type: Boolean, default: false },
  actionsDisabled: { type: Boolean, default: false }
});

const emit = defineEmits(['open', 'favorite', 'cart']);

const imageError = ref(false);

const safeImage = computed(() => {
  if (imageError.value) return '/images/placeholder.svg';
  if (props.item.imageUrl) {
    if (props.item.imageUrl.startsWith('/images/') || props.item.imageUrl.startsWith('http')) {
      return props.item.imageUrl;
    }
    return `https://picsum.photos/seed/${props.item.id || props.item.itemId}/400/300`;
  }
  return `https://picsum.photos/seed/${props.item.id || props.item.itemId}/400/300`;
});

const categoryText = computed(() => props.item.category || '未分类');
const avgScore = computed(() => Number(props.item.avgScore || 0));
const ratingCount = computed(() => Number(props.item.ratingCount || 0));
const priceText = computed(() => {
  const p = Number(props.item.price || 0);
  return p > 0 ? `¥${p.toFixed(2)}` : '';
});
const favoriteLabel = computed(() => {
  if (props.actionsDisabled) return '登录后收藏';
  return props.favorite ? '已收藏' : '收藏';
});
const cartLabel = computed(() => {
  if (props.actionsDisabled) return '登录后加购';
  return props.inCart ? '已加购' : '加购';
});

const handleImageError = () => {
  imageError.value = true;
};
</script>

<template>
  <article class="item-card" @click="emit('open')">
    <div class="item-media">
      <img :src="safeImage" :alt="item.name" loading="lazy" @error="handleImageError" />
      <span v-if="reason" class="item-tag">推荐理由</span>
    </div>
    <div class="item-body">
      <div class="item-title">
        <h3>{{ item.name }}</h3>
        <span class="item-category">{{ categoryText }}</span>
      </div>
      <div v-if="priceText" class="item-price">{{ priceText }}</div>
      <p v-if="reason" class="item-reason">{{ reason }}</p>
      <div class="item-meta">
        <span v-if="metaLabel" class="meta-pill">{{ metaLabel }} {{ metaValue }}</span>
        <span v-if="avgScore" class="meta-pill">评分 {{ avgScore.toFixed(1) }}</span>
        <span v-if="ratingCount" class="meta-pill">人气 {{ ratingCount }}</span>
      </div>
      <div class="item-actions">
        <button class="btn-ghost" :disabled="actionsDisabled" @click.stop="emit('favorite')">
          {{ favoriteLabel }}
        </button>
        <button class="btn-plant" :disabled="actionsDisabled" @click.stop="emit('cart')">
          {{ cartLabel }}
        </button>
      </div>
    </div>
  </article>
</template>
