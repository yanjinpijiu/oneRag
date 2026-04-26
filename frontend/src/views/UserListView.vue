<template>
  <div class="page-wrap">
    <el-form inline>
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="账号/昵称/邮箱/手机号" />
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="query.role" clearable style="width:120px">
          <el-option label="user" value="user" />
          <el-option label="admin" value="admin" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable style="width:120px">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadUsers">查询</el-button>
      </el-form-item>
      <el-form-item>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
      <el-form-item>
        <el-button type="success" @click="openCreate">新增用户</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="records" style="width:100%;margin-top:12px">
      <el-table-column prop="username" label="账号" />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column prop="email" label="邮箱" />
      <el-table-column prop="phone" label="手机号" />
      <el-table-column prop="role" label="角色" />
      <el-table-column prop="status" label="状态" />
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top:12px"
      background
      layout="prev, pager, next, total"
      :total="total"
      :page-size="query.pageSize"
      :current-page="query.pageNo"
      @current-change="onPageChange" />

    <el-dialog v-model="dialogVisible" :title="form.userId ? '编辑用户' : '新增用户'">
      <el-form :model="form" label-width="100px">
        <el-form-item label="账号" v-if="!form.userId"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="密码" v-if="!form.userId"><el-input type="password" v-model="form.password" /></el-form-item>
        <el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role">
            <el-option label="user" value="user" />
            <el-option label="admin" value="admin" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createUserApi, deleteUserApi, pageUsersApi, updateUserApi } from '../api/users'

const query = reactive({ pageNo: 1, pageSize: 10, keyword: '', status: undefined, role: '' })
const total = ref(0)
const records = ref([])
const dialogVisible = ref(false)
const form = reactive({
  userId: '',
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: '',
  role: 'user',
  status: 1
})

const resetForm = () => {
  Object.assign(form, { userId: '', username: '', password: '', nickname: '', email: '', phone: '', role: 'user', status: 1 })
}

const loadUsers = async () => {
  const res = await pageUsersApi(query)
  if (res.code !== '0') return ElMessage.error(res.message || '查询失败')
  total.value = res.data?.total || 0
  records.value = res.data?.records || []
}

const resetQuery = () => {
  query.pageNo = 1
  query.pageSize = 10
  query.keyword = ''
  query.status = undefined
  query.role = ''
  loadUsers()
}

const onPageChange = (page) => {
  query.pageNo = page
  loadUsers()
}

const openCreate = () => {
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row) => {
  Object.assign(form, row)
  dialogVisible.value = true
}

const submit = async () => {
  if (!form.userId) {
    if (!form.username || form.username.trim().length < 3) {
      return ElMessage.warning('账号至少3位')
    }
    if (!form.password || form.password.length < 6) {
      return ElMessage.warning('密码至少6位')
    }
  }
  if (!form.nickname || !form.nickname.trim()) {
    return ElMessage.warning('昵称不能为空')
  }
  const res = form.userId
    ? await updateUserApi(form.userId, form)
    : await createUserApi(form)
  if (res.code !== '0') return ElMessage.error(res.message || '保存失败')
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadUsers()
}

const remove = async (row) => {
  await ElMessageBox.confirm('确认删除该用户？', '提示', { type: 'warning' })
  const res = await deleteUserApi(row.userId)
  if (res.code !== '0') return ElMessage.error(res.message || '删除失败')
  ElMessage.success('删除成功')
  loadUsers()
}

onMounted(loadUsers)
</script>
