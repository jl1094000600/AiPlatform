# AI Platform v6.0.0 Phase 1 Project War Room and Team Agent Design

## 1. Context

AI Platform already has Automation Pipeline, Agent Runtime, Memory Center, approval, quality, deployment, governance, and tenant RBAC foundations. v6.0.0 Phase 1 should not rebuild these systems. It should expose them as a project-level war room where business, product, project, development, testing, and operations roles can understand one project through a shared delivery view.

The approved delivery scope is **M1 + M2 together**:

- M1: project war room entry and business-facing aggregation.
- M2: Team Agent queue integration, explicit Run creation, queue visibility, and control actions.

The old automation pipeline page and Agent Run console stay available as technical and advanced views.

## 2. Goals

- Give project members one page to see current project progress, queue status, pending confirmations, artifacts, memory references, and blockers.
- Make Team Agent execution explicit, auditable, cancellable, and visible in the project queue.
- Keep Discussion Coordinator, Private Ask, and Team Agent clearly separated.
- Reuse existing Runtime, Automation Pipeline, Memory Center, and governance data.
- Keep dispatcher default-off until integration tests and release gates approve pilot enablement.
- Track development progress with project-manager and product-manager checkpoints during implementation.

## 3. Non-Goals

- Do not copy CodeBanana cloud IDE, multi-user code editor, or full DevBox.
- Do not create multiple always-on business Agent microservices.
- Do not enable complex autonomous subagent loops in Phase 1.
- Do not let ordinary discussion create Runs.
- Do not expose raw prompts, task payloads, lease details, storage paths, raw memory bodies, or internal stack traces in the business view.
- Do not delete or deeply refactor the existing automation pipeline or Agent Run console.

## 4. Product Model

### 4.1 Project Workspace

`ai_memory_project` remains the project workspace source of truth for Phase 1. The product surface calls it Project Workspace, but the database does not need a new `project_workspace` table yet.

Each workspace is identified by `projectKey`. Phase 1 should add `project_key` to `automation_pipeline` so pipelines, Agent Runs, memory projects, traces, and artifacts can be reliably aggregated.

### 4.2 Three Agent Modes

| Mode | Purpose | Can change project state | Phase 1 behavior |
| --- | --- | --- | --- |
| Discussion Coordinator | Summarize project discussion, identify risks, propose task drafts | No | Minimal placeholder or draft-only flow |
| Private Ask | Read-only personal Q&A about visible project information | No | No writes to project state, discussion, or formal memory |
| Team Agent | Controlled project executor | Yes | Creates or reuses Agent Run only after explicit user confirmation |

Ordinary discussion never creates a Run. The user must click a Team Agent action and confirm before the backend creates or reuses a Run.

## 5. Backend Design

### 5.1 New API Surface

Add `ProjectWarRoomController`, `ProjectWarRoomService`, and DTOs.

Endpoints:

- `GET /api/v1/project-workspaces`
  - Returns current user's accessible project workspaces.
  - Wraps existing Memory Project access rules.

- `GET /api/v1/project-workspaces/{projectKey}/war-room`
  - Returns project overview, pipelines, Team Agent queue, recent events, pending artifacts, memory references, and risk cards.
  - Verifies project membership through `MemoryProjectService.requireAccessibleProject`.

- `POST /api/v1/project-workspaces/{projectKey}/team-agent-runs`
  - Explicit Team Agent execution entry.
  - Reuses `AgentRunService.createOrReuse`.
  - Requires `agentId`, `businessType`, `businessId`, and `input`.
  - Uses `projectKey` from the path, not from client-supplied body.

- `GET /api/v1/project-workspaces/{projectKey}/mentionable-users`
  - Returns project members that can be mentioned or assigned.
  - Phase 1 may return project members only. If node assignment is implemented in the same milestone, it must also enforce node-role policy.

### 5.2 Aggregation Rules

The war room response should include only business-safe projections:

- Project: `projectKey`, `projectName`, `projectType`, owner, status.
- Pipelines: code, title, current stage, business stage label, status, progress, pending approvals.
- Team Agent queue: Run id, business type, business id, status, owner, create/start/end time, can cancel, can approve.
- Timeline: merged business events from Run events and pipeline stages.
- Pending artifacts: safe artifact titles, type, status, update time.
- Memory: memory codes, scope, source type, version, token count, trace id, and non-sensitive summary.
- Risks: queue backlog, failed Runs, rejected approvals, blocked stages, timeout or token-budget failures.

Raw fields that must not be returned:

- `AgentRun.inputJson`, `AgentRun.resultJson`, definition snapshot bodies, task payloads, tool payloads.
- Raw prompt text, model reasoning, stack traces, lease owner details.
- `AutomationStageRun.artifactContent` in war room overview.
- Raw memory content beyond already approved safe summary.

