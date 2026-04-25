<template>
  <div class="sim-data-generator">
    <div class="section-header">
      <h3 class="section-title">模拟数据生成</h3>
      <p class="section-desc">选择模板并配置字段规则，生成模拟测试数据</p>
    </div>

    <!-- Template Selection -->
    <div class="template-section">
      <h4 class="sub-title">选择模板</h4>
      <div class="template-grid">
        <div
          v-for="template in templates"
          :key="template.id"
          class="template-card"
          :class="{ active: selectedTemplate?.id === template.id }"
          @click="selectTemplate(template)"
        >
          <div class="template-icon">{{ template.icon }}</div>
          <div class="template-info">
            <span class="template-name">{{ template.name }}</span>
            <span class="template-desc">{{ template.description }}</span>
          </div>
          <div v-if="selectedTemplate?.id === template.id" class="template-check">
            <el-icon><Check /></el-icon>
          </div>
        </div>
      </div>
    </div>

    <!-- Field Rules Configuration -->
    <div v-if="selectedTemplate" class="rules-section">
      <h4 class="sub-title">字段规则配置</h4>

      <div class="rules-list">
        <div v-for="(rule, index) in fieldRules" :key="index" class="rule-item">
          <div class="rule-header">
            <el-input v-model="rule.fieldName" placeholder="字段名" class="field-name-input" />
            <el-select v-model="rule.fieldType" placeholder="类型" class="field-type-select">
              <el-option label="字符串" value="string" />
              <el-option label="整数" value="integer" />
              <el-option label="浮点数" value="float" />
              <el-option label="布尔值" value="boolean" />
              <el-option label="日期" value="date" />
              <el-option label="邮箱" value="email" />
              <el-option label="手机号" value="phone" />
              <el-option label="UUID" value="uuid" />
            </el-select>
            <el-button text type="danger" @click="removeRule(index)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>

          <div class="rule-config">
            <div class="rule-row">
              <span class="rule-label">生成规则</span>
              <el-select v-model="rule.generator" placeholder="生成规则" style="width: 300px">
                <el-option label="固定值" value="fixed" />
                <el-option label="随机范围" value="range" />
                <el-option label="正则匹配" value="pattern" />
                <el-option label="从列表选择" value="choice" />
                <el-option label="递增序列" value="sequence" />
                <el-option label="UUID" value="uuid" />
              </el-select>
            </div>

            <div v-if="rule.generator === 'fixed'" class="rule-row">
              <span class="rule-label">固定值</span>
              <el-input v-model="rule.fixedValue" placeholder="输入固定值" style="width: 300px" />
            </div>

            <div v-if="rule.generator === 'range'" class="rule-row">
              <span class="rule-label">范围</span>
              <el-input v-model="rule.min" placeholder="最小值" type="number" style="width: 140px" />
              <span class="range-separator">至</span>
              <el-input v-model="rule.max" placeholder="最大值" type="number" style="width: 140px" />
            </div>

            <div v-if="rule.generator === 'pattern'" class="rule-row">
              <span class="rule-label">正则表达式</span>
              <el-input v-model="rule.pattern" placeholder="如: [A-Z]{3}[0-9]{4}" style="width: 300px" />
            </div>

            <div v-if="rule.generator === 'choice'" class="rule-row">
              <span class="rule-label">选项列表</span>
              <el-input
                v-model="rule.choices"
                placeholder="用逗号分隔，如: 甲,乙,丙,丁"
                style="width: 300px"
              />
            </div>

            <div v-if="rule.generator === 'sequence'" class="rule-row">
              <span class="rule-label">起始值</span>
              <el-input v-model="rule.startValue" placeholder="起始值" type="number" style="width: 140px" />
              <span class="rule-label" style="margin-left: 24px;">步长</span>
              <el-input v-model="rule.step" placeholder="步长" type="number" style="width: 140px" />
            </div>
          </div>
        </div>
      </div>

      <el-button @click="addRule" class="add-rule-btn">
        <el-icon><Plus /></el-icon> 添加字段规则
      </el-button>
    </div>

    <!-- Generation Settings -->
    <div v-if="selectedTemplate" class="generation-section">
      <h4 class="sub-title">生成数量</h4>
      <div class="count-setting">
        <el-input-number v-model="generateCount" :min="1" :max="10000" />
        <span class="count-unit">条记录</span>
      </div>
    </div>

    <!-- Actions -->
    <div class="generator-actions">
      <el-button @click="handleBack" class="back-btn">上一步</el-button>
      <el-button @click="resetConfig" class="reset-btn">重置</el-button>
      <el-button
        type="primary"
        :disabled="!canGenerate"
        :loading="generating"
        @click="handleGenerate"
        class="generate-btn"
      >
        生成数据
      </el-button>
      <el-button
        type="primary"
        :disabled="!canProceed"
        @click="handleProceed"
        class="proceed-btn"
      >
        下一步
        <el-icon><ArrowRight /></el-icon>
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Delete, Plus, ArrowRight } from '@element-plus/icons-vue'
import api from '../../api'

