import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/login', component: () => import('../views/LoginView.vue') },
  {
    path: '/',
    component: () => import('../views/LayoutView.vue'),
    children: [
      { path: '', redirect: '/chat' },
      { path: '/chat', component: () => import('../views/ChatView.vue') },
      { path: '/upload', redirect: '/knowledge' },
      { path: '/knowledge', component: () => import('../views/UploadView.vue') },
      { path: '/users', component: () => import('../views/UserListView.vue'), meta: { admin: true } },
      { path: '/profile', component: () => import('../views/ProfileView.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  if (to.path === '/login') return true
  if (!authStore.token) return '/login'
  const ok = await authStore.ensureUser()
  if (!ok) return '/login'
  if (to.meta?.admin && authStore.user?.role !== 'admin') return '/chat'
  return true
})

export default router
