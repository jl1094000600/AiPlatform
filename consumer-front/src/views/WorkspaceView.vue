<template>
  <main class="workspace-page">
    <aside class="workspace-sidebar">
      <RouterLink class="brand compact" to="/">
        <img class="brand-logo" src="/think-land-logo.svg" alt="Think Land logo" />
        <strong>Think Land</strong>
      </RouterLink>
      <div class="workspace-user">
        <span>{{ userDisplayName }}</span>
        <small>{{ currentUser?.tenant?.name || currentUser?.tenant?.tenantName || '个人工作区' }}</small>
      </div>
      <button class="side-link active">创意工作台</button>
      <button class="side-link" disabled>我的 PRD</button>
      <button class="side-link" disabled>流程图</button>
      <button class="side-link" disabled>任务计划</button>
      <button class="side-link logout-link" @click="logout">退出登录</button>
    </aside>

    <section class="workspace-main">
      <header class="workspace-header">
        <div>
          <p class="eyebrow">个人使用页面</p>
          <h1>今天想把哪个想法变成产品？</h1>
        </div>
        <button class="primary-btn small" :disabled="primaryBusy" @click="handlePrimaryAction">
          {{ primaryActionText }}
        </button>
      </header>

      <p v-if="pageError" class="workspace-alert error" role="alert">
        <span>{{ pageError }}</span>
        <button type="button" @click="pageError = ''">关闭</button>
      </p>
      <p v-if="pageMessage" class="workspace-alert success" role="status">{{ pageMessage }}</p>

      <section class="composer-panel">
        <label class="composer-title-field">
          <span>需求标题</span>
          <input v-model.trim="title" type="text" maxlength="100" placeholder="例如：健康饮食计划助手" />
        </label>
        <textarea
          v-model="idea"
          placeholder="例如：我想做一个帮助上班族管理健康饮食的小程序，需要能生成计划、提醒打卡、记录反馈。"
        ></textarea>

        <div class="media-toolbar" aria-label="添加需求附件">
          <label class="media-action">
            <span>添加图片</span>
            <input type="file" accept="image/*" multiple @change="selectImages" />
          </label>
          <label class="media-action">
            <span>添加音频</span>
            <input type="file" accept="audio/*" multiple @change="selectAudio" />
          </label>
          <button
            class="media-action record-action"
            :class="{ recording }"
            type="button"
            :disabled="recordingBusy"
            @click="toggleRecording"
          >
            {{ recording ? `停止录音 ${recordingTime}` : '浏览器录音' }}
          </button>
          <small>支持多张图片、多段音频；每个附件会独立解析。</small>
        </div>

        <div v-if="allAttachments.length" class="attachment-list">
          <article v-for="attachment in allAttachments" :key="attachment.id" class="attachment-card">
            <div class="attachment-heading">
              <div>
                <strong>{{ attachment.name }}</strong>
                <small>{{ attachmentTypeText(attachment) }}{{ formatSize(attachment.size) }}</small>
              </div>
              <span class="status-badge" :class="statusClass(attachment.status)">
                {{ statusText(attachment.status) }}
              </span>
            </div>

            <p v-if="attachment.error" class="attachment-error">{{ attachment.error }}</p>
            <textarea
              v-if="canEditAttachment(attachment)"
              v-model="attachmentEdits[attachment.id]"
              class="attachment-result"
              aria-label="附件解析结果"
              @input="markDraftStale"
            ></textarea>

            <div class="attachment-actions">
              <button
                v-if="canEditAttachment(attachment) && attachmentDirty(attachment)"
                type="button"
                :disabled="busyAttachmentIds.has(attachment.id)"
                @click="saveAttachmentResult(attachment)"
              >
                {{ busyAttachmentIds.has(attachment.id) ? '保存中...' : '保存修改' }}
              </button>
              <button
                v-if="attachment.status === 'FAILED'"
                type="button"
                :disabled="busyAttachmentIds.has(attachment.id)"
                @click="retryAttachment(attachment)"
              >重试解析</button>
              <button
                v-if="attachment.local && attachment.status === 'UPLOAD_FAILED'"
                type="button"
                @click="retryUpload(attachment)"
              >重新上传</button>
              <button
                class="danger-link"
                type="button"
                :disabled="attachment.status === 'UPLOADING' || busyAttachmentIds.has(attachment.id)"
                @click="removeAttachment(attachment)"
              >删除</button>
            </div>
          </article>
        </div>

        <div class="composer-actions">
          <span>{{ composerHint }}</span>
          <button :disabled="primaryBusy" @click="handlePrimaryAction">{{ primaryActionText }}</button>
        </div>
      </section>

      <section v-if="draftReady" class="draft-panel result-panel">
        <div class="panel-title">
          <span>合并需求草稿</span>
          <b>{{ draftStale ? '内容已变更' : '等待确认' }}</b>
        </div>
        <p class="panel-description">请检查并编辑草稿。确认后才会创建真实 PRD 流水线。</p>
        <textarea v-model="mergedDraft" class="merged-draft" @input="draftStale = false"></textarea>
        <div class="draft-actions">
          <button type="button" :disabled="mergeLoading" @click="mergeDraft">
            {{ mergeLoading ? '正在重新合并...' : '重新合并' }}
          </button>
          <button class="primary-btn" type="button" :disabled="prdLoading || !mergedDraft.trim()" @click="confirmAndCreatePrd">
            {{ prdLoading ? '正在创建...' : '确认需求并生成 PRD' }}
          </button>
        </div>
      </section>

      <section class="workspace-grid">
        <article class="result-panel prd-panel">
          <div class="panel-title">
            <span>PRD 草稿</span>
            <b :class="statusClass(prdState.status)">{{ prdStatusText }}</b>
          </div>
          <p v-if="prdState.error" class="attachment-error">{{ prdState.error }}</p>
          <pre v-if="prdState.content" class="prd-content">{{ prdState.content }}</pre>
          <div v-else class="result-empty">
            <strong>{{ prdLoading ? '正在生成真实 PRD' : '等待需求确认' }}</strong>
            <p>{{ prdLoading ? '流水线正在后台运行，本页会自动刷新结果。' : '填写文字，或添加图片与音频后开始生成。' }}</p>
          </div>
        </article>

        <article class="result-panel flow-panel">
          <div class="panel-title">
            <span>业务流程图</span>
            <b>后续阶段</b>
          </div>
          <div class="workspace-flow">
            <i>用户输入</i><b></b><i>需求理解</i><b></b><i>PRD 生成</i><b></b><i>流程拆解</i>
          </div>
        </article>

        <article class="result-panel tasks-panel">
          <div class="panel-title">
            <span>当前处理链路</span>
            <b>真实 API</b>
          </div>
          <div class="task-list">
            <p>文字、图片和音频共同描述需求</p>
            <p>逐附件查看、修改或重试解析结果</p>
            <p>合并并确认最终需求草稿</p>
            <p>创建流水线并轮询 PRD 正文</p>
          </div>
        </article>
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ApiError,
  clearAuth,
  extractDraftText,
  getCurrentUser,
  isPrdPending,
  normalizeAttachment,
  normalizePrd,
  requirementApi
} from '@/api'

