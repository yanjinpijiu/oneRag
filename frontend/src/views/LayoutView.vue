<template>
  <el-container class="app-shell">
    <el-container>
      <el-header class="app-header">
        <div>
          <div class="app-title">OneRag</div>
          <div class="app-subtitle">校园智能知识问答平台</div>
        </div>
        <div class="top-nav">
          <el-button link class="nav-link" :class="{ 'is-active': activePath === '/chat' }" :type="activePath === '/chat' ? 'primary' : 'default'" @click="go('/chat')">聊天</el-button>
          <el-button link class="nav-link" :class="{ 'is-active': activePath === '/knowledge' }" :type="activePath === '/knowledge' ? 'primary' : 'default'" @click="go('/knowledge')">知识库</el-button>
          <el-button v-if="isAdmin" link class="nav-link" :class="{ 'is-active': activePath === '/users' }" :type="activePath === '/users' ? 'primary' : 'default'" @click="go('/users')">用户管理</el-button>
          <el-button link class="nav-link" :class="{ 'is-active': activePath === '/profile' }" :type="activePath === '/profile' ? 'primary' : 'default'" @click="go('/profile')">个人中心</el-button>
          <el-button link type="danger" @click="logout">退出登录</el-button>
          <span class="user-name">{{ displayName }}</span>
        </div>
      </el-header>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { logoutApi } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const activePath = computed(() => route.path)
const isAdmin = computed(() => authStore.user?.role === 'admin')
const displayName = computed(() => authStore.user?.nickname || authStore.user?.username || '未登录用户')

onMounted(() => {
  authStore.ensureUser()
})

const logout = async () => {
  try {
    await logoutApi()
  } finally {
    authStore.clear()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}

const go = (path) => {
  if (route.path === path) return
  router.push(path)
}
</script>

<style scoped>
.app-shell {
  height: 100%;
}

.app-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--border-soft);
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(8px);
  box-shadow: 0 6px 18px rgba(31, 42, 68, 0.04);
}

.app-title {
  font-weight: 600;
  font-size: 18px;
  color: var(--text-primary);
}

.app-subtitle {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-secondary);
}

.top-nav {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.top-nav :deep(.nav-link) {
  position: relative;
  padding-bottom: 4px;
}

.top-nav :deep(.nav-link::after) {
  content: "";
  position: absolute;
  left: 8px;
  right: 8px;
  bottom: -2px;
  height: 2px;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(79, 124, 255, 0.25) 0%, rgba(79, 124, 255, 0.92) 50%, rgba(79, 124, 255, 0.25) 100%);
  transform: scaleX(0);
  transform-origin: center;
  transition: transform 0.22s ease;
}

.top-nav :deep(.nav-link.is-active::after) {
  transform: scaleX(1);
}

.user-name {
  color: var(--text-secondary);
  font-size: 14px;
  padding-left: 8px;
  border-left: 1px solid var(--border-soft);
}

.app-main {
  background: transparent;
}
</style>
