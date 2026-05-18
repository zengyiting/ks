<script setup>
import { ref, onMounted, onUnmounted } from 'vue';

const toasts = ref([]);
let nextId = 1;

const addToast = (message, type = 'info', duration = 4000) => {
  const id = nextId++;
  toasts.value.push({ id, message, type, visible: true });
  if (duration > 0) {
    setTimeout(() => removeToast(id), duration);
  }
};

const removeToast = (id) => {
  const toast = toasts.value.find(t => t.id === id);
  if (toast) {
    toast.visible = false;
    setTimeout(() => {
      toasts.value = toasts.value.filter(t => t.id !== id);
    }, 300);
  }
};

const typeIcon = (type) => {
  switch (type) {
    case 'success': return '✓';
    case 'error': return '✕';
    case 'warning': return '⚠';
    default: return 'ℹ';
  }
};

onMounted(() => {
  window.$toast = {
    success: (msg, dur) => addToast(msg, 'success', dur),
    error: (msg, dur) => addToast(msg, 'error', dur),
    warning: (msg, dur) => addToast(msg, 'warning', dur),
    info: (msg, dur) => addToast(msg, 'info', dur)
  };
});

onUnmounted(() => {
  delete window.$toast;
});

defineExpose({ addToast, removeToast });
</script>

<template>
  <Teleport to="body">
    <div class="toast-container">
      <TransitionGroup name="toast">
        <div
          v-for="toast in toasts"
          :key="toast.id"
          class="toast"
          :class="`toast-${toast.type}`"
          :style="{ opacity: toast.visible ? 1 : 0, transform: toast.visible ? 'translateY(0)' : 'translateY(-20px)' }"
        >
          <span class="toast-icon">{{ typeIcon(toast.type) }}</span>
          <span class="toast-message">{{ toast.message }}</span>
          <button class="toast-close" @click="removeToast(toast.id)">×</button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-container {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-width: 380px;
}

.toast {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  border-radius: 12px;
  background: white;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  border: 1px solid rgba(0, 0, 0, 0.06);
  transition: all 0.3s ease;
}

.toast-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.toast-message {
  flex: 1;
  font-size: 14px;
  line-height: 1.4;
  color: #1f2937;
}

.toast-close {
  background: none;
  border: none;
  color: #9ca3af;
  font-size: 18px;
  cursor: pointer;
  padding: 0;
  line-height: 1;
  transition: color 0.2s;
}

.toast-close:hover {
  color: #4b5563;
}

.toast-success .toast-icon {
  background: #d1fae5;
  color: #059669;
}

.toast-error .toast-icon {
  background: #fee2e2;
  color: #dc2626;
}

.toast-warning .toast-icon {
  background: #fef3c7;
  color: #d97706;
}

.toast-info .toast-icon {
  background: #dbeafe;
  color: #2563eb;
}

.toast-enter-active {
  transition: all 0.3s ease;
}

.toast-leave-active {
  transition: all 0.3s ease;
}

.toast-enter-from {
  opacity: 0;
  transform: translateX(100%);
}

.toast-leave-to {
  opacity: 0;
  transform: translateX(100%);
}
</style>
