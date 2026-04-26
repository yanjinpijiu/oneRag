<template>
  <div class="page-wrap">
    <el-card>
      <template #header>个人资料</template>
      <el-form :model="profile" label-width="100px">
        <el-form-item label="昵称"><el-input v-model="profile.nickname" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="profile.email" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="profile.phone" /></el-form-item>
        <el-form-item label="头像"><el-input v-model="profile.avatar" /></el-form-item>
      </el-form>
      <el-button type="primary" @click="saveProfile">保存资料</el-button>
    </el-card>

    <el-card style="margin-top:12px">
      <template #header>修改密码</template>
      <el-form :model="pwd" label-width="100px">
        <el-form-item label="旧密码"><el-input type="password" v-model="pwd.oldPassword" /></el-form-item>
        <el-form-item label="新密码"><el-input type="password" v-model="pwd.newPassword" /></el-form-item>
      </el-form>
      <el-button type="warning" @click="changePassword">修改密码</el-button>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { changePasswordApi, getProfileApi, updateProfileApi } from '../api/profile'

const profile = reactive({ nickname: '', email: '', phone: '', avatar: '' })
const pwd = reactive({ oldPassword: '', newPassword: '' })

const loadProfile = async () => {
  const res = await getProfileApi()
  if (res.code !== '0') return ElMessage.error(res.message || '加载失败')
  Object.assign(profile, res.data || {})
}

const saveProfile = async () => {
  const res = await updateProfileApi(profile)
  if (res.code !== '0') return ElMessage.error(res.message || '保存失败')
  ElMessage.success('资料已更新')
}

const changePassword = async () => {
  const res = await changePasswordApi(pwd)
  if (res.code !== '0') return ElMessage.error(res.message || '修改失败')
  ElMessage.success('密码已修改')
  pwd.oldPassword = ''
  pwd.newPassword = ''
}

onMounted(loadProfile)
</script>
