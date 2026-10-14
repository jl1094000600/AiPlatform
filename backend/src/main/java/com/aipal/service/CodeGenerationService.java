package com.aipal.service;

import com.aipal.entity.AiModel;
import com.aipal.entity.AutomationPipeline;
import com.aipal.mapper.AiModelMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns PRD/code artifact generation prompts, parsing and on-disk scaffolding.
 * Orchestration, approval and governance stay in {@link AutomationPipelineService}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CodeGenerationService {

    public static final int MAX_CODE_FILE_COUNT = 40;
    public static final int MAX_CODE_FILE_CHARS = 120_000;

    private static final String CODE_SYSTEM_PROMPT =
            "You are an enterprise full-stack engineer. Return pure JSON only, without Markdown fences.";
    private static final String PRD_SYSTEM_PROMPT =
            "You are a senior product manager. Return a structured Markdown PRD with background, goals, user stories, functional requirements, non-functional requirements, acceptance criteria, and risks.";

    private final ObjectMapper objectMapper;
    private final ModelGateway modelGateway;
    private final AiModelMapper modelMapper;

    public record GeneratedFile(String path, String content) {
    }

    public List<GeneratedFile> generateCodeFiles(AutomationPipeline pipeline, String prdContent,
                                                 String templateContent, String skillContext,
                                                 String memorySection, String aiModelCode,
                                                 TokenUsageSink tokenUsageSink) {
        AiModel model = modelMapper.selectOne(
                new LambdaQueryWrapper<AiModel>()
                        .eq(aiModelCode != null, AiModel::getModelCode, aiModelCode)
                        .last("LIMIT 1")
        );
        if (model == null || model.getEndpoint() == null || model.getEndpoint().isBlank()
                || model.getApiKey() == null || model.getApiKey().isBlank()) {
            return fallbackCodeFiles(pipeline, prdContent, "Model is not configured or API key is missing");
        }

        try {
            String userPrompt = buildCodePrompt(pipeline, prdContent, templateContent, skillContext)
                    + (memorySection == null ? "" : memorySection);
            ModelCallResult result = modelGateway.chat(model, CODE_SYSTEM_PROMPT, userPrompt, BigDecimal.valueOf(0.3), null);
            if (tokenUsageSink != null) {
                tokenUsageSink.accept(result);
            }
            List<GeneratedFile> files = parseGeneratedCodeFiles(result.contentOrEmpty(), pipeline);
            return files.isEmpty()
                    ? fallbackCodeFiles(pipeline, prdContent, "Model returned no parseable code files")
                    : files;
        } catch (Exception e) {
            return fallbackCodeFiles(pipeline, prdContent, "Model call failed: " + e.getMessage());
        }
    }

    public String generatePrdContent(AiModel model, String userPromptWithoutMemory, String memorySection,
                                     Integer maxTokens, TokenUsageSink tokenUsageSink) {
        // Callers pass the already-built business prompt; model absence is handled by the caller.
        String userPrompt = userPromptWithoutMemory + (memorySection == null ? "" : memorySection);
        ModelCallResult result = modelGateway.chat(model, PRD_SYSTEM_PROMPT, userPrompt, BigDecimal.ONE, maxTokens);
        if (tokenUsageSink != null) {
            tokenUsageSink.accept(result);
        }
        return result.contentOrEmpty();
    }

    public String buildCodePrompt(AutomationPipeline pipeline, String prdContent, String templateContent, String skillContext) {
        String effectiveTemplateContent = (templateContent == null ? "" : templateContent)
                + "\n\nSkill Context:\n" + (skillContext == null ? "No skill selected." : skillContext);
        return """
                请根据以下 PRD 生成最小可运行的前后端代码增量。
                项目：%s
                需求：%s
                项目模式：%s
                生成层级：%s
                是否生成前端：%s
                是否生成后端：%s
                前端输出目录：%s
                后端输出目录：%s

                Markdown 生成模板：
                %s

                输出格式必须是纯 JSON：
                {
                  "files": [
                    {"path": "%s/ExampleController.java", "content": "..."},
                    {"path": "%s/ExampleView.vue", "content": "..."}
                  ]
                }

                约束：
                - 只生成被启用端的代码。若前端未启用，不要返回前端文件；若后端未启用，不要返回后端文件。
                - 前端文件必须放在前端输出目录下，后端文件必须放在后端输出目录下。
                - 当前项目不是从零开始时，只生成与需求相关的增量文件，不要重写无关模块。
                - 单文件内容保持精简，避免生成依赖大段二进制或锁文件。
                - 后端使用 Spring Boot 风格，前端使用 Vue 单文件组件风格。
                - 不要返回解释文字，不要使用 Markdown 代码围栏。

                PRD：
                %s
                """.formatted(
                pipeline.getProjectName(),
                pipeline.getRequirementTitle(),
                pipeline.getProjectMode(),
                pipeline.getCodeLevel(),
                pipeline.getGenerateFrontend() != null && pipeline.getGenerateFrontend() == 1,
                pipeline.getGenerateBackend() != null && pipeline.getGenerateBackend() == 1,
                pipeline.getFrontendOutputPath(),
                pipeline.getBackendOutputPath(),
                effectiveTemplateContent,
                pipeline.getBackendOutputPath(),
                pipeline.getFrontendOutputPath(),
                prdContent == null ? "" : prdContent
        );
    }

    public List<GeneratedFile> parseGeneratedCodeFiles(String modelContent, AutomationPipeline pipeline) throws IOException {
        if (modelContent == null || modelContent.isBlank()) {
            return List.of();
        }
        String json = extractJsonPayload(modelContent);
        JsonNode filesNode = objectMapper.readTree(json).path("files");
        if (!filesNode.isArray()) {
            return List.of();
        }
        List<GeneratedFile> files = new ArrayList<>();
        for (JsonNode node : filesNode) {
            if (files.size() >= MAX_CODE_FILE_COUNT) {
                break;
            }
            String path = normalizeRelativePath(node.path("path").asText(""));
            String content = node.path("content").asText("");
            if (!isSafeGeneratedCodePath(path, pipeline) || content.isBlank()) {
                continue;
            }
            if (content.length() > MAX_CODE_FILE_CHARS) {
                content = content.substring(0, MAX_CODE_FILE_CHARS)
                        + "\n\n/* Truncated by AI Platform code artifact size guard. */\n";
            }
            files.add(new GeneratedFile(path, content));
        }
        return files;
    }

    public String writeGeneratedCode(Long pipelineId, Long jobId, List<GeneratedFile> files) throws IOException {
        Path root = codeArtifactRoot(pipelineId, jobId);
        Files.createDirectories(root);
        List<Map<String, Object>> manifestFiles = new ArrayList<>();
        for (GeneratedFile file : files) {
            Path target = root.resolve(file.path()).normalize();
            if (!target.startsWith(root)) {
                continue;
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.content());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("path", file.path());
            entry.put("size", Files.size(target));
            entry.put("type", file.path().startsWith("backend/") ? "backend" : "front");
            manifestFiles.add(entry);
        }
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("jobId", jobId);
        manifest.put("files", manifestFiles);
        manifest.put("generatedAt", LocalDateTime.now().toString());
        return objectMapper.writeValueAsString(manifest);
    }

    public Path codeArtifactRoot(Long pipelineId) {
        return Paths.get("marketDoc", "generated-code", "pipeline-" + pipelineId).toAbsolutePath().normalize();
    }

    public Path codeArtifactRoot(Long pipelineId, Long jobId) {
        return codeArtifactRoot(pipelineId).resolve("job-" + jobId).normalize();
    }

    public List<GeneratedFile> fallbackCodeFiles(AutomationPipeline pipeline, String prdContent, String reason) {
        String title = sanitizeJavaIdentifier(pipeline.getRequirementTitle());
        List<GeneratedFile> files = new ArrayList<>();
        if (pipeline.getGenerateBackend() != null && pipeline.getGenerateBackend() == 1) {
            String packageName = packageFromBackendPath(pipeline.getBackendOutputPath());
            String controller = """
                package %s;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                import java.util.Map;

                @RestController
                @RequestMapping("/api/v1/generated/%s")
                public class %sController {

                    @GetMapping("/health")
                    public Map<String, Object> health() {
                        return Map.of("status", "ready", "requirement", "%s");
                    }
                }
                """.formatted(packageName, title.toLowerCase(), title, escapeJava(pipeline.getRequirementTitle()));
            files.add(new GeneratedFile(pipeline.getBackendOutputPath() + "/" + title + "Controller.java", controller));
        }
        if (pipeline.getGenerateFrontend() != null && pipeline.getGenerateFrontend() == 1) {
            String vue = """
                <template>
                  <section class="generated-feature">
                    <h2>%s</h2>
                    <p>Generated implementation scaffold is ready for review.</p>
                    <pre>%s</pre>
                  </section>
                </template>

                <script setup>
                const requirement = %s
                </script>

                <style scoped>
                .generated-feature { padding: 24px; }
                .generated-feature pre { white-space: pre-wrap; color: #475569; }
                </style>
                """.formatted(escapeHtml(pipeline.getRequirementTitle()), escapeHtml(reason),
                    objectMapper.valueToTree(prdContent == null ? "" : prdContent).toString());
            files.add(new GeneratedFile(pipeline.getFrontendOutputPath() + "/" + title + "View.vue", vue));
        }
        String readme = """
                # Generated Code Artifact

                Requirement: %s

                This fallback scaffold was created locally because: %s
                Template: %s
                Project mode: %s
                Code level: %s
                Frontend enabled: %s
                Backend enabled: %s
                The selected model should normally return a JSON file list. Please check model configuration if this appears unexpectedly.
                """.formatted(
                pipeline.getRequirementTitle(),
                reason,
                pipeline.getTemplateFile(),
                pipeline.getProjectMode(),
                pipeline.getCodeLevel(),
                pipeline.getGenerateFrontend(),
                pipeline.getGenerateBackend()
        );
        files.add(new GeneratedFile("README.md", readme));
        return files;
    }

    public String extractJsonPayload(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    public String normalizeRelativePath(String path) {
        return path == null ? "" : path.replace("\\", "/").replaceAll("^/+", "");
    }

    public boolean isSafeGeneratedCodePath(String path, AutomationPipeline pipeline) {
        if (path == null || path.isBlank() || path.contains("..") || Paths.get(path).isAbsolute()) {
            return false;
        }
        if ("README.md".equals(path)) {
            return true;
        }
        boolean backendAllowed = pipeline.getGenerateBackend() != null && pipeline.getGenerateBackend() == 1
                && path.startsWith(pipeline.getBackendOutputPath() + "/");
        boolean frontendAllowed = pipeline.getGenerateFrontend() != null && pipeline.getGenerateFrontend() == 1
                && path.startsWith(pipeline.getFrontendOutputPath() + "/");
        return backendAllowed || frontendAllowed;
    }

    public String packageFromBackendPath(String backendPath) {
        String normalized = normalizeRelativePath(backendPath);
        int javaIndex = normalized.indexOf("src/main/java/");
        if (javaIndex < 0) {
            return "com.aipal.generated";
        }
        String packagePath = normalized.substring(javaIndex + "src/main/java/".length()).replace("/", ".");
        return packagePath.isBlank() ? "com.aipal.generated" : packagePath;
    }

    public String sanitizeJavaIdentifier(String value) {
        String base = value == null ? "GeneratedFeature" : value.replaceAll("[^A-Za-z0-9]", "");
        if (base.isBlank()) {
            base = "GeneratedFeature";
        }
        if (Character.isDigit(base.charAt(0))) {
            base = "Feature" + base;
        }
        return base.substring(0, 1).toUpperCase() + base.substring(1);
    }

    public String escapeJava(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public long utf8Size(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    @FunctionalInterface
    public interface TokenUsageSink {
        void accept(ModelCallResult result);
    }
}
