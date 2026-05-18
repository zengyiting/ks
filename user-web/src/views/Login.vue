<script setup>
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useUser } from '../composables/useUser.js';

const router = useRouter();
const route = useRoute();
const { loginWithPhone, loginWithEmail, loginWithUsername, loginWithSms, sendSmsCode } = useUser();

const loginType = ref('phone');
const phone = ref('');
const email = ref('');
const username = ref('');
const password = ref('');
const smsCode = ref('');
const status = ref('');
const loading = ref(false);
const sendingCode = ref(false);
const codeCountdown = ref(0);

let countdownTimer = null;

const redirectTo = computed(() => {
  const target = route.query.redirect;
  return typeof target === 'string' && target ? target : '/';
});

const canSubmit = computed(() => {
  if (loginType.value === 'sms') {
    return phone.value.trim().length >= 6 && smsCode.value.length === 6;
  }
  if (loginType.value === 'phone') {
    return phone.value.trim().length >= 6 && password.value.length >= 6;
  } else if (loginType.value === 'email') {
    return email.value.trim().includes('@') && password.value.length >= 6;
  } else {
    return username.value.trim().length >= 3 && password.value.length >= 6;
  }
});

const startCountdown = () => {
  codeCountdown.value = 60;
  countdownTimer = setInterval(() => {
    codeCountdown.value--;
    if (codeCountdown.value <= 0) {
      clearInterval(countdownTimer);
      codeCountdown.value = 0;
    }
  }, 1000);
};

const handleSendCode = async () => {
  if (loginType.value === 'sms' && phone.value.trim().length < 6) {
    status.value = '请输入正确的手机号';
    return;
  }
  sendingCode.value = true;
  status.value = '';
  try {
    await sendSmsCode(phone.value.trim());
    status.value = '验证码已发送';
    startCountdown();
  } catch (err) {
    status.value = err.message || '发送失败';
  } finally {
    sendingCode.value = false;
  }
};

const submitLogin = async () => {
  if (!canSubmit.value) {
    status.value = '请填写完整的登录信息';
    return;
  }

  loading.value = true;
  status.value = '';

  try {
    let result;
    if (loginType.value === 'sms') {
      result = await loginWithSms(phone.value.trim(), smsCode.value);
    } else if (loginType.value === 'phone') {
      result = await loginWithPhone(phone.value.trim(), password.value);
    } else if (loginType.value === 'email') {
      result = await loginWithEmail(email.value.trim(), password.value);
    } else {
      result = await loginWithUsername(username.value.trim(), password.value);
    }

    status.value = '登录成功，正在跳转...';
    setTimeout(() => {
      router.push(redirectTo.value);
    }, 400);
  } catch (err) {
    status.value = err.message || '登录失败';
  } finally {
    loading.value = false;
  }
};

const skipLogin = () => {
  router.push('/');
};

const goToRegister = () => {
  router.push('/register');
};
</script>

<template>
  <div class="login-page">
    <div class="login-container">
      <section class="login-hero">
        <div class="hero-content">
          <span class="pill">轻电商 · 协同过滤</span>
          <h2>发现你的专属推荐</h2>
          <p>浏览无需登录；登录后才能收藏、加购和评分，系统会更懂你的偏好。</p>
          <div class="login-actions">
            <button class="btn-ghost" type="button" @click="skipLogin">先随便逛逛</button>
          </div>
          <div class="login-tip">
            未注册账号会自动创建，建议使用常用账号体验。
          </div>
        </div>
      </section>

      <section class="login-panel">
        <div class="login-header">
          <div>
            <h3>登录账号</h3>
            <div class="section-subtitle">选择登录方式</div>
          </div>
        </div>

        <div class="login-tabs">
          <button
            class="tab-btn"
            :class="{ active: loginType === 'phone' }"
            @click="loginType = 'phone'"
          >
            手机密码
          </button>
          <button
            class="tab-btn"
            :class="{ active: loginType === 'sms' }"
            @click="loginType = 'sms'"
          >
            验证码
          </button>
          <button
            class="tab-btn"
            :class="{ active: loginType === 'email' }"
            @click="loginType = 'email'"
          >
            邮箱
          </button>
          <button
            class="tab-btn"
            :class="{ active: loginType === 'username' }"
            @click="loginType = 'username'"
          >
            用户名
          </button>
        </div>

        <form class="login-form" @submit.prevent="submitLogin">
          <label class="field" v-if="loginType !== 'email' && loginType !== 'username'">
            <span class="field-label">手机号</span>
            <input
              v-model="phone"
              class="text-input"
              type="tel"
              placeholder="请输入手机号"
              autocomplete="tel"
              inputmode="numeric"
            />
          </label>

          <label class="field" v-else-if="loginType === 'email'">
            <span class="field-label">邮箱</span>
            <input
              v-model="email"
              class="text-input"
              type="email"
              placeholder="请输入邮箱地址"
              autocomplete="email"
            />
          </label>

          <label class="field" v-else>
            <span class="field-label">用户名</span>
            <input
              v-model="username"
              class="text-input"
              type="text"
              placeholder="请输入用户名"
              autocomplete="username"
            />
          </label>

          <label class="field" v-if="loginType === 'sms'">
            <span class="field-label">验证码</span>
            <div class="code-row">
              <input
                v-model="smsCode"
                class="text-input code-input"
                type="text"
                placeholder="6位验证码"
                inputmode="numeric"
                maxlength="6"
              />
              <button
                type="button"
                class="btn-code"
                :disabled="sendingCode || codeCountdown > 0"
                @click="handleSendCode"
              >
                {{ codeCountdown > 0 ? `${codeCountdown}s` : '发送验证码' }}
              </button>
            </div>
          </label>

          <label class="field" v-else>
            <span class="field-label">密码</span>
            <input
              v-model="password"
              class="text-input"
              type="password"
              placeholder="请输入密码"
              autocomplete="current-password"
            />
          </label>

          <button class="btn-primary btn-large" type="submit" :disabled="loading || !canSubmit">
            <span v-if="loading" class="loading-spinner"></span>
            {{ loading ? '登录中...' : '登 录' }}
          </button>

          <div class="login-hint">
            <template v-if="loginType === 'sms'">验证码登录，未注册将自动创建账号。</template>
            <template v-else-if="loginType === 'phone'">若手机号未注册，将自动创建账号。</template>
            <template v-else-if="loginType === 'email'">请先注册邮箱账号。</template>
            <template v-else>请先注册用户名账号。</template>
          </div>
        </form>

        <div class="status-line" :class="{ error: status && !status.includes('成功') }" v-if="status">
          {{ status }}
        </div>

        <div class="register-link">
          还没有账号？
          <a href="/register" @click.prevent="goToRegister" class="link-primary">立即注册</a>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 30%, #0f3460 60%, #1a1a2e 100%);
}

