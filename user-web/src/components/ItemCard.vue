<script setup>
import { computed } from 'vue';

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

const safeImage = computed(() => props.item.imageUrl || '/images/placeholder.svg');
const categoryText = computed(() => props.item.category || '未分类');
const avgScore = computed(() => Number(props.item.avgScore || 0));
const ratingCount = computed(() => Number(props.item.ratingCount || 0));
const favoriteLabel = computed(() => {
  if (props.actionsDisabled) return '登录后收藏';
  return props.favorite ? '已收藏' : '收藏';
});
const cartLabel = computed(() => {
  if (props.actionsDisabled) return '登录后加购';
  return props.inCart ? '已加购' : '加购';
});
</script>

<template>
  <article class="item-card" @click="emit('open')">
    <div class="item-media">
      <img :src="safeImage" :alt="item.name" />
      <span v-if="reason" class="item-tag">推荐理由</span>
    </div>
    <div class="item-body">
      <div class="item-title">
        <h3>{{ item.name }}</h3>
        <span class="item-category">{{ categoryText }}</span>
      </div>
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
