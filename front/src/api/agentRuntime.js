import { http } from './index'

// 后端未就绪时自动降级 mock，便于前端预览；联调时路径已对齐契约。
const FORCE_MOCK = String(import.meta.env?.VITE_AGENT_RUNTIME_MOCK || '') === '1'

const unwrap = (response) => response?.data?.data ?? response?.data ?? null

const asResponse = (data, mocked = false) => ({ data: { data, mock: mocked } })

const isBackendMissing = (error) => {
  const status = error?.response?.status
  return !error?.response || status === 404 || status === 501 || status === 502
}

const isForbidden = (error) => error?.response?.status === 403

export const RISK_LEVELS = ['READ', 'WRITE', 'EXEC']

export const riskLevelType = (level) => ({ READ: 'success', WRITE: 'warning', EXEC: 'danger' })[level] || 'info'
export const riskLevelLabel = (level) => ({ READ: '读取', WRITE: '写入', EXEC: '执行' })[level] || level || '-'

export const toolStatusLabel = (status) => ({
  PENDING: '待执行',
  RUNNING: '执行中',
  WAITING_USER: '待用户回复',
  WAITING: '待用户回复',
  ANSWERED: '已回复',
  SUCCESS: '成功',
  SUCCEEDED: '成功',
  FAILED: '失败',
  TIMEOUT: '已超时',
  CANCELLED: '已取消',
  DENIED: '已拒绝'
})[status] || status || '-'

export const toolStatusType = (status) => ({
  PENDING: 'info',
  RUNNING: 'primary',
  WAITING_USER: 'warning',
  WAITING: 'warning',
  ANSWERED: 'success',
  SUCCESS: 'success',
  SUCCEEDED: 'success',
  FAILED: 'danger',
  TIMEOUT: 'danger',
  CANCELLED: 'info',
  DENIED: 'danger'
})[status] || 'info'

export const isAskUserPending = (tool) => {
  const name = String(tool?.toolName || '').toLowerCase()
  if (!name.includes('ask_user') && name !== 'askuser') return false
  return ['PENDING', 'RUNNING', 'WAITING_USER', 'WAITING'].includes(tool?.status)
}

export const isAskUserTimedOut = (tool) => {
  const name = String(tool?.toolName || '').toLowerCase()
  if (!name.includes('ask_user') && name !== 'askuser') return false
  return tool?.status === 'TIMEOUT'
}

export const isAskUserTool = (tool) => {
  const name = String(tool?.toolName || '').toLowerCase()
  return name.includes('ask_user') || name === 'askuser'
}

const nowIso = () => new Date().toISOString()

const mockToolCalls = () => ([
  {
    id: 'tc-1001',
    toolName: 'fs.read',
    riskLevel: 'READ',
    status: 'SUCCESS',
    startedAt: nowIso(),
    durationMs: 42,
    argsSummary: 'path=src/main/java/App.java',
    resultSummary: '读取 128 行，UTF-8'
  },
  {
    id: 'tc-1002',
    toolName: 'fs.write',
    riskLevel: 'WRITE',
    status: 'SUCCESS',
    startedAt: nowIso(),
    durationMs: 87,
    argsSummary: 'path=src/test/java/AppTest.java',
    resultSummary: '写入 64 行'
  },
  {
    id: 'tc-1003',
    toolName: 'shell.exec',
    riskLevel: 'EXEC',
    status: 'FAILED',
    startedAt: nowIso(),
    durationMs: 3210,
    argsSummary: 'cmd=mvn -q test',
    resultSummary: '退出码 1，2 个用例失败'
  },
  {
    id: 'tc-1004',
    toolName: 'ask_user',
    riskLevel: 'READ',
    status: 'WAITING_USER',
    startedAt: nowIso(),
    durationMs: null,
    argsSummary: '请选择测试失败时的处理策略：跳过失败用例，或修复后重跑？',
    resultSummary: '等待用户回复'
  },
  {
    id: 'tc-1005',
    toolName: 'ask_user',
    riskLevel: 'READ',
    status: 'TIMEOUT',
    startedAt: nowIso(),
    durationMs: 60000,
    argsSummary: '是否允许执行 npm install？',
    resultSummary: '等待超时，已按策略失败'
  }
])

