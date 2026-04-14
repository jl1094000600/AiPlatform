<template>
  <div class="login-container">
    <!-- Animated particles -->
    <div class="particles">
      <div v-for="i in 20" :key="i" class="particle" :style="getParticleStyle(i)"></div>
    </div>

    <!-- Login Card -->
    <div class="login-wrapper animate-fade-in-up">
      <div class="logo-section">
        <div class="logo-icon">
          <svg width="48" height="48" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
            <circle cx="24" cy="24" r="20" stroke="url(#logoGrad)" stroke-width="2" fill="none"/>
            <circle cx="24" cy="24" r="8" fill="url(#logoGrad)"/>
            <circle cx="24" cy="10" r="3" fill="#00f0ff"/>
            <circle cx="24" cy="38" r="3" fill="#ff00aa"/>
            <circle cx="10" cy="24" r="3" fill="#8b5cf6"/>
            <circle cx="38" cy="24" r="3" fill="#00f0ff"/>
            <line x1="24" y1="10" x2="24" y2="16" stroke="#00f0ff" stroke-width="1.5"/>
            <line x1="24" y1="32" x2="24" y2="38" stroke="#ff00aa" stroke-width="1.5"/>
            <line x1="10" y1="24" x2="16" y2="24" stroke="#8b5cf6" stroke-width="1.5"/>
            <line x1="32" y1="24" x2="38" y2="24" stroke="#00f0ff" stroke-width="1.5"/>
            <defs>
              <linearGradient id="logoGrad" x1="0" y1="0" x2="48" y2="48">
                <stop offset="0%" stop-color="#00f0ff"/>
                <stop offset="100%" stop-color="#ff00aa"/>
              </linearGradient>
            </defs>
          </svg>
        </div>
        <h1 class="logo-title">AI中台管理系统</h1>
        <p class="logo-subtitle">Neural Command Center</p>
      </div>

      <el-form :model="form" @submit.prevent="handleLogin" class="login-form">
        <div class="input-group">
          <label class="input-label">用户名</label>
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            size="large"
            :prefix-icon="User"
          />
        </div>
        <div class="input-group">
          <label class="input-label">密码</label>
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
            :prefix-icon="Lock"
            @keyup.enter="handleLogin"
          />
        </div>
        <el-button
          type="primary"
          size="large"
          native-type="submit"
          class="login-btn"
          :loading="loading"
        >
          {{ loading ? '登录中...' : '进入系统' }}
        </el-button>
      </el-form>

      <div class="login-footer">
        <span class="footer-text">默认账号: admin / admin123</span>
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

const getParticleStyle = (i) => {
  const size = Math.random() * 4 + 2
  const duration = Math.random() * 20 + 10
  const delay = Math.random() * 5
  return {
    width: `${size}px`,
    height: `${size}px`,
    left: `${Math.random() * 100}%`,
    top: `${Math.random() * 100}%`,
    animationDuration: `${duration}s`,
    animationDelay: `${delay}s`
  }
}

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
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  position: relative;
  overflow: hidden;
}

.particles {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.particle {
  position: absolute;
  background: var(--accent-cyan);
  border-radius: 50%;
  opacity: 0.4;
  animation: floatParticle linear infinite;
}

@keyframes floatParticle {
  0% { transform: translateY(100vh) rotate(0deg); opacity: 0; }
  10% { opacity: 0.4; }
  90% { opacity: 0.4; }
  100% { transform: translateY(-100vh) rotate(720deg); opacity: 0; }
}

.login-wrapper {
  width: 420px;
  padding: 48px 40px;
  background: var(--bg-card);
  backdrop-filter: blur(30px);
  border: 1px solid var(--border-color);
  border-radius: 24px;
  box-shadow:
    0 0 60px rgba(0, 240, 255, 0.1),
    0 25px 50px rgba(0, 0, 0, 0.5);
  position: relative;
  z-index: 10;
}

.login-wrapper::before {
  content: '';
  position: absolute;
  top: -1px;
  left: 20%;
  right: 20%;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--accent-cyan), var(--accent-magenta), transparent);
  border-radius: 2px;
}

.logo-section {
  text-align: center;
  margin-bottom: 40px;
}

.logo-icon {
  width: 80px;
  height: 80px;
  margin: 0 auto 20px;
  animation: glowPulse 3s ease-in-out infinite;
}

@keyframes glowPulse {
  0%, 100% { filter: drop-shadow(0 0 10px rgba(0, 240, 255, 0.5)); }
  50% { filter: drop-shadow(0 0 25px rgba(0, 240, 255, 0.8)); }
}

.logo-title {
  font-size: 28px;
  font-weight: 700;
  background: linear-gradient(135deg, var(--accent-cyan), var(--accent-magenta));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 8px;
  letter-spacing: -0.02em;
}

.logo-subtitle {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-muted);
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.input-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  letter-spacing: 0.05em;
}

.login-btn {
  width: 100%;
  height: 52px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.05em;
  background: linear-gradient(135deg, var(--accent-cyan), var(--accent-purple));
  border: none;
  border-radius: 12px;
  color: #000;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-top: 8px;
}

.login-btn:hover:not(:disabled) {
  transform: translateY(-3px);
  box-shadow: 0 10px 30px rgba(0, 240, 255, 0.4);
}

.login-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.login-footer {
  margin-top: 32px;
  text-align: center;
}

.footer-text {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-muted);
  letter-spacing: 0.05em;
}

:deep(.el-input__wrapper) {
  padding: 12px 16px;
}

:deep(.el-input__prefix) {
  color: var(--text-muted);
}
</style>
