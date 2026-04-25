<template>
  <div class="benchmark-editor">
    <div class="section-header">
      <h3 class="section-title">测评标准编辑</h3>
      <p class="section-desc">配置测评指标和权重，支持预设标准和自定义规则</p>
    </div>

    <!-- Preset Standards -->
    <div class="standards-section">
      <h4 class="sub-title">预设标准</h4>
      <div class="preset-grid">
        <div
          v-for="preset in presetStandards"
          :key="preset.id"
          class="preset-card"
          :class="{ active: selectedPreset?.id === preset.id }"
          @click="selectPreset(preset)"
        >
          <div class="preset-icon">{{ preset.icon }}</div>
          <div class="preset-info">
            <span class="preset-name">{{ preset.name }}</span>
            <span class="preset-desc">{{ preset.description }}</span>
          </div>
          <div v-if="selectedPreset?.id === preset.id" class="preset-check">
            <el-icon><Check /></el-icon>
          </div>
        </div>
      </div>
    </div>

    <!-- Custom Standards -->
    <div class="custom-section">
      <h4 class="sub-title">
        自定义标准
        <el-button size="small" @click="showAddDialog = true" class="add-btn">
          <el-icon><Plus /></el-icon> 添加标准
        </el-button>
      </h4>

      <div class="standards-table">
        <el-table :data="standards" stripe>
          <el-table-column prop="name" label="标准名称" min-width="160">
            <template #default="{ row }">
              <span class="standard-name">{{ row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="category" label="类别" width="120">
            <template #default="{ row }">
              <span class="category-tag">{{ row.category }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="weight" label="权重" width="120" align="center">
            <template #default="{ row }">
              <div class="weight-config">
                <el-input-number
                  v-model="row.weight"
                  :min="0"
                  :max="100"
                  size="small"
                  @change="updateWeight(row)"
                />
                <span class="weight-unit">%</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="threshold" label="阈值" width="120" align="center">
            <template #default="{ row }">
              <span class="mono">{{ row.threshold || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" align="center">
            <template #default="{ row, $index }">
              <el-button size="small" text type="danger" @click="removeStandard($index)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- Weight Summary -->
      <div class="weight-summary">
        <span class="summary-label">权重总计:</span>
        <span class="summary-value mono" :class="{ error: totalWeight !== 100 }">{{ totalWeight }}%</span>
        <span v-if="totalWeight !== 100" class="weight-warning">
          权重之和必须等于100%
        </span>
      </div>
    </div>

    <!-- Rule Editor -->
    <div class="rules-section">
      <h4 class="sub-title">评分规则配置</h4>

      <div class="rules-editor">
        <div v-for="(rule, index) in scoringRules" :key="index" class="rule-item">
          <div class="rule-header">
            <span class="rule-title">{{ rule.name }}</span>
            <el-tag :type="getRuleType(rule.type)" size="small">{{ rule.type }}</el-tag>
          </div>
          <div class="rule-content">
            <div v-if="rule.type === 'range'" class="rule-config">
              <span class="config-label">范围:</span>
              <el-input-number v-model="rule.min" size="small" placeholder="最小" />
              <span>至</span>
              <el-input-number v-model="rule.max" size="small" placeholder="最大" />
              <span class="config-label" style="margin-left: 16px;">分值:</span>
              <el-input-number v-model="rule.score" size="small" :min="0" :max="100" />
            </div>
            <div v-else-if="rule.type === 'contains'" class="rule-config">
              <span class="config-label">关键词:</span>
              <el-input v-model="rule.keyword" size="small" placeholder="关键词" />
              <span class="config-label" style="margin-left: 16px;">分值:</span>
              <el-input-number v-model="rule.score" size="small" :min="0" :max="100" />
            </div>
            <div v-else-if="rule.type === 'regex'" class="rule-config">
              <span class="config-label">正则:</span>
              <el-input v-model="rule.pattern" size="small" placeholder="正则表达式" style="width: 200px;" />
              <span class="config-label" style="margin-left: 16px;">分值:</span>
              <el-input-number v-model="rule.score" size="small" :min="0" :max="100" />
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Add Standard Dialog -->
    <el-dialog v-model="showAddDialog" title="添加自定义标准" width="500px" class="add-dialog">
      <el-form :model="newStandard" label-position="top" class="standard-form">
        <el-form-item label="标准名称" required>
          <el-input v-model="newStandard.name" placeholder="如: 响应时间评分" />
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="newStandard.category" placeholder="选择类别" style="width: 100%">
            <el-option label="性能" value="性能" />
            <el-option label="准确性" value="准确性" />
            <el-option label="稳定性" value="稳定性" />
            <el-option label="用户体验" value="用户体验" />
            <el-option label="业务指标" value="业务指标" />
          </el-select>
        </el-form-item>
        <el-form-item label="权重 (%)">
          <el-input-number v-model="newStandard.weight" :min="0" :max="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="阈值">
          <el-input v-model="newStandard.threshold" placeholder="如: >= 90%" />
        </el-form-item>
        <el-form-item label="评分规则类型">
          <el-select v-model="newStandard.ruleType" placeholder="选择规则类型" style="width: 100%">
            <el-option label="范围评分" value="range" />
            <el-option label="关键词匹配" value="contains" />
            <el-option label="正则匹配" value="regex" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="addStandard">添加</el-button>
      </template>
    </el-dialog>

    <!-- Actions -->
    <div class="editor-actions">
      <el-button @click="handleBack" class="back-btn">上一步</el-button>
      <el-button @click="resetConfig" class="reset-btn">重置</el-button>
      <el-button
        type="primary"
        :disabled="!canProceed"
        @click="handleProceed"
        class="proceed-btn"
      >
        保存并下一步
        <el-icon><ArrowRight /></el-icon>
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Plus, Delete, ArrowRight } from '@element-plus/icons-vue'
import api from '../../api'

const emit = defineEmits(['back', 'next'])

const presetStandards = [
  {
    id: 1,
    name: '通用AI能力评测',
    icon: '🤖',
    description: '涵盖准确性、响应速度、稳定性等基础指标',
    standards: [
      { name: '准确性', category: '准确性', weight: 35, threshold: '>= 85%' },
      { name: '响应时间', category: '性能', weight: 25, threshold: '< 2s' },
      { name: '稳定性', category: '稳定性', weight: 20, threshold: '>= 95%' },
      { name: '用户体验', category: '用户体验', weight: 20, threshold: '>= 80%' }
    ]
  },
  {
    id: 2,
    name: '对话系统评测',
    icon: '💬',
    description: '专注对话理解、生成质量、上下文连贯性',
    standards: [
      { name: '意图识别准确率', category: '准确性', weight: 30, threshold: '>= 90%' },
      { name: '回复相关性', category: '准确性', weight: 25, threshold: '>= 85%' },
      { name: '对话连贯性', category: '用户体验', weight: 25, threshold: '>= 80%' },
      { name: '平均响应时间', category: '性能', weight: 20, threshold: '< 1.5s' }
    ]
  },
  {
    id: 3,
    name: '代码生成评测',
    icon: '👨‍💻',
    description: '评估代码正确性、可读性、执行效率',
    standards: [
      { name: '代码正确性', category: '准确性', weight: 40, threshold: '>= 90%' },
      { name: '执行效率', category: '性能', weight: 25, threshold: 'O(n)以内' },
      { name: '代码可读性', category: '业务指标', weight: 20, threshold: '>= 85%' },
      { name: '异常处理', category: '稳定性', weight: 15, threshold: '完整' }
    ]
  }
]

const selectedPreset = ref(null)
const showAddDialog = ref(false)
const standards = ref([])
const scoringRules = ref([
  { name: '响应时间评分', type: 'range', min: 0, max: 2000, score: 100 },
  { name: '关键词匹配', type: 'contains', keyword: '', score: 50 },
  { name: '格式验证', type: 'regex', pattern: '', score: 30 }
])

const newStandard = ref({
  name: '',
  category: '',
  weight: 10,
  threshold: '',
  ruleType: 'range'
})

const totalWeight = computed(() => {
  return standards.value.reduce((sum, s) => sum + (s.weight || 0), 0)
})

const canProceed = computed(() => {
  return standards.value.length > 0 && totalWeight.value === 100
})

const selectPreset = (preset) => {
  selectedPreset.value = preset
  standards.value = preset.standards.map(s => ({ ...s }))
}

const addStandard = () => {
  if (!newStandard.value.name) {
    ElMessage.warning('请输入标准名称')
    return
  }

  const rule = {
    name: newStandard.value.name,
    category: newStandard.value.category || '业务指标',
    weight: newStandard.value.weight || 10,
    threshold: newStandard.value.threshold,
    ruleType: newStandard.value.ruleType,
    min: 0,
    max: 100,
    keyword: '',
    pattern: '',
    score: 100
  }

  standards.value.push(rule)
  scoringRules.value.push({
    name: newStandard.value.name,
    type: newStandard.value.ruleType,
    ...(newStandard.value.ruleType === 'range' ? { min: 0, max: 100, score: 100 } : {}),
    ...(newStandard.value.ruleType === 'contains' ? { keyword: '', score: 50 } : {}),
    ...(newStandard.value.ruleType === 'regex' ? { pattern: '', score: 30 } : {})
  })

  showAddDialog.value = false
  newStandard.value = {
    name: '',
    category: '',
    weight: 10,
    threshold: '',
    ruleType: 'range'
  }
}

const removeStandard = (index) => {
  standards.value.splice(index, 1)
}

const updateWeight = (row) => {
}

const getRuleType = (type) => {
  const map = { range: 'success', contains: 'warning', regex: 'info' }
  return map[type] || 'info'
}

const resetConfig = () => {
  selectedPreset.value = null
  standards.value = []
}

const handleBack = () => {
  emit('back')
}

const handleProceed = async () => {
  if (!canProceed.value) {
    ElMessage.warning('请确保权重总和为100%')
    return
  }

  try {
    const res = await api.saveBenchmarkStandards({
      standards: standards.value,
      rules: scoringRules.value
    })

    if (res.data.code === 200) {
      ElMessage.success('测评标准已保存')
      emit('next', { standards: standards.value, rules: scoringRules.value })
    } else {
      ElMessage.error(res.data.message || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存测评标准失败')
  }
}
</script>

<style scoped>
.benchmark-editor {
  max-width: 900px;
}

.section-header {
  margin-bottom: 24px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.section-desc {
  font-size: 14px;
  color: var(--text-muted);
}

.sub-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 12px;
}

.add-btn {
  padding: 4px 12px;
  font-size: 12px;
}

.preset-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}

.preset-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: var(--glass-bg);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.preset-card:hover {
  border-color: var(--neon-cyan);
  background: rgba(0, 212, 255, 0.05);
}

.preset-card.active {
  border-color: var(--neon-cyan);
  background: rgba(0, 212, 255, 0.1);
}

.preset-icon {
  font-size: 28px;
}

.preset-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.preset-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.preset-desc {
  font-size: 12px;
  color: var(--text-muted);
}

.preset-check {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--neon-cyan);
  color: #000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.custom-section {
  margin-top: 32px;
}

.standards-table {
  border-radius: 12px;
  overflow: hidden;
}

.standard-name {
  font-weight: 600;
  color: var(--text-primary);
}

.category-tag {
  display: inline-block;
  padding: 4px 10px;
  background: rgba(139, 92, 246, 0.15);
  border: 1px solid rgba(139, 92, 246, 0.3);
  border-radius: 6px;
  font-size: 12px;
  color: var(--neon-purple);
}

.weight-config {
  display: flex;
  align-items: center;
  gap: 4px;
}

.weight-unit {
  color: var(--text-muted);
  font-size: 12px;
}

.weight-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  padding: 12px 16px;
  background: var(--glass-bg);
  border-radius: 8px;
}

.summary-label {
  font-size: 14px;
  color: var(--text-secondary);
}

.summary-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--neon-cyan);
}

.summary-value.error {
  color: var(--neon-pink);
}

.weight-warning {
  font-size: 12px;
  color: var(--neon-pink);
}

.rules-section {
  margin-top: 32px;
}

.rules-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rule-item {
  padding: 16px;
  background: var(--glass-bg);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.rule-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.rule-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.rule-content {
  padding-left: 12px;
}

.rule-config {
  display: flex;
  align-items: center;
  gap: 12px;
}

.config-label {
  font-size: 12px;
  color: var(--text-muted);
  width: 50px;
}

.standard-form {
  padding: 8px 0;
}

.editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid var(--border-color);
}

.back-btn,
.reset-btn {
  padding: 12px 24px;
  border: 1px solid var(--border-color);
  background: transparent;
  color: var(--text-secondary);
  border-radius: 10px;
}

.proceed-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 32px;
  background: linear-gradient(135deg, var(--neon-cyan), var(--neon-purple));
  border: none;
  color: #000;
  font-weight: 600;
  border-radius: 10px;
}

.proceed-btn:disabled {
  opacity: 0.5;
}
</style>