<template>
  <div class="page-wrap">
    <el-form inline>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable style="width: 180px" @change="loadData">
          <el-option label="未处理" value="UNPROCESSED" />
          <el-option label="运行中" value="RUNNING" />
          <el-option label="完成" value="COMPLETED" />
          <el-option label="失败" value="FAILED" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="文件名模糊查询" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
      </el-form-item>
      <el-form-item v-if="isAdmin">
        <el-upload :http-request="customUpload" :show-file-list="false">
          <el-button type="success">上传文件</el-button>
        </el-upload>
      </el-form-item>
    </el-form>

    <el-table :data="records" style="margin-top: 12px" v-loading="loading" border>
      <el-table-column prop="fileName" label="文件名" min-width="260" />
      <el-table-column label="大小" width="120">
        <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="130">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="chunkCount" label="分片数" width="100" />
      <el-table-column prop="vectorCount" label="向量数" width="100" />
      <el-table-column label="更新时间" min-width="170">
        <template #default="{ row }">{{ formatTime(row.updateTime || row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="失败原因" min-width="220">
        <template #default="{ row }">{{ row.errorMessage || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="download(row)">下载</el-button>
          <el-button
            v-if="isAdmin"
            link
            type="success"
            :disabled="row.status === 'RUNNING'"
            @click="processDoc(row)">
            {{ row.status === 'COMPLETED' ? '重处理' : '处理' }}
          </el-button>
          <el-button v-if="isAdmin" link type="danger" @click="removeDoc(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top: 12px; display: flex; justify-content: flex-end">
      <el-pagination
        background
        layout="total, prev, pager, next, sizes"
        :total="total"
        :current-page="query.pageNo"
        :page-size="query.pageSize"
        :page-sizes="[10, 20, 50]"
        @current-change="onPageChange"
        @size-change="onPageSizeChange" />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import {
  deleteKbDocumentApi,
  getKbDownloadUrlApi,
  pageKbDocumentsApi,
  processKbDocumentApi
} from '../api/knowledge'

const authStore = useAuthStore()
const isAdmin = computed(() => authStore.user?.role === 'admin')

const query = reactive({
  pageNo: 1,
  pageSize: 10,
  status: '',
  keyword: ''
})
const processForm = reactive({
  strategy: 'fixed-size',
  chunkSize: 500,
  overlapSize: 50
})
const loading = ref(false)
const records = ref([])
const total = ref(0)

const loadData = async () => {
  loading.value = true
  try {
    const res = await pageKbDocumentsApi({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      status: query.status || undefined,
      keyword: query.keyword || undefined
    })
    if (res.code !== '0') throw new Error(res.message || '查询失败')
    total.value = Number(res.data?.total || 0)
    records.value = Array.isArray(res.data?.records) ? res.data.records : []
  } catch (e) {
    ElMessage.error(`查询失败: ${e.message || e}`)
  } finally {
    loading.value = false
  }
}

const customUpload = async (options) => {
  if (!isAdmin.value) {
    ElMessage.warning('无上传权限')
    return
  }
  const formData = new FormData()
  formData.append('file', options.file)
  try {
    const headers = {}
    if (authStore.token) headers.Authorization = authStore.token
    const res = await fetch('/api/kb/documents/upload', {
      method: 'POST',
      headers,
      body: formData
    })
    const json = await res.json()
    if (!res.ok || json.code !== '0') {
      throw new Error(json.message || `HTTP ${res.status}`)
    }
    ElMessage.success('上传成功')
    options.onSuccess?.(json)
    query.pageNo = 1
    await loadData()
  } catch (e) {
    ElMessage.error(`上传失败: ${e.message || e}`)
    options.onError?.(e)
  }
}

const processDoc = async (row) => {
  try {
    await processKbDocumentApi(row.documentId, {
      strategy: processForm.strategy,
      chunkSize: processForm.chunkSize,
      overlapSize: processForm.overlapSize
    })
    ElMessage.success('已触发处理')
    await loadData()
  } catch (e) {
    ElMessage.error(`处理失败: ${e.message || e}`)
  }
}

const removeDoc = async (row) => {
  try {
    await ElMessageBox.confirm('确认删除该文件？将同步删除向量与存储文件。', '提示', { type: 'warning' })
    await deleteKbDocumentApi(row.documentId)
    ElMessage.success('删除成功')
    await loadData()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(`删除失败: ${e.message || e}`)
    }
  }
}

const download = async (row) => {
  try {
    const res = await getKbDownloadUrlApi(row.documentId)
    if (res.code !== '0') throw new Error(res.message || '获取下载链接失败')
    const url = res.data?.url
    if (!url) throw new Error('下载链接为空')
    window.open(url, '_blank')
  } catch (e) {
    ElMessage.error(`下载失败: ${e.message || e}`)
  }
}

const onPageChange = (page) => {
  query.pageNo = page
  loadData()
}

const onPageSizeChange = (size) => {
  query.pageSize = size
  query.pageNo = 1
  loadData()
}

const statusType = (status) => {
  if (status === 'COMPLETED') return 'success'
  if (status === 'RUNNING') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

const formatTime = (value) => {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return '-'
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd} ${hh}:${mi}`
}

const formatSize = (size) => {
  const n = Number(size || 0)
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`
  return `${(n / 1024 / 1024 / 1024).toFixed(1)} GB`
}

onMounted(() => {
  loadData()
})
</script>
