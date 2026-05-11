<template>
  <div class="automation-page">
    <div class="page-head">
      <div>
        <h2>{{ t('automation.title') }}</h2>
        <p>{{ t('automation.subtitle') }}</p>
      </div>
      <div class="head-actions">
        <el-button @click="openTemplateEditor">{{ t('automation.templateManage') }}</el-button>
        <el-button type="primary" @click="createVisible = true">{{ t('automation.new') }}</el-button>
      </div>
    </div>

    <div class="summary-grid">
      <div class="summary-card" v-for="item in summaryCards" :key="item.label">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </div>
    </div>

    <section class="panel">
      <div class="panel-title">{{ t('automation.pipelines') }}</div>
      <el-table :data="pipelines" v-loading="loading" stripe @row-click="openDetail">
        <el-table-column prop="pipelineCode" :label="t('automation.code')" width="150" />
        <el-table-column prop="requirementTitle" :label="t('automation.requirement')" min-width="220" />
        <el-table-column prop="productLine" :label="t('automation.product')" width="140" />
        <el-table-column prop="projectName" :label="t('automation.project')" width="150" />
        <el-table-column prop="currentStage" :label="t('automation.currentStage')" width="180" />
        <el-table-column prop="status" :label="t('common.status')" width="130">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('automation.progress')" width="140">
          <template #default="{ row }">{{ row.passedStages || 0 }} / {{ row.totalStages || 7 }}</template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel approvals-panel">
      <div class="panel-title">{{ t('automation.reviewQueue') }}</div>
      <el-table :data="approvals" stripe>
        <el-table-column prop="approvalType" :label="t('automation.stage')" min-width="180" />
        <el-table-column prop="reviewerRole" :label="t('automation.reviewerRole')" width="150" />
        <el-table-column prop="status" :label="t('common.status')" width="120">
          <template #default="{ row }">{{ statusText(row.status) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="180">
          <template #default="{ row }">
            <el-button
              v-if="row.approvalType === 'requirement_analysis'"
              size="small"
              @click="openPrdReview(row)"
            >{{ t('automation.viewPrd') }}</el-button>
            <el-button
              v-else-if="row.approvalType === 'code_generation'"
              size="small"
              @click="openCodeTree({ pipelineId: row.pipelineId, approval: row })"
            >{{ t('automation.viewCode') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-drawer v-model="detailVisible" :title="t('automation.detail')" size="560px">
      <div v-if="detail.pipeline" class="detail">
        <h3>{{ detail.pipeline.requirementTitle }}</h3>
        <p>{{ detail.pipeline.requirementSummary || t('automation.noSummary') }}</p>
        <div class="stage-list">
          <div v-for="stage in detail.stages" :key="stage.id" class="stage-item">
            <div>
              <strong>{{ stage.stageOrder }}. {{ stage.stageName }}</strong>
              <span>{{ stage.outputSummary || stage.inputSummary }}</span>
            </div>
            <div class="stage-actions">
              <el-tag :type="statusType(stage.status)">{{ statusText(stage.status) }}</el-tag>
              <el-button
                v-if="stage.stageKey === 'code_generation' && (stage.status === 'QUEUED' || stage.status === 'RUNNING')"
                size="small"
                loading
              >{{ stage.status === 'QUEUED' ? t('automation.queued') : t('automation.codeGenerating') }}</el-button>
              <el-button
                v-else-if="stage.stageKey === 'code_generation' && stage.artifactPath"
                size="small"
                @click.stop="openCodeTree(stage)"
              >{{ t('automation.viewCode') }}</el-button>
              <el-button
                v-else-if="isStageRunnable(stage)"
                size="small"
                @click="runStage(stage)"
              >{{ t('automation.run') }}</el-button>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="createVisible" :title="t('automation.createTitle')" width="680px">
      <el-form :model="form" label-position="top">
        <el-form-item :label="t('automation.productLine')">
          <el-input v-model="form.productLine" placeholder="Core Platform" />
        </el-form-item>
        <el-form-item :label="t('automation.projectName')">
          <el-input v-model="form.projectName" placeholder="AI Platform" />
        </el-form-item>
        <el-form-item :label="t('automation.requirementTitle')">
          <el-input v-model="form.requirementTitle" placeholder="Automated delivery loop" />
        </el-form-item>
        <el-form-item :label="t('automation.requirementSummary')">
          <el-input v-model="form.requirementSummary" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item :label="t('automation.openModel')">
          <el-select v-model="form.modelId" filterable clearable style="width: 100%" @change="selectPipelineModel">
            <el-option
              v-for="model in models"
              :key="model.id"
              :label="model.modelName + ' / ' + model.modelCode"
              :value="model.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Skill">
          <el-select v-model="form.skillId" filterable clearable placeholder="可选，不选择则按默认流水线生成" style="width: 100%">
            <el-option
              v-for="skill in skills"
              :key="skill.id"
              :label="skill.skillName + ' / ' + skill.skillCode"
              :value="skill.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('automation.template')">
          <div class="inline-field">
            <el-select v-model="form.templateFile" filterable style="width: 100%">
              <el-option
                v-for="template in prdTemplates"
                :key="template.fileName"
                :label="template.fileName"
                :value="template.fileName"
              />
            </el-select>
            <el-button @click="openTemplateEditor">{{ t('automation.editTemplate') }}</el-button>
          </div>
        </el-form-item>
        <div class="form-grid">
          <el-form-item :label="t('automation.projectMode')">
            <el-select v-model="form.projectMode">
              <el-option :label="t('automation.scratchProject')" value="scratch" />
              <el-option :label="t('automation.existingProject')" value="existing" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('automation.codeLevel')">
            <el-select v-model="form.codeLevel">
              <el-option :label="t('automation.levelProject')" value="project" />
              <el-option :label="t('automation.levelModule')" value="module" />
              <el-option :label="t('automation.levelPackage')" value="package" />
              <el-option :label="t('automation.levelComponent')" value="component" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item :label="t('automation.generateScope')">
          <el-checkbox v-model="form.generateFrontend">{{ t('automation.generateFrontend') }}</el-checkbox>
          <el-checkbox v-model="form.generateBackend">{{ t('automation.generateBackend') }}</el-checkbox>
        </el-form-item>
        <div class="form-grid">
          <el-form-item :label="t('automation.frontendOutput')">
            <el-tree-select
              v-model="form.frontendOutputPath"
              :data="projectDirectoryTree"
              node-key="value"
              check-strictly
              filterable
              :props="directoryTreeProps"
              :disabled="!form.generateFrontend"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item :label="t('automation.backendOutput')">
            <el-tree-select
              v-model="form.backendOutputPath"
              :data="projectDirectoryTree"
              node-key="value"
              check-strictly
              filterable
              :props="directoryTreeProps"
              :disabled="!form.generateBackend"
              style="width: 100%"
            />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="createPipeline">{{ t('common.create') }}</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="templateVisible" :title="t('automation.templateManage')" size="680px">
      <div class="template-toolbar">
        <el-select v-model="currentTemplateFile" filterable style="width: 100%" @change="loadTemplateContent">
          <el-option
            v-for="template in prdTemplates"
            :key="template.fileName"
            :label="template.fileName"
            :value="template.fileName"
          />
        </el-select>
        <el-input v-model="newTemplateName" :placeholder="t('automation.newTemplateName')" />
        <el-button @click="createTemplate">{{ t('common.create') }}</el-button>
      </div>
      <div class="template-meta">
        {{ t('automation.templateSavePath') }}：marketDoc/prd-templates/{{ currentTemplateFile || '-' }}
      </div>
      <el-input v-model="templateContent" type="textarea" :rows="24" v-loading="templateLoading" />
      <div class="drawer-footer">
        <el-button @click="templateVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveTemplate">{{ t('common.save') }}</el-button>
      </div>
    </el-drawer>

    <el-dialog v-model="prdVisible" title="PRD 审核" width="900px" :close-on-click-modal="false">
      <div class="prd-meta">
        <span>文件：{{ prdPath || '-' }}</span>
      </div>
      <el-input v-model="prdContent" type="textarea" :rows="20" />
      <template #footer>
        <el-button @click="prdVisible = false">取消</el-button>
        <el-button @click="regeneratePrd">重新生成</el-button>
        <el-button type="danger" plain @click="submitPrdReview('REJECTED')">拒绝</el-button>
        <el-button type="primary" @click="submitPrdReview('SUCCESS')">保存并通过</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="codeVisible" :title="t('automation.generatedCode')" width="980px" :close-on-click-modal="false">
      <div class="code-meta">
        <span>{{ t('common.status') }}：{{ statusText(codeTree.status || 'PENDING') }}</span>
        <span v-if="codeTree.artifactPath">{{ t('automation.directory') }}：{{ codeTree.artifactPath }}</span>
      </div>
      <div v-if="codeTree.status === 'QUEUED' || codeTree.status === 'RUNNING'" class="code-generating">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>{{ codeTree.status === 'QUEUED' ? t('automation.codeQueuedHint') : t('automation.codeGeneratingHint') }}</span>
      </div>
      <div v-else class="code-layout">
        <aside class="code-files">
          <button
            v-for="file in codeFiles"
            :key="file.path"
            class="code-file"
            :class="{ active: selectedCodeFile === file.path }"
            @click="selectCodeFile(file)"
          >
            <span>{{ file.path }}</span>
            <small>{{ formatBytes(file.size) }}</small>
          </button>
          <div v-if="!codeFiles.length" class="empty-code">{{ t('automation.noCodeFiles') }}</div>
        </aside>
        <main class="code-preview" v-loading="codeLoading">
          <div v-if="selectedCodeFile" class="code-preview-head">
            <strong>{{ selectedCodeFile }}</strong>
            <el-tag v-if="codeTruncated" type="warning">{{ t('automation.previewTruncated') }}</el-tag>
          </div>
          <pre>{{ codeContent || t('automation.selectCodeFile') }}</pre>
        </main>
      </div>
      <template #footer>
        <el-button @click="codeVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button v-if="codeTree.pipelineId" @click="regenerateCode">{{ t('automation.regenerateCode') }}</el-button>
        <el-button v-if="isPendingApproval(currentCodeApproval)" type="danger" plain @click="submitCodeReview('REJECTED')">{{ t('automation.reject') }}</el-button>
        <el-button v-if="isPendingApproval(currentCodeApproval)" type="primary" @click="submitCodeReview('SUCCESS')">{{ t('automation.approve') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import api from '../api'
import { useI18n } from '../i18n'

const { t } = useI18n()

const loading = ref(false)
const createVisible = ref(false)
const detailVisible = ref(false)
const summary = ref({})
const pipelines = ref([])
const approvals = ref([])
const models = ref([])
const skills = ref([])
const prdTemplates = ref([])
const templateVisible = ref(false)
const currentTemplateFile = ref('')
const newTemplateName = ref('')
const templateContent = ref('')
const templateLoading = ref(false)
const projectDirectoryTree = ref([])
const directoryTreeProps = { label: 'label', children: 'children', disabled: 'disabled' }
const prdVisible = ref(false)
const currentApproval = ref(null)
const currentCodeApproval = ref(null)
const currentPrdStage = ref(null)
const prdContent = ref('')
const prdPath = ref('')
const codeVisible = ref(false)
const codeTree = ref({})
const codeFiles = ref([])
const selectedCodeFile = ref('')
const codeContent = ref('')
const codeLoading = ref(false)
const codeTruncated = ref(false)
let detailPollTimer = null
const detail = reactive({ pipeline: null, stages: [], approvals: [] })
const form = reactive({
  productLine: '',
  projectName: '',
  requirementTitle: '',
  requirementSummary: '',
  initiator: 'admin',
  modelId: null,
  aiModelCode: 'default-open-model',
  skillId: null,
  templateFile: 'default-prd-template.md',
  projectMode: 'scratch',
  codeLevel: 'module',
  generateFrontend: true,
  generateBackend: true,
  frontendOutputPath: 'front/src/generated',
  backendOutputPath: 'backend/src/main/java/com/aipal/generated'
})

const summaryCards = computed(() => [
  { label: t('automation.total'), value: summary.value.totalPipelines || 0 },
  { label: t('automation.running'), value: summary.value.runningPipelines || 0 },
  { label: t('automation.waitingReview'), value: summary.value.waitingApprovals || 0 },
  { label: t('automation.stagePassRate'), value: (summary.value.stagePassRate || 0) + '%' }
])

const loadAll = async () => {
  loading.value = true
  try {
    const [summaryRes, pipelineRes, approvalRes, modelsRes, skillsRes, prdTemplatesRes, directoriesRes] = await Promise.all([
      api.getAutomationSummary(),
      api.getAutomationPipelines({ pageNum: 1, pageSize: 20 }),
      api.getAutomationApprovals({ pageNum: 1, pageSize: 20, status: 'PENDING' }),
      api.getModels({ pageNum: 1, pageSize: 100 }),
      api.getEnabledSkills(),
      api.getAutomationPrdTemplates(),
      api.getAutomationProjectDirectories()
    ])
    summary.value = summaryRes.data.data || {}
    pipelines.value = pipelineRes.data.data?.records || []
    approvals.value = approvalRes.data.data?.records || []
    models.value = modelsRes.data.data?.records || []
    skills.value = skillsRes.data.data || []
    prdTemplates.value = prdTemplatesRes.data.data || []
    projectDirectoryTree.value = directoriesRes.data.data ? [directoriesRes.data.data] : []
    if (!form.templateFile && prdTemplates.value.length) {
      form.templateFile = prdTemplates.value[0].fileName
    }
  } finally {
    loading.value = false
  }
}

const createPipeline = async () => {
  if (!form.productLine || !form.projectName || !form.requirementTitle) {
    ElMessage.warning(t('automation.required'))
    return
  }
  if (!form.modelId) {
    ElMessage.warning('请选择用于生成 PRD 的模型')
    return
  }
  if (!form.generateFrontend && !form.generateBackend) {
    ElMessage.warning(t('automation.scopeRequired'))
    return
  }
  await api.createAutomationPipeline(form)
  ElMessage.success(t('automation.created'))
  createVisible.value = false
  Object.assign(form, {
    productLine: '',
    projectName: '',
    requirementTitle: '',
    requirementSummary: '',
    initiator: 'admin',
    modelId: null,
    aiModelCode: 'default-open-model',
    skillId: null,
    templateFile: prdTemplates.value[0]?.fileName || 'default-prd-template.md',
    projectMode: 'scratch',
    codeLevel: 'module',
    generateFrontend: true,
    generateBackend: true,
    frontendOutputPath: 'front/src/generated',
    backendOutputPath: 'backend/src/main/java/com/aipal/generated'
  })
  await loadAll()
}

const selectPipelineModel = (modelId) => {
  const model = models.value.find(item => item.id === modelId)
  form.aiModelCode = model?.modelCode || 'default-open-model'
}

const openTemplateEditor = async () => {
  try {
    if (!prdTemplates.value.length) {
      await loadAll()
    }
    currentTemplateFile.value = form.templateFile || prdTemplates.value[0]?.fileName || 'default-prd-template.md'
    await loadTemplateContent(currentTemplateFile.value)
    templateVisible.value = true
  } catch (error) {
    ElMessage.error(t('automation.templateLoadFailed'))
  }
}

const loadTemplateContent = async (fileName) => {
  if (!fileName) return
  templateLoading.value = true
  try {
    const res = await api.getAutomationPrdTemplate(fileName)
    templateContent.value = res.data.data?.content || ''
    currentTemplateFile.value = res.data.data?.fileName || fileName
    form.templateFile = currentTemplateFile.value
  } catch (error) {
    ElMessage.error(t('automation.templateLoadFailed'))
  } finally {
    templateLoading.value = false
  }
}

const saveTemplate = async () => {
  if (!currentTemplateFile.value) return
  templateLoading.value = true
  try {
    await api.saveAutomationPrdTemplate(currentTemplateFile.value, templateContent.value)
    ElMessage.success(t('automation.templateSaved'))
    await loadAll()
    form.templateFile = currentTemplateFile.value
  } catch (error) {
    ElMessage.error(t('automation.templateSaveFailed'))
  } finally {
    templateLoading.value = false
  }
}

const createTemplate = async () => {
  const rawName = newTemplateName.value.trim()
  if (!rawName) {
    ElMessage.warning(t('automation.templateNameRequired'))
    return
  }
  const fileName = rawName.endsWith('.md') ? rawName : `${rawName}.md`
  templateLoading.value = true
  try {
    await api.saveAutomationPrdTemplate(fileName, '# PRD Template\n\n## Background\n-\n\n## Acceptance Criteria\n-\n')
    newTemplateName.value = ''
    await loadAll()
    await loadTemplateContent(fileName)
    form.templateFile = fileName
    ElMessage.success(t('automation.templateCreated'))
  } catch (error) {
    ElMessage.error(t('automation.templateSaveFailed'))
  } finally {
    templateLoading.value = false
  }
}

const openDetail = async (row, keepDrawer = true) => {
  const res = await api.getAutomationPipeline(row.id)
  Object.assign(detail, res.data.data || { pipeline: null, stages: [], approvals: [] })
  if (keepDrawer) {
    detailVisible.value = true
  }
  syncDetailPolling()
}

const runStage = async (stage) => {
  await api.runAutomationStage(stage.id)
  ElMessage.success(t('automation.stageExecuted'))
  await openDetail(detail.pipeline)
  await loadAll()
}

const isPendingApproval = (approval) => approval?.status === 'PENDING'

const isStageRunnable = (stage) => {
  if (!stage || stage.stageKey === 'requirement_analysis' || stage.stageKey === 'code_generation') {
    return false
  }
  const rejectedStage = detail.stages.find(item => item.status === 'REJECTED')
  if (rejectedStage) {
    return rejectedStage.id === stage.id
  }
  if (!['PENDING', 'RUNNING'].includes(stage.status)) {
    return false
  }
  if (detail.pipeline?.currentStage && detail.pipeline.currentStage !== stage.stageKey) {
    return false
  }
  return !detail.stages.some(item => item.stageOrder < stage.stageOrder && item.status !== 'SUCCESS')
}

const syncDetailPolling = () => {
  stopDetailPolling()
  if (!detail.pipeline) return
  const hasRunningCode = detail.stages.some(stage => stage.stageKey === 'code_generation' && (stage.status === 'QUEUED' || stage.status === 'RUNNING'))
  if (!hasRunningCode) return
  detailPollTimer = window.setInterval(async () => {
    await openDetail(detail.pipeline, false)
    if (codeVisible.value && codeTree.value?.pipelineId) {
      await refreshCodeTree(codeTree.value.pipelineId)
    }
    await loadAll()
  }, 3000)
}

const stopDetailPolling = () => {
  if (detailPollTimer) {
    window.clearInterval(detailPollTimer)
    detailPollTimer = null
  }
}

const approve = async (row, status) => {
  await api.approveAutomation(row.id, { status, reviewedBy: 'admin', comment: status })
  ElMessage.success(status === 'SUCCESS' ? t('automation.approved') : t('automation.rejected'))
  await loadAll()
  if (detail.pipeline) {
    await openDetail(detail.pipeline)
  }
}

const openPrdReview = async (row) => {
  const res = await api.getAutomationApprovalDocument(row.id)
  currentApproval.value = row
  currentPrdStage.value = res.data.data?.stage || null
  prdContent.value = res.data.data?.content || ''
  prdPath.value = res.data.data?.artifactPath || ''
  prdVisible.value = true
}

const regeneratePrd = async () => {
  const pipelineId = currentPrdStage.value?.pipelineId || currentApproval.value?.pipelineId
  if (!pipelineId) return
  await api.regenerateAutomationPrd(pipelineId)
  ElMessage.success('PRD 已重新生成')
  prdVisible.value = false
  await loadAll()
  if (detail.pipeline) {
    await openDetail(detail.pipeline)
  }
}

const submitPrdReview = async (status) => {
  if (!currentApproval.value) return
  await api.approveAutomation(currentApproval.value.id, {
    status,
    reviewedBy: 'admin',
    comment: status === 'SUCCESS' ? 'PRD approved' : 'PRD rejected',
    artifactContent: prdContent.value
  })
  ElMessage.success(status === 'SUCCESS' ? 'PRD 已通过' : 'PRD 已拒绝')
  prdVisible.value = false
  await loadAll()
  if (detail.pipeline) {
    await openDetail(detail.pipeline)
  }
}

const openCodeTree = async (stage) => {
  await refreshCodeTree(stage.pipelineId)
  currentCodeApproval.value = stage.approval || null
  selectedCodeFile.value = ''
  codeContent.value = ''
  codeTruncated.value = false
  codeVisible.value = true
}

const refreshCodeTree = async (pipelineId) => {
  const res = await api.getAutomationCodeTree(pipelineId)
  codeTree.value = res.data.data || {}
  codeFiles.value = codeTree.value.files || []
}

const selectCodeFile = async (file) => {
  selectedCodeFile.value = file.path
  codeLoading.value = true
  try {
    const res = await api.getAutomationCodeFile(codeTree.value.pipelineId, file.path)
    codeContent.value = res.data.data?.content || ''
    codeTruncated.value = !!res.data.data?.truncated
  } finally {
    codeLoading.value = false
  }
}

const regenerateCode = async () => {
  const pipelineId = codeTree.value?.pipelineId || detail.pipeline?.id
  if (!pipelineId) return
  await api.regenerateAutomationCode(pipelineId)
  ElMessage.success(t('automation.codeRegenerating'))
  await refreshCodeTree(pipelineId)
  if (detail.pipeline) {
    await openDetail(detail.pipeline)
  }
}

const submitCodeReview = async (status) => {
  if (!currentCodeApproval.value) return
  await api.approveAutomation(currentCodeApproval.value.id, {
    status,
    reviewedBy: 'admin',
    comment: status === 'SUCCESS' ? 'Code approved' : 'Code rejected'
  })
  ElMessage.success(status === 'SUCCESS' ? t('automation.codeApproved') : t('automation.codeRejected'))
  codeVisible.value = false
  currentCodeApproval.value = null
  await loadAll()
  if (detail.pipeline) {
    await openDetail(detail.pipeline)
  }
}

const formatBytes = (value) => {
  const size = Number(value || 0)
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

const statusType = (status) => {
  if (status === 'SUCCESS' || status === 'COMPLETED') return 'success'
  if (status === 'REJECTED' || status === 'BLOCKED') return 'danger'
  if (status === 'WAITING_APPROVAL') return 'warning'
  return 'info'
}

const statusText = (status) => t(`status.${status}`)

watch(detailVisible, visible => {
  if (!visible) stopDetailPolling()
})

onMounted(loadAll)
onUnmounted(stopDetailPolling)
</script>

<style scoped>
.automation-page { color: var(--text-primary); }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px; }
.head-actions { display: flex; gap: 10px; align-items: center; }
.page-head h2 { font-size: 24px; margin-bottom: 6px; }
.page-head p { color: var(--text-muted); }
.inline-field { display: flex; gap: 10px; width: 100%; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.template-toolbar { display: grid; grid-template-columns: 1fr 180px auto; gap: 10px; margin-bottom: 12px; }
.template-meta { color: var(--text-muted); font-size: 12px; margin-bottom: 10px; word-break: break-all; }
.drawer-footer { display: flex; justify-content: flex-end; gap: 10px; margin-top: 14px; }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(150px, 1fr)); gap: 14px; margin-bottom: 16px; }
.summary-card, .panel { background: #ffffff; border: 1px solid var(--border-color); border-radius: 8px; }
.summary-card { padding: 16px; display: flex; flex-direction: column; gap: 8px; }
.summary-card span { color: var(--text-muted); font-size: 13px; }
.summary-card strong { font-size: 28px; color: #111827; }
.panel { padding: 16px; margin-bottom: 16px; }
.panel-title { font-weight: 700; margin-bottom: 12px; }
.approvals-panel { margin-top: 16px; }
.detail h3 { margin-bottom: 8px; }
.detail p { color: var(--text-muted); margin-bottom: 16px; }
.stage-list { display: flex; flex-direction: column; gap: 10px; }
.stage-item { display: flex; justify-content: space-between; gap: 12px; border: 1px solid var(--border-color); border-radius: 8px; padding: 12px; }
.stage-item span { display: block; color: var(--text-muted); margin-top: 5px; line-height: 1.5; }
.stage-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.prd-meta { color: var(--text-muted); margin-bottom: 12px; font-size: 13px; word-break: break-all; }
.code-meta { display: flex; flex-direction: column; gap: 6px; color: var(--text-muted); font-size: 13px; margin-bottom: 12px; word-break: break-all; }
.code-generating { min-height: 220px; display: flex; align-items: center; justify-content: center; gap: 10px; color: var(--text-muted); border: 1px dashed var(--border-color); border-radius: 8px; }
.code-layout { display: grid; grid-template-columns: 290px 1fr; gap: 14px; min-height: 520px; }
.code-files { border: 1px solid var(--border-color); border-radius: 8px; padding: 8px; overflow: auto; background: #f8fafc; }
.code-file { width: 100%; border: 0; border-radius: 6px; background: transparent; padding: 9px 10px; text-align: left; cursor: pointer; display: flex; flex-direction: column; gap: 4px; color: #111827; }
.code-file:hover, .code-file.active { background: #eaf1ff; }
.code-file span { font-size: 13px; line-height: 1.35; word-break: break-all; }
.code-file small { color: var(--text-muted); }
.empty-code { color: var(--text-muted); padding: 18px 10px; text-align: center; }
.code-preview { border: 1px solid var(--border-color); border-radius: 8px; background: #0f172a; color: #e5e7eb; overflow: hidden; display: flex; flex-direction: column; }
.code-preview-head { min-height: 42px; padding: 10px 12px; border-bottom: 1px solid rgba(255,255,255,.1); display: flex; align-items: center; justify-content: space-between; gap: 12px; color: #f8fafc; }
.code-preview pre { margin: 0; padding: 14px; overflow: auto; flex: 1; white-space: pre-wrap; word-break: break-word; font-size: 12px; line-height: 1.6; }
@media (max-width: 1100px) {
  .summary-grid { grid-template-columns: repeat(2, 1fr); }
  .code-layout { grid-template-columns: 1fr; }
  .code-files { max-height: 220px; }
  .form-grid, .template-toolbar { grid-template-columns: 1fr; }
  .inline-field { flex-direction: column; }
}
</style>
