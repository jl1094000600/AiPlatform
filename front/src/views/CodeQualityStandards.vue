<template>
  <div class="quality-page">
    <div class="page-head">
      <div>
        <h2>代码质量标准</h2>
        <p>维护生成代码的评估标准、规则和质量门禁，供自动化流水线选择使用。</p>
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
      width="960px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-position="top">
        <div class="template-row">
          <span>标准模板</span>
          <el-button v-for="template in standardTemplates" :key="template.key" size="small" @click="applyTemplate(template)">
            {{ template.name }}
          </el-button>
        </div>

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

        <div class="gate-panel">
          <div class="gate-head">
            <strong>质量门禁</strong>
            <el-switch v-model="advancedGateMode" active-text="高级 JSON" inactive-text="表单模式" @change="handleGateModeChange" />
          </div>
          <div v-if="!advancedGateMode" class="gate-grid">
            <el-form-item label="最低总分">
              <el-input-number v-model="gateForm.overallScoreMin" :min="0" :max="100" />
            </el-form-item>
            <el-form-item label="安全最低分">
              <el-input-number v-model="gateForm.securityScoreMin" :min="0" :max="100" />
            </el-form-item>
            <el-form-item label="需求符合度最低分">
              <el-input-number v-model="gateForm.prdAlignmentMin" :min="0" :max="100" />
            </el-form-item>
            <el-form-item label="BLOCKER 最大数">
              <el-input-number v-model="gateForm.blockerMax" :min="0" :max="20" />
            </el-form-item>
            <el-form-item label="CRITICAL 最大数">
              <el-input-number v-model="gateForm.criticalMax" :min="0" :max="20" />
            </el-form-item>
            <el-form-item label="MAJOR 最大数">
              <el-input-number v-model="gateForm.majorMax" :min="0" :max="50" />
            </el-form-item>
          </div>
          <el-form-item v-else label="质量门禁 JSON">
            <el-input v-model="form.gateConfig" type="textarea" :rows="7" @blur="syncGateFormFromJson" />
          </el-form-item>
        </div>

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
const advancedGateMode = ref(false)

const defaultGateObject = () => ({
  overallScoreMin: 80,
  blockerMax: 0,
  criticalMax: 0,
  majorMax: 5,
  securityScoreMin: 0,
  prdAlignmentMin: 75
})

const defaultGate = () => JSON.stringify(defaultGateObject(), null, 2)

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
const gateForm = reactive(defaultGateObject())

const standardTemplates = [
  {
    key: 'java',
    name: 'Java 后端',
    value: {
      standardName: 'Java 后端生成代码标准',
      standardCode: 'JAVA_BACKEND_STANDARD',
      description: '适用于 Spring Boot、Controller、Service、Mapper、DTO 等后端生成代码。',
      language: 'JAVA',
      framework: 'Spring Boot',
      gate: { overallScoreMin: 82, securityScoreMin: 80, prdAlignmentMin: 75, blockerMax: 0, criticalMax: 0, majorMax: 4 },
      rules: [
        rule('SEC-001', 'security', 'BLOCKER', '禁止硬编码敏感信息', '不得硬编码密钥、Token、数据库密码或内部访问凭证。'),
        rule('ARCH-001', 'architecture', 'MAJOR', '保持后端分层', '业务逻辑应放在 Service，Controller 不应堆叠复杂业务。'),
        rule('TEST-001', 'testability', 'MAJOR', '保留可测试入口', '关键服务应具备可单测的输入输出和异常路径。')
      ]
    }
  },
  {
    key: 'vue',
    name: 'Vue 前端',
    value: {
      standardName: 'Vue 前端生成代码标准',
      standardCode: 'VUE_FRONTEND_STANDARD',
      description: '适用于 Vue 页面、组件、表单交互、接口调用和响应式布局。',
      language: 'VUE',
      framework: 'Vue 3 / Element Plus',
      gate: { overallScoreMin: 80, securityScoreMin: 75, prdAlignmentMin: 75, blockerMax: 0, criticalMax: 0, majorMax: 5 },
      rules: [
        rule('UI-001', 'readability', 'MAJOR', '页面结构清晰', '组件状态、表单校验和交互反馈应清晰可维护。'),
        rule('SEC-001', 'security', 'CRITICAL', '用户输入安全处理', '避免直接渲染不可信 HTML，敏感信息不得暴露在前端。'),
        rule('UX-001', 'prdAlignment', 'MAJOR', '符合业务流程', '页面应覆盖 PRD 中的关键操作和异常提示。')
      ]
    }
  },
  {
    key: 'agent',
    name: 'Agent 服务',
    value: {
      standardName: 'Agent 服务生成代码标准',
      standardCode: 'AGENT_SERVICE_STANDARD',
      description: '适用于 Agent 注册、心跳、A2A 调用、模型调用和能力编排代码。',
      language: 'GENERAL',
      framework: 'Agent Service',
      gate: { overallScoreMin: 85, securityScoreMin: 85, prdAlignmentMin: 80, blockerMax: 0, criticalMax: 0, majorMax: 3 },
      rules: [
        rule('AGENT-001', 'architecture', 'CRITICAL', 'Agent 边界清晰', 'Agent 能力、路由、模型调用和持久化职责不得混杂。'),
        rule('AGENT-002', 'runnable', 'MAJOR', '调用失败可恢复', 'A2A 或模型调用失败时应有错误信息和恢复路径。'),
        rule('SEC-001', 'security', 'BLOCKER', '鉴权与凭证保护', '不得绕过鉴权或泄露模型 API Key。')
      ]
    }
  },
  {
    key: 'security',
    name: '通用安全',
    value: {
      standardName: '通用安全代码标准',
      standardCode: 'GENERAL_SECURITY_STANDARD',
      description: '聚焦鉴权、输入校验、敏感信息保护和越权风险。',
      language: 'GENERAL',
      framework: 'Security',
      gate: { overallScoreMin: 80, securityScoreMin: 90, prdAlignmentMin: 70, blockerMax: 0, criticalMax: 0, majorMax: 4 },
      rules: [
        rule('AUTH-001', 'security', 'BLOCKER', '禁止越权访问', '涉及用户、客户、配置和流水线数据时必须校验权限。'),
        rule('INPUT-001', 'security', 'CRITICAL', '输入必须校验', '接口和表单输入必须校验必填、长度、类型和危险内容。'),
        rule('SECRET-001', 'security', 'BLOCKER', '禁止泄露敏感信息', '日志、返回值和前端代码不得暴露密钥、Token、密码。')
      ]
    }
  }
]

