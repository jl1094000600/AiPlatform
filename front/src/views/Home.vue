<template>
  <div class="layout-container">
    <aside class="sidebar">
      <div class="sidebar-header">
        <div class="logo-icon">AP</div>
        <span class="logo-text">{{ t('app.name') }}</span>
      </div>

      <nav class="sidebar-nav">
        <template v-for="item in navGroups" :key="item.key">
          <router-link
            v-if="!item.children?.length"
            :to="item.path"
            class="nav-item"
            :class="{ active: isActivePath(item.path) }"
          >
            <component :is="item.icon" class="nav-icon" />
            <span class="nav-text">{{ item.label }}</span>
          </router-link>

          <div v-else class="nav-group" :class="{ open: isGroupOpen(item), active: hasActiveChild(item) }">
            <button class="nav-group-title" type="button" @click="toggleGroup(item)">
              <component :is="item.icon" class="nav-icon" />
              <span class="nav-text">{{ item.label }}</span>
              <span class="group-caret">&gt;</span>
            </button>
            <div v-show="isGroupOpen(item)" class="nav-children">
              <router-link
                v-for="child in item.children"
                :key="child.key"
                :to="child.path"
                class="nav-item nav-child"
                :class="{ active: isActivePath(child.path) }"
              >
                <component :is="child.icon" class="nav-icon" />
                <span class="nav-text">{{ child.label }}</span>
              </router-link>
            </div>
          </div>
        </template>
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
          <el-select
            v-if="tenantOptions.length > 1"
            :model-value="currentTenantId"
            class="tenant-switch"
            size="small"
            @change="handleTenantChange"
          >
            <el-option
              v-for="tenant in tenantOptions"
              :key="tenant.id"
              :label="tenant.tenantName"
              :value="tenant.id"
            />
          </el-select>
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
  Key,
  MagicStick,
  Menu,
  Money,
  Monitor,
  OfficeBuilding,
  Promotion,
  SetUp,
  SwitchButton,
  Tickets,
  User,
  UserFilled
} from '@element-plus/icons-vue'
import { useI18n } from '../i18n'
import api from '../api'
import { filterNavByPermissions, readStoredUser } from '../utils/permissions'

const router = useRouter()
const route = useRoute()
const { locale, t, setLocale } = useI18n()

const currentUser = ref(readUser())
const openGroupKeys = ref(new Set(['group-workbench', 'group-automation', 'group-agent', 'group-operations']))
const fallbackNavGroups = computed(() => [
  {
    key: 'group-workbench',
    label: '工作台',
    icon: DataAnalysis,
    children: [
      navItem('/dashboard', t('nav.overview'), DataAnalysis)
    ]
  },
  {
    key: 'group-automation',
    label: '自动化交付',
    icon: Promotion,
    children: [
      navItem('/automation', t('nav.automation'), Promotion),
      navItem('/workflows', '工作流编排', Connection),
      navItem('/code-quality', t('nav.codeQuality'), Finished),
      navItem('/ai-output-governance', 'AI产出治理', Connection),
      navItem('/prompt-engineering', '提示词工程', MagicStick),
      navItem('/deploy-profiles', t('nav.deployProfiles'), SetUp)
    ]
  },
  {
    key: 'group-agent',
    label: 'Agent 能力',
    icon: Cpu,
    children: [
      navItem('/skills', t('nav.skills'), MagicStick),
      navItem('/memories', t('nav.memories'), Tickets),
      navItem('/agents', t('nav.agents'), Cpu),
      navItem('/agent-quality', t('nav.quality'), Finished),
      navItem('/rag', t('nav.rag'), FolderOpened),
      navItem('/models', t('nav.models'), Box),
      navItem('/model-training', t('nav.modelTraining'), DataLine)
    ]
  },
  {
    key: 'group-operations',
    label: '运营观测',
    icon: Monitor,
    children: [
      navItem('/monitor', t('nav.monitor'), Monitor),
      navItem('/graph', t('nav.graph'), Connection),
      navItem('/benchmark', '数据集测评', DataAnalysis),
      navItem('/billing', t('nav.billing'), Money),
      navItem('/alerts', t('nav.alerts'), Bell),
      navItem('/audit-logs', t('nav.audit'), Tickets),
      navItem('/customers', t('nav.customers'), User),
      navItem('/invoke', t('nav.invoke'), MagicStick)
    ]
  },
  {
    key: 'group-system',
    label: '系统管理',
    icon: Menu,
    children: [
      navItem('/tenants', '租户管理', OfficeBuilding),
      navItem('/members', '成员管理', UserFilled),
      navItem('/roles', '角色权限', Key),
      navItem('/menus', '菜单权限', Menu)
    ]
  }
])

