package com.aipal.agent.image.controller;

import com.aipal.agent.image.dto.ImageRecognitionRequest;
import com.aipal.agent.image.dto.ImageRecognitionResponse;
import com.aipal.agent.image.entity.ImageRecognitionTask;
import com.aipal.agent.image.service.AgentService;
import com.aipal.agent.image.service.HeartbeatService;
import com.aipal.agent.image.service.ImageRecognitionService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/image-agent")
@RequiredArgsConstructor
public class ImageAgentController {

    private final ImageRecognitionService imageRecognitionService;
    private final HeartbeatService heartbeatService;
    private final AgentService agentService;

    @PostConstruct
    public void init() {
        agentService.registerAgent();
    }

    @PostMapping("/recognize")
    public ImageRecognitionResponse recognize(@Valid @RequestBody ImageRecognitionRequest request) {
        log.info("Received image recognition request, inputType: {}", request.getInputType());
        return imageRecognitionService.processRequest(request);
    }

    @GetMapping("/task/{taskId}")
    public ImageRecognitionResponse getTask(@PathVariable String taskId) {
        ImageRecognitionTask task = imageRecognitionService.getTask(taskId);
        if (task == null) {
            return ImageRecognitionResponse.builder()
                    .status(3)
                    .message("Task not found")
                    .build();
        }
        return ImageRecognitionResponse.builder()
                .taskId(task.getTaskId())
                .status(task.getStatus())
                .message(task.getStatus() == 2 ? "处理成功" : task.getStatus() == 3 ? "处理失败" : "处理中")
                .result(task.getResult())
                .fileType(task.getFileType())
                .build();
    }

    @GetMapping("/health")
    public Object health() {
        return java.util.Map.of(
                "status", "UP",
                "agent", "image-recognition-agent",
                "instanceId", heartbeatService.getInstanceId()
        );
    }
}
