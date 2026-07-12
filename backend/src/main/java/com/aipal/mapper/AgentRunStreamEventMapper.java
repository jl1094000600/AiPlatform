package com.aipal.mapper;

import com.aipal.entity.AgentRunStreamEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentRunStreamEventMapper extends BaseMapper<AgentRunStreamEvent> {
    @Select("SELECT COALESCE(MAX(sequence_no), 0) FROM agent_run_stream_event "
            + "WHERE tenant_id = #{tenantId} AND run_id = #{runId} AND is_deleted = 0")
    Long selectMaxSequenceNo(@Param("tenantId") Long tenantId, @Param("runId") Long runId);
}
