<template>
  <div class="ops-page">
    <div class="ops-header">
      <div>
        <h2>操作审计日志</h2>
        <span class="muted">追溯用户、资源、操作结果和时间</span>
      </div>
      <div class="actions">
        <el-button @click="exportLogs">导出</el-button>
        <el-button type="primary" @click="loadLogs">查询</el-button>
      </div>
    </div>

    <div class="filter-row">
      <el-input v-model="filters.userId" placeholder="用户ID" clearable />
      <el-input v-model="filters.operation" placeholder="操作类型" clearable />
      <el-date-picker v-model="dateRange" type="datetimerange" start-placeholder="开始时间" end-placeholder="结束时间" />
    </div>

    <section class="ops-panel">
      <el-table :data="logs" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户" width="120" />
        <el-table-column prop="operation" label="操作" width="150" />
        <el-table-column prop="resourceType" label="资源" width="130" />
        <el-table-column prop="resourceCode" label="资源编码" min-width="180" />
        <el-table-column prop="result" label="结果" width="90">
          <template #default="{ row }"><el-tag :type="row.result === 1 ? 'success' : 'danger'">{{ row.result === 1 ? '成功' : '失败' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="180" />
      </el-table>
      <el-pagination v-model:current-page="pageNum" :total="total" :page-size="pageSize" layout="total, prev, pager, next" @current-change="loadLogs" />
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const logs = ref([])
const loading = ref(false)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const dateRange = ref([])
const filters = reactive({ userId: '', operation: '' })

const formatDateTime = (value) => {
  if (!value) return undefined
  const d = new Date(value)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const params = () => ({
  pageNum: pageNum.value,
  pageSize: pageSize.value,
  userId: filters.userId || undefined,
  operation: filters.operation || undefined,
  startTime: formatDateTime(dateRange.value?.[0]),
  endTime: formatDateTime(dateRange.value?.[1])
})

const loadLogs = async () => {
  loading.value = true
  try {
    const res = await api.getAuditLogs(params())
    logs.value = res.data.data?.records || []
    total.value = res.data.data?.total || 0
  } catch {
    ElMessage.error('加载审计日志失败')
  } finally {
    loading.value = false
  }
}

const exportLogs = async () => {
  const res = await api.exportAuditLogs(params())
  const url = URL.createObjectURL(new Blob([res.data]))
  const link = document.createElement('a')
  link.href = url
  link.download = 'audit-logs.csv'
  link.click()
  URL.revokeObjectURL(url)
}

onMounted(loadLogs)
</script>

<style scoped>
.ops-page { padding: 24px; color: var(--text-primary); }
.ops-header, .actions, .filter-row { display: flex; align-items: center; gap: 12px; }
.ops-header { justify-content: space-between; margin-bottom: 18px; }
.ops-header h2 { font-size: 26px; margin-bottom: 6px; }
.muted { color: var(--text-muted); }
.filter-row { margin-bottom: 18px; }
.filter-row .el-input { width: 160px; }
.ops-panel { background: var(--glass-bg); border: 1px solid var(--glass-border); border-radius: 8px; padding: 18px; }
.el-pagination { margin-top: 16px; justify-content: flex-end; }
</style>
