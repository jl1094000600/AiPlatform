-- Diagnostic script: list existing tables that should have tenant_id but do not.
-- Run this in the current application database.

SELECT expected.table_name AS missing_tenant_id_table
FROM (
    SELECT 'sys_role' AS table_name UNION ALL
    SELECT 'sys_user_role' UNION ALL
    SELECT 'sys_role_permission' UNION ALL
    SELECT 'sys_audit_log' UNION ALL
    SELECT 'ai_a2a_task' UNION ALL
    SELECT 'ai_agent' UNION ALL
    SELECT 'ai_agent_graph_edge' UNION ALL
    SELECT 'ai_agent_heartbeat' UNION ALL
    SELECT 'ai_agent_quality_result' UNION ALL
    SELECT 'ai_agent_quality_run' UNION ALL
    SELECT 'ai_agent_registration' UNION ALL
    SELECT 'ai_agent_registration_event' UNION ALL
    SELECT 'ai_agent_runtime_config' UNION ALL
    SELECT 'ai_agent_version' UNION ALL
    SELECT 'ai_dataset' UNION ALL
    SELECT 'ai_evaluation' UNION ALL
    SELECT 'ai_evaluation_criteria' UNION ALL
    SELECT 'ai_model' UNION ALL
    SELECT 'ai_output_governance_record' UNION ALL
    SELECT 'ai_skill' UNION ALL
    SELECT 'ai_tts_config' UNION ALL
    SELECT 'ai_tts_task' UNION ALL
    SELECT 'ai_user_memory' UNION ALL
    SELECT 'ai_workflow' UNION ALL
    SELECT 'ai_workflow_execution' UNION ALL
    SELECT 'alert_event' UNION ALL
    SELECT 'alert_rule' UNION ALL
    SELECT 'automation_approval' UNION ALL
    SELECT 'automation_build_run' UNION ALL
    SELECT 'automation_code_requirement_feedback' UNION ALL
    SELECT 'automation_code_quality_issue' UNION ALL
    SELECT 'automation_code_quality_evidence' UNION ALL
    SELECT 'automation_code_quality_run' UNION ALL
    SELECT 'automation_deploy_profile' UNION ALL
    SELECT 'automation_deploy_run' UNION ALL
    SELECT 'automation_generated_code_batch' UNION ALL
    SELECT 'automation_generated_code_file' UNION ALL
    SELECT 'automation_generation_job' UNION ALL
    SELECT 'automation_pipeline' UNION ALL
    SELECT 'automation_report_snapshot' UNION ALL
    SELECT 'automation_stage_run' UNION ALL
    SELECT 'automation_test_run' UNION ALL
    SELECT 'billing_balance_transaction' UNION ALL
    SELECT 'billing_budget' UNION ALL
    SELECT 'billing_usage_daily' UNION ALL
    SELECT 'biz_agent_auth' UNION ALL
    SELECT 'biz_customer' UNION ALL
    SELECT 'biz_module' UNION ALL
    SELECT 'code_quality_rule' UNION ALL
    SELECT 'code_quality_standard' UNION ALL
    SELECT 'lowcode_invocation_record' UNION ALL
    SELECT 'mon_api_metrics' UNION ALL
    SELECT 'mon_call_record' UNION ALL
    SELECT 'prompt_engineering_eval_result' UNION ALL
    SELECT 'prompt_engineering_eval_run' UNION ALL
    SELECT 'prompt_engineering_optimize_run' UNION ALL
    SELECT 'prompt_engineering_prompt' UNION ALL
    SELECT 'prompt_engineering_test_case' UNION ALL
    SELECT 'prompt_engineering_version' UNION ALL
    SELECT 'rag_ingestion_record'
) expected
JOIN information_schema.tables t
  ON t.table_schema = DATABASE()
 AND t.table_name = expected.table_name
LEFT JOIN information_schema.columns c
  ON c.table_schema = DATABASE()
 AND c.table_name = expected.table_name
 AND c.column_name = 'tenant_id'
WHERE c.column_name IS NULL
ORDER BY expected.table_name;
