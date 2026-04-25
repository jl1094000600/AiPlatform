# AI Platform v3.0 UX 设计指南

| 版本 | 日期       | 作者 | 备注 |
|------|------------|------|------|
| 1.0  | 2026-04-18 | UX Designer | 初稿 |

---

## 1. 用户旅程分析

### 1.1 用户角色定义

| 角色 | 使用频率 | 核心场景 | 痛点 |
|------|----------|----------|------|
| 系统管理员 | 高 | 全面监控、权限配置、Agent全生命周期管理 | 信息量大，需快速定位问题 |
| 运维人员 | 高 | Agent发布下线、监控告警、故障排查 | 需要清晰的系统状态感知 |
| 业务模块负责人 | 中 | 调用Agent、查看统计数据、授权管理 | 需要了解Agent可用性和性能 |
| 普通用户 | 低 | 浏览Agent列表、查看基本监控 | 界面复杂，功能难以发现 |

### 1.2 用户旅程地图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              用户旅程地图                                     │
├──────────┬──────────────────────────────────────────────────────────────────┤
│ 阶段      │ 登录 → 首页概览 → Agent管理 → 监控分析 → 图谱查看                │
├──────────┼──────────────────────────────────────────────────────────────────┤
│ 用户目标  │ 安全进入系统   了解全局状态  管理Agent   分析性能    理解关系     │
├──────────┼──────────────────────────────────────────────────────────────────┤
│ 触点      │ 登录页        首页Dashboard Agent列表    监控面板    关系图谱      │
├──────────┼──────────────────────────────────────────────────────────────────┤
│ 关键行为  │ 输入账号密码   查看指标卡片   CRUD操作   时间筛选    节点交互     │
├──────────┼──────────────────────────────────────────────────────────────────┤
│ 情绪曲线  │ 😊 → 😐 → 😊 → 😟 → 😊                                           │
│           │ 期待流畅登录   看到数据满意  操作便捷   排查复杂    交互有趣     │
├──────────┼──────────────────────────────────────────────────────────────────┤
│ 痛点      │ 等待时间长     信息过载      弹窗层次深   数据难理解  图谱复杂    │
├──────────┼──────────────────────────────────────────────────────────────────┤
│ 机会点    │ 记住登录状态   简化Dashboard 减少操作步骤 提供智能分析 优化图例  │
└──────────┴──────────────────────────────────────────────────────────────────┘
```

### 1.3 关键场景分析

#### 场景1: 新用户首次登录
```
用户行为序列:
1. 打开登录页 → 2. 观察品牌Logo动画 → 3. 输入凭证 → 4. 点击登录
5. 等待加载 → 6. 进入首页 → 7. 探索功能模块

优化建议:
- 登录按钮添加微交互反馈
- 登录过程显示进度指示
- 首次登录显示引导提示
- Dashboard默认展示关键指标，避免信息过载
```

#### 场景2: 运维人员发布Agent
```
用户行为序列:
1. 进入Agent列表 → 2. 筛选/搜索目标Agent → 3. 点击发布按钮
4. 确认发布配置 → 5. 等待发布结果 → 6. 验证上线状态

优化建议:
- 列表页支持快速筛选（状态、分类）
- 发布按钮添加加载状态
- 发布成功显示Toast并自动刷新状态
- 发布失败显示具体错误信息和重试选项
```

#### 场景3: 故障排查
```
用户行为序列:
1. 首页发现异常指标 → 2. 点击异常Agent → 3. 查看调用记录
4. 分析错误日志 → 5. 追踪执行链路 → 6. 定位问题根因

优化建议:
- 首页异常指标添加告警标记和快速跳转
- 调用记录支持按状态筛选（成功/失败）
- 执行链路支持时间线回放
- 错误信息高亮显示，提供解决方案链接
```

---

## 2. 交互流程优化建议

### 2.1 导航架构优化

**当前结构:**
```
侧边栏
├── 首页 (Dashboard)
├── Agent管理
│   ├── Agent列表
│   ├── 关系图谱
│   └── 监控面板
├── 模型管理
├── 统计分析
└── 系统设置
```

**优化建议 - 采用渐进式披露:**

```
侧边栏（收起/展开）
├── 首页
├── Agent中心 ← 一级入口
│   ├── 列表视图（默认）
│   ├── 图谱视图（切换）
│   └── 监控视图（切换）
├── 模型中心
├── 数据中心
└── 系统
```

### 2.2 关键流程优化

#### Agent发布流程优化
```
当前流程（5步）:
列表页 → 点击发布 → 确认弹窗 → 发布中loading → 结果反馈

