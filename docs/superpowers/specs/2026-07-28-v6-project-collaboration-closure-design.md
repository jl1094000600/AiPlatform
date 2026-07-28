# AI Platform v6 Project Collaboration Closure Design

**Date:** 2026-07-28
**Status:** Approved for implementation planning
**Scope:** v6 product closure only

## 1. Purpose

Complete the product boundary around Project War Room without rebuilding the
existing Automation Pipeline, Agent Runtime, Memory Center, governance, or
tenant RBAC systems.

The product must give project members three clearly separated collaboration
modes:

1. Shared project discussion coordinated by a read-only AI assistant.
2. Private, non-persistent questions about visible project information.
3. Explicit Team Agent execution that can change project state only after user
   confirmation.

The same delivery also makes project work assignment structured, auditable,
permission-controlled, and visible as personal work.

## 2. First-Principles Boundaries

- Reading project information and changing project state are different
  capabilities and must use different APIs and permissions.
- A model response is advice until a human explicitly turns it into a task
  draft or confirms execution.
- Shared project information is persistent and auditable; private questions
  are ephemeral by default.
- Assignment is a domain concept, not free text inside an Agent Run input or an
  approval comment.
- Project authorization must fail closed at every service boundary.
- Existing Runtime, pipeline, memory, model, audit, and tenant components are
  reused through stable interfaces.

## 3. Scope

### 3.1 Included

- Discussion Coordinator backed by the tenant's enabled default `CHAT` model.
- Shared project discussion history.
- Structured Coordinator output containing summary, risks, questions, and task
  draft suggestions.
- Private Ask backed by the same default `CHAT` model and safe project context.
- Explicit conversion of selected Private Ask content into a project task
  draft.
- Persistent task drafts with confirmation and cancellation lifecycle.
- Structured assignment for Agent Runs, approvals, and task drafts.
- Assignment, reassignment, acceptance, completion, and cancellation events.
- "My assignments" project and cross-project views.
- Dedicated project workspace permissions.
- War Room component decomposition and clear mode boundaries.
- Memory Center link back to the relevant Project War Room.

### 3.2 Excluded

- RAG hybrid chunking improvements.
- Real code scanning tool integration.
- Skill business data-source connectors.
- Spring AI 2.x Chat Memory migration.
- Agent marketplace work.
- Video and document requirement parsing.
- A new collaboration microservice.
- Autonomous multi-agent loops.
- Enabling the Agent Runtime dispatcher by default.

## 4. Architecture

The implementation remains inside the existing Spring Boot application and Vue
administration frontend.

### 4.1 Backend Components

`ProjectDiscussionService`

- Stores and lists shared project discussion messages.
- Calls the Coordinator with safe project context.
- Persists validated Coordinator replies and proposed task drafts.
- Never creates an Agent Run or modifies a pipeline.

`ProjectPrivateAskService`

- Builds safe project context for the current member.
- Calls the default `CHAT` model.
- Returns an answer, safe source references, and limitations.
- Does not persist the request, response, or current page conversation.

`ProjectTaskDraftService`

- Creates task drafts from Coordinator output, manual input, or an explicit
  Private Ask conversion.
- Owns the `DRAFT`, `CONFIRMED`, and `CANCELLED` lifecycle.
- Creates or reuses a Team Agent Run only when a user confirms a draft.

`ProjectAssignmentService`

- Creates and updates assignments for `AGENT_RUN`, `APPROVAL`, and
  `TASK_DRAFT`.
- Validates active project membership.
- Enforces assignment-management permission.
- Uses optimistic locking for reassignment.
- Appends immutable assignment events.

`ProjectWorkspaceContextService`

- Produces a business-safe context projection shared by Discussion Coordinator
  and Private Ask.
- Reuses existing War Room, membership, pipeline, Runtime, and approved-memory
  services.
- Excludes raw prompts, tool payloads, model reasoning, secret values, absolute
  paths, stack traces, and private memory bodies.

`ProjectWarRoomService`

- Remains the read aggregation boundary.
- Adds safe projections for recent shared discussion, task drafts, and
  assignments.
- Does not absorb write-oriented collaboration logic.

### 4.2 Frontend Components

- `ProjectOverviewPanel.vue`
- `ProjectDiscussionPanel.vue`
- `ProjectPrivateAskPanel.vue`
- `ProjectAssignmentPanel.vue`
- `ProjectTaskDraftCard.vue`

`ProjectWarRoom.vue` remains responsible only for project selection, layout,
route state, and coordination of panel-level loading.

## 5. Data Model

### 5.1 `project_discussion_message`

