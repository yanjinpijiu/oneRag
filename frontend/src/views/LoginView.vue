<template>
  <div style="height:100%;display:flex;align-items:center;justify-content:center">
    <el-card style="width:420px">
      <el-tabs v-model="tab">
        <el-tab-pane label="登录" name="login">
          <el-form :model="loginForm">
            <el-form-item label="账号"><el-input v-model="loginForm.username" /></el-form-item>
            <el-form-item label="密码"><el-input type="password" v-model="loginForm.password" /></el-form-item>
          </el-form>
          <el-button type="primary" @click="doLogin" style="width:100%">登录</el-button>
        </el-tab-pane>
        <el-tab-pane label="注册" name="register">
          <el-form :model="registerForm">
            <el-form-item label="账号"><el-input v-model="registerForm.username" /></el-form-item>
            <el-form-item label="密码"><el-input type="password" v-model="registerForm.password" /></el-form-item>
            <el-form-item label="昵称"><el-input v-model="registerForm.nickname" /></el-form-item>
            <el-form-item label="邮箱"><el-input v-model="registerForm.email" /></el-form-item>
            <el-form-item label="手机号"><el-input v-model="registerForm.phone" /></el-form-item>
          </el-form>
          <el-button type="success" @click="doRegister" style="width:100%">注册</el-button>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { loginApi, registerApi } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const tab = ref('login')
const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ username: '', password: '', nickname: '', email: '', phone: '' })

const doLogin = async () => {
  const res = await loginApi(loginForm)
  if (res.code !== '0') return ElMessage.error(res.message || '登录失败')
  authStore.setToken(res.data?.token || '')
  authStore.setUser(res.data || null)
  ElMessage.success('登录成功')
  router.push('/chat')
}

const doRegister = async () => {
  const res = await registerApi(registerForm)
  if (res.code !== '0') return ElMessage.error(res.message || '注册失败')
  ElMessage.success('注册成功，请登录')
  tab.value = 'login'
}
</script>
