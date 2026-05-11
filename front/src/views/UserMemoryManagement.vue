<template>
  <div class="ops-page">
    <div class="ops-header">
      <div>
        <h2>用户记忆管理</h2>
        <span class="muted">查看当前用户短时记忆、压缩后的长期记忆，以及流水线生成留下的上下文</span>
      </div>
      <div class="actions">
        <el-button @click="loadAll">刷新</el-button>
        <el-button type="warning" @click="compressNow">立即压缩</el-button>
        <el-button type="danger" plain @click="clearShortTerm">清空短时记忆</el-button>
      </div>
    </div>

    <div class="filter-row">
      <el-input v-model="filters.userKey" placeholder="user:1 / username:admin" clearable />
      <el-input v-model="filters.username" placeholder="用户名" clearable />
      <el-button type="primary" @click="loadAll">查询</el-button>
    </div>

    <div class="metric-grid">
      <div class="metric-card">
        <span>记忆用户</span>
        <strong class="mono">{{ effectiveUserKey }}</strong>
        <small>所有 Redis key 与 MySQL 记录都按此隔离</small>
      </div>
      <div class="metric-card">
        <span>短时记忆</span>
        <strong class="mono">{{ shortMemories.length }}</strong>
        <small>Redis 中待压缩条数</small>
      </div>
      <div class="metric-card">
        <span>长期记忆</span>
        <strong class="mono">{{ compressedPage.total || 0 }}</strong>
        <small>MySQL 压缩摘要数</small>
      </div>
    </div>

    <div class="ops-grid two">
      <section class="ops-panel">
        <div class="panel-title">短时记忆 Redis</div>
        <el-empty v-if="shortMemories.length === 0" description="暂无短时记忆" />
        <div v-else class="memory-list">
          <article v-for="item in shortMemories" :key="item.memoryId || item.createTime" class="memory-item">
            <div class="memory-head">
              <strong>{{ item.stage || 'PIPELINE' }}</strong>
              <span class="mono">{{ item.createTime }}</span>
            </div>
            <p>{{ item.outputSummary || item.inputSummary }}</p>
            <div class="memory-meta">
              <el-tag size="small">pipeline {{ item.pipelineId || '-' }}</el-tag>
              <el-tag size="small" type="info">{{ item.totalTokens || 0 }} Token</el-tag>
            </div>
          </article>
        </div>
      </section>

      <section class="ops-panel">
        <div class="panel-title">长期记忆 MySQL</div>
        <el-table :data="compressedPage.records || []" size="small" class="memory-table">
          <el-table-column prop="memoryCode" label="记忆编码" min-width="150" />
          <el-table-column prop="username" label="用户" width="110" />
          <el-table-column prop="rawCount" label="原始条数" width="90" />
          <el-table-column prop="compressionModel" label="压缩模型" width="130" />
          <el-table-column prop="createTime" label="创建时间" width="170" />
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button link type="primary" @click="selectedMemory = row">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="compressedPage.total > page.pageSize"
          class="pager"
          layout="prev, pager, next"
          :total="compressedPage.total"
          :page-size="page.pageSize"
          v-model:current-page="page.pageNum"
          @current-change="loadCompressed"
        />
      </section>
    </div>

    <el-drawer v-model="memoryDrawerVisible" title="长期记忆详情" size="46%">
      <template v-if="selectedMemory">
        <div class="detail-meta">
          <el-tag>{{ selectedMemory.userKey }}</el-tag>
          <el-tag type="info">{{ selectedMemory.rawCount }} 条压缩</el-tag>
          <el-tag type="success">{{ selectedMemory.compressionModel }}</el-tag>
        </div>
        <pre class="summary-content">{{ selectedMemory.summaryContent }}</pre>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const filters = reactive({ userKey: '', username: '' })
const page = reactive({ pageNum: 1, pageSize: 20 })
const shortMemories = ref([])
const compressedPage = ref({ records: [], total: 0 })
const selectedMemory = ref(null)

