<template>
  <div class="login-page">
    <!-- Left Panel - Decorative -->
    <div class="login-panel-left">
      <div class="panel-content">
        <div class="brand-section">
          <div class="brand-logo">
            <svg width="56" height="56" viewBox="0 0 56 56" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect x="4" y="4" width="48" height="48" rx="12" stroke="white" stroke-width="2" fill="none"/>
              <circle cx="28" cy="28" r="10" stroke="white" stroke-width="2" fill="none"/>
              <circle cx="28" cy="28" r="4" fill="white"/>
              <line x1="28" y1="4" x2="28" y2="18" stroke="white" stroke-width="2"/>
              <line x1="28" y1="38" x2="28" y2="52" stroke="white" stroke-width="2"/>
              <line x1="4" y1="28" x2="18" y2="28" stroke="white" stroke-width="2"/>
              <line x1="38" y1="28" x2="52" y2="28" stroke="white" stroke-width="2"/>
            </svg>
          </div>
          <h1 class="brand-title">AI Platform</h1>
          <p class="brand-tagline">智能_agent协作平台</p>
        </div>

        <div class="features-list">
          <div class="feature-item" v-for="(feature, index) in features" :key="index">
            <div class="feature-icon">
              <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                <path d="M4 10L8 14L16 6" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </div>
            <span>{{ feature }}</span>
          </div>
        </div>

        <div class="decoration-circles">
          <div class="circle circle-1"></div>
          <div class="circle circle-2"></div>
          <div class="circle circle-3"></div>
        </div>
      </div>
    </div>

    <!-- Right Panel - Login Form -->
    <div class="login-panel-right">
      <div class="login-container">
        <div class="login-card animate-fade-in-up">
          <div class="login-header">
            <h2 class="login-title">欢迎回来</h2>
            <p class="login-subtitle">请登录您的账户继续</p>
          </div>

          <el-form :model="form" @submit.prevent="handleLogin" class="login-form">
            <div class="form-item">
              <label class="form-label">用户名 / 邮箱</label>
              <el-input
                v-model="form.username"
                placeholder="请输入用户名或邮箱"
                size="large"
                :prefix-icon="User"
                class="custom-input"
              />
            </div>

            <div class="form-item">
              <label class="form-label">密码</label>
              <el-input
                v-model="form.password"
                type="password"
                placeholder="请输入密码"
                size="large"
                show-password
                :prefix-icon="Lock"
                class="custom-input"
                @keyup.enter="handleLogin"
              />
            </div>

            <div class="form-options">
              <label class="remember-me">
                <input type="checkbox" v-model="rememberMe" />
                <span>记住我</span>
              </label>
              <a href="#" class="forgot-link">忘记密码？</a>
            </div>

            <el-button
              type="primary"
              size="large"
              native-type="submit"
              class="login-btn"
              :loading="loading"
            >
              {{ loading ? '登录中...' : '登 录' }}
            </el-button>
          </el-form>

          <div class="login-footer">
            <p class="footer-hint">默认账号: <span class="highlight">admin</span> / <span class="highlight">admin123</span></p>
          </div>
        </div>

        <p class="copyright">© 2024 AI Platform. All rights reserved.</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import api from '../api'

const router = useRouter()
const form = reactive({ username: '', password: '' })
const loading = ref(false)
const rememberMe = ref(false)

const features = [
  '智能 Agent 注册与管理',
  '实时心跳监控',
  'A2A 通信协议',
  'Agent 调用关系图谱',
  'TTS 语音合成'
]

const handleLogin = async () => {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }

  loading.value = true
  try {
    const res = await api.login(form)
    if (res.data.code === 200) {
      localStorage.setItem('token', res.data.data.token)
      localStorage.setItem('user', JSON.stringify(res.data.data))
      ElMessage.success({ message: '登录成功，欢迎回来！', duration: 1500 })
      router.push('/agents')
    } else {
      ElMessage.error(res.data.message || '登录失败')
    }
  } catch (e) {
    ElMessage.error('登录失败，请检查网络连接')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* ============================================
   Login Page Layout
   ============================================ */
.login-page {
  display: flex;
  min-height: 100vh;
  background: #f8fafc;
}

/* ============================================
   Left Panel - Decorative
   ============================================ */
.login-panel-left {
  flex: 1;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px;
}

.panel-content {
  position: relative;
  z-index: 2;
  max-width: 480px;
}

.brand-section {
  text-align: center;
  margin-bottom: 60px;
}

.brand-logo {
  width: 80px;
  height: 80px;
  margin: 0 auto 24px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(10px);
  animation: floatLogo 3s ease-in-out infinite;
}

@keyframes floatLogo {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

.brand-title {
  font-size: 36px;
  font-weight: 700;
  color: white;
  margin-bottom: 12px;
  letter-spacing: -0.02em;
}

.brand-tagline {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.8);
  letter-spacing: 0.1em;
}

.features-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 14px;
  color: white;
  font-size: 15px;
  opacity: 0;
  animation: slideInLeft 0.5s ease forwards;
}

