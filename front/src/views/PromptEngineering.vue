<template>
  <div class="prompt-page">
    <header class="page-head">
      <div>
        <h2>提示词工程</h2>
        <p>把提示词绑定到 Agent 和项目上下文，进行版本优化、样例评测和发布。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="loadAll">刷新</el-button>
        <el-button type="primary" @click="openPromptDialog()">新建提示词</el-button>
      </div>
    </header>

    <section class="workspace-grid">
      <aside class="panel prompt-list">
        <div class="filter-row">
          <el-input v-model="filters.keyword" clearable placeholder="搜索名称/编码" @keyup.enter="loadPrompts" />
          <el-select v-model="filters.agentId" clearable filterable placeholder="Agent" @change="loadPrompts">
            <el-option v-for="agent in agents" :key="agent.id" :label="agent.agentName + ' / ' + agent.agentCode" :value="agent.id" />
          </el-select>
        </div>
        <div class="prompt-items" v-loading="loading">
          <button
            v-for="item in prompts"
            :key="item.id"
            class="prompt-item"
            :class="{ active: selectedPrompt?.id === item.id }"
            @click="selectPrompt(item)"
          >
            <span class="item-title">{{ item.promptName }}</span>
            <span class="item-meta">{{ item.agentName || item.agentCode }} · {{ item.projectName || '未绑定项目' }}</span>
            <span class="item-score">最近得分 {{ item.latestScore ?? '-' }}</span>
          </button>
          <div v-if="!prompts.length && !loading" class="empty-state">暂无提示词</div>
        </div>
      </aside>

      <main class="panel editor-panel">
        <template v-if="selectedPrompt">
          <div class="section-title">
            <div>
              <h3>{{ selectedPrompt.promptName }}</h3>
              <p>{{ selectedPrompt.description || '暂无描述' }}</p>
            </div>
            <div class="inline-actions">
              <el-button @click="openPromptDialog(selectedPrompt)">编辑归属</el-button>
              <el-button type="primary" @click="openVersionDialog()">新建版本</el-button>
            </div>
          </div>

          <div class="version-strip">
            <button
              v-for="version in versions"
              :key="version.id"
              class="version-pill"
              :class="{ active: selectedVersion?.id === version.id }"
              @click="selectVersion(version)"
            >
              v{{ version.versionNo }} {{ version.versionName }}
              <span>{{ statusLabel(version.status) }}</span>
            </button>
          </div>

          <template v-if="selectedVersion">
            <div class="prompt-editor">
              <el-form label-position="top">
                <el-form-item label="System Prompt">
                  <el-input v-model="versionForm.systemPrompt" type="textarea" :rows="8" />
                </el-form-item>
                <el-form-item label="User Prompt Template">
                  <el-input v-model="versionForm.userPromptTemplate" type="textarea" :rows="8" />
                </el-form-item>
                <el-form-item label="变量定义">
                  <el-input v-model="versionForm.variableDefinitions" type="textarea" :rows="4" placeholder='{"input":"用户输入"}' />
                </el-form-item>
              </el-form>
              <div class="editor-actions">
                <el-select v-model="selectedModelId" clearable filterable placeholder="选择优化/评测模型">
                  <el-option v-for="model in models" :key="model.id" :label="model.modelName + ' / ' + model.modelCode" :value="model.id" />
                </el-select>
                <el-button @click="runEvaluate" :loading="evalLoading">运行评测</el-button>
                <el-button @click="openOptimizeDialog" :loading="optimizeLoading">AI 优化</el-button>
                <el-button type="success" @click="publishVersion">发布到 Agent</el-button>
              </div>
            </div>
          </template>
        </template>
        <div v-else class="empty-large">请选择或新建一个提示词</div>
      </main>

      <aside class="panel side-panel">
        <template v-if="selectedPrompt">
          <div class="info-block">
            <h3>绑定信息</h3>
            <dl>
              <div><dt>Agent</dt><dd>{{ selectedPrompt.agentName }} / {{ selectedPrompt.agentCode }}</dd></div>
              <div><dt>项目</dt><dd>{{ selectedPrompt.projectName || '-' }}</dd></div>
              <div><dt>Project Key</dt><dd>{{ selectedPrompt.projectKey || '-' }}</dd></div>
              <div><dt>流水线</dt><dd>{{ selectedPrompt.pipelineId || '-' }}</dd></div>
            </dl>
          </div>
          <div class="info-block">
            <h3>评测运行</h3>
            <div v-if="evalRuns.length" class="run-list">
              <button v-for="run in evalRuns" :key="run.id" @click="loadEvalResults(run)">
                <strong>{{ run.overallScore }}</strong>
                <span>{{ run.summary || run.status }}</span>
              </button>
            </div>
            <div v-else class="muted">暂无评测记录</div>
          </div>
          <div class="info-block">
            <h3>优化记录</h3>
            <div v-if="optimizeRuns.length" class="opt-list">
              <div v-for="run in optimizeRuns" :key="run.id">
                <strong>{{ run.status }}</strong>
                <span>{{ run.optimizationSummary || run.optimizeGoal }}</span>
              </div>
            </div>
            <div v-else class="muted">暂无优化记录</div>
          </div>
        </template>
      </aside>
    </section>

    <section v-if="selectedPrompt" class="bottom-grid">
      <div class="panel">
        <div class="section-title compact">
          <div>
            <h3>测试样例</h3>
            <p>内置样例用于评测当前提示词版本。</p>
          </div>
          <el-button type="primary" @click="openCaseDialog()">新增样例</el-button>
        </div>
        <el-table :data="testCases" stripe>
          <el-table-column prop="caseName" label="样例" min-width="150" />
          <el-table-column prop="expectedOutput" label="期望输出" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130">
            <template #default="{ row }">
              <el-button link type="primary" @click="openCaseDialog(row)">编辑</el-button>
              <el-button link type="danger" @click="deleteCase(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="panel">
        <div class="section-title compact">
          <div>
            <h3>评测明细</h3>
            <p>点击右侧评测运行查看逐样例反馈。</p>
          </div>
        </div>
        <el-table :data="evalResults" stripe>
          <el-table-column prop="caseName" label="样例" width="140" />
          <el-table-column prop="score" label="得分" width="80" />
          <el-table-column prop="feedback" label="反馈" min-width="260" show-overflow-tooltip />
          <el-table-column prop="predictedOutput" label="预测输出" min-width="220" show-overflow-tooltip />
        </el-table>
      </div>
    </section>

    <el-dialog v-model="promptDialog.visible" :title="promptDialog.form.id ? '编辑提示词' : '新建提示词'" width="620px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="提示词名称" required>
            <el-input v-model="promptDialog.form.promptName" />
          </el-form-item>
          <el-form-item label="提示词编码">
            <el-input v-model="promptDialog.form.promptCode" :disabled="!!promptDialog.form.id" placeholder="留空自动生成" />
          </el-form-item>
        </div>
        <el-form-item label="绑定 Agent" required>
          <el-select v-model="promptDialog.form.agentId" filterable style="width: 100%">
            <el-option v-for="agent in agents" :key="agent.id" :label="agent.agentName + ' / ' + agent.agentCode" :value="agent.id" />
          </el-select>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="项目名称">
            <el-input v-model="promptDialog.form.projectName" />
          </el-form-item>
          <el-form-item label="Project Key">
            <el-input v-model="promptDialog.form.projectKey" />
          </el-form-item>
        </div>
        <el-form-item label="流水线 ID">
          <el-input v-model="promptDialog.form.pipelineId" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="promptDialog.form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="promptDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="savePrompt">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="versionDialog.visible" title="新建版本" width="760px">
      <el-form label-position="top">
        <el-form-item label="版本名称">
          <el-input v-model="versionDialog.form.versionName" />
        </el-form-item>
        <el-form-item label="System Prompt">
          <el-input v-model="versionDialog.form.systemPrompt" type="textarea" :rows="6" />
        </el-form-item>
        <el-form-item label="User Prompt Template">
          <el-input v-model="versionDialog.form.userPromptTemplate" type="textarea" :rows="6" />
        </el-form-item>
        <el-form-item label="变量定义">
          <el-input v-model="versionDialog.form.variableDefinitions" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="变更说明">
          <el-input v-model="versionDialog.form.changelog" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="versionDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveVersion">创建版本</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="caseDialog.visible" :title="caseDialog.form.id ? '编辑样例' : '新增样例'" width="640px">
      <el-form label-position="top">
        <el-form-item label="样例名称" required>
          <el-input v-model="caseDialog.form.caseName" />
        </el-form-item>
        <el-form-item label="输入 JSON">
          <el-input v-model="caseDialog.form.inputJson" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="期望输出">
          <el-input v-model="caseDialog.form.expectedOutput" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="评分规则">
          <el-input v-model="caseDialog.form.scoringRule" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="caseDialog.form.enabled" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="caseDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveCase">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="optimizeDialog.visible" title="AI 优化提示词" width="560px">
      <el-form label-position="top">
        <el-form-item label="优化目标">
          <el-input v-model="optimizeDialog.goal" type="textarea" :rows="4" placeholder="例如：让输出更结构化，减少幻觉，强化风险边界" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="optimizeDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="runOptimize" :loading="optimizeLoading">开始优化</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const loading = ref(false)