- `id`
- `tenant_id`
- `project_id`
- `project_key`
- `sender_user_id`
- `sender_type`: `USER`, `COORDINATOR`, or `SYSTEM`
- `message_type`: `TEXT`, `SUMMARY`, `RISK`, or `TASK_SUGGESTION`
- `content`
- `model_code`
- `idempotency_key`
- `create_time`
- `is_deleted`

The idempotency key is unique within a tenant and project for Coordinator
requests.

### 5.2 `project_task_draft`

- `id`
- `tenant_id`
- `project_id`
- `project_key`
- `source_type`: `DISCUSSION`, `PRIVATE_ASK`, or `MANUAL`
- `source_message_id`
- `title`
- `description`
- `business_type`
- `business_id`
- `suggested_agent_id`
- `status`: `DRAFT`, `CONFIRMED`, or `CANCELLED`
- `confirmed_run_id`
- `created_by`
- `confirmed_by`
- `create_time`
- `update_time`
- `version`
- `is_deleted`

Private Ask conversions leave `source_message_id` empty because the private
conversation is not persisted.

### 5.3 `project_assignment`

- `id`
- `tenant_id`
- `project_id`
- `project_key`
- `target_type`: `AGENT_RUN`, `APPROVAL`, or `TASK_DRAFT`
- `target_id`
- `assignee_user_id`
- `assigned_by`
- `status`: `ASSIGNED`, `ACCEPTED`, `COMPLETED`, or `CANCELLED`
- `due_time`
- `create_time`
- `update_time`
- `version`
- `is_deleted`

There is one active assignment per tenant, target type, and target ID.

### 5.4 `project_assignment_event`

- `id`
- `tenant_id`
- `project_id`
- `project_key`
- `assignment_id`
- `event_type`: `CREATED`, `REASSIGNED`, `ACCEPTED`, `COMPLETED`, or
  `CANCELLED`
- `from_user_id`
- `to_user_id`
- `actor_user_id`
- `reason`
- `create_time`

Events are append-only and are also represented in the existing audit log at
the application boundary.

## 6. Permissions

Add three permissions:

- `project-workspace:view`
- `project-workspace:execute`
- `project-workspace:manage`

Migration behavior:

- System administrators and project administrators receive all three.
- Existing active project members receive `project-workspace:view`.
- Users without explicit permission receive no compatibility fallback from
  `automation:list`.

Enforcement:

- Viewing War Room, shared discussion, and Private Ask requires
  `project-workspace:view` plus active project membership.
- Confirming a task draft or creating a Team Agent Run requires
  `project-workspace:execute` plus active project membership.
- Assigning or reassigning work requires `project-workspace:manage`, project
  manager authority, or the existing permission appropriate to the approval
  target.
- The assignee must be an active member of the same tenant and project.

## 7. API Surface

All endpoints are under `/api/v1/project-workspaces`.

### 7.1 Discussion

- `GET /{projectKey}/discussions`
- `POST /{projectKey}/discussions`
- `POST /{projectKey}/discussion-coordinator`

Coordinator responses use:

```json
{
  "summary": "string",
  "risks": ["string"],
  "questions": ["string"],
  "taskDrafts": [
    {
      "title": "string",
      "description": "string",
      "businessType": "string",
      "suggestedAgentId": 1
    }
  ]
}
```

The Coordinator reads at most the latest 50 shared messages and 20 project
events.

### 7.2 Private Ask

- `POST /{projectKey}/private-ask`
- `POST /{projectKey}/task-drafts`

Private Ask returns:

```json
{
  "answer": "string",
  "sources": [
    {
      "type": "PROJECT_SUMMARY",
      "reference": "string"
    }
  ],
  "limitations": ["string"]
}
```

Creating a task draft from Private Ask requires the client to submit the
selected title and description in a separate explicit request.

### 7.3 Task Drafts

- `GET /{projectKey}/task-drafts`
- `POST /{projectKey}/task-drafts`
- `POST /{projectKey}/task-drafts/{draftId}/confirm`
- `POST /{projectKey}/task-drafts/{draftId}/cancel`

Confirmation creates or reuses a Team Agent Run through the existing Runtime
and stores the returned Run ID on the draft.

### 7.4 Assignments

- `GET /{projectKey}/assignments`
- `PUT /{projectKey}/assignments/{assignmentId}/assignee`
- `POST /{projectKey}/assignments/{assignmentId}/accept`
- `POST /{projectKey}/assignments/{assignmentId}/complete`
- `GET /my-assignments`

Reassignment requests include the current `version`; a stale version returns a
conflict response containing the latest safe assignment view.

## 8. Model Context and Safety

Allowed model context:

- Project name, type, owner, and business-safe status.
- Business stage labels and public project timeline.
- Risk summaries and counts.
- Pending approval counts and safe titles.
- Approved memory summaries and references.
- Shared discussion for the current project.

Forbidden model context:

