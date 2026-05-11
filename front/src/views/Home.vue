<template>
  <div class="layout-container">
    <aside class="sidebar">
      <div class="sidebar-header">
        <div class="logo-icon">AP</div>
        <span class="logo-text">{{ t('app.name') }}</span>
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
          <div class="user-avatar">{{ userInfo.username?.charAt(0)?.toUpperCase() || 'A' }}</div>
          <div class="user-details">
            <span class="user-name">{{ userInfo.realName || userInfo.username || 'Admin' }}</span>
            <span class="user-role">{{ t('common.admin') }}</span>
          </div>
        </div>
        <button class="logout-btn" @click="handleLogout" :title="t('common.logout')">
          <SwitchButton />
        </button>
      </div>
    </aside>

    <main class="main-content">
      <header class="main-header">
        <div class="header-left">
          <h2 class="page-title">{{ currentPageTitle }}</h2>
        </div>
        <div class="header-right">
          <div class="lang-switch">
            <button :class="{ active: locale === 'zh' }" @click="setLocale('zh')">中</button>
            <button :class="{ active: locale === 'en' }" @click="setLocale('en')">EN</button>
          </div>
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
import {
  Bell,
  Box,
  Connection,
  Cpu,
  DataAnalysis,
  DataLine,
  Finished,
  FolderOpened,
  MagicStick,
  Money,
  Monitor,
  Promotion,
  SetUp,
  SwitchButton,
  Tickets,
  User
} from '@element-plus/icons-vue'
import { useI18n } from '../i18n'

const router = useRouter()
const route = useRoute()
const { locale, t, setLocale } = useI18n()

const navItems = computed(() => [
  { path: '/dashboard', label: t('nav.overview'), icon: DataAnalysis },
  { path: '/automation', label: t('nav.automation'), icon: Promotion },
  { path: '/deploy-profiles', label: t('nav.deployProfiles'), icon: SetUp },
  { path: '/skills', label: t('nav.skills'), icon: MagicStick },
  { path: '/memories', label: t('nav.memories'), icon: Tickets },
  { path: '/agents', label: t('nav.agents'), icon: Cpu },
  { path: '/agent-quality', label: t('nav.quality'), icon: Finished },
  { path: '/rag', label: t('nav.rag'), icon: FolderOpened },
  { path: '/monitor', label: t('nav.monitor'), icon: Monitor },
  { path: '/graph', label: t('nav.graph'), icon: Connection },
  { path: '/models', label: t('nav.models'), icon: Box },
  { path: '/model-training', label: t('nav.modelTraining'), icon: DataLine },
  { path: '/billing', label: t('nav.billing'), icon: Money },
  { path: '/alerts', label: t('nav.alerts'), icon: Bell },
  { path: '/audit-logs', label: t('nav.audit'), icon: Tickets },
  { path: '/customers', label: t('nav.customers'), icon: User },
  { path: '/invoke', label: t('nav.invoke'), icon: MagicStick }
])

const currentPageTitle = computed(() => {
  const item = navItems.value.find(n => route.path.startsWith(n.path))
  return item?.label || t('common.console')
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

.sidebar {
  width: 240px;
  height: 100vh;
  background: #111827;
  border-right: 1px solid #111827;
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 100;
}

.sidebar-header {
  padding: 24px 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.logo-icon {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: 8px;
  background: #ffffff;
  color: #111827;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 800;
}

.logo-text {
  font-size: 16px;
  font-weight: 700;
  color: #f9fafb;
  letter-spacing: 0;
}

.sidebar-nav {
  flex: 1;
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 16px;
  border-radius: 8px;
  color: #cbd5e1;
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
  background: #60a5fa;
  border-radius: 0 3px 3px 0;
  transition: height 0.2s ease;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.06);
  color: #ffffff;
}

.nav-item.active {
  background: #1f2937;
  color: #ffffff;
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
  letter-spacing: 0;
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
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
  background: #2563eb;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  color: #ffffff;
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
  color: #f9fafb;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-role {
  font-size: 11px;
  color: #94a3b8;
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
}

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
  background: #ffffff;
}

.header-left {
  display: flex;
  align-items: center;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.lang-switch {
  display: inline-flex;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  overflow: hidden;
  background: #f8fafc;
}

.lang-switch button {
  min-width: 42px;
  height: 30px;
  border: 0;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  font-weight: 600;
}

.lang-switch button.active {
  background: #111827;
  color: #ffffff;
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
  background: var(--bg-base);
}

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
