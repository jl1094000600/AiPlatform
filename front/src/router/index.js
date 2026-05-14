import { createRouter, createWebHistory } from 'vue-router'

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
      { path: 'dashboard', name: 'BusinessDashboard', component: () => import('../views/BusinessDashboard.vue') },
      { path: 'agents', name: 'Agents', component: () => import('../views/AgentList.vue') },
      { path: 'monitor', name: 'Monitor', component: () => import('../views/Monitor.vue') },
      { path: 'graph', name: 'AgentGraph', component: () => import('../views/AgentGraph.vue') },
      { path: 'agent-quality', name: 'AgentQuality', component: () => import('../views/AgentQualityMonitor.vue') },
      { path: 'rag', name: 'RagKnowledge', component: () => import('../views/RagKnowledge.vue') },
      { path: 'automation', name: 'AutomationPipeline', component: () => import('../views/AutomationPipeline.vue') },
      { path: 'code-quality', name: 'CodeQualityStandards', component: () => import('../views/CodeQualityStandards.vue') },
      { path: 'deploy-profiles', name: 'DeployProfiles', component: () => import('../views/DeployProfileManagement.vue') },
      { path: 'skills', name: 'Skills', component: () => import('../views/SkillManagement.vue') },
      { path: 'memories', name: 'UserMemories', component: () => import('../views/UserMemoryManagement.vue') },
      { path: 'models', name: 'Models', component: () => import('../views/ModelList.vue') },
      { path: 'model-training', name: 'ModelTraining', component: () => import('../views/ModelTraining.vue') },
      { path: 'billing', name: 'BillingCenter', component: () => import('../views/BillingCenter.vue') },
      { path: 'alerts', name: 'AlertCenter', component: () => import('../views/AlertCenter.vue') },
      { path: 'audit-logs', name: 'AuditLogs', component: () => import('../views/AuditLogs.vue') },
      { path: 'customers', name: 'Customers', component: () => import('../views/CustomerManagement.vue') },
      { path: 'invoke', name: 'LowCodeInvoke', component: () => import('../views/LowCodeInvoke.vue') },
      { path: 'benchmark', name: 'Benchmark', component: () => import('../views/DatasetBenchmark.vue') },
      { path: 'workflows', name: 'Workflows', component: () => import('../views/WorkflowEditor.vue') },
      { path: 'workflows/list', name: 'WorkflowList', component: () => import('../views/WorkflowList.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：未登录则重定向到登录页
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    next('/login')
  } else if (to.path === '/' && token) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
