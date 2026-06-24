import { createRouter, createWebHistory } from 'vue-router'
import axios from 'axios'
import { hasCompleteAuthState, hasPermission, isPlatformAdmin, readStoredUser } from '../utils/permissions'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    children: [
      { path: 'dashboard', name: 'BusinessDashboard', component: () => import('../views/BusinessDashboard.vue'), meta: { permission: 'dashboard:view' } },
      { path: 'agents', name: 'Agents', component: () => import('../views/AgentList.vue'), meta: { permission: 'agent:list' } },
      { path: 'monitor', name: 'Monitor', component: () => import('../views/Monitor.vue'), meta: { permission: 'monitor:view' } },
      { path: 'graph', name: 'AgentGraph', component: () => import('../views/AgentGraph.vue'), meta: { permission: 'graph:manage' } },
      { path: 'agent-quality', name: 'AgentQuality', component: () => import('../views/AgentQualityMonitor.vue'), meta: { permission: 'agent:list' } },
      { path: 'agent-runs', name: 'AgentRuns', component: () => import('../views/AgentRunConsole.vue'), meta: { permission: 'agent:invoke' } },
      { path: 'rag', name: 'RagKnowledge', component: () => import('../views/RagKnowledge.vue'), meta: { permission: 'rag:list' } },
      { path: 'automation', name: 'AutomationPipeline', component: () => import('../views/AutomationPipeline.vue'), meta: { permission: 'automation:list' } },
      { path: 'badcases', name: 'BadCaseAnalysis', component: () => import('../views/BadCaseAnalysis.vue'), meta: { permission: 'automation:list' } },
      { path: 'platform-analytics', name: 'PlatformAnalytics', component: () => import('../views/PlatformAnalytics.vue'), meta: { permission: 'dashboard:view' } },
      { path: 'code-quality', name: 'CodeQualityStandards', component: () => import('../views/CodeQualityStandards.vue'), meta: { permission: 'code-quality:list' } },
      { path: 'ai-output-governance', name: 'AiOutputGovernance', component: () => import('../views/AiOutputGovernance.vue'), meta: { permission: 'governance:list' } },
      { path: 'prompt-engineering', name: 'PromptEngineering', component: () => import('../views/PromptEngineering.vue'), meta: { permission: 'prompt:list' } },
      { path: 'deploy-profiles', name: 'DeployProfiles', component: () => import('../views/DeployProfileManagement.vue'), meta: { permission: 'automation:list' } },
      { path: 'skills', name: 'Skills', component: () => import('../views/SkillManagement.vue'), meta: { permission: 'skill:list' } },
      { path: 'memories', name: 'UserMemories', component: () => import('../views/UserMemoryManagement.vue'), meta: { permission: 'memory:list' } },
      { path: 'models', name: 'Models', component: () => import('../views/ModelList.vue'), meta: { permission: 'model:list' } },
      { path: 'model-training', name: 'ModelTraining', component: () => import('../views/ModelTraining.vue'), meta: { permission: 'model:update' } },
      { path: 'billing', name: 'BillingCenter', component: () => import('../views/BillingCenter.vue'), meta: { permission: 'billing:view' } },
      { path: 'alerts', name: 'AlertCenter', component: () => import('../views/AlertCenter.vue'), meta: { permission: 'alert:view' } },
      { path: 'audit-logs', name: 'AuditLogs', component: () => import('../views/AuditLogs.vue'), meta: { permission: 'audit:view' } },
      { path: 'customers', name: 'Customers', component: () => import('../views/CustomerManagement.vue'), meta: { permission: 'customer:manage' } },
      { path: 'invoke', name: 'LowCodeInvoke', component: () => import('../views/LowCodeInvoke.vue'), meta: { permission: 'agent:invoke' } },
      { path: 'tenants', name: 'TenantManagement', component: () => import('../views/TenantManagement.vue'), meta: { permission: 'tenant:manage' } },
      { path: 'members', name: 'MemberManagement', component: () => import('../views/MemberManagement.vue'), meta: { permission: 'member:manage' } },
      { path: 'roles', name: 'RolePermissionManagement', component: () => import('../views/RolePermissionManagement.vue'), meta: { permission: 'role:manage' } },
      { path: 'menus', name: 'MenuPermissionManagement', component: () => import('../views/MenuPermissionManagement.vue'), meta: { permission: 'menu:manage' } },
      { path: '403', name: 'Forbidden', component: () => import('../views/Forbidden.vue') },
      { path: 'benchmark', name: 'Benchmark', component: () => import('../views/DatasetBenchmark.vue'), meta: { permission: 'benchmark:view' } },
      { path: 'workflows', name: 'Workflows', component: () => import('../views/WorkflowEditor.vue'), meta: { permission: 'workflow:manage' } },
      { path: 'workflows/list', name: 'WorkflowList', component: () => import('../views/WorkflowList.vue'), meta: { permission: 'workflow:manage' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

let authRefreshPromise = null

async function refreshAuthState(token) {
  if (!authRefreshPromise) {
    authRefreshPromise = axios.get('/api/v1/auth/me', {
      headers: { Authorization: 'Bearer ' + token }
    }).then(res => {
      const payload = res.data?.data
      if (payload) {
        localStorage.setItem('token', payload.token || token)
        localStorage.setItem('user', JSON.stringify(payload))
      }
      return payload
    }).finally(() => {
      authRefreshPromise = null
    })
  }
  return authRefreshPromise
}

// 路由守卫：未登录则重定向到登录页
function hasLegacyThinkLandTenant(user) {
  const currentName = user?.tenant?.tenantName
  const tenantNames = Array.isArray(user?.tenants) ? user.tenants.map(tenant => tenant?.tenantName) : []
  return currentName === 'Think Land' || tenantNames.includes('Think Land')
}

router.beforeEach(async (to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    next('/login')
    return
  }
  const storedUser = readStoredUser()
  if (to.path !== '/login' && token && (!hasCompleteAuthState(storedUser) || hasLegacyThinkLandTenant(storedUser))) {
    try {
      await refreshAuthState(token)
    } catch {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      next('/login')
      return
    }
  }

  const user = readStoredUser()
  if (to.path === '/' && token) {
    next('/dashboard')
  } else if (to.meta?.permission && !isPlatformAdmin(user) && !hasPermission(to.meta.permission, user)) {
    next('/403')
  } else {
    next()
  }
})

export default router
