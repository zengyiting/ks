<script setup>
const props = defineProps({
  modelValue: { type: Number, default: 0 },
  readonly: { type: Boolean, default: false }
});

const emit = defineEmits(['update:modelValue']);

const setRating = (value) => {
  if (props.readonly) return;
  emit('update:modelValue', value);
};
</script>

<template>
  <div class="rating-stars">
    <button
      v-for="n in 5"
      :key="n"
      :class="{ active: n <= modelValue }"
      type="button"
      @click="setRating(n)"
      :disabled="readonly"
      :aria-label="`评分 ${n} 星`"
    >
      ★
    </button>
  </div>
</template>
