<template>
  <div class="analytics-page">
    <div class="analytics-header">
      <div>
        <h2>平台数据分析中心</h2>
        <span class="muted">统一查看 Badcase、Token 成本、流水线效率与模型性价比</span>
      </div>
      <div class="header-actions">
        <el-button disabled>分析 Agent（二期）</el-button>
        <el-button type="primary" :loading="loading" @click="loadAll">刷新</el-button>
      </div>
    </div>

    <section class="filter-panel">
      <el-radio-group v-model="filters.range" @change="loadAll">
        <el-radio-button label="7d">近7天</el-radio-button>
        <el-radio-button label="30d">近30天</el-radio-button>
      </el-radio-group>
      <el-select v-model="filters.project" filterable allow-create clearable default-first-option placeholder="项目" @change="loadAll">
        <el-option v-for="item in projectOptions" :key="item" :label="item" :value="item" />
      </el-select>
      <el-select v-model="filters.model" filterable allow-create clearable default-first-option placeholder="模型" @change="loadAll">
        <el-option v-for="item in modelOptions" :key="item" :label="item" :value="item" />
      </el-select>
      <el-select v-model="filters.stage" clearable placeholder="阶段" @change="loadAll">
        <el-option v-for="item in stageOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="filters.severity" clearable placeholder="严重程度" @change="loadAll">
        <el-option v-for="item in severityOptions" :key="item" :label="item" :value="item" />
      </el-select>
      <el-button @click="resetFilters">重置</el-button>
    </section>

    <el-alert
      v-if="failedSections.length"
      class="load-alert"
      type="warning"
      show-icon
      :closable="false"
      :title="`部分数据加载失败：${failedSections.join('、')}，已按空数据展示`"
    />

    <el-tabs v-model="activeTab" class="analytics-tabs">
      <el-tab-pane label="总览" name="overview">
        <section class="metric-grid">
          <div v-for="item in overviewMetrics" :key="item.label" class="metric-card" :class="`tone-${item.tone}`">
            <span>{{ item.label }}</span>
            <strong class="mono">{{ item.value }}</strong>
            <small>{{ item.hint }}</small>
          </div>
        </section>

        <div class="ops-grid two">
          <section class="ops-panel">
            <PanelTitle title="趋势概览" subtitle="Token、成本、Badcase 与流水线成功率" />
            <TrendRows :items="overviewTrendRows" />
          </section>
          <section class="ops-panel">
            <PanelTitle title="治理建议" subtitle="基于聚合结果的轻量提示" />
            <SuggestionList :items="overviewSuggestions" empty-text="暂无建议" />
          </section>
        </div>
      </el-tab-pane>

      <el-tab-pane label="Badcase" name="badcases">
        <section class="metric-grid compact">
          <div v-for="item in badcaseMetrics" :key="item.label" class="metric-card" :class="`tone-${item.tone}`">
            <span>{{ item.label }}</span>
            <strong class="mono">{{ item.value }}</strong>
            <small>{{ item.hint }}</small>
          </div>
        </section>

        <div class="ops-grid three">
          <DistributionPanel title="阶段分布" :items="badcaseStageDistribution" />
          <DistributionPanel title="类型分布" :items="badcaseTypeDistribution" />
          <DistributionPanel title="严重程度" :items="badcaseSeverityDistribution" />
        </div>

        <div class="ops-grid two">
          <section class="ops-panel">
            <PanelTitle title="Top 失败原因" subtitle="优先治理高频问题" />
            <RankList :items="badcaseReasons" empty-text="暂无失败原因" />
          </section>
          <section class="ops-panel">
            <PanelTitle title="最近 Badcase" subtitle="最新记录用于快速复盘" />
            <el-table :data="badcaseRecords" size="small" class="mini-table" max-height="320">
              <el-table-column prop="caseCode" label="编号" min-width="120" show-overflow-tooltip />
              <el-table-column prop="projectName" label="项目" min-width="140" show-overflow-tooltip />
              <el-table-column prop="stage" label="阶段" width="120" />
              <el-table-column prop="severity" label="严重程度" width="100">
                <template #default="{ row }">
                  <el-tag :type="severityTagType(row.severity)" size="small">{{ row.severity || '-' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="failureReason" label="失败原因" min-width="220" show-overflow-tooltip />
            </el-table>
          </section>
        </div>
      </el-tab-pane>

      <el-tab-pane label="Token与成本" name="token-cost">
        <section class="metric-grid compact">
          <div v-for="item in tokenMetrics" :key="item.label" class="metric-card" :class="`tone-${item.tone}`">
            <span>{{ item.label }}</span>
            <strong class="mono">{{ item.value }}</strong>
            <small>{{ item.hint }}</small>
          </div>
        </section>

        <div class="ops-grid two">
          <section class="ops-panel">
            <PanelTitle title="模型拆分" subtitle="按模型查看 Token 与费用贡献" />
            <el-table :data="tokenModelRows" size="small" class="mini-table" max-height="360">
              <el-table-column prop="name" label="模型" min-width="140" show-overflow-tooltip />
              <el-table-column prop="calls" label="调用" width="90" />
              <el-table-column prop="tokens" label="Token" width="120" />
              <el-table-column label="成本" width="110">
                <template #default="{ row }">{{ formatCost(row.cost) }}</template>
              </el-table-column>
            </el-table>
          </section>
          <section class="ops-panel">
            <PanelTitle title="高消耗调用 TopN" subtitle="定位异常消耗与优化入口" />
            <RankList :items="topTokenCalls" empty-text="暂无高消耗调用" />
          </section>
        </div>
      </el-tab-pane>

      <el-tab-pane label="流水线效率" name="pipelines">
        <section class="metric-grid compact">
          <div v-for="item in pipelineMetrics" :key="item.label" class="metric-card" :class="`tone-${item.tone}`">
            <span>{{ item.label }}</span>
            <strong class="mono">{{ item.value }}</strong>
            <small>{{ item.hint }}</small>
          </div>
        </section>

        <div class="ops-grid two">
          <section class="ops-panel">
            <PanelTitle title="阶段通过率" subtitle="各交付阶段的通过与阻塞情况" />
            <TrendRows :items="pipelineStageRows" />
          </section>
          <section class="ops-panel">
            <PanelTitle title="阻塞与返工" subtitle="最常见的效率损耗来源" />
            <RankList :items="pipelineBottlenecks" empty-text="暂无阻塞记录" />
          </section>
        </div>
      </el-tab-pane>

      <el-tab-pane label="模型性价比" name="model-value">
        <div class="ops-grid two">
          <section class="ops-panel">
            <PanelTitle title="模型性价比对比" subtitle="成本、质量风险与成功率综合观察" />
            <el-table :data="modelValueRows" size="small" class="mini-table" max-height="460">
              <el-table-column prop="name" label="模型" min-width="160" show-overflow-tooltip />
              <el-table-column prop="calls" label="调用" width="90" />
              <el-table-column prop="tokens" label="Token" width="120" />
              <el-table-column label="成本" width="110">
                <template #default="{ row }">{{ formatCost(row.cost) }}</template>
              </el-table-column>
              <el-table-column prop="badcases" label="Badcase" width="100" />
              <el-table-column label="成功率" width="100">
                <template #default="{ row }">{{ formatPercent(row.successRate) }}</template>
              </el-table-column>
            </el-table>
          </section>
          <section class="ops-panel">
            <PanelTitle title="优化建议" subtitle="高成本、低质量、可降级场景" />
            <SuggestionList :items="modelSuggestions" empty-text="暂无模型优化建议" />
          </section>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, reactive, ref } from 'vue'
import api from '../api'

const PanelTitle = defineComponent({
  props: { title: String, subtitle: String },
  setup: (props) => () => h('div', { class: 'panel-title-row' }, [
    h('div', [
      h('div', { class: 'panel-title' }, props.title),
      props.subtitle ? h('span', { class: 'panel-subtitle' }, props.subtitle) : null
    ])
  ])
})

const TrendRows = defineComponent({
  props: { items: { type: Array, default: () => [] } },
  setup: (props) => () => props.items.length
    ? h('div', { class: 'trend-stack' }, props.items.map(item => h('div', { class: 'trend-row-card', key: item.label }, [
      h('div', { class: 'trend-meta' }, [h('strong', item.label), h('span', item.hint || '')]),
      h('div', { class: 'trend-bar' }, [h('i', { class: item.tone || '', style: { width: item.width || '0%' } })]),
      h('span', { class: 'mono trend-value' }, item.value)
    ])))
    : h('div', { class: 'empty-lite' }, '暂无数据')
})

const DistributionPanel = defineComponent({
  props: { title: String, items: { type: Array, default: () => [] } },
  setup: (props) => () => h('section', { class: 'ops-panel' }, [
    h(PanelTitle, { title: props.title }),
    h(TrendRows, { items: props.items })
  ])
})

const RankList = defineComponent({
  props: { items: { type: Array, default: () => [] }, emptyText: { type: String, default: '暂无数据' } },
  setup: (props) => () => props.items.length
    ? h('div', { class: 'rank-list' }, props.items.map((item, index) => h('div', { class: 'rank-row', key: `${item.label}-${index}` }, [
      h('span', { class: 'rank-index' }, index + 1),
      h('div', [h('strong', item.label), item.hint ? h('span', item.hint) : null]),
      h('em', { class: 'mono' }, item.value)
    ])))
    : h('div', { class: 'empty-lite' }, props.emptyText)
})

const SuggestionList = defineComponent({
  props: { items: { type: Array, default: () => [] }, emptyText: { type: String, default: '暂无建议' } },
  setup: (props) => () => props.items.length
    ? h('div', { class: 'suggestion-list' }, props.items.map(item => h('div', { class: 'suggestion-item', key: item }, item)))
    : h('div', { class: 'empty-lite' }, props.emptyText)
})

const activeTab = ref('overview')
const loading = ref(false)
const failedSections = ref([])
const overview = ref({})
const badcases = ref({})
const tokenCost = ref({})
const pipelines = ref({})
const modelValue = ref({})

const filters = reactive({ range: '7d', project: '', model: '', stage: '', severity: '' })
const stageOptions = [
  { label: 'PRD', value: 'PRD' },
  { label: '代码生成', value: 'CODE_GENERATION' },
  { label: '代码质量', value: 'CODE_QUALITY' },
  { label: '构建', value: 'BUILD' },
  { label: '测试', value: 'TEST' },
  { label: '部署', value: 'DEPLOY' }
]
const severityOptions = ['P0', 'P1', 'P2', 'P3']

const params = computed(() => ({
  range: filters.range,
  days: filters.range === '30d' ? 30 : 7,
  projectName: filters.project || undefined,
  modelCode: filters.model || undefined,
  stage: filters.stage || undefined,
  severity: filters.severity || undefined
}))

const projectOptions = computed(() => uniqueValues([...rowsFrom(badcases.value).map(item => item.projectName || item.project), ...rowsFrom(pipelines.value).map(item => item.projectName || item.project)]))
const modelOptions = computed(() => uniqueValues([...rowsFrom(tokenCost.value).map(modelName), ...rowsFrom(modelValue.value).map(modelName)]))

const overviewMetrics = computed(() => {
  const data = overview.value.summary || overview.value.metrics || overview.value
  return [
    metric('总调用次数', formatNumber(pick(data, ['totalCalls', 'calls', 'callCount'])), '平台聚合调用量', 'blue'),
    metric('总 Token', formatNumber(pick(data, ['totalTokens', 'tokens', 'tokenCount'])), '输入 + 输出 Token', 'green'),
    metric('预计总费用', formatCost(pick(data, ['totalCost', 'cost', 'estimatedCost'])), '按现有计费口径估算', 'orange'),
    metric('Badcase 数量', formatNumber(pick(data, ['badcaseCount', 'badcases', 'totalBadcases'])), '当前筛选范围内', 'red'),
    metric('流水线成功率', formatPercent(pick(data, ['pipelineSuccessRate', 'successRate'])), '自动化交付链路', 'green'),
    metric('平均阶段耗时', formatDuration(pick(data, ['avgStageDuration', 'avgStageTime'])), '阶段运行平均值', 'blue'),
    metric('失败率', formatPercent(pick(data, ['failureRate', 'pipelineFailureRate'])), '失败 / 总量', 'red'),
    metric('高风险产出', formatNumber(pick(data, ['highRiskOutputs', 'highRiskOutputCount'])), 'AI 产出治理风险', 'orange')
  ]
})

const overviewTrendRows = computed(() => {
  const data = overview.value.trends || overview.value
  const token = latestValue(data.tokenTrend || data.tokensTrend)
  const cost = latestValue(data.costTrend)
  const badcase = latestValue(data.badcaseTrend)
  const success = latestValue(data.pipelineSuccessRateTrend || data.successRateTrend)
  return [
    trend('Token 趋势', formatNumber(token), '最近一个统计点', relativeWidth(token, [token, cost, badcase]), 'blue'),
    trend('成本趋势', formatCost(cost), '最近一个统计点', relativeWidth(cost, [token, cost, badcase]), 'orange'),
    trend('Badcase 趋势', formatNumber(badcase), '最近一个统计点', relativeWidth(badcase, [token, cost, badcase]), 'red'),
    trend('成功率趋势', formatPercent(success), '最近一个统计点', percentWidth(success), 'green')
  ]
})

const overviewSuggestions = computed(() => suggestionsFrom(overview.value, ['优先关注 Badcase 集中的阶段和高频失败原因。', '对 Token 增长较快的模型或调用场景设置成本告警。', '持续观察流水线成功率低于预期的阶段。']))

const badcaseMetrics = computed(() => {
  const data = badcases.value.summary || badcases.value.metrics || badcases.value
  return [
    metric('Badcase 总量', formatNumber(pick(data, ['total', 'totalBadcases', 'badcaseCount'])), '当前筛选范围', 'red'),
    metric('P0/P1 数量', formatNumber(pick(data, ['criticalCount', 'highSeverityCount', 'p0p1Count'])), '高优先级问题', 'orange'),
    metric('涉及项目', formatNumber(pick(data, ['projectCount', 'projects'])), '关联项目数量', 'blue'),
    metric('待治理原因', formatNumber(badcaseReasons.value.length), 'Top 原因条目', 'green')
  ]
})
const badcaseStageDistribution = computed(() => distributionFrom(badcases.value.byStage || badcases.value.stageDistribution))
const badcaseTypeDistribution = computed(() => distributionFrom(badcases.value.byType || badcases.value.typeDistribution))
const badcaseSeverityDistribution = computed(() => distributionFrom(badcases.value.bySeverity || badcases.value.severityDistribution))
const badcaseReasons = computed(() => rankFrom(badcases.value.topReasons || badcases.value.reasons || badcases.value.failureReasons))
const badcaseRecords = computed(() => rowsFrom(badcases.value).slice(0, 20))

const tokenMetrics = computed(() => {
  const data = tokenCost.value.summary || tokenCost.value.metrics || tokenCost.value
  return [
    metric('总 Token', formatNumber(pick(data, ['totalTokens', 'tokens'])), '全部调用汇总', 'green'),
    metric('输入 Token', formatNumber(pick(data, ['inputTokens', 'promptTokens'])), 'Prompt 消耗', 'blue'),
    metric('输出 Token', formatNumber(pick(data, ['outputTokens', 'completionTokens'])), '模型输出消耗', 'orange'),
    metric('预计费用', formatCost(pick(data, ['totalCost', 'cost', 'estimatedCost'])), '综合计费', 'red'),
    metric('单次平均成本', formatCost(pick(data, ['avgCostPerCall', 'averageCallCost'])), '成本 / 调用', 'blue')
  ]
})
const tokenModelRows = computed(() => tableRowsFrom(tokenCost.value.byModel || tokenCost.value.modelBreakdown || tokenCost.value.models))
const topTokenCalls = computed(() => rankFrom(tokenCost.value.topCalls || tokenCost.value.topConsumers || tokenCost.value.highCostCalls))

const pipelineMetrics = computed(() => {
  const data = pipelines.value.summary || pipelines.value.metrics || pipelines.value
  return [
    metric('流水线总数', formatNumber(pick(data, ['total', 'totalPipelines', 'pipelineCount'])), '当前范围内', 'blue'),
    metric('成功数量', formatNumber(pick(data, ['success', 'successCount', 'completed'])), '成功完成', 'green'),
    metric('失败数量', formatNumber(pick(data, ['failed', 'failureCount'])), '执行失败', 'red'),
    metric('阻塞数量', formatNumber(pick(data, ['blocked', 'blockedCount'])), '等待或卡点', 'orange'),
    metric('审批拒绝率', formatPercent(pick(data, ['approvalRejectRate', 'rejectRate'])), '人工门禁', 'red'),
    metric('PRD 返工率', formatPercent(pick(data, ['prdReworkRate', 'requirementReworkRate'])), '需求侧返工', 'orange')
  ]
})
const pipelineStageRows = computed(() => tableRowsFrom(pipelines.value.stageStats || pipelines.value.stagePassRates || pipelines.value.stages).map(row => trend(row.name, formatPercent(row.passRate ?? row.rate ?? row.value), `平均耗时 ${formatDuration(row.avgDuration ?? row.duration)}`, percentWidth(row.passRate ?? row.rate ?? row.value), 'green')))
const pipelineBottlenecks = computed(() => rankFrom(pipelines.value.bottlenecks || pipelines.value.topBlockedStages || pipelines.value.blockedStages))

const modelValueRows = computed(() => tableRowsFrom(modelValue.value.models || modelValue.value.records || modelValue.value.valueModels))
const modelSuggestions = computed(() => suggestionsFrom(modelValue.value, ['对高成本且 Badcase 较多的模型优先复盘 prompt 与调用场景。', '成功率稳定、成本较低的模型可进入默认候选池。', '低风险阶段可评估降级模型以降低流水线成本。']))

function resetFilters() {
  Object.assign(filters, { range: '7d', project: '', model: '', stage: '', severity: '' })
  loadAll()
}

async function loadAll() {
  loading.value = true
  failedSections.value = []
  const query = params.value
  const [overviewData, badcaseData, tokenData, pipelineData, modelData] = await Promise.all([
    safeLoad('总览', () => api.getPlatformAnalyticsOverview(query)),
    safeLoad('Badcase', () => api.getPlatformAnalyticsBadcases(query)),
    safeLoad('Token与成本', () => api.getPlatformAnalyticsTokenCost(query)),
    safeLoad('流水线效率', () => api.getPlatformAnalyticsPipelines(query)),
    safeLoad('模型性价比', () => api.getPlatformAnalyticsModelValue(query))
  ])
  overview.value = overviewData
  badcases.value = badcaseData
  tokenCost.value = tokenData
  pipelines.value = pipelineData
  modelValue.value = modelData
  loading.value = false
}

async function safeLoad(name, request) {
  try {
    const res = await request()
    return res?.data?.data ?? res?.data ?? {}
  } catch {
    failedSections.value.push(name)
    return {}
  }
}

function metric(label, value, hint, tone) {
  return { label, value, hint, tone }
}

function trend(label, value, hint, width, tone) {
  return { label, value, hint, width, tone }
}

function pick(obj, keys, fallback = 0) {
  for (const key of keys) {
    const value = obj?.[key]
    if (value !== undefined && value !== null && value !== '') return value
  }
  return fallback
}

function rowsFrom(obj) {
  if (Array.isArray(obj)) return obj
  for (const key of ['records', 'recent', 'recentBadcases', 'badcases', 'items', 'list', 'models']) {
    const value = obj?.[key]
    if (Array.isArray(value)) return value
    if (Array.isArray(value?.records)) return value.records
  }
  return []
}

function rankFrom(source = []) {
  const rows = Array.isArray(source) ? source : Object.entries(source || {}).map(([label, value]) => ({ label, value }))
  return rows.map(row => ({
    label: row.label || row.name || row.stage || row.type || row.severity || row.reason || row.projectName || row.modelName || '-',
    value: row.value ?? row.count ?? row.total ?? row.tokens ?? row.cost ?? 0,
    hint: row.hint || row.description || row.category || row.owner || row.modelCode || ''
  })).slice(0, 10)
}

function distributionFrom(source = []) {
  const rows = rankFrom(source)
  const max = Math.max(...rows.map(item => Number(item.value) || 0), 1)
  return rows.map(item => ({ ...item, width: `${Math.max(item.value ? 5 : 0, ((Number(item.value) || 0) / max) * 100)}%` }))
}

function tableRowsFrom(source = []) {
  const rows = Array.isArray(source) ? source : Object.entries(source || {}).map(([name, value]) => (typeof value === 'object' ? { name, ...value } : { name, value }))
  return rows.map(row => ({
    ...row,
    name: modelName(row) || row.stage || row.projectName || '-',
    calls: row.calls ?? row.callCount ?? 0,
    tokens: row.tokens ?? row.totalTokens ?? 0,
    cost: row.cost ?? row.totalCost ?? row.estimatedCost ?? 0,
    badcases: row.badcases ?? row.badcaseCount ?? 0,
    successRate: row.successRate ?? row.pipelineSuccessRate ?? 0
  }))
}

function suggestionsFrom(source, fallback) {
  const values = source?.suggestions || source?.recommendations || source?.actions || []
  const normalized = Array.isArray(values) ? values : Object.values(values || {})
  const result = normalized.map(item => typeof item === 'string' ? item : item.title || item.content || item.action).filter(Boolean)
  return result.length ? result : fallback
}

function latestValue(records) {
  const last = Array.isArray(records) ? records[records.length - 1] : null
  return last ? (last.value ?? last.count ?? last.tokens ?? last.cost ?? last.rate ?? last.successRate ?? 0) : 0
}

function relativeWidth(value, values) {
  const max = Math.max(...values.map(item => Number(item) || 0), 1)
  const current = Number(value) || 0
  return `${Math.max(current ? 5 : 0, (current / max) * 100)}%`
}

function percentWidth(value) {
  return `${Math.min(Math.max(Number(value) || 0, 0), 100)}%`
}

function modelName(row) {
  return row?.name || row?.modelName || row?.modelCode
}

function uniqueValues(values) {
  return [...new Set(values.filter(Boolean).map(String))].slice(0, 30)
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function formatCost(value) {
  return `￥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function formatPercent(value) {
  return `${Number(value || 0).toFixed(1)}%`
}

function formatDuration(value) {
  const seconds = Number(value || 0)
  if (seconds >= 3600) return `${(seconds / 3600).toFixed(1)}h`
  if (seconds >= 60) return `${(seconds / 60).toFixed(1)}min`
  return `${seconds.toFixed(0)}s`
}

function severityTagType(severity) {
  if (severity === 'P0') return 'danger'
  if (severity === 'P1') return 'warning'
  if (severity === 'P2') return 'info'
  return ''
}

onMounted(loadAll)
</script>

<style scoped>
.analytics-page { display: flex; flex-direction: column; gap: 18px; color: var(--text-primary); }
.analytics-header { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.analytics-header h2 { font-size: 26px; line-height: 1.15; margin-bottom: 6px; font-weight: 800; letter-spacing: 0; }
.muted, .panel-subtitle { color: var(--text-muted); }
.header-actions { display: flex; align-items: center; gap: 10px; }
.filter-panel { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; padding: 16px; border: 1px solid var(--glass-border); border-radius: 8px; background: var(--glass-bg); }
.filter-panel .el-select { width: 160px; }
.load-alert { margin-top: -4px; }
.analytics-tabs :deep(.el-tabs__header) { margin-bottom: 16px; }
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(150px, 1fr)); gap: 14px; }
.metric-grid.compact { grid-template-columns: repeat(5, minmax(140px, 1fr)); }
.metric-card, .ops-panel { background: var(--glass-bg); border: 1px solid var(--glass-border); border-radius: 8px; padding: 18px; box-shadow: 0 10px 28px rgba(15, 23, 42, .05); }
.metric-card { min-height: 104px; display: flex; flex-direction: column; justify-content: space-between; }
.metric-card span { color: var(--text-muted); font-size: 13px; font-weight: 650; }
.metric-card strong { color: var(--accent-cyan); font-size: 27px; line-height: 1.2; font-weight: 800; font-variant-numeric: tabular-nums; }
.metric-card small { color: var(--text-muted); }
.metric-card.tone-green strong { color: var(--accent-green); }
.metric-card.tone-orange strong { color: var(--accent-orange); }
.metric-card.tone-red strong { color: var(--accent-red); }
.ops-grid { display: grid; gap: 18px; margin-top: 18px; }
.ops-grid.two { grid-template-columns: minmax(0, 1.25fr) minmax(320px, .85fr); }
.ops-grid.three { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.panel-title-row { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.panel-title { font-size: 17px; font-weight: 800; line-height: 1.3; }
.panel-subtitle { display: block; margin-top: 4px; font-size: 13px; }
.trend-stack, .rank-list, .suggestion-list { display: flex; flex-direction: column; gap: 10px; }
.trend-row-card { display: grid; grid-template-columns: minmax(120px, .48fr) minmax(120px, 1fr) 92px; align-items: center; gap: 12px; padding: 10px 0; border-top: 1px solid var(--border-color); }
.trend-meta { min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.trend-meta strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.trend-meta span { color: var(--text-muted); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.trend-bar { height: 10px; border-radius: 999px; background: #eef2f7; overflow: hidden; }
.trend-bar i { display: block; height: 100%; min-width: 0; max-width: 100%; border-radius: inherit; background: var(--accent-cyan); transition: width .35s ease; }
.trend-bar i.green { background: var(--accent-green); }
.trend-bar i.orange { background: var(--accent-orange); }
.trend-bar i.red { background: var(--accent-red); }
.trend-value { justify-self: end; color: var(--text-secondary); font-size: 13px; }
.rank-row { display: grid; grid-template-columns: 28px 1fr auto; gap: 10px; align-items: center; padding: 10px; border: 1px solid var(--border-color); border-radius: 8px; background: #ffffff; }
.rank-index { width: 24px; height: 24px; border-radius: 8px; background: #eef2ff; color: #2563eb; display: inline-flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 800; }
.rank-row div { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.rank-row strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rank-row span { color: var(--text-muted); font-size: 12px; }
.rank-row em { color: var(--accent-cyan); font-style: normal; font-weight: 800; }
.suggestion-item { padding: 12px; border: 1px solid var(--border-color); border-radius: 8px; background: #ffffff; color: var(--text-secondary); line-height: 1.55; }
.mini-table { width: 100%; border: 1px solid var(--border-color); border-radius: 8px; overflow: hidden; }
.empty-lite { min-height: 96px; display: flex; align-items: center; justify-content: center; color: var(--text-muted); border: 1px dashed var(--border-color); border-radius: 8px; background: #ffffff; }
@media (max-width: 1280px) {
  .metric-grid, .metric-grid.compact { grid-template-columns: repeat(2, minmax(160px, 1fr)); }
  .ops-grid.two, .ops-grid.three { grid-template-columns: 1fr; }
}
@media (max-width: 760px) {
  .analytics-header { flex-direction: column; align-items: flex-start; }
  .header-actions { width: 100%; flex-wrap: wrap; }
  .filter-panel .el-select { width: 100%; }
  .metric-grid, .metric-grid.compact { grid-template-columns: 1fr; }
  .trend-row-card { grid-template-columns: 1fr; gap: 8px; }
  .trend-value { justify-self: start; }
}
</style>
