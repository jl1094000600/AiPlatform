<template>
  <div class="automation-page">
    <div class="page-head">
      <div>
        <h2>{{ t('automation.title') }}</h2>
        <p>{{ t('automation.subtitle') }}</p>
      </div>
      <div class="head-actions">
        <el-button @click="openTemplateEditor">{{ t('automation.templateManage') }}</el-button>
        <el-button type="primary" @click="createVisible = true">{{ t('automation.new') }}</el-button>
      </div>
    </div>

    <div class="summary-grid">
      <div class="summary-card" v-for="item in summaryCards" :key="item.label">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </div>
    </div>

    <section class="panel">
      <div class="panel-title">{{ t('automation.pipelines') }}</div>
      <el-table :data="pipelines" v-loading="loading" stripe @row-click="openDetail">
        <el-table-column prop="pipelineCode" :label="t('automation.code')" width="150" />
        <el-table-column prop="requirementTitle" :label="t('automation.requirement')" min-width="220" />
        <el-table-column prop="productLine" :label="t('automation.product')" width="140" />
        <el-table-column prop="projectName" :label="t('automation.project')" width="150" />
        <el-table-column :label="t('automation.currentStage')" width="180">
          <template #default="{ row }">{{ stageDisplayName(row.currentStage) }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="t('common.status')" width="130">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('automation.progress')" width="140">
          <template #default="{ row }">{{ row.passedStages || 0 }} / {{ row.totalStages || 7 }}</template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel approvals-panel">
      <div class="panel-title">{{ t('automation.reviewQueue') }}</div>
      <el-table :data="approvals" stripe>
        <el-table-column :label="t('automation.stage')" min-width="180">
          <template #default="{ row }">{{ stageDisplayName(row.approvalType) }}</template>
        </el-table-column>
        <el-table-column prop="reviewerRole" :label="t('automation.reviewerRole')" width="150" />
        <el-table-column prop="status" :label="t('common.status')" width="120">
          <template #default="{ row }">{{ statusText(row.status) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="180">
          <template #default="{ row }">
            <el-button
              v-if="row.approvalType === 'requirement_analysis'"
              size="small"
              @click="openPrdReview(row)"
            >{{ t('automation.viewPrd') }}</el-button>
            <el-button
              v-else-if="row.approvalType === 'code_generation'"
              size="small"
              @click="openCodeTree({ pipelineId: row.pipelineId, approval: row })"
            >{{ t('automation.viewCode') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-drawer v-model="detailVisible" :title="t('automation.detail')" size="560px">
      <div v-if="detail.pipeline" class="detail">
        <h3>{{ detail.pipeline.requirementTitle }}</h3>
        <p>{{ detail.pipeline.requirementSummary || t('automation.noSummary') }}</p>
        <div class="detail-overview">
          <div class="detail-overview-head">
            <h4>概况说明</h4>
            <el-tag :type="statusType(detail.pipeline.status)">{{ statusText(detail.pipeline.status) }}</el-tag>
          </div>
          <p>{{ pipelineOverviewText }}</p>
          <div class="overview-grid">
            <div v-for="item in detailOverviewItems" :key="item.label">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </div>
        <div v-if="detail.pipeline.autoDeployEnabled === 1" class="deploy-badge">
          <el-tag type="success">自动部署</el-tag>
          <span>部署配置 ID：{{ detail.pipeline.deployProfileId || '-' }}</span>
        </div>
        <div v-if="detail.pipeline.codeQualityEnabled === 1" class="deploy-badge">
          <el-tag type="warning">代码测评</el-tag>
          <span>标准 ID：{{ detail.pipeline.codeQualityStandardId || '-' }}</span>
          <span>评估模型：{{ detail.pipeline.qualityModelCode || '-' }}</span>
        </div>
        <div class="stage-list">
          <div v-for="stage in detail.stages" :key="stage.id" class="stage-item">
            <div>
              <strong>{{ stage.stageOrder }}. {{ stageDisplayName(stage) }}</strong>
              <span>{{ stageSummary(stage) }}</span>
            </div>
            <div class="stage-actions">
              <el-tag :type="statusType(stage.status)">{{ statusText(stage.status) }}</el-tag>
              <el-button
                v-if="stage.stageKey === 'code_generation' && (stage.status === 'QUEUED' || stage.status === 'RUNNING')"
                size="small"
                loading
              >{{ stage.status === 'QUEUED' ? t('automation.queued') : t('automation.codeGenerating') }}</el-button>
              <el-button
                v-else-if="stage.stageKey === 'code_generation' && stage.artifactPath"
                size="small"
                @click.stop="openCodeTree(stage)"
              >{{ t('automation.viewCode') }}</el-button>
              <el-button
                v-else-if="isStageRunnable(stage)"
                size="small"
                @click="runStage(stage)"
              >{{ stageRunButtonText(stage) }}</el-button>
            </div>
          </div>
        </div>
        <div v-if="buildRuns.length" class="deploy-runs">
          <h4>构建编译记录</h4>
          <div v-for="run in buildRuns" :key="run.id" class="deploy-run">
            <div class="deploy-run-head">
              <strong>{{ run.commandText || '未配置构建命令' }}</strong>
              <el-tag :type="statusType(run.status)">{{ statusText(run.status) }}</el-tag>
            </div>
            <div class="deploy-run-meta">
              <span>工作目录：{{ run.workDir || '-' }}</span>
              <span>退出码：{{ run.exitCode ?? '-' }}</span>
              <span>耗时：{{ run.durationMs || 0 }} ms</span>
            </div>
            <pre>{{ run.commandLog || run.errorMessage || '-' }}</pre>
          </div>
        </div>
        <div v-if="testRuns.length" class="deploy-runs">
          <h4>测试执行记录</h4>
          <div v-for="run in testRuns" :key="run.id" class="deploy-run">
            <div class="deploy-run-head">
              <strong>{{ run.commandText || '未配置测试命令' }}</strong>
              <el-tag :type="statusType(run.status)">{{ statusText(run.status) }}</el-tag>
            </div>
            <div class="deploy-run-meta">
              <span>工作目录：{{ run.workDir || '-' }}</span>
              <span>通过/失败/跳过：{{ run.passedCount || 0 }}/{{ run.failedCount || 0 }}/{{ run.skippedCount || 0 }}</span>
              <span>总数：{{ run.totalCount || 0 }}</span>
              <span>退出码：{{ run.exitCode ?? '-' }}</span>
              <span>耗时：{{ run.durationMs || 0 }} ms</span>
            </div>
            <pre>{{ run.commandLog || run.errorMessage || '-' }}</pre>
          </div>
        </div>
        <div v-if="deployRuns.length" class="deploy-runs">
          <h4>部署执行记录</h4>
          <div v-for="run in deployRuns" :key="run.id" class="deploy-run">
            <div class="deploy-run-head">
              <strong>{{ run.stageKey }}</strong>
              <el-tag :type="statusType(run.status)">{{ statusText(run.status) }}</el-tag>
            </div>
            <div class="deploy-run-meta">
              <span>{{ run.deployType || '-' }} / {{ run.environmentName || '-' }}</span>
              <span v-if="run.imageName">镜像：{{ run.imageName }}</span>
              <span v-if="run.containerName">容器：{{ run.containerName }}</span>
              <span v-if="run.jenkinsBuildUrl">Jenkins：{{ run.jenkinsBuildUrl }}</span>
              <span v-if="run.healthMessage">健康检查：{{ run.healthMessage }}</span>
            </div>
            <pre>{{ run.commandLog || run.errorMessage || '-' }}</pre>
          </div>
        </div>
        <div v-if="codeQualityRuns.length" class="quality-runs">
          <div class="quality-title">
            <h4>代码质量测评</h4>
            <el-button v-if="qualityStage" size="small" @click="rerunQualityEvaluation">重新评估</el-button>
          </div>
          <div v-for="run in codeQualityRuns" :key="run.id" class="quality-run">
            <div class="quality-run-head">
              <strong>评分 {{ run.overallScore ?? 0 }} / 100</strong>
              <el-tag :type="statusType(run.status)">{{ statusText(run.status) }}</el-tag>
            </div>
            <div class="quality-metrics">
              <span>模型：{{ run.modelCode || '-' }}</span>
              <span>Token：{{ run.totalTokens || 0 }}</span>
              <span>耗时：{{ run.durationMs || 0 }} ms</span>
            </div>
            <div class="quality-score-grid">
              <div v-for="metric in qualityMetricItems(run)" :key="metric.key" class="quality-score-item">
                <div>
                  <span>{{ metric.label }}</span>
                  <strong>{{ metric.value }}</strong>
                </div>
                <el-progress :percentage="metric.value" :show-text="false" />
              </div>
            </div>
            <p>{{ run.summary || run.errorMessage || '-' }}</p>
            <div v-if="qualityEvidence(run).length" class="quality-evidence">
              <div class="quality-subtitle">评估证据</div>
              <div v-for="evidence in qualityEvidence(run)" :key="evidence.id || evidence.toolName" class="quality-evidence-item">
                <div>
                  <el-tag size="small" :type="evidenceStatusType(evidence.status)">{{ evidence.status || '-' }}</el-tag>
                  <strong>{{ evidenceLabel(evidence) }}</strong>
                </div>
                <span>{{ evidence.summary || '-' }}</span>
                <small v-if="evidence.commandText">{{ evidence.commandText }}</small>
              </div>
            </div>
            <div v-if="qualityIssues(run).length" class="quality-issues">
              <div v-for="issue in qualityIssues(run)" :key="issue.id || issue.title" class="quality-issue">
                <el-tag size="small" :type="severityType(issue.severity)">{{ issue.severity || 'MAJOR' }}</el-tag>
                <div>
                  <strong>{{ issue.title }}</strong>
                  <span>{{ issue.filePath || '-' }}{{ issue.lineStart ? `:${issue.lineStart}` : '' }}</span>
                  <p>{{ issue.description || '-' }}</p>
                  <small v-if="issue.suggestion">建议：{{ issue.suggestion }}</small>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="createVisible" :title="t('automation.createTitle')" width="820px">
      <el-form :model="form" label-position="top">
        <el-form-item :label="t('automation.productLine')">
          <el-input v-model="form.productLine" placeholder="Core Platform" />
        </el-form-item>
        <el-form-item :label="t('automation.projectName')">
          <el-input v-model="form.projectName" placeholder="AI Platform" />
        </el-form-item>
        <el-form-item :label="t('automation.requirementTitle')">
          <el-input v-model="form.requirementTitle" placeholder="Automated delivery loop" />
        </el-form-item>
        <el-form-item :label="t('automation.requirementSummary')">
          <div class="requirement-input">
            <el-input v-model="form.requirementSummary" type="textarea" :rows="5" />
            <div class="requirement-toolbar">
              <input ref="imageInputRef" class="hidden-file-input" type="file" accept="image/*" multiple @change="handleFileSelection($event, 'IMAGE')" />
              <input ref="audioInputRef" class="hidden-file-input" type="file" accept="audio/*" multiple @change="handleFileSelection($event, 'AUDIO')" />
              <el-button size="small" @click="imageInputRef?.click()">添加图片</el-button>
              <el-button size="small" @click="audioInputRef?.click()">添加音频</el-button>
              <el-button size="small" :type="recording ? 'danger' : 'default'" @click="toggleRecording">
                {{ recording ? '停止录音' : '浏览器录音' }}
              </el-button>
              <el-button
                size="small"
                :type="realtimeAsrActive ? 'danger' : 'primary'"
                plain
                :loading="realtimeAsrConnecting"
                @click="toggleRealtimeAsr"
              >
                {{ realtimeAsrActive ? '停止实时语音' : '实时语音输入' }}
              </el-button>
              <span v-if="uploadingCount" class="requirement-hint">正在上传 {{ uploadingCount }} 个文件...</span>
            </div>
            <div v-if="realtimeAsrActive || realtimeAsrPartial || realtimeAsrStatus" class="realtime-asr-panel">
              <span class="realtime-asr-dot" :class="{ active: realtimeAsrActive }"></span>
              <span>{{ realtimeAsrStatus || '实时识别中' }}</span>
              <strong v-if="realtimeAsrPartial">{{ realtimeAsrPartial }}</strong>
            </div>

            <div v-if="requirementAttachments.length" class="requirement-attachments">
              <div v-for="attachment in requirementAttachments" :key="attachment.id" class="requirement-attachment">
                <div class="attachment-head">
                  <div>
                    <strong>{{ attachment.fileName }}</strong>
                    <span>{{ attachment.mediaType === 'IMAGE' ? '图片' : '音频' }} · {{ formatFileSize(attachment.fileSize) }}</span>
                  </div>
                  <el-tag size="small" :type="attachmentStatusType(attachment.latestTask?.status)">
                    {{ attachmentStatusText(attachment.latestTask?.status) }}
                  </el-tag>
                </div>
                <el-input
                  v-if="attachment.latestTask?.status === 'SUCCEEDED'"
                  v-model="attachmentEdits[attachment.id]"
                  type="textarea"
                  :rows="3"
                  placeholder="可修改图片或语音解析结果"
                  @input="attachmentDirty.add(attachment.id)"
                />
                <p v-if="attachment.latestTask?.status === 'FAILED'" class="attachment-error">
                  {{ attachment.latestTask?.errorMessage || '解析失败，请重试' }}
                </p>
                <div class="attachment-actions">
                  <el-button
                    v-if="attachment.latestTask?.status === 'SUCCEEDED'"
                    size="small"
                    :disabled="!attachmentDirty.has(attachment.id)"
                    @click="saveAttachmentResult(attachment)"
                  >保存解析结果</el-button>
                  <el-button
                    v-if="attachment.latestTask?.status === 'FAILED'"
                    size="small"
                    @click="retryAttachment(attachment)"
                  >重试解析</el-button>
                  <el-button size="small" type="danger" plain @click="deleteAttachment(attachment)">删除</el-button>
                </div>
              </div>
              <div class="requirement-merge-row">
                <span>解析结果可编辑，合并后会写入上方需求描述。</span>
                <el-button size="small" type="primary" :loading="mergingRequirement" @click="mergeRequirementDraft">
                  合并到需求描述
                </el-button>
              </div>
            </div>
          </div>
        </el-form-item>
        <el-form-item :label="t('automation.openModel')">
          <el-select v-model="form.modelId" filterable clearable style="width: 100%" @change="selectPipelineModel">
            <el-option
              v-for="model in models"
              :key="model.id"
              :label="model.modelName + ' / ' + model.modelCode"
              :value="model.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Skill">
          <el-select v-model="form.skillId" filterable clearable placeholder="可选，不选择则按默认流水线生成" style="width: 100%">
            <el-option
              v-for="skill in skills"
              :key="skill.id"
              :label="skill.skillName + ' / ' + skill.skillCode"
              :value="skill.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="自动部署">
          <el-switch v-model="form.autoDeployEnabled" active-text="启用" inactive-text="关闭" />
        </el-form-item>
        <el-form-item v-if="form.autoDeployEnabled" label="部署配置" required>
          <el-select v-model="form.deployProfileId" filterable clearable placeholder="选择 Docker 或 Jenkins 部署配置" style="width: 100%">
            <el-option
              v-for="profile in deployProfiles"
              :key="profile.id"
              :label="profile.profileName + ' / ' + profile.deployType + ' / ' + profile.environmentName"
              :value="profile.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Code Quality">
          <el-switch v-model="form.codeQualityEnabled" active-text="Enabled" inactive-text="Off" />
        </el-form-item>
        <el-form-item v-if="form.codeQualityEnabled" label="Code Quality Standard" required>
          <el-select v-model="form.codeQualityStandardId" filterable clearable placeholder="Select a code quality standard" style="width: 100%">
            <el-option
              v-for="standard in codeQualityStandards"
              :key="standard.id"
              :label="standard.standardName + ' / ' + standard.standardCode"
              :value="standard.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.codeQualityEnabled" label="代码评估模型" required>
          <el-select v-model="form.qualityModelCode" filterable clearable placeholder="选择用于代码质量评估的模型" style="width: 100%">
            <el-option
              v-for="model in models"
              :key="model.id"
              :label="model.modelName + ' / ' + model.modelCode"
              :value="model.modelCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('automation.template')">
          <div class="inline-field">
            <el-select v-model="form.templateFile" filterable style="width: 100%">
              <el-option
                v-for="template in prdTemplates"
                :key="template.fileName"
                :label="template.fileName"
                :value="template.fileName"
              />
            </el-select>
            <el-button @click="openTemplateEditor">{{ t('automation.editTemplate') }}</el-button>
          </div>
        </el-form-item>
        <div class="form-grid">
          <el-form-item :label="t('automation.projectMode')">
            <el-select v-model="form.projectMode">
              <el-option :label="t('automation.scratchProject')" value="scratch" />
              <el-option :label="t('automation.existingProject')" value="existing" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('automation.codeLevel')">
            <el-select v-model="form.codeLevel">
              <el-option :label="t('automation.levelProject')" value="project" />
              <el-option :label="t('automation.levelModule')" value="module" />
              <el-option :label="t('automation.levelPackage')" value="package" />
              <el-option :label="t('automation.levelComponent')" value="component" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item :label="t('automation.generateScope')">
          <el-checkbox v-model="form.generateFrontend">{{ t('automation.generateFrontend') }}</el-checkbox>
          <el-checkbox v-model="form.generateBackend">{{ t('automation.generateBackend') }}</el-checkbox>
        </el-form-item>
        <div class="form-grid">
          <el-form-item :label="t('automation.frontendOutput')">
            <el-tree-select
              v-model="form.frontendOutputPath"
              :data="projectDirectoryTree"
              node-key="value"
              check-strictly
              filterable
              :props="directoryTreeProps"
              :disabled="!form.generateFrontend"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item :label="t('automation.backendOutput')">
            <el-tree-select
              v-model="form.backendOutputPath"
              :data="projectDirectoryTree"
              node-key="value"
              check-strictly
              filterable
              :props="directoryTreeProps"
              :disabled="!form.generateBackend"
              style="width: 100%"
            />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="createPipeline">{{ t('common.create') }}</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="templateVisible" :title="t('automation.templateManage')" size="680px">
      <div class="template-toolbar">
        <el-select v-model="currentTemplateFile" filterable style="width: 100%" @change="loadTemplateContent">
          <el-option
            v-for="template in prdTemplates"
            :key="template.fileName"
            :label="template.fileName"
            :value="template.fileName"
          />
        </el-select>
        <el-input v-model="newTemplateName" :placeholder="t('automation.newTemplateName')" />
        <el-button @click="createTemplate">{{ t('common.create') }}</el-button>
      </div>
      <div class="template-meta">
        {{ t('automation.templateSavePath') }}：marketDoc/prd-templates/{{ currentTemplateFile || '-' }}
      </div>
      <el-input v-model="templateContent" type="textarea" :rows="24" v-loading="templateLoading" />
      <div class="drawer-footer">
        <el-button @click="templateVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveTemplate">{{ t('common.save') }}</el-button>
      </div>
    </el-drawer>

    <el-dialog v-model="prdVisible" title="PRD 审核" width="900px" :close-on-click-modal="false">
      <div class="prd-meta">
        <span>文件：{{ prdPath || '-' }}</span>
      </div>
      <el-input v-model="prdContent" type="textarea" :rows="20" />
      <template #footer>
        <el-button @click="prdVisible = false">取消</el-button>
        <el-button @click="regeneratePrd">重新生成</el-button>
        <el-button type="danger" plain @click="submitPrdReview('REJECTED')">拒绝</el-button>
        <el-button type="primary" @click="submitPrdReview('SUCCESS')">保存并通过</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="codeVisible" :title="t('automation.generatedCode')" width="980px" :close-on-click-modal="false">
      <div class="code-meta">
        <span>{{ t('common.status') }}：{{ statusText(codeTree.status || 'PENDING') }}</span>
        <span v-if="codeTree.batchId">批次：{{ codeTree.batchId }}</span>
        <span v-if="codeTree.artifactPath">{{ t('automation.directory') }}：{{ codeTree.artifactPath }}</span>
      </div>
      <div v-if="codeTree.batchId" class="code-feedback-panel">
        <el-alert
          v-if="codeTree.doubleFailed"
          type="error"
          :closable="false"
          title="AI 和人工均判定当前代码不符合需求，请重新生成代码。"
        >
          <div class="failure-reasons">
            <p v-if="codeTree.failureSummary?.aiFailureReason">AI原因：{{ codeTree.failureSummary.aiFailureReason }}</p>
            <p v-if="codeTree.failureSummary?.manualFailureReason">人工原因：{{ codeTree.failureSummary.manualFailureReason }}</p>
          </div>
        </el-alert>
        <div v-if="codeFeedbacks.length" class="feedback-list">
          <div v-for="feedback in codeFeedbacks" :key="feedback.id" class="feedback-item">
            <div class="feedback-head">
              <strong>{{ feedbackSourceLabel(feedback.feedbackSource) }}</strong>
              <el-tag size="small" :type="feedbackStatusType(feedback.alignmentStatus)">
                {{ feedbackStatusText(feedback.alignmentStatus) }}
              </el-tag>
              <span v-if="feedback.alignmentScore !== null && feedback.alignmentScore !== undefined">
                {{ feedback.alignmentScore }} / 100
              </span>
            </div>
            <p>{{ feedback.summary || '-' }}</p>
            <small v-if="feedback.failureReason">不合格原因：{{ feedback.failureReason }}</small>
          </div>
        </div>
        <div class="manual-feedback-form">
          <el-select v-model="codeFeedbackForm.alignmentStatus" size="small" style="width: 150px">
            <el-option label="合格" value="PASSED" />
            <el-option label="部分符合" value="PARTIAL" />
            <el-option label="不合格" value="FAILED" />
          </el-select>
          <el-input-number v-model="codeFeedbackForm.alignmentScore" size="small" :min="0" :max="100" />
          <el-input v-model="codeFeedbackForm.summary" size="small" placeholder="人工反馈说明" />
          <el-input
            v-if="codeFeedbackForm.alignmentStatus === 'FAILED'"
            v-model="codeFeedbackForm.failureReason"
            size="small"
            placeholder="不合格原因（必填）"
          />
          <el-button size="small" type="primary" :loading="feedbackSubmitting" @click="submitCodeFeedback">提交反馈</el-button>
        </div>
      </div>
      <div v-if="codeTree.status === 'QUEUED' || codeTree.status === 'RUNNING'" class="code-generating">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>{{ codeTree.status === 'QUEUED' ? t('automation.codeQueuedHint') : t('automation.codeGeneratingHint') }}</span>
      </div>
      <div v-else class="code-layout">
        <aside class="code-files">
          <button
            v-for="file in codeFiles"
            :key="file.path"
            class="code-file"
            :class="{ active: selectedCodeFile === file.path }"
            @click="selectCodeFile(file)"
          >
            <span>{{ file.path }}</span>
            <small>{{ formatBytes(file.size) }}</small>
          </button>
          <div v-if="!codeFiles.length" class="empty-code">{{ t('automation.noCodeFiles') }}</div>
        </aside>
        <main class="code-preview" v-loading="codeLoading">
          <div v-if="selectedCodeFile" class="code-preview-head">
            <strong>{{ selectedCodeFile }}</strong>
            <el-tag v-if="codeTruncated" type="warning">{{ t('automation.previewTruncated') }}</el-tag>
          </div>
          <pre>{{ codeContent || t('automation.selectCodeFile') }}</pre>
        </main>
      </div>
      <template #footer>
        <el-button @click="codeVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button v-if="codeTree.pipelineId" @click="regenerateCode">{{ t('automation.regenerateCode') }}</el-button>
        <el-button v-if="isPendingApproval(currentCodeApproval)" type="danger" plain @click="submitCodeReview('REJECTED')">{{ t('automation.reject') }}</el-button>
        <el-button v-if="isPendingApproval(currentCodeApproval)" type="primary" @click="submitCodeReview('SUCCESS')">{{ t('automation.approve') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import api from '../api'
import { useI18n } from '../i18n'
import { downsampleFloat32, float32ToPcm16Buffer } from '../utils/pcm'

const { t } = useI18n()

const loading = ref(false)
const createVisible = ref(false)
const detailVisible = ref(false)
const summary = ref({})
const pipelines = ref([])
const approvals = ref([])
const models = ref([])
const skills = ref([])
const deployProfiles = ref([])
const deployRuns = ref([])
const buildRuns = ref([])
const testRuns = ref([])
const codeQualityStandards = ref([])
const codeQualityRuns = ref([])
const prdTemplates = ref([])
const templateVisible = ref(false)
const currentTemplateFile = ref('')
const newTemplateName = ref('')
const templateContent = ref('')
const templateLoading = ref(false)
const projectDirectoryTree = ref([])
const directoryTreeProps = { label: 'label', children: 'children', disabled: 'disabled' }
const prdVisible = ref(false)
const currentApproval = ref(null)
const currentCodeApproval = ref(null)
const currentPrdStage = ref(null)
const prdContent = ref('')
const prdPath = ref('')
const codeVisible = ref(false)
const codeTree = ref({})
const codeFiles = ref([])
const selectedCodeFile = ref('')
const codeContent = ref('')
const codeLoading = ref(false)
const codeTruncated = ref(false)
const codeFeedbacks = ref([])
const feedbackSubmitting = ref(false)
const codeFeedbackForm = reactive({
  alignmentStatus: 'PASSED',
  alignmentScore: 80,
  summary: '',
  failureReason: ''
})
let detailPollTimer = null
const detail = reactive({ pipeline: null, stages: [], approvals: [] })
const currentUsername = () => {
  try {
    return JSON.parse(localStorage.getItem('user') || '{}')?.username || 'admin'
  } catch {
    return 'admin'
  }
}
const form = reactive({
  productLine: '',
  projectName: '',
  requirementTitle: '',
  requirementSummary: '',
  initiator: currentUsername(),
  modelId: null,
  aiModelCode: 'default-open-model',
  skillId: null,
  autoDeployEnabled: false,
  deployProfileId: null,
  codeQualityEnabled: false,
  codeQualityStandardId: null,
  qualityModelCode: '',
  templateFile: 'default-prd-template.md',
  projectMode: 'scratch',
  codeLevel: 'module',
  generateFrontend: true,
  generateBackend: true,
  frontendOutputPath: 'front/src/generated',
  backendOutputPath: 'backend/src/main/java/com/aipal/generated'
})

const createRequirementRequestId = () => {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `req_${Date.now()}_${Math.random().toString(36).slice(2, 12)}`
}

const imageInputRef = ref(null)
const audioInputRef = ref(null)
const requirementRequestId = ref(createRequirementRequestId())
const requirementAttachments = ref([])
const attachmentEdits = reactive({})
const attachmentDirty = reactive(new Set())
const uploadingCount = ref(0)
const recording = ref(false)
const realtimeAsrActive = ref(false)
const realtimeAsrConnecting = ref(false)
const realtimeAsrPartial = ref('')
const realtimeAsrStatus = ref('')
const realtimeAsrSessionId = ref('')
const mergingRequirement = ref(false)
const requirementOriginalText = ref('')
const requirementDraftMerged = ref(false)
const requirementMergeStale = ref(false)
let requirementPollTimer = null
let mediaRecorder = null
let microphoneStream = null
let recordingChunks = []
let discardRecording = false
let realtimeAsrSocket = null
let realtimeAsrStream = null
let realtimeAudioContext = null
let realtimeAudioSource = null
let realtimeAudioProcessor = null
let realtimePcmQueue = []
let realtimePcmSampleCount = 0

const REALTIME_ASR_SAMPLE_RATE = 16000
const REALTIME_ASR_FRAME_SAMPLES = REALTIME_ASR_SAMPLE_RATE / 10

const processingRequirementAttachments = computed(() => requirementAttachments.value.some(item =>
  ['PENDING', 'RUNNING'].includes(item.latestTask?.status)
))

const successfulRequirementAttachments = computed(() => requirementAttachments.value.filter(item =>
  item.latestTask?.status === 'SUCCEEDED'
))

const failedRequirementAttachments = computed(() => requirementAttachments.value.filter(item =>
  item.latestTask?.status === 'FAILED'
))

const summaryCards = computed(() => [
  { label: t('automation.total'), value: summary.value.totalPipelines || 0 },
  { label: t('automation.running'), value: summary.value.runningPipelines || 0 },
  { label: t('automation.waitingReview'), value: summary.value.waitingApprovals || 0 },
  { label: t('automation.stagePassRate'), value: (summary.value.stagePassRate || 0) + '%' }
])

const stageNameMap = {
  requirement_analysis: '需求分析',
  code_generation: '代码生成',
  code_quality_evaluation: '代码质量评估',
  build_compile: '构建编译',
  test_execution: '测试执行',
  deployment_release: '部署发布',
  operations_monitoring: '运维监控',
  delivery_report: '交付报告'
}

const legacyStageNameMap = {
  'Requirement Analysis': '需求分析',
  'Code Generation': '代码生成',
  'Code Quality Evaluation': '代码质量评估',
  'Build Compile': '构建编译',
  'Test Execution': '测试执行',
  'Deployment Release': '部署发布',
  'Operations Monitoring': '运维监控',
  'Delivery Report': '交付报告'
}

const stageIntroMap = {
  requirement_analysis: '梳理业务目标、范围、验收标准与交付约束，生成 PRD 并等待产品审核。',
  code_generation: '根据已确认的 PRD 生成工程代码、目录结构和必要配置。',
  code_quality_evaluation: '检查生成代码的质量、风险和门禁结果，决定是否进入后续交付。',
  build_compile: '执行构建和编译检查，确认依赖、语法和工程结构可用。',
  test_execution: '运行测试并验证核心流程，确认功能符合需求说明。',
  deployment_release: '按部署配置发布产物，并记录部署执行结果。',
  operations_monitoring: '观察运行状态、健康检查和异常信息，确认服务稳定。',
  delivery_report: '汇总需求、代码、测试、部署和审核结果，形成交付说明。'
}

const stageKeyOf = (stage) => {
  if (!stage) return ''
  return typeof stage === 'string' ? stage : (stage.stageKey || stage.currentStage || stage.approvalType || '')
}

const stageDisplayName = (stage) => {
  const key = stageKeyOf(stage)
  if (stageNameMap[key]) return stageNameMap[key]
  if (typeof stage === 'object' && legacyStageNameMap[stage.stageName]) {
    return legacyStageNameMap[stage.stageName]
  }
  return legacyStageNameMap[key] || key || '-'
}

const localizeStageSummary = (stage, text) => {
  const key = stageKeyOf(stage)
  const title = detail.pipeline?.requirementTitle || '当前需求'
  if (!text) return stageIntroMap[key] || '等待流水线调度处理。'
  if (text === `${legacyStageNameMap[stage?.stageName] || stage?.stageName} input for ${title}`) {
    return stageIntroMap[key] || text
  }
  if (text.includes(' input for ')) {
    return stageIntroMap[key] || text
  }
  if (text.startsWith('PRD generated:')) {
    return `PRD 已生成：${text.replace('PRD generated:', '').trim()}`
  }
  if (text.startsWith('Code generation finished with')) {
    const count = text.match(/with\s+(\d+)\s+files/)?.[1] || '-'
    return text.includes('quality evaluation')
      ? `代码生成完成，共 ${count} 个文件，等待代码质量评估。`
      : `代码生成完成，共 ${count} 个文件，等待架构师审核。`
  }
  if (text.startsWith('AI generated')) {
    return `${stageDisplayName(stage)}已完成，流水线进入人工审核或下一阶段处理。`
  }
  return text
}

const stageSummary = (stage) => localizeStageSummary(stage, stage.outputSummary || stage.inputSummary)

const detailOverviewItems = computed(() => {
  if (!detail.pipeline) return []
  const passed = detail.pipeline.passedStages || 0
  const total = detail.pipeline.totalStages || detail.stages.length || 0
  return [
    { label: '产品线', value: detail.pipeline.productLine || '-' },
    { label: '项目', value: detail.pipeline.projectName || '-' },
    { label: '当前阶段', value: stageDisplayName(detail.pipeline.currentStage) },
    { label: '整体进度', value: `${passed} / ${total}` }
  ]
})

const pipelineOverviewText = computed(() => {
  if (!detail.pipeline) return ''
  const currentStage = stageDisplayName(detail.pipeline.currentStage)
  const waitingReview = detail.approvals?.filter(item => item.status === 'PENDING').length || 0
  const reviewText = waitingReview ? `当前有 ${waitingReview} 个事项等待审核。` : '当前没有待审核事项。'
  return `该流水线围绕「${detail.pipeline.requirementTitle}」推进自动化交付，当前处于「${currentStage}」阶段。${reviewText}`
})

const qualityStage = computed(() => detail.stages.find(stage => stage.stageKey === 'code_quality_evaluation'))

const qualityMetricLabels = {
  prdAlignment: '需求符合度',
  runnable: '可运行性',
  security: '安全性',
  architecture: '架构合理性',
  maintainability: '可维护性',
  readability: '可读性',
  testability: '测试完整性',
  performance: '性能风险'
}

const parseJsonObject = (value) => {
  if (!value) return {}
  if (typeof value === 'object') return value
  try {
    return JSON.parse(value)
  } catch {
    return {}
  }
}

const qualityMetricItems = (run) => {
  const metrics = parseJsonObject(run.metricsJson)
  return Object.entries(qualityMetricLabels).map(([key, label]) => ({
    key,
    label,
    value: Math.max(0, Math.min(100, Number(metrics[key] ?? 0)))
  }))
}

const severityOrder = { BLOCKER: 1, CRITICAL: 2, MAJOR: 3, MINOR: 4, INFO: 5 }
const qualityIssues = (run) => [...(run.issues || [])].sort((a, b) => {
  const left = severityOrder[a.severity] || 9
  const right = severityOrder[b.severity] || 9
  return left - right
})

const qualityEvidence = (run) => [...(run.evidence || [])]

const severityType = (severity) => {
  if (severity === 'BLOCKER' || severity === 'CRITICAL') return 'danger'
  if (severity === 'MAJOR') return 'warning'
  if (severity === 'MINOR') return 'info'
  return 'success'
}

const evidenceStatusType = (status) => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'UNAVAILABLE' || status === 'SKIPPED') return 'info'
  return 'warning'
}

const evidenceTypeLabels = {
  artifact: '生成产物',
  command_plan: '命令检测',
  build: '构建验证',
  test: '测试验证',
  static_scan: '静态扫描',
  security_scan: '安全扫描'
}

const evidenceLabel = (evidence) => {
  const type = evidenceTypeLabels[evidence.evidenceType] || evidence.evidenceType || '-'
  return `${type} / ${evidence.toolName || '-'}`
}

const loadAll = async () => {
  loading.value = true
  try {
    const [summaryRes, pipelineRes, approvalRes, modelsRes, skillsRes, deployProfilesRes, qualityStandardsRes, prdTemplatesRes, directoriesRes] = await Promise.all([
      api.getAutomationSummary(),
      api.getAutomationPipelines({ pageNum: 1, pageSize: 20 }),
      api.getAutomationApprovals({ pageNum: 1, pageSize: 20, status: 'PENDING' }),
      api.getModels({ pageNum: 1, pageSize: 100 }),
      api.getEnabledSkills(),
      api.getEnabledAutomationDeployProfiles(),
      api.getEnabledCodeQualityStandards(),
      api.getAutomationPrdTemplates(),
      api.getAutomationProjectDirectories()
    ])
    summary.value = summaryRes.data.data || {}
    pipelines.value = pipelineRes.data.data?.records || []
    approvals.value = approvalRes.data.data?.records || []
    models.value = modelsRes.data.data?.records || []
    skills.value = skillsRes.data.data || []
    deployProfiles.value = deployProfilesRes.data.data || []
    codeQualityStandards.value = qualityStandardsRes.data.data || []
    prdTemplates.value = prdTemplatesRes.data.data || []
    projectDirectoryTree.value = directoriesRes.data.data ? [directoriesRes.data.data] : []
    if (!form.templateFile && prdTemplates.value.length) {
      form.templateFile = prdTemplates.value[0].fileName
    }
  } finally {
    loading.value = false
  }
}

const requirementErrorMessage = (error, fallback) => (
  error?.response?.data?.message || error?.message || fallback
)

const formatFileSize = (value) => {
  const size = Number(value || 0)
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

const attachmentStatusText = (status) => ({
  PENDING: '等待解析',
  RUNNING: '解析中',
  SUCCEEDED: '解析完成',
  FAILED: '解析失败'
}[status] || '等待上传')

const attachmentStatusType = (status) => {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

const stopRequirementPolling = () => {
  if (requirementPollTimer) {
    clearInterval(requirementPollTimer)
    requirementPollTimer = null
  }
}

const refreshRequirementAttachments = async () => {
  const response = await api.getRequirementAttachments(requirementRequestId.value)
  requirementAttachments.value = response.data.data || []
  requirementAttachments.value.forEach(attachment => {
    if (!attachmentDirty.has(attachment.id)) {
      attachmentEdits[attachment.id] = attachment.latestTask?.editedResult
        ?? attachment.latestTask?.resultText
        ?? ''
    }
  })
  if (!processingRequirementAttachments.value) stopRequirementPolling()
}

const startRequirementPolling = () => {
  if (requirementPollTimer) return
  requirementPollTimer = setInterval(async () => {
    try {
      await refreshRequirementAttachments()
    } catch (error) {
      stopRequirementPolling()
      ElMessage.error(requirementErrorMessage(error, '刷新附件解析状态失败'))
    }
  }, 1800)
}

const validateRequirementFile = (file, mediaType) => {
  const expectedPrefix = mediaType === 'IMAGE' ? 'image/' : 'audio/'
  const maxBytes = mediaType === 'IMAGE' ? 15 * 1024 * 1024 : 50 * 1024 * 1024
  if (!file.type?.startsWith(expectedPrefix)) {
    ElMessage.warning(`${file.name} 不是支持的${mediaType === 'IMAGE' ? '图片' : '音频'}文件`)
    return false
  }
  if (file.size > maxBytes) {
    ElMessage.warning(`${file.name} 超过 ${mediaType === 'IMAGE' ? '15MB' : '50MB'} 限制`)
    return false
  }
  return true
}

const uploadRequirementFiles = async (files, mediaType) => {
  const acceptedFiles = Array.from(files || []).filter(file => validateRequirementFile(file, mediaType))
  if (!acceptedFiles.length) return
  uploadingCount.value += acceptedFiles.length
  requirementMergeStale.value = true
  await Promise.all(acceptedFiles.map(async file => {
    try {
      await api.uploadRequirementAttachment(requirementRequestId.value, file)
    } catch (error) {
      ElMessage.error(requirementErrorMessage(error, `${file.name} 上传失败`))
    } finally {
      uploadingCount.value -= 1
    }
  }))
  await refreshRequirementAttachments()
  if (processingRequirementAttachments.value) startRequirementPolling()
}

const handleFileSelection = async (event, mediaType) => {
  const input = event.target
  await uploadRequirementFiles(input.files, mediaType)
  input.value = ''
}

const withQueryParam = (url, key, value) => {
  if (!value) return url
  const separator = url.includes('?') ? '&' : '?'
  return `${url}${separator}${encodeURIComponent(key)}=${encodeURIComponent(value)}`
}

const websocketUrlFromPath = (path) => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}${path.startsWith('/') ? path : `/${path}`}`
}

const buildAsrWebSocketUrl = (session) => {
  const sessionId = session.sessionId || session.id || realtimeAsrSessionId.value
  const token = session.wsToken || session.websocketToken || session.token || session.accessToken
  let wsUrl = session.wsUrl || session.websocketUrl || session.url

  if (wsUrl) {
    if (wsUrl.startsWith('http://') || wsUrl.startsWith('https://')) {
      wsUrl = wsUrl.replace(/^http/, 'ws')
    }
    if (wsUrl.startsWith('/')) {
      wsUrl = websocketUrlFromPath(wsUrl)
    } else if (!wsUrl.startsWith('ws://') && !wsUrl.startsWith('wss://')) {
      wsUrl = websocketUrlFromPath(wsUrl)
    }
    return wsUrl.includes('token=') ? wsUrl : withQueryParam(wsUrl, 'token', token)
  }

  if (!sessionId) return ''
  return withQueryParam(websocketUrlFromPath(`/api/asr/realtime?sessionId=${encodeURIComponent(sessionId)}`), 'token', token)
}

const resetRealtimePcmQueue = () => {
  realtimePcmQueue = []
  realtimePcmSampleCount = 0
}

const sendRealtimePcmFrames = (sendRemaining = false) => {
  if (!realtimeAsrSocket || realtimeAsrSocket.readyState !== WebSocket.OPEN) return
  const targetSamples = sendRemaining ? realtimePcmSampleCount : REALTIME_ASR_FRAME_SAMPLES

  while (realtimePcmSampleCount >= targetSamples && targetSamples > 0) {
    const frame = new Float32Array(targetSamples)
    let offset = 0

    while (offset < targetSamples && realtimePcmQueue.length) {
      const chunk = realtimePcmQueue[0]
      const take = Math.min(chunk.length, targetSamples - offset)
      frame.set(chunk.subarray(0, take), offset)
      offset += take

      if (take === chunk.length) {
        realtimePcmQueue.shift()
      } else {
        realtimePcmQueue[0] = chunk.subarray(take)
      }
    }

    realtimePcmSampleCount -= targetSamples
    realtimeAsrSocket.send(float32ToPcm16Buffer(frame))
    if (sendRemaining) break
  }
}

const enqueueRealtimeSamples = (samples) => {
  if (!samples.length) return
  realtimePcmQueue.push(samples)
  realtimePcmSampleCount += samples.length
  sendRealtimePcmFrames()
}

const appendRealtimeFinalText = (text) => {
  const finalText = (text || '').trim()
  if (!finalText) return
  const current = form.requirementSummary.trimEnd()
  form.requirementSummary = current ? `${current}\n${finalText}` : finalText
  realtimeAsrPartial.value = ''
  realtimeAsrStatus.value = '已写入需求描述'
}

const cleanupRealtimeAsr = ({ closeSocket = true } = {}) => {
  realtimeAudioProcessor?.disconnect()
  realtimeAudioSource?.disconnect()
  realtimeAsrStream?.getTracks().forEach(track => track.stop())
  realtimeAudioContext?.close()
  if (closeSocket && realtimeAsrSocket) {
    if (realtimeAsrSocket.readyState === WebSocket.OPEN) {
      sendRealtimePcmFrames(true)
      realtimeAsrSocket.send(JSON.stringify({ type: 'stop' }))
    }
    realtimeAsrSocket.close(1000, 'client stop')
  }

  realtimeAsrStream = null
  realtimeAudioContext = null
  realtimeAudioSource = null
  realtimeAudioProcessor = null
  realtimeAsrSocket = null
  realtimeAsrSessionId.value = ''
  resetRealtimePcmQueue()
}

const stopRealtimeAsr = ({ completed = false, keepStatus = false } = {}) => {
  realtimeAsrActive.value = false
  realtimeAsrConnecting.value = false
  cleanupRealtimeAsr()
  realtimeAsrPartial.value = ''
  if (!keepStatus) {
    realtimeAsrStatus.value = completed ? '实时识别已完成' : ''
  }
}

const handleRealtimeAsrMessage = (event) => {
  if (typeof event.data !== 'string') return
  let message = {}
  try {
    message = JSON.parse(event.data)
  } catch {
    return
  }

  const eventType = String(message.type || message.event || message.status || '').toLowerCase()
  const text = message.text || message.result || message.transcript || message.sentence || ''

  if (eventType === 'ready') {
    realtimeAsrStatus.value = '实时识别已连接'
    return
  }
  if (eventType === 'partial') {
    realtimeAsrPartial.value = text
    realtimeAsrStatus.value = '实时识别中'
    return
  }
  if (eventType === 'final') {
    appendRealtimeFinalText(text)
    return
  }
  if (eventType === 'completed') {
    stopRealtimeAsr({ completed: true })
    return
  }
  if (eventType === 'error') {
    stopRealtimeAsr({ keepStatus: true })
    realtimeAsrStatus.value = '实时识别已中断'
    ElMessage.error(message.message || text || '实时语音识别失败')
  }
}

const connectRealtimeAsrSocket = (wsUrl, sessionId) => new Promise((resolve, reject) => {
  const socket = new WebSocket(wsUrl)
  socket.binaryType = 'arraybuffer'
  realtimeAsrSocket = socket
  socket.onmessage = handleRealtimeAsrMessage

  socket.onopen = () => {
    socket.send(JSON.stringify({
      type: 'start',
      sessionId,
      format: 'pcm_s16le',
      sampleRate: REALTIME_ASR_SAMPLE_RATE,
      channels: 1,
      frameDurationMs: 100
    }))
    resolve(socket)
  }
  socket.onerror = () => reject(new Error('实时语音连接失败'))
})

const startRealtimeAudioProcessing = (stream) => {
  const AudioContextConstructor = window.AudioContext || window.webkitAudioContext
  if (!AudioContextConstructor) {
    throw new Error('当前浏览器不支持 AudioContext')
  }

  realtimeAsrStream = stream
  realtimeAudioContext = new AudioContextConstructor()
  realtimeAudioSource = realtimeAudioContext.createMediaStreamSource(stream)
  realtimeAudioProcessor = realtimeAudioContext.createScriptProcessor(4096, 1, 1)
  realtimeAudioProcessor.onaudioprocess = event => {
    const output = event.outputBuffer.getChannelData(0)
    output.fill(0)
    if (!realtimeAsrActive.value || !realtimeAsrSocket || realtimeAsrSocket.readyState !== WebSocket.OPEN) return
    const input = event.inputBuffer.getChannelData(0)
    try {
      const samples = downsampleFloat32(input, realtimeAudioContext.sampleRate, REALTIME_ASR_SAMPLE_RATE)
      enqueueRealtimeSamples(samples)
    } catch (error) {
      stopRealtimeAsr({ keepStatus: true })
      realtimeAsrStatus.value = '实时识别音频处理失败'
      ElMessage.error(error.message || '实时识别音频处理失败')
    }
  }
  realtimeAudioSource.connect(realtimeAudioProcessor)
  realtimeAudioProcessor.connect(realtimeAudioContext.destination)
}

const toggleRealtimeAsr = async () => {
  if (realtimeAsrActive.value || realtimeAsrConnecting.value) {
    stopRealtimeAsr()
    return
  }
  if (recording.value) {
    ElMessage.warning('请先停止浏览器录音')
    return
  }
  if (!navigator.mediaDevices?.getUserMedia || typeof WebSocket === 'undefined') {
    ElMessage.warning('当前浏览器不支持实时语音输入，请使用添加音频或浏览器录音')
    return
  }

  realtimeAsrConnecting.value = true
  realtimeAsrStatus.value = '正在创建识别会话...'
  realtimeAsrPartial.value = ''

  try {
    const response = await api.createAsrSession({
      requestId: requirementRequestId.value,
      format: 'pcm_s16le',
      sampleRate: REALTIME_ASR_SAMPLE_RATE,
      channels: 1,
      frameDurationMs: 100
    })
    const session = response.data?.data || response.data || {}
    realtimeAsrSessionId.value = session.sessionId || session.id || ''
    const wsUrl = buildAsrWebSocketUrl(session)
    if (!wsUrl) throw new Error('后端未返回实时识别 WebSocket 地址')

    realtimeAsrStatus.value = '正在连接实时识别...'
    realtimeAsrSocket = await connectRealtimeAsrSocket(wsUrl, realtimeAsrSessionId.value)
    realtimeAsrSocket.onerror = () => {
      if (realtimeAsrActive.value || realtimeAsrConnecting.value) {
        stopRealtimeAsr({ keepStatus: true })
        realtimeAsrStatus.value = '实时识别连接异常'
        ElMessage.error('实时语音连接异常')
      }
    }
    realtimeAsrSocket.onclose = () => {
      if (realtimeAsrActive.value) {
        stopRealtimeAsr({ keepStatus: true })
        realtimeAsrStatus.value = '实时识别连接已断开'
        ElMessage.warning('实时语音连接已断开')
      }
    }

    realtimeAsrStatus.value = '正在请求麦克风权限...'
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: {
        channelCount: 1,
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true
      }
    })
    realtimeAsrActive.value = true
    realtimeAsrConnecting.value = false
    realtimeAsrStatus.value = '实时识别中'
    startRealtimeAudioProcessing(stream)
  } catch (error) {
    stopRealtimeAsr({ keepStatus: true })
    realtimeAsrStatus.value = ''
    const fallback = error?.response?.status === 404
      ? '实时语音会话接口暂不可用，请使用添加音频或浏览器录音'
      : '启动实时语音输入失败'
    ElMessage.error(requirementErrorMessage(error, fallback))
  }
}

const releaseMicrophone = () => {
  microphoneStream?.getTracks().forEach(track => track.stop())
  microphoneStream = null
  mediaRecorder = null
  recordingChunks = []
}

const toggleRecording = async () => {
  if (recording.value) {
    mediaRecorder?.stop()
    return
  }
  if (realtimeAsrActive.value || realtimeAsrConnecting.value) {
    ElMessage.warning('请先停止实时语音输入')
    return
  }
  if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
    ElMessage.warning('当前浏览器不支持录音，请改为上传音频文件')
    return
  }
  try {
    discardRecording = false
    microphoneStream = await navigator.mediaDevices.getUserMedia({ audio: true })
    const options = MediaRecorder.isTypeSupported('audio/webm') ? { mimeType: 'audio/webm' } : undefined
    mediaRecorder = new MediaRecorder(microphoneStream, options)
    recordingChunks = []
    mediaRecorder.ondataavailable = event => {
      if (event.data.size) recordingChunks.push(event.data)
    }
    mediaRecorder.onstop = async () => {
      const chunks = [...recordingChunks]
      const shouldUpload = !discardRecording && chunks.length > 0
      recording.value = false
      releaseMicrophone()
      if (shouldUpload) {
        const file = new File(chunks, `recording-${Date.now()}.webm`, { type: 'audio/webm' })
        await uploadRequirementFiles([file], 'AUDIO')
      }
    }
    mediaRecorder.start()
    recording.value = true
  } catch (error) {
    releaseMicrophone()
    ElMessage.error(requirementErrorMessage(error, '无法访问麦克风'))
  }
}

const saveAttachmentResult = async (attachment) => {
  try {
    await api.updateRequirementAttachmentResult(attachment.id, attachmentEdits[attachment.id] || '')
    attachmentDirty.delete(attachment.id)
    requirementMergeStale.value = true
    await refreshRequirementAttachments()
    ElMessage.success('解析结果已保存')
    return true
  } catch (error) {
    ElMessage.error(requirementErrorMessage(error, '保存解析结果失败'))
    return false
  }
}

const retryAttachment = async (attachment) => {
  try {
    await api.retryRequirementAttachment(attachment.id)
    attachmentDirty.delete(attachment.id)
    requirementMergeStale.value = true
    await refreshRequirementAttachments()
    startRequirementPolling()
  } catch (error) {
    ElMessage.error(requirementErrorMessage(error, '重试解析失败'))
  }
}

const deleteAttachment = async (attachment) => {
  try {
    await api.deleteRequirementAttachment(attachment.id)
    attachmentDirty.delete(attachment.id)
    delete attachmentEdits[attachment.id]
    requirementMergeStale.value = true
    await refreshRequirementAttachments()
    if (!requirementAttachments.value.length && requirementDraftMerged.value) {
      form.requirementSummary = requirementOriginalText.value
      requirementDraftMerged.value = false
      requirementMergeStale.value = false
    }
  } catch (error) {
    ElMessage.error(requirementErrorMessage(error, '删除附件失败'))
  }
}

const mergeRequirementDraft = async () => {
  if (uploadingCount.value || processingRequirementAttachments.value) {
    ElMessage.warning('请等待图片和音频解析完成')
    return false
  }
  for (const attachment of successfulRequirementAttachments.value) {
    if (attachmentDirty.has(attachment.id)) {
      const saved = await saveAttachmentResult(attachment)
      if (!saved) return false
    }
  }
  const attachmentIds = successfulRequirementAttachments.value.map(item => item.id)
  if (!attachmentIds.length && !form.requirementSummary.trim()) {
    ElMessage.warning('请填写需求描述或添加可用的图片、音频')
    return false
  }
  mergingRequirement.value = true
  try {
    if (!requirementDraftMerged.value) requirementOriginalText.value = form.requirementSummary
    const originalText = requirementDraftMerged.value ? requirementOriginalText.value : form.requirementSummary
    const response = await api.mergeRequirementDraft(originalText, attachmentIds)
    form.requirementSummary = response.data.data?.draftText || response.data.data?.mergedText || originalText
    requirementDraftMerged.value = true
    requirementMergeStale.value = false
    ElMessage.success('已合并到需求描述，可继续编辑')
    return true
  } catch (error) {
    ElMessage.error(requirementErrorMessage(error, '合并需求描述失败'))
    return false
  } finally {
    mergingRequirement.value = false
  }
}

const resetRequirementAttachments = () => {
  stopRequirementPolling()
  requirementRequestId.value = createRequirementRequestId()
  requirementAttachments.value = []
  Object.keys(attachmentEdits).forEach(key => delete attachmentEdits[key])
  attachmentDirty.clear()
  requirementOriginalText.value = ''
  requirementDraftMerged.value = false
  requirementMergeStale.value = false
}

const createPipeline = async () => {
  if (!form.productLine || !form.projectName || !form.requirementTitle) {
    ElMessage.warning(t('automation.required'))
    return
  }
  if (realtimeAsrActive.value || realtimeAsrConnecting.value) {
    ElMessage.warning('请先停止实时语音输入')
    return
  }
  if (!form.modelId) {
    ElMessage.warning('请选择用于生成 PRD 的模型')
    return
  }
  if (!form.generateFrontend && !form.generateBackend) {
    ElMessage.warning(t('automation.scopeRequired'))
    return
  }
  if (form.autoDeployEnabled && !form.deployProfileId) {
    ElMessage.warning('开启自动部署时请选择部署配置')
    return
  }
  if (form.codeQualityEnabled && !form.codeQualityStandardId) {
    ElMessage.warning('请选择代码质量标准')
    return
  }
  if (form.codeQualityEnabled && !form.qualityModelCode) {
    ElMessage.warning('请选择代码评估模型')
    return
  }
  if (uploadingCount.value || processingRequirementAttachments.value) {
    ElMessage.warning('请等待图片和音频解析完成后再创建流水线')
    return
  }
  if (failedRequirementAttachments.value.length) {
    ElMessage.warning('存在解析失败的附件，请重试或删除后再创建流水线')
    return
  }
  if (requirementAttachments.value.length && requirementMergeStale.value) {
    const merged = await mergeRequirementDraft()
    if (!merged) return
  }
  await api.createAutomationPipeline(form)
  ElMessage.success(t('automation.created'))
  createVisible.value = false
  Object.assign(form, {
    productLine: '',
    projectName: '',
    requirementTitle: '',
    requirementSummary: '',
    initiator: currentUsername(),
    modelId: null,
    aiModelCode: 'default-open-model',
    skillId: null,
    autoDeployEnabled: false,
    deployProfileId: null,
    codeQualityEnabled: false,
    codeQualityStandardId: null,
    qualityModelCode: '',
    templateFile: prdTemplates.value[0]?.fileName || 'default-prd-template.md',
    projectMode: 'scratch',
    codeLevel: 'module',
    generateFrontend: true,
    generateBackend: true,
    frontendOutputPath: 'front/src/generated',
    backendOutputPath: 'backend/src/main/java/com/aipal/generated'
  })
  resetRequirementAttachments()
  await loadAll()
}

const selectPipelineModel = (modelId) => {
  const model = models.value.find(item => item.id === modelId)
  form.aiModelCode = model?.modelCode || 'default-open-model'
  if (!form.qualityModelCode) {
    form.qualityModelCode = model?.modelCode || ''
  }
}

const openTemplateEditor = async () => {
  try {
    if (!prdTemplates.value.length) {
      await loadAll()
    }
    currentTemplateFile.value = form.templateFile || prdTemplates.value[0]?.fileName || 'default-prd-template.md'
    await loadTemplateContent(currentTemplateFile.value)
    templateVisible.value = true
  } catch (error) {
    ElMessage.error(t('automation.templateLoadFailed'))
  }
}

const loadTemplateContent = async (fileName) => {
  if (!fileName) return
  templateLoading.value = true
  try {
    const res = await api.getAutomationPrdTemplate(fileName)
    templateContent.value = res.data.data?.content || ''
    currentTemplateFile.value = res.data.data?.fileName || fileName
    form.templateFile = currentTemplateFile.value
  } catch (error) {
    ElMessage.error(t('automation.templateLoadFailed'))
  } finally {
    templateLoading.value = false
  }
}

const saveTemplate = async () => {
  if (!currentTemplateFile.value) return
  templateLoading.value = true
  try {
    await api.saveAutomationPrdTemplate(currentTemplateFile.value, templateContent.value)
    ElMessage.success(t('automation.templateSaved'))
    await loadAll()
    form.templateFile = currentTemplateFile.value
  } catch (error) {
    ElMessage.error(t('automation.templateSaveFailed'))
  } finally {
    templateLoading.value = false
  }
}

const createTemplate = async () => {
  const rawName = newTemplateName.value.trim()
  if (!rawName) {
    ElMessage.warning(t('automation.templateNameRequired'))
    return
  }
  const fileName = rawName.endsWith('.md') ? rawName : `${rawName}.md`
  templateLoading.value = true
  try {
    await api.saveAutomationPrdTemplate(fileName, '# PRD Template\n\n## Background\n-\n\n## Acceptance Criteria\n-\n')
    newTemplateName.value = ''
    await loadAll()
    await loadTemplateContent(fileName)
    form.templateFile = fileName
    ElMessage.success(t('automation.templateCreated'))
  } catch (error) {
    ElMessage.error(t('automation.templateSaveFailed'))
  } finally {
    templateLoading.value = false
  }
}

const openDetail = async (row, keepDrawer = true) => {
  const res = await api.getAutomationPipeline(row.id)
  Object.assign(detail, res.data.data || { pipeline: null, stages: [], approvals: [] })
  if (detail.pipeline?.id) {
    const [deployRes, buildRes, testRes, qualityRes] = await Promise.all([
      api.getAutomationDeployRuns(detail.pipeline.id),
      api.getAutomationBuildRuns(detail.pipeline.id),
      api.getAutomationTestRuns(detail.pipeline.id),
      api.getAutomationCodeQualityRuns(detail.pipeline.id)
    ])
    deployRuns.value = deployRes.data?.data || []
    buildRuns.value = buildRes.data?.data || []
    testRuns.value = testRes.data?.data || []
    const runs = qualityRes.data?.data || []
    codeQualityRuns.value = await Promise.all(runs.map(async run => {
      try {
        const [issueRes, evidenceRes] = await Promise.all([
          api.getAutomationCodeQualityIssues(run.id),
          api.getAutomationCodeQualityEvidence(run.id)
        ])
        return { ...run, issues: issueRes.data?.data || [], evidence: evidenceRes.data?.data || [] }
      } catch {
        return { ...run, issues: [], evidence: [] }
      }
    }))
  } else {
    deployRuns.value = []
    buildRuns.value = []
    testRuns.value = []
    codeQualityRuns.value = []
  }
  if (keepDrawer) {
    detailVisible.value = true
  }
  syncDetailPolling()
}

const runStage = async (stage) => {
  await api.runAutomationStage(stage.id)
  ElMessage.success(t('automation.stageExecuted'))
  await openDetail(detail.pipeline)
  await loadAll()
}

const rerunQualityEvaluation = async () => {
  if (!qualityStage.value) return
  await runStage(qualityStage.value)
}

const stageRunButtonText = (stage) => {
  return stage?.stageKey === 'code_quality_evaluation' && codeQualityRuns.value.length
    ? '重新评估'
    : t('automation.run')
}

const isPendingApproval = (approval) => approval?.status === 'PENDING'

const isStageRunnable = (stage) => {
  if (!stage || stage.stageKey === 'requirement_analysis' || stage.stageKey === 'code_generation') {
    return false
  }
  const rejectedStage = detail.stages.find(item => item.status === 'REJECTED')
  if (rejectedStage) {
    return rejectedStage.id === stage.id
  }
  if (!['PENDING', 'RUNNING', 'BLOCKED'].includes(stage.status)) {
    return false
  }
  if (detail.pipeline?.currentStage && detail.pipeline.currentStage !== stage.stageKey) {
    return false
  }
  return !detail.stages.some(item => item.stageOrder < stage.stageOrder && item.status !== 'SUCCESS')
}

const syncDetailPolling = () => {
  stopDetailPolling()
  if (!detail.pipeline) return
  const hasRunningStage = detail.stages.some(stage => ['QUEUED', 'RUNNING'].includes(stage.status))
  if (!hasRunningStage) return
  detailPollTimer = window.setInterval(async () => {
    await openDetail(detail.pipeline, false)
    if (codeVisible.value && codeTree.value?.pipelineId) {
      await refreshCodeTree(codeTree.value.pipelineId)
    }
    await loadAll()
  }, 3000)
}

const stopDetailPolling = () => {
  if (detailPollTimer) {
    window.clearInterval(detailPollTimer)
    detailPollTimer = null
  }
}

const approve = async (row, status) => {
  await api.approveAutomation(row.id, { status, reviewedBy: 'admin', comment: status })
  ElMessage.success(status === 'SUCCESS' ? t('automation.approved') : t('automation.rejected'))
  await loadAll()
  if (detail.pipeline) {
    await openDetail(detail.pipeline)
  }
}

const openPrdReview = async (row) => {
  const res = await api.getAutomationApprovalDocument(row.id)
  currentApproval.value = row
  currentPrdStage.value = res.data.data?.stage || null
  prdContent.value = res.data.data?.content || ''
  prdPath.value = res.data.data?.artifactPath || ''
  prdVisible.value = true
}

const regeneratePrd = async () => {
  const pipelineId = currentPrdStage.value?.pipelineId || currentApproval.value?.pipelineId
  if (!pipelineId) return
  await api.regenerateAutomationPrd(pipelineId)
  ElMessage.success('PRD 已重新生成')
  prdVisible.value = false
  await loadAll()
  if (detail.pipeline) {
    await openDetail(detail.pipeline)
  }
}

const submitPrdReview = async (status) => {
  if (!currentApproval.value) return
  let comment = status === 'SUCCESS' ? 'PRD approved' : 'PRD rejected'
  if (status === 'REJECTED') {
    try {
      const result = await ElMessageBox.prompt('Please enter the PRD rejection reason', 'Reject PRD', {
        confirmButtonText: 'Submit',
        cancelButtonText: 'Cancel',
        inputPattern: /\S+/,
        inputErrorMessage: 'Rejection reason is required'
      })
      comment = result.value
    } catch {
      return
    }
  }
  await api.approveAutomation(currentApproval.value.id, {
    status,
    reviewedBy: 'admin',
    comment,
    artifactContent: prdContent.value
  })
  ElMessage.success(status === 'SUCCESS' ? 'PRD 已通过' : 'PRD 已拒绝')
  prdVisible.value = false
  await loadAll()
  if (detail.pipeline) {
    await openDetail(detail.pipeline)
  }
}

const openCodeTree = async (stage) => {
  await refreshCodeTree(stage.pipelineId)
  currentCodeApproval.value = stage.approval || null
  selectedCodeFile.value = ''
  codeContent.value = ''
  codeTruncated.value = false
  codeVisible.value = true
}

const refreshCodeTree = async (pipelineId) => {
  const res = await api.getAutomationCodeTree(pipelineId)
  codeTree.value = res.data.data || {}
  codeFiles.value = codeTree.value.files || []
  codeFeedbacks.value = codeTree.value.feedbacks || []
}

const selectCodeFile = async (file) => {
  selectedCodeFile.value = file.path
  codeLoading.value = true
  try {
    const res = await api.getAutomationCodeFile(codeTree.value.pipelineId, file.path)
    codeContent.value = res.data.data?.content || ''
    codeTruncated.value = !!res.data.data?.truncated
  } finally {
    codeLoading.value = false
  }
}

const regenerateCode = async () => {
  const pipelineId = codeTree.value?.pipelineId || detail.pipeline?.id
  if (!pipelineId) return
  await api.regenerateAutomationCode(pipelineId)
  ElMessage.success(t('automation.codeRegenerating'))
  await refreshCodeTree(pipelineId)
  if (detail.pipeline) {
    await openDetail(detail.pipeline)
  }
}

const submitCodeReview = async (status) => {
  if (!currentCodeApproval.value) return
  let comment = status === 'SUCCESS' ? 'Code approved' : 'Code rejected'
  if (status === 'REJECTED') {
    try {
      const result = await ElMessageBox.prompt('Please enter the code rejection reason', 'Reject Code', {
        confirmButtonText: 'Submit',
        cancelButtonText: 'Cancel',
        inputPattern: /\S+/,
        inputErrorMessage: 'Rejection reason is required'
      })
      comment = result.value
    } catch {
      return
    }
  }
  await api.approveAutomation(currentCodeApproval.value.id, {
    status,
    reviewedBy: 'admin',
    comment
  })
  ElMessage.success(status === 'SUCCESS' ? t('automation.codeApproved') : t('automation.codeRejected'))
  codeVisible.value = false
  currentCodeApproval.value = null
  await loadAll()
  if (detail.pipeline) {
    await openDetail(detail.pipeline)
  }
}

const submitCodeFeedback = async () => {
  const pipelineId = codeTree.value?.pipelineId || detail.pipeline?.id
  if (!pipelineId || !codeTree.value?.batchId) return
  if (codeFeedbackForm.alignmentStatus === 'FAILED' && !codeFeedbackForm.failureReason.trim()) {
    ElMessage.warning('请填写不合格原因')
    return
  }
  feedbackSubmitting.value = true
  try {
    await api.submitAutomationCodeFeedback(pipelineId, {
      batchId: codeTree.value.batchId,
      alignmentStatus: codeFeedbackForm.alignmentStatus,
      alignmentScore: codeFeedbackForm.alignmentScore,
      summary: codeFeedbackForm.summary,
      failureReason: codeFeedbackForm.failureReason,
      reviewedBy: currentUsername()
    })
    ElMessage.success('反馈已提交')
    codeFeedbackForm.summary = ''
    codeFeedbackForm.failureReason = ''
    await refreshCodeTree(pipelineId)
    if (detail.pipeline) {
      await openDetail(detail.pipeline)
    }
  } finally {
    feedbackSubmitting.value = false
  }
}

const feedbackSourceLabel = (source) => {
  if (source === 'AI') return 'AI评价'
  if (source === 'MANUAL') return '人工反馈'
  return source || '-'
}

const feedbackStatusText = (status) => {
  const map = {
    PASSED: '合格',
    PARTIAL: '部分符合',
    FAILED: '不合格',
    ERROR: '评价失败'
  }
  return map[status] || status || '-'
}

const feedbackStatusType = (status) => {
  if (status === 'PASSED') return 'success'
  if (status === 'FAILED' || status === 'ERROR') return 'danger'
  if (status === 'PARTIAL') return 'warning'
  return 'info'
}

const formatBytes = (value) => {
  const size = Number(value || 0)
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

const statusType = (status) => {
  if (status === 'SUCCESS' || status === 'COMPLETED') return 'success'
  if (status === 'REJECTED' || status === 'BLOCKED') return 'danger'
  if (status === 'WAITING_APPROVAL') return 'warning'
  return 'info'
}

const statusText = (status) => t(`status.${status}`)

watch(detailVisible, visible => {
  if (!visible) stopDetailPolling()
})

watch(createVisible, visible => {
  if (visible) {
    if (processingRequirementAttachments.value) startRequirementPolling()
    return
  }
  stopRequirementPolling()
  if (realtimeAsrActive.value || realtimeAsrConnecting.value) {
    stopRealtimeAsr()
  }
  if (recording.value && mediaRecorder) {
    discardRecording = true
    mediaRecorder.stop()
  }
})

onMounted(loadAll)
onUnmounted(() => {
  stopDetailPolling()
  stopRequirementPolling()
  if (recording.value && mediaRecorder) {
    discardRecording = true
    mediaRecorder.stop()
  } else {
    releaseMicrophone()
  }
  stopRealtimeAsr()
})
</script>

<style scoped>
.automation-page { color: var(--text-primary); }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px; }
.head-actions { display: flex; gap: 10px; align-items: center; }
.page-head h2 { font-size: 24px; margin-bottom: 6px; }
.page-head p { color: var(--text-muted); }
.inline-field { display: flex; gap: 10px; width: 100%; }
.requirement-input { width: 100%; }
.requirement-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
.hidden-file-input { display: none; }
.requirement-hint { color: var(--text-muted); font-size: 12px; }
.realtime-asr-panel { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-top: 8px; padding: 8px 10px; border: 1px solid #bfdbfe; border-radius: 8px; background: #eff6ff; color: #1d4ed8; font-size: 12px; }
.realtime-asr-panel strong { color: #111827; font-weight: 600; word-break: break-word; }
.realtime-asr-dot { width: 8px; height: 8px; border-radius: 50%; background: #94a3b8; flex-shrink: 0; }
.realtime-asr-dot.active { background: #22c55e; box-shadow: 0 0 0 4px rgba(34, 197, 94, .15); }
.requirement-attachments { display: flex; flex-direction: column; gap: 10px; margin-top: 12px; }
.requirement-attachment { border: 1px solid var(--border-color); border-radius: 8px; padding: 10px; background: #f8fafc; }
.attachment-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 10px; margin-bottom: 8px; }
.attachment-head div { min-width: 0; }
.attachment-head strong, .attachment-head span { display: block; word-break: break-all; }
.attachment-head strong { color: #111827; font-size: 13px; }
.attachment-head span { color: var(--text-muted); font-size: 12px; margin-top: 3px; }
.attachment-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }
.attachment-error { color: #b91c1c; font-size: 12px; margin: 6px 0; }
.requirement-merge-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; color: var(--text-muted); font-size: 12px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.template-toolbar { display: grid; grid-template-columns: 1fr 180px auto; gap: 10px; margin-bottom: 12px; }
.template-meta { color: var(--text-muted); font-size: 12px; margin-bottom: 10px; word-break: break-all; }
.drawer-footer { display: flex; justify-content: flex-end; gap: 10px; margin-top: 14px; }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(150px, 1fr)); gap: 14px; margin-bottom: 16px; }
.summary-card, .panel { background: #ffffff; border: 1px solid var(--border-color); border-radius: 8px; }
.summary-card { padding: 16px; display: flex; flex-direction: column; gap: 8px; }
.summary-card span { color: var(--text-muted); font-size: 13px; }
.summary-card strong { font-size: 28px; color: #111827; }
.panel { padding: 16px; margin-bottom: 16px; }
.panel-title { font-weight: 700; margin-bottom: 12px; }
.approvals-panel { margin-top: 16px; }
.detail h3 { margin-bottom: 8px; }
.detail p { color: var(--text-muted); margin-bottom: 16px; }
.detail-overview { border: 1px solid var(--border-color); border-radius: 8px; padding: 12px; margin-bottom: 14px; background: #f8fafc; }
.detail-overview-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; margin-bottom: 8px; }
.detail-overview h4 { margin: 0; font-size: 15px; }
.detail-overview p { margin: 0 0 10px; line-height: 1.6; color: #475569; }
.overview-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.overview-grid div { border: 1px solid #e5e7eb; border-radius: 6px; padding: 8px; background: #ffffff; }
.overview-grid span { display: block; color: var(--text-muted); font-size: 12px; margin-bottom: 4px; }
.overview-grid strong { color: #111827; font-size: 13px; word-break: break-word; }
.deploy-badge { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; color: var(--text-muted); font-size: 13px; }
.stage-list { display: flex; flex-direction: column; gap: 10px; }
.stage-item { display: flex; justify-content: space-between; gap: 12px; border: 1px solid var(--border-color); border-radius: 8px; padding: 12px; }
.stage-item span { display: block; color: var(--text-muted); margin-top: 5px; line-height: 1.5; }
.stage-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.deploy-runs { margin-top: 18px; display: flex; flex-direction: column; gap: 10px; }
.deploy-runs h4 { margin: 0; font-size: 15px; }
.deploy-run { border: 1px solid var(--border-color); border-radius: 8px; padding: 10px; background: #f8fafc; }
.deploy-run-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; margin-bottom: 8px; }
.deploy-run-meta { display: flex; flex-direction: column; gap: 4px; color: var(--text-muted); font-size: 12px; margin-bottom: 8px; word-break: break-all; }
.deploy-run pre { max-height: 180px; overflow: auto; white-space: pre-wrap; word-break: break-word; margin: 0; font-size: 12px; line-height: 1.5; color: #111827; }
.quality-runs { margin-top: 18px; display: flex; flex-direction: column; gap: 10px; }
.quality-title { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.quality-runs h4 { margin: 0; font-size: 15px; }
.quality-run { border: 1px solid var(--border-color); border-radius: 8px; padding: 10px; background: #f8fafc; }
.quality-run-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; margin-bottom: 8px; }
.quality-metrics { display: flex; flex-direction: column; gap: 4px; color: var(--text-muted); font-size: 12px; margin-bottom: 8px; }
.quality-run p { margin: 0; color: #374151; line-height: 1.5; }
.quality-score-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin: 10px 0; }
.quality-score-item { border: 1px solid #e5e7eb; border-radius: 6px; padding: 8px; background: #ffffff; }
.quality-score-item div { display: flex; justify-content: space-between; gap: 8px; margin-bottom: 6px; color: var(--text-muted); font-size: 12px; }
.quality-score-item strong { color: #111827; }
.quality-subtitle { font-weight: 700; font-size: 13px; color: #111827; margin-bottom: 8px; }
.quality-evidence { display: flex; flex-direction: column; gap: 8px; margin-top: 10px; }
.quality-evidence-item { border: 1px solid #dbeafe; border-radius: 6px; padding: 8px; background: #f8fbff; }
.quality-evidence-item div { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.quality-evidence-item strong { color: #111827; font-size: 13px; }
.quality-evidence-item span, .quality-evidence-item small { display: block; color: var(--text-muted); font-size: 12px; line-height: 1.5; word-break: break-word; }
.quality-evidence-item small { margin-top: 4px; font-family: Consolas, Monaco, monospace; }
.quality-issues { display: flex; flex-direction: column; gap: 8px; margin-top: 10px; }
.quality-issue { display: grid; grid-template-columns: auto 1fr; gap: 8px; border: 1px solid #fee2e2; border-radius: 6px; padding: 8px; background: #fff7f7; }
.quality-issue div { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.quality-issue strong { color: #111827; font-size: 13px; }
.quality-issue span, .quality-issue small { color: var(--text-muted); font-size: 12px; word-break: break-all; }
.quality-issue p { margin: 0; color: #374151; font-size: 12px; }
.prd-meta { color: var(--text-muted); margin-bottom: 12px; font-size: 13px; word-break: break-all; }
.code-meta { display: flex; flex-direction: column; gap: 6px; color: var(--text-muted); font-size: 13px; margin-bottom: 12px; word-break: break-all; }
.code-feedback-panel { border: 1px solid var(--border-color); border-radius: 8px; padding: 12px; margin-bottom: 12px; background: #f8fafc; display: flex; flex-direction: column; gap: 10px; }
.failure-reasons p { margin: 4px 0 0; }
.feedback-list { display: grid; gap: 8px; }
.feedback-item { background: #fff; border: 1px solid var(--border-color); border-radius: 6px; padding: 9px 10px; }
.feedback-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.feedback-item p { margin: 6px 0; color: #374151; }
.feedback-item small { color: #b45309; }
.manual-feedback-form { display: grid; grid-template-columns: auto auto minmax(180px, 1fr) minmax(180px, 1fr) auto; gap: 8px; align-items: center; }
.code-generating { min-height: 220px; display: flex; align-items: center; justify-content: center; gap: 10px; color: var(--text-muted); border: 1px dashed var(--border-color); border-radius: 8px; }
.code-layout { display: grid; grid-template-columns: 290px 1fr; gap: 14px; min-height: 520px; }
.code-files { border: 1px solid var(--border-color); border-radius: 8px; padding: 8px; overflow: auto; background: #f8fafc; }
.code-file { width: 100%; border: 0; border-radius: 6px; background: transparent; padding: 9px 10px; text-align: left; cursor: pointer; display: flex; flex-direction: column; gap: 4px; color: #111827; }
.code-file:hover, .code-file.active { background: #eaf1ff; }
.code-file span { font-size: 13px; line-height: 1.35; word-break: break-all; }
.code-file small { color: var(--text-muted); }
.empty-code { color: var(--text-muted); padding: 18px 10px; text-align: center; }
.code-preview { border: 1px solid var(--border-color); border-radius: 8px; background: #0f172a; color: #e5e7eb; overflow: hidden; display: flex; flex-direction: column; }
.code-preview-head { min-height: 42px; padding: 10px 12px; border-bottom: 1px solid rgba(255,255,255,.1); display: flex; align-items: center; justify-content: space-between; gap: 12px; color: #f8fafc; }
.code-preview pre { margin: 0; padding: 14px; overflow: auto; flex: 1; white-space: pre-wrap; word-break: break-word; font-size: 12px; line-height: 1.6; }
@media (max-width: 1100px) {
  .summary-grid { grid-template-columns: repeat(2, 1fr); }
  .code-layout { grid-template-columns: 1fr; }
  .code-files { max-height: 220px; }
  .manual-feedback-form { grid-template-columns: 1fr; }
  .form-grid, .template-toolbar { grid-template-columns: 1fr; }
  .inline-field { flex-direction: column; }
  .requirement-merge-row { align-items: flex-start; flex-direction: column; }
}
</style>