### 5.3 Project Key Migration

Add `project_key` to `automation_pipeline` plus an index:

- `project_key VARCHAR(128) DEFAULT NULL`
- `KEY idx_automation_pipeline_project_key (tenant_id, project_key, create_time)`

When creating a pipeline, derive `projectKey` from the request if present; otherwise normalize the project name. For existing rows, a backfill can use normalized `projectName` as a compatibility value.

The DTO for pipeline creation can accept `projectKey` without making it mandatory for old clients.

### 5.4 Team Agent Run Creation

`POST /team-agent-runs` should:

1. Verify project membership.
2. Validate `agentId`, `businessType`, and `businessId`.
3. Build a command for `AgentRunService.createOrReuse`.
4. Record or return the existing Run when idempotency matches.
5. Return the business-safe Run view.

Dispatcher remains controlled by existing configuration and remains default-off.

## 6. Frontend Design

### 6.1 New Page

Add `front/src/views/ProjectWarRoom.vue`.

Route:

- `/project-workspaces/:projectKey`
- Permission for Phase 1: reuse `automation:list` or `memory:list`.
- Later permission: `project-workspace:view`.

### 6.2 Layout

Use a dense operational layout, not a marketing page.

- Left rail: project selector, project facts, recent pipelines or requirements.
- Center: discussion placeholder, Team Agent action panel, business timeline.
- Right rail: Team Agent queue, pending confirmations, risk and blocker cards.
- Drawer or lower section: technical links to automation detail, Agent Run console, memory trace, code quality, and governance records.

### 6.3 Entry Points

Add links into the war room from:

- `AutomationPipeline.vue`: each pipeline row/detail can enter the project war room.
- `AgentRunConsole.vue`: `projectKey` can navigate to the war room.
- Later: `UserMemoryManagement.vue` can link memory trace or project memory back to the war room.

### 6.4 Team Agent UX

The Team Agent action form must show a confirmation dialog before calling the backend.

The confirmation copy should make the state change explicit:

- It will create or reuse an auditable Team Agent Run.
- It will enter the project queue.
- It can produce artifacts requiring approval.
- It is different from discussion or Private Ask.

## 7. Progress Control

The project manager controls development using this daily status format:

- Milestone: M1, M2, or gate.
- State: Green, Yellow, or Red.
- Completed: concrete interface, page, test, or verification evidence.
- Blocker: owner, reason, and next action.
- Next commitment: no more than three verifiable tasks.

Product manager checks at each milestone:

- Business user can tell what is happening, who initiated it, where it is blocked, and who confirms next.
- Discussion, Private Ask, and Team Agent boundaries remain visible.
- Business view does not leak technical or sensitive fields.
- Generated artifacts and memory candidates do not become final without confirmation.

## 8. Testing Strategy

Backend tests:

- Project war room list respects tenant and project membership.
- War room overview excludes raw prompt, payload, artifact body, raw memory content, and stack traces.
- Team Agent Run creation uses path `projectKey`, is idempotent, and rejects non-members.
- Automation pipeline creation stores `projectKey` and remains compatible with existing clients.

Frontend tests or build checks:

- War room route builds.
- Empty state and forbidden state render cleanly.
- Team Agent action requires confirmation.
- Business stage labels display instead of raw technical stage keys in the default view.

Existing regression checks:

- `mvn -q -Dtest=AgentRunExecutionServiceTest,AgentTaskServiceTest,AgentRunTimeoutServiceTest,AutomationPipelineServiceTest test`
- `npm run build`
- `npm run test:business`

## 9. Milestone Exit Criteria

### M1 Exit

- Project war room route exists.
- War room API returns project overview, pipelines, queue, timeline, artifacts, memory summary, and risks.
- Business view hides sensitive technical fields.
- Automation and Run console link into the war room.

### M2 Exit

- Team Agent execution is explicit and confirmed.
- Backend creates or reuses Agent Run through existing Runtime.
- Queue shows Run state and pending approval state.
- Project manager or authorized user can follow links to cancel or approve via existing Run controls.
- Dispatcher is still default-off unless release gates approve pilot enablement.

## 10. Risks

- Project identity can split if `automation_pipeline.project_key` is not added early.
- Building a new execution model would duplicate existing Runtime and delay delivery.
- Mention and node assignment have deeper permission implications; keep Phase 1 minimal unless backend validation and event audit are included.
- Chroma projection reliability remains a release-gate topic; business memory access must rely on MySQL permission recheck.
- UI may become too technical if raw pipeline details are surfaced by default. Keep business summary first and technical links secondary.