const ACTIVE_ATTACHMENT_STATUSES = new Set(['PENDING', 'RUNNING', 'PROCESSING', 'UPLOADING'])
const SUCCESS_ATTACHMENT_STATUSES = new Set(['SUCCEEDED', 'SUCCESS', 'COMPLETED'])
const SUCCESS_PRD_STATUSES = new Set(['SUCCEEDED', 'SUCCESS', 'COMPLETED', 'DONE'])
const FAILED_PRD_STATUSES = new Set(['FAILED', 'ERROR', 'CANCELLED'])
const REQUEST_ID_KEY = 'consumer-requirement-request-id'

const router = useRouter()
const currentUser = ref(getCurrentUser())
const title = ref('')
const idea = ref('我想做一个帮助个人创作者把想法整理成 PRD 和流程图的 AI 工作台。')
const requestId = ref(getOrCreateRequestId())
const attachments = ref([])
const localUploads = ref([])
const attachmentEdits = reactive({})
const savedAttachmentResults = reactive({})
const busyAttachmentIds = reactive(new Set())
const pageError = ref('')
const pageMessage = ref('')
const mergeLoading = ref(false)
const draftReady = ref(false)
const draftStale = ref(false)
const mergedDraft = ref('')
const prdLoading = ref(false)
const prdState = reactive({ id: null, status: 'IDLE', content: '', error: '' })
const recording = ref(false)
const recordingBusy = ref(false)
const recordingSeconds = ref(0)

