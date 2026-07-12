<template>
  <div class="war-room">
    <header class="page-head">
      <div>
        <h2>项目作战室</h2>
        <p>{{ currentProjectTitle }}</p>
      </div>
      <div class="head-actions">
        <el-button :icon="Refresh" :loading="loading" @click="refreshCurrent">刷新</el-button>
        <el-button :icon="Promotion" type="primary" @click="goAutomation">流水线看板</el-button>
      </div>
    </header>

    <section class="workspace-strip">
      <button
        v-for="project in workspaces"
        :key="project.projectKey"
        class="workspace-tab"
        :class="{ active: project.projectKey === activeProjectKey }"
        @click="openProject(project.projectKey)"
      >
        <strong>{{ project.projectName }}</strong>
        <span>{{ workspaceSubtitle(project) }}</span>
      </button>
      <el-empty v-if="!loading && workspaces.length === 0" description="暂无可访问项目工作区" :image-size="76" />
    </section>

    <template v-if="warRoom">
      <section class="summary-band">
        <div v-for="item in summaryCards" :key="item.label" class="metric">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
        <div class="status-chip" :class="overallClass">
          <CircleCheck v-if="warRoom.summary.overallStatus === 'GREEN'" />
          <Warning v-else />
          <span>{{ overallStatusText }}</span>
        </div>
      </section>

      <main class="war-room-grid">
        <section class="panel project-panel">
          <div class="panel-title">
            <span>交付动态</span>
            <el-tag>{{ projectTypeText(warRoom.project.projectType) }}</el-tag>
          </div>
          <el-timeline>
            <el-timeline-item
              v-for="event in timelineItems"
              :key="event.sourceType + '-' + event.sourceId + '-' + event.eventTime"
              :timestamp="formatTime(event.eventTime)"
              :type="timelineType(event.status)"
            >
              <div class="timeline-item">
                <strong>{{ event.title }}</strong>
                <span>{{ event.description || statusText(event.status) || '-' }}</span>
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-if="timelineItems.length === 0" description="暂无交付动态" :image-size="68" />
        </section>

        <section class="panel pipeline-panel">
          <div class="panel-title">
            <span>业务阶段流转</span>
            <el-button text type="primary" @click="goAutomation">查看技术流水线</el-button>
          </div>
          <div v-for="pipeline in warRoom.pipelines" :key="pipeline.id" class="pipeline-row">
            <div class="pipeline-head">
              <div>
                <strong>{{ pipeline.requirementTitle }}</strong>
                <span>{{ pipelineStageLabel(pipeline) }} · {{ pendingApprovalCount(pipeline) ? `${pendingApprovalCount(pipeline)} 项待审` : '无待审' }}</span>
              </div>
              <el-tag :type="statusType(pipeline.status)">{{ statusText(pipeline.status) }}</el-tag>
            </div>

            <div class="stage-flow">
              <div
                v-for="(stage, index) in pipeline.stages"
                :key="stage.id"
                class="stage-node"
                :class="stageStateClass(stage)"
              >
                <div class="stage-index">{{ index + 1 }}</div>
                <div class="stage-body">
                  <strong>{{ stageDisplayLabel(stage) }}</strong>
                  <span>{{ statusText(stage.status) }}</span>
                </div>
              </div>
            </div>

            <div class="pipeline-meta">
              <span>进度 {{ pipeline.passedStages || 0 }} / {{ pipeline.totalStages || pipeline.stages.length }}</span>
              <span>更新时间 {{ formatTime(pipeline.updateTime) }}</span>
              <span>待审 {{ pendingApprovalCount(pipeline) }} 项</span>
            </div>
          </div>
          <el-empty v-if="warRoom.pipelines.length === 0" description="暂无交付流水线" :image-size="78" />
        </section>

        <section class="panel agent-panel">
          <div class="panel-title">
            <span>Team Agent 协作</span>
            <el-button text type="primary" @click="goAgentRuns">运行控制台</el-button>
          </div>
          <div class="action-picker">
            <button
              v-for="action in businessActions"
              :key="action.value"
              class="action-card"
              :class="{ active: runForm.businessType === action.value }"
              type="button"
              @click="selectBusinessAction(action)"
            >
              <strong>{{ action.label }}</strong>
              <span>{{ action.description }}</span>
            </button>
          </div>
          <el-form class="agent-form" label-position="top">
            <div class="form-grid">
              <el-form-item label="协作 Agent">
                <el-select v-model="runForm.agentId" filterable placeholder="选择可用 Agent">
                  <el-option
                    v-for="agent in agents"
                    :key="agent.id"
                    :label="agentLabel(agent)"
                    :value="agent.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="业务动作">
                <el-select v-model="runForm.businessType">
                  <el-option v-for="action in businessActions" :key="action.value" :label="action.label" :value="action.value" />
                </el-select>
              </el-form-item>
            </div>
            <el-form-item label="指定处理人">
              <el-select v-model="runForm.assigneeUserId" clearable placeholder="选择项目成员">
                <el-option
                  v-for="member in mentionableUsers"
                  :key="member.userId"
                  :label="memberName(member)"
                  :value="member.userId"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="关联对象">
              <el-input v-model="runForm.businessId" placeholder="可选，例如需求、审批或发布批次" />
            </el-form-item>
            <el-form-item label="处理说明">
              <el-input v-model="runForm.inputText" type="textarea" :rows="4" resize="none" placeholder="说明目标、上下文和交付口径" />
            </el-form-item>
            <el-button :icon="VideoPlay" type="primary" :loading="submittingRun" @click="submitRun">发起协作</el-button>
          </el-form>

          <div class="queue-list">
            <div v-for="run in warRoom.queue" :key="run.id" class="queue-item">
              <div>
                <strong>#{{ run.id }} {{ businessTypeText(run.businessType) }}</strong>
                <span>{{ run.businessId || '未绑定业务对象' }} · {{ agentDisplayName(run.agentId) }}</span>
              </div>
              <div class="queue-actions">
                <el-tag :type="statusType(run.status)">{{ statusText(run.status) }}</el-tag>
                <el-button v-if="run.canApprove" size="small" type="success" plain @click="approveRun(run)">确认</el-button>
                <el-button v-if="run.canApprove" size="small" type="warning" plain @click="rejectRun(run)">驳回</el-button>
                <el-button v-if="run.canCancel" size="small" type="danger" plain @click="cancelRun(run)">取消</el-button>
              </div>
            </div>
            <el-empty v-if="warRoom.queue.length === 0" description="暂无 Team Agent 任务" :image-size="68" />
          </div>
        </section>

        <aside class="side-stack">
          <section class="panel pm-panel">
            <div class="panel-title">
              <span>PM 控制台</span>
              <el-tag :type="pendingApprovals.length ? 'warning' : 'success'">{{ pendingApprovals.length }} 个待处理</el-tag>
            </div>
            <div class="pm-actions">
              <el-button size="small" :icon="Refresh" @click="refreshCurrent">刷新状态</el-button>
              <el-button size="small" type="primary" plain @click="goAutomation">查看全部审批</el-button>
              <el-button size="small" type="warning" plain @click="startBlockerFollowUp">阻塞处理</el-button>
            </div>
          </section>

          <section class="panel review-panel">
            <div class="panel-title">
              <span>Pending Reviews</span>
              <el-tag :type="pendingApprovals.length ? 'warning' : 'success'">{{ pendingApprovals.length }}</el-tag>
            </div>
            <div v-for="approval in pendingApprovals" :key="approval.id" class="approval-item">
              <div class="approval-head">
                <div>
                  <strong>{{ approval.pipelineTitle }}</strong>
                  <span>审批项 #{{ approval.id }} · {{ approval.pipelineCode || `流水线 #${approval.pipelineId}` }}</span>
                </div>
                <el-tag type="warning">{{ approvalStatusText(approval.status) }}</el-tag>
              </div>
              <dl class="review-facts">
                <div>
                  <dt>阶段</dt>
                  <dd>{{ approvalStageLabel(approval) }}</dd>
                </div>
                <div>
                  <dt>评审角色</dt>
                  <dd>{{ approval.reviewerRole || '项目负责人' }}</dd>
                </div>
                <div>
                  <dt>提交时间</dt>
                  <dd>{{ formatTime(approval.createTime) }}</dd>
                </div>
              </dl>
              <p v-if="approval.comment">{{ approval.comment }}</p>
              <el-select v-model="approvalAssignees[approval.id]" clearable size="small" placeholder="指定处理人">
                <el-option
                  v-for="member in mentionableUsers"
                  :key="member.userId"
                  :label="memberName(member)"
                  :value="member.userId"
                />
              </el-select>
              <div class="approval-actions">
                <el-button size="small" type="primary" plain @click="goApproval(approval)">去审批</el-button>
                <el-button size="small" plain @click="viewApprovalDetail(approval)">查看详情</el-button>
                <el-button size="small" type="warning" plain @click="handleBlocker(approval)">阻塞处理</el-button>
                <el-button size="small" type="success" plain @click="approveApproval(approval, 'SUCCESS')">通过</el-button>
                <el-button size="small" type="warning" plain @click="approveApproval(approval, 'REJECTED')">驳回</el-button>
              </div>
            </div>
            <el-empty v-if="pendingApprovals.length === 0" description="当前无待审批事项" :image-size="68" />
          </section>

          <section class="panel risk-panel">
            <div class="panel-title">
              <span>风险提醒</span>
              <el-tag :type="warRoom.risks.length ? 'warning' : 'success'">{{ warRoom.risks.length }}</el-tag>
            </div>
            <div v-for="risk in warRoom.risks" :key="risk.level + risk.title + risk.sourceId" class="risk-item" :class="risk.level.toLowerCase()">
              <strong>{{ riskTitle(risk.title) }}</strong>
              <span>{{ risk.description }}</span>
            </div>
            <el-empty v-if="warRoom.risks.length === 0" description="暂无风险" :image-size="68" />
          </section>

          <section class="panel compact-panel">
            <div class="panel-title">
              <span>待确认产物</span>
              <el-tag>{{ warRoom.pendingArtifacts.length }}</el-tag>
            </div>
            <div v-for="artifact in warRoom.pendingArtifacts" :key="artifact.id" class="compact-row">
              <strong>{{ artifact.title }}</strong>
              <span>{{ artifact.artifactType }} · Run #{{ artifact.runId }}</span>
            </div>
            <el-empty v-if="warRoom.pendingArtifacts.length === 0" description="暂无待确认产物" :image-size="68" />
          </section>

          <section class="panel compact-panel">
            <div class="panel-title">
              <span>Memory References</span>
              <el-tag>{{ warRoom.memoryReferences.length }}</el-tag>
            </div>
            <div v-for="memory in warRoom.memoryReferences" :key="memory.traceId + '-' + memory.memoryCode" class="memory-row">
              <strong>{{ memory.memoryCode }} v{{ memory.memoryVersion || 1 }}</strong>
              <span>{{ memorySourceText(memory) }} · {{ memory.tokenCount || 0 }} tokens</span>
              <small>Trace {{ memory.traceId || '-' }}</small>
            </div>
            <el-empty v-if="warRoom.memoryReferences.length === 0" description="暂无记忆引用" :image-size="60" />
          </section>

          <section class="panel compact-panel">
            <div class="panel-title">
              <span>项目成员</span>
              <User />
            </div>
            <el-tag v-for="member in mentionableUsers" :key="member.userId" class="member-tag">
              {{ memberName(member) }}
            </el-tag>
            <el-empty v-if="mentionableUsers.length === 0" description="暂无成员" :image-size="60" />
          </section>
        </aside>
      </main>
    </template>

    <el-empty v-else-if="!loading" description="请选择一个项目工作区" :image-size="96" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, Promotion, Refresh, User, VideoPlay, Warning } from '@element-plus/icons-vue'