优化后流程（3步）:
列表页 → 发布按钮(带预览) → 即时反馈

交互细节:
- 悬停发布按钮显示发布预览tooltip
- 点击后按钮变为进度条样式
- 发布成功：按钮变绿+震动反馈
- 发布失败：按钮变红+显示错误原因
```

#### 筛选器优化
```
当前问题:
- 筛选条件分散
- 筛选结果不实时反馈
- 清除筛选操作繁琐

优化方案:
┌─────────────────────────────────────────────┐
│ 🔍 搜索Agent...   [状态▼] [分类▼] [时间▼]  x │  ← 一行式高级筛选
└─────────────────────────────────────────────┘
- 输入框支持实时搜索（300ms防抖）
- 状态和分类支持多选
- 筛选条件显示为标签，可单独移除
- 一键清除全部筛选
```

#### 图谱交互优化
```
当前问题:
- 节点信息需要点击才能查看
- 图谱操作复杂，普通用户难以理解
- 节点过多时难以阅读

优化方案:
1. 悬停显示快速信息卡片
   ┌────────────────┐
   │ 文本生成器     │
   │ 状态: 在线     │  ← 悬停0.5s后显示
   │ 调用: 1.2k/日  │
   └────────────────┘

2. 双击节点进入详情
3. 右键菜单提供快捷操作
4. 支持框选批量操作
5. 缩略图导航（节点过多时）
```

### 2.3 状态反馈设计

| 操作 | 当前反馈 | 优化后反馈 |
|------|----------|------------|
| 保存成功 | Toast提示 | Toast + 标签变色 + 微动画 |
| 保存失败 | Toast提示 | Toast + 输入框高亮 + 错误说明 |
| 删除确认 | 确认弹窗 | 确认弹窗 + 二次确认输入 |
| 加载中 | 页面级loading | 骨架屏 + 操作按钮loading态 |
| 网络错误 | 通用错误提示 | 具体错误 + 重试按钮 |

### 2.4 快捷操作设计

```
键盘快捷键:
- / 或 Ctrl+K: 全局搜索
- Esc: 关闭当前弹窗/取消操作
- Enter: 确认当前操作
- Tab: 在表单中切换焦点
- ← →: 在图谱中缩放

