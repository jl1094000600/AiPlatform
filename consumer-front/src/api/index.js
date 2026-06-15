export const AUTH_TOKEN_KEY = 'consumer-token'
export const AUTH_USER_KEY = 'consumer-user'

export class ApiError extends Error {
  constructor(message, { status = 0, code = 0, data = null } = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.data = data
  }
}

function browserStorage() {
  return typeof window === 'undefined' ? null : window.localStorage
}

export function getToken(storage = browserStorage()) {
  return storage?.getItem(AUTH_TOKEN_KEY) || ''
}

export function getCurrentUser(storage = browserStorage()) {
  const value = storage?.getItem(AUTH_USER_KEY)
  if (!value) return null
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}

export function saveAuth(login, storage = browserStorage()) {
  if (!login?.token) throw new ApiError('登录响应缺少 token')
  const { token, ...user } = login
  storage?.setItem(AUTH_TOKEN_KEY, token)
  storage?.setItem(AUTH_USER_KEY, JSON.stringify(user))
  return user
}

export function clearAuth(storage = browserStorage()) {
  storage?.removeItem(AUTH_TOKEN_KEY)
  storage?.removeItem(AUTH_USER_KEY)
  storage?.removeItem('consumer-auth')
}

export function createRequestId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `req-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export function normalizeAttachment(item = {}) {
  const task = item.latestTask || item.parseTask || item.task || {}
  const result = task.editedResult ?? task.userResult ?? task.revisedResult ?? task.resultText
    ?? item.editedResult ?? item.userResult ?? item.revisedResult ?? item.resultText
    ?? item.parsedText ?? item.content ?? task.result ?? task.rawResult ?? ''
  return {
    ...item,
    id: item.id ?? item.attachmentId,
    fileName: item.fileName || item.originalFilename || item.originalName || item.name || '未命名附件',
    mediaType: item.mediaType || item.attachmentType || item.type || 'UNKNOWN',
    status: String(task.status || item.parseStatus || item.taskStatus || item.status || 'PENDING').toUpperCase(),
    resultText: typeof result === 'string' ? result : JSON.stringify(result, null, 2),
    errorMessage: task.errorMessage || task.errorSummary || task.error || item.errorMessage || item.errorSummary || item.error || ''
  }
}

export function normalizeAttachmentList(data) {
  const items = Array.isArray(data) ? data : data?.records || data?.items || data?.attachments || data?.list || []
  return items.map(normalizeAttachment)
}

export function normalizeMergedDraft(data) {
  if (typeof data === 'string') return data
  return data?.mergedText || data?.draftText || data?.requirementText || data?.content || data?.draft || ''
}

export const extractDraftText = normalizeMergedDraft

export function normalizePrd(data = {}) {
  if (typeof data === 'string') {
    return { pipelineId: null, status: 'WAITING_APPROVAL', content: data, errorMessage: '' }
  }
  const pipeline = data.pipeline || data.prd || {}
  const content = data.content ?? data.prdContent ?? data.markdown ?? data.resultText
    ?? data.artifactContent ?? data.result ?? pipeline.content ?? pipeline.prdContent ?? pipeline.markdown ?? ''
  return {
    ...data,
    pipelineId: data.pipelineId ?? data.id ?? pipeline.pipelineId ?? pipeline.id,
    status: String(data.status || data.stageStatus || data.prdStatus || data.pipelineStatus
      || pipeline.status || pipeline.prdStatus || (content ? 'WAITING_APPROVAL' : 'QUEUED')).toUpperCase(),
    content: typeof content === 'string' ? content : JSON.stringify(content, null, 2),
    errorMessage: data.errorMessage || data.error || data.failureReason || pipeline.errorMessage || pipeline.error || ''
  }
}

export function isAttachmentPending(status) {
  return ['PENDING', 'QUEUED', 'RUNNING', 'PROCESSING', 'UPLOADING'].includes(String(status).toUpperCase())
}

export function isPrdPending(status) {
  return ['QUEUED', 'PENDING', 'RUNNING', 'PROCESSING', 'GENERATING'].includes(String(status).toUpperCase())
}

export function unwrapResult(payload, status = 200) {
  if (payload && typeof payload === 'object' && Object.hasOwn(payload, 'code')) {
    if (payload.code !== 200) {
      throw new ApiError(payload.message || '请求失败', {
        status,
        code: payload.code,
        data: payload.data
      })
    }
    return payload.data
  }
  return payload
}

export async function apiRequest(path, options = {}) {
  const {
    method = 'GET', body, headers = {}, token = getToken(), fetchImpl = globalThis.fetch
  } = options
  const requestHeaders = { Accept: 'application/json', ...headers }
  if (token) requestHeaders.Authorization = `Bearer ${token}`

  let requestBody = body
  const isFormData = typeof FormData !== 'undefined' && body instanceof FormData
  if (body != null && !isFormData) {
    requestHeaders['Content-Type'] = 'application/json'
    requestBody = JSON.stringify(body)
  }

  let response
  try {
    response = await fetchImpl(path, { method, headers: requestHeaders, body: requestBody })
  } catch (error) {
    throw new ApiError(error?.message || '网络连接失败')
  }

  const text = await response.text()
  let payload = null
  if (text) {
    try {
      payload = JSON.parse(text)
    } catch {
      payload = text
    }
  }

  if (!response.ok) {
    if (response.status === 401) clearAuth()
    throw new ApiError(payload?.message || `请求失败（${response.status}）`, {
      status: response.status,
      code: payload?.code || response.status,
      data: payload?.data
    })
  }

  try {
    return unwrapResult(payload, response.status)
  } catch (error) {
    if (error.code === 401) clearAuth()
    throw error
  }
}

export const authApi = {
  login(credentials) {
    return apiRequest('/api/v1/auth/login', { method: 'POST', body: credentials, token: '' })
  }
}

export const requirementApi = {
  uploadAttachment(requestId, file) {
    const body = new FormData()
    body.append('requestId', requestId)
    body.append('file', file)
    return apiRequest('/api/v1/requirements/attachments', { method: 'POST', body })
  },
  listAttachments(requestId) {
    const query = new URLSearchParams({ requestId })
    return apiRequest(`/api/v1/requirements/attachments?${query}`).then(normalizeAttachmentList)
  },
  updateAttachmentResult(id, resultText) {
    return apiRequest(`/api/v1/requirements/attachments/${id}/result`, {
      method: 'PUT', body: { resultText }
    })
  },
  retryAttachment(id) {
    return apiRequest(`/api/v1/requirements/attachments/${id}/retry`, { method: 'POST' })
  },
  deleteAttachment(id) {
    return apiRequest(`/api/v1/requirements/attachments/${id}`, { method: 'DELETE' })
  },
  mergeDraft(originalText, attachmentIds) {
    return apiRequest('/api/v1/requirements/drafts/merge', {
      method: 'POST', body: { originalText, attachmentIds }
    }).then(normalizeMergedDraft)
  },
  createPrd(title, requirementText, requestId) {
    return apiRequest('/api/v1/requirements/prd', {
      method: 'POST', body: { title, requirementText, requestId }
    })
  },
  getPrd(pipelineId) {
    return apiRequest(`/api/v1/requirements/prd/${pipelineId}`).then(normalizePrd)
  }
}
