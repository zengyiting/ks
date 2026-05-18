<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { apiGet, apiPut, apiPost } from '../api/client.js';
import { useUser } from '../composables/useUser.js';

const router = useRouter();
const { userId, isLoggedIn, logout, fetchCurrentUser } = useUser();

const username = ref('');
const phone = ref('');
const email = ref('');
const oldPassword = ref('');
const newPassword = ref('');
const confirmPassword = ref('');
const status = ref('');
const loading = ref(false);
const passwordLoading = ref(false);
const activeTab = ref('profile');

const loadProfile = async () => {
  if (!userId.value) return;
  try {
    const data = await apiGet('/api/auth/me');
    if (data) {
      username.value = data.username || '';
      phone.value = data.phone || '';
      email.value = data.email || '';
    }
  } catch (err) {
    status.value = err.message;
  }
};

const updateProfile = async () => {
  if (!username.value.trim()) {
    status.value = '用户名不能为空';
    return;
  }
  if (username.value.trim().length < 3) {
    status.value = '用户名至少3个字符';
    return;
  }
  loading.value = true;
  status.value = '';
  try {
    await apiPut('/api/auth/me', {
      username: username.value.trim(),
      phone: phone.value.trim() || null,
      email: email.value.trim() || null
    });
    status.value = '资料已更新';
    await fetchCurrentUser();
  } catch (err) {
    status.value = err.message;
  } finally {
    loading.value = false;
  }
};

const changePassword = async () => {
  if (!oldPassword.value) {
    status.value = '请输入旧密码';
    return;
  }
  if (!newPassword.value || newPassword.value.length < 6) {
    status.value = '新密码至少6位';
    return;
  }
  if (newPassword.value !== confirmPassword.value) {
    status.value = '两次密码输入不一致';
    return;
  }
  passwordLoading.value = true;
  status.value = '';
  try {
    await apiPost('/api/auth/me/change-password', {
      oldPassword: oldPassword.value,
      newPassword: newPassword.value
    });
    status.value = '密码修改成功，请重新登录';
    oldPassword.value = '';
    newPassword.value = '';
    confirmPassword.value = '';
    setTimeout(() => {
      logout();
      router.push('/login');
    }, 1500);
  } catch (err) {
    status.value = err.message;
  } finally {
    passwordLoading.value = false;
  }
};

onMounted(() => {
  loadProfile();
});
</script>

<template>
  <section class="profile-page">
    <h2 class="section-title">个人中心</h2>

    <div class="profile-tabs">
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'profile' }"
        @click="activeTab = 'profile'"
      >
        基本资料
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'password' }"
        @click="activeTab = 'password'"
      >
        修改密码
      </button>
    </div>

    <div v-if="activeTab === 'profile'" class="profile-card">
      <form class="profile-form" @submit.prevent="updateProfile">
        <label class="field">
          <span class="field-label">用户名</span>
          <input v-model="username" class="text-input" type="text" placeholder="请输入用户名" />
        </label>

        <label class="field">
          <span class="field-label">手机号</span>
          <input v-model="phone" class="text-input" type="tel" placeholder="请输入手机号" inputmode="numeric" />
        </label>

        <label class="field">
          <span class="field-label">邮箱</span>
          <input v-model="email" class="text-input" type="email" placeholder="请输入邮箱" />
        </label>

        <button class="btn-primary" type="submit" :disabled="loading">
          {{ loading ? '保存中...' : '保存修改' }}
        </button>
      </form>
    </div>

    <div v-if="activeTab === 'password'" class="profile-card">
      <form class="profile-form" @submit.prevent="changePassword">
        <label class="field">
          <span class="field-label">旧密码</span>
          <input v-model="oldPassword" class="text-input" type="password" placeholder="请输入旧密码" />
        </label>

        <label class="field">
          <span class="field-label">新密码</span>
          <input v-model="newPassword" class="text-input" type="password" placeholder="至少6位字符" />
        </label>

        <label class="field">
          <span class="field-label">确认新密码</span>
          <input v-model="confirmPassword" class="text-input" type="password" placeholder="再次输入新密码" />
        </label>

        <button class="btn-primary" type="submit" :disabled="passwordLoading">
          {{ passwordLoading ? '修改中...' : '修改密码' }}
        </button>
      </form>
    </div>

    <div class="status-line" :class="{ error: status && !status.includes('成功') }" v-if="status">
      {{ status }}
    </div>
  </section>
</template>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
  animation: riseIn 0.6s ease both;
  max-width: 560px;
}

.profile-tabs {
  display: flex;
  gap: 6px;
  background: #f3f4f6;
  border-radius: 10px;
  padding: 3px;
}

.tab-btn {
  flex: 1;
  padding: 10px 16px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #6b7280;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.tab-btn.active {
  background: white;
  color: #4f7cff;
  box-shadow: 0 2px 8px rgba(79, 124, 255, 0.15);
}

.profile-card {
  padding: 28px;
  border-radius: 20px;
  border: 1px solid rgba(20, 20, 20, 0.08);
  background: linear-gradient(145deg, #fbfdff, #f2f7ff);
  box-shadow: var(--shadow-soft);
}

.profile-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}

.text-input {
  width: 100%;
  min-height: 48px;
  padding: 12px 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  font-size: 14px;
  background: rgba(255, 255, 255, 0.8);
  font-family: inherit;
  transition: var(--transition);
}

.text-input:focus {
  outline: none;
  border-color: #4f7cff;
  background: white;
  box-shadow: 0 0 0 3px rgba(79, 124, 255, 0.1);
}

.btn-primary {
  width: 100%;
  padding: 14px 24px;
  border-radius: 12px;
  border: none;
  background: linear-gradient(135deg, #4f7cff 0%, #8b5cf6 100%);
  color: white;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 4px 15px rgba(79, 124, 255, 0.4);
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(79, 124, 255, 0.5);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.status-line {
  text-align: center;
  font-size: 13px;
  padding: 10px;
  border-radius: 8px;
  background: rgba(79, 124, 255, 0.1);
  color: #4f7cff;
}

.status-line.error {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}
</style>
