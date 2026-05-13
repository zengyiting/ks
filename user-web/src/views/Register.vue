<script setup>
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useUser } from '../composables/useUser.js';

const router = useRouter();
const { loginWithPhone, loginWithEmail } = useUser();

const registerType = ref('phone'); // 'phone' or 'email'
const username = ref('');
const phone = ref('');
const email = ref('');
const emailCode = ref('');
const password = ref('');
const confirmPassword = ref('');
const status = ref('');
const loading = ref(false);
const codeLoading = ref(false);
const codeCountdown = ref(0);

const canSubmit = computed(() => {
  const hasUsername = username.value.trim().length >= 3;
  const hasPassword = password.value.length >= 6;
  const passwordMatch = password.value === confirmPassword.value;
  
  if (registerType.value === 'phone') {
    return hasUsername && phone.value.trim().length === 11 && hasPassword && passwordMatch;
  } else {
    return hasUsername && email.value.trim().includes('@') && emailCode.value.length === 6 && hasPassword && passwordMatch;
  }
});

const canSendCode = computed(() => {
  return email.value.trim().includes('@') && codeCountdown.value === 0 && !codeLoading.value;
});

const sendEmailCode = async () => {
  if (!canSendCode.value) return;
  
  codeLoading.value = true;
  status.value = '';
  
  try {
    const data = await fetch('/api/auth/send-email-code', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: email.value.trim() })
    }).then(res => res.json());
    
    if (data.success) {
      status.value = '验证码已发送，请查收邮箱';
      codeCountdown.value = 60;
      startCountdown();
    } else {
      status.value = data.message || '发送失败';
    }
  } catch (err) {
    status.value = err.message || '发送失败';
  } finally {
    codeLoading.value = false;
  }
};

const startCountdown = () => {
  const timer = setInterval(() => {
    codeCountdown.value--;
    if (codeCountdown.value <= 0) {
      clearInterval(timer);
    }
  }, 1000);
};

const submitRegister = async () => {
  if (!canSubmit.value) {
    if (password.value !== confirmPassword.value) {
      status.value = '两次输入的密码不一致';
    } else if (registerType.value === 'email' && emailCode.value.length !== 6) {
      status.value = '请输入6位验证码';
    } else {
      status.value = '请填写完整信息';
    }
    return;
  }
  
  loading.value = true;
  status.value = '';
  
  try {
    const endpoint = registerType.value === 'phone' ? '/api/auth/register' : '/api/auth/register-email';
    const data = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: username.value.trim(),
        phone: registerType.value === 'phone' ? phone.value.trim() : null,
        email: registerType.value === 'email' ? email.value.trim() : null,
        code: registerType.value === 'email' ? emailCode.value : null,
        password: password.value
      })
    }).then(res => res.json());
    
    if (data.success) {
      status.value = '注册成功，正在登录...';
      setTimeout(() => {
        if (registerType.value === 'phone') {
          loginWithPhone(phone.value, password.value).then(() => {
            router.push('/');
          }).catch(() => {
            router.push('/login');
          });
        } else {
          loginWithEmail(email.value, password.value).then(() => {
            router.push('/');
          }).catch(() => {
            router.push('/login');
          });
        }
      }, 1500);
    } else {
      status.value = data.message || '注册失败';
    }
  } catch (err) {
    status.value = err.message || '注册失败';
  } finally {
    loading.value = false;
  }
};

const goToLogin = () => {
  router.push('/login');
};

const switchRegisterType = (type) => {
  registerType.value = type;
  emailCode.value = '';
  codeCountdown.value = 0;
  status.value = '';
};
</script>

