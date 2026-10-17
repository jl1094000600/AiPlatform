<template>
  <div class="war-room">
    <header class="page-head">
      <div>
        <h2>项目作战室</h2>
        <p>{{ currentProjectTitle }}</p>
      </div>
      <div class="head-actions">
        <el-button :icon="Refresh" :loading="loading || loadingWorkspaces" @click="refreshCurrent">刷新</el-button>
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
      <el-empty v-if="!loading && !loadingWorkspaces && workspaces.length === 0" description="暂无可访问项目工作区" :image-size="76" />
    </section>

    <el-alert v-if="workspaceError" :title="workspaceError" type="error" :closable="false" show-icon class="workspace-error" />
    <ProjectAssignmentPanel
      :project-key="warRoom?.project?.projectKey || ''"
      :assignments="assignments"
      :my-assignments="myAssignments"
      :workspaces="workspaces"
      :loading="loading || loadingAssignments"
      :busy="assignmentBusy"
      :assignments-error="assignmentsError"
      :my-assignments-error="myAssignmentsError"
      @refresh="refreshAssignments"
      @action="performAssignmentAction"
      @reassign="openReassignment"
      @open-project="openProject"
    />

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
              :timestamp="formatProjectTime(event.eventTime)"
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
              <span>更新时间 {{ formatProjectTime(pipeline.updateTime) }}</span>
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
          <el-alert v-if="!canExecute" title="当前可查看项目；发起协作需要项目执行权限。" type="info" :closable="false" />
          <div v-if="canExecute" class="action-picker">
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
          <el-form v-if="canExecute" class="agent-form" label-position="top">
            <div class="form-grid">
              <el-form-item label="协作 Agent">
                <el-select v-model="runForm.agentId" filterable :disabled="agents.length === 0" placeholder="选择可用 Agent">
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
            <el-alert v-if="agentsError" :title="agentsError" type="warning" :closable="false" show-icon />
            <el-form-item v-if="canManageAssignments" label="指定处理人（可选）">
              <el-select v-model="runForm.assigneeUserId" filterable clearable :disabled="!membersAvailable" :loading="loading" placeholder="选择有效项目成员">
                <el-option
                  v-for="member in mentionableUsers"
                  :key="member.userId"
                  :label="memberName(member)"
                  :value="member.userId"
                />
              </el-select>
            </el-form-item>
            <el-form-item v-if="canManageAssignments && runForm.assigneeUserId" label="指派说明（可选）">
              <el-input v-model="runForm.assignmentReason" maxlength="500" placeholder="说明指派原因" />
            </el-form-item>
            <el-form-item label="关联对象">
              <el-input v-model="runForm.businessId" placeholder="可选，例如需求、审批或发布批次" />
            </el-form-item>
            <el-form-item label="处理说明">
              <el-input v-model="runForm.inputText" type="textarea" :rows="4" resize="none" placeholder="说明目标、上下文和交付口径" />
            </el-form-item>
            <el-button :icon="VideoPlay" type="primary" :loading="submittingRun" :disabled="!runForm.agentId || agents.length === 0 || loading" @click="submitRun">发起协作</el-button>
          </el-form>

          <div class="queue-list">
            <div v-for="run in warRoom.queue" :key="run.id" class="queue-item">
              <div>
                <strong>#{{ run.id }} {{ businessTypeText(run.businessType) }}</strong>
                <span>{{ run.businessId || '未绑定业务对象' }} · {{ agentDisplayName(run.agentId) }}</span>
                <span v-if="assignmentFor('AGENT_RUN', run.id)">{{ assignmentSummary('AGENT_RUN', run.id) }}</span>
              </div>
              <div class="queue-actions">
                <el-tag :type="statusType(run.status)">{{ statusText(run.status) }}</el-tag>
                <el-button size="small" plain @click="openRunConsole(run)">运行观测</el-button>
                <el-button v-if="canAssignTarget('AGENT_RUN', run.id)" size="small" :disabled="assignmentBusy" @click="openAssignment('AGENT_RUN', run.id)">{{ assignmentFor('AGENT_RUN', run.id) ? '改派' : '指派' }}</el-button>
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
              <el-button v-if="canExecute" size="small" type="warning" plain @click="startBlockerFollowUp">阻塞处理</el-button>
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
                  <dd>{{ formatProjectTime(approval.createTime) }}</dd>
                </div>
              </dl>
              <p v-if="approval.comment">{{ approval.comment }}</p>
              <p v-if="assignmentFor('AUTOMATION_APPROVAL', approval.id)">{{ assignmentSummary('AUTOMATION_APPROVAL', approval.id) }}</p>
              <div class="approval-actions">
                <el-button v-if="canAssignTarget('AUTOMATION_APPROVAL', approval.id)" size="small" :disabled="assignmentBusy" @click="openAssignment('AUTOMATION_APPROVAL', approval.id)">{{ assignmentFor('AUTOMATION_APPROVAL', approval.id) ? '改派' : '指派' }}</el-button>
                <el-button size="small" type="primary" plain @click="goApproval(approval)">去审批</el-button>
                <el-button size="small" plain @click="viewApprovalDetail(approval)">查看详情</el-button>
                <el-button v-if="canExecute" size="small" type="warning" plain @click="handleBlocker(approval)">阻塞处理</el-button>
                <el-button v-if="canApproveAutomation" size="small" type="success" plain @click="approveApproval(approval, 'SUCCESS')">通过</el-button>
                <el-button v-if="canApproveAutomation" size="small" type="warning" plain @click="approveApproval(approval, 'REJECTED')">驳回</el-button>
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

    <el-empty v-else-if="!loading && !loadingWorkspaces && !workspaceError" description="请选择一个项目工作区" :image-size="96" />

    <el-dialog v-model="assignmentDialog.visible" :title="assignmentDialog.assignmentId ? '改派事项' : '指派事项'" width="460px" :close-on-click-modal="false" :close-on-press-escape="!assignmentBusy" :show-close="!assignmentBusy">
      <p>{{ assignmentDialog.targetLabel }}</p>
      <p v-if="assignmentDialog.assignmentId">当前处理人：{{ assignmentDialog.fromName }}</p>
      <el-form label-position="top">
        <el-form-item label="新处理人">
          <el-select v-model="assignmentDialog.assigneeUserId" filterable :disabled="assignmentBusy || !membersAvailable" placeholder="选择有效项目成员" style="width:100%">
            <el-option v-for="member in mentionableUsers" :key="member.userId" :value="member.userId" :label="memberName(member)" />
          </el-select>
        </el-form-item>
        <el-form-item label="指派说明（可选）"><el-input v-model="assignmentDialog.reason" type="textarea" maxlength="500" :rows="3" :disabled="assignmentBusy" /></el-form-item>
      </el-form>
      <el-alert v-if="assignmentDialog.error" :title="assignmentDialog.error" type="warning" :closable="false" show-icon />
      <template #footer>
        <el-button :disabled="assignmentBusy" @click="assignmentDialog.visible = false">返回</el-button>
        <el-button type="primary" :loading="assignmentBusy" :disabled="!assignmentDialog.assigneeUserId || !membersAvailable" @click="saveAssignment">{{ assignmentDialog.assignmentId ? '确认改派' : '确认指派' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, Promotion, Refresh, User, VideoPlay, Warning } from '@element-plus/icons-vue'
import api from '../api'
import ProjectAssignmentPanel from '../components/ProjectAssignmentPanel.vue'
import { hasPermission } from '../utils/permissions'
import { assigneeLabel, assignmentStatusText, assignmentTargetText, buildTeamRunPayload, createLatestRequestGuard, formatProjectTime, isConfirmationCancelled, projectErrorStatus, projectErrorText, projectResponseData, retainTeamRunBusinessId } from '../utils/projectAssignments'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const loadingWorkspaces = ref(false)
const submittingRun = ref(false)
const workspaces = ref([])
const warRoom = ref(null)
const mentionableUsers = ref([])
const agents = ref([])
const agentsError = ref('')
const assignments = ref([])
const myAssignments = ref([])
const membersAvailable = ref(false)
const assignmentsAvailable = ref(false)
const loadingAssignments = ref(false)
const assignmentBusy = ref(false)
const workspaceError = ref('')
const assignmentsError = ref('')
const myAssignmentsError = ref('')
const projectGuard = createLatestRequestGuard()
const listGuard = createLatestRequestGuard()
const assignmentsGuard = createLatestRequestGuard()
const mineGuard = createLatestRequestGuard()
const refreshGuard = createLatestRequestGuard()
const agentsGuard = createLatestRequestGuard()
let contextRevision = 0
const assignmentDialog = reactive({ visible: false, assignmentId: null, projectKey: '', targetType: '', targetId: null, targetLabel: '', fromName: '', assigneeUserId: null, expectedVersion: null, reason: '', error: '' })
const canExecute = computed(() => hasPermission('project-workspace:execute'))
const canApproveAutomation = computed(() => hasPermission('automation:approve'))
const canManageAssignments = computed(() => warRoom.value?.canManageAssignments === true || warRoom.value?.project?.canManageAssignments === true)
const runForm = reactive({
  agentId: null,
  businessType: 'DELIVERY_REQUEST',
  assigneeUserId: null,
  assignmentReason: '',
  businessId: '',
  inputText: ''
})

const businessActions = [
  { label: '交付请求', value: 'DELIVERY_REQUEST', description: '拉齐目标、范围和交付节奏' },
  { label: '需求评审', value: 'REQUIREMENT_REVIEW', description: '整理评审意见和待确认问题' },
  { label: '发布门禁', value: 'RELEASE_GATE', description: '检查发布前置条件和风险' },
  { label: '阻塞跟进', value: 'FOLLOW_UP_TASK', description: '指派责任人处理卡点' }
]

const activeProjectKey = computed(() => String(route.params.projectKey || ''))
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

const loadAgents = async () => {
  const request = agentsGuard.begin()
  try {
    const data = projectResponseData(await api.getAgents({ pageNum: 1, pageSize: 100 }))
    if (!agentsGuard.isCurrent(request)) return
    agents.value = data?.records || []
    agentsError.value = agents.value.length ? '' : '暂无可用协作 Agent，请先启用 Agent 后刷新'
    if (!agents.value.some(agent => agent.id === runForm.agentId)) runForm.agentId = agents.value[0]?.id || null
  } catch (error) {
    if (!agentsGuard.isCurrent(request)) return
    agents.value = []
    runForm.agentId = null
    agentsError.value = projectErrorStatus(error) === 403 ? '当前缺少 Agent 查看权限，请联系管理员' : 'Agent 列表加载失败，请刷新后重试'
  }
}

const resetProjectContext = () => {
  contextRevision += 1
  projectGuard.invalidate()
  assignmentsGuard.invalidate()
  refreshGuard.invalidate()
  warRoom.value = null
  mentionableUsers.value = []
  assignments.value = []
  membersAvailable.value = false
  assignmentsAvailable.value = false
  assignmentsError.value = ''
  workspaceError.value = ''
  assignmentDialog.visible = false
  runForm.assigneeUserId = null
  runForm.assignmentReason = ''
  runForm.businessId = ''
  runForm.inputText = ''
  loading.value = false
  loadingAssignments.value = false
}

const isCurrentContext = (projectKey, revision) => revision === contextRevision && projectKey === activeProjectKey.value

const loadMyAssignments = async () => {
  const request = mineGuard.begin()
  try {
    const data = projectResponseData(await api.getMyProjectAssignments())
    if (!mineGuard.isCurrent(request)) return
    myAssignments.value = data || []
    myAssignmentsError.value = ''
  } catch (error) {
    if (!mineGuard.isCurrent(request)) return
    myAssignments.value = []
    myAssignmentsError.value = projectErrorText(error, '我的待办加载失败，请刷新重试')
  }
}

const loadProjectAssignments = async (projectKey) => {
  const request = assignmentsGuard.begin()
  const revision = contextRevision
  if (!projectKey) return
  try {
    const data = projectResponseData(await api.getProjectAssignments(projectKey))
    if (!assignmentsGuard.isCurrent(request) || !isCurrentContext(projectKey, revision)) return
    assignments.value = data || []
    assignmentsError.value = ''
    assignmentsAvailable.value = true
  } catch (error) {
    if (!assignmentsGuard.isCurrent(request) || !isCurrentContext(projectKey, revision)) return
    assignments.value = []
    assignmentsAvailable.value = false
    assignmentsError.value = projectErrorText(error, '项目指派加载失败，请刷新重试')
  }
}

const loadWarRoom = async (projectKey) => {
  const request = projectGuard.begin()
  const revision = contextRevision
  if (!projectKey) return
  loading.value = true
  const results = await Promise.allSettled([
    api.getProjectWarRoom(projectKey).then(projectResponseData),
    api.getProjectMentionableUsers(projectKey).then(projectResponseData),
    loadProjectAssignments(projectKey)
  ])
  if (!projectGuard.isCurrent(request) || !isCurrentContext(projectKey, revision)) return
  loading.value = false
  const [room, users] = results
  if (room.status === 'rejected') {
    resetProjectContext()
    workspaceError.value = projectErrorText(room.reason, '项目作战室加载失败，请刷新重试')
    if ([403, 404].includes(projectErrorStatus(room.reason))) {
      myAssignments.value = myAssignments.value.filter(item => item.projectKey !== projectKey)
      workspaces.value = workspaces.value.filter(item => item.projectKey !== projectKey)
      await loadMyAssignments()
    }
    return
  }
  warRoom.value = room.value
  if (users.status === 'fulfilled') {
    mentionableUsers.value = users.value || []
    membersAvailable.value = true
    workspaceError.value = ''
    if (!findMember(runForm.assigneeUserId)) runForm.assigneeUserId = null
    if (assignmentDialog.visible && !findMember(assignmentDialog.assigneeUserId)) assignmentDialog.assigneeUserId = null
  } else {
    mentionableUsers.value = []
    membersAvailable.value = false
    runForm.assigneeUserId = null
    workspaceError.value = projectErrorText(users.reason, '项目成员加载失败，暂不可指派，请刷新重试')
  }
  if (!canManageAssignments.value) runForm.assigneeUserId = null
}

const loadAll = async () => {
  const request = listGuard.begin()
  loadingWorkspaces.value = true
  try {
    const [response] = await Promise.all([api.getProjectWorkspaces(), loadAgents(), loadMyAssignments()])
    if (!listGuard.isCurrent(request)) return
    workspaces.value = projectResponseData(response) || []
    const routeProjectKey = activeProjectKey.value
    if (!routeProjectKey && workspaces.value.length > 0) {
      await router.replace(`/project-workspaces/${encodeURIComponent(workspaces.value[0].projectKey)}`)
      return
    }
    await loadWarRoom(routeProjectKey)
  } catch (error) {
    if (!listGuard.isCurrent(request)) return
    workspaces.value = []
    resetProjectContext()
    workspaceError.value = projectErrorText(error, '项目工作区列表加载失败，请刷新重试')
  } finally {
    if (listGuard.isCurrent(request)) loadingWorkspaces.value = false
  }
}

const refreshAssignments = async () => {
  const request = refreshGuard.begin()
  loadingAssignments.value = true
  await Promise.all([loadProjectAssignments(activeProjectKey.value), loadMyAssignments()])
  if (refreshGuard.isCurrent(request)) loadingAssignments.value = false
}

const refreshCurrent = async () => {
  await loadAll()
}

const openProject = (projectKey) => {
  router.push(`/project-workspaces/${encodeURIComponent(projectKey)}`)
}

const goAutomation = () => router.push('/automation')
const openRunConsole = (run) => {
  if (!run?.id) return
  router.push({ path: '/agent-runs', query: { runId: run.id } })
}
const goAgentRuns = () => router.push('/agent-runs')

const selectBusinessAction = (action) => {
  runForm.businessType = action.value
  if (action.value === 'FOLLOW_UP_TASK' && pendingApprovals.value[0]) {
    runForm.businessId = `APPROVAL-${pendingApprovals.value[0].id}`
  }
}

const assignmentFor = (targetType, targetId) => assignments.value.find(item => item.targetType === targetType && String(item.targetId) === String(targetId))
const assignmentSummary = (type, id) => {
  const assignment = assignmentFor(type, id)
  return `${assigneeLabel(assignment?.assigneeName, assignment?.assigneeUserId)} · ${assignmentStatusText(assignment?.status)}`
}
const canAssignTarget = (type, id) => {
  if (!canManageAssignments.value || !membersAvailable.value || !assignmentsAvailable.value) return false
  const assignment = assignmentFor(type, id)
  return !assignment || assignment.canReassign === true
}

const openAssignment = (targetType, targetId) => {
  if (!canAssignTarget(targetType, targetId)) return
  const existing = assignmentFor(targetType, targetId)
  if (existing) return openReassignment(existing)
  Object.assign(assignmentDialog, {
    visible: true, assignmentId: null, projectKey: activeProjectKey.value, targetType, targetId,
    targetLabel: `${assignmentTargetText(targetType)} #${targetId}`, fromName: '', assigneeUserId: null,
    expectedVersion: null, reason: '', error: ''
  })
}

const openReassignment = (assignment) => {
  if (!assignment.canReassign || assignment.projectKey !== activeProjectKey.value || !membersAvailable.value) return
  Object.assign(assignmentDialog, {
    visible: true, assignmentId: assignment.id, projectKey: assignment.projectKey, targetType: assignment.targetType,
    targetId: assignment.targetId, targetLabel: `${assignmentTargetText(assignment.targetType)} #${assignment.targetId}`,
    fromName: assigneeLabel(assignment.assigneeName, assignment.assigneeUserId), assigneeUserId: null,
    expectedVersion: assignment.version, reason: '', error: ''
  })
}

const refreshAfterAssignmentError = async (error, projectKey, revision) => {
  if (!isCurrentContext(projectKey, revision)) return
  if ([403, 404].includes(projectErrorStatus(error))) {
    resetProjectContext()
    await Promise.all([loadWarRoom(projectKey), loadMyAssignments()])
  } else if (projectErrorStatus(error) === 409) {
    await refreshAssignments()
  }
}

const saveAssignment = async () => {
  if (assignmentBusy.value || !assignmentDialog.visible) return
  const projectKey = assignmentDialog.projectKey
  const revision = contextRevision
  const member = findMember(assignmentDialog.assigneeUserId)
  if (!isCurrentContext(projectKey, revision) || !canManageAssignments.value || !member) {
    assignmentDialog.error = '请选择当前项目的有效成员，或刷新项目权限'
    return
  }
  const snapshot = { ...assignmentDialog }
  const existing = snapshot.assignmentId ? assignments.value.find(item => item.id === snapshot.assignmentId) : null
  if (existing && String(existing.assigneeUserId) === String(snapshot.assigneeUserId)) {
    assignmentDialog.error = '请选择与当前处理人不同的成员'
    return
  }
  assignmentBusy.value = true
  try {
    const message = snapshot.assignmentId
      ? `将「${snapshot.targetLabel}」从 ${snapshot.fromName} 改派给 ${memberName(member)}。确认后会记录改派事件。`
      : `将「${snapshot.targetLabel}」指派给 ${memberName(member)}。确认后会进入处理人的待办。`
    await ElMessageBox.confirm(message, snapshot.assignmentId ? '确认改派' : '确认指派', { type: 'warning', confirmButtonText: '确认', cancelButtonText: '返回' })
    if (!isCurrentContext(projectKey, revision) || !assignmentDialog.visible) return
    const request = snapshot.assignmentId
      ? api.reassignProjectAssignment(projectKey, snapshot.assignmentId, { assigneeUserId: snapshot.assigneeUserId, expectedVersion: snapshot.expectedVersion, reason: snapshot.reason.trim() })
      : api.createProjectAssignment(projectKey, { targetType: snapshot.targetType, targetId: snapshot.targetId, assigneeUserId: snapshot.assigneeUserId, reason: snapshot.reason.trim() })
    projectResponseData(await request)
    if (!isCurrentContext(projectKey, revision)) return
    assignmentDialog.visible = false
    ElMessage.success(snapshot.assignmentId ? '已改派' : '已指派')
    await refreshAssignments()
  } catch (error) {
    if (isConfirmationCancelled(error) || !isCurrentContext(projectKey, revision)) return
    if ([403, 404].includes(projectErrorStatus(error))) ElMessage.error(projectErrorText(error))
    await refreshAfterAssignmentError(error, projectKey, revision)
    if (!isCurrentContext(projectKey, revision)) return
    assignmentDialog.error = projectErrorText(error, '指派失败，请稍后重试')
    if (projectErrorStatus(error) === 409) {
      const latest = assignmentFor(snapshot.targetType, snapshot.targetId)
      if (!assignmentsAvailable.value) {
        assignmentDialog.visible = false
        ElMessage.error('最新指派状态加载失败，请刷新后重试')
      } else if (latest?.canReassign) {
        // Keep the chosen new assignee and reason, but require confirmation against the latest version.
        assignmentDialog.assignmentId = latest.id
        assignmentDialog.expectedVersion = latest.version
        assignmentDialog.fromName = assigneeLabel(latest.assigneeName, latest.assigneeUserId)
      } else {
        assignmentDialog.visible = false
        ElMessage.warning('该指派当前已无法改派，请查看最新状态')
      }
    }
  } finally {
    assignmentBusy.value = false
  }
}

const performAssignmentAction = async (action, assignment) => {
  if (assignmentBusy.value || !['accept', 'complete'].includes(action)) return
  if (!(action === 'accept' ? assignment.canAccept : assignment.canComplete)) return
  const displayedProjectKey = activeProjectKey.value
  const revision = contextRevision
  const actionText = action === 'accept' ? '接受' : '完成'
  assignmentBusy.value = true
  try {
    await ElMessageBox.confirm(
      `确认${actionText}「${assignmentTargetText(assignment.targetType)} #${assignment.targetId}」的指派？${action === 'complete' ? '此操作只更新指派状态，审批及 Agent 执行仍需按各自流程处理。' : ''}`,
      `${actionText}指派`, { type: 'warning', confirmButtonText: actionText, cancelButtonText: '返回' }
    )
    if (!isCurrentContext(displayedProjectKey, revision)) return
    const data = { expectedVersion: assignment.version, reason: `项目待办${actionText}` }
    const response = action === 'accept'
      ? await api.acceptProjectAssignment(assignment.projectKey, assignment.id, data)
      : await api.completeProjectAssignment(assignment.projectKey, assignment.id, data)
    projectResponseData(response)
    if (!isCurrentContext(displayedProjectKey, revision)) return
    ElMessage.success(`已${actionText}指派`)
    await refreshAssignments()
  } catch (error) {
    if (isConfirmationCancelled(error) || !isCurrentContext(displayedProjectKey, revision)) return
    ElMessage.error(projectErrorText(error, `${actionText}指派失败，请重试`))
    if ([403, 404].includes(projectErrorStatus(error))) {
      myAssignments.value = myAssignments.value.filter(item => item.projectKey !== assignment.projectKey)
    }
    await refreshAfterAssignmentError(error, displayedProjectKey, revision)
  } finally {
    assignmentBusy.value = false
  }
}

const submitRun = async () => {
  if (!activeProjectKey.value || !canExecute.value || submittingRun.value) return
  if (!runForm.agentId) {
    ElMessage.warning('请选择一个可用的协作 Agent')
    return
  }
  const businessId = normalizedBusinessId()
  const projectKey = activeProjectKey.value
  const revision = contextRevision
  const payload = buildTeamRunPayload(runForm, businessId, canManageAssignments.value)
  if (payload.assigneeUserId && !findMember(payload.assigneeUserId)) {
    ElMessage.warning('处理人已不在当前项目成员列表，请重新选择')
    return
  }
  submittingRun.value = true
  try {
    await ElMessageBox.confirm(`将创建或复用一条可审计的 Team Agent 任务，并进入当前项目队列。${payload.assigneeUserId ? `同时指派给 ${memberName(findMember(payload.assigneeUserId))}。` : ''}`, '发起协作', {
      type: 'warning',
      confirmButtonText: '发起',
      cancelButtonText: '返回'
    })
    if (!isCurrentContext(projectKey, revision)) return
    projectResponseData(await api.createProjectTeamAgentRun(projectKey, payload))
    if (!isCurrentContext(projectKey, revision)) return
    ElMessage.success('协作任务已进入队列')
    runForm.businessId = ''
    runForm.inputText = ''
    runForm.assigneeUserId = null
    runForm.assignmentReason = ''
    await Promise.all([loadWarRoom(projectKey), loadMyAssignments()])
  } catch (error) {
    if (!isConfirmationCancelled(error) && isCurrentContext(projectKey, revision)) ElMessage.error(projectErrorText(error, '协作任务发起失败，请重试'))
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
  if (!canApproveAutomation.value) return
  const actionText = status === 'SUCCESS' ? '通过' : '驳回'
  const projectKey = activeProjectKey.value
  const revision = contextRevision
  try {
    await ElMessageBox.confirm(`确认${actionText}「${approval.pipelineTitle}」的${stageText(approval.approvalType)}审批？`, `${actionText}审批`, {
      type: status === 'SUCCESS' ? 'success' : 'warning',
      confirmButtonText: actionText,
      cancelButtonText: '返回'
    })
    if (!isCurrentContext(projectKey, revision)) return
    projectResponseData(await api.approveAutomation(approval.id, {
      status,
      comment: `作战室${actionText}`
    }))
    if (!isCurrentContext(projectKey, revision)) return
    ElMessage.success(`已${actionText}，流程将继续更新`)
    await loadWarRoom(projectKey)
  } catch (error) {
    if (!isConfirmationCancelled(error) && isCurrentContext(projectKey, revision)) ElMessage.error(projectErrorText(error, '审批操作失败，请稍后重试'))
  }
}

const createFollowUp = async (approval) => {
  if (!canExecute.value || submittingRun.value) return
  const agentId = runForm.agentId || agents.value[0]?.id
  if (!agentId) {
    ElMessage.warning(agentsError.value || '暂无可用协作 Agent，请先到 Agent 管理启用')
    return
  }
  const projectKey = activeProjectKey.value
  const revision = contextRevision
  const assignee = canManageAssignments.value ? findMember(runForm.assigneeUserId) : null
  submittingRun.value = true
  try {
    await ElMessageBox.confirm(`将为该审批创建一条 Team Agent 跟进任务。${assignee ? `同时指派给 ${memberName(assignee)}。` : '暂不指定处理人，可在队列中另行指派。'}`, '创建跟进', {
      type: 'warning',
      confirmButtonText: '创建跟进',
      cancelButtonText: '返回'
    })
    if (!isCurrentContext(projectKey, revision)) return
    projectResponseData(await api.createProjectTeamAgentRun(projectKey, {
      agentId,
      businessType: 'FOLLOW_UP_TASK',
      businessId: `APPROVAL-${approval.id}`,
      assigneeUserId: assignee?.userId || null,
      assignmentReason: assignee ? runForm.assignmentReason.trim() : null,
      input: {
        approvalId: approval.id,
        pipelineId: approval.pipelineId,
        stage: approval.approvalType,
        instruction: `跟进「${approval.pipelineTitle}」的${stageText(approval.approvalType)}审批`
      }
    }))
    if (!isCurrentContext(projectKey, revision)) return
    ElMessage.success('已创建跟进任务')
    await Promise.all([loadWarRoom(projectKey), loadMyAssignments()])
  } catch (error) {
    if (!isConfirmationCancelled(error) && isCurrentContext(projectKey, revision)) ElMessage.error(projectErrorText(error, '创建跟进任务失败，请重试'))
  } finally {
    submittingRun.value = false
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
    `提交时间：${formatProjectTime(approval.createTime)}`,
    approval.comment ? `说明：${approval.comment}` : '',
    ...extraLines
  ].filter(Boolean).join('\n'),
  '审批详情',
  { confirmButtonText: '知道了' }
)

const handleBlocker = async (approval) => {
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
  const projectKey = activeProjectKey.value
  const revision = contextRevision
  try {
    await ElMessageBox.confirm(message, '确认操作', {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '返回'
    })
    if (!isCurrentContext(projectKey, revision)) return
    projectResponseData(await action())
    if (!isCurrentContext(projectKey, revision)) return
    ElMessage.success('操作已完成')
    await loadWarRoom(projectKey)
  } catch (error) {
    if (!isConfirmationCancelled(error) && isCurrentContext(projectKey, revision)) ElMessage.error(projectErrorText(error, '操作失败，请重试'))
  }
}

const normalizedBusinessId = () => retainTeamRunBusinessId(runForm, defaultBusinessId)
const defaultBusinessId = () => {
  const prefix = {
    DELIVERY_REQUEST: 'DELIVERY',
    REQUIREMENT_REVIEW: 'REVIEW',
    RELEASE_GATE: 'RELEASE',
    FOLLOW_UP_TASK: 'FOLLOWUP'
  }[runForm.businessType] || 'TASK'
  return `${prefix}-${Date.now()}`
}
const findMember = (userId) => userId ? mentionableUsers.value.find(member => String(member.userId) === String(userId)) : undefined
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
const formatShortDate = (value) => value ? new Date(value).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }) : '-'
watch(() => route.params.projectKey, async (projectKey) => {
  resetProjectContext()
  await Promise.all([loadWarRoom(String(projectKey || '')), loadMyAssignments()])
})

onMounted(loadAll)
onUnmounted(() => {
  contextRevision += 1
  projectGuard.invalidate()
  listGuard.invalidate()
  assignmentsGuard.invalidate()
  mineGuard.invalidate()
  refreshGuard.invalidate()
  agentsGuard.invalidate()
})
</script>

<style scoped>
.war-room { color: var(--text-primary); }
.workspace-error { margin-bottom: 14px; }
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