const evalLoading = ref(false)
const optimizeLoading = ref(false)
const prompts = ref([])
const agents = ref([])
const models = ref([])
const versions = ref([])
const testCases = ref([])
const evalRuns = ref([])
const evalResults = ref([])
const optimizeRuns = ref([])
const selectedPrompt = ref(null)
const selectedVersion = ref(null)
const selectedModelId = ref(null)
const versionForm = reactive({ systemPrompt: '', userPromptTemplate: '', variableDefinitions: '' })
const filters = reactive({ keyword: '', agentId: null })

const promptDialog = reactive({ visible: false, form: defaultPrompt() })
const versionDialog = reactive({ visible: false, form: defaultVersion() })
const caseDialog = reactive({ visible: false, form: defaultCase() })
const optimizeDialog = reactive({ visible: false, goal: '' })

function defaultPrompt() {
  return { id: null, promptCode: '', promptName: '', description: '', agentId: null, projectName: '', projectKey: '', pipelineId: null, status: 1 }
}

function defaultVersion() {
  return { versionName: '', systemPrompt: '', userPromptTemplate: '', variableDefinitions: '{}', changelog: '' }
}

function defaultCase() {
  return { id: null, caseName: '', inputJson: '{}', expectedOutput: '', scoringRule: '', enabled: 1 }
}