let attachmentPollTimer
let prdPollTimer
let recordingTimer
let mediaRecorder
let recordingStream
let recordingChunks = []

const allAttachments = computed(() => [...localUploads.value, ...attachments.value])
const hasActiveAttachments = computed(() => allAttachments.value.some((item) => ACTIVE_ATTACHMENT_STATUSES.has(item.status)))
const successfulAttachments = computed(() => attachments.value.filter((item) => SUCCESS_ATTACHMENT_STATUSES.has(item.status)))
const userDisplayName = computed(() => currentUser.value?.realName || currentUser.value?.username || '已登录用户')
const recordingTime = computed(() => `${String(Math.floor(recordingSeconds.value / 60)).padStart(2, '0')}:${String(recordingSeconds.value % 60).padStart(2, '0')}`)
const primaryBusy = computed(() => mergeLoading.value || prdLoading.value || hasActiveAttachments.value)
const primaryActionText = computed(() => {
  if (prdLoading.value) return 'PRD 生成中'
  if (mergeLoading.value) return '正在合并草稿'
  if (hasActiveAttachments.value) return '等待附件解析'
  if (successfulAttachments.value.length) return '生成合并草稿'
  return '确认并生成 PRD'
})
const composerHint = computed(() => {
  if (hasActiveAttachments.value) return '附件正在独立解析，完成后即可合并需求草稿。'
  if (successfulAttachments.value.length) return '先生成合并草稿，检查附件解析内容后再确认生成 PRD。'
  return '纯文本需求可以直接创建 PRD；附件能力不会增加必经步骤。'
})
const prdStatusText = computed(() => {
  if (prdState.status === 'IDLE') return '等待生成'
  if (SUCCESS_PRD_STATUSES.has(prdState.status)) return '已完成'
  if (FAILED_PRD_STATUSES.has(prdState.status)) return '生成失败'
  return '生成中'
})

watch(idea, markDraftStale)

onMounted(() => {
  refreshAttachments({ silent: true })
  attachmentPollTimer = window.setInterval(() => {
    if (hasActiveAttachments.value) refreshAttachments({ silent: true })
  }, 2000)
})

onBeforeUnmount(() => {
  window.clearInterval(attachmentPollTimer)
  window.clearTimeout(prdPollTimer)
  stopRecordingResources()
})

function getOrCreateRequestId() {
  const existing = window.sessionStorage.getItem(REQUEST_ID_KEY)
  if (existing) return existing
  const next = globalThis.crypto?.randomUUID?.() || `req-${Date.now()}-${Math.random().toString(16).slice(2)}`
  window.sessionStorage.setItem(REQUEST_ID_KEY, next)
  return next
}

function markDraftStale() {
  if (draftReady.value) draftStale.value = true
}

function setMessage(message) {
  pageMessage.value = message
  window.setTimeout(() => {
    if (pageMessage.value === message) pageMessage.value = ''
  }, 3200)
}

function handleRequestError(error, fallback) {
  pageError.value = error?.message || fallback
  if (error instanceof ApiError && (error.status === 401 || error.code === 401)) {
    router.replace({ name: 'login', query: { redirect: '/workspace' } })
  }
}

