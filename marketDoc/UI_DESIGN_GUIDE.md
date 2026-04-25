# AI Platform v3.0 UI 设计指南 - 科技美学风格

## 1. 设计理念

基于 agency-agents 的设计理念，打造具有未来科技感的界面。采用深色主题、霓虹渐变、粒子动效和玻璃态设计，营造沉浸式 AI 平台体验。

---

## 2. 色彩系统

### 主色调 (Primary Colors)

| 名称 | 色值 | 用途 |
|------|------|------|
| 深空黑 | `#0a0a0f` | 主背景色 |
| 星云深蓝 | `#1a1a2e` | 卡片/面板背景 |
| 科技靛蓝 | `#16213e` | 次级背景/侧边栏 |

### 霓虹渐变色 (Neon Gradients)

```css
/* 主渐变 - 用于按钮、强调元素 */
--neon-primary: linear-gradient(135deg, #00d4ff 0%, #9b59ff 100%);

/* 辅助渐变 - 用于标签、徽章 */
--neon-secondary: linear-gradient(135deg, #9b59ff 0%, #ff6b9d 100%);

/* 成功渐变 */
--neon-success: linear-gradient(135deg, #00d4ff 0%, #00ff88 100%);

/* 警告渐变 */
--neon-warning: linear-gradient(135deg, #ff6b9d 0%, #ffaa00 100%);

/* 文字渐变 */
--text-gradient: linear-gradient(90deg, #00d4ff, #9b59ff, #ff6b9d);
```

### 功能色 (Functional Colors)

| 名称 | 色值 | 用途 |
|------|------|------|
| 科技青 | `#00d4ff` | 主要操作、链接、活跃状态 |
| 梦幻紫 | `#9b59ff` | 次要操作、特殊强调 |
| 霓虹粉 | `#ff6b9d` | 警示、重要提示 |
| 极光绿 | `#00ff88` | 成功状态、在线指示 |
| 日出橙 | `#ffaa00` | 警告状态 |
| 玻璃白 | `rgba(255, 255, 255, 0.08)` | 玻璃态背景 |
| 边框白 | `rgba(255, 255, 255, 0.1)` | 分割线、边框 |

---

## 3. 深色主题色板

### 背景层级 (Background Layers)

```css
/* 第0层 - 最深背景 */
--bg-base: #0a0a0f;

/* 第1层 - 卡片/面板 */
--bg-elevated: #1a1a2e;

/* 第2层 - 悬浮元素 */
--bg-elevated-hover: #252540;

/* 第3层 - 模态框/弹出层 */
--bg-overlay: #0f0f1a;
```

### 文字层级 (Text Hierarchy)

| 名称 | 色值 | 用途 |
|------|------|------|
| 主文字 | `#ffffff` (100% opacity) | 标题、重要信息 |
| 次文字 | `rgba(255, 255, 255, 0.85)` | 正文内容 |
| 弱文字 | `rgba(255, 255, 255, 0.65)` | 辅助说明 |
| 禁用文字 | `rgba(255, 255, 255, 0.35)` | 禁用状态 |

---

## 4. 玻璃态设计 (Glassmorphism)

### 玻璃卡片

```css
.glass-card {
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 16px;
  box-shadow:
    0 8px 32px rgba(0, 0, 0, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.1);
}
```

### 玻璃按钮

```css
.glass-button {
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 8px;
  transition: all 0.3s ease;
}

.glass-button:hover {
  background: rgba(255, 255, 255, 0.12);
  border-color: rgba(0, 212, 255, 0.5);
  box-shadow: 0 0 20px rgba(0, 212, 255, 0.2);
}
```

---

## 5. 粒子/网格效果

### 背景网格

```css
.bg-grid {
  background-image:
    linear-gradient(rgba(0, 212, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 212, 255, 0.03) 1px, transparent 1px);
  background-size: 50px 50px;
}
```

### 霓虹光晕效果