const mockTraceSpans = () => ([
  { id: 'sp-1', type: 'run', status: 'SUCCESS', startedAt: nowIso(), durationMs: 12500, detail: 'Agent Run 启动' },
  { id: 'sp-2', type: 'llm', status: 'SUCCESS', startedAt: nowIso(), durationMs: 2100, detail: '规划下一步工具调用' },
  { id: 'sp-3', type: 'tool', status: 'SUCCESS', startedAt: nowIso(), durationMs: 42, detail: 'fs.read src/main/java/App.java' },
  { id: 'sp-4', type: 'tool', status: 'SUCCESS', startedAt: nowIso(), durationMs: 87, detail: 'fs.write src/test/java/AppTest.java' },
  { id: 'sp-5', type: 'tool', status: 'FAILED', startedAt: nowIso(), durationMs: 3210, detail: 'shell.exec mvn -q test' },
  { id: 'sp-6', type: 'ask_user', status: 'WAITING_USER', startedAt: nowIso(), durationMs: null, detail: '等待用户确认测试策略' },
  { id: 'sp-7', type: 'budget', status: 'SUCCESS', startedAt: nowIso(), durationMs: 3, detail: '预算检查：已用 62%' }
])

const mockWorkspace = () => ({
  status: 'RUNNING',
  workDir: '/workspace/run-demo',
  files: [
    { path: 'pom.xml', size: 2048 },
    { path: 'src/main/java/App.java', size: 4096 },
    { path: 'src/test/java/AppTest.java', size: 1536 },
    { path: 'README.md', size: 512 },
    { path: 'target/surefire-reports/TEST-AppTest.xml', size: 8192 }
  ],
  recentLogs: [
    '[INFO] Scanning for projects...',
    '[INFO] Building agent-demo 1.0.0',
    '[INFO] --- maven-surefire-plugin:3.1.2:test ---',
    '[ERROR] Tests run: 3, Failures: 2, Errors: 0, Skipped: 0',
    '[ERROR] AppTest.shouldParseRequirement FAILED',
    '[ERROR] AppTest.shouldGenerateCase FAILED'
  ].join('\n')
})

const mockResponded = new Set()

const withMockFallback = async (request, mockFactory) => {
  if (FORCE_MOCK) return asResponse(mockFactory(), true)
  try {
    const response = await request()
    return response
  } catch (error) {
    if (isForbidden(error)) throw error
    if (isBackendMissing(error)) return asResponse(mockFactory(), true)
    throw error
  }
}

export default {
  /** GET /api/v1/agent-runs/{id}/tools */
  getAgentRunTools(runId) {
    return withMockFallback(
      () => http.get('/agent-runs/' + runId + '/tools'),
      () => mockToolCalls().map(item => (mockResponded.has(item.id) ? { ...item, status: 'ANSWERED', resultSummary: '用户已回复' } : item))
    )
  },

  /** GET /api/v1/agent-runs/{id}/trace */
  getAgentRunTrace(runId) {
    return withMockFallback(
      () => http.get('/agent-runs/' + runId + '/trace'),
      () => mockTraceSpans()
    )
  },

  /** POST /api/v1/agent-runs/{id}/tools/{callId}/respond  body { answer } */
  respondAgentRunTool(runId, callId, answer) {
    const payload = { answer }
    return withMockFallback(
      () => http.post('/agent-runs/' + runId + '/tools/' + callId + '/respond', payload),
      () => {
        mockResponded.add(String(callId))
        return { id: callId, status: 'ANSWERED', answer: payload.answer }
      }
    )
  },

  /** GET /api/v1/workspaces/{runId} */
  getWorkspace(runId) {
    return withMockFallback(
      () => http.get('/workspaces/' + runId),
      () => mockWorkspace()
    )
  },

  unwrap,
  isForbidden,
  isBackendMissing
}
