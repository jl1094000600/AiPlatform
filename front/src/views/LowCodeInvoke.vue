<template>
  <div class="ops-page">
    <div class="ops-header">
      <div>
        <h2>低代码调用界面</h2>
        <span class="muted">选择Agent、填写参数、执行并追溯历史</span>
      </div>
      <el-button type="primary" @click="runInvocation">执行调用</el-button>
    </div>

    <div class="ops-grid two">
      <section class="ops-panel">
        <div class="panel-title">调用配置</div>
        <el-form label-position="top">
          <el-form-item label="Agent">
            <el-select v-model="form.agentId" filterable style="width: 100%">
              <el-option v-for="agent in agents" :key="agent.id" :label="`${agent.agentName} (${agent.agentCode})`" :value="agent.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="预置模板">
            <el-select v-model="form.templateCode" style="width: 100%">
              <el-option label="通用调用" value="generic" />
              <el-option label="图像识别" value="image_recognition" />
              <el-option label="TTS播报" value="tts" />
            </el-select>
          </el-form-item>
          <el-form-item label="参数(JSON)">
            <el-input v-model="paramsText" type="textarea" :rows="10" />
          </el-form-item>
        </el-form>
        <div class="result-box">
          <span class="muted">最近结果</span>
          <pre>{{ latestResult }}</pre>
        </div>
      </section>

      <section class="ops-panel">
        <div class="panel-title">执行记录</div>
        <el-table :data="records" v-loading="loading">
          <el-table-column prop="agentCode" label="Agent" min-width="150" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }"><el-tag :type="row.status === 'SUCCESS' ? 'success' : row.status === 'FAILED' ? 'danger' : 'info'">{{ row.status }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="durationMs" label="耗时" width="90" />
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button size="small" @click="retry(row)">重试</el-button>
              <el-button size="small" @click="download(row)">下载</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const agents = ref([])
const records = ref([])
const loading = ref(false)
const latestResult = ref('')
const form = reactive({ agentId: null, templateCode: 'generic' })
const paramsText = ref('{\n  "input": "hello"\n}')

const loadAgents = async () => {
  const res = await api.getAgents({ pageNum: 1, pageSize: 100 })
  agents.value = res.data.data?.records || []
  if (!form.agentId && agents.value.length) form.agentId = agents.value[0].id
}

const loadRecords = async () => {
  loading.value = true
  try {
    const res = await api.getInvocations({ pageNum: 1, pageSize: 20 })
    records.value = res.data.data?.records || []
  } finally {
    loading.value = false
  }
}

const parseParams = () => {
  try {
    return JSON.parse(paramsText.value || '{}')
  } catch {
    ElMessage.error('参数不是合法 JSON')
    throw new Error('invalid json')
  }
}

const runInvocation = async () => {
  const res = await api.createInvocation({
    agentId: form.agentId,
    templateCode: form.templateCode,
    params: parseParams()
  })
  latestResult.value = JSON.stringify(res.data.data, null, 2)
  ElMessage.success('调用完成')
  loadRecords()
}

const retry = async (row) => {
  const res = await api.retryInvocation(row.id)
  latestResult.value = JSON.stringify(res.data.data, null, 2)
  loadRecords()
}

const download = async (row) => {
  const res = await api.downloadInvocation(row.id)
  const url = URL.createObjectURL(new Blob([res.data], { type: 'application/json' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `invocation-${row.id}.json`
  link.click()
  URL.revokeObjectURL(url)
}

onMounted(async () => {
  await loadAgents()
  await loadRecords()
})
</script>

<style scoped>
.ops-page { padding: 24px; color: var(--text-primary); }
.ops-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
.ops-header h2 { font-size: 26px; margin-bottom: 6px; }
.muted { color: var(--text-muted); }
.ops-grid.two { display: grid; grid-template-columns: 1fr 1.3fr; gap: 18px; }
.ops-panel { background: var(--glass-bg); border: 1px solid var(--glass-border); border-radius: 8px; padding: 18px; }
.panel-title { font-weight: 700; margin-bottom: 14px; }
.result-box { margin-top: 14px; border: 1px solid var(--border-color); border-radius: 8px; padding: 12px; }
pre { margin-top: 8px; white-space: pre-wrap; color: var(--accent-cyan); max-height: 260px; overflow: auto; }
</style>
