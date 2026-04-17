package com.aipal.agent.image.service;

import cn.hutool.core.util.IdUtil;
import com.aipal.agent.image.config.AgentConfig;
import com.aipal.agent.image.dto.ImageRecognitionRequest;
import com.aipal.agent.image.dto.ImageRecognitionResponse;
import com.aipal.agent.image.entity.ImageRecognitionTask;
import com.aipal.agent.image.mapper.ImageRecognitionTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.MediaModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageRecognitionService {

    private final FileParseService fileParseService;
    private final ImageRecognitionTaskMapper taskMapper;
    private final AgentConfig agentConfig;

    @Value("${spring-ai.openai.api-key:}")
    private String openaiApiKey;

    public ImageRecognitionResponse processRequest(ImageRecognitionRequest request) {
        String taskId = IdUtil.fastSimpleUUID();
        long startTime = System.currentTimeMillis();

        ImageRecognitionTask task = new ImageRecognitionTask();
        task.setTaskId(taskId);
        task.setInputType(request.getInputType());
        task.setInputData(request.getInputData());
        task.setCreateTime(LocalDateTime.now());
        task.setStatus(1);
        taskMapper.insert(task);

        try {
            Object result;
            String fileType = detectFileType(request.getInputData(), request.getInputType());

            if (isImageFile(fileType)) {
                result = recognizeImage(request.getInputData(), request.getInputType());
            } else {
                result = fileParseService.parseFile(request.getInputData(), request.getInputType(), fileType);
            }

            task.setStatus(2);
            task.setResult(String.valueOf(result));
            task.setFileType(fileType);
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);

            long processTime = System.currentTimeMillis() - startTime;

            return ImageRecognitionResponse.builder()
                    .taskId(taskId)
                    .status(2)
                    .message("处理成功")
                    .result(result)
                    .fileType(fileType)
                    .processTime(processTime)
                    .build();

        } catch (Exception e) {
            log.error("Image recognition failed for task: {}", taskId, e);
            task.setStatus(3);
            task.setErrorMessage(e.getMessage());
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);

            return ImageRecognitionResponse.builder()
                    .taskId(taskId)
                    .status(3)
                    .message("处理失败: " + e.getMessage())
                    .build();
        }
    }

    private String detectFileType(String inputData, String inputType) {
        if ("url".equalsIgnoreCase(inputType)) {
            String lowerData = inputData.toLowerCase();
            if (lowerData.contains(".jpg") || lowerData.contains(".jpeg") || lowerData.contains(".png")
                    || lowerData.contains(".gif") || lowerData.contains(".bmp") || lowerData.contains(".webp")) {
                return extractFileExtension(lowerData);
            }
        } else if ("base64".equalsIgnoreCase(inputType)) {
            if (inputData.contains(",")) {
                String header = inputData.split(",")[0];
                if (header.contains("image/")) {
                    return header.substring(header.indexOf("image/") + 6).split(";")[0];
                }
            }
        }
        return "unknown";
    }

    private String extractFileExtension(String url) {
        if (url.contains(".jpg")) return "jpg";
        if (url.contains(".jpeg")) return "jpeg";
        if (url.contains(".png")) return "png";
        if (url.contains(".gif")) return "gif";
        if (url.contains(".bmp")) return "bmp";
        if (url.contains(".webp")) return "webp";
        return "unknown";
    }

    private boolean isImageFile(String fileType) {
        return "jpg".equalsIgnoreCase(fileType) ||
                "jpeg".equalsIgnoreCase(fileType) ||
                "png".equalsIgnoreCase(fileType) ||
                "gif".equalsIgnoreCase(fileType) ||
                "bmp".equalsIgnoreCase(fileType) ||
                "webp".equalsIgnoreCase(fileType);
    }

    private String recognizeImage(String inputData, String inputType) {
        String imageUrl = null;
        String base64Data = null;

        if ("url".equalsIgnoreCase(inputType)) {
            imageUrl = inputData;
        } else if ("base64".equalsIgnoreCase(inputType)) {
            base64Data = inputData;
        }

        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(openaiApiKey)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .build();

        MediaModel mediaModel = MediaModel.builder()
                .chatModel(chatModel)
                .build();

        ChatClient chatClient = ChatClient.builder(chatModel).build();

        String prompt = "请描述这张图片的内容，包括主要物体、场景、颜色等细节。";

        String response;
        if (StringUtils.hasText(imageUrl)) {
            response = chatClient.prompt()
                    .user(userMessage -> userMessage
                            .text(prompt)
                            .media(OpenAiApi.ImageUrl.of(imageUrl)))
                    .call()
                    .content();
        } else {
            response = chatClient.prompt()
                    .user(userMessage -> userMessage
                            .text(prompt)
                            .media(OpenAiApi.ImageData.data("image/jpeg", base64Data)))
                    .call()
                    .content();
        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("description", response);
        resultMap.put("imageUrl", imageUrl);
        resultMap.put("base64Length", base64Data != null ? base64Data.length() : 0);

        return response;
    }

    public ImageRecognitionTask getTask(String taskId) {
        return taskMapper.selectOne(
                new LambdaQueryWrapper<ImageRecognitionTask>()
                        .eq(ImageRecognitionTask::getTaskId, taskId)
        );
    }
}
