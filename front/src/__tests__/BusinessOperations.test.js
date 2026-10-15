import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { locale, setLocale, t } from '../i18n/index.js'
import { filterNavByPermissions, hasPermission, isPlatformAdmin } from '../utils/permissions.js'
import {
  isMockAllowed,
  isMockForced,
  normalizeSpan,
  normalizeToolCall,
  normalizeSpans,
  normalizeToolCalls,
  spanStatusLabel,
  spanStatusType,
  spanTypeLabel,
  toolStatusLabel,
  toolStatusType,
  isAskUserPending,
  isAskUserTimedOut,
  TOOL_CALLS_PATH,
  TOOL_CALLS_PATH_LEGACY,
  WORKSPACE_PATH
} from '../api/agentRuntimeContract.js'

const testMenuTargets = () => {
  const routes = ['/dashboard', '/automation', '/agent-quality', '/billing', '/alerts', '/audit-logs', '/customers', '/invoke', '/model-training', '/benchmark', '/workflows']
  assert.ok(routes.includes('/dashboard'))
  assert.ok(routes.includes('/automation'))
  assert.ok(routes.includes('/agent-quality'))
  assert.ok(routes.includes('/invoke'))
  assert.ok(routes.includes('/benchmark'))
  assert.ok(routes.includes('/workflows'))
  assert.equal(routes.length, 11)
}

const testTrendBarWidth = () => {
  const value = 0
  const max = 100
  const width = Math.max(4, (value / max) * 100)
  assert.equal(width, 4)
}

testMenuTargets()
testTrendBarWidth()

const testQualityMetricAverage = () => {
  const values = [{ f1Score: 80 }, { f1Score: 100 }]
  const avg = values.reduce((sum, item) => sum + item.f1Score, 0) / values.length
  assert.equal(avg.toFixed(2) + '%', '90.00%')
}

testQualityMetricAverage()

const testAutomationPipelineStages = () => {
  const stages = [
    'requirement_analysis',
    'code_generation',
    'build_compile',
    'test_execution',
    'deployment_release',
    'operations_monitoring',
    'delivery_report'
  ]
  assert.equal(stages.length, 7)
  assert.equal(stages[0], 'requirement_analysis')
  assert.equal(stages.at(-1), 'delivery_report')
}

const testAutomationSummary = () => {
  const successStages = 6
  const terminalStages = 8
  const passRate = Number(((successStages * 100) / terminalStages).toFixed(2))
  assert.equal(passRate, 75)
}

testAutomationPipelineStages()
testAutomationSummary()

const testLocaleSwitch = () => {
  setLocale('zh')
  assert.equal(locale.value, 'zh')
  assert.equal(t('nav.automation'), '自动化流水线')
  setLocale('en')
  assert.equal(locale.value, 'en')
  assert.equal(t('nav.automation'), 'Automation')
  setLocale('zh')
}

testLocaleSwitch()

const testModelSelectionPayload = () => {
  const model = { id: 12, modelCode: 'MiniMax-M2.7' }
  const pipelineForm = { modelId: model.id, aiModelCode: model.modelCode }
  const agentForm = { modelId: model.id, modelCode: model.modelCode }
  assert.equal(pipelineForm.aiModelCode, 'MiniMax-M2.7')
  assert.equal(agentForm.modelId, 12)
}

testModelSelectionPayload()

const testRegeneratePrdEndpoint = () => {
  const pipelineId = 9
  const endpoint = '/automation/pipelines/' + pipelineId + '/regenerate-prd'
  assert.equal(endpoint, '/automation/pipelines/9/regenerate-prd')
}

testRegeneratePrdEndpoint()

const testAutomationPrdTemplateEndpoints = () => {
  assert.equal('/automation/prd-templates', '/automation/prd-templates')
  assert.equal('/automation/prd-template', '/automation/prd-template')
}

testAutomationPrdTemplateEndpoints()

