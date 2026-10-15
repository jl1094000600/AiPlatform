// Pure agent-runtime DTO contract helpers (no HTTP imports).
// Normalizes backend SpanView / ToolCallView field names to the shapes panels render,
// while still accepting the legacy frontend field names.

export const TOOL_CALLS_PATH = '/tool-calls'
export const TOOL_CALLS_PATH_LEGACY = '/tools'
export const WORKSPACE_PATH = '/workspaces/'

const resolveEnv = (env) => {
  if (env !== undefined) return env
  return typeof import.meta !== 'undefined' ? import.meta.env : undefined
}

/** Mock demo data is allowed only in Vite dev, or when VITE_AGENT_RUNTIME_MOCK=1 is explicit. */
export const isMockAllowed = (env) => {
  const source = resolveEnv(env)
  if (!source) return false
  if (String(source.VITE_AGENT_RUNTIME_MOCK || '') === '1') return true
  return Boolean(source.DEV)
}

/** Forced demo mode: skip network entirely (explicit opt-in only). */
export const isMockForced = (env) => {
  const source = resolveEnv(env)
  return String(source?.VITE_AGENT_RUNTIME_MOCK || '') === '1'
}

const firstDefined = (...values) => {
  for (const value of values) {
    if (value !== undefined && value !== null && value !== '') return value
  }
  return null
}

/** Accept ISO strings, "YYYY-MM-DD HH:mm:ss", and Jackson LocalDateTime arrays. */
export const toTimeValue = (value) => {
  if (value === undefined || value === null || value === '') return null
  if (typeof value === 'number') return value
  if (Array.isArray(value) && value.length >= 3) {
    const [y, mo, d, h = 0, mi = 0, s = 0] = value
    const date = new Date(y, (mo || 1) - 1, d || 1, h, mi, s)
    return Number.isNaN(date.getTime()) ? null : date.toISOString()
  }
  const text = String(value)
  const isoish = text.includes('T') ? text : text.replace(' ', 'T')
  const date = new Date(isoish)
  return Number.isNaN(date.getTime()) ? text : date.toISOString()
}

const durationBetween = (start, end) => {
  const s = new Date(start).getTime()
  const e = new Date(end).getTime()
  if (!Number.isFinite(s) || !Number.isFinite(e) || e < s) return null
  return e - s
}

/**
 * Trace span: backend SpanView uses spanId/spanType/summary/startTime;
 * legacy frontend uses id/type/detail/startedAt. Keep both readable.
 */
export const normalizeSpan = (span) => {
  if (!span || typeof span !== 'object') return null
  const id = firstDefined(span.spanId, span.id)
  const type = firstDefined(span.spanType, span.type)
  const detail = firstDefined(span.summary, span.detail, span.spanName)
  const startedAt = toTimeValue(firstDefined(span.startTime, span.startedAt))
  const endTime = toTimeValue(span.endTime)
  const rawDuration = firstDefined(span.durationMs, span.duration)
  const durationMs = rawDuration !== null ? Number(rawDuration) : durationBetween(startedAt, endTime)
  return {
    id,
    type,
    status: firstDefined(span.status),
    startedAt,
    endTime,
    durationMs: Number.isFinite(durationMs) ? durationMs : null,
    detail,
    spanName: firstDefined(span.spanName),
    spanType: type,
    spanId: id,
    startTime: startedAt,
    summary: detail
  }
}

/**
 * Tool call: backend ToolCallView uses callId/startTime/outputSummary/errorMessage;
 * legacy frontend uses id/startedAt/resultSummary/argsSummary.
 * `id` prefers callId because POST .../tools/{callId}/respond looks up by callId.
 */
export const normalizeToolCall = (call) => {
  if (!call || typeof call !== 'object') return null
  const callId = firstDefined(call.callId, call.id)
  const startedAt = toTimeValue(firstDefined(call.startTime, call.startedAt))
  const endTime = toTimeValue(call.endTime)
  const rawDuration = firstDefined(call.durationMs, call.duration)
  const durationMs = rawDuration !== null ? Number(rawDuration) : durationBetween(startedAt, endTime)
  const resultSummary = firstDefined(call.resultSummary, call.outputSummary, call.errorMessage)
  const argsSummary = firstDefined(call.argsSummary, call.inputSummary)
  return {
    id: callId,
    callId,
    rowId: firstDefined(call.id),
    stepId: firstDefined(call.stepId),
    toolName: firstDefined(call.toolName),
    riskLevel: firstDefined(call.riskLevel),
    permissionDecision: firstDefined(call.permissionDecision),
    status: firstDefined(call.status),
    startedAt,
    startTime: startedAt,
    endTime,
    durationMs: Number.isFinite(durationMs) ? durationMs : null,
    argsSummary,
    resultSummary,
    errorMessage: firstDefined(call.errorMessage),
    inputTokens: firstDefined(call.inputTokens),
    outputTokens: firstDefined(call.outputTokens)
  }
}

