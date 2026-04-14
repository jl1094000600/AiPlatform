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
      { path: 'agents', name: 'Agents', component: () => import('../views/AgentList.vue') },
      { path: 'monitor', name: 'Monitor', component: () => import('../views/Monitor.vue') },
      { path: 'models', name: 'Models', component: () => import('../views/ModelList.vue') }
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
    next('/agents')
  } else {
    next()
  }
})

export default router