```css
.neon-glow {
  box-shadow:
    0 0 5px rgba(0, 212, 255, 0.5),
    0 0 20px rgba(0, 212, 255, 0.3),
    0 0 40px rgba(0, 212, 255, 0.1);
}

.neon-text {
  text-shadow:
    0 0 10px rgba(0, 212, 255, 0.8),
    0 0 20px rgba(0, 212, 255, 0.5),
    0 0 40px rgba(0, 212, 255, 0.3);
}
```

### 动态粒子 (可选增强)

- 使用 canvas 或 CSS animation 实现浮动粒子
- 粒子颜色：rgba(0, 212, 255, 0.6)
- 粒子大小：2-4px
- 动画：缓慢上浮 + 轻微左右摆动

---

## 6. 登录页面 UI 规范

### 页面布局

```
┌─────────────────────────────────────────────────────────────┐
│  [全屏背景：深空黑 + 动态网格 + 粒子效果]                    │
│                                                             │
│     ┌──────────────────────────────────────────┐            │
│     │                                          │            │
│     │           [Logo + 平台名称]              │            │
│     │                                          │            │
│     │    ┌────────────────────────────────┐    │            │
│     │    │     用户名输入框 (玻璃态)       │    │            │
│     │    └────────────────────────────────┘    │            │
│     │                                          │            │
│     │    ┌────────────────────────────────┐    │            │
│     │    │     密码输入框 (玻璃态)        │    │            │
│     │    └────────────────────────────────┘    │            │
│     │                                          │            │
│     │    [ 登 录 按 钮 (霓虹渐变) ]            │            │
│     │                                          │            │
│     │    忘记密码 | 注册账号                    │            │
│     └──────────────────────────────────────────┘            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 登录卡片规范

| 属性 | 值 |
|------|-----|
| 宽度 | 420px |
| 背景 | `rgba(26, 26, 46, 0.8)` + `backdrop-filter: blur(20px)` |
| 边框 | `1px solid rgba(255, 255, 255, 0.1)` |
| 圆角 | 24px |
| 内边距 | 48px |
| 阴影 | `0 25px 50px rgba(0, 0, 0, 0.5)` |

### 输入框规范

| 属性 | 值 |
|------|-----|
| 高度 | 52px |
| 背景 | `rgba(255, 255, 255, 0.05)` |
| 边框 | `1px solid rgba(255, 255, 255, 0.1)` |
| 圆角 | 12px |
| 聚焦边框 | `1px solid #00d4ff` |
| 聚焦阴影 | `0 0 20px rgba(0, 212, 255, 0.3)` |

### 登录按钮规范

| 属性 | 值 |
|------|-----|
| 高度 | 52px |
| 背景 | 霓虹渐变 (`--neon-primary`) |
| 圆角 | 12px |
| 字体 | 16px, 600 weight |
| Hover | 亮度增加 10% + 上移 2px + 增强阴影 |

---

## 7. 仪表盘 UI 规范

### 整体布局

```
┌────────────────────────────────────────────────────────────────────┐
│  [顶部导航栏]                                                      │
│  Logo | 仪表盘 | Agent管理 | 图谱 | 设置              [用户头像] ▼  │
├────────────────────────────────────────────────────────────────────┤
│                                                                    │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐  │
│  │  Agent总数  │ │   在线数    │ │  调用次数   │ │  成功率    │  │
│  │     12      │ │     8       │ │   1,234     │ │   98.5%    │  │
│  │  ↑ 12.5%   │ │  ↑ 5.2%    │ │  ↑ 23.1%   │ │  ↑ 0.3%   │  │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘  │
│                                                                    │
│  ┌────────────────────────────────────┐ ┌──────────────────────┐  │
│  │                                    │ │                      │  │
│  │         Agent 状态图 (环形图)       │ │   在线Agent列表      │  │
│  │                                    │ │   ○ Agent-1  在线    │  │
│  │           65% 在线                 │ │   ○ Agent-2  在线    │  │
│  │                                    │ │   ○ Agent-3  离线   │  │
│  └────────────────────────────────────┘ └──────────────────────┘  │
│                                                                    │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │                      最近调用记录                           │  │
│  │  ┌──────────────────────────────────────────────────────┐  │  │
│  │  │ Agent-1  │  用户:张三  │  状态:成功  │  2秒前        │  │  │
│  │  │ Agent-2  │  用户:李四  │  状态:失败  │  5秒前        │  │  │
│  │  └──────────────────────────────────────────────────────┘  │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

### 统计卡片规范

| 属性 | 值 |
|------|-----|
| 背景 | 玻璃态 (`rgba(255, 255, 255, 0.05)`) |
| 边框 | `1px solid rgba(255, 255, 255, 0.08)` |
| 圆角 | 16px |
| 内边距 | 24px |
| Hover | 边框变为 `rgba(0, 212, 255, 0.3)` |

### 数字展示

| 属性 | 值 |
|------|-----|
| 数字大小 | 36px |
| 数字颜色 | 白色 + 霓虹文字阴影 |
| 标签大小 | 14px |
| 标签颜色 | `rgba(255, 255, 255, 0.65)` |
| 趋势箭头 | 绿色(上涨) / 红色(下跌) |

### Agent 列表项

```css
.agent-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 12px;
  margin-bottom: 8px;
  transition: all 0.2s ease;
}