async function refreshAttachments({ silent = false } = {}) {
  try {
    const response = await requirementApi.listAttachments(requestId.value)
    const values = Array.isArray(response) ? response : response?.records || response?.list || response?.items || []
    const next = values.map(toWorkspaceAttachment).filter((item) => item.id != null)
    for (const attachment of next) {
      const id = String(attachment.id)
      const saved = savedAttachmentResults[id]
      if (saved === undefined || attachmentEdits[id] === saved) {
        attachmentEdits[id] = attachment.resultText
        savedAttachmentResults[id] = attachment.resultText
      }
    }
    attachments.value = next
  } catch (error) {
    if (!silent) handleRequestError(error, '附件状态刷新失败')
  }
}

function toWorkspaceAttachment(value) {
  const normalized = normalizeAttachment(value)
  return {
    ...normalized,
    name: normalized.fileName,
    type: normalized.mediaType,
    mimeType: normalized.mimeType || normalized.contentType || '',
    size: normalized.sizeBytes ?? normalized.fileSize ?? normalized.size ?? 0,
    error: normalized.errorMessage
  }
}

function selectImages(event) {
  uploadFiles([...event.target.files], 'image/')
  event.target.value = ''
}

function selectAudio(event) {
  uploadFiles([...event.target.files], 'audio/')
  event.target.value = ''
}

async function uploadFiles(files, expectedMimePrefix) {
  const accepted = files.filter((file) => file.type.startsWith(expectedMimePrefix))
  if (accepted.length !== files.length) pageError.value = '已忽略类型不匹配的文件'
  await Promise.all(accepted.map(uploadFile))
  markDraftStale()
}

