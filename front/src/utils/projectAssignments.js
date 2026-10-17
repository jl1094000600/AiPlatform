export const assignmentTargetText = (type) => ({
  AGENT_RUN: '协作任务',
  AUTOMATION_APPROVAL: '审批项',
  TASK_DRAFT: '任务草案'
})[type] || '项目事项'

export const assignmentStatusText = (status) => ({
  ASSIGNED: '待接受',
  ACCEPTED: '处理中',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
})[status] || status || '-'

export function activeMyAssignments(assignments) {
  return (assignments || []).filter(item => ['ASSIGNED', 'ACCEPTED'].includes(item.status))
}

// Every project change invalidates older responses, including a switch back to the same project.
export function createLatestRequestGuard() {
  let version = 0
  return {
    begin: () => ++version,
    isCurrent: (requestVersion) => requestVersion === version,
    invalidate: () => ++version
  }
}

export function parseTeamRunInput(value = '') {
  const text = value.trim()
  if (!text) return {}
  try {
    const parsed = JSON.parse(text)
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      // Assignment belongs to the assignment API, never to the free-form Run input.
      const { assignee, assigneeUserId, assignmentReason, ...input } = parsed
      return input
    }
  } catch {
    // A plain instruction is also valid input.
  }
  return { instruction: text }
}

export function buildTeamRunPayload(form, businessId, canManageAssignments) {
  const assigneeUserId = canManageAssignments ? form.assigneeUserId || null : null
  return {
    agentId: form.agentId,
    businessType: form.businessType,
    businessId,
    input: parseTeamRunInput(form.inputText),
    assigneeUserId,
    assignmentReason: assigneeUserId ? (form.assignmentReason || '').trim() : null
  }
}

// Keep the retry identity until a successful submission or project switch clears the form.
export function retainTeamRunBusinessId(form, createBusinessId) {
  form.businessId = (form.businessId || '').trim() || createBusinessId()
  return form.businessId
}

export function projectResponseData(response) {
  const result = response?.data
  if (result?.code !== undefined && result.code !== 200) {
    const error = new Error(result.message || '请求失败')
    error.response = { ...response, status: result.code }
    throw error
  }
  return result?.data
}

export const projectErrorStatus = (error) => error?.response?.status || error?.response?.data?.code

export function projectErrorText(error, fallback = '项目数据加载失败，请重试') {
  const status = projectErrorStatus(error)
  if (status === 401) return '登录已失效，请重新登录'
  if (status === 403) return '权限或项目成员资格已变更，请刷新权限后重试'
  if (status === 404) return '项目或关联事项已不可访问，请刷新列表'
  if (status === 409) return '该指派已被其他人更新，已刷新最新状态，请确认后重试'
  if (status === 400) return error?.response?.data?.message || '操作不符合当前状态，请刷新后重试'
  return fallback
}

export const isConfirmationCancelled = (error) => ['cancel', 'close'].includes(error)

export function formatProjectTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

export function assigneeLabel(assigneeName, assigneeUserId) {
  return assigneeName || `用户 ${assigneeUserId}`
}