.agent-item:hover {
  background: rgba(255, 255, 255, 0.08);
  transform: translateX(4px);
}

/* 在线状态指示 */
.status-online::before {
  content: '';
  width: 8px;
  height: 8px;
  background: #00ff88;
  border-radius: 50%;
  box-shadow: 0 0 10px #00ff88;
}

.status-offline::before {
  content: '';
  width: 8px;
  height: 8px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 50%;
}
```

---

## 8. 响应式设计断点

### 断点定义

| 名称 | 宽度范围 | 用途 |
|------|----------|------|
| xs | < 576px | 极小屏幕手机 |
| sm | 576px - 768px | 平板竖屏 |
| md | 768px - 992px | 平板横屏 |
| lg | 992px - 1200px | 小桌面 |
| xl | 1200px - 1400px | 标准桌面 |
| xxl | > 1400px | 大屏显示器 |

### 登录页面响应式

| 断点 | 布局调整 |
|------|----------|
| < 576px | 登录卡片宽度 100%，padding 缩小为 24px |
| 576px - 768px | 登录卡片宽度 90%，padding 32px |
| > 768px | 登录卡片居中，宽度 420px |

### 仪表盘响应式

| 断点 | 布局调整 |
|------|----------|
| < 576px | 统计卡片 1 列，图表全宽，侧边栏折叠 |
| 576px - 768px | 统计卡片 2 列 |
| 768px - 992px | 统计卡片 2 列，图表/列表上下排列 |
| 992px - 1200px | 统计卡片 4 列，图表/列表左右排列 |
| > 1200px | 完整布局，最大内容宽度 1400px |

### 栅格系统

```css
.grid-system {
  display: grid;
  gap: 24px;
}

/* 响应式列数 */
.grid-1 { grid-template-columns: repeat(1, 1fr); }
.grid-2 { grid-template-columns: repeat(2, 1fr); }
.grid-3 { grid-template-columns: repeat(3, 1fr); }
.grid-4 { grid-template-columns: repeat(4, 1fr); }

@media (max-width: 768px) {
  .grid-2, .grid-3, .grid-4 {
    grid-template-columns: repeat(1, 1fr);
  }
}

@media (min-width: 768px) and (max-width: 992px) {
  .grid-3, .grid-4 {
    grid-template-columns: repeat(2, 1fr);
  }
}
```

---

## 9. 动画规范

### 过渡时长

| 类型 | 时长 |
|------|------|
| 微交互 | 150ms |
| 标准过渡 | 300ms |
| 复杂动画 | 500ms |
| 页面切换 | 400ms |

### 缓动函数

```css
--ease-out: cubic-bezier(0.16, 1, 0.3, 1);      /* 标准缓出 */
--ease-in-out: cubic-bezier(0.65, 0, 0.35, 1);  /* 渐入渐出 */
--ease-bounce: cubic-bezier(0.34, 1.56, 0.64, 1); /* 弹性效果 */
```

### 常见动画

```css
/* 卡片悬浮动画 */
.card-hover {
  transition: transform 0.3s var(--ease-out),
              border-color 0.3s ease,
              box-shadow 0.3s ease;
}
.card-hover:hover {
  transform: translateY(-4px);
  border-color: rgba(0, 212, 255, 0.4);
  box-shadow: 0 12px 40px rgba(0, 212, 255, 0.15);
}