import api from '../api'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const submittingRun = ref(false)
const workspaces = ref([])
const warRoom = ref(null)
const mentionableUsers = ref([])
const agents = ref([])
const approvalAssignees = reactive({})
const runForm = reactive({
  agentId: null,
  businessType: 'DELIVERY_REQUEST',
  assigneeUserId: null,
  businessId: '',
  inputText: ''
})

const businessActions = [
  { label: '交付请求', value: 'DELIVERY_REQUEST', description: '拉齐目标、范围和交付节奏' },
  { label: '需求评审', value: 'REQUIREMENT_REVIEW', description: '整理评审意见和待确认问题' },
  { label: '发布门禁', value: 'RELEASE_GATE', description: '检查发布前置条件和风险' },
  { label: '阻塞跟进', value: 'FOLLOW_UP_TASK', description: '指派责任人处理卡点' }
]

const activeProjectKey = computed(() => route.params.projectKey || warRoom.value?.project?.projectKey || '')
const currentProjectTitle = computed(() => {
  if (!warRoom.value?.project) return '请选择项目，查看交付状态、待办审批和 Agent 协作'
  return `${warRoom.value.project.projectName} · ${projectTypeText(warRoom.value.project.projectType)}`
})
const overallClass = computed(() => (warRoom.value?.summary?.overallStatus || 'GREEN').toLowerCase())
const overallStatusText = computed(() => ({
  GREEN: '进展正常',
  YELLOW: '需要关注',
  RED: '存在阻塞'
})[warRoom.value?.summary?.overallStatus] || '未知')
const summaryCards = computed(() => {
  const summary = warRoom.value?.summary || {}
  return [
    { label: '交付流水线', value: summary.pipelineCount || 0 },
    { label: '进行中任务', value: summary.activeRunCount || 0 },
    { label: '待审批', value: summary.pendingApprovalCount || 0 },
    { label: '风险项', value: summary.riskCount || 0 }
  ]
})
const pendingApprovals = computed(() => {
  const pipelines = warRoom.value?.pipelines || []
  return pipelines.flatMap(pipeline => (pipeline.approvals || [])
    .filter(approval => ['PENDING', 'WAITING_APPROVAL'].includes(String(approval.status || '').toUpperCase()))
    .map(approval => ({
      ...approval,
      pipelineId: pipeline.id,
      pipelineTitle: pipeline.requirementTitle,
      pipelineCode: pipeline.pipelineCode,
      businessStageLabel: pipeline.businessStageLabel
    })))
})
const timelineItems = computed(() => (warRoom.value?.timeline || []).slice(0, 12))