<template>
  <div class="register-page">
    <!-- 背景动画层 -->
    <div class="animation-bg">
      <!-- 流星 -->
      <div class="shooting-star" v-for="i in 8" :key="i" :style="{ '--delay': `${i * 2}s`, '--top': `${10 + i * 10}%` }"></div>
      <!-- 花瓣 -->
      <div class="petal" v-for="i in 12" :key="'petal-' + i" :style="{ '--delay': `${i * 1.5}s`, '--left': `${5 + i * 8}%` }"></div>
      <!-- 光晕 -->
      <div class="glow-circle glow-1"></div>
      <div class="glow-circle glow-2"></div>
      <div class="glow-circle glow-3"></div>
    </div>

    <div class="register-container">
      <section class="register-panel">
        <div class="register-header">
          <div class="logo-wrapper">
            <div class="logo-icon">
              <svg viewBox="0 0 48 48" fill="none">
                <circle cx="24" cy="24" r="22" fill="url(#gradient1)"/>
                <path d="M16 28l6-6 4 4 8-10" stroke="white" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
                <defs>
                  <linearGradient id="gradient1" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" style="stop-color:#4f7cff"/>
                    <stop offset="100%" style="stop-color:#8b5cf6"/>
                  </linearGradient>
                </defs>
              </svg>
            </div>
            <h1>欢迎加入</h1>
            <p>创建账号，开启发现之旅</p>
          </div>
        </div>

        <!-- 注册类型切换 -->
        <div class="register-tabs">
          <button 
            class="tab-btn" 
            :class="{ active: registerType === 'phone' }"
            @click="switchRegisterType('phone')"
          >
            <span class="tab-icon">📱</span>
            手机注册
          </button>
          <button 
            class="tab-btn" 
            :class="{ active: registerType === 'email' }"
            @click="switchRegisterType('email')"
          >
            <span class="tab-icon">📧</span>
            邮箱注册
          </button>
        </div>

        <form class="register-form" @submit.prevent="submitRegister">
          <label class="field">
            <span class="field-label">用户名</span>
            <input
              v-model="username"
              class="text-input"
              type="text"
              placeholder="请输入用户名（至少3个字符）"
              autocomplete="username"
            />
          </label>

          <label class="field" v-if="registerType === 'phone'">
            <span class="field-label">手机号</span>
            <input
              v-model="phone"
              class="text-input"
              type="tel"
              placeholder="请输入11位手机号"
              autocomplete="tel"
              inputmode="numeric"
            />
          </label>

          <template v-else>
            <label class="field">
              <span class="field-label">邮箱</span>
              <input
                v-model="email"
                class="text-input"
                type="email"
                placeholder="请输入邮箱地址"
                autocomplete="email"
              />
            </label>
            
            <label class="field">
              <span class="field-label">验证码</span>
              <div class="code-input-wrapper">
                <input
                  v-model="emailCode"
                  class="text-input code-input"
                  type="text"
                  placeholder="请输入6位验证码"
                  maxlength="6"
                  inputmode="numeric"
                />
                <button 
                  class="code-btn" 
                  type="button" 
                  @click="sendEmailCode"
                  :disabled="!canSendCode"
                >
                  <span v-if="codeLoading">发送中...</span>
                  <span v-else-if="codeCountdown > 0">{{ codeCountdown }}s</span>
                  <span v-else>发送验证码</span>
                </button>
              </div>
            </label>
          </template>

          <label class="field">
            <span class="field-label">密码</span>
            <input
              v-model="password"
              class="text-input"
              type="password"
              placeholder="请输入密码（至少6位）"
              autocomplete="new-password"
            />
          </label>

          <label class="field">
            <span class="field-label">确认密码</span>
            <input
              v-model="confirmPassword"
              class="text-input"
              type="password"
              placeholder="请再次输入密码"
              autocomplete="new-password"
            />
          </label>

          <button class="btn-primary btn-large" type="submit" :disabled="loading || !canSubmit">
            <span v-if="loading" class="loading-spinner"></span>
            {{ loading ? '注册中...' : '立即注册' }}
          </button>

          <div class="status-line" :class="{ error: status && !status.includes('成功') && !status.includes('发送') }" v-if="status">
            {{ status }}
          </div>
        </form>

        <div class="login-link">
          已有账号？
          <a href="/login" @click.prevent="goToLogin" class="link-primary">立即登录</a>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.register-page {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 25%, #f093fb 50%, #f5576c 75%, #4facfe 100%);
  background-size: 400% 400%;
  animation: gradientShift 15s ease infinite;
}

@keyframes gradientShift {
  0% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
  100% { background-position: 0% 50%; }
}

.animation-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

/* 流星动画 */
.shooting-star {
  position: absolute;
  top: var(--top);
  right: -100px;
  width: 100px;
  height: 2px;
  background: linear-gradient(90deg, rgba(255,255,255,0.8), transparent);
  animation: shoot 4s var(--delay) infinite linear;
}

.shooting-star::before {
  content: '';
  position: absolute;
  right: 0;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: white;
  box-shadow: 0 0 10px 2px rgba(255,255,255,0.8);
}

@keyframes shoot {
  0% { transform: translateX(0) translateY(0) rotate(-45deg); opacity: 0; }
  10% { opacity: 1; }
  90% { opacity: 1; }
  100% { transform: translateX(-120vw) translateY(120vh) rotate(-45deg); opacity: 0; }
}

/* 花瓣动画 */
.petal {
  position: absolute;
  bottom: -50px;
  left: var(--left);
  width: 12px;
  height: 12px;
  background: rgba(255, 200, 220, 0.6);
  border-radius: 50% 0 50% 50%;
  animation: fall 8s var(--delay) infinite ease-in-out;
}

@keyframes fall {
  0% { transform: translateY(0) rotate(0deg); opacity: 0; }
  10% { opacity: 0.8; }
  90% { opacity: 0.6; }
  100% { transform: translateY(-120vh) rotate(720deg); opacity: 0; }
}

/* 光晕效果 */
.glow-circle {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.4;
}

.glow-1 {
  width: 400px;
  height: 400px;
  top: -100px;
  right: -100px;
  background: rgba(139, 92, 246, 0.5);
}

.glow-2 {
  width: 300px;
  height: 300px;
  bottom: -50px;
  left: -50px;
  background: rgba(240, 147, 251, 0.4);
}

.glow-3 {
  width: 250px;
  height: 250px;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: rgba(79, 124, 255, 0.3);
}

.register-container {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 420px;
  padding: 20px;
}

.register-panel {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 24px;
  padding: 32px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
}

.register-header {
  text-align: center;
  margin-bottom: 28px;
}

.logo-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.logo-icon {
  width: 64px;
  height: 64px;
  margin-bottom: 8px;
}

.logo-wrapper h1 {
  font-size: 32px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
}

.logo-wrapper p {
  color: #6b7280;
  font-size: 14px;
  margin: 0;
}

.register-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 24px;
  background: #f3f4f6;
  border-radius: 12px;
  padding: 4px;
}

.tab-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 16px;
  border: none;
  border-radius: 10px;
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

.tab-icon {
  font-size: 18px;
}

.register-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
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

.code-input-wrapper {
  display: flex;
  gap: 12px;
}

.code-input {
  flex: 1;
}

.code-btn {
  min-width: 120px;
  padding: 12px 16px;
  border-radius: 12px;
  border: 1px solid #4f7cff;
  background: rgba(79, 124, 255, 0.1);
  color: #4f7cff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.code-btn:hover:not(:disabled) {
  background: rgba(79, 124, 255, 0.2);
}

.code-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-large {
  min-height: 52px;
  font-size: 15px;
  font-weight: 600;
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

.login-link {
  margin-top: 20px;
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
</style>