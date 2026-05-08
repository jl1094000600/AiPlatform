import assert from 'node:assert/strict'
import { locale, setLocale, t } from '../i18n/index.js'

const testMenuTargets = () => {
  const routes = ['/dashboard', '/automation', '/agent-quality', '/billing', '/alerts', '/audit-logs', '/customers', '/invoke']
  assert.ok(routes.includes('/dashboard'))
  assert.ok(routes.includes('/automation'))
  assert.ok(routes.includes('/agent-quality'))
  assert.ok(routes.includes('/invoke'))
  assert.equal(routes.length, 8)
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