const emit = defineEmits(['back', 'next'])

const templates = [
  { id: 1, name: '用户信息', icon: '👤', description: '生成用户基本信息，包含姓名、邮箱、手机等' },
  { id: 2, name: '订单记录', icon: '📦', description: '生成电商订单数据，包含订单号、商品、金额等' },
  { id: 3, name: '日志数据', icon: '📋', description: '生成系统日志，包含时间、级别、内容等' },
  { id: 4, name: '评论数据', icon: '💬', description: '生成用户评论，包含评分、内容、时间等' },
  { id: 5, name: '自定义', icon: '⚙️', description: '从空白模板开始自定义字段规则' }
]

const selectedTemplate = ref(null)
const fieldRules = ref([])
const generateCount = ref(100)
const generating = ref(false)

const canGenerate = computed(() => {
  return selectedTemplate.value && fieldRules.value.length > 0
})

const canProceed = computed(() => {
  return generatedData.value && generatedData.value.length > 0
})

const generatedData = ref(null)

const selectTemplate = (template) => {
  selectedTemplate.value = template

  if (template.id === 5) {
    fieldRules.value = []
  } else {
    initTemplateRules(template.id)
  }
}

const initTemplateRules = (templateId) => {
  const templateRules = {
    1: [
      { fieldName: 'user_id', fieldType: 'string', generator: 'sequence', startValue: 10001, step: 1 },
      { fieldName: 'username', fieldType: 'string', generator: 'pattern', pattern: 'user_[a-z]{6}' },
      { fieldName: 'email', fieldType: 'email', generator: 'pattern', pattern: '[a-z]{6}@example.com' },
      { fieldName: 'phone', fieldType: 'phone', generator: 'fixed', fixedValue: '13800138000' },
      { fieldName: 'age', fieldType: 'integer', generator: 'range', min: 18, max: 80 }
    ],
    2: [
      { fieldName: 'order_id', fieldType: 'string', generator: 'sequence', startValue: 1, step: 1 },
      { fieldName: 'product_name', fieldType: 'string', generator: 'choice', choices: 'iPhone,MacBook,iPad,AirPods,Apple Watch' },
      { fieldName: 'price', fieldType: 'float', generator: 'range', min: 99.99, max: 9999.99 },
      { fieldName: 'quantity', fieldType: 'integer', generator: 'range', min: 1, max: 10 },
      { fieldName: 'status', fieldType: 'string', generator: 'choice', choices: 'pending,paid,shipped,completed,cancelled' }
    ],
    3: [
      { fieldName: 'timestamp', fieldType: 'date', generator: 'sequence', startValue: 1, step: 1 },
      { fieldName: 'level', fieldType: 'string', generator: 'choice', choices: 'DEBUG,INFO,WARN,ERROR' },
      { fieldName: 'message', fieldType: 'string', generator: 'pattern', pattern: 'Operation completed at [0-9]{10}' },
      { fieldName: 'source', fieldType: 'string', generator: 'choice', choices: 'server-a,server-b,server-c' }
    ],
    4: [
      { fieldName: 'comment_id', fieldType: 'string', generator: 'sequence', startValue: 1, step: 1 },
      { fieldName: 'user_id', fieldType: 'string', generator: 'sequence', startValue: 1001, step: 1 },
      { fieldName: 'rating', fieldType: 'integer', generator: 'range', min: 1, max: 5 },
      { fieldName: 'content', fieldType: 'string', generator: 'pattern', pattern: 'This is a sample comment number [0-9]{6}' },
      { fieldName: 'create_time', fieldType: 'date', generator: 'sequence', startValue: 1, step: 1 }
    ]
  }

  fieldRules.value = (templateRules[templateId] || []).map(rule => ({ ...rule }))
}