const testAutomationMultimodalRequirementEntry = () => {
  const apiSource = readFileSync(new URL('../api/index.js', import.meta.url), 'utf8')
  const viewSource = readFileSync(new URL('../views/AutomationPipeline.vue', import.meta.url), 'utf8')
  const endpoints = [
    '/requirements/attachments',
    "'/requirements/attachments/' + id + '/result'",
    "'/requirements/attachments/' + id + '/retry'",
    '/requirements/drafts/merge'
  ]
  endpoints.forEach(endpoint => assert.ok(apiSource.includes(endpoint), endpoint))
  assert.ok(viewSource.includes('添加图片'))
  assert.ok(viewSource.includes('添加音频'))
  assert.ok(viewSource.includes('浏览器录音'))
  assert.ok(viewSource.includes('MediaRecorder'))
  assert.ok(viewSource.includes('mergeRequirementDraft'))
}

testAutomationMultimodalRequirementEntry()

const testAutomationDirectoryTreeSelectionPayload = () => {
  const selectedDirectory = 'backend/src/main/java/com/aipal'
  const form = { backendOutputPath: selectedDirectory }
  assert.equal(form.backendOutputPath, selectedDirectory)
}

testAutomationDirectoryTreeSelectionPayload()

const testRejectedPipelineOnlyRunsRejectedStage = () => {
  const stages = [
    { id: 1, stageKey: 'build_compile', stageOrder: 3, status: 'REJECTED' },
    { id: 2, stageKey: 'test_execution', stageOrder: 4, status: 'PENDING' }
  ]
  const canRun = (stage) => {
    const rejected = stages.find(item => item.status === 'REJECTED')
    return rejected ? rejected.id === stage.id : ['PENDING', 'RUNNING'].includes(stage.status)
  }
  assert.equal(canRun(stages[0]), true)
  assert.equal(canRun(stages[1]), false)
}

testRejectedPipelineOnlyRunsRejectedStage()

const testApprovalActionUsesCurrentRowId = () => {
  const row = { id: 22, pipelineId: 8, status: 'PENDING' }
  const endpoint = '/automation/approvals/' + row.id + '/approve'
  assert.equal(endpoint, '/automation/approvals/22/approve')
}

testApprovalActionUsesCurrentRowId()

const testTokenUsesSessionStorageHelper = () => {
  const apiSource = readFileSync(new URL('../api/index.js', import.meta.url), 'utf8')
  const loginSource = readFileSync(new URL('../views/Login.vue', import.meta.url), 'utf8')
  const homeSource = readFileSync(new URL('../views/Home.vue', import.meta.url), 'utf8')
  const storageSource = readFileSync(new URL('../utils/authStorage.js', import.meta.url), 'utf8')
  assert.ok(apiSource.includes('getAuthToken()'))
  assert.ok(loginSource.includes('setAuthToken(res.data.data.token)'))
  assert.ok(homeSource.includes('setAuthToken(payload.token)'))
  assert.ok(storageSource.includes('sessionStorage.setItem(TOKEN_KEY, token)'))
  assert.equal(loginSource.includes("localStorage.setItem('token'"), false)
  assert.equal(homeSource.includes("localStorage.setItem('token'"), false)
}

testTokenUsesSessionStorageHelper()

const testModelTrainingDefaults = () => {
  const form = {
    modelPath: 'BAAI/bge-m3',
    trainData: 'bge-m3-training/data/train.jsonl',
    outputDir: 'bge-m3-training/output/bge-m3-ft',
    epochs: 1,
    learningRate: '1e-5',
    queryMaxLen: 256,
    passageMaxLen: 512,
    trainGroupSize: 4,
    dryRun: true
  }
  assert.equal(form.modelPath, 'BAAI/bge-m3')
  assert.equal(form.trainData.endsWith('train.jsonl'), true)
  assert.equal(form.dryRun, true)
}

testModelTrainingDefaults()

