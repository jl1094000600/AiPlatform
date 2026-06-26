package com.aipal.mapper;
import com.aipal.entity.AgentTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTask> {

    /**
     * MySQL 8 SKIP LOCKED lets concurrent workers reserve different tasks without waiting on
     * the same queue head. This method must always be called inside a transaction.
     */
    @Select("""
            SELECT * FROM agent_task FORCE INDEX (idx_agent_task_claim_tenant)
            WHERE tenant_id = #{tenantId}
              AND is_deleted = 0
              AND status = 'QUEUED'
              AND task_type = 'RUN'
              AND parent_task_id IS NULL
              AND available_at <= NOW()
            ORDER BY available_at ASC, id ASC
            LIMIT 1 FOR UPDATE SKIP LOCKED
            """)
    AgentTask selectNextClaimableForUpdate(@Param("tenantId") Long tenantId);
}
