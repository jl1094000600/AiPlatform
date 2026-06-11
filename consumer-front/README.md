# Think Land Consumer Front

面向 C 端用户的 Think Land 前端应用，结构参考 `front`，使用 Vue 3 + Vue Router + Vite。

## 页面

- `/`：官网首页，展示产品介绍、PRD 生成动画、流程图生成动画和底部合规说明。
- `/login`：登录 / 注册页，保留当前偏轻消费级的视觉设计。
- `/workspace`：登录后的使用页面，用于输入产品想法并查看 PRD、流程图和任务拆解预览。

## 命令

当前项目复用同级 `front/node_modules` 中的依赖版本，避免产生额外版本漂移。

```bash
npm run dev
npm run build
```