.feature-item:nth-child(1) { animation-delay: 0.2s; }
.feature-item:nth-child(2) { animation-delay: 0.35s; }
.feature-item:nth-child(3) { animation-delay: 0.5s; }
.feature-item:nth-child(4) { animation-delay: 0.65s; }
.feature-item:nth-child(5) { animation-delay: 0.8s; }

@keyframes slideInLeft {
  from {
    opacity: 0;
    transform: translateX(-20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.feature-icon {
  width: 28px;
  height: 28px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

/* Decorative Circles */
.decoration-circles {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 1;
}

.circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
}

.circle-1 {
  width: 300px;
  height: 300px;
  top: -100px;
  right: -80px;
  animation: pulse 4s ease-in-out infinite;
}

.circle-2 {
  width: 200px;
  height: 200px;
  bottom: -60px;
  left: -60px;
  animation: pulse 4s ease-in-out infinite 1s;
}

.circle-3 {
  width: 150px;
  height: 150px;
  top: 50%;
  right: 20%;
  animation: pulse 4s ease-in-out infinite 2s;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 0.5; }
  50% { transform: scale(1.1); opacity: 0.8; }
}

/* ============================================
   Right Panel - Login Form
   ============================================ */
.login-panel-right {
  width: 520px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px;
}

.login-container {
  width: 100%;
  max-width: 400px;
}

.login-card {
  background: white;
  border-radius: 24px;
  padding: 48px 40px;
  box-shadow:
    0 4px 6px -1px rgba(0, 0, 0, 0.05),
    0 10px 20px -5px rgba(0, 0, 0, 0.08);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.login-card:hover {
  transform: translateY(-4px);
  box-shadow:
    0 8px 12px -2px rgba(0, 0, 0, 0.08),
    0 20px 40px -10px rgba(0, 0, 0, 0.12);
}

.login-header {
  text-align: center;
  margin-bottom: 36px;
}

.login-title {
  font-size: 28px;
  font-weight: 700;
  color: #1a1a2e;
  margin-bottom: 8px;
}

.login-subtitle {
  font-size: 14px;
  color: #64748b;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-label {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  letter-spacing: 0.02em;
}

.custom-input :deep(.el-input__wrapper) {
  padding: 14px 16px;
  border-radius: 12px;
  border: 1.5px solid #e2e8f0;
  background: #f8fafc;
  box-shadow: none;
  transition: all 0.2s ease;
}

.custom-input :deep(.el-input__wrapper:hover) {
  border-color: #c7d2fe;
  background: white;
}

.custom-input :deep(.el-input__wrapper.is-focus) {
  border-color: #667eea;
  background: white;
  box-shadow: 0 0 0 4px rgba(102, 126, 234, 0.1);
}

.custom-input :deep(.el-input__inner) {
  color: #1a1a2e;
  font-size: 15px;
}

.custom-input :deep(.el-input__prefix) {
  color: #94a3b8;
}

.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.remember-me {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 13px;
  color: #64748b;
}

.remember-me input {
  width: 16px;
  height: 16px;
  accent-color: #667eea;
}

.forgot-link {
  font-size: 13px;
  color: #667eea;
  text-decoration: none;
  font-weight: 500;
  transition: color 0.2s;
}

.forgot-link:hover {
  color: #764ba2;
}

.login-btn {
  width: 100%;
  height: 52px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.05em;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  border-radius: 12px;
  color: white;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
  margin-top: 8px;
}

.login-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.4);
}

.login-btn:active:not(:disabled) {
  transform: translateY(0);
}

.login-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.login-footer {
  margin-top: 32px;
  text-align: center;
}

.footer-hint {
  font-size: 12px;
  color: #94a3b8;
}

.highlight {
  color: #667eea;
  font-weight: 600;
}

.copyright {
  text-align: center;
  margin-top: 24px;
  font-size: 12px;
  color: #94a3b8;
}

/* ============================================
   Animations
   ============================================ */
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.animate-fade-in-up {
  animation: fadeInUp 0.6s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

/* ============================================
   Responsive
   ============================================ */
@media (max-width: 1024px) {
  .login-panel-left {
    display: none;
  }

  .login-panel-right {
    width: 100%;
    padding: 40px 24px;
  }
}

@media (max-width: 480px) {
  .login-card {
    padding: 32px 24px;
  }

  .login-title {
    font-size: 24px;
  }
}
</style>
