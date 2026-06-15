import test from 'node:test'
import assert from 'node:assert/strict'

import {
  AUTH_TOKEN_KEY,
  apiRequest,
  authApi,
  isPrdPending,
  normalizeAttachmentList,
  normalizePrd,
  requirementApi,
  saveAuth
} from '../src/api/index.js'

function jsonResponse(payload, status = 200) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}

test('saveAuth stores Result.data token and user fields', () => {
  const values = new Map()
  const storage = {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key)
  }

  const user = saveAuth({ token: 'jwt-token', userId: 7, username: 'demo' }, storage)
  assert.equal(values.get(AUTH_TOKEN_KEY), 'jwt-token')
  assert.deepEqual(user, { userId: 7, username: 'demo' })
})

test('login uses username/password and unwraps Result.data', async () => {
  const originalFetch = globalThis.fetch
  globalThis.fetch = async (path, options) => {
    assert.equal(path, '/api/v1/auth/login')
    assert.equal(options.method, 'POST')
    assert.deepEqual(JSON.parse(options.body), { username: 'alice', password: 'secret' })
    assert.equal(options.headers.Authorization, undefined)
    return jsonResponse({ code: 200, message: 'success', data: { token: 'token-1' } })
  }

  try {
    assert.deepEqual(await authApi.login({ username: 'alice', password: 'secret' }), { token: 'token-1' })
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('requirement API sends merge and PRD contracts', async () => {
  const calls = []
  const originalFetch = globalThis.fetch
  globalThis.fetch = async (path, options) => {
    calls.push({ path, body: options.body && JSON.parse(options.body) })
    if (path.endsWith('/drafts/merge')) {
      return jsonResponse({ code: 200, data: { mergedText: 'merged requirement' } })
    }
    return jsonResponse({ code: 200, data: { pipelineId: 42, status: 'QUEUED' } })
  }

  try {
    assert.equal(await requirementApi.mergeDraft('original', [1, 2]), 'merged requirement')
    await requirementApi.createPrd('title', 'confirmed', 'request-1')
    assert.deepEqual(calls, [
      { path: '/api/v1/requirements/drafts/merge', body: { originalText: 'original', attachmentIds: [1, 2] } },
      { path: '/api/v1/requirements/prd', body: { title: 'title', requirementText: 'confirmed', requestId: 'request-1' } }
    ])
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('attachment and PRD responses normalize supported states', () => {
  assert.deepEqual(normalizeAttachmentList({ attachments: [{ attachmentId: 3, originalFilename: 'voice.webm', taskStatus: 'running' }] }), [
    {
      attachmentId: 3,
      originalFilename: 'voice.webm',
      taskStatus: 'running',
      id: 3,
      fileName: 'voice.webm',
      mediaType: 'UNKNOWN',
      status: 'RUNNING',
      resultText: '',
      errorMessage: ''
    }
  ])
  assert.deepEqual(normalizePrd({ id: 9, status: 'WAITING_APPROVAL', content: '# PRD' }), {
    id: 9,
    pipelineId: 9,
    status: 'WAITING_APPROVAL',
    content: '# PRD',
    errorMessage: ''
  })
  assert.equal(isPrdPending('QUEUED'), true)
  assert.equal(isPrdPending('RUNNING'), true)
  assert.equal(isPrdPending('WAITING_APPROVAL'), false)
  assert.equal(isPrdPending('FAILED'), false)
})

test('apiRequest reports Result business errors', async () => {
  await assert.rejects(
    apiRequest('/failure', {
      token: '',
      fetchImpl: async () => jsonResponse({ code: 400, message: 'invalid requirement', data: null })
    }),
    (error) => error.code === 400 && error.message === 'invalid requirement'
  )
})