.login-container {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 900px;
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 24px;
}

.login-hero {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 40px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.hero-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.pill {
  align-self: flex-start;
  padding: 6px 14px;
  border-radius: 999px;
  background: rgba(79, 124, 255, 0.2);
  border: 1px solid rgba(79, 124, 255, 0.3);
  color: rgba(255, 255, 255, 0.9);
  font-size: 11px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.login-hero h2 {
  margin: 0;
  font-size: clamp(32px, 3vw, 44px);
  line-height: 1.1;
  color: white;
}

.login-hero p {
  margin: 0;
  color: rgba(255, 255, 255, 0.7);
  font-size: 15px;
  line-height: 1.6;
}

.login-actions {
  margin-top: 8px;
}

.btn-ghost {
  padding: 10px 20px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  background: rgba(255, 255, 255, 0.1);
  color: white;
  font-size: 13px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  cursor: pointer;
  transition: all 0.3s ease;
}

.btn-ghost:hover {
  background: rgba(255, 255, 255, 0.2);
  border-color: rgba(255, 255, 255, 0.5);
}

.login-tip {
  margin-top: 8px;
  color: rgba(255, 255, 255, 0.5);
  font-size: 13px;
}

.login-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 32px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.3);
}

.login-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.login-header h3 {
  margin: 0;
  font-size: 24px;
  color: #1a1a2e;
}

.section-subtitle {
  color: #6b7280;
  font-size: 12px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.login-tabs {
  display: flex;
  gap: 6px;
  background: #f3f4f6;
  border-radius: 10px;
  padding: 3px;
}

.tab-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 8px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #6b7280;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.tab-btn.active {
  background: white;
  color: #4f7cff;
  box-shadow: 0 2px 8px rgba(79, 124, 255, 0.15);
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
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
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  font-size: 14px;
  background: #fafafa;
  transition: all 0.2s ease;
}

.text-input:focus {
  outline: none;
  border-color: #4f7cff;
  background: white;
  box-shadow: 0 0 0 3px rgba(79, 124, 255, 0.1);
}

.code-row {
  display: flex;
  gap: 8px;
}

.code-input {
  flex: 1;
}

.btn-code {
  min-width: 110px;
  padding: 12px 16px;
  border-radius: 12px;
  border: 1px solid #4f7cff;
  background: white;
  color: #4f7cff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.btn-code:hover:not(:disabled) {
  background: #4f7cff;
  color: white;
}

.btn-code:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-large {
  min-height: 52px;
  font-size: 15px;
  font-weight: 600;
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
  letter-spacing: 0.08em;
  text-transform: uppercase;
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

.loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.login-hint {
  text-align: center;
  color: #6b7280;
  font-size: 12px;
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

.register-link {
  text-align: center;
  color: #6b7280;
  font-size: 14px;
}

.link-primary {
  color: #4f7cff;
  font-weight: 600;
  text-decoration: none;
  transition: color 0.2s;
}

.link-primary:hover {
  color: #3b6ae8;
}

@media (max-width: 768px) {
  .login-container {
    grid-template-columns: 1fr;
  }

  .login-hero {
    display: none;
  }

  .login-panel {
    padding: 24px;
  }
}
</style>
