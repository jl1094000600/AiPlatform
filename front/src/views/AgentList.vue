<template>
  <div class="agent-page">
    <div class="page-header">
      <div>
        <h2>Agent 管理</h2>
        <p>共 {{ total }} 个 Agent，可绑定已配置模型。</p>
      </div>
      <el-button type="primary" @click="openDialog()">
        <Plus class="btn-icon" /> 新增 Agent
      </el-button>
    </div>

    <section class="panel">
      <el-table :data="agents" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" align="center">
          <template #default="{ row }">#{{ row.id }}</template>
        </el-table-column>
        <el-table-column prop="agentName" label="名称" min-width="170">
          <template #default="{ row }">
            <div class="name-cell">
              <strong>{{ row.agentName }}</strong>
              <span class="mono">{{ row.agentCode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column prop="modelCode" label="模型" min-width="160">
          <template #default="{ row }">{{ row.modelCode || '-' }}</template>
        </el-table-column>
        <el-table-column prop="apiUrl" label="接口地址" min-width="220" show-overflow-tooltip />
        <el-table-column prop="runtimeStatus" label="运行状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="runtimeType(row)">{{ runtimeText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
            <el-button v-if="row.status !== 1" size="small" type="success" @click="publish(row)">发布</el-button>
            <el-button v-else size="small" type="warning" @click="offline(row)">下线</el-button>
            <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper" v-if="total > pageSize">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          @current-change="loadAgents"
          layout="total, prev, pager, next"
        />
      </div>
    </section>

    <el-dialog
      v-model="showDialog"
      :title="form.id ? '编辑 Agent' : '新增 Agent'"
      width="620px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-position="top">
        <div class="form-grid">
          <el-form-item label="Agent 编码" required>
            <el-input v-model="form.agentCode" placeholder="marketing-agent" :disabled="!!form.id" />
          </el-form-item>
          <el-form-item label="名称" required>
            <el-input v-model="form.agentName" placeholder="市场营销 Agent" />
          </el-form-item>
        </div>

        <div class="form-grid">
          <el-form-item label="分类">
            <el-input v-model="form.category" placeholder="市场营销" />
          </el-form-item>
          <el-form-item label="模型">
            <el-select v-model="form.modelId" filterable clearable style="width: 100%" @change="selectAgentModel">
              <el-option
                v-for="model in models"
                :key="model.id"
                :label="model.modelName + ' / ' + model.modelCode"
                :value="model.id"
              />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="接口地址">
          <el-input v-model="form.apiUrl" placeholder="http://localhost:8081" />
        </el-form-item>

        <el-form-item label="调用方式">
          <el-radio-group v-model="form.httpMethod">
            <el-radio value="GET">GET</el-radio>
            <el-radio value="POST">POST</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="save">{{ form.id ? '保存修改' : '创建 Agent' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import api from '../api'

const agents = ref([])
const models = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const showDialog = ref(false)
const form = reactive(defaultForm())

function defaultForm() {
  return {
    id: null,
    agentCode: '',
    agentName: '',
    category: '',
    apiUrl: '',
    httpMethod: 'POST',
    modelId: null,
    modelCode: '',
    description: '',
    status: 2
  }
}

const loadAgents = async () => {
  loading.value = true
  try {
    const [agentRes, modelRes] = await Promise.all([
      api.getAgents({ pageNum: pageNum.value, pageSize: pageSize.value }),
      api.getModels({ pageNum: 1, pageSize: 100 })
    ])
    agents.value = agentRes.data.data?.records || []
    total.value = agentRes.data.data?.total || 0
    models.value = modelRes.data.data?.records || []
  } catch (e) {
    ElMessage.error('加载 Agent 列表失败')
  } finally {
    loading.value = false
  }
}

const openDialog = (row = null) => {
  Object.assign(form, row ? { ...defaultForm(), ...row } : defaultForm())
  showDialog.value = true
}

const selectAgentModel = (modelId) => {
  const model = models.value.find(item => item.id === modelId)
  form.modelCode = model?.modelCode || ''
}

const save = async () => {
  if (!form.agentCode || !form.agentName) {
    ElMessage.warning('请填写 Agent 编码和名称')
    return
  }
  const payload = { ...form }
  const res = form.id ? await api.updateAgent(form.id, payload) : await api.createAgent(payload)
  if (res.data.code === 200) {
    ElMessage.success(form.id ? '修改成功' : '创建成功')
    showDialog.value = false
    await loadAgents()
  } else {
    ElMessage.error(res.data.message || '操作失败')
  }
}

const publish = async (row) => {
  await api.publishAgent(row.id)
  ElMessage.success('发布成功')
  await loadAgents()
}

const offline = async (row) => {
  await api.offlineAgent(row.id)
  ElMessage.success('下线成功')
  await loadAgents()
}

const remove = async (row) => {
  await ElMessageBox.confirm(`确定删除 Agent「${row.agentName}」吗？`, '删除确认', { type: 'warning' })
  await api.deleteAgent(row.id)
  ElMessage.success('删除成功')
  await loadAgents()
}

const runtimeType = (row) => {
  if (row.runtimeStatus === 'online') return 'success'
  if (row.runtimeStatus === 'error') return 'danger'
  return 'info'
}

const runtimeText = (row) => {
  if (row.runtimeStatus === 'online') return '在线'
  if (row.runtimeStatus === 'error') return '异常'
  return '离线'
}

onMounted(loadAgents)
</script>

<style scoped>
.agent-page {
  animation: fadeInUp 0.4s ease;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 24px;
  margin-bottom: 6px;
}

.page-header p {
  color: var(--text-muted);
}

.btn-icon {
  width: 16px;
  height: 16px;
}

.panel {
  background: #ffffff;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 16px;
}

.name-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.name-cell span {
  color: var(--text-muted);
  font-size: 12px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding-top: 16px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

@media (max-width: 820px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