const currentUser = computed(() => {
  try {
    return JSON.parse(localStorage.getItem('user') || '{}')
  } catch {
    return {}
  }
})

const effectiveUserKey = computed(() => {
  if (filters.userKey) return filters.userKey
  if (currentUser.value.userId) return `user:${currentUser.value.userId}`
  if (currentUser.value.username) return `username:${currentUser.value.username}`
  return 'system:unknown'
})

const memoryDrawerVisible = computed({
  get: () => Boolean(selectedMemory.value),
  set: value => {
    if (!value) selectedMemory.value = null
  }
})

const params = () => ({
  userKey: effectiveUserKey.value,
  username: filters.username || undefined,
  pageNum: page.pageNum,
  pageSize: page.pageSize
})

const loadShortTerm = async () => {
  const res = await api.getShortTermMemories({ userKey: effectiveUserKey.value })
  shortMemories.value = res.data.data || []
}

const loadCompressed = async () => {
  const res = await api.getUserMemories(params())
  compressedPage.value = res.data.data || { records: [], total: 0 }
}

const loadAll = async () => {
  try {
    await Promise.all([loadShortTerm(), loadCompressed()])
  } catch {
    ElMessage.error('加载用户记忆失败')
  }
}

const compressNow = async () => {
  await api.compressUserMemories({ userKey: effectiveUserKey.value })
  ElMessage.success('压缩完成')
  await loadAll()
}

const clearShortTerm = async () => {
  await api.clearShortTermMemories({ userKey: effectiveUserKey.value })
  ElMessage.success('短时记忆已清空')
  await loadAll()
}

watch(() => filters.userKey, () => {
  page.pageNum = 1
})

onMounted(loadAll)
</script>

<style scoped>
.ops-page { padding: 24px; color: var(--text-primary); }
.ops-header, .actions, .filter-row { display: flex; align-items: center; gap: 12px; }
.ops-header { justify-content: space-between; margin-bottom: 18px; }
.ops-header h2 { font-size: 26px; margin-bottom: 6px; }
.muted { color: var(--text-muted); }
.filter-row { margin-bottom: 18px; flex-wrap: wrap; }
.filter-row .el-input { width: 240px; }
.metric-grid { display: grid; grid-template-columns: 1.3fr .8fr .8fr; gap: 14px; margin-bottom: 18px; }
.metric-card, .ops-panel { background: var(--glass-bg); border: 1px solid var(--glass-border); border-radius: 8px; padding: 18px; }
.metric-card strong { display: block; margin-top: 8px; color: var(--accent-cyan); font-size: 24px; overflow-wrap: anywhere; }
.metric-card small { color: var(--text-muted); display: block; margin-top: 6px; }
.ops-grid.two { display: grid; grid-template-columns: .9fr 1.1fr; gap: 18px; }
.panel-title { font-weight: 700; margin-bottom: 14px; }
.memory-list { display: flex; flex-direction: column; gap: 12px; max-height: 620px; overflow: auto; }
.memory-item { border: 1px solid var(--border-color); border-radius: 8px; padding: 12px; }
.memory-head, .memory-meta { display: flex; justify-content: space-between; gap: 10px; align-items: center; }
.memory-head span { color: var(--text-muted); font-size: 12px; }
.memory-item p { margin: 10px 0; color: var(--text-secondary); line-height: 1.6; max-height: 120px; overflow: hidden; }
.memory-meta { justify-content: flex-start; }
.pager { margin-top: 14px; justify-content: flex-end; }
.detail-meta { display: flex; gap: 8px; margin-bottom: 12px; }
.summary-content { white-space: pre-wrap; line-height: 1.7; background: #0f172a; color: #e5e7eb; border-radius: 8px; padding: 14px; min-height: 360px; }
@media (max-width: 1200px) {
  .metric-grid, .ops-grid.two { grid-template-columns: 1fr; }
}
@media (max-width: 760px) {
  .ops-page { padding: 16px; }
  .ops-header { align-items: flex-start; flex-direction: column; }
  .filter-row .el-input { width: 100%; }
}
</style>