export const normalizeSpans = (payload) => {
  const list = Array.isArray(payload)
    ? payload
    : (payload?.spans || payload?.records || payload?.list || [])
  return list.map(normalizeSpan).filter(Boolean)
}

export const normalizeToolCalls = (payload) => {
  const list = Array.isArray(payload)
    ? payload
    : (payload?.records || payload?.list || payload?.spans || [])
  return list.map(normalizeToolCall).filter(Boolean)
}

const SPAN_TYPE_LABELS = {
  // backend SpanType
  RUN: '运行',
  REPLY: '模型',
  TOOL_CALL: '工具调用',
  TOOL_RESULT: '工具结果',
  ASK_USER_WAIT: '用户交互',
  APPROVAL_WAIT: '审批等待',
  BUDGET_CHECK: '预算检查',
  // legacy frontend types
  run: '运行',
  llm: '模型',
  tool: '工具',
  ask_user: '用户交互',
  budget: '预算',
  workspace: '沙箱',
  pipeline: '流水线'
}

const SPAN_STATUS_LABELS = {
  // backend SpanStatus
  OK: '成功',
  ERROR: '失败',
  TIMEOUT: '超时',
  CANCELLED: '已取消',
  WAITING: '待回复',
  // legacy / tool-call statuses
  SUCCESS: '成功',
  SUCCEEDED: '成功',
  FAILED: '失败',
  RUNNING: '进行中',
  PENDING: '待执行',
  WAITING_USER: '待回复',
  WAITING_USER_INPUT: '待回复',
  ANSWERED: '已回复',
  QUEUED: '待执行',
  DENIED: '已拒绝'
}

// Element-Plus tag types: success / warning / danger / info / primary
const SPAN_STATUS_TYPES = {
  OK: 'success',
  SUCCESS: 'success',
  SUCCEEDED: 'success',
  ERROR: 'danger',
  FAILED: 'danger',
  TIMEOUT: 'danger',
  DENIED: 'danger',
  CANCELLED: 'info',
  PENDING: 'info',
  QUEUED: 'info',
  WAITING: 'warning',
  WAITING_USER: 'warning',
  WAITING_USER_INPUT: 'warning',
  RUNNING: 'primary',
  ANSWERED: 'success'
}

const lookup = (map, key) => {
  if (key === undefined || key === null || key === '') return undefined
  const text = String(key)
  return map[text] ?? map[text.toUpperCase()] ?? map[text.toLowerCase()]
}

export const spanTypeLabel = (type) => lookup(SPAN_TYPE_LABELS, type) || (type == null || type === '' ? '-' : String(type))
export const spanStatusLabel = (status) => lookup(SPAN_STATUS_LABELS, status) || (status == null || status === '' ? '-' : String(status))
export const spanStatusType = (status) => lookup(SPAN_STATUS_TYPES, status) || 'info'

export const RISK_LEVELS = ['READ', 'WRITE', 'EXEC']
export const riskLevelType = (level) => ({ READ: 'success', WRITE: 'warning', EXEC: 'danger' })[String(level || '').toUpperCase()] || 'info'
export const riskLevelLabel = (level) => ({ READ: '读取', WRITE: '写入', EXEC: '执行' })[String(level || '').toUpperCase()] || level || '-'

export const toolStatusLabel = (status) => spanStatusLabel(status)
export const toolStatusType = (status) => spanStatusType(status)

export const isAskUserTool = (tool) => {
  const name = String(tool?.toolName || '').toLowerCase()
  return name.includes('ask_user') || name === 'askuser'
}

const ASK_PENDING_STATUSES = [
  'PENDING', 'QUEUED', 'RUNNING',
  'WAITING', 'WAITING_USER', 'WAITING_USER_INPUT'
]

export const isAskUserPending = (tool) => {
  if (!isAskUserTool(tool)) return false
  return ASK_PENDING_STATUSES.includes(String(tool?.status || '').toUpperCase())
}

export const isAskUserTimedOut = (tool) => {
  if (!isAskUserTool(tool)) return false
  const status = String(tool?.status || '').toUpperCase()
  if (status === 'TIMEOUT') return true
  if (status === 'FAILED') {
    const message = String(tool?.errorMessage || tool?.resultSummary || '')
    return /timeout|timed out|超时/i.test(message)
  }
  return false
}
