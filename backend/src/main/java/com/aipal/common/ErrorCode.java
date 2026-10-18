package com.aipal.common;

/**
 * Central catalog of business error codes and client-facing messages.
 * Use {@code ErrorCode.XXX.ex()} or {@code ErrorCode.XXX.ex(args)} instead of
 * ad-hoc {@code new BizException(code, "...")} literals.
 */
public enum ErrorCode {

    // ---------- 400 Bad Request ----------
    PROJECT_KEY_NAME_REQUIRED(400, "项目键和项目名称不能为空"),
    PROJECT_KEY_REQUIRED(400, "项目键不能为空"),
    MEMBER_USER_REQUIRED(400, "成员用户不能为空"),
    ASSIGNEE_NOT_ACTIVE_MEMBER(400, "被指派人不是当前项目的有效成员"),
    ASSIGN_TARGET_INVALID(400, "指派目标类型或编号无效"),
    REASSIGN_REQUEST_REQUIRED(400, "改派请求不能为空"),
    ASSIGN_ACTION_REQUEST_REQUIRED(400, "指派操作请求不能为空"),
    ASSIGNMENT_ID_INVALID(400, "指派编号无效"),
    ASSIGN_TARGET_SCOPE_MISMATCH(400, "指派目标不属于当前租户和项目"),
    ASSIGN_REASON_TOO_LONG(400, "指派原因不能超过 500 字符"),
    EXPECTED_VERSION_REQUIRED(400, "expectedVersion 必须为非负整数"),
    TEAM_RUN_FIELDS_REQUIRED(400, "agentId, businessType and businessId are required"),
    RUN_CREATE_FIELDS_REQUIRED(400, "agentId、projectKey、businessType、businessId不能为空"),
    AGENT_NOT_AVAILABLE(400, "Agent不存在或未上线"),
    AGENT_NO_PUBLISHED_VERSION(400, "Agent没有已发布版本"),
    AGENT_INPUT_TOO_LARGE(400, "Agent input exceeds 64KB limit"),
    AGENT_INPUT_NOT_SERIALIZABLE(400, "Agent input cannot be serialized"),
    INVALID_PAGE_RANGE(400, "Invalid page range"),
    INVALID_RUN_STATUS(400, "Invalid run status"),
    CALL_ID_REQUIRED(400, "callId is required"),
    ANSWER_REQUIRED(400, "answer is required"),
    TASK_TYPE_TOO_LONG(400, "taskType too long"),
    INVALID_TASK_STATUS(400, "Invalid task status"),
    TASK_PAYLOAD_NOT_SERIALIZABLE(400, "Task payload cannot be serialized"),
    TASK_VIOLATION(400, "%s"),
    TOOL_NAME_REQUIRED(400, "toolName is required"),
    TOOL_INPUT_NOT_OBJECT(400, "Tool input must be an object"),
    TOOL_INPUT_MISSING_FIELD(400, "Missing required tool input: %s"),
    TOOL_INPUT_FIELD_MUST_BE_STRING(400, "Tool input field must be string: %s"),
    TOOL_INPUT_FIELD_MUST_BE_OBJECT(400, "Tool input field must be object: %s"),
    TOOL_INPUT_FIELD_MUST_BE_ARRAY(400, "Tool input field must be array: %s"),
    TOOL_INPUT_FIELD_MUST_BE_INTEGER(400, "Tool input field must be integer: %s"),
    TOOL_INPUT_FIELD_BELOW_MIN(400, "Tool input field below minimum: %s"),
    TOOL_INPUT_FIELD_ABOVE_MAX(400, "Tool input field above maximum: %s"),

    // ---------- 403 Forbidden ----------
    PERMISSION_DENIED(403, "无权限访问：%s"),
    USER_CONTEXT_REQUIRED(403, "User context is required"),
    PLATFORM_ADMIN_REQUIRED(403, "Platform administrator privileges are required"),
    CROSS_TENANT_ADMIN_REQUIRED(403, "Cross-tenant administration requires platform administrator privileges"),
    SYSTEM_TASK_CANNOT_CREATE_PROJECT(403, "系统任务不能创建项目"),
    MEMORY_ACCESS_DENIED(403, "无权访问该记忆"),
    MEMORY_MODIFY_DENIED(403, "只能修改自己的记忆"),
    MEMORY_TRACE_ACCESS_DENIED(403, "无权查看该记忆调用记录"),
    MEMORY_OWNER_ONLY(403, "只能访问当前用户的记忆"),
    MEMORY_PROJECT_ACCESS_DENIED(403, "无权访问项目记忆"),
    PROJECT_MEMBER_REQUIRED(403, "不是该项目成员"),
    PROJECT_ACTIVE_MEMBER_REQUIRED(403, "不是该项目的有效成员"),
    PROJECT_MANAGE_DENIED(403, "无权管理项目成员"),
    ASSIGN_MANAGER_REQUIRED(403, "只有项目负责人、项目管理员或具备项目管理权限的用户可以指派"),
    ASSIGN_ASSIGNEE_ONLY(403, "只有被指派人可以接受或完成指派"),
    RUN_CANCEL_DENIED(403, "Only the run owner or project manager can cancel this run"),
    RUN_APPROVE_DENIED(403, "Only the run owner or project manager can approve this run"),

    WORKFLOW_ID_INVALID(400, "编排编号无效"),
    WORKFLOW_DEFINITION_REQUIRED(400, "workflowDefinition is required"),
    WORKFLOW_TRY_RUN_NO_DEFINITION(400, "试运行需要已发布版本或传入 definitionJson"),
    WORKFLOW_TRY_RUN_INPUTS_INVALID(400, "试运行 inputs 无效"),

