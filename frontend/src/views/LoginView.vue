<template>
  <div class="login-page" @mousemove="handleMouseMove">
    <div class="login-bg" aria-hidden="true">
      <div class="bg-gradient" :style="bgGradientStyle"></div>
      <div class="bg-grid"></div>
      <div class="bg-spotlight" :style="spotlightStyle"></div>
      <div class="bg-orb orb-a" :style="orbAStyle"></div>
      <div class="bg-orb orb-b" :style="orbBStyle"></div>
      <div class="bg-orb orb-c" :style="orbCStyle"></div>
    </div>
    <div class="login-shell" :style="shellStyle">
      <section class="brand-panel">
        <div class="brand-badge">OneRag</div>
        <h1>校园智能知识问答平台</h1>
        <p class="brand-intro">
          基于知识库检索与流式对话，为师生提供稳定、清晰、可追溯的问答体验。
        </p>
        <ul class="brand-points">
          <li>多轮对话 + 历史会话管理</li>
          <li>知识库检索 + 引用依据展示</li>
          <li>实时流式回答，低延迟反馈</li>
        </ul>
      </section>
      <el-card class="login-card" shadow="hover" :style="loginCardStyle">
        <el-tabs v-model="tab">
          <el-tab-pane label="登录" name="login">
            <el-form :model="loginForm">
              <el-form-item label="账号"><el-input v-model="loginForm.username" /></el-form-item>
              <el-form-item label="密码"><el-input type="password" v-model="loginForm.password" show-password /></el-form-item>
            </el-form>
            <el-button type="primary" @click="doLogin" style="width:100%">登录</el-button>
          </el-tab-pane>
          <el-tab-pane label="注册" name="register">
            <el-form :model="registerForm">
              <el-form-item label="账号"><el-input v-model="registerForm.username" /></el-form-item>
              <el-form-item label="密码"><el-input type="password" v-model="registerForm.password" show-password /></el-form-item>
              <el-form-item label="昵称"><el-input v-model="registerForm.nickname" /></el-form-item>
              <el-form-item label="邮箱"><el-input v-model="registerForm.email" /></el-form-item>
              <el-form-item label="手机号"><el-input v-model="registerForm.phone" /></el-form-item>
            </el-form>
            <el-button type="success" @click="doRegister" style="width:100%">注册</el-button>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { loginApi, registerApi } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const tab = ref('login')
const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ username: '', password: '', nickname: '', email: '', phone: '' })
const pointerX = ref(0.5)
const pointerY = ref(0.5)
const smoothX = ref(0.5)
const smoothY = ref(0.5)
let rafId = 0

const clamp01 = (n) => Math.max(0, Math.min(1, n))

const handleMouseMove = (e) => {
  const rect = e.currentTarget?.getBoundingClientRect?.()
  if (!rect || !rect.width || !rect.height) return
  pointerX.value = clamp01((e.clientX - rect.left) / rect.width)
  pointerY.value = clamp01((e.clientY - rect.top) / rect.height)
}

const animatePointer = () => {
  smoothX.value += (pointerX.value - smoothX.value) * 0.08
  smoothY.value += (pointerY.value - smoothY.value) * 0.08
  rafId = requestAnimationFrame(animatePointer)
}

onMounted(() => {
  rafId = requestAnimationFrame(animatePointer)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(rafId)
})

const shellStyle = computed(() => ({
  transform: `perspective(1200px) rotateX(${(0.5 - smoothY.value) * 4.2}deg) rotateY(${(smoothX.value - 0.5) * 6.5}deg) translate3d(${(smoothX.value - 0.5) * 8}px, ${(smoothY.value - 0.5) * 6}px, 0)`
}))

const bgGradientStyle = computed(() => ({
  transform: `translate3d(${(smoothX.value - 0.5) * 54}px, ${(smoothY.value - 0.5) * 46}px, 0) scale(1.08)`
}))

const spotlightStyle = computed(() => ({
  background: `radial-gradient(circle at ${(smoothX.value * 100).toFixed(2)}% ${(smoothY.value * 100).toFixed(2)}%, rgba(108,170,255,0.95) 0%, rgba(58,122,255,0.74) 10%, rgba(74,142,255,0.46) 22%, rgba(98,166,255,0.26) 36%, rgba(80,142,255,0.12) 48%, rgba(70,130,255,0) 62%)`
}))

const orbAStyle = computed(() => ({
  transform: `translate(${(smoothX.value - 0.5) * 68}px, ${(smoothY.value - 0.5) * 52}px)`
}))

const orbBStyle = computed(() => ({
  transform: `translate(${(0.5 - smoothX.value) * 56}px, ${(0.5 - smoothY.value) * 44}px)`
}))

const orbCStyle = computed(() => ({
  transform: `translate(${(smoothX.value - 0.5) * 40}px, ${(0.5 - smoothY.value) * 62}px)`
}))

const loginCardStyle = computed(() => ({
  '--sheen-x': `${(smoothX.value * 100).toFixed(2)}%`,
  '--sheen-y': `${(smoothY.value * 100).toFixed(2)}%`,
  '--sheen-rot': `${((smoothX.value - 0.5) * 14).toFixed(2)}deg`
}))

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

<style scoped>
.login-page {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  position: relative;
  overflow: hidden;
}