const loadWorkspaces = async () => {
  const response = await api.getProjectWorkspaces()
  workspaces.value = response.data.data || []
}

const loadAgents = async () => {
  try {
    const response = await api.getAgents({ pageNum: 1, pageSize: 100 })
    agents.value = response.data.data?.records || []
    if (!runForm.agentId && agents.value.length) runForm.agentId = agents.value[0].id
  } catch {
    agents.value = []
  }
}

const loadWarRoom = async (projectKey) => {
  if (!projectKey) {
    warRoom.value = null
    mentionableUsers.value = []
    return
  }
  const [roomResponse, usersResponse] = await Promise.all([
    api.getProjectWarRoom(projectKey),
    api.getProjectMentionableUsers(projectKey)
  ])
  warRoom.value = roomResponse.data.data
  mentionableUsers.value = usersResponse.data.data || []
}

const loadAll = async () => {
  loading.value = true
  try {
    await Promise.all([loadWorkspaces(), loadAgents()])
    const routeProjectKey = route.params.projectKey
    if (!routeProjectKey && workspaces.value.length > 0) {
      router.replace(`/project-workspaces/${encodeURIComponent(workspaces.value[0].projectKey)}`)
      return
    }
    await loadWarRoom(routeProjectKey)
  } catch (error) {
    ElMessage.error(loadErrorText(error))
  } finally {
    loading.value = false
  }
}

