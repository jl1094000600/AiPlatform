# AI中台开发规范

## 版本历史

| 版本 | 日期       | 作者 | 备注 |
|------|------------|------|------|
| 1.0  | 2026-04-14 | PM   | 初稿 |
| 1.1  | 2026-04-14 | PM   | 增加代码审查规范 |

---

## 1. 数据库设计规范

### 1.1 外键约束

**规则：数据库表之间不允许使用外键约束**

原因：
1. **性能考虑**：外键约束在大数据量导入、更新时会产生额外的锁开销
2. **维护性**：级联删除可能导致意外数据丢失，难以追踪
3. **扩展性**：不便于分库分表等水平扩展
4. **一致性**：应用层事务控制可以更好地保证数据一致性

**实现方式**：
- 关联字段仅作为普通索引存在
- 数据一致性由应用层（Service层）保证
- 删除操作前需先查询确认关联数据

**示例**：
```sql
-- 错误：使用外键
CREATE TABLE order (
    id BIGINT PRIMARY KEY,
    user_id BIGINT FOREIGN KEY REFERENCES user(id)
);

-- 正确：只保留索引，应用层保证一致性
CREATE TABLE order (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    INDEX idx_user_id (user_id)
);
```

### 1.2 表设计规范

1. **主键**：使用BIGINT自增主键，名称为`id`
2. **软删除**：每张表必须有`is_deleted`字段（TINYINT，0未删除，1已删除）
3. **时间戳**：必须包含`create_time`和`update_time`字段
4. **索引**：
   - 唯一字段添加UNIQUE KEY
   - 常用查询条件字段添加普通INDEX
   - 复合索引注意字段顺序
5. **命名**：
   - 表名使用小写+下划线
   - 字段名使用小写+下划线
   - 索引名格式：`idx_字段名` 或 `uk_字段名`（唯一）

### 1.3 字段类型规范

| 场景 | 推荐类型 | 说明 |
|------|----------|------|
| 主键ID | BIGINT | 自增，足够大的范围 |
| 状态标志 | TINYINT | 0/1/2等小整数 |
| 状态码 | INT | HTTP状态码等 |
| 金额/价格 | DECIMAL(10,2) | 精确小数 |
| JSON数据 | JSON | MySQL 5.7+原生支持 |
| 大文本 | TEXT | 文章内容等 |
| 短文本 | VARCHAR(255) | 名称、标题等 |
| 时间 | DATETIME | 带时间精度需求 |
| 日期 | DATE | 只需日期 |

---

## 2. Java后端规范

### 2.1 项目结构

```
src/main/java/com/aipal/
├── controller/     # 控制器层，接收请求、参数校验
├── service/        # 服务层，业务逻辑处理
├── mapper/         # 数据访问层，数据库操作
├── entity/         # 实体类，对应数据库表
├── dto/            # 数据传输对象，请求/响应
├── config/         # 配置类
└── common/         # 通用类、工具类
```

### 2.2 命名规范

- **类名**：大驼峰，如 `AgentController`
- **方法名**：小驼峰，如 `listAgents`
- **变量名**：小驼峰，如 `pageNum`
- **常量**：全大写+下划线，如 `MAX_PAGE_SIZE`

### 2.3 Controller规范

1. 使用@RestController注解
2. 统一使用Result类返回响应
3. 参数使用@RequestParam、@RequestBody等注解
4. 路径变量使用@PathVariable
5. 必须指定请求方法（GET/POST/PUT/DELETE）

**示例**：
```java
@PostMapping("/{id}/publish")
public Result<Boolean> publishAgent(@PathVariable Long id) {
    return Result.success(agentService.publish(id));
}
```

### 2.4 Service规范

1. 使用@Service注解
2. 使用@RequiredArgsConstructor注入依赖
3. 业务逻辑必须在Service层处理
4. 关联查询前先校验数据是否存在
5. 删除操作前检查是否有关联数据

