import axios from 'axios'
import router from '../router'

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 10000
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

// 响应拦截器：401时跳转登录页
api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      router.push('/login')
    }
    return Promise.reject(error)
  }
)

export default {
  // Agent APIs
  getAgents(params) {
    return api.get('/agents', { params })
  },
  getAgent(id) {
    return api.get('/agents/' + id)
  },
  createAgent(data) {
    return api.post('/agents', data)
  },
  updateAgent(id, data) {
    return api.put('/agents/' + id, data)
  },
  deleteAgent(id) {
    return api.delete('/agents/' + id)
  },
  publishAgent(id) {
    return api.post('/agents/' + id + '/publish')
  },
  offlineAgent(id) {
    return api.post('/agents/' + id + '/offline')
  },
  
  // Monitor APIs
  getCallRecords(params) {
    return api.get('/a2a/graph/agents/' + params.agentId + '/calls', { params })
  },
  getTrace(traceId) {
    return api.get('/monitor/traces/' + traceId)
  },
  getAgentGraph() {
    return api.get('/monitor/agent-graph')
  },
  evaluateAgentGraphEdge(data) {
    return api.post('/agent-graph/edges/evaluate', data)
  },
  createAgentGraphEdge(data) {
    return api.post('/agent-graph/edges', data)
  },
  deleteAgentGraphEdge(edgeId) {
    return api.delete('/agent-graph/edges/' + edgeId)
  },
  getRealtimeData() {
    return api.get('/monitor/realtime')
  },
  getExecutionChain(params) {
    return api.get('/a2a/graph/executions/' + params.taskId)
  },
  exportGraph() {
    return api.post('/a2a/graph/export')
  },
  
  // Model APIs
  getModels(params) {
    return api.get('/models', { params })
  },
  getModel(id) {
    return api.get('/models/' + id)
  },
  createModel(data) {
    return api.post('/models', data)
  },
  updateModel(id, data) {
    return api.put('/models/' + id, data)
  },
  deleteModel(id) {
    return api.delete('/models/' + id)
  },

  // Model training APIs
  createModelTrainingJob(data) {
    return api.post('/model-training/jobs', data)
  },
  getModelTrainingJobs() {
    return api.get('/model-training/jobs')
  },
  getModelTrainingJob(id) {
    return api.get('/model-training/jobs/' + id)
  },
  getModelTrainingLogs(id) {
    return api.get('/model-training/jobs/' + id + '/logs')
  },
  getModelTrainingDatasets() {
    return api.get('/model-training/datasets')
  },
  importModelTrainingDataset(data) {
    return api.post('/model-training/datasets/import', data)
  },
  previewModelTrainingMockDataset(data) {
    return api.post('/model-training/datasets/mock', data, { timeout: 180000 })
  },
  saveModelTrainingDataset(data) {
    return api.post('/model-training/datasets/save', data)
  },

  // Skill APIs
  getSkills(params) {
    return api.get('/skills', { params })
  },
  getEnabledSkills() {
    return api.get('/skills/enabled')
  },
  getSkill(id) {
    return api.get('/skills/' + id)
  },
  generateSkillDraft(data) {
    return api.post('/skills/generate', data, { timeout: 60000 })
  },
  createSkill(data) {
    return api.post('/skills', data)
  },
  updateSkill(id, data) {
    return api.put('/skills/' + id, data)
  },
  deleteSkill(id) {
    return api.delete('/skills/' + id)
  },

  // User memory APIs
  getUserMemories(params) {
    return api.get('/user-memories', { params })
  },
  getShortTermMemories(params) {
    return api.get('/user-memories/short-term', { params })
  },
  compressUserMemories(params) {
    return api.post('/user-memories/compress', null, { params })
  },
  clearShortTermMemories(params) {
    return api.delete('/user-memories/short-term', { params })
  },

  // Dataset APIs
  getDatasets(params) {
    return api.get('/datasets', { params })
  },

  // Agent quality APIs
  getAgentRuntimeConfig(agentId) {
    return api.get('/agents/' + agentId + '/runtime-config')
  },
  updateAgentRuntimeConfig(agentId, data) {
    return api.put('/agents/' + agentId + '/runtime-config', data)
  },
  getAgentQualitySummary() {
    return api.get('/agent-quality/summary')
  },
  getAgentQualityTrends(params) {
    return api.get('/agent-quality/trends', { params })
  },
  runAgentQualityEvaluation(data) {
    return api.post('/agent-quality/evaluations', data)
  },
  getAgentQualityEvaluations(params) {
    return api.get('/agent-quality/evaluations', { params })
  },
  getAgentQualityResults(runId) {
    return api.get('/agent-quality/evaluations/' + runId + '/results')
  },

  // RAG APIs
  createRagIngestion(data) {
    return api.post('/rag/ingestions', data)
  },
  getRagIngestions(params) {
    return api.get('/rag/ingestions', { params })
  },
  getChromaCollections(params) {
    return api.get('/rag/chroma/collections', { params })
  },
  getChromaDocuments(collectionId, params) {
    return api.get('/rag/chroma/collections/' + collectionId + '/documents', { params })
  },
  
  // Auth APIs
  login(data) {
    return api.post('/auth/login', data)
  },
  register(data) {
    return api.post('/auth/register', data)
  },
  getCurrentUser() {
    return api.get('/auth/me')
  },
  getAuthMenus() {
    return api.get('/auth/menus')
  },
  getAuthTenants() {
    return api.get('/auth/tenants')
  },
  switchTenant(data) {
    return api.post('/auth/switch-tenant', data)
  },

  // Tenant admin APIs
  getTenants(params) {
    return api.get('/tenant-admin/tenants', { params })
  },
  createTenant(data) {
    return api.post('/tenant-admin/tenants', data)
  },
  updateTenant(id, data) {
    return api.put('/tenant-admin/tenants/' + id, data)
  },
  getTenantMembers(params) {
    return api.get('/tenant-admin/members', { params })
  },
  addTenantMember(data) {
    return api.post('/tenant-admin/members', data)
  },
  updateTenantMemberRoles(userId, roleIds, params) {
    return api.put('/tenant-admin/members/' + userId + '/roles', roleIds, { params })
  },
  getTenantMemberRoles(userId, params) {
    return api.get('/tenant-admin/members/' + userId + '/roles', { params })
  },
  getTenantRoles(params) {
    return api.get('/tenant-admin/roles', { params })
  },
  getTenantPermissions() {
    return api.get('/tenant-admin/permissions')
  },
  updateRolePermissions(roleId, permissionIds, params) {
    return api.put('/tenant-admin/roles/' + roleId + '/permissions', permissionIds, { params })
  },
  getRolePermissions(roleId, params) {
    return api.get('/tenant-admin/roles/' + roleId + '/permissions', { params })
  },
  getTenantMenus() {
    return api.get('/tenant-admin/menus')
  },
  saveTenantMenu(data) {
    return data.id ? api.put('/tenant-admin/menus/' + data.id, data) : api.post('/tenant-admin/menus', data)
  },

  // Benchmark APIs
  getBenchmarkHistory() {
    return api.get('/benchmark/history')
  },
  uploadDataset(formData) {
    return api.post('/benchmark/dataset/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  generateSimData(data) {
    return api.post('/benchmark/simdata/generate', data)
  },
  startBenchmark(data) {
    return api.post('/benchmark/start', data)
  },
  getBenchmarkProgress(benchmarkId) {
    return api.get('/benchmark/progress/' + benchmarkId)
  },
  saveBenchmarkStandards(data) {
    return api.post('/benchmark/standards', data)
  },
  getBenchmarkResult(benchmarkId) {
    return api.get('/benchmark/result/' + benchmarkId)
  },
  exportBenchmarkReport(benchmarkId) {
    return api.get('/benchmark/export/' + benchmarkId, { responseType: 'blob' })
  },

  // Workflow APIs
  getWorkflows(params) {
    return api.get('/workflows', { params })
  },
  getWorkflow(id) {
    return api.get('/workflows/' + id)
  },
  createWorkflow(data) {
    return api.post('/workflows', data)
  },
  updateWorkflow(id, data) {
    return api.put('/workflows/' + id, data)
  },
  deleteWorkflow(id) {
    return api.delete('/workflows/' + id)
  },
  triggerWorkflow(id) {
    return api.post('/workflows/' + id + '/trigger')
  },
  getWorkflowExecutions(workflowId) {
    return api.get('/workflows/' + workflowId + '/executions')
  },
  getAllWorkflowExecutions() {
    return api.get('/workflows/executions')
  },

  // Business dashboard APIs
  getDashboardSummary() {
    return api.get('/business-dashboard/summary')
  },
  getDashboardTrends() {
    return api.get('/business-dashboard/trends')
  },
  getDashboardExceptions() {
    return api.get('/business-dashboard/exceptions')
  },

  // Billing APIs
  getBillingUsage(params) {
    return api.get('/billing/usage', { params })
  },
  getBillingCostTrends(params) {
    return api.get('/billing/cost-trends', { params })
  },
  getBillingBudgets(params) {
    return api.get('/billing/budgets', { params })
  },
  createBillingBudget(data) {
    return api.post('/billing/budgets', data)
  },
  updateBillingBudget(id, data) {
    return api.put('/billing/budgets/' + id, data)
  },
  exportBilling(params) {
    return api.get('/billing/bills/export', { params, responseType: 'blob' })
  },

  // Alert APIs
  getAlertRules(params) {
    return api.get('/alerts/rules', { params })
  },
  createAlertRule(data) {
    return api.post('/alerts/rules', data)
  },
  updateAlertRule(id, data) {
    return api.put('/alerts/rules/' + id, data)
  },
  deleteAlertRule(id) {
    return api.delete('/alerts/rules/' + id)
  },
  getAlertEvents(params) {
    return api.get('/alerts/events', { params })
  },
  ackAlertEvent(id) {
    return api.post('/alerts/events/' + id + '/ack')
  },
  evaluateAlerts() {
    return api.post('/alerts/evaluate')
  },

  // Audit APIs
  getAuditLogs(params) {
    return api.get('/audit-logs', { params })
  },
  exportAuditLogs(params) {
    return api.get('/audit-logs/export', { params, responseType: 'blob' })
  },

  // Customer APIs
  getCustomers(params) {
    return api.get('/customers', { params })
  },
  getCustomer(id) {
    return api.get('/customers/' + id)
  },
  createCustomer(data) {
    return api.post('/customers', data)
  },
  updateCustomer(id, data) {
    return api.put('/customers/' + id, data)
  },
  deleteCustomer(id) {
    return api.delete('/customers/' + id)
  },
  freezeCustomer(id) {
    return api.post('/customers/' + id + '/freeze')
  },
  adjustCustomerBalance(id, data) {
    return api.post('/customers/' + id + '/balance/adjust', data)
  },

  // Low-code invocation APIs
  createInvocation(data) {
    return api.post('/invocations', data)
  },
  getInvocations(params) {
    return api.get('/invocations', { params })
  },
  retryInvocation(id) {
    return api.post('/invocations/' + id + '/retry')
  },
  downloadInvocation(id) {
    return api.get('/invocations/' + id + '/download', { responseType: 'blob' })
  },

  // Automation pipeline APIs
  getAutomationSummary() {
    return api.get('/automation/reports/summary')
  },
  getAutomationPipelines(params) {
    return api.get('/automation/pipelines', { params })
  },
  createAutomationPipeline(data) {
    return api.post('/automation/pipelines', data)
  },
  getAutomationPipeline(id) {
    return api.get('/automation/pipelines/' + id)
  },
  runAutomationStage(stageId) {
    return api.post('/automation/stages/' + stageId + '/run')
  },
  regenerateAutomationPrd(pipelineId) {
    return api.post('/automation/pipelines/' + pipelineId + '/regenerate-prd')
  },
  regenerateAutomationCode(pipelineId) {
    return api.post('/automation/pipelines/' + pipelineId + '/regenerate-code')
  },
  getAutomationCodeTree(pipelineId) {
    return api.get('/automation/pipelines/' + pipelineId + '/code-tree')
  },
  getAutomationCodeFile(pipelineId, path) {
    return api.get('/automation/pipelines/' + pipelineId + '/code-file', { params: { path } })
  },
  getAutomationCodeTemplates() {
    return api.get('/automation/code-templates')
  },
  getAutomationPrdTemplates() {
    return api.get('/automation/prd-templates')
  },
  getAutomationCodeTemplate(fileName) {
    return api.get('/automation/code-template', { params: { fileName } })
  },
  getAutomationPrdTemplate(fileName) {
    return api.get('/automation/prd-template', { params: { fileName } })
  },
  saveAutomationCodeTemplate(fileName, content) {
    return api.put('/automation/code-template', { content }, { params: { fileName } })
  },
  saveAutomationPrdTemplate(fileName, content) {
    return api.put('/automation/prd-template', { content }, { params: { fileName } })
  },
  getAutomationProjectDirectories() {
    return api.get('/automation/project-directories')
  },
  getAutomationApprovals(params) {
    return api.get('/automation/approvals', { params })
  },
  getAutomationApprovalDocument(id) {
    return api.get('/automation/approvals/' + id + '/document')
  },
  approveAutomation(id, data) {
    return api.post('/automation/approvals/' + id + '/approve', data)
  },
  getAutomationDeployProfiles(params) {
    return api.get('/automation/deploy-profiles', { params })
  },
  getEnabledAutomationDeployProfiles() {
    return api.get('/automation/deploy-profiles/enabled')
  },
  getAutomationDeployProfile(id) {
    return api.get('/automation/deploy-profiles/' + id)
  },
  createAutomationDeployProfile(data) {
    return api.post('/automation/deploy-profiles', data)
  },
  updateAutomationDeployProfile(id, data) {
    return api.put('/automation/deploy-profiles/' + id, data)
  },
  deleteAutomationDeployProfile(id) {
    return api.delete('/automation/deploy-profiles/' + id)
  },
  getAutomationDeployRuns(pipelineId) {
    return api.get('/automation/pipelines/' + pipelineId + '/deploy-runs')
  },
  getCodeQualityStandards(params) {
    return api.get('/code-quality/standards', { params })
  },
  getEnabledCodeQualityStandards() {
    return api.get('/code-quality/standards/enabled')
  },
  getCodeQualityStandard(id) {
    return api.get('/code-quality/standards/' + id)
  },
  createCodeQualityStandard(data) {
    return api.post('/code-quality/standards', data)
  },
  updateCodeQualityStandard(id, data) {
    return api.put('/code-quality/standards/' + id, data)
  },
  deleteCodeQualityStandard(id) {
    return api.delete('/code-quality/standards/' + id)
  },
  getAutomationCodeQualityRuns(pipelineId) {
    return api.get('/automation/pipelines/' + pipelineId + '/code-quality-runs')
  },
  getAutomationCodeQualityIssues(runId) {
    return api.get('/automation/code-quality-runs/' + runId + '/issues')
  },
  getAutomationCodeQualityEvidence(runId) {
    return api.get('/automation/code-quality-runs/' + runId + '/evidence')
  },
  getAiOutputGovernanceRecords(params) {
    return api.get('/ai-output-governance/records', { params })
  },
  getAiOutputGovernanceRecord(id) {
    return api.get('/ai-output-governance/records/' + id)
  },
  getAiOutputGovernancePolicyTemplates(params) {
    return api.get('/ai-output-governance/policy-templates', { params })
  },
  getEnabledAiOutputGovernancePolicyTemplates() {
    return api.get('/ai-output-governance/policy-templates/enabled')
  },
  createAiOutputGovernancePolicyTemplate(data) {
    return api.post('/ai-output-governance/policy-templates', data)
  },
  updateAiOutputGovernancePolicyTemplate(id, data) {
    return api.put('/ai-output-governance/policy-templates/' + id, data)
  },
  deleteAiOutputGovernancePolicyTemplate(id) {
    return api.delete('/ai-output-governance/policy-templates/' + id)
  },
  getPromptEngineeringPrompts(params) {
    return api.get('/prompt-engineering/prompts', { params })
  },
  getPromptEngineeringPrompt(id) {
    return api.get('/prompt-engineering/prompts/' + id)
  },
  createPromptEngineeringPrompt(data) {
    return api.post('/prompt-engineering/prompts', data)
  },
  updatePromptEngineeringPrompt(id, data) {
    return api.put('/prompt-engineering/prompts/' + id, data)
  },
  deletePromptEngineeringPrompt(id) {
    return api.delete('/prompt-engineering/prompts/' + id)
  },
  getPromptEngineeringVersions(promptId) {
    return api.get('/prompt-engineering/prompts/' + promptId + '/versions')
  },
  createPromptEngineeringVersion(promptId, data) {
    return api.post('/prompt-engineering/prompts/' + promptId + '/versions', data)
  },
  getPromptEngineeringTestCases(promptId) {
    return api.get('/prompt-engineering/prompts/' + promptId + '/test-cases')
  },
  createPromptEngineeringTestCase(promptId, data) {
    return api.post('/prompt-engineering/prompts/' + promptId + '/test-cases', data)
  },
  updatePromptEngineeringTestCase(promptId, caseId, data) {
    return api.put('/prompt-engineering/prompts/' + promptId + '/test-cases/' + caseId, data)
  },
  deletePromptEngineeringTestCase(promptId, caseId) {
    return api.delete('/prompt-engineering/prompts/' + promptId + '/test-cases/' + caseId)
  },
  evaluatePromptEngineeringVersion(versionId, data) {
    return api.post('/prompt-engineering/versions/' + versionId + '/evaluate', data)
  },
  getPromptEngineeringEvalRuns(versionId) {
    return api.get('/prompt-engineering/versions/' + versionId + '/eval-runs')
  },
  getPromptEngineeringEvalResults(runId) {
    return api.get('/prompt-engineering/eval-runs/' + runId + '/results')
  },
  optimizePromptEngineeringVersion(versionId, data) {
    return api.post('/prompt-engineering/versions/' + versionId + '/optimize', data)
  },
  getPromptEngineeringOptimizeRuns(versionId) {
    return api.get('/prompt-engineering/versions/' + versionId + '/optimize-runs')
  },
  publishPromptEngineeringVersion(versionId) {
    return api.post('/prompt-engineering/versions/' + versionId + '/publish')
  }
}