const refreshCurrent = async () => {
  await loadAll()
}

const openProject = (projectKey) => {
  router.push(`/project-workspaces/${encodeURIComponent(projectKey)}`)
}

const goAutomation = () => router.push('/automation')
const goAgentRuns = () => router.push('/agent-runs')

const selectBusinessAction = (action) => {
  runForm.businessType = action.value
  if (action.value === 'FOLLOW_UP_TASK' && pendingApprovals.value[0]) {
    runForm.businessId = `APPROVAL-${pendingApprovals.value[0].id}`
  }
}

const submitRun = async () => {
  if (!activeProjectKey.value) return
  if (!runForm.agentId) {
    ElMessage.warning('请选择一个可用的协作 Agent')
    return
  }
  const businessId = normalizedBusinessId()
  try {
    await ElMessageBox.confirm('将创建或复用一条可审计的 Team Agent 任务，并进入当前项目队列。', '发起协作', {
      type: 'warning',
      confirmButtonText: '发起',
      cancelButtonText: '返回'
    })
    submittingRun.value = true
    await api.createProjectTeamAgentRun(activeProjectKey.value, {
      agentId: runForm.agentId,
      businessType: runForm.businessType,
      businessId,
      input: buildRunInput()
    })
    ElMessage.success('协作任务已进入队列')
    runForm.businessId = ''
    runForm.inputText = ''
    await loadWarRoom(activeProjectKey.value)
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('协作任务发起失败')
  } finally {
    submittingRun.value = false
  }
}

const approveRun = async (run) => {
  await confirmRunAction('确认该 Team Agent 结果并继续流转？', () => api.approveAgentRun(run.id, 'Approved from Project War Room'))
}