const iconMap = {
  Bell,
  Box,
  Connection,
  Cpu,
  DataAnalysis,
  DataLine,
  Finished,
  FolderOpened,
  Key,
  MagicStick,
  Menu,
  Money,
  Monitor,
  OfficeBuilding,
  Promotion,
  SetUp,
  Tickets,
  User,
  UserFilled
}

const navGroups = computed(() => {
  const menus = currentUser.value.menus || []
  if (!menus.length) return filterNavByPermissions(fallbackNavGroups.value, currentUser.value)
  return menus.map(toNavNode).filter(item => item.path || item.children.length)
})

const currentPageTitle = computed(() => {
  const item = flattenNavGroups(navGroups.value).find(n => isActivePath(n.path))
  return item?.label || t('common.console')
})

const userInfo = computed(() => currentUser.value || {})
const tenantOptions = computed(() => currentUser.value.tenants || [])
const currentTenantId = computed(() => currentUser.value.tenant?.id)

function readUser() {
  return readStoredUser()
}

function navItem(path, label, icon) {
  const routePermissions = {
    '/dashboard': 'dashboard:view',
    '/automation': 'automation:list',
    '/workflows': 'workflow:manage',
    '/code-quality': 'code-quality:list',
    '/ai-output-governance': 'governance:list',
    '/prompt-engineering': 'prompt:list',
    '/deploy-profiles': 'automation:list',
    '/skills': 'skill:list',
    '/memories': 'agent:list',
    '/agents': 'agent:list',
    '/agent-quality': 'agent:list',
    '/rag': 'rag:list',
    '/models': 'model:list',
    '/model-training': 'model:update',
    '/monitor': 'monitor:view',
    '/graph': 'graph:manage',
    '/benchmark': 'benchmark:view',
    '/billing': 'billing:view',
    '/alerts': 'alert:view',
    '/audit-logs': 'audit:view',
    '/customers': 'customer:manage',
    '/invoke': 'agent:invoke',
    '/tenants': 'tenant:manage',
    '/members': 'member:manage',
    '/roles': 'role:manage',
    '/menus': 'menu:manage'
  }
  return { key: path, path, label, icon, permissionCode: routePermissions[path], children: [] }
}

function toNavNode(menu) {
  return {
    key: menu.menuCode || menu.path || String(menu.id),
    path: menu.path,
    label: menu.menuName,
    icon: iconMap[menu.icon] || MagicStick,
    permissionCode: menu.permissionCode,
    children: (menu.children || []).map(toNavNode).filter(item => item.path || item.children.length)
  }
}

function flattenNavGroups(items) {
  return items.flatMap(item => [
    ...(item.path ? [item] : []),
    ...flattenNavGroups(item.children || [])
  ])
}

function isActivePath(path) {
  return !!path && (route.path === path || (route.path.startsWith(path) && path !== '/'))
}

function hasActiveChild(group) {
  return flattenNavGroups(group.children || []).some(item => isActivePath(item.path))
}

function isGroupOpen(group) {
  return openGroupKeys.value.has(group.key) || hasActiveChild(group)
}

function toggleGroup(group) {
  const next = new Set(openGroupKeys.value)
  if (next.has(group.key)) {
    next.delete(group.key)
  } else {
    next.add(group.key)
  }
  openGroupKeys.value = next
}

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

const handleTenantChange = async (tenantId) => {
  if (!tenantId || tenantId === currentTenantId.value) return
  const res = await api.switchTenant({ tenantId })
  const payload = res.data.data
  localStorage.setItem('token', payload.token)
  localStorage.setItem('user', JSON.stringify(payload))
  currentUser.value = payload
  router.push('/dashboard')
}

onMounted(() => {
  currentUser.value = readUser()
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

.nav-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-group-title {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border: 0;
  border-radius: 8px;
  color: #cbd5e1;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: all 0.2s ease;
}

.nav-group-title:hover,
.nav-group.active .nav-group-title {
  background: rgba(255, 255, 255, 0.06);
  color: #ffffff;
}

.group-caret {
  margin-left: auto;
  font-size: 12px;
  color: #94a3b8;
  transform: rotate(0deg);
  transition: transform 0.2s ease;
}

.nav-group.open .group-caret {
  transform: rotate(90deg);
}

.nav-children {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding-left: 8px;
}

.nav-child {
  padding-left: 28px;
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

.tenant-switch {
  width: 150px;
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

  .nav-group-title {
    justify-content: center;
    padding: 14px;
  }

  .group-caret,
  .nav-children {
    display: none;
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