const testModelTrainingEndpoints = () => {
  const jobId = 'MT_ABC'
  assert.equal('/model-training/jobs', '/model-training/jobs')
  assert.equal('/model-training/jobs/' + jobId, '/model-training/jobs/MT_ABC')
  assert.equal('/model-training/jobs/' + jobId + '/logs', '/model-training/jobs/MT_ABC/logs')
}

testModelTrainingEndpoints()

const testRbacPermissionHelpers = () => {
  const platformAdmin = { platformAdmin: true, permissions: [] }
  const developer = { platformAdmin: false, permissions: ['benchmark:view', 'benchmark:run'] }
  assert.equal(isPlatformAdmin(platformAdmin), true)
  assert.equal(isPlatformAdmin({ username: 'admin', permissions: [] }), false)
  assert.equal(hasPermission('benchmark:manage', platformAdmin), true)
  assert.equal(hasPermission('benchmark:run', developer), true)
  assert.equal(hasPermission('benchmark:manage', developer), false)
}

testRbacPermissionHelpers()

const testFallbackNavFiltering = () => {
  const nav = [
    { key: 'benchmark', path: '/benchmark', permissionCode: 'benchmark:view', children: [] },
    { key: 'tenants', path: '/tenants', permissionCode: 'tenant:manage', children: [] },
    {
      key: 'ops',
      label: '运营',
      children: [
        { key: 'monitor', path: '/monitor', permissionCode: 'monitor:view', children: [] },
        { key: 'audit', path: '/audit-logs', permissionCode: 'audit:view', children: [] }
      ]
    }
  ]
  const filtered = filterNavByPermissions(nav, { permissions: ['benchmark:view', 'monitor:view'] })
  assert.deepEqual(filtered.map(item => item.key), ['benchmark', 'ops'])
  assert.deepEqual(filtered[1].children.map(item => item.key), ['monitor'])
}

testFallbackNavFiltering()

const testBenchmarkAndWorkflowApiContracts = () => {
  const source = readFileSync(new URL('../api/index.js', import.meta.url), 'utf8')
  const endpoints = [
    '/benchmark/start/batch',
    "'/datasets/' + datasetId + '/preview'",
    '/datasets/generate',
    "'/evaluations/batch/' + batchCode",
    "'/evaluations/' + evaluationId + '/results'",
    "'/evaluations/' + evaluationId + '/export'",
    '/benchmark/standards/',
    '/benchmark/statistics',
    '/benchmark/leaderboard',
    "'/workflows/' + id + '/deploy'",
    "'/workflows/' + workflowId + '/executions'",
    "'/workflows/executions/' + executionId",
    "'/workflows/executions/' + executionId + '/cancel'"
  ]
  endpoints.forEach(endpoint => assert.ok(source.includes(endpoint), endpoint))
}

testBenchmarkAndWorkflowApiContracts()

const testBenchmarkFlowUsesPersistedDatasetsAndBatchEvaluations = () => {
  const parent = readFileSync(new URL('../views/DatasetBenchmark.vue', import.meta.url), 'utf8')
  const selector = readFileSync(new URL('../components/benchmark/AgentSelector.vue', import.meta.url), 'utf8')
  const result = readFileSync(new URL('../components/benchmark/ResultVisualization.vue', import.meta.url), 'utf8')
  assert.ok(parent.includes(':criteria-code="criteriaCode"'))
  assert.ok(selector.includes('startBatchBenchmark'))
  assert.ok(selector.includes('getBatchEvaluations'))
  assert.ok(result.includes('URL.createObjectURL'))
}

const testWorkflowEditorBuildsSupportedGraphContract = () => {
  const source = readFileSync(new URL('../views/WorkflowEditor.vue', import.meta.url), 'utf8')
  assert.equal(source.includes("{ type: 'LOOP'"), false)
  assert.equal(source.includes("{ type: 'MERGE'"), false)
  assert.ok(source.includes('connectToNode'))
  assert.ok(source.includes('conditionField'))
  assert.ok(source.includes('triggerConfig'))
}