const rejectRun = async (run) => {
  await confirmRunAction('驳回该 Team Agent 结果？', () => api.rejectAgentRun(run.id, 'Rejected from Project War Room'))
}

const cancelRun = async (run) => {
  await confirmRunAction('取消该 Team Agent 任务？', () => api.cancelAgentRun(run.id, 'Cancelled from Project War Room'))
}

const approveApproval = async (approval, status) => {
  const assignee = findMember(approvalAssignees[approval.id])
  const actionText = status === 'SUCCESS' ? '通过' : '驳回'
  try {
    await ElMessageBox.confirm(`确认${actionText}「${approval.pipelineTitle}」的${stageText(approval.approvalType)}审批？`, `${actionText}审批`, {
      type: status === 'SUCCESS' ? 'success' : 'warning',
      confirmButtonText: actionText,
      cancelButtonText: '返回'
    })
    await api.approveAutomation(approval.id, {
      status,
      comment: assignee ? `指定 ${memberName(assignee)} 处理后${actionText}` : `作战室${actionText}`
    })
    ElMessage.success(`已${actionText}，流程将继续更新`)
    await loadWarRoom(activeProjectKey.value)
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('审批操作失败，请确认权限或稍后重试')
  }
}

const createFollowUp = async (approval) => {
  const agentId = runForm.agentId || agents.value[0]?.id
  if (!agentId) {
    ElMessage.warning('暂无可用协作 Agent，请先到 Agent 管理启用')
    return
  }
  const assignee = findMember(approvalAssignees[approval.id])
  try {
    await ElMessageBox.confirm('将为该审批创建一条 Team Agent 跟进任务，处理人会写入任务上下文。', '指派跟进', {
      type: 'warning',
      confirmButtonText: '创建跟进',
      cancelButtonText: '返回'
    })
    await api.createProjectTeamAgentRun(activeProjectKey.value, {
      agentId,
      businessType: 'FOLLOW_UP_TASK',
      businessId: `APPROVAL-${approval.id}`,
      input: {
        approvalId: approval.id,
        pipelineId: approval.pipelineId,
        stage: approval.approvalType,
        assignee: assignee ? { userId: assignee.userId, name: memberName(assignee) } : null,
        instruction: `跟进「${approval.pipelineTitle}」的${stageText(approval.approvalType)}审批`
      }
    })
    ElMessage.success('已创建跟进任务')
    await loadWarRoom(activeProjectKey.value)
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('创建跟进任务失败')
  }
}

const goApproval = (approval) => {
  router.push({ path: '/automation', query: { approvalId: approval.id, pipelineId: approval.pipelineId } })
}

const viewApprovalDetail = async (approval) => {
  await showApprovalDetail(approval, ['原始产物与正文请通过“去审批”在审批页查看。'])
}

const showApprovalDetail = (approval, extraLines = []) => ElMessageBox.alert(
  [
    `项目事项：${approval.pipelineTitle}`,
    `审批项：#${approval.id}`,
    `阶段：${approvalStageLabel(approval)}`,
    `角色：${approval.reviewerRole || '项目负责人'}`,
    `状态：${approvalStatusText(approval.status)}`,
    `提交时间：${formatTime(approval.createTime)}`,
    approval.comment ? `说明：${approval.comment}` : '',
    ...extraLines
  ].filter(Boolean).join('\n'),
  '审批详情',
  { confirmButtonText: '知道了' }
)

const handleBlocker = async (approval) => {
  approvalAssignees[approval.id] = approvalAssignees[approval.id] || mentionableUsers.value[0]?.userId
  await createFollowUp(approval)
}

const startBlockerFollowUp = async () => {
  if (pendingApprovals.value.length) {
    await handleBlocker(pendingApprovals.value[0])
    return
  }
  runForm.businessType = 'FOLLOW_UP_TASK'
  runForm.businessId = defaultBusinessId()
  ElMessage.info('已切换到阻塞跟进动作，可直接发起 Team Agent 协作')
}

const confirmRunAction = async (message, action) => {
  try {
    await ElMessageBox.confirm(message, '确认操作', {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '返回'
    })
    await action()
    ElMessage.success('操作已完成')
    await loadWarRoom(activeProjectKey.value)
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('操作失败')
  }
}