async function loadAll() {
  await Promise.all([loadAgentsAndModels(), loadPrompts()])
}

async function loadAgentsAndModels() {
  const [agentRes, modelRes] = await Promise.all([
    api.getAgents({ pageNum: 1, pageSize: 200 }),
    api.getModels({ pageNum: 1, pageSize: 200 })
  ])
  agents.value = agentRes.data.data?.records || []
  models.value = modelRes.data.data?.records || []
}

async function loadPrompts() {
  loading.value = true
  try {
    const res = await api.getPromptEngineeringPrompts({
      pageNum: 1,
      pageSize: 100,
      keyword: filters.keyword || undefined,
      agentId: filters.agentId || undefined
    })
    prompts.value = res.data.data?.records || []
    if (!selectedPrompt.value && prompts.value.length) {
      await selectPrompt(prompts.value[0])
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载提示词失败')
  } finally {
    loading.value = false
  }
}

async function selectPrompt(prompt) {
  selectedPrompt.value = prompt
  evalResults.value = []
  await Promise.all([loadVersions(), loadCases()])
}

async function loadVersions() {
  if (!selectedPrompt.value) return
  const res = await api.getPromptEngineeringVersions(selectedPrompt.value.id)
  versions.value = res.data.data || []
  selectedVersion.value = versions.value[0] || null
  syncVersionForm()
  await loadRuns()
}

async function loadCases() {
  if (!selectedPrompt.value) return
  const res = await api.getPromptEngineeringTestCases(selectedPrompt.value.id)
  testCases.value = res.data.data || []
}

async function loadRuns() {
  if (!selectedVersion.value) {
    evalRuns.value = []
    optimizeRuns.value = []
    return
  }
  const [evalRes, optRes] = await Promise.all([
    api.getPromptEngineeringEvalRuns(selectedVersion.value.id),
    api.getPromptEngineeringOptimizeRuns(selectedVersion.value.id)
  ])
  evalRuns.value = evalRes.data.data || []
  optimizeRuns.value = optRes.data.data || []
}

function selectVersion(version) {
  selectedVersion.value = version
  syncVersionForm()
  evalResults.value = []
  loadRuns()
}

function syncVersionForm() {
  Object.assign(versionForm, {
    systemPrompt: selectedVersion.value?.systemPrompt || '',
    userPromptTemplate: selectedVersion.value?.userPromptTemplate || '',
    variableDefinitions: selectedVersion.value?.variableDefinitions || '{}'
  })
}

function openPromptDialog(row = null) {
  promptDialog.form = row ? { ...defaultPrompt(), ...row } : defaultPrompt()
  promptDialog.visible = true
}

async function savePrompt() {
  if (!promptDialog.form.promptName || !promptDialog.form.agentId) {
    ElMessage.warning('请填写提示词名称并绑定 Agent')
    return
  }
  const payload = { ...promptDialog.form, pipelineId: promptDialog.form.pipelineId ? Number(promptDialog.form.pipelineId) : null }
  try {
    const res = payload.id
      ? await api.updatePromptEngineeringPrompt(payload.id, payload)
      : await api.createPromptEngineeringPrompt(payload)
    ElMessage.success('提示词已保存')
    promptDialog.visible = false
    await loadPrompts()
    await selectPrompt(res.data.data)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存提示词失败')
  }
}

function openVersionDialog() {
  versionDialog.form = selectedVersion.value ? {
    versionName: '',
    systemPrompt: versionForm.systemPrompt,
    userPromptTemplate: versionForm.userPromptTemplate,
    variableDefinitions: versionForm.variableDefinitions,
    changelog: ''
  } : defaultVersion()
  versionDialog.visible = true
}

async function saveVersion() {
  if (!selectedPrompt.value) return
  try {
    const res = await api.createPromptEngineeringVersion(selectedPrompt.value.id, versionDialog.form)
    ElMessage.success('版本已创建')
    versionDialog.visible = false
    await loadVersions()
    selectVersion(res.data.data)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '创建版本失败')
  }
}

