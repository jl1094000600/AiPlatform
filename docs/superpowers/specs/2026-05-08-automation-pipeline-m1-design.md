# Automation Pipeline M1 Design

## Scope
M1 delivers an internal AI-led delivery pipeline with human review. It covers requirement parsing, code generation, build, test, deployment, operations monitoring, and final report stages.

## Roles
- Business owner validates requirement value and delivery closure.
- Product manager owns structured requirements and acceptance criteria.
- Project manager tracks stage progress, blockers, and SLA.
- Architect reviews technical approach and deployment risk.
- Developers, testers, and operations reviewers handle stage approvals.

## Product Behavior
Users create a pipeline from a product line, project name, requirement title, and requirement summary. The platform creates the standard stage list. Each stage can be run by AI, enter a waiting-review state, and be approved or rejected by a human reviewer. Reports show total pipelines, running work, completed work, blocked work, approval queue size, and stage pass rate.

## Architecture
Backend owns a deterministic state machine and writes all stage transitions to MySQL. The first version simulates AI execution output per stage and records model hints, inputs, outputs, duration, and errors. Real CI/CD and model-agent integration can be attached later without changing the UI contract.

## APIs
- `GET /api/v1/automation/pipelines`
- `POST /api/v1/automation/pipelines`
- `GET /api/v1/automation/pipelines/{id}`
- `POST /api/v1/automation/stages/{stageId}/run`
- `GET /api/v1/automation/approvals`
- `POST /api/v1/automation/approvals/{approvalId}/approve`
- `GET /api/v1/automation/reports/summary`

## Testing
Backend tests cover pipeline creation, stage initialization, stage execution, and approval status changes. Frontend tests cover the route, summary math, and expected pipeline stages. Redis is not a blocker.