const buildRunInput = () => {
  const rawInput = parseRunInput(runForm.inputText)
  const assignee = findMember(runForm.assigneeUserId)
  return {
    ...rawInput,
    assignee: assignee ? { userId: assignee.userId, name: memberName(assignee), role: assignee.memberRole } : null
  }
}

const parseRunInput = (value) => {
  if (!value.trim()) return {}
  try {
    return JSON.parse(value)
  } catch {
    return { instruction: value.trim() }
  }
}

const normalizedBusinessId = () => runForm.businessId.trim() || defaultBusinessId()
const defaultBusinessId = () => {
  const prefix = {
    DELIVERY_REQUEST: 'DELIVERY',
    REQUIREMENT_REVIEW: 'REVIEW',
    RELEASE_GATE: 'RELEASE',
    FOLLOW_UP_TASK: 'FOLLOWUP'
  }[runForm.businessType] || 'TASK'
  return `${prefix}-${Date.now()}`
}
const findMember = (userId) => mentionableUsers.value.find(member => member.userId === userId)
const memberName = (member) => member?.realName || member?.username || `用户 ${member?.userId || '-'}`
const agentLabel = (agent) => [agent.agentName, agent.agentCode].filter(Boolean).join(' / ') || `Agent ${agent.id}`
const agentDisplayName = (agentId) => {
  const agent = agents.value.find(item => item.id === agentId)
  return agent ? agentLabel(agent) : '协作 Agent'
}
const memorySourceText = (memory) => {
  const source = memory.sourceType || '来源未知'
  const scope = memory.scopeType || '作用域未知'
  return `${source} · ${scope}`
}
const workspaceSubtitle = (project) => {
  const updated = project?.updateTime ? ` · 更新 ${formatShortDate(project.updateTime)}` : ''
  return `${projectTypeText(project?.projectType)}工作区${updated}`
}
const projectTypeText = (type) => ({
  DELIVERY_PROJECT: '交付项目',
  CUSTOMER_PROJECT: '客户项目',
  TRUSTED_CONTEXT: '可信项目'
})[type] || '项目'
const businessTypeText = (type) => ({
  DELIVERY_REQUEST: '交付请求',
  REQUIREMENT_REVIEW: '需求评审',
  RELEASE_GATE: '发布门禁',
  FOLLOW_UP_TASK: '指派跟进'
})[type] || type
const stageText = (stageKey) => ({
  requirement_analysis: '需求确认',
  code_generation: '方案/代码',
  code_quality_evaluation: '质量评估',
  build_compile: '构建',
  test_execution: '测试',
  deployment_release: '发布',
  operations_monitoring: '运维观察',
  delivery_report: '交付报告'
})[stageKey] || stageKey || '-'
const stageDisplayLabel = (stage) => stageText(stage?.stageKey) || stage?.businessStageLabel || '-'
const pipelineStageLabel = (pipeline) => stageText(pipeline?.currentStage) || pipeline?.businessStageLabel || '-'
const approvalStageLabel = (approval) => stageText(approval?.approvalType) || approval?.businessStageLabel || '-'
const statusText = (status) => ({
  SUCCESS: '已通过',
  COMPLETED: '已完成',
  SUCCEEDED: '已完成',
  RUNNING: '进行中',
  QUEUED: '排队中',
  PENDING: '待开始',
  WAITING_APPROVAL: '待审批',
  BLOCKED: '已阻塞',
  REJECTED: '已驳回',
  FAILED: '失败',
  TIMEOUT: '超时',
  CANCELLED: '已取消'
})[status] || status
const approvalStatusText = (status) => ({
  PENDING: '待审批',
  WAITING_APPROVAL: '待审批'
})[status] || statusText(status)
const riskTitle = (title) => ({
  'Approval waiting': '有审批待处理',
  'Pending artifacts': '有产物待确认',
  'Team Agent backlog': 'Agent 队列积压',
  'Agent run needs attention': 'Agent 任务异常',
  'Pipeline blocked': '流水线阻塞'
})[title] || title
const statusType = (status) => ({
  SUCCESS: 'success',
  COMPLETED: 'success',
  SUCCEEDED: 'success',
  RUNNING: 'primary',
  QUEUED: 'info',
  PENDING: 'info',
  WAITING_APPROVAL: 'warning',
  BLOCKED: 'warning',
  REJECTED: 'danger',
  FAILED: 'danger',
  TIMEOUT: 'danger',
  CANCELLED: 'info'
})[status] || 'info'
const stageStateClass = (stage) => ({
  success: ['SUCCESS', 'COMPLETED'].includes(stage.status),
  active: ['RUNNING', 'WAITING_APPROVAL', 'QUEUED'].includes(stage.status),
  danger: ['REJECTED', 'FAILED', 'BLOCKED', 'TIMEOUT'].includes(stage.status)
})
const timelineType = (status) => {
  const type = statusType(status)
  return type === 'primary' ? '' : type
}
const pendingApprovalCount = (pipeline) => (pipeline.approvals || [])
  .filter(approval => ['PENDING', 'WAITING_APPROVAL'].includes(String(approval.status || '').toUpperCase()))
  .length