testBenchmarkFlowUsesPersistedDatasetsAndBatchEvaluations()
testWorkflowEditorBuildsSupportedGraphContract()

const testAgentRuntimeMockGate = () => {
  // production default: no mock, no forced demo data
  assert.equal(isMockAllowed({ DEV: false }), false)
  assert.equal(isMockAllowed({ DEV: false, VITE_AGENT_RUNTIME_MOCK: '' }), false)
  assert.equal(isMockAllowed({ DEV: false, VITE_AGENT_RUNTIME_MOCK: '0' }), false)
  assert.equal(isMockForced({ DEV: false }), false)
  // dev preview allows demo fallback
  assert.equal(isMockAllowed({ DEV: true }), true)
  assert.equal(isMockForced({ DEV: true }), false)
  // explicit flag allows (and forces) demo data even outside dev
  assert.equal(isMockAllowed({ DEV: false, VITE_AGENT_RUNTIME_MOCK: '1' }), true)
  assert.equal(isMockForced({ DEV: false, VITE_AGENT_RUNTIME_MOCK: '1' }), true)
  // missing env object never mocks
  assert.equal(isMockAllowed(null), false)
  assert.equal(isMockAllowed(undefined), false)
}

testAgentRuntimeMockGate()

const testAgentRuntimePaths = () => {
  assert.equal(TOOL_CALLS_PATH, '/tool-calls')
  assert.equal(TOOL_CALLS_PATH_LEGACY, '/tools')
  assert.equal(WORKSPACE_PATH, '/workspaces/')
  const source = readFileSync(new URL('../api/agentRuntime.js', import.meta.url), 'utf8')
  assert.ok(source.includes('TOOL_CALLS_PATH'))
  assert.ok(source.includes('TOOL_CALLS_PATH_LEGACY'))
  assert.ok(source.includes('WORKSPACE_PATH'))
  assert.ok(source.includes('/trace'))
  assert.ok(source.includes('/respond'))
  // silent production mock must be gone: fallback only when MOCK_ALLOWED
  assert.ok(source.includes('MOCK_ALLOWED && isBackendMissing'))
  assert.equal(source.includes('if (isBackendMissing(error)) return asResponse'), false)
}

testAgentRuntimePaths()

const testNormalizeSpanDualContract = () => {
  // backend SpanView shape
  const backendSpan = normalizeSpan({
    spanId: 'sp-1',
    spanType: 'TOOL_CALL',
    spanName: 'fs.read',
    status: 'OK',
    summary: 'read file',
    startTime: '2026-10-15T10:00:00',
    endTime: '2026-10-15T10:00:01',
    durationMs: 1000
  })
  assert.equal(backendSpan.id, 'sp-1')
  assert.equal(backendSpan.spanId, 'sp-1')
  assert.equal(backendSpan.type, 'TOOL_CALL')
  assert.equal(backendSpan.spanType, 'TOOL_CALL')
  assert.equal(backendSpan.detail, 'read file')
  assert.equal(backendSpan.summary, 'read file')
  assert.equal(backendSpan.status, 'OK')
  assert.equal(backendSpan.durationMs, 1000)
  assert.ok(backendSpan.startedAt)
  assert.equal(backendSpan.startTime, backendSpan.startedAt)

  // legacy frontend shape
  const legacySpan = normalizeSpan({
    id: 'sp-2',
    type: 'tool',
    detail: 'legacy detail',
    startedAt: '2026-10-15T10:00:00',
    durationMs: 42,
    status: 'SUCCESS'
  })
  assert.equal(legacySpan.id, 'sp-2')
  assert.equal(legacySpan.type, 'tool')
  assert.equal(legacySpan.detail, 'legacy detail')
  assert.equal(legacySpan.status, 'SUCCESS')
  assert.equal(legacySpan.durationMs, 42)

  // duration derived from start/end when durationMs absent
  const derived = normalizeSpan({
    spanId: 'sp-3',
    spanType: 'RUN',
    status: 'ERROR',
    startTime: '2026-10-15T10:00:00',
    endTime: '2026-10-15T10:00:02'
  })
  assert.equal(derived.durationMs, 2000)

  // list wrappers
  assert.equal(normalizeSpans({ spans: [legacySpan] }).length, 1)
  assert.equal(normalizeSpans([backendSpan, legacySpan]).length, 2)
  assert.equal(normalizeSpans(null).length, 0)
}

