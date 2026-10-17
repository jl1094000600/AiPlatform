import assert from 'node:assert/strict'
import {
  activeMyAssignments, assignmentTargetText, assignmentStatusText,
  createLatestRequestGuard, parseTeamRunInput, buildTeamRunPayload, retainTeamRunBusinessId,
  projectResponseData, projectErrorStatus, projectErrorText, isConfirmationCancelled
} from '../utils/projectAssignments.js'

let passed = 0
const check = (name, run) => {
  run()
  passed += 1
}

check('pending todo states exclude terminal and unknown states', () => {
  const items = ['ASSIGNED', 'ACCEPTED', 'COMPLETED', 'CANCELLED', 'UNKNOWN'].map(status => ({ status }))
  assert.deepEqual(activeMyAssignments(items).map(item => item.status), ['ASSIGNED', 'ACCEPTED'])
  assert.deepEqual(activeMyAssignments(null), [])
  assert.equal(items.length, 5)
})

check('project switches reject an older response even after switching back', () => {
  const guard = createLatestRequestGuard()
  const projectA = guard.begin()
  const projectB = guard.begin()
  const projectAAgain = guard.begin()
  assert.equal(guard.isCurrent(projectA), false)
  assert.equal(guard.isCurrent(projectB), false)
  assert.equal(guard.isCurrent(projectAAgain), true)
})

check('revoked membership and unmount invalidate outstanding responses', () => {
  const guard = createLatestRequestGuard()
  const outstanding = guard.begin()
  guard.invalidate()
  assert.equal(guard.isCurrent(outstanding), false)
  assert.equal(guard.isCurrent(guard.begin()), true)
})

check('separate panels keep independent request generations', () => {
  const members = createLatestRequestGuard()
  const todos = createLatestRequestGuard()
  const memberRequest = members.begin()
  const todoRequest = todos.begin()
  members.invalidate()
  assert.equal(members.isCurrent(memberRequest), false)
  assert.equal(todos.isCurrent(todoRequest), true)
})

check('JSON input cannot carry a legacy assignment', () => {
  assert.deepEqual(parseTeamRunInput(JSON.stringify({
    instruction: 'review', assignee: { id: 9 }, assigneeUserId: 9, assignmentReason: 'legacy', projectKey: 'body-project'
  })), { instruction: 'review', projectKey: 'body-project' })
})

check('plain instructions and invalid JSON remain useful run input', () => {
  assert.deepEqual(parseTeamRunInput('  review release  '), { instruction: 'review release' })
  assert.deepEqual(parseTeamRunInput('{invalid'), { instruction: '{invalid' })
  assert.deepEqual(parseTeamRunInput(''), {})
  assert.deepEqual(parseTeamRunInput('[1,2]'), { instruction: '[1,2]' })
  assert.deepEqual(parseTeamRunInput('null'), { instruction: 'null' })
})

check('manager uses top-level structured assignment and trusted business id', () => {
  const form = { agentId: 2, businessType: 'DELIVERY_REQUEST', inputText: '{"instruction":"run","assigneeUserId":99}', assigneeUserId: 8, assignmentReason: '  release owner  ' }
  assert.deepEqual(buildTeamRunPayload(form, 'trusted-business', true), {
    agentId: 2, businessType: 'DELIVERY_REQUEST', businessId: 'trusted-business',
    input: { instruction: 'run' }, assigneeUserId: 8, assignmentReason: 'release owner'
  })
})

check('non-manager cannot submit a stale assignee selection', () => {
  const payload = buildTeamRunPayload({ agentId: 2, inputText: '{"assignee":{"id":8}}', assigneeUserId: 8, assignmentReason: 'stale' }, 'business', false)
  assert.equal(payload.assigneeUserId, null)
  assert.equal(payload.assignmentReason, null)
  assert.deepEqual(payload.input, {})
})

check('unassigned run omits assignment reason', () => {
  const payload = buildTeamRunPayload({ inputText: '', assigneeUserId: null, assignmentReason: 'stale' }, 'business', true)
  assert.equal(payload.assigneeUserId, null)
  assert.equal(payload.assignmentReason, null)
})