async function uploadFile(file) {
  const local = reactive({
    id: `upload-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    local: true,
    file,
    name: file.name,
    type: file.type.startsWith('image/') ? 'IMAGE' : 'AUDIO',
    mimeType: file.type,
    size: file.size,
    status: 'UPLOADING',
    error: ''
  })
  localUploads.value.push(local)

  try {
    await requirementApi.uploadAttachment(requestId.value, file)
    localUploads.value = localUploads.value.filter((item) => item.id !== local.id)
    await refreshAttachments()
  } catch (error) {
    local.status = 'UPLOAD_FAILED'
    local.error = error?.message || '上传失败'
  }
}

async function retryUpload(attachment) {
  localUploads.value = localUploads.value.filter((item) => item.id !== attachment.id)
  await uploadFile(attachment.file)
}

function canEditAttachment(attachment) {
  return !attachment.local && SUCCESS_ATTACHMENT_STATUSES.has(attachment.status)
}

function attachmentDirty(attachment) {
  const id = String(attachment.id)
  return attachmentEdits[id] !== savedAttachmentResults[id]
}

async function saveAttachmentResult(attachment, { silent = false } = {}) {
  const id = String(attachment.id)
  busyAttachmentIds.add(attachment.id)
  try {
    await requirementApi.updateAttachmentResult(attachment.id, attachmentEdits[id] || '')
    savedAttachmentResults[id] = attachmentEdits[id] || ''
    if (!silent) setMessage(`已保存 ${attachment.name} 的解析结果`)
  } catch (error) {
    handleRequestError(error, '保存附件结果失败')
    throw error
  } finally {
    busyAttachmentIds.delete(attachment.id)
  }
}

async function saveDirtyAttachmentResults() {
  const dirty = successfulAttachments.value.filter(attachmentDirty)
  for (const attachment of dirty) await saveAttachmentResult(attachment, { silent: true })
}

async function retryAttachment(attachment) {
  busyAttachmentIds.add(attachment.id)
  pageError.value = ''
  try {
    await requirementApi.retryAttachment(attachment.id)
    markDraftStale()
    await refreshAttachments()
  } catch (error) {
    handleRequestError(error, '重试附件解析失败')
  } finally {
    busyAttachmentIds.delete(attachment.id)
  }
}

async function removeAttachment(attachment) {
  if (attachment.local) {
    localUploads.value = localUploads.value.filter((item) => item.id !== attachment.id)
    return
  }
  if (!window.confirm(`确定删除附件“${attachment.name}”吗？`)) return

  busyAttachmentIds.add(attachment.id)
  try {
    await requirementApi.deleteAttachment(attachment.id)
    attachments.value = attachments.value.filter((item) => item.id !== attachment.id)
    delete attachmentEdits[String(attachment.id)]
    delete savedAttachmentResults[String(attachment.id)]
    markDraftStale()
  } catch (error) {
    handleRequestError(error, '删除附件失败')
  } finally {
    busyAttachmentIds.delete(attachment.id)
  }
}

async function toggleRecording() {
  if (recording.value) {
    mediaRecorder?.stop()
    recording.value = false
    window.clearInterval(recordingTimer)
    return
  }

  if (!navigator.mediaDevices?.getUserMedia || !globalThis.MediaRecorder) {
    pageError.value = '当前浏览器不支持录音，请改用音频文件上传'
    return
  }

  recordingBusy.value = true
  pageError.value = ''
  try {
    recordingStream = await navigator.mediaDevices.getUserMedia({ audio: true })
    const mimeType = pickRecordingMimeType()
    mediaRecorder = mimeType ? new MediaRecorder(recordingStream, { mimeType }) : new MediaRecorder(recordingStream)
    recordingChunks = []
    mediaRecorder.ondataavailable = (event) => {
      if (event.data.size) recordingChunks.push(event.data)
    }
    mediaRecorder.onerror = () => {
      pageError.value = '录音失败，请检查麦克风权限'
      stopRecordingResources()
    }
    mediaRecorder.onstop = async () => {
      const type = mediaRecorder.mimeType || mimeType || 'audio/webm'
      const extension = type.includes('ogg') ? 'ogg' : type.includes('mp4') ? 'm4a' : 'webm'
      const blob = new Blob(recordingChunks, { type })
      stopRecordingResources()
      if (!blob.size) {
        pageError.value = '没有录到有效音频，请重试'
        return
      }
      const file = new File([blob], `recording-${new Date().toISOString().replace(/[:.]/g, '-')}.${extension}`, { type })
      await uploadFile(file)
    }
    recordingSeconds.value = 0
    mediaRecorder.start(500)
    recording.value = true
    recordingTimer = window.setInterval(() => recordingSeconds.value += 1, 1000)
  } catch (error) {
    pageError.value = error?.name === 'NotAllowedError' ? '麦克风权限被拒绝，请在浏览器设置中允许访问' : '无法启动录音'
    stopRecordingResources()
  } finally {
    recordingBusy.value = false
  }
}

function pickRecordingMimeType() {
  return ['audio/webm;codecs=opus', 'audio/ogg;codecs=opus', 'audio/mp4']
    .find((type) => MediaRecorder.isTypeSupported(type)) || ''
}

function stopRecordingResources() {
  recording.value = false
  window.clearInterval(recordingTimer)
  recordingStream?.getTracks().forEach((track) => track.stop())
  recordingStream = null
}

async function handlePrimaryAction() {
  pageError.value = ''
  if (hasActiveAttachments.value) {
    pageError.value = '请等待附件上传和解析完成'
    return
  }
  if (!idea.value.trim() && !successfulAttachments.value.length) {
    pageError.value = '请填写文字需求，或至少保留一个解析成功的附件'
    return
  }
  if (successfulAttachments.value.length) {
    await mergeDraft()
    return
  }
  mergedDraft.value = idea.value.trim()
  await confirmAndCreatePrd()
}

async function mergeDraft() {
  mergeLoading.value = true
  pageError.value = ''
  try {
    await saveDirtyAttachmentResults()
    const response = await requirementApi.mergeDraft(
      idea.value.trim(),
      successfulAttachments.value.map((item) => item.id)
    )
    const text = extractDraftText(response)
    if (!text.trim()) throw new Error('合并接口未返回需求草稿')
    mergedDraft.value = text
    draftReady.value = true
    draftStale.value = false
    setMessage('合并草稿已生成，请检查后确认')
  } catch (error) {
    handleRequestError(error, '合并需求草稿失败')
  } finally {
    mergeLoading.value = false
  }
}

async function confirmAndCreatePrd() {
  const requirementText = mergedDraft.value.trim() || idea.value.trim()
  if (!requirementText) {
    pageError.value = '确认后的需求内容不能为空'
    return
  }

  prdLoading.value = true
  pageError.value = ''
  prdState.status = 'PENDING'
  prdState.content = ''
  prdState.error = ''
  try {
    const response = await requirementApi.createPrd(
      title.value || buildDefaultTitle(requirementText),
      requirementText,
      requestId.value
    )
    const normalized = normalizePrd(response)
    const pipelineId = normalized.pipelineId ?? (typeof response === 'number' || typeof response === 'string' ? response : null)
    if (pipelineId == null) throw new Error('创建 PRD 后未返回 pipelineId')
    prdState.id = pipelineId
    prdState.status = normalized.status || 'PENDING'
    prdState.content = normalized.content
    prdState.error = normalized.errorMessage
    draftReady.value = false
    await pollPrd()
  } catch (error) {
    prdLoading.value = false
    prdState.status = 'FAILED'
    prdState.error = error?.message || '创建 PRD 失败'
    handleRequestError(error, '创建 PRD 失败')
  }
}

async function pollPrd() {
  window.clearTimeout(prdPollTimer)
  try {
    const response = await requirementApi.getPrd(prdState.id)
    const next = normalizePrd(response)
    prdState.status = next.status
    prdState.content = next.content
    prdState.error = next.errorMessage

    if (SUCCESS_PRD_STATUSES.has(next.status)) {
      prdLoading.value = false
      setMessage('PRD 已生成完成')
      return
    }
    if (FAILED_PRD_STATUSES.has(next.status)) {
      prdLoading.value = false
      if (!prdState.error) prdState.error = 'PRD 流水线执行失败'
      return
    }
    if (!isPrdPending(next.status)) {
      prdLoading.value = false
      if (next.content) {
        prdState.status = 'COMPLETED'
        setMessage('PRD 已生成完成')
      } else {
        prdState.error = `PRD 已进入 ${next.status} 状态，但尚未返回正文`
      }
      return
    }
    prdPollTimer = window.setTimeout(pollPrd, 2000)
  } catch (error) {
    prdLoading.value = false
    prdState.status = 'FAILED'
    prdState.error = error?.message || '查询 PRD 状态失败'
    handleRequestError(error, '查询 PRD 状态失败')
  }
}

function buildDefaultTitle(text) {
  const firstLine = text.split(/\r?\n/).find((line) => line.trim()) || '新产品需求'
  return firstLine.replace(/^#+\s*/, '').slice(0, 60)
}

function statusText(status) {
  const labels = {
    UPLOADING: '上传中', UPLOAD_FAILED: '上传失败', PENDING: '等待解析',
    RUNNING: '解析中', PROCESSING: '解析中', SUCCEEDED: '解析成功',
    SUCCESS: '解析成功', COMPLETED: '解析成功', FAILED: '解析失败'
  }
  return labels[status] || status
}

function statusClass(status) {
  if (SUCCESS_ATTACHMENT_STATUSES.has(status) || SUCCESS_PRD_STATUSES.has(status)) return 'is-success'
  if (status === 'FAILED' || status === 'UPLOAD_FAILED' || FAILED_PRD_STATUSES.has(status)) return 'is-error'
  if (ACTIVE_ATTACHMENT_STATUSES.has(status) || status === 'PENDING') return 'is-active'
  return ''
}

function attachmentTypeText(attachment) {
  return attachment.type === 'IMAGE' || attachment.mimeType?.startsWith('image/') ? '图片' : '音频'
}

function formatSize(size) {
  if (!size) return ''
  if (size < 1024 * 1024) return ` · ${(size / 1024).toFixed(1)} KB`
  return ` · ${(size / 1024 / 1024).toFixed(1)} MB`
}

function logout() {
  clearAuth()
  window.sessionStorage.removeItem(REQUEST_ID_KEY)
  router.replace('/')
}
</script>