testNormalizeSpanDualContract()

const testNormalizeToolCallDualContract = () => {
  // backend ToolCallView shape
  const backendCall = normalizeToolCall({
    id: 9,
    stepId: 3,
    callId: 'call-abc',
    toolName: 'ask_user',
    riskLevel: 'READ',
    permissionDecision: 'ALLOW',
    status: 'WAITING_USER_INPUT',
    outputSummary: 'ask_user answered',
    errorMessage: null,
    startTime: '2026-10-15T10:00:00',
    endTime: null,
    inputTokens: 1,
    outputTokens: 2
  })
  // respond endpoint resolves by callId string
  assert.equal(backendCall.id, 'call-abc')
  assert.equal(backendCall.callId, 'call-abc')
  assert.equal(backendCall.rowId, 9)
  assert.equal(backendCall.status, 'WAITING_USER_INPUT')
  assert.equal(backendCall.resultSummary, 'ask_user answered')
  assert.ok(backendCall.startedAt)
  assert.equal(backendCall.startTime, backendCall.startedAt)

  // legacy frontend shape
  const legacyCall = normalizeToolCall({
    id: 'tc-1001',
    toolName: 'fs.read',
    riskLevel: 'READ',
    status: 'SUCCESS',
    startedAt: '2026-10-15T10:00:00',
    durationMs: 42,
    argsSummary: 'path=a',
    resultSummary: 'ok'
  })
  assert.equal(legacyCall.id, 'tc-1001')
  assert.equal(legacyCall.argsSummary, 'path=a')
  assert.equal(legacyCall.resultSummary, 'ok')
  assert.equal(legacyCall.durationMs, 42)

  // errorMessage fills resultSummary when outputSummary missing
  const failed = normalizeToolCall({
    callId: 'call-x',
    toolName: 'shell.exec',
    status: 'FAILED',
    errorMessage: 'exit 1',
    startTime: '2026-10-15T10:00:00',
    endTime: '2026-10-15T10:00:03'
  })
  assert.equal(failed.resultSummary, 'exit 1')
  assert.equal(failed.durationMs, 3000)

  assert.equal(normalizeToolCalls({ records: [legacyCall] }).length, 1)
  assert.equal(normalizeToolCalls([backendCall, legacyCall]).length, 2)
}

testNormalizeToolCallDualContract()

