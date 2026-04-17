package com.aipal.imageagent;

import com.aipal.dto.A2AMessage;
import com.aipal.entity.AgentHeartbeat;
import com.aipal.mapper.AgentHeartbeatMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageRecognitionAgent {

    private static final String AGENT_CODE = "image-agent";
    private static final String AGENT_NAME = "图像识别Agent";
    private static final String INSTANCE_ID = UUID.randomUUID().toString();
    private static final Set<String> IMAGE_TYPES = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp");
    private static final Set<String> DOCUMENT_TYPES = Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md");

    private final ChatClient chatClient;
    private final AgentHeartbeatMapper heartbeatMapper;

    private final Map<String, Function<A2AMessage, A2AMessage>> handlers = new ConcurrentHashMap<>();
    private Long agentId;

    @PostConstruct
    public void init() {
        log.info("Initializing ImageRecognitionAgent [{}] with instanceId: {}", AGENT_NAME, INSTANCE_ID);
        registerDefaultHandlers();
    }

    private void registerDefaultHandlers() {
        handlers.put("recognize", this::handleRecognize);
        handlers.put("process", this::handleProcess);
        handlers.put("health", this::handleHealth);
    }

    public String getAgentCode() {
        return AGENT_CODE;
    }

    public String getAgentName() {
        return AGENT_NAME;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public void registerHandler(String action, Function<A2AMessage, A2AMessage> handler) {
        handlers.put(action, handler);
        log.info("Registered handler for action: {}", action);
    }

    public Function<A2AMessage, A2AMessage> getHandler(String action) {
        return handlers.get(action);
    }

    public Map<String, Function<A2AMessage, A2AMessage>> getAllHandlers() {
        return handlers;
    }

    public A2AMessage handleMessage(A2AMessage message) {
        A2AMessage response = new A2AMessage();
        response.setSourceAgent(AGENT_CODE);
        response.setTargetAgent(message.getSourceAgent());
        response.setSessionId(message.getSessionId());
        response.setAction(A2AMessage.Action.respond);

        try {
            if (message.getPayload() == null) {
                response.setPayload(Map.of("status", "success", "message", AGENT_NAME + " is running"));
                return response;
            }

            String action = message.getPayload().get("action") != null
                ? message.getPayload().get("action").toString()
                : "recognize";

            Function<A2AMessage, A2AMessage> handler = handlers.get(action);
            if (handler != null) {
                return handler.apply(message);
            } else {
                response.setPayload(Map.of("status", "error", "error", "Unknown action: " + action));
            }
        } catch (Exception e) {
            log.error("Error handling message", e);
            response.setPayload(Map.of("status", "error", "error", e.getMessage()));
        }

        return response;
    }

    public String recognizeImage(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("File name is required");
        }

        String extension = cn.hutool.core.io.FileUtil.extName(originalFilename).toLowerCase();
        if (!IMAGE_TYPES.contains(extension)) {
            throw new IllegalArgumentException("Not a supported image file: " + extension);
        }

        byte[] imageBytes = file.getBytes();
        String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);

        String prompt = "Please describe this image in detail. Focus on: " +
                "1. What objects or people are in the image? " +
                "2. What is the setting or context? " +
                "3. Any text or labels visible? " +
                "4. Colors and visual style.";

        String result = chatClient.prompt()
                .user(u -> u.text(prompt)
                        .media(org.springframework.ai.chat.client.ChatClient.MediaContentRequest.builder()
                                .media(org.springframework.ai.chat.messages.MediaMessage.MediaType.IMAGE_JPEG, base64Image)
                                .build()))
                .call()
                .content();

        log.info("Image recognition completed for file: {}", originalFilename);
        return result;
    }

    public String processDocument(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("File name is required");
        }

        String extension = cn.hutool.core.io.FileUtil.extName(originalFilename).toLowerCase();
        if (!DOCUMENT_TYPES.contains(extension)) {
            throw new IllegalArgumentException("Not a supported document file: " + extension);
        }

        String content;
        if ("txt".equals(extension) || "md".equals(extension)) {
            content = new String(file.getBytes());
        } else {
            content = extractTextFromDocument(file, extension);
        }

        String prompt = "Please analyze the following document content and provide a summary:\n\n" + content;

        String result = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        log.info("Document processing completed for file: {}", originalFilename);
        return result;
    }

    private String extractTextFromDocument(MultipartFile file, String extension) throws IOException {
        Path tempFile = Files.createTempFile("doc_", "." + extension);
        try {
            Files.write(tempFile, file.getBytes());
            switch (extension) {
                case "pdf":
                    return extractTextFromPdf(tempFile);
                case "doc":
                case "docx":
                    return extractTextFromWord(tempFile);
                case "xls":
                case "xlsx":
                    return extractTextFromExcel(tempFile);
                default:
                    return new String(file.getBytes());
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private String extractTextFromPdf(Path path) {
        try {
            return cn.hutool.pdf.PdfUtil.readText(path.toFile());
        } catch (Exception e) {
            log.warn("Failed to extract text from PDF: {}", e.getMessage());
            return "PDF content extraction not available";
        }
    }

    private String extractTextFromWord(Path path) {
        try {
            return cn.hutool.poi.word.WordUtil.read(path.toFile());
        } catch (Exception e) {
            log.warn("Failed to extract text from Word: {}", e.getMessage());
            return "Word content extraction not available";
        }
    }

    private String extractTextFromExcel(Path path) {
        try {
            return cn.hutool.poi.excel.ExcelUtil.read(path.toFile()).toString();
        } catch (Exception e) {
            log.warn("Failed to extract text from Excel: {}", e.getMessage());
            return "Excel content extraction not available";
        }
    }

    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        try {
            AgentHeartbeat heartbeat = heartbeatMapper.selectOne(
                new LambdaQueryWrapper<AgentHeartbeat>()
                    .eq(AgentHeartbeat::getAgentId, agentId)
                    .eq(AgentHeartbeat::getInstanceId, INSTANCE_ID)
            );

            if (heartbeat == null) {
                heartbeat = new AgentHeartbeat();
                heartbeat.setAgentId(agentId);
                heartbeat.setInstanceId(INSTANCE_ID);
                heartbeat.setCreateTime(LocalDateTime.now());
                heartbeat.setStatus(1);
                heartbeatMapper.insert(heartbeat);
            } else {
                heartbeat.setLastHeartbeat(LocalDateTime.now());
                heartbeat.setHealthScore(100);
                heartbeat.setUpdateTime(LocalDateTime.now());
                heartbeat.setStatus(1);
                heartbeatMapper.updateById(heartbeat);
            }

            log.debug("Heartbeat sent for agent: {} instance: {}", AGENT_CODE, INSTANCE_ID);
        } catch (Exception e) {
            log.error("Failed to send heartbeat", e);
        }
    }

    private A2AMessage handleRecognize(A2AMessage message) {
        A2AMessage response = new A2AMessage();
        response.setSourceAgent(AGENT_CODE);
        response.setTargetAgent(message.getSourceAgent());
        response.setSessionId(message.getSessionId());
        response.setAction(A2AMessage.Action.respond);

        try {
            Object fileData = message.getPayload().get("file");
            if (fileData == null) {
                response.setPayload(Map.of("status", "error", "error", "No file provided"));
                return response;
            }

            String fileType = message.getPayload().get("fileType") != null
                ? message.getPayload().get("fileType").toString()
                : "image";

            String result;
            if ("document".equalsIgnoreCase(fileType)) {
                result = "Document processing requires actual file upload via REST API";
            } else {
                result = "Image recognition requires actual file upload via REST API";
            }

            response.setPayload(Map.of(
                "status", "success",
                "result", result,
                "agent", AGENT_NAME
            ));
        } catch (Exception e) {
            log.error("Error in recognize handler", e);
            response.setPayload(Map.of("status", "error", "error", e.getMessage()));
        }

        return response;
    }

    private A2AMessage handleProcess(A2AMessage message) {
        A2AMessage response = new A2AMessage();
        response.setSourceAgent(AGENT_CODE);
        response.setTargetAgent(message.getSourceAgent());
        response.setSessionId(message.getSessionId());
        response.setAction(A2AMessage.Action.respond);

        try {
            String content = message.getPayload().get("content") != null
                ? message.getPayload().get("content").toString()
                : "";

            String prompt = "Please analyze the following content and provide insights:\n\n" + content;

            String result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            response.setPayload(Map.of(
                "status", "success",
                "result", result
            ));
        } catch (Exception e) {
            log.error("Error in process handler", e);
            response.setPayload(Map.of("status", "error", "error", e.getMessage()));
        }

        return response;
    }

    private A2AMessage handleHealth(A2AMessage message) {
        A2AMessage response = new A2AMessage();
        response.setSourceAgent(AGENT_CODE);
        response.setTargetAgent(message.getSourceAgent());
        response.setSessionId(message.getSessionId());
        response.setAction(A2AMessage.Action.respond);

        response.setPayload(Map.of(
            "status", "success",
            "agent", AGENT_NAME,
            "agentCode", AGENT_CODE,
            "instanceId", INSTANCE_ID,
            "health", "healthy"
        ));

        return response;
    }
}