.login-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.bg-gradient {
  position: absolute;
  inset: -12%;
  background:
    radial-gradient(circle at 20% 15%, rgba(72, 127, 255, 0.24) 0%, rgba(72, 127, 255, 0) 54%),
    radial-gradient(circle at 80% 20%, rgba(61, 153, 255, 0.17) 0%, rgba(61, 153, 255, 0) 56%),
    radial-gradient(circle at 50% 85%, rgba(104, 144, 255, 0.2) 0%, rgba(104, 144, 255, 0) 56%);
  animation: gradientMove 6.2s ease-in-out infinite alternate;
  transition: transform 0.12s linear;
}

.bg-grid {
  position: absolute;
  inset: 0;
  opacity: 0.25;
  background-image:
    linear-gradient(to right, rgba(96, 117, 157, 0.1) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(96, 117, 157, 0.1) 1px, transparent 1px);
  background-size: 28px 28px;
  mask-image: radial-gradient(circle at center, #000 35%, transparent 80%);
}

.bg-spotlight {
  position: absolute;
  inset: 0;
  transition: background 0.08s linear;
  mix-blend-mode: screen;
  filter: saturate(1.35) brightness(1.15);
  animation: spotlightPulse 2.8s ease-in-out infinite;
}

.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(2px);
  transition: transform 0.1s linear;
}

.orb-a {
  width: 320px;
  height: 320px;
  top: -70px;
  left: -90px;
  background: radial-gradient(circle, rgba(80, 126, 255, 0.34) 0%, rgba(80, 126, 255, 0) 72%);
  animation: orbFloatA 8s ease-in-out infinite;
}

.orb-b {
  width: 300px;
  height: 300px;
  top: 8%;
  right: -80px;
  background: radial-gradient(circle, rgba(79, 160, 255, 0.28) 0%, rgba(79, 160, 255, 0) 70%);
  animation: orbFloatB 10s ease-in-out infinite;
}

.orb-c {
  width: 260px;
  height: 260px;
  bottom: -60px;
  left: 42%;
  background: radial-gradient(circle, rgba(120, 150, 255, 0.26) 0%, rgba(120, 150, 255, 0) 70%);
  animation: orbFloatC 11s ease-in-out infinite;
}

.login-shell {
  width: min(1040px, 100%);
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 18px;
  position: relative;
  z-index: 2;
  transition: transform 0.22s ease;
}

.brand-panel {
  border: 1px solid rgba(228, 234, 243, 0.9);
  background: linear-gradient(160deg, rgba(248, 251, 255, 0.92) 0%, rgba(243, 247, 255, 0.9) 100%);
  backdrop-filter: blur(6px);
  border-radius: 16px;
  padding: 28px;
  box-shadow: 0 14px 28px rgba(31, 42, 68, 0.08);
}

.brand-badge {
  display: inline-block;
  padding: 6px 10px;
  background: var(--brand-soft);
  color: var(--brand);
  border-radius: 999px;
  font-weight: 600;
  font-size: 13px;
}

.brand-panel h1 {
  margin: 14px 0 10px;
  font-size: 30px;
  line-height: 1.25;
  color: var(--text-primary);
}

.brand-intro {
  margin: 0;
  color: var(--text-regular);
  line-height: 1.8;
}

.brand-points {
  margin: 22px 0 0;
  padding-left: 20px;
  color: var(--text-regular);
  line-height: 2;
}

.login-card {
  border-radius: 16px;
  border: 1px solid rgba(228, 234, 243, 0.92);
  backdrop-filter: blur(8px);
  box-shadow: 0 16px 32px rgba(31, 42, 68, 0.1);
  animation: cardFloat 5.2s ease-in-out infinite;
  position: relative;
  overflow: hidden;
}

.login-card::before {
  content: "";
  position: absolute;
  inset: 0;
  border-radius: inherit;
  pointer-events: none;
  background:
    radial-gradient(circle at var(--sheen-x, 50%) var(--sheen-y, 40%), rgba(255, 255, 255, 0.72) 0%, rgba(255, 255, 255, 0.24) 22%, rgba(255, 255, 255, 0) 52%),
    linear-gradient(calc(120deg + var(--sheen-rot, 0deg)), rgba(255, 255, 255, 0.42) 0%, rgba(255, 255, 255, 0) 36%);
  opacity: 0.92;
  transition: background 0.1s linear;
}

.login-card::after {
  content: "";
  position: absolute;
  inset: 1px;
  border-radius: 15px;
  pointer-events: none;
  border: 1px solid rgba(255, 255, 255, 0.62);
  mix-blend-mode: screen;
}

@keyframes gradientMove {
  0% {
    filter: saturate(1);
  }
  100% {
    filter: saturate(1.08);
  }
}

@keyframes orbFloatA {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-14px);
  }
}

@keyframes orbFloatB {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(12px);
  }
}

@keyframes orbFloatC {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

@keyframes cardFloat {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-4px);
  }
}

@keyframes spotlightPulse {
  0%, 100% {
    opacity: 0.88;
  }
  50% {
    opacity: 1;
  }
}

@media (max-width: 900px) {
  .login-shell {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    padding: 20px;
  }

  .brand-panel h1 {
    font-size: 24px;
  }
}
</style>