    // ---------- 404 Not Found ----------
    MEMORY_TRACE_NOT_FOUND(404, "记忆调用记录不存在"),
    MEMORY_NOT_FOUND(404, "记忆不存在"),
    PROJECT_NOT_FOUND(404, "项目不存在或已停用"),
    ASSIGNMENT_NOT_FOUND(404, "指派不存在"),
    AGENT_RUN_NOT_FOUND(404, "Agent Run不存在"),
    TOOL_CALL_NOT_FOUND(404, "Tool call not found"),
    TASK_NOT_FOUND(404, "Task not found"),
    AGENT_TOOL_NOT_FOUND(404, "Agent tool not found or disabled"),
    WORKFLOW_NOT_FOUND(404, "编排不存在或已停用"),
    WORKFLOW_VERSION_NOT_FOUND(404, "工作流版本不存在"),
    WORKFLOW_EXECUTION_NOT_FOUND(404, "编排执行不存在"),

    // ---------- 409 Conflict ----------
    PROJECT_KEY_EXISTS(409, "项目键已存在"),
    ASSIGN_TARGET_CHANGED(409, "指派目标已变更，请刷新后重试"),
    ASSIGN_STATUS_CHANGED(409, "指派状态已变更，请刷新后重试"),
    ASSIGN_VERSION_EXPIRED(409, "指派版本已过期，请刷新后重试"),
    ASSIGNMENT_ENDED(409, "已结束的指派不能改派"),
    ASSIGN_HAS_OTHER_ASSIGNEE(409, "该目标已有其他处理人，请刷新后使用改派操作"),
    RUN_CREATE_CONFLICT(409, "并发创建Run冲突，请重试"),
    RUN_NOT_WAITING_APPROVAL(409, "Run is not waiting for approval"),
    TOOL_CALL_NOT_ASK_USER(409, "Tool call is not an ask_user call"),
    TOOL_CALL_NOT_WAITING_INPUT(409, "Tool call is not waiting for user input"),
    ASK_USER_ALREADY_TIMED_OUT(409, "ask_user already timed out"),
    ASK_USER_ALREADY_CANCELLED(409, "ask_user already cancelled"),
    ASK_USER_NOT_WAITING_INPUT(409, "ask_user is not waiting for user input"),
    TASK_ALREADY_CLOSED(409, "Task is already closed"),
    WORKFLOW_VERSION_NOT_ACTIVE(409, "工作流版本已停用，请刷新后重试"),

    // ---------- 400/409/502/503 Collaboration M3 ----------
    DISCUSSION_CONTENT_REQUIRED(400, "讨论内容不能为空"),
    DISCUSSION_CONTENT_TOO_LONG(400, "讨论内容不能超过 4000 字"),
    DISCUSSION_LIMIT_INVALID(400, "讨论数量限制必须在 1-50 之间"),
    DISCUSSION_IDEMPOTENCY_CONFLICT(409, "相同幂等键的讨论内容不一致"),
    PRIVATE_ASK_QUESTION_REQUIRED(400, "私密问答内容不能为空"),
    PRIVATE_ASK_HISTORY_INVALID(400, "私密问答历史最多 20 轮，每条 1-4000 字"),
    TASK_DRAFT_TITLE_REQUIRED(400, "任务草稿标题不能为空"),
    TASK_DRAFT_TITLE_TOO_LONG(400, "任务草稿标题不能超过 255 字"),
    TASK_DRAFT_DESCRIPTION_TOO_LONG(400, "任务草稿描述不能超过 4000 字"),
    TASK_DRAFT_SOURCE_INVALID(400, "任务草稿来源类型无效"),
    TASK_DRAFT_AGENT_REQUIRED(400, "确认任务草稿必须选择 Agent"),
    TASK_DRAFT_NOT_FOUND(404, "任务草稿不存在"),
    TASK_DRAFT_STATUS_CHANGED(409, "任务草稿状态已变更，请刷新后重试"),
    TASK_DRAFT_VERSION_EXPIRED(409, "任务草稿版本已过期，请刷新后重试"),
    TASK_DRAFT_CONFIRM_CONFLICT(409, "任务草稿确认冲突，请刷新后重试"),
    TASK_DRAFT_IDEMPOTENCY_CONFLICT(409, "相同幂等键的任务草稿内容不一致"),
    IDEMPOTENCY_KEY_TOO_LONG(400, "idempotencyKey 不能超过 128 字符"),
    PRIVATE_ASK_QUESTION_TOO_LONG(400, "私密问答内容不能超过 4000 字"),
    MODEL_NOT_CONFIGURED(503, "当前租户未配置可用的默认 CHAT 模型"),
    MODEL_RESPONSE_INVALID(502, "模型返回内容不符合约定"),
    MODEL_CALL_FAILED(502, "模型调用失败，请稍后重试"),

    // ---------- 500 Internal ----------
    AGENT_SNAPSHOT_FREEZE_FAILED(500, "无法冻结Agent定义快照"),
    TOOL_INPUT_SCHEMA_INVALID(500, "Tool input schema is invalid"),
    RUN_STREAM_EVENT_UNREADABLE(500, "Run stream event payload cannot be read");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getMessage(Object... args) {
        return args == null || args.length == 0 ? message : String.format(message, args);
    }

    /** Exception with the catalog message. */
    public BizException ex() {
        return new BizException(code, message);
    }

    /** Exception with formatted message ({@code %s} placeholders). */
    public BizException ex(Object... args) {
        return new BizException(code, getMessage(args));
    }
}