/* 按钮点击波纹 */
.button-ripple {
  position: relative;
  overflow: hidden;
}
.button-ripple::after {
  content: '';
  position: absolute;
  width: 100%;
  height: 100%;
  top: 0;
  left: 0;
  background: radial-gradient(circle, rgba(255,255,255,0.3) 0%, transparent 70%);
  transform: scale(0);
  opacity: 0;
  transition: transform 0.5s, opacity 0.3s;
}
.button-ripple:active::after {
  transform: scale(2);
  opacity: 1;
  transition: 0s;
}
```

---

## 10. 字体规范

| 用途 | 字体 | 字号 | 字重 |
|------|------|------|------|
| 平台名称 | Inter / 系统无衬线 | 24px | 700 |
| 页面标题 | Inter / 系统无衬线 | 20px | 600 |
| 卡片标题 | Inter / 系统无衬线 | 16px | 600 |
| 正文内容 | Inter / 系统无衬线 | 14px | 400 |
| 辅助说明 | Inter / 系统无衬线 | 12px | 400 |
| 数字展示 | JetBrains Mono / monospace | 32-48px | 700 |

---

## 11. 组件状态汇总

### 按钮状态

| 状态 | 背景 | 边框 | 文字颜色 |
|------|------|------|----------|
| Default | `rgba(255,255,255,0.08)` | `rgba(255,255,255,0.15)` | 白色 |
| Hover | `rgba(255,255,255,0.12)` | `rgba(0,212,255,0.5)` | 白色 |
| Active | `rgba(0,212,255,0.2)` | `rgba(0,212,255,0.8)` | 白色 |
| Disabled | `rgba(255,255,255,0.03)` | `rgba(255,255,255,0.05)` | `rgba(255,255,255,0.3)` |

### 输入框状态

| 状态 | 边框 | 背景 |
|------|------|------|
| Default | `rgba(255,255,255,0.1)` | `rgba(255,255,255,0.05)` |
| Focus | `#00d4ff` | `rgba(0,212,255,0.05)` |
| Error | `#ff6b9d` | `rgba(255,107,157,0.05)` |
| Disabled | `rgba(255,255,255,0.05)` | `rgba(255,255,255,0.02)` |

---

## 12. 附录：CSS 变量速查表

```css
:root {
  /* 背景色 */
  --bg-base: #0a0a0f;
  --bg-elevated: #1a1a2e;
  --bg-elevated-hover: #252540;
  --bg-overlay: #0f0f1a;

  /* 品牌色 */
  --neon-cyan: #00d4ff;
  --neon-purple: #9b59ff;
  --neon-pink: #ff6b9d;
  --neon-green: #00ff88;
  --neon-orange: #ffaa00;

  /* 渐变 */
  --neon-primary: linear-gradient(135deg, #00d4ff 0%, #9b59ff 100%);
  --neon-secondary: linear-gradient(135deg, #9b59ff 0%, #ff6b9d 100%);

  /* 玻璃态 */
  --glass-bg: rgba(255, 255, 255, 0.05);
  --glass-border: rgba(255, 255, 255, 0.1);
  --glass-blur: blur(20px);

  /* 文字 */
  --text-primary: rgba(255, 255, 255, 1);
  --text-secondary: rgba(255, 255, 255, 0.85);
  --text-muted: rgba(255, 255, 255, 0.65);
  --text-disabled: rgba(255, 255, 255, 0.35);

  /* 缓动 */
  --ease-out: cubic-bezier(0.16, 1, 0.3, 1);
  --ease-in-out: cubic-bezier(0.65, 0, 0.35, 1);
  --ease-bounce: cubic-bezier(0.34, 1.56, 0.64, 1);
}
```
