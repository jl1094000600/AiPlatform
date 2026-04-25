package com.aipal.service;

import com.aipal.entity.AiTtsTask;
import com.aipal.mapper.AiTtsTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TtsTaskService {

    private final AiTtsTaskMapper ttsTaskMapper;

    public AiTtsTask createTask(AiTtsTask task) {
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        ttsTaskMapper.insert(task);
        return task;
    }

    public boolean updateTaskStatus(String taskId, String status, String errorMessage) {
        AiTtsTask task = getTaskByTaskId(taskId);
        if (task != null) {
            task.setStatus(status);
            task.setErrorMessage(errorMessage);
            task.setUpdateTime(LocalDateTime.now());
            return ttsTaskMapper.updateById(task) > 0;
        }
        return false;
    }

    public boolean updateTaskSuccess(String taskId, String audioUrl, Integer duration) {
        AiTtsTask task = getTaskByTaskId(taskId);
        if (task != null) {
            task.setStatus("success");
            task.setAudioUrl(audioUrl);
            task.setDuration(duration != null ? duration.floatValue() : null);
            task.setUpdateTime(LocalDateTime.now());
            return ttsTaskMapper.updateById(task) > 0;
        }
        return false;
    }

    public AiTtsTask getTaskByTaskId(String taskId) {
        return ttsTaskMapper.selectOne(
            new LambdaQueryWrapper<AiTtsTask>()
                .eq(AiTtsTask::getTaskId, taskId)
        );
    }

    public List<AiTtsTask> getTasksByAgentCode(String agentCode) {
        return ttsTaskMapper.selectList(
            new LambdaQueryWrapper<AiTtsTask>()
                .eq(AiTtsTask::getAgentCode, agentCode)
                .orderByDesc(AiTtsTask::getCreateTime)
                .last("LIMIT 100")
        );
    }

    public List<AiTtsTask> getTasksByStatus(String status) {
        return ttsTaskMapper.selectList(
            new LambdaQueryWrapper<AiTtsTask>()
                .eq(AiTtsTask::getStatus, status)
                .orderByDesc(AiTtsTask::getCreateTime)
        );
    }
}
