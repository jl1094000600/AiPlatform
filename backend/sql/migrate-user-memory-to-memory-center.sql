-- Idempotent legacy migration. Legacy summaries are retained for review only and
-- are never eligible for recall until an operator approves them.

INSERT INTO ai_memory_item (
    tenant_id, memory_code, memory_type, scope_type, scope_key,
    owner_user_id, owner_username, title, content, source_type, source_ref,
    legacy_memory_id, sensitivity, importance, confidence, status, version,
    valid_from, created_by, create_time, update_time, is_deleted
)
SELECT
    legacy.tenant_id,
    CONCAT('LEGACY_MEM_', legacy.tenant_id, '_', legacy.id),
    'PIPELINE_SUMMARY',
    CASE WHEN legacy.user_id IS NOT NULL THEN 'USER' ELSE 'TENANT' END,
    CASE WHEN legacy.user_id IS NOT NULL THEN CONCAT('user:', legacy.user_id) ELSE CONCAT('tenant:', legacy.tenant_id) END,
    legacy.user_id,
    legacy.username,
    CONCAT('历史流水线记忆 ', legacy.memory_code),
    legacy.summary_content,
    'LEGACY',
    legacy.memory_code,
    legacy.id,
    'INTERNAL',
    50,
    0.6000,
    'PENDING_REVIEW',
    1,
    legacy.memory_start_time,
    'legacy-migration',
    legacy.create_time,
    legacy.update_time,
    0
FROM ai_user_memory legacy
WHERE legacy.is_deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM ai_memory_item migrated
      WHERE migrated.tenant_id = legacy.tenant_id
        AND migrated.legacy_memory_id = legacy.id
        AND migrated.is_deleted = 0
  );
