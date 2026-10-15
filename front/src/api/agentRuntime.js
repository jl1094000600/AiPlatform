import { http } from './index'
import {
  TOOL_CALLS_PATH,
  TOOL_CALLS_PATH_LEGACY,
  WORKSPACE_PATH,
  isMockAllowed,
  isMockForced,
  normalizeSpans,
  normalizeToolCalls,
  riskLevelLabel,
  riskLevelType,
  RISK_LEVELS,
  toolStatusLabel,
  toolStatusType,
  isAskUserPending,
  isAskUserTimedOut,
  isAskUserTool
} from './agentRuntimeContract'

export {
  RISK_LEVELS,
  riskLevelType,
  riskLevelLabel,
  toolStatusLabel,
  toolStatusType,
  isAskUserPending,
  isAskUserTimedOut,
  isAskUserTool
}

const env = typeof import.meta !== 'undefined' ? import.meta.env : undefined
const FORCE_MOCK = isMockForced(env)
const MOCK_ALLOWED = isMockAllowed(env)

const unwrap = (response) => response?.data?.data ?? response?.data ?? null

const asResponse = (data, mocked = false) => ({ data: { data, mock: mocked } })

const isBackendMissing = (error) => {
  const status = error?.response?.status
  return !error?.response || status === 404 || status === 501 || status === 502
}

const isForbidden = (error) => error?.response?.status === 403

const isPathMissing = (error) => error?.response?.status === 404

const nowIso = () => new Date().toISOString()

const mockToolCalls = () => ([
  {
    callId: 'tc-1001',
    id: 'tc-1001',
    toolName: 'fs.read',
    riskLevel: 'READ',
    status: 'SUCCEEDED',
    startTime: nowIso(),
    durationMs: 42,
    argsSummary: 'path=src/main/java/App.java',
    outputSummary: '读取 128 行，UTF-8'
  },
  {
    callId: 'tc-1002',
    id: 'tc-1002',
    toolName: 'fs.write',
    riskLevel: 'WRITE',
    status: 'SUCCEEDED',
    startTime: nowIso(),
    durationMs: 87,
    argsSummary: 'path=src/test/java/AppTest.java',
    outputSummary: '写入 64 行'
  },
  {
    callId: 'tc-1003',
    id: 'tc-1003',
    toolName: 'shell.exec',
    riskLevel: 'EXEC',
    status: 'FAILED',
    startTime: nowIso(),
    durationMs: 3210,
    argsSummary: 'cmd=mvn -q test',
    errorMessage: '退出码 1，2 个用例失败'
  },
  {
    callId: 'tc-1004',
    id: 'tc-1004',
    toolName: 'ask_user',
    riskLevel: 'READ',
    status: 'WAITING_USER_INPUT',
    startTime: nowIso(),
    durationMs: null,
    argsSummary: '请选择测试失败时的处理策略：跳过失败用例，或修复后重跑？',
    outputSummary: '等待用户回复'
  },
  {
    callId: 'tc-1005',
    id: 'tc-1005',
    toolName: 'ask_user',
    riskLevel: 'READ',
    status: 'FAILED',
    startTime: nowIso(),
    durationMs: 60000,
    argsSummary: '是否允许执行 npm install？',
    errorMessage: 'ask_user timed out'
  }
])

const mockTraceSpans = () => ([
  { spanId: 'sp-1', spanType: 'RUN', status: 'OK', startTime: nowIso(), durationMs: 12500, summary: 'Agent Run 启动' },
  { spanId: 'sp-2', spanType: 'REPLY', status: 'OK', startTime: nowIso(), durationMs: 2100, summary: '规划下一步工具调用' },
  { spanId: 'sp-3', spanType: 'TOOL_CALL', status: 'OK', startTime: nowIso(), durationMs: 42, summary: 'fs.read src/main/java/App.java' },
  { spanId: 'sp-4', spanType: 'TOOL_CALL', status: 'OK', startTime: nowIso(), durationMs: 87, summary: 'fs.write src/test/java/AppTest.java' },
  { spanId: 'sp-5', spanType: 'TOOL_RESULT', status: 'ERROR', startTime: nowIso(), durationMs: 3210, summary: 'shell.exec mvn -q test' },
  { spanId: 'sp-6', spanType: 'ASK_USER_WAIT', status: 'WAITING', startTime: nowIso(), durationMs: null, summary: '等待用户确认测试策略' },
  { spanId: 'sp-7', spanType: 'BUDGET_CHECK', status: 'OK', startTime: nowIso(), durationMs: 3, summary: '预算检查：已用 62%' }
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

/**
 * Demo data is never a silent production fallback.
 * - default: request failures propagate as real errors
 * - only when DEV or VITE_AGENT_RUNTIME_MOCK=1 may backend-missing fall back to demo data
 * - VITE_AGENT_RUNTIME_MOCK=1 also forces demo data without hitting the network
 */
const withMockFallback = async (request, mockFactory) => {
  if (FORCE_MOCK) return asResponse(mockFactory(), true)
  try {
    const response = await request()
    return response
  } catch (error) {
    if (isForbidden(error)) throw error
    if (MOCK_ALLOWED && isBackendMissing(error)) return asResponse(mockFactory(), true)
    throw error
  }
}

const normalizeToolCallPayload = (payload) => normalizeToolCalls(payload)
const normalizeSpanPayload = (payload) => normalizeSpans(payload)

export default {
  /** GET /api/v1/agent-runs/{id}/tool-calls  (legacy fallback: /tools) */
  getAgentRunTools(runId) {
    return withMockFallback(
      async () => {
        try {
          const response = await http.get('/agent-runs/' + runId + TOOL_CALLS_PATH)
          response.data = { ...response.data, data: normalizeToolCallPayload(unwrap(response)) }
          return response
        } catch (error) {
          if (!isPathMissing(error)) throw error
          const response = await http.get('/agent-runs/' + runId + TOOL_CALLS_PATH_LEGACY)
          response.data = { ...response.data, data: normalizeToolCallPayload(unwrap(response)) }
          return response
        }
      },
      () => normalizeToolCallPayload(mockToolCalls().map(item => (
        mockResponded.has(item.id)
          ? { ...item, status: 'ANSWERED', outputSummary: '用户已回复' }
          : item
      )))
    )
  },

  /** GET /api/v1/agent-runs/{id}/trace */
  getAgentRunTrace(runId) {
    return withMockFallback(
      async () => {
        const response = await http.get('/agent-runs/' + runId + '/trace')
        response.data = { ...response.data, data: normalizeSpanPayload(unwrap(response)) }
        return response
      },
      () => normalizeSpanPayload(mockTraceSpans())
    )
  },

  /** POST /api/v1/agent-runs/{id}/tools/{callId}/respond  body { answer } */
  respondAgentRunTool(runId, callId, answer) {
    const payload = { answer }
    return withMockFallback(
      () => http.post('/agent-runs/' + runId + '/tools/' + callId + '/respond', payload),
      () => {
        mockResponded.add(String(callId))
        return { id: callId, callId, status: 'ANSWERED', answer: payload.answer }
      }
    )
  },

  /** GET /api/v1/workspaces/{runId} */
  getWorkspace(runId) {
    return withMockFallback(
      () => http.get(WORKSPACE_PATH + runId),
      () => mockWorkspace()
    )
  },

  unwrap,
  isForbidden,
  isBackendMissing,
  isMockAllowed: () => MOCK_ALLOWED
}
