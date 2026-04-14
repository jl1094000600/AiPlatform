<template>
  <div class="layout-container">
    <!-- Sidebar -->
    <aside class="sidebar">
      <div class="sidebar-header">
        <div class="logo-icon">
          <svg width="32" height="32" viewBox="0 0 48 48" fill="none">
            <circle cx="24" cy="24" r="20" stroke="url(#sidebarGrad)" stroke-width="2" fill="none"/>
            <circle cx="24" cy="24" r="8" fill="url(#sidebarGrad)"/>
            <defs>
              <linearGradient id="sidebarGrad" x1="0" y1="0" x2="48" y2="48">
                <stop offset="0%" stop-color="#00f0ff"/>
                <stop offset="100%" stop-color="#ff00aa"/>
              </linearGradient>
            </defs>
          </svg>
        </div>
        <span class="logo-text">AI Platform</span>
      </div>

      <nav class="sidebar-nav">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: $route.path === item.path || ($route.path.startsWith(item.path) && item.path !== '/') }"
        >
          <component :is="item.icon" class="nav-icon" />
          <span class="nav-text">{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <div class="user-info">
          <div class="user-avatar">
            {{ userInfo.username?.charAt(0)?.toUpperCase() || 'A' }}
          </div>
          <div class="user-details">
            <span class="user-name">{{ userInfo.realName || userInfo.username }}</span>
            <span class="user-role">系统管理员</span>
          </div>
        </div>
        <button class="logout-btn" @click="handleLogout" title="退出登录">
          <SwitchButton />
        </button>
      </div>
    </aside>

    <!-- Main Content -->
    <main class="main-content">
      <header class="main-header">
        <div class="header-left">
          <h2 class="page-title">{{ currentPageTitle }}</h2>
        </div>
        <div class="header-right">
          <div class="header-time mono">{{ currentTime }}</div>
        </div>
      </header>

      <div class="content-area">
        <router-view />
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Cpu, Monitor, Box, SwitchButton } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()

const navItems = [
  { path: '/agents', label: 'Agent管理', icon: Cpu },
  { path: '/monitor', label: '接口监控', icon: Monitor },
  { path: '/models', label: '模型管理', icon: Box }
]

const currentPageTitle = computed(() => {
  const item = navItems.find(n => route.path.startsWith(n.path))
  return item?.label || '控制台'
})

const userInfo = computed(() => {
  try {
    return JSON.parse(localStorage.getItem('user') || '{}')
  } catch {
    return {}
  }
})

const currentTime = ref('')
let timeInterval

const updateTime = () => {
  const now = new Date()
  currentTime.value = now.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  })
}

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  router.push('/login')
}

onMounted(() => {
  updateTime()
  timeInterval = setInterval(updateTime, 1000)
})

onUnmounted(() => {
  if (timeInterval) clearInterval(timeInterval)
})
</script>

<style scoped>
.layout-container {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* Sidebar */
.sidebar {
  width: 240px;
  height: 100vh;
  background: var(--bg-card);
  backdrop-filter: blur(20px);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 100;
}

.sidebar::after {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  width: 1px;
  height: 100%;
  background: linear-gradient(180deg, var(--accent-cyan), transparent, var(--accent-magenta));
  opacity: 0.5;
}

.sidebar-header {
  padding: 24px 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid var(--border-color);
}

.logo-icon {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
}

.logo-text {
  font-size: 16px;
  font-weight: 700;
  background: linear-gradient(135deg, var(--accent-cyan), var(--accent-magenta));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: -0.01em;
}

.sidebar-nav {
  flex: 1;
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 12px;
  color: var(--text-secondary);
  text-decoration: none;
  transition: all 0.2s ease;
  position: relative;
  overflow: hidden;
}

.nav-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 0;
  background: var(--accent-cyan);
  border-radius: 0 3px 3px 0;
  transition: height 0.2s ease;
}

.nav-item:hover {
  background: rgba(0, 240, 255, 0.05);
  color: var(--text-primary);
}

.nav-item.active {
  background: rgba(0, 240, 255, 0.1);
  color: var(--accent-cyan);
}

.nav-item.active::before {
  height: 24px;
}

.nav-icon {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
}

.nav-text {
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.01em;
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-info {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--accent-cyan), var(--accent-purple));
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  color: #000;
  flex-shrink: 0;
}

.user-details {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-role {
  font-size: 11px;
  color: var(--text-muted);
}

.logout-btn {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.2);
  color: var(--accent-red);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.logout-btn:hover {
  background: rgba(239, 68, 68, 0.2);
  transform: scale(1.05);
}

/* Main Content */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.main-header {
  height: 64px;
  padding: 0 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border-color);
  background: rgba(5, 5, 8, 0.5);
  backdrop-filter: blur(10px);
}

.header-left {
  display: flex;
  align-items: center;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.header-time {
  font-size: 13px;
  color: var(--text-muted);
  letter-spacing: 0.05em;
}

.content-area {
  flex: 1;
  padding: 24px 32px;
  overflow-y: auto;
}

/* Responsive */
@media (max-width: 768px) {
  .sidebar {
    width: 72px;
  }

  .logo-text,
  .nav-text,
  .user-details {
    display: none;
  }

  .sidebar-header {
    justify-content: center;
    padding: 20px 12px;
  }

  .nav-item {
    justify-content: center;
    padding: 14px;
  }

  .sidebar-footer {
    flex-direction: column;
    gap: 8px;
  }

  .user-info {
    justify-content: center;
  }

  .logout-btn {
    width: 100%;
  }
}
</style>