- Raw prompts and model reasoning.
- Agent Run raw input or output.
- Tool input, output, lease data, and internal execution payloads.
- Secrets, API keys, credentials, and encryption keys.
- Absolute file paths and storage roots.
- Internal stack traces.
- Private Ask content from any user or any previous page session.
- Unapproved raw memory content.

All model output is parsed into the documented response type and checked for
required fields, maximum lengths, and forbidden sensitive patterns before it
is returned or persisted.

## 9. State and Data Flow

```text
Shared discussion
  -> safe project context
  -> Discussion Coordinator
  -> validated reply and optional task drafts

Private Ask
  -> safe project context plus current page conversation
  -> private answer
  -> no persistence
  -> optional explicit task-draft conversion

Task draft
  -> human confirmation
  -> existing AgentRunService.createOrReuse
  -> assignment
  -> auditable project queue

Run / approval / task draft
  -> structured assignment
  -> immutable assignment event
  -> My Assignments view
```

## 10. Error Handling

- Missing enabled default `CHAT` model returns a model-not-configured business
  error.
- Model timeout or provider failure does not persist a Coordinator reply or
  task draft. The user's shared message remains available for retry.
- Duplicate Coordinator requests use an idempotency key and return the prior
  result.
- Project membership or permission changes fail closed on the next request.
- Cross-project and cross-tenant target IDs are rejected.
- Stale reassignment versions return HTTP conflict with the latest assignment
  view.
- Invalid structured model output is rejected rather than shown as a partial
  answer.
- Private Ask content is never placed in shared logs, project events, task
  records, or formal memory unless the user explicitly submits selected text as
  a new task draft.

## 11. User Experience

War Room exposes four clearly labeled areas:

1. Project Overview
2. Shared Discussion
3. Private Ask
4. My Assignments

Rules:

- Shared user messages, Coordinator responses, and system events have distinct
  visual treatment.
- Private Ask states that it is visible only to the current user and is not
  retained after the page session.
- Draft confirmation, assignment, and reassignment require explicit
  confirmation.
- Reassignment confirmation shows the old assignee, new assignee, and target.
- Permission failures, model configuration failures, and version conflicts
  provide a specific recovery action.
- Private Ask never appears in the shared timeline, Memory Center, or project
  discussion.

## 12. Delivery Plan

The implementation target is six development days.

| Milestone | Duration | Deliverable |
| --- | ---: | --- |
| M0 Design and plan | 0.5 day | Approved design, implementation plan, acceptance matrix |
| M1 Permission and data foundation | 1 day | Permissions, four tables, migration, entities |
| M2 Structured assignment | 1 day | Assignment, reassignment, audit, My Assignments |
| M3 Discussion and Private Ask | 1.5 days | Discussion, Coordinator, ephemeral Private Ask, draft conversion |
| M4 War Room frontend | 1.5 days | Four product areas and assignment interaction |
| M5 Integration closure | 0.5 day | API contracts, migration entry, documentation, final review |

## 13. Supervision and Task Governance

Project manager:

- Owns the implementation ledger, dependencies, blockers, daily state, and the
  next three verifiable commitments.
- Reports each milestone as Green, Yellow, or Red.
- Does not modify business code.

Product manager:

- Checks user value, wording, mode separation, privacy, and acceptance criteria
  at every milestone.
- Rejects work that blurs Shared Discussion, Private Ask, and Team Agent.
- Does not modify business code.

Development:

- Each implementation task is owned by a fresh development agent.
- Each task uses a failing test before implementation and produces an isolated
  commit.
- A reviewer checks specification compliance and code quality before the next
  task begins.
- Important findings return to the original developer for correction.

Milestone gates:

- M1 migrations are idempotent and independently reviewable.
- M2 rejects unauthorized, cross-project, and cross-tenant assignment changes.
- M3 demonstrates that Private Ask has no persistence and Coordinator causes no
  project-state mutation.
- M4 makes the three collaboration modes visually and operationally distinct.
- M5 maps every approved requirement to implementation evidence.

## 14. Acceptance Criteria

- A project member with view permission can read shared discussion and use
  Private Ask for an accessible project.
- Discussion Coordinator produces only validated summaries, risks, questions,
  and task draft suggestions.
- Private Ask leaves no database, project-event, formal-memory, or shared-log
  record of its content.
- A user can explicitly convert selected Private Ask content into a task draft.
- Confirming a task draft is the only draft action that creates or reuses a
  Team Agent Run.
- Assignments are persistent, membership-validated, versioned, and auditable.
- Authorized users can reassign work; unauthorized users cannot.
- Users can list their assignments across accessible projects.
- The War Room uses dedicated project workspace permissions.
- The existing Automation Pipeline, Agent Run console, and Memory Center remain
  available and link consistently to the Project War Room.
- The dispatcher remains default-off.