鼠标快捷操作:
- 双击Agent节点: 打开详情
- 右键节点: 显示操作菜单
- 滚轮: 图谱缩放
- Shift+框选: 批量选择节点
```

---

## 3. 动效设计规范

### 3.1 动效设计原则

```
┌────────────────────────────────────────────────────────────────┐
│                     动效设计四原则                              │
├────────────────────────────────────────────────────────────────┤
│  1. 有意义 (Meaningful)                                        │
│     - 每个动画都应传达信息，而非装饰                            │
│     - 例: 状态变化动画告知用户操作结果                          │
│                                                                │
│  2. 即时响应 (Immediate)                                       │
│     - 响应时间 < 100ms，用户感受无延迟                          │
│     - 例: 按钮点击反馈在50ms内                                 │
│                                                                │
│  3. 自然流畅 (Natural)                                         │
│     - 使用物理感知的缓动曲线，避免线性动画                      │
│     - 例: 弹性效果、惯性滚动                                    │
│                                                                │
│  4. 低耗高效 (Efficient)                                       │
│     - 避免大型动画影响性能                                      │
│     - 使用CSS transform/opacity实现硬件加速                     │
└────────────────────────────────────────────────────────────────┘
```

### 3.2 过渡动画规范

#### 页面过渡
```css
/* 标准页面进入 */
.page-enter-active {
  animation: pageIn 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes pageIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 页面退出 */
.page-leave-active {
  animation: pageOut 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes pageOut {
  to {
    opacity: 0;
    transform: translateY(-10px);
  }
}
```

#### 弹窗过渡
```css
/* 弹窗进入 - 从中心放大 */
.dialog-enter-active {
  animation: dialogIn 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes dialogIn {
  from {
    opacity: 0;
    transform: scale(0.95);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

/* 弹窗退出 */
.dialog-leave-active {
  animation: dialogOut 0.2s cubic-bezier(0.4, 0, 1, 1);
}

@keyframes dialogOut {
  to {
    opacity: 0;
    transform: scale(0.95);
  }
}

/* 遮罩层 */
.overlay-enter-active,
.overlay-leave-active {
  transition: opacity 0.2s ease;
}
.overlay-enter-from,
.overlay-leave-to {
  opacity: 0;
}
```

### 3.3 加载动画规范

#### 全局Loading
```css
/* 统一loading动画 */
.global-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.loading-dot {
  width: 8px;
  height: 8px;
  background: var(--accent-cyan);
  border-radius: 50%;
  animation: loadingPulse 1.4s ease-in-out infinite;
}

.loading-dot:nth-child(2) { animation-delay: 0.2s; }
.loading-dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes loadingPulse {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.5;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}
```

#### 骨架屏
```css
/* 骨架屏动画 */
.skeleton {
  background: linear-gradient(
    90deg,
    var(--bg-card) 0%,
    rgba(255,255,255,0.05) 50%,
    var(--bg-card) 100%
  );
  background-size: 200% 100%;
  animation: skeletonShimmer 1.5s ease-in-out infinite;
}

@keyframes skeletonShimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
```

#### 按钮Loading态
```css
.btn-loading {
  position: relative;
  color: transparent !important;
  pointer-events: none;
}

.btn-loading::after {
  content: '';
  position: absolute;
  width: 16px;
  height: 16px;
  top: 50%;
  left: 50%;
  margin: -8px 0 0 -8px;
  border: 2px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: btnSpin 0.6s linear infinite;
}

@keyframes btnSpin {
  to { transform: rotate(360deg); }
}
```

### 3.4 反馈动画规范

#### 操作成功
```css
/* 成功状态 - 绿色脉冲 */
.success-pulse {
  animation: successPulse 0.6s ease-out;
}

@keyframes successPulse {
  0% {
    box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7);
  }
  70% {
    box-shadow: 0 0 0 10px rgba(16, 185, 129, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(16, 185, 129, 0);
  }
}
```

#### 操作失败
```css
/* 失败状态 - 抖动效果 */
.error-shake {
  animation: errorShake 0.5s cubic-bezier(0.36, 0.07, 0.19, 0.97);
}

@keyframes errorShake {
  10%, 90% { transform: translateX(-1px); }
  20%, 80% { transform: translateX(2px); }
  30%, 50%, 70% { transform: translateX(-3px); }
  40%, 60% { transform: translateX(3px); }
}
```

#### 状态切换
```css
/* 状态徽章变化 */
.status-change {
  animation: statusChange 0.3s ease-out;
}

@keyframes statusChange {
  0% { transform: scale(1); }
  50% { transform: scale(1.2); }
  100% { transform: scale(1); }
}
```

### 3.5 图谱动画规范

#### 节点出现
```javascript
// 节点依次出现动画
nodes.forEach((node, index) => {
  node.style = {
    opacity: 0,
    scale: 0
  }
  setTimeout(() => {
    animate(node, {
      opacity: 1,
      scale: 1,
      duration: 300,
      easing: 'elasticOut'
    })
  }, index * 50) // 依次延迟
})
```

#### 节点连接线动画
```javascript
// 连线流动动画
lineStyle: {
  animation: {
    duration: 2000,
    easing: 'linear',
    loop: true
  }
}
```

#### 节点拖拽
```javascript
// 拖拽时的惯性效果
onDragEnd(node) {
  // 减速动画
  animateWithPhysics(node, {
    velocity: currentVelocity,
    friction: 0.9,
    onUpdate: (position) => {
      node.position = position
    }
  })
}
```

### 3.6 动画性能规范

```
性能优化原则:
1. 优先使用 CSS 动画（transform, opacity）
2. 避免动画改变布局属性（width, height, margin, padding）
3. 使用 will-change 提示浏览器优化
4. 复杂动画使用 requestAnimationFrame
5. 滚动时禁用非必要动画

性能检测指标:
- FPS < 30 时需优化
- 主线程阻塞 > 50ms 需优化
- 动画内存占用 > 50MB 需优化
```

---

## 4. 无障碍设计考虑

### 4.1 色彩对比规范

```css
/* 色彩系统 - 符合 WCAG 2.1 AA 标准 */
:root {
  /* 主文本 - 对比度 >= 4.5:1 */
  --text-primary: #e5e7eb;      /* on dark bg, ratio: 12:1 */
  --text-secondary: #9ca3af;    /* 对比度 5.2:1 */

  /* 次要文本 - 对比度 >= 3:1 */
  --text-muted: #6b7280;        /* 对比度 3.2:1 */

  /* 功能色 - 需要验证对比度 */
  --accent-cyan: #00f0ff;        /* 亮色需配合深色背景 */
  --accent-purple: #8b5cf6;
  --accent-green: #10b981;
  --accent-red: #ef4444;
  --accent-orange: #f59e0b;

  /* 状态指示 - 需配合图标/文字 */
  --status-online: #10b981;
  --status-offline: #6b7280;
  --status-error: #ef4444;
}

/* 高对比度模式 */
@media (prefers-contrast: high) {
  :root {
    --text-primary: #ffffff;
    --text-secondary: #d1d5db;
    --border-color: #525252;
  }
}
```

### 4.2 焦点管理规范

```css
/* 键盘焦点样式 */
:focus-visible {
  outline: 2px solid var(--accent-cyan);
  outline-offset: 2px;
}

/* 移除默认焦点环 */
:focus:not(:focus-visible) {
  outline: none;
}

/* 焦点顺序指示 */
[data-focus-order] {
  counter-reset: focus-order;
}

[data-focus-order] > *:focus {
  counter-increment: focus-order;
}

[data-focus-order] > *:focus::after {
  content: counter(focus-order);
  position: absolute;
  top: -8px;
  right: -8px;
  width: 16px;
  height: 16px;
  background: var(--accent-cyan);
  border-radius: 50%;
  font-size: 10px;
  line-height: 16px;
  text-align: center;
  color: #000;
}
```

### 4.3 键盘导航规范

```javascript
// 键盘导航配置
const keyboardNavigation = {
  // 全局快捷键
  global: {
    '/': { action: 'focusSearch', description: '聚焦搜索框' },
    '?': { action: 'showShortcuts', description: '显示快捷键帮助' },
    'Escape': { action: 'closeModal', description: '关闭弹窗' }
  },

  // Agent列表页
  'agent-list': {
    'j': { action: 'nextRow', description: '下一行' },
    'k': { action: 'prevRow', description: '上一行' },
    'Enter': { action: 'openDetail', description: '打开详情' },
    'e': { action: 'editSelected', description: '编辑选中项' },
    'p': { action: 'publishSelected', description: '发布选中项' }
  },

  // 图谱页
  'agent-graph': {
    '+': { action: 'zoomIn', description: '放大' },
    '-': { action: 'zoomOut', description: '缩小' },
    '0': { action: 'resetZoom', description: '重置缩放' },
    'ArrowUp/Down/Left/Right': { action: 'pan', description: '平移视图' },
    'Tab': { action: 'nextNode', description: '下一个节点' }
  }
}
```

### 4.4 屏幕阅读器支持

```html
<!-- 语义化标签 -->
<nav aria-label="主导航">
  <aside aria-label="Agent列表">
    <article aria-labelledby="agent-title">
      <h3 id="agent-title">文本生成器</h3>
      <p aria-describedby="agent-desc">用于生成营销文案的Agent</p>
    </article>
  </aside>
</nav>

<!-- 状态 aria 属性 -->
<span
  class="status-badge"
  role="status"
  aria-label="Agent状态"
  aria-live="polite"
>
  在线
</span>

<!-- 加载状态 -->
<div aria-busy="true" aria-label="加载中">
  <span class="sr-only">正在加载Agent列表，请稍候</span>
</div>

<!-- 隐藏但保留可访问性 -->
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
```

### 4.5 减少运动偏好

```css
/* 尊重用户减少运动偏好 */
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }

  /* 页面过渡改为简单淡入 */
  .page-enter-active,
  .page-leave-active {
    animation: none;
    opacity: 1;
  }

  /* 图谱动画禁用 */
  .graph-node {
    transition: none !important;
  }
}
```

### 4.6 无障碍检查清单

```
□ 所有交互元素可键盘访问
□ 焦点顺序符合逻辑阅读顺序
□ 表单标签与输入框关联
□ 按钮/链接有描述性文字
□ 图片有alt属性
□ 颜色不作为唯一信息传达方式
□ 对比度符合WCAG标准
□ 错误信息清晰说明原因
□ 支持屏幕阅读器
□ 支持减少运动偏好
```

---

## 5. 用户反馈机制设计

### 5.1 反馈类型与层级

```
┌─────────────────────────────────────────────────────────────────┐
│                        反馈金字塔                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│                      ┌─────────┐                                │
│                      │  Toast  │  ← 操作结果（2-4秒自动消失）     │
│                      └────┬────┘                                │
│                     ┌─────┴─────┐                               │
│                     │  Inline   │  ← 表单验证、状态变化           │
│                     └─────┬─────┘                               │
│                    ┌──────┴──────┐                              │
│                    │   Dialog   │  ← 确认操作、复杂信息           │
│                    └────────────┘                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 5.2 Toast 反馈规范

```javascript
// Toast 配置
const toastConfig = {
  success: {
    duration: 3000,
    icon: '✓',
    color: 'var(--accent-green)',
    position: 'top-center'
  },
  error: {
    duration: 5000,  // 错误信息停留更久
    icon: '✕',
    color: 'var(--accent-red)',
    position: 'top-center',
    action: {
      text: '重试',
      handler: () => {} // 可选的重新操作
    }
  },
  warning: {
    duration: 4000,
    icon: '⚠',
    color: 'var(--accent-orange)'
  },
  info: {
    duration: 3000,
    icon: 'ℹ',
    color: 'var(--accent-cyan)'
  }
}

// 使用示例
ElMessage.success({
  message: 'Agent发布成功',
  duration: 3000,
  showClose: true
})

ElMessage.error({
  message: '网络连接失败',
  duration: 5000,
  action: {
    text: '重试',
    handler: () => retryRequest()
  }
})
```

### 5.3 表单验证反馈

```html
<template>
  <el-form :model="form" :rules="rules" ref="formRef">
    <!-- 实时验证 -->
    <el-form-item prop="agentName" required>
      <el-input v-model="form.agentName" />
      <span class="field-error" role="alert">
        {{ formErrors.agentName }}
      </span>
    </el-form-item>

    <!-- 提交时验证 -->
    <span class="form-error-summary" role="alert" aria-live="polite">
      请修正以下 {{ errorCount }} 个问题后重试
    </span>
  </el-form>
</template>
```

```css
/* 验证样式 */
.field-error {
  color: var(--accent-red);
  font-size: 12px;
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.field-error::before {
  content: '!';
  width: 14px;
  height: 14px;
  background: var(--accent-red);
  color: #fff;
  border-radius: 50%;
  font-size: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.is-error :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--accent-red);
}

.is-valid :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--accent-green);
}
```

### 5.4 加载状态反馈

```html
<!-- 全局加载 -->
<el-loading
  v-model="loading"
  text="加载中..."
  background="rgba(0, 0, 0, 0.8)"
/>

<!-- 骨架屏 -->
<div v-if="skeletonLoading" class="skeleton-container">
  <div class="skeleton-header"></div>
  <div class="skeleton-row" v-for="i in 5" :key="i"></div>
</div>

<!-- 进度指示 -->
<el-progress
  :percentage="progress"
  :status="progressStatus"
  :stroke-width="8"
  striped
  striped-flow
/>
```

### 5.5 空状态设计

```html
<!-- 空状态模板 -->
<el-empty
  v-if="isEmpty"
  image="empty-illustration.svg"
  description="暂无数据"
>
  <template #actions>
    <el-button type="primary" @click="handleCreate">
      立即创建
    </el-button>
    <el-button @click="handleRefresh">
      刷新页面
    </el-button>
  </template>
</el-empty>
```

```css
/* 空状态样式 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
}

.empty-state .empty-icon {
  width: 120px;
  height: 120px;
  margin-bottom: 24px;
  opacity: 0.6;
}

.empty-state h3 {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 8px;
}

.empty-state p {
  color: var(--text-muted);
  margin-bottom: 24px;
}
```

### 5.6 错误状态设计

```html
<!-- 错误状态 -->
<div v-if="hasError" class="error-state">
  <div class="error-icon">
    <svg viewBox="0 0 24 24" fill="none">
      <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/>
      <line x1="12" y1="8" x2="12" y2="12" stroke="currentColor" stroke-width="2"/>
      <circle cx="12" cy="16" r="1" fill="currentColor"/>
    </svg>
  </div>
  <h3>加载失败</h3>
  <p>{{ errorMessage }}</p>
  <div class="error-actions">
    <el-button type="primary" @click="handleRetry">
      重试
    </el-button>
    <el-button @click="handleReport">
      报告问题
    </el-button>
  </div>
</div>
```

```javascript
// 错误边界处理
const errorHandler = (error, instance, info) => {
  // 上报错误
  console.error('Error:', error)
  console.error('Info:', info)

  // 用户反馈
  ElMessage.error({
    message: '页面出现错误，已尝试自动恢复',
    duration: 5000
  })
}
```

### 5.7 反馈渠道设计

```
┌─────────────────────────────────────────────────────────────────┐
│                      用户反馈渠道                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. 内置反馈入口                                                 │
│     ┌──────────────────────────────────────────┐                │
│     │ [?] 帮助  │  💬 反馈  │  ⚙ 设置          │                │
│     └──────────────────────────────────────────┘                │
│     - 浮窗反馈表                                                   │
│     - 问题截图+描述                                                │
│     - 提交后显示工单号                                             │
│                                                                 │
│  2. 快捷反馈                                                     │
│     - Toast操作栏「有帮助/没帮助」                                │
│     - 页面底部「这个页面有帮助吗？」                               │
│                                                                 │
│  3. 用户满意度调查                                               │
│     - 关键操作后弹出                                              │
│     - 1-5星评分+可选问题分类                                      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 5.8 反馈响应时间规范

| 反馈类型 | 响应时间 | 处理方式 |
|----------|----------|----------|
| 操作成功 | 即时 | Toast 2-4秒 |
| 表单错误 | <100ms | 输入框下方即时显示 |
| 页面加载 | <2s | 骨架屏/进度条 |
| 接口错误 | 即时 | Toast+重试按钮 |
| 功能异常 | - | 错误边界自动恢复 |
| 用户提交反馈 | <24h | 客服回复 |

---

## 6. 实施优先级

### Phase 1 - 核心体验优化（1-2周）
1. Toast反馈增强（增加操作按钮）
2. 表单验证即时反馈
3. 键盘快捷键支持
4. 加载状态骨架屏
5. 空状态和错误状态UI

### Phase 2 - 交互体验升级（2-3周）
1. 页面过渡动画优化
2. 图谱交互增强（悬停预览、右键菜单）
3. 快捷操作优化
4. 焦点管理优化
5. 筛选器交互优化

### Phase 3 - 无障碍与国际化（2周）
1. WCAG AA合规性修复
2. 屏幕阅读器测试
3. 减少运动偏好支持
4. 高对比度模式
5. 国际化准备

---

## 附录

### A. 色彩变量参考
```css
:root {
  /* 主色 */
  --accent-cyan: #00f0ff;
  --accent-purple: #8b5cf6;
  --accent-magenta: #ff00aa;

  /* 语义色 */
  --accent-green: #10b981;
  --accent-red: #ef4444;
  --accent-orange: #f59e0b;
  --accent-blue: #3b82f6;

  /* 背景 */
  --bg-primary: #0f0f23;
  --bg-secondary: #1a1a2e;
  --bg-card: rgba(30, 30, 50, 0.8);

  /* 文字 */
  --text-primary: #e5e7eb;
  --text-secondary: #9ca3af;
  --text-muted: #6b7280;

  /* 边框 */
  --border-color: rgba(255, 255, 255, 0.1);
}
```

### B. 动画时长参考
| 类型 | 时长 | 缓动 |
|------|------|------|
| 微交互 | 100-200ms | ease-out |
| 元素过渡 | 200-300ms | cubic-bezier(0.4, 0, 0.2, 1) |
| 页面过渡 | 300-400ms | cubic-bezier(0.4, 0, 0.2, 1) |
| 加载动画 | 无限循环 | linear |
| 图谱动画 | 500-1000ms | elasticOut |

### C. 参考资源
- [WCAG 2.1 Guidelines](https://www.w3.org/WAI/WCAG21/quickref/)
- [Element Plus Accessibility](https://element-plus.org/zh-CN/guide/accessibility.html)
- [Motion Design Principles](https://material.io/design/motion/)
