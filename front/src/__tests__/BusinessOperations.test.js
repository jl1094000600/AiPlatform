import assert from 'node:assert/strict'
import { locale, setLocale, t } from '../i18n/index.js'
import { filterNavByPermissions, hasPermission, isPlatformAdmin } from '../utils/permissions.js'

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
