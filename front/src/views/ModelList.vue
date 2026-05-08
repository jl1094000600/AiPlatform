<template>
  <div class="model-page">
    <div class="page-header">
      <div>
        <h2>{{ t('model.title') }}</h2>
        <p>{{ t('model.subtitle') }}</p>
      </div>
      <el-button type="primary" @click="openDialog()">
        <Plus class="btn-icon" /> {{ t('model.add') }}
      </el-button>
    </div>

    <div v-if="models.length > 0" class="model-grid">
      <div v-for="model in models" :key="model.id" class="model-card">
        <div class="card-head">
          <div>
            <h3>{{ model.modelName }}</h3>
            <span class="mono">{{ model.modelCode }}</span>
          </div>
          <el-tag :type="model.status === 1 ? 'success' : 'info'">
            {{ model.status === 1 ? t('model.enabled') : t('model.disabled') }}
          </el-tag>
        </div>

        <div class="meta-grid">
          <div>
            <span>{{ t('model.provider') }}</span>
            <strong>{{ model.provider || '-' }}</strong>
          </div>
          <div>
            <span>{{ t('model.sdkType') }}</span>
            <strong>{{ model.sdkType || 'openai-compatible' }}</strong>
          </div>
          <div>
            <span>temperature</span>
            <strong>{{ model.defaultTemperature ?? '-' }}</strong>
          </div>
          <div>
            <span>max tokens</span>
            <strong>{{ model.maxTokens || '-' }}</strong>
          </div>
        </div>

        <div class="endpoint mono">{{ model.endpoint || '-' }}</div>

        <div class="model-actions">
          <el-button size="small" @click="openDialog(model)">
            <Edit /> {{ t('common.save') }}
          </el-button>
          <el-button size="small" type="danger" plain @click="handleDelete(model)">
            <Delete /> {{ t('model.delete') }}
          </el-button>
        </div>
      </div>
    </div>

    <div v-else class="empty-state">
      <p>{{ t('model.empty') }}</p>
      <el-button type="primary" @click="openMiniMaxPreset">{{ t('model.addMiniMax') }}</el-button>
    </div>

    <div class="pagination-wrapper" v-if="total > pageSize">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        @current-change="loadModels"
        layout="total, prev, pager, next"
      />
    </div>

    <el-dialog
      v-model="showDialog"
      :title="form.id ? t('model.edit') : t('model.add')"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-alert
        class="doc-note"
        type="info"
        show-icon
        :closable="false"
        :title="t('model.minimaxHint')"
      />

      <el-form :model="form" label-position="top" class="model-form">
        <div class="form-grid">
          <el-form-item :label="t('model.name')" required>
            <el-input v-model="form.modelName" placeholder="MiniMax M2.7" />
          </el-form-item>
          <el-form-item :label="t('model.code')" required>
            <el-input v-model="form.modelCode" placeholder="MiniMax-M2.7" :disabled="!!form.id" />
          </el-form-item>
        </div>

        <div class="form-grid">
          <el-form-item :label="t('model.provider')">
            <el-select v-model="form.provider" filterable allow-create style="width: 100%">
              <el-option label="MiniMax" value="MiniMax" />
              <el-option label="OpenAI" value="OpenAI" />
              <el-option label="Anthropic" value="Anthropic" />
              <el-option label="Aliyun" value="Aliyun" />
              <el-option label="Baidu" value="Baidu" />
              <el-option label="Tencent" value="Tencent" />
              <el-option label="Other" value="Other" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('model.sdkType')">
            <el-select v-model="form.sdkType" style="width: 100%">
              <el-option label="OpenAI Compatible" value="openai-compatible" />
              <el-option label="Native REST" value="native-rest" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item :label="t('model.baseUrl')" required>
          <el-input v-model="form.endpoint" placeholder="https://api.minimaxi.com/v1" />
        </el-form-item>

        <el-form-item :label="t('model.apiKey')">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="MiniMax API Key"
          />
        </el-form-item>

        <div class="form-grid">
          <el-form-item :label="t('model.version')">
            <el-input v-model="form.modelVersion" placeholder="v1" />
          </el-form-item>
          <el-form-item :label="t('model.apiVersion')">
            <el-input v-model="form.apiVersion" placeholder="2025-01-01" />
          </el-form-item>
        </div>

        <div class="form-grid">
          <el-form-item label="temperature">
            <el-input-number
              v-model="form.defaultTemperature"
              :precision="2"
              :step="0.1"
              :min="0.01"
              :max="1"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="max tokens">
            <el-input-number v-model="form.maxTokens" :min="1" :max="200000" style="width: 100%" />
          </el-form-item>
        </div>

        <div class="form-grid">
          <el-form-item :label="t('model.price')">
            <el-input-number
              v-model="form.pricePer1kToken"
              :precision="6"
              :step="0.0001"
              :min="0"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item :label="t('common.status')">
            <el-switch v-model="enabled" />
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="showDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button @click="openMiniMaxPreset">{{ t('model.useMiniMaxPreset') }}</el-button>
        <el-button type="primary" @click="handleSave">
          {{ form.id ? t('model.saveChanges') : t('model.create') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import api from '../api'
import { useI18n } from '../i18n'

const { t } = useI18n()

const models = ref([])
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const showDialog = ref(false)
const enabled = ref(true)
const form = reactive(defaultForm())

function defaultForm() {
  return {
    id: null,
    modelCode: '',
    modelName: '',
    provider: '',
    modelVersion: '',
    endpoint: '',
    sdkType: 'openai-compatible',
    apiKey: '',
    defaultTemperature: 1,
    maxTokens: 4096,
    apiVersion: '',
    pricePer1kToken: 0,
    status: 1
  }
}

const loadModels = async () => {
  try {
    const res = await api.getModels({ pageNum: pageNum.value, pageSize: pageSize.value })
    if (res.data.code === 200) {
      models.value = res.data.data.records || []
      total.value = res.data.data.total || 0
    }
  } catch (e) {
    ElMessage.error(t('model.loadFailed'))
  }
}

const openDialog = (model = null) => {
  Object.assign(form, model ? { ...defaultForm(), ...model } : defaultForm())
  enabled.value = form.status === 1
  showDialog.value = true
}

const openMiniMaxPreset = () => {
  Object.assign(form, {
    ...form,
    modelCode: form.modelCode || 'MiniMax-M2.7',
    modelName: form.modelName || 'MiniMax M2.7',
    provider: 'MiniMax',
    modelVersion: form.modelVersion || 'v1',
    endpoint: 'https://api.minimaxi.com/v1',
    sdkType: 'openai-compatible',
    defaultTemperature: 1,
    maxTokens: form.maxTokens || 4096,
    status: 1
  })
  enabled.value = true
  showDialog.value = true
}

const handleSave = async () => {
  if (!form.modelName || !form.modelCode || !form.endpoint) {
    ElMessage.warning(t('model.required'))
    return
  }
  form.status = enabled.value ? 1 : 0
  try {
    const payload = { ...form }
    const res = form.id ? await api.updateModel(form.id, payload) : await api.createModel(payload)
    if (res.data.code === 200) {
      ElMessage.success(form.id ? t('model.updated') : t('model.created'))
      showDialog.value = false
      loadModels()
    } else {
      ElMessage.error(res.data.message || t('model.operationFailed'))
    }
  } catch (e) {
    ElMessage.error(t('model.operationFailed'))
  }
}

const handleDelete = async (model) => {
  try {
    await ElMessageBox.confirm(t('model.deleteConfirm').replace('{name}', model.modelName), t('model.deleteTitle'), {
      type: 'warning'
    })
    await api.deleteModel(model.id)
    ElMessage.success(t('model.deleted'))
    loadModels()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('model.deleteFailed'))
  }
}

onMounted(loadModels)
</script>

<style scoped>
.model-page {
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

.model-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.model-card {
  background: #ffffff;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.card-head h3 {
  font-size: 18px;
  margin-bottom: 4px;
}

.card-head span {
  color: var(--text-muted);
  font-size: 12px;
}

.meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.meta-grid div {
  background: #f8fafc;
  border-radius: 8px;
  padding: 10px;
}

.meta-grid span {
  display: block;
  color: var(--text-muted);
  font-size: 12px;
  margin-bottom: 4px;
}

.meta-grid strong {
  font-size: 13px;
  color: var(--text-primary);
}

.endpoint {
  font-size: 12px;
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

.empty-state {
  background: #ffffff;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 64px 24px;
  text-align: center;
}

.empty-state p {
  color: var(--text-muted);
  margin-bottom: 14px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}

.doc-note {
  margin-bottom: 16px;
}

.model-form {
  padding-top: 4px;
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
