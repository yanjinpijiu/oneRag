<template>
  <el-container style="height: 100%">
    <el-container>
      <el-header class="app-header">
        <div class="app-title">Onerag</div>
        <div class="top-nav">
          <el-button link :type="activePath === '/chat' ? 'primary' : 'default'" @click="go('/chat')">聊天</el-button>
          <el-button link :type="activePath === '/knowledge' ? 'primary' : 'default'" @click="go('/knowledge')">知识库</el-button>
          <el-button v-if="isAdmin" link :type="activePath === '/users' ? 'primary' : 'default'" @click="go('/users')">用户管理</el-button>
          <el-button link :type="activePath === '/profile' ? 'primary' : 'default'" @click="go('/profile')">个人中心</el-button>
          <el-button link type="danger" @click="logout">退出登录</el-button>
          <span class="user-name">{{ displayName }}</span>
        </div>
      </el-header>
      <el-main>
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
.app-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #ebeef5;
}

.app-title {
  font-weight: 600;
  font-size: 18px;
}

.top-nav {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-name {
  color: #606266;
  font-size: 14px;
}
</style>