const formatTime = (value) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
const formatShortDate = (value) => value ? new Date(value).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }) : '-'
const loadErrorText = (error) => {
  const status = error?.response?.status
  if (status === 403) return '没有访问该项目作战室的权限'
  if (status === 404) return '项目工作区不存在，请先重新创建或等待历史数据迁移'
  return '项目作战室加载失败'
}

watch(() => route.params.projectKey, async (projectKey) => {
  loading.value = true
  try {
    await loadWarRoom(projectKey)
  } catch (error) {
    ElMessage.error(loadErrorText(error))
  } finally {
    loading.value = false
  }
})

onMounted(loadAll)
</script>

<style scoped>
.war-room { color: var(--text-primary); }
.page-head { display: flex; justify-content: space-between; gap: 20px; align-items: flex-start; margin-bottom: 16px; }
.page-head h2 { margin: 0 0 6px; font-size: 24px; }
.page-head p { margin: 0; color: var(--text-muted); }
.head-actions { display: flex; gap: 10px; flex-wrap: wrap; justify-content: flex-end; }
.workspace-strip { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 10px; margin-bottom: 14px; }
.workspace-tab { border: 1px solid var(--glass-border); border-radius: 8px; background: var(--glass-bg); color: var(--text-primary); padding: 12px 14px; text-align: left; cursor: pointer; min-width: 0; }
.workspace-tab strong, .workspace-tab span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.workspace-tab span { margin-top: 5px; color: var(--text-muted); font-size: 12px; }
.workspace-tab.active { border-color: var(--accent-cyan); box-shadow: 0 0 0 1px rgba(34, 211, 238, .28) inset; }
.summary-band { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)) minmax(140px, .65fr); gap: 12px; margin-bottom: 16px; }
.metric, .status-chip, .panel { border: 1px solid var(--glass-border); border-radius: 8px; background: var(--glass-bg); }
.metric { padding: 14px; display: flex; flex-direction: column; gap: 6px; }
.metric span { color: var(--text-muted); font-size: 12px; }
.metric strong { font-size: 24px; }
.status-chip { display: flex; align-items: center; justify-content: center; gap: 8px; font-weight: 800; }
.status-chip svg { width: 18px; height: 18px; }
.status-chip.green { color: #22c55e; }
.status-chip.yellow { color: #f59e0b; }
.status-chip.red { color: #ef4444; }
.war-room-grid { display: grid; grid-template-columns: 330px minmax(520px, 1fr) 380px; grid-template-areas: "progress pipeline side" "progress agent side"; gap: 16px; align-items: start; }
.panel { padding: 16px; min-width: 0; }
.project-panel { grid-area: progress; }
.pipeline-panel { grid-area: pipeline; }
.agent-panel { grid-area: agent; }
.side-stack { grid-area: side; display: flex; flex-direction: column; gap: 12px; }
.panel-title { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 14px; font-weight: 800; }
.timeline-item { display: flex; flex-direction: column; gap: 5px; min-width: 0; }
.timeline-item span { color: var(--text-muted); font-size: 12px; word-break: break-word; }
.pipeline-row { padding-top: 4px; }
.pipeline-head { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.pipeline-head strong, .pipeline-head span { display: block; }
.pipeline-head span, .pipeline-meta { color: var(--text-muted); font-size: 12px; }
.stage-flow { display: grid; grid-template-columns: repeat(auto-fit, minmax(168px, 1fr)); gap: 10px; }
.stage-node { min-height: 74px; border: 1px solid var(--glass-border); border-radius: 8px; padding: 10px 12px; display: flex; gap: 10px; align-items: center; background: rgba(148, 163, 184, .08); min-width: 0; }
.stage-node.success { border-color: rgba(34, 197, 94, .45); background: rgba(34, 197, 94, .08); }
.stage-node.active { border-color: rgba(59, 130, 246, .55); background: rgba(59, 130, 246, .08); }
.stage-node.danger { border-color: rgba(239, 68, 68, .45); background: rgba(239, 68, 68, .08); }
.stage-index { width: 28px; height: 28px; border-radius: 999px; display: grid; place-items: center; flex: 0 0 auto; background: rgba(148, 163, 184, .22); font-weight: 800; }
.stage-body { min-width: 0; }
.stage-body strong, .stage-body span { display: block; }
.stage-body strong { font-size: 14px; line-height: 1.35; word-break: keep-all; overflow-wrap: normal; }
.stage-body span { margin-top: 4px; color: var(--text-muted); font-size: 12px; }
.pipeline-meta { display: flex; justify-content: space-between; gap: 12px; margin-top: 12px; flex-wrap: wrap; }
.action-picker { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-bottom: 14px; }
.action-card { border: 1px solid var(--glass-border); border-radius: 8px; background: rgba(148, 163, 184, .08); color: var(--text-primary); padding: 10px; text-align: left; cursor: pointer; min-height: 82px; }
.action-card strong, .action-card span { display: block; }
.action-card strong { font-size: 14px; margin-bottom: 6px; }
.action-card span { color: var(--text-muted); font-size: 12px; line-height: 1.45; }
.action-card.active { border-color: var(--accent-cyan); background: rgba(34, 211, 238, .1); box-shadow: 0 0 0 1px rgba(34, 211, 238, .22) inset; }
.agent-form { padding-bottom: 14px; border-bottom: 1px solid var(--glass-border); margin-bottom: 14px; }
.form-grid { display: grid; grid-template-columns: minmax(130px, .7fr) minmax(180px, 1fr); gap: 12px; }
.queue-list { display: flex; flex-direction: column; gap: 10px; }
.queue-item, .approval-item, .risk-item, .compact-row, .memory-row { border-radius: 8px; background: rgba(148, 163, 184, .08); }
.queue-item { display: flex; justify-content: space-between; gap: 12px; padding: 12px; }
.queue-item strong, .queue-item span { display: block; }
.queue-item span { color: var(--text-muted); font-size: 12px; margin-top: 4px; }
.queue-actions, .approval-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
.pm-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.approval-item { padding: 12px; margin-bottom: 10px; }
.approval-head { display: flex; justify-content: space-between; gap: 8px; align-items: flex-start; }
.approval-head strong, .approval-head span { display: block; }
.approval-head strong { line-height: 1.35; }
.approval-head span { margin-top: 5px; color: var(--text-muted); font-size: 12px; }
.approval-item p { margin: 8px 0; color: var(--text-muted); font-size: 12px; }
.review-facts { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin: 10px 0; }
.review-facts div { border: 1px solid rgba(148, 163, 184, .18); border-radius: 8px; padding: 8px; min-width: 0; }
.review-facts dt { color: var(--text-muted); font-size: 11px; margin-bottom: 4px; }
.review-facts dd { margin: 0; font-size: 12px; line-height: 1.35; word-break: keep-all; overflow-wrap: anywhere; }
.approval-actions { margin-top: 10px; justify-content: flex-start; }
.risk-item, .compact-row, .memory-row { padding: 11px 12px; }
.risk-item { border-left: 3px solid #f59e0b; }
.risk-item.danger { border-left-color: #ef4444; }
.risk-item strong, .risk-item span, .compact-row strong, .compact-row span, .memory-row strong, .memory-row span, .memory-row small { display: block; }
.risk-item span, .compact-row span, .memory-row span, .memory-row small { color: var(--text-muted); font-size: 12px; margin-top: 5px; word-break: break-word; }
.memory-row + .memory-row { margin-top: 8px; }
.member-tag { margin: 0 8px 8px 0; max-width: 100%; }
:deep(.el-form-item__label) { font-weight: 700; }
@media (max-width: 1500px) { .war-room-grid { grid-template-columns: 1fr 1fr; grid-template-areas: "pipeline side" "agent side" "progress progress"; } .action-picker { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 960px) { .war-room-grid, .summary-band, .form-grid, .action-picker, .review-facts { grid-template-columns: 1fr; grid-template-areas: none; } .project-panel, .pipeline-panel, .agent-panel, .side-stack { grid-area: auto; } .page-head, .queue-item { flex-direction: column; } }
</style>
