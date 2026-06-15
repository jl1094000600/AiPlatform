import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import AuthView from '@/views/AuthView.vue'
import WorkspaceView from '@/views/WorkspaceView.vue'
import { getToken } from '@/api'

const routes = [
  { path: '/', name: 'home', component: HomeView },
  { path: '/login', name: 'login', component: AuthView },
  {
    path: '/workspace',
    name: 'workspace',
    component: WorkspaceView,
    meta: { requiresAuth: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

router.beforeEach((to) => {
  const authenticated = Boolean(getToken())
  if (to.meta.requiresAuth && !authenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && authenticated) {
    return { name: 'workspace' }
  }
})

export default router
