<template>
  <div class="quality-page">
    <div class="page-head">
      <div>
        <h2>代码质量标准</h2>
        <p>维护生成代码的评估标准、规则和质量门禁，供自动化流水线可选使用。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="loadStandards">刷新</el-button>
        <el-button type="primary" @click="openCreate">新增标准</el-button>
      </div>
    </div>

    <section class="panel">
      <el-table :data="standards" v-loading="loading" stripe>
        <el-table-column prop="standardName" label="标准名称" min-width="190" />
        <el-table-column prop="standardCode" label="编码" min-width="180" />
        <el-table-column prop="language" label="语言" width="110" />
        <el-table-column prop="framework" label="框架" width="150" show-overflow-tooltip />
        <el-table-column label="规则" width="90">
          <template #default="{ row }">{{ row.rules?.length || 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" plain @click="deleteStandard(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑代码质量标准' : '新增代码质量标准'"
      width="920px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-position="top">
        <div class="form-grid">
          <el-form-item label="标准名称" required>
            <el-input v-model="form.standardName" placeholder="Java/Spring/Vue 默认代码标准" />
          </el-form-item>
          <el-form-item label="标准编码">
            <el-input v-model="form.standardCode" placeholder="留空自动生成" :disabled="!!editingId" />
          </el-form-item>
        </div>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="语言">
            <el-input v-model="form.language" placeholder="JAVA / VUE / GENERAL" />
          </el-form-item>
          <el-form-item label="框架">
            <el-input v-model="form.framework" placeholder="Spring Boot / Vue" />
          </el-form-item>
        </div>
        <el-form-item label="状态">
          <el-switch v-model="enabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>
        <el-form-item label="质量门禁 JSON">
          <el-input v-model="form.gateConfig" type="textarea" :rows="4" />
        </el-form-item>

        <div class="rule-head">
          <strong>规则列表</strong>
          <el-button size="small" @click="addRule">添加规则</el-button>
        </div>
        <div v-if="form.rules.length" class="rule-list">
          <div v-for="(rule, index) in form.rules" :key="index" class="rule-item">
            <div class="rule-row">
              <el-input v-model="rule.ruleCode" placeholder="规则编码，如 SEC-001" />
              <el-select v-model="rule.severity" style="width: 150px">
                <el-option label="BLOCKER" value="BLOCKER" />
                <el-option label="CRITICAL" value="CRITICAL" />
                <el-option label="MAJOR" value="MAJOR" />
                <el-option label="MINOR" value="MINOR" />
                <el-option label="INFO" value="INFO" />
              </el-select>
              <el-switch v-model="rule.enabled" active-text="启用" inactive-text="禁用" />
              <el-button text type="danger" @click="removeRule(index)">删除</el-button>
            </div>
            <div class="form-grid">
              <el-input v-model="rule.category" placeholder="分类，如 security" />
              <el-input v-model="rule.title" placeholder="规则标题" />
            </div>
            <el-input v-model="rule.description" type="textarea" :rows="2" placeholder="规则描述" />
            <el-input v-model="rule.checkPrompt" type="textarea" :rows="2" placeholder="给大模型的检查提示" />
          </div>
        </div>
        <div v-else class="empty">暂无规则</div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveStandard">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const loading = ref(false)
const standards = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)

const defaultGate = () => JSON.stringify({
  overallScoreMin: 80,
  blockerMax: 0,
  criticalMax: 0,
  majorMax: 5,
  securityScoreMin: 0
}, null, 2)

const defaultForm = () => ({
  standardCode: '',
  standardName: '',
  description: '',
  language: 'GENERAL',
  framework: '',
  status: 1,
  gateConfig: defaultGate(),
  rules: []
})

const form = reactive(defaultForm())

const enabled = computed({
  get: () => form.status === 1,
  set: value => { form.status = value ? 1 : 0 }
})

const loadStandards = async () => {
  loading.value = true
  try {
    const res = await api.getCodeQualityStandards({ pageNum: 1, pageSize: 100 })
    standards.value = res.data?.data?.records || []
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  editingId.value = null
  Object.assign(form, defaultForm())
  dialogVisible.value = true
}

const openEdit = async (row) => {
  const res = await api.getCodeQualityStandard(row.id)
  const data = res.data?.data || row
  editingId.value = data.id
  Object.assign(form, {
    standardCode: data.standardCode || '',
    standardName: data.standardName || '',
    description: data.description || '',
    language: data.language || 'GENERAL',
    framework: data.framework || '',
    status: data.status ?? 1,
    gateConfig: data.gateConfig || defaultGate(),
    rules: (data.rules || []).map(rule => ({
      ruleCode: rule.ruleCode || '',
      category: rule.category || 'maintainability',
      severity: rule.severity || 'MAJOR',
      title: rule.title || '',
      description: rule.description || '',
      checkPrompt: rule.checkPrompt || '',
      enabled: rule.enabled !== false
    }))
  })
  dialogVisible.value = true
}

const addRule = () => {
  form.rules.push({
    ruleCode: '',
    category: 'maintainability',
    severity: 'MAJOR',
    title: '',
    description: '',
    checkPrompt: '',
    enabled: true
  })
}

const removeRule = (index) => {
  form.rules.splice(index, 1)
}

const saveStandard = async () => {
  if (!form.standardName.trim()) {
    ElMessage.warning('请填写标准名称')
    return
  }
  try {
    JSON.parse(form.gateConfig || '{}')
    const payload = { ...form, rules: form.rules.map(rule => ({ ...rule })) }
    if (editingId.value) {
      await api.updateCodeQualityStandard(editingId.value, payload)
    } else {
      await api.createCodeQualityStandard(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadStandards()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '保存失败')
  }
}

const deleteStandard = async (row) => {
  await ElMessageBox.confirm(`删除代码质量标准「${row.standardName}」？`, '删除确认', { type: 'warning' })
  await api.deleteCodeQualityStandard(row.id)
  ElMessage.success('删除成功')
  await loadStandards()
}

onMounted(loadStandards)
</script>

<style scoped>
.quality-page { color: var(--text-primary); }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px; }
.page-head h2 { font-size: 24px; margin-bottom: 6px; }
.page-head p { color: var(--text-muted); }
.head-actions { display: flex; gap: 10px; align-items: center; }
.panel { background: #fff; border: 1px solid var(--border-color); border-radius: 8px; padding: 16px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.rule-head { display: flex; justify-content: space-between; align-items: center; margin: 10px 0; }
.rule-list { display: flex; flex-direction: column; gap: 12px; }
.rule-item { border: 1px solid var(--border-color); border-radius: 8px; padding: 12px; display: flex; flex-direction: column; gap: 10px; }
.rule-row { display: grid; grid-template-columns: 1fr auto auto auto; gap: 10px; align-items: center; }
.empty { color: var(--text-muted); text-align: center; padding: 18px; border: 1px dashed var(--border-color); border-radius: 8px; }
@media (max-width: 900px) {
  .form-grid, .rule-row { grid-template-columns: 1fr; }
}
</style>