**示例**：
```java
public boolean deleteAgent(Long id) {
    AiAgent agent = agentMapper.selectById(id);
    if (agent != null && agent.getStatus() == 1) {
        throw new RuntimeException("只能删除已下线的Agent");
    }
    return agentMapper.deleteById(id) > 0;
}
```

### 2.5 异常处理

1. 使用自定义BizException处理业务异常
2. 使用GlobalExceptionHandler统一处理异常
3. 不在Controller层捕获异常（由全局处理器处理）

---

## 3. 前端Vue3规范

### 3.1 项目结构

```
src/
├── api/           # API调用封装
├── components/    # 公共组件
├── views/        # 页面组件
├── router/       # 路由配置
├── stores/       # 状态管理（Pinia）
└── utils/        # 工具函数
```

### 3.2 组件规范

1. 使用`<script setup>`语法
2. 组件名使用大驼峰，文件名与组件名一致
3. 样式使用scoped避免污染
4. 优先使用Element Plus组件

### 3.3 API封装规范

1. 统一使用axios封装
2. 请求拦截器处理Token
3. 响应拦截器统一处理错误
4. 按模块拆分API

**示例**：
```javascript
// api/index.js
const api = axios.create({
  baseURL: '/api/v1',
  timeout: 10000
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

export default {
  getAgents(params) {
    return api.get('/agents', { params })
  }
}
```

---

## 4. API设计规范

### 4.1 URL规范

- 格式：`/api/v1/{resource}/{action}`
- 使用名词复数形式：`/agents` 而非 `/agent`
- 动名词用于特定操作：`/agents/{id}/publish`

### 4.2 响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": 1713000000000
}
```

### 4.3 错误码

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 5. Git提交规范

### 5.1 提交信息格式

```
<type>: <subject>

<body>
```

### 5.2 Type类型

- feat: 新功能
- fix: 修复bug
- docs: 文档变更
- style: 代码格式（不影响功能）
- refactor: 重构
- test: 测试相关
- chore: 构建/工具变更

### 5.3 示例

```
feat: 添加Agent发布功能

- 支持Agent从草稿状态发布
- 发布前校验接口配置
- 自动创建1.0.0版本

Closes #123
```

---

## 6. 安全规范

1. **密码加密**：使用BCrypt加密存储
2. **敏感数据**：禁止在日志中打印密码、Token等
3. **SQL注入**：使用参数化查询，禁止字符串拼接SQL
4. **XSS**：后端对输入进行校验，前端对输出进行转义
5. **CSRF**：使用JWT Token认证

---

## 7. 代码审查规范

### 7.1 提交前自检清单

**每次提交代码前，必须检查以下内容：**

1. **编译检查**
   - [ ] 代码能正常编译，无语法错误
   - [ ] 所有import语句正确，无未使用的导入
   - [ ] 类名、方法名、变量名与文件名的对应关系正确

2. **引用检查**
   - [ ] 引用的类/方法/字段在目标位置存在
   - [ ] DTO/Entity字段与实际使用匹配
   - [ ] 已删除的文件不再被引用

3. **逻辑检查**
   - [ ] 方法参数和返回值类型正确
   - [ ] 业务逻辑完整，无明显缺陷

### 7.2 常见错误及避免

| 错误类型 | 示例 | 避免方法 |
|----------|------|----------|
| 类名与文件名不一致 | `WebConfig.java` 中定义 `class MyBatisConfig` | 确保public类名与文件名完全一致 |
| 引用已删除的类 | `import com.aipal.entity.Model` 但Model已被删除 | 删除文件时全局搜索引用并一并修改 |
| DTO字段缺失 | `request.getStatus()` 但status字段不存在 | 定义DTO时同步定义所有需要的字段 |
| 类型不匹配 | 方法返回`Agent`但实际返回`AiAgent` | 明确使用正确的类型 |

### 7.3 强制要求

- **禁止未经编译检查直接提交代码**
- **禁止提交存在编译错误的代码**
- **禁止提交引用了不存在类的代码**
- **所有代码变更必须经过自检后才能提交**