function rule(ruleCode, category, severity, title, description) {
  return { ruleCode, category, severity, title, description, checkPrompt: description, enabled: true }
}

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
  advancedGateMode.value = false
  Object.assign(form, defaultForm())
  Object.assign(gateForm, defaultGateObject())
  dialogVisible.value = true
}

const openEdit = async (row) => {
  const res = await api.getCodeQualityStandard(row.id)
  const data = res.data?.data || row
  editingId.value = data.id
  advancedGateMode.value = false
  Object.assign(form, {
    standardCode: data.standardCode || '',
    standardName: data.standardName || '',
    description: data.description || '',
    language: data.language || 'GENERAL',
    framework: data.framework || '',
    status: data.status ?? 1,
    gateConfig: data.gateConfig || defaultGate(),
    rules: (data.rules || []).map(item => ({
      ruleCode: item.ruleCode || '',
      category: item.category || 'maintainability',
      severity: item.severity || 'MAJOR',
      title: item.title || '',
      description: item.description || '',
      checkPrompt: item.checkPrompt || '',
      enabled: item.enabled !== false
    }))
  })
  syncGateFormFromJson()
  dialogVisible.value = true
}

const applyTemplate = (template) => {
  const value = template.value
  Object.assign(form, {
    standardName: value.standardName,
    standardCode: editingId.value ? form.standardCode : value.standardCode,
    description: value.description,
    language: value.language,
    framework: value.framework,
    status: 1,
    gateConfig: JSON.stringify(value.gate, null, 2),
    rules: value.rules.map(item => ({ ...item }))
  })
  Object.assign(gateForm, { ...defaultGateObject(), ...value.gate })
  advancedGateMode.value = false
}

const syncGateJson = () => {
  form.gateConfig = JSON.stringify({ ...gateForm }, null, 2)
}

const handleGateModeChange = (advanced) => {
  if (advanced) {
    syncGateJson()
  } else {
    syncGateFormFromJson()
  }
}

const syncGateFormFromJson = () => {
  try {
    Object.assign(gateForm, { ...defaultGateObject(), ...JSON.parse(form.gateConfig || '{}') })
    syncGateJson()
  } catch {
    ElMessage.warning('质量门禁 JSON 不合法，请检查格式')
  }
}

const addRule = () => {
  form.rules.push(rule('', 'maintainability', 'MAJOR', '', ''))
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
    if (!advancedGateMode.value) syncGateJson()
    JSON.parse(form.gateConfig || '{}')
    const payload = { ...form, rules: form.rules.map(item => ({ ...item })) }
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
.template-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 10px 12px; border: 1px solid #e5e7eb; border-radius: 8px; background: #f8fafc; margin-bottom: 14px; }
.template-row span { color: var(--text-muted); font-size: 13px; font-weight: 700; margin-right: 4px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.gate-panel { border: 1px solid var(--border-color); border-radius: 8px; padding: 12px; margin-bottom: 14px; background: #fbfdff; }
.gate-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.gate-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.rule-head { display: flex; justify-content: space-between; align-items: center; margin: 10px 0; }
.rule-list { display: flex; flex-direction: column; gap: 12px; }
.rule-item { border: 1px solid var(--border-color); border-radius: 8px; padding: 12px; display: flex; flex-direction: column; gap: 10px; }
.rule-row { display: grid; grid-template-columns: 1fr auto auto auto; gap: 10px; align-items: center; }
.empty { color: var(--text-muted); text-align: center; padding: 18px; border: 1px dashed var(--border-color); border-radius: 8px; }
@media (max-width: 900px) {
  .form-grid, .rule-row, .gate-grid { grid-template-columns: 1fr; }
}
</style>
