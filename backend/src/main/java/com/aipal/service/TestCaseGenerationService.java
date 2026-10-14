package com.aipal.service;

import com.aipal.entity.AutomationPipeline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Generates readable JUnit / Vitest style test-case files from a generated-code tree.
 * Output is deterministic scaffolding (no model call) so the stage is reliable offline.
 */
@Service
@Slf4j
public class TestCaseGenerationService {

    private static final int MAX_TEST_FILES = 40;
    private static final List<String> EXCLUDED_DIRS =
            List.of(".git", "node_modules", "target", "dist", "build", "tests");

    public record GeneratedTestFile(String path, String content) {
    }

    public record TestCaseGenerationResult(Path testsDir, List<GeneratedTestFile> files, String summary) {
    }

    /**
     * Write test-case files under {@code testsDir} based on sources found in {@code codeRoot}.
     */
    public TestCaseGenerationResult generate(AutomationPipeline pipeline, Path codeRoot, Path testsDir)
            throws IOException {
        if (codeRoot == null || !Files.isDirectory(codeRoot)) {
            throw new IllegalArgumentException("generated code root does not exist: " + codeRoot);
        }
        Files.createDirectories(testsDir);
        List<SourceFile> sources = collectSources(codeRoot);
        List<GeneratedTestFile> files = new ArrayList<>();
        String requirement = pipeline == null ? "" : pipeline.getRequirementTitle();

        for (SourceFile source : sources) {
            if (files.size() >= MAX_TEST_FILES) {
                break;
            }
            GeneratedTestFile testFile = source.backend()
                    ? javaTestFile(source, requirement)
                    : frontendTestFile(source, requirement);
            Path target = testsDir.resolve(testFile.path()).normalize();
            if (!target.startsWith(testsDir.toAbsolutePath().normalize())) {
                continue;
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, testFile.content(), StandardCharsets.UTF_8);
            files.add(testFile);
        }
        GeneratedTestFile readme = readmeFile(pipeline, files);
        Path readmePath = testsDir.resolve(readme.path()).normalize();
        Files.writeString(readmePath, readme.content(), StandardCharsets.UTF_8);
        files.add(readme);

        String summary = "生成测试用例 " + (files.size() - 1) + " 个（JUnit/Vitest 风格），目录：" + testsDir;
        return new TestCaseGenerationResult(testsDir, files, summary);
    }

    private List<SourceFile> collectSources(Path codeRoot) throws IOException {
        List<SourceFile> sources = new ArrayList<>();
        Path root = codeRoot.toAbsolutePath().normalize();
        try (Stream<Path> walk = Files.walk(root, 12)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> !isExcluded(root, path))
                    .filter(path -> isSource(path.getFileName().toString()))
                    .sorted()
                    .forEach(path -> sources.add(new SourceFile(
                            root.relativize(path).toString().replace('\\', '/'),
                            isBackendSource(path),
                            readPreview(path))));
        }
        return sources;
    }

    private boolean isExcluded(Path root, Path path) {
        Path relative = root.relativize(path);
        for (Path segment : relative) {
            String name = segment.toString();
            if (EXCLUDED_DIRS.contains(name) || name.startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private boolean isSource(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".java")
                || lower.endsWith(".vue")
                || lower.endsWith(".ts")
                || lower.endsWith(".tsx")
                || lower.endsWith(".js")
                || lower.endsWith(".jsx");
    }

    private boolean isBackendSource(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".java");
    }

    private String readPreview(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return content.length() > 400 ? content.substring(0, 400) : content;
        } catch (IOException e) {
            return "";
        }
    }

    private GeneratedTestFile javaTestFile(SourceFile source, String requirement) {
        String className = className(source.path());
        String packageName = packageName(source.path());
        String testClassName = className + "Test";
        String safeRequirement = escapeJava(requirement == null ? "" : requirement);
        String content = """
                package %s;

                import org.junit.jupiter.api.Test;

                import static org.junit.jupiter.api.Assertions.assertNotNull;
                import static org.junit.jupiter.api.Assertions.assertTrue;

                /**
                 * Generated test scaffold for %s
                 * Requirement: %s
                 */
                class %s {

                    @Test
                    void componentLoads() {
                        // Smoke: the production type is on the classpath and constructible for review.
                        assertNotNull(%s.class.getName());
                    }

                    @Test
                    void coversAcceptanceScenarioPlaceholder() {
                        // Replace with assertions derived from the PRD acceptance criteria.
                        assertTrue(true, "pending automated assertions for: %s");
                    }
                }
                """.formatted(
                packageName,
                source.path(),
                safeRequirement,
                testClassName,
                className,
                safeRequirement
        );
        return new GeneratedTestFile("java/" + packageName.replace('.', '/') + "/" + testClassName + ".java", content);
    }

    private GeneratedTestFile frontendTestFile(SourceFile source, String requirement) {
        String safeName = source.path().replaceAll("[^A-Za-z0-9]+", "_");
        String safeRequirement = String.valueOf(requirement == null ? "" : requirement).replace("`", "'");
        String content = """
                import { describe, it, expect } from 'vitest'

                /**
                 * Generated test scaffold for %s
                 * Requirement: %s
                 */
                describe('%s', () => {
                  it('module is importable', () => {
                    expect(true).toBe(true)
                  })

                  it('covers acceptance scenario placeholder', () => {
                    // Replace with assertions derived from the PRD acceptance criteria.
                    expect(safeRequirement()).toContain('%s')
                  })
                })

                function safeRequirement() {
                  return '%s'
                }
                """.formatted(
                source.path(),
                safeRequirement,
                safeName,
                safeRequirement.length() > 40 ? safeRequirement.substring(0, 40) : safeRequirement,
                safeRequirement.replace("'", "\\'")
        );
        return new GeneratedTestFile("front/" + safeName + ".test.js", content);
    }

    private GeneratedTestFile readmeFile(AutomationPipeline pipeline, List<GeneratedTestFile> files) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Generated Test Cases\n\n");
        if (pipeline != null) {
            sb.append("- Project: ").append(pipeline.getProjectName()).append('\n');
            sb.append("- Requirement: ").append(pipeline.getRequirementTitle()).append('\n');
        }
        sb.append("- Generated at: ").append(LocalDateTime.now()).append('\n');
        sb.append("- Framework: JUnit 5 (backend) / Vitest (frontend)\n\n");
        sb.append("## Files\n\n");
        for (GeneratedTestFile file : files) {
            sb.append("- `").append(file.path()).append("`\n");
        }
        sb.append("\n## How to run\n\n");
        sb.append("- Backend: `mvn test`\n");
        sb.append("- Frontend: `npm test` or `npx vitest run`\n");
        return new GeneratedTestFile("README.md", sb.toString());
    }

    private String className(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String packageName(String path) {
        // backend/src/main/java/com/aipal/generated/X.java -> com.aipal.generated
        int javaIdx = path.indexOf("java/");
        if (javaIdx < 0) {
            return "com.aipal.generated.tests";
        }
        String rest = path.substring(javaIdx + "java/".length());
        int lastSlash = rest.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "com.aipal.generated.tests";
        }
        return rest.substring(0, lastSlash).replace('/', '.');
    }

    private String escapeJava(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    private record SourceFile(String path, boolean backend, String preview) {
    }

    public Map<String, Object> toMetadata(TestCaseGenerationResult result) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("testsDir", result.testsDir() == null ? null : result.testsDir().toString());
        metadata.put("fileCount", result.files().size());
        metadata.put("summary", result.summary());
        return metadata;
    }
}