function openCaseDialog(row = null) {
  caseDialog.form = row ? { ...defaultCase(), ...row } : defaultCase()
  caseDialog.visible = true
}

async function saveCase() {
  if (!selectedPrompt.value || !caseDialog.form.caseName) {
    ElMessage.warning('请填写样例名称')
    return
  }
  try {
    JSON.parse(caseDialog.form.inputJson || '{}')
    if (caseDialog.form.id) {
      await api.updatePromptEngineeringTestCase(selectedPrompt.value.id, caseDialog.form.id, caseDialog.form)
    } else {
      await api.createPromptEngineeringTestCase(selectedPrompt.value.id, caseDialog.form)
    }
    ElMessage.success('样例已保存')
    caseDialog.visible = false
    await loadCases()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存样例失败，请检查输入 JSON')
  }
}

async function deleteCase(row) {
  await ElMessageBox.confirm(`确定删除样例「${row.caseName}」吗？`, '删除确认', { type: 'warning' })
  await api.deletePromptEngineeringTestCase(selectedPrompt.value.id, row.id)
  ElMessage.success('样例已删除')
  await loadCases()
}

async function runEvaluate() {
  if (!selectedVersion.value) return
  evalLoading.value = true
  try {
    const res = await api.evaluatePromptEngineeringVersion(selectedVersion.value.id, { modelId: selectedModelId.value })
    ElMessage.success('评测完成')
    await loadVersions()
    await loadRuns()
    await loadEvalResults(res.data.data)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '运行评测失败')
  } finally {
    evalLoading.value = false
  }
}