const addRule = () => {
  fieldRules.value.push({
    fieldName: '',
    fieldType: 'string',
    generator: 'fixed',
    fixedValue: '',
    min: null,
    max: null,
    pattern: '',
    choices: '',
    startValue: 1,
    step: 1
  })
}

const removeRule = (index) => {
  fieldRules.value.splice(index, 1)
}

const handleGenerate = async () => {
  if (!canGenerate.value) {
    ElMessage.warning('请选择模板并配置字段规则')
    return
  }

  generating.value = true

  try {
    const res = await api.generateSimData({
      templateId: selectedTemplate.value.id,
      count: generateCount.value,
      rules: fieldRules.value
    })

    if (res.data.code === 200) {
      generatedData.value = res.data.data
      ElMessage.success(`成功生成 ${generateCount.value} 条模拟数据`)
    } else {
      ElMessage.error(res.data.message || '生成失败')
    }
  } catch (e) {
    ElMessage.error('生成模拟数据失败')
  } finally {
    generating.value = false
  }
}

const resetConfig = () => {
  selectedTemplate.value = null
  fieldRules.value = []
  generateCount.value = 100
  generatedData.value = null
}

const handleBack = () => {
  emit('back')
}

const handleProceed = () => {
  if (generatedData.value) {
    emit('next', { simData: generatedData.value, rules: fieldRules.value })
  } else {
    ElMessage.warning('请先生成模拟数据')
  }
}
</script>

<style scoped>
.sim-data-generator {
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
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 12px;
}

.template-section {
  margin-bottom: 32px;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.template-card {
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

.template-card:hover {
  border-color: var(--neon-cyan);
  background: rgba(0, 212, 255, 0.05);
}

.template-card.active {
  border-color: var(--neon-cyan);
  background: rgba(0, 212, 255, 0.1);
}

.template-icon {
  font-size: 28px;
}

.template-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.template-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.template-desc {
  font-size: 12px;
  color: var(--text-muted);
}

.template-check {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--neon-cyan);
  color: #000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.rules-section {
  margin-bottom: 32px;
}

.rules-list {
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

.field-name-input {
  width: 180px;
}

.field-type-select {
  width: 120px;
}

.rule-config {
  padding-left: 12px;
}

.rule-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.rule-label {
  width: 80px;
  font-size: 12px;
  color: var(--text-muted);
  flex-shrink: 0;
}

.range-separator {
  color: var(--text-muted);
}

.add-rule-btn {
  margin-top: 12px;
  border: 1px dashed var(--border-color);
  background: transparent;
  color: var(--text-secondary);
}

.add-rule-btn:hover {
  border-color: var(--neon-cyan);
  color: var(--neon-cyan);
}

.generation-section {
  margin-bottom: 32px;
}

.count-setting {
  display: flex;
  align-items: center;
  gap: 12px;
}

.count-unit {
  color: var(--text-muted);
  font-size: 14px;
}

.generator-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
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

.generate-btn {
  padding: 12px 24px;
  background: var(--neon-primary);
  border: none;
  color: #000;
  font-weight: 600;
  border-radius: 10px;
}

.generate-btn:disabled {
  opacity: 0.5;
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