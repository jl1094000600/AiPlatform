<template>
  <div class="page-container">
    <!-- Header -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">模型管理</h2>
        <span class="total-count mono">共 {{ total }} 个模型</span>
      </div>
      <el-button type="primary" @click="openDialog()" class="add-btn">
        <Plus class="btn-icon" /> 新增模型
      </el-button>
    </div>

    <!-- Models Grid -->
    <div class="models-grid stagger-children" v-if="models.length > 0">
      <div
        v-for="model in models"
        :key="model.id"
        class="model-card glass-card"
      >
        <div class="model-header">
          <div class="model-icon">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none">
              <rect x="3" y="3" width="18" height="18" rx="3" stroke="currentColor" stroke-width="1.5"/>
              <circle cx="12" cy="12" r="4" fill="currentColor"/>
              <circle cx="12" cy="6" r="1.5" fill="currentColor"/>
              <circle cx="12" cy="18" r="1.5" fill="currentColor"/>
            </svg>
          </div>
          <div class="model-status" :class="model.status === 1 ? 'enabled' : 'disabled'">
            {{ model.status === 1 ? '启用' : '禁用' }}
          </div>
        </div>

        <div class="model-body">
          <h3 class="model-name">{{ model.modelName }}</h3>
          <p class="model-provider mono">{{ model.provider || '-' }}</p>
          <p class="model-version mono">v{{ model.modelVersion || '1.0' }}</p>
        </div>

        <div class="model-stats">
          <div class="model-stat">
            <span class="stat-label">价格</span>
            <span class="stat-value mono">¥{{ (model.pricePer1kToken || 0).toFixed(4) }}</span>
          </div>
          <div class="model-stat">
            <span class="stat-label">单位</span>
            <span class="stat-value">/1k tokens</span>
          </div>
        </div>

        <div class="model-endpoint mono" v-if="model.endpoint">
          {{ model.endpoint }}
        </div>

        <div class="model-actions">
          <el-button size="small" @click="openDialog(model)" class="action-btn edit">
            <Edit /> 编辑
          </el-button>
          <el-button size="small" type="danger" @click="handleDelete(model)" class="action-btn delete">
            <Delete /> 删除
          </el-button>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div class="empty-state glass-card" v-else>
      <div class="empty-icon">
        <svg width="64" height="64" viewBox="0 0 24 24" fill="none">
          <rect x="3" y="3" width="18" height="18" rx="3" stroke="currentColor" stroke-width="1.5"/>
          <circle cx="12" cy="12" r="4" stroke="currentColor" stroke-width="1.5"/>
        </svg>
      </div>
      <p class="empty-text">暂无模型</p>
      <el-button type="primary" @click="openDialog()">添加第一个模型</el-button>
    </div>

    <!-- Pagination -->
    <div class="pagination-wrapper" v-if="total > pageSize">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        @current-change="loadModels"
        layout="total, prev, pager, next"
      />
    </div>

    <!-- Dialog -->
    <el-dialog
      v-model="showDialog"
      :title="form.id ? '编辑模型' : '新增模型'"
      width="520px"
      class="model-dialog"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-position="top" class="model-form">
        <el-form-item label="模型名称" required>
          <el-input v-model="form.modelName" placeholder="如: GPT-4 Turbo" />
        </el-form-item>
        <el-form-item label="模型编码" required>
          <el-input v-model="form.modelCode" placeholder="如: gpt-4-turbo" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="厂商">
          <el-select v-model="form.provider" placeholder="选择厂商" style="width: 100%">
            <el-option label="OpenAI" value="OpenAI" />
            <el-option label="Anthropic" value="Anthropic" />
            <el-option label="阿里云" value="阿里云" />
            <el-option label="百度" value="百度" />
            <el-option label="腾讯云" value="腾讯云" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="form.modelVersion" placeholder="如: v1.0" />
        </el-form-item>
        <el-form-item label="API地址">
          <el-input v-model="form.endpoint" placeholder="如: https://api.openai.com/v1" />
        </el-form-item>
        <el-form-item label="价格 (元/千Token)">
          <el-input-number
            v-model="form.pricePer1kToken"
            :precision="6"
            :step="0.0001"
            :min="0"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false" class="cancel-btn">取消</el-button>
        <el-button type="primary" @click="handleSave" class="save-btn">
          {{ form.id ? '保存修改' : '创建模型' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import api from '../api'

const models = ref([])
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const showDialog = ref(false)
const form = reactive({
  id: null,
  modelCode: '',
  modelName: '',
  provider: '',
  modelVersion: '',
  endpoint: '',
  pricePer1kToken: 0
})

const loadModels = async () => {
  try {
    const res = await api.getModels({ pageNum: pageNum.value, pageSize: pageSize.value })
    if (res.data.code === 200) {
      models.value = res.data.data.records || []
      total.value = res.data.data.total || 0
    }
  } catch (e) {
    ElMessage.error('加载模型列表失败')
  }
}

const openDialog = (model = null) => {
  if (model) {
    Object.assign(form, model)
  } else {
    Object.assign(form, {
      id: null,
      modelCode: '',
      modelName: '',
      provider: '',
      modelVersion: '',
      endpoint: '',
      pricePer1kToken: 0
    })
  }
  showDialog.value = true
}

const handleSave = async () => {
  if (!form.modelName) {
    ElMessage.warning('请填写模型名称')
    return
  }
  try {
    const fn = form.id ? api.updateModel : api.createModel
    const res = await fn(form.id, form)
    if (res.data.code === 200) {
      ElMessage.success(form.id ? '修改成功' : '创建成功')
      showDialog.value = false
      loadModels()
    } else {
      ElMessage.error(res.data.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

const handleDelete = async (model) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除模型「${model.modelName}」吗？`,
      '删除确认',
      { type: 'warning' }
    )
    await api.deleteModel(model.id)
    ElMessage.success('删除成功')
    loadModels()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

onMounted(loadModels)
</script>

<style scoped>
.page-container {
  animation: fadeInUp 0.4s ease;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.header-left {
  display: flex;
  align-items: baseline;
  gap: 16px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
}

.total-count {
  font-size: 13px;
  color: var(--text-muted);
}

.add-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  font-weight: 600;
}

.btn-icon {
  width: 16px;
  height: 16px;
}

/* Models Grid */
.models-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.model-card {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  animation: fadeInUp 0.5s ease forwards;
  opacity: 0;
}

.model-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.model-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(0, 240, 255, 0.2), rgba(139, 92, 246, 0.2));
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent-cyan);
}

.model-status {
  padding: 6px 14px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.model-status.enabled {
  background: rgba(16, 185, 129, 0.15);
  color: var(--accent-green);
}

.model-status.disabled {
  background: rgba(139, 92, 246, 0.15);
  color: var(--accent-purple);
}

.model-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.model-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.model-provider {
  font-size: 13px;
  color: var(--text-secondary);
}

.model-version {
  font-size: 12px;
  color: var(--text-muted);
}

.model-stats {
  display: flex;
  gap: 24px;
  padding: 16px;
  background: rgba(0, 240, 255, 0.03);
  border-radius: 12px;
  border: 1px solid var(--border-color);
}

.model-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-label {
  font-size: 11px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.stat-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.model-endpoint {
  font-size: 11px;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-actions {
  display: flex;
  gap: 8px;
  margin-top: auto;
}

.action-btn {
  flex: 1;
  padding: 8px 12px;
  font-size: 12px;
  border-radius: 8px;
  border: 1px solid var(--border-color);
  background: transparent;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  transition: all 0.2s ease;
}

.action-btn:hover {
  border-color: var(--accent-cyan);
  color: var(--accent-cyan);
}

.action-btn.delete:hover {
  border-color: var(--accent-red);
  color: var(--accent-red);
  background: rgba(239, 68, 68, 0.1);
}

/* Empty State */
.empty-state {
  padding: 80px 40px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  text-align: center;
}

.empty-icon {
  color: var(--text-muted);
  opacity: 0.5;
}

.empty-text {
  font-size: 16px;
  color: var(--text-secondary);
}

/* Pagination */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}

/* Dialog */
.model-dialog :deep(.el-dialog) {
  border-radius: 20px;
}

.model-form {
  padding: 8px 0;
}

.cancel-btn {
  padding: 12px 24px;
  border-radius: 10px;
}

.save-btn {
  padding: 12px 32px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--accent-cyan), var(--accent-purple));
  border: none;
  font-weight: 600;
}
</style>