function openOptimizeDialog() {
  optimizeDialog.goal = ''
  optimizeDialog.visible = true
}

async function runOptimize() {
  if (!selectedVersion.value) return
  optimizeLoading.value = true
  try {
    await api.optimizePromptEngineeringVersion(selectedVersion.value.id, {
      modelId: selectedModelId.value,
      optimizeGoal: optimizeDialog.goal
    })
    ElMessage.success('优化版本已生成')
    optimizeDialog.visible = false
    await loadVersions()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || 'AI 优化失败')
  } finally {
    optimizeLoading.value = false
  }
}

async function publishVersion() {
  if (!selectedVersion.value) return
  await api.publishPromptEngineeringVersion(selectedVersion.value.id)
  ElMessage.success('已发布到 Agent 运行时')
  await loadVersions()
  await loadPrompts()
}

async function loadEvalResults(run) {
  const runId = run.id || run
  const res = await api.getPromptEngineeringEvalResults(runId)
  evalResults.value = res.data.data || []
}

function statusLabel(status) {
  return { DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }[status] || status || '-'
}

onMounted(loadAll)
</script>

<style scoped>
.prompt-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.page-head,
.section-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-head h2,
.section-title h3 {
  margin: 0;
}

.page-head p,
.section-title p,
.muted {
  margin: 6px 0 0;
  color: #64748b;
  line-height: 1.6;
}

.head-actions,
.inline-actions,
.editor-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.workspace-grid {
  display: grid;
  grid-template-columns: 300px minmax(520px, 1fr) 300px;
  gap: 16px;
  align-items: start;
}

.bottom-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.panel {
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
}

.filter-row {
  display: grid;
  gap: 10px;
  margin-bottom: 12px;
}

.prompt-items {
  display: grid;
  gap: 10px;
  min-height: 360px;
}

.prompt-item,
.version-pill,
.run-list button {
  width: 100%;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.prompt-item {
  display: grid;
  gap: 4px;
  padding: 12px;
}

.prompt-item.active,
.version-pill.active {
  border-color: #2563eb;
  background: #eff6ff;
}

.item-title {
  font-weight: 700;
}

.item-meta,
.item-score {
  color: #64748b;
  font-size: 12px;
}

.version-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 16px 0;
}

.version-pill {
  width: auto;
  padding: 9px 12px;
}

.version-pill span {
  margin-left: 8px;
  color: #64748b;
}

.prompt-editor {
  display: grid;
  gap: 12px;
}

.editor-actions {
  justify-content: flex-end;
}

.editor-actions .el-select {
  width: 240px;
}

.side-panel {
  display: grid;
  gap: 16px;
}

.info-block h3 {
  margin: 0 0 10px;
}

.info-block dl {
  display: grid;
  gap: 10px;
  margin: 0;
}

.info-block dt {
  color: #64748b;
  font-size: 12px;
}

.info-block dd {
  margin: 3px 0 0;
  word-break: break-word;
}

.run-list,
.opt-list {
  display: grid;
  gap: 8px;
}

.run-list button,
.opt-list div {
  padding: 10px;
}

.run-list strong {
  display: block;
  font-size: 22px;
}

.run-list span,
.opt-list span {
  display: block;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.compact {
  margin-bottom: 12px;
}

.empty-state,
.empty-large {
  display: grid;
  place-items: center;
  min-height: 240px;
  color: #94a3b8;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

@media (max-width: 1180px) {
  .workspace-grid,
  .bottom-grid {
    grid-template-columns: 1fr;
  }
}
</style>