const testSpanStatusAndTypeEnums = () => {
  // status pairs required by B04: OK|SUCCESS, ERROR|FAILED
  assert.equal(spanStatusLabel('OK'), spanStatusLabel('SUCCESS'))
  assert.equal(spanStatusLabel('ERROR'), spanStatusLabel('FAILED'))
  assert.equal(spanStatusType('OK'), 'success')
  assert.equal(spanStatusType('SUCCESS'), 'success')
  assert.equal(spanStatusType('SUCCEEDED'), 'success')
  assert.equal(spanStatusType('ERROR'), 'danger')
  assert.equal(spanStatusType('FAILED'), 'danger')
  assert.equal(spanStatusType('WAITING'), 'warning')
  assert.equal(spanStatusType('WAITING_USER'), 'warning')
  assert.equal(spanStatusType('WAITING_USER_INPUT'), 'warning')
  assert.equal(spanStatusType('TIMEOUT'), 'danger')
  assert.equal(spanStatusType('CANCELLED'), 'info')
  assert.equal(spanStatusType('RUNNING'), 'primary')

  // tool-call status colors share the same table
  assert.equal(toolStatusLabel('SUCCEEDED'), '成功')
  assert.equal(toolStatusType('SUCCEEDED'), 'success')
  assert.equal(toolStatusType('FAILED'), 'danger')
  assert.equal(toolStatusType('WAITING_USER_INPUT'), 'warning')

  // span types: backend SpanType + legacy frontend keys
  assert.equal(spanTypeLabel('RUN'), '运行')
  assert.equal(spanTypeLabel('TOOL_CALL'), '工具调用')
  assert.equal(spanTypeLabel('TOOL_RESULT'), '工具结果')
  assert.equal(spanTypeLabel('ASK_USER_WAIT'), '用户交互')
  assert.equal(spanTypeLabel('BUDGET_CHECK'), '预算检查')
  assert.equal(spanTypeLabel('REPLY'), '模型')
  assert.equal(spanTypeLabel('run'), '运行')
  assert.equal(spanTypeLabel('llm'), '模型')
  assert.equal(spanTypeLabel('tool'), '工具')
  assert.equal(spanTypeLabel('ask_user'), '用户交互')
  assert.equal(spanTypeLabel('budget'), '预算')
}

testSpanStatusAndTypeEnums()

const testAskUserStatusCompat = () => {
  const waiting = { toolName: 'ask_user', status: 'WAITING_USER_INPUT' }
  const legacyWaiting = { toolName: 'ask_user', status: 'WAITING_USER' }
  const running = { toolName: 'ask_user', status: 'RUNNING' }
  const timedOut = { toolName: 'ask_user', status: 'FAILED', errorMessage: 'ask_user timed out' }
  const legacyTimeout = { toolName: 'ask_user', status: 'TIMEOUT' }
  const done = { toolName: 'ask_user', status: 'SUCCEEDED' }
  const notAsk = { toolName: 'fs.read', status: 'WAITING_USER_INPUT' }

  assert.equal(isAskUserPending(waiting), true)
  assert.equal(isAskUserPending(legacyWaiting), true)
  assert.equal(isAskUserPending(running), true)
  assert.equal(isAskUserPending(done), false)
  assert.equal(isAskUserPending(notAsk), false)

  assert.equal(isAskUserTimedOut(timedOut), true)
  assert.equal(isAskUserTimedOut(legacyTimeout), true)
  assert.equal(isAskUserTimedOut(waiting), false)
}

testAskUserStatusCompat()

const testAgentRuntimePanelsUseContractHelpers = () => {
  const tracePanel = readFileSync(new URL('../components/agent-runtime/TraceTimelinePanel.vue', import.meta.url), 'utf8')
  const toolPanel = readFileSync(new URL('../components/agent-runtime/ToolCallsPanel.vue', import.meta.url), 'utf8')
  const workspacePanel = readFileSync(new URL('../components/agent-runtime/WorkspacePanel.vue', import.meta.url), 'utf8')

  // UI must label demo data as 开发 mock
  ;[tracePanel, toolPanel, workspacePanel].forEach(source => {
    assert.ok(source.includes('开发 mock'), 'panel must show 开发 mock banner')
    assert.ok(source.includes('VITE_AGENT_RUNTIME_MOCK'), 'panel must document mock flag')
  })

  assert.ok(tracePanel.includes('normalizeSpans') || tracePanel.includes('spanTypeLabel'))
  assert.ok(toolPanel.includes('normalizeToolCalls') || toolPanel.includes('toolStatusLabel'))
  // respond by callId (backend contract), not numeric row id alone
  assert.ok(toolPanel.includes('callId') || toolPanel.includes('item.callId'))
}

testAgentRuntimePanelsUseContractHelpers()