check('a failed run response keeps the same generated business id and assignment payload on retry', () => {
  let generated = 0
  const createId = () => `DELIVERY-${++generated}`
  const form = { agentId: 2, businessType: 'DELIVERY_REQUEST', businessId: '', inputText: 'deliver', assigneeUserId: 8, assignmentReason: 'owner' }
  const first = buildTeamRunPayload(form, retainTeamRunBusinessId(form, createId), true)
  // A lost response leaves the form intact, as does cancelling the confirmation before retrying.
  assert.equal(form.businessId, 'DELIVERY-1')
  const retry = buildTeamRunPayload(form, retainTeamRunBusinessId(form, createId), true)
  assert.deepEqual(retry, first)
  assert.equal(retainTeamRunBusinessId(form, createId), first.businessId)
  assert.equal(generated, 1)
})

check('successful submission and project switches clear the retry identity for a new run', () => {
  let generated = 0
  const createId = () => `DELIVERY-${++generated}`
  const form = { businessId: '' }
  const first = retainTeamRunBusinessId(form, createId)
  form.businessId = '' // Successful submission clears the form.
  const afterSuccess = retainTeamRunBusinessId(form, createId)
  form.businessId = '' // Project context reset clears the form.
  const afterSwitch = retainTeamRunBusinessId(form, createId)
  assert.notEqual(afterSuccess, first)
  assert.notEqual(afterSwitch, afterSuccess)
  assert.equal(generated, 3)
})

check('an explicit business id is normalized and retained without generating another id', () => {
  const form = { businessId: '  REQUIREMENT-42  ' }
  const createId = () => { throw new Error('explicit ids must not be replaced') }
  assert.equal(retainTeamRunBusinessId(form, createId), 'REQUIREMENT-42')
  assert.equal(form.businessId, 'REQUIREMENT-42')
})

check('success envelope preserves actual data including an empty list', () => {
  assert.deepEqual(projectResponseData({ data: { code: 200, data: [] } }), [])
  assert.deepEqual(projectResponseData({ data: { code: 200, data: { id: 7 } } }), { id: 7 })
})

check('business errors never become empty success', () => {
  for (const code of [400, 401, 403, 404, 409, 500]) {
    assert.throws(() => projectResponseData({ data: { code, message: 'failed' } }), error => {
      assert.equal(projectErrorStatus(error), code)
      assert.equal(error.message, 'failed')
      return true
    })
  }
})

check('conflict reports update and refresh instead of an automatic retry', () => {
  assert.match(projectErrorText({ response: { status: 409 } }), /已.*更新/)
  assert.match(projectErrorText({ response: { data: { code: 409 } } }), /最新状态/)
})

check('authorization, missing data and server failure have distinct messages', () => {
  const messages = [401, 403, 404, 500].map(status => projectErrorText({ response: { status } }))
  assert.equal(new Set(messages).size, 4)
  assert.match(messages[1], /成员资格/)
  assert.equal(projectErrorText(new Error('server'), '重新加载失败'), '重新加载失败')
  assert.equal(projectErrorText({ response: { status: 400, data: { message: '处理人失效' } } }), '处理人失效')
})

check('confirmation cancellation excludes real request failures', () => {
  assert.equal(isConfirmationCancelled('cancel'), true)
  assert.equal(isConfirmationCancelled('close'), true)
  assert.equal(isConfirmationCancelled(new Error('server')), false)
})

check('labels match the final backend contract', () => {
  assert.equal(assignmentTargetText('AUTOMATION_APPROVAL'), '审批项')
  assert.equal(assignmentTargetText('TASK_DRAFT'), '任务草案')
  assert.equal(assignmentStatusText('ACCEPTED'), '处理中')
  assert.equal(assignmentStatusText('CANCELLED'), '已取消')
})

console.log(`Project assignment helper tests passed (${passed}/${passed})`)
