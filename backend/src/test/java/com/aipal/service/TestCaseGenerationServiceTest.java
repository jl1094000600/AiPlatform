package com.aipal.service;

import com.aipal.entity.AutomationPipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCaseGenerationServiceTest {

    @TempDir
    Path root;

    @Test
    void generatesJunitAndVitestStyleTests() throws Exception {
        Path codeRoot = root.resolve("job-1");
        Path javaFile = codeRoot.resolve("backend/src/main/java/com/aipal/generated/DemoController.java");
        Path vueFile = codeRoot.resolve("front/src/generated/DemoView.vue");
        Files.createDirectories(javaFile.getParent());
        Files.createDirectories(vueFile.getParent());
        Files.writeString(javaFile, "package com.aipal.generated; public class DemoController {}");
        Files.writeString(vueFile, "<template><div>demo</div></template>");

        AutomationPipeline pipeline = new AutomationPipeline();
        pipeline.setProjectName("Demo");
        pipeline.setRequirementTitle("demo requirement");

        TestCaseGenerationService service = new TestCaseGenerationService();
        Path testsDir = codeRoot.resolve("tests");
        TestCaseGenerationService.TestCaseGenerationResult result = service.generate(pipeline, codeRoot, testsDir);

        assertTrue(Files.isDirectory(testsDir));
        assertTrue(result.files().size() >= 3);
        assertTrue(result.files().stream().anyMatch(f -> f.path().endsWith("DemoControllerTest.java")));
        assertTrue(result.files().stream().anyMatch(f -> f.path().contains("front/")));
        assertTrue(result.files().stream().anyMatch(f -> "README.md".equals(f.path())));
        String javaTest = result.files().stream()
                .filter(f -> f.path().endsWith("DemoControllerTest.java"))
                .findFirst().orElseThrow().content();
        assertTrue(javaTest.contains("org.junit.jupiter.api.Test"));
        String frontTest = result.files().stream()
                .filter(f -> f.path().startsWith("front/"))
                .findFirst().orElseThrow().content();
        assertTrue(frontTest.contains("vitest"));
    }

    @Test
    void writesUnderTestsDirectoryOnly() throws Exception {
        Path codeRoot = root.resolve("job-2");
        Files.createDirectories(codeRoot.resolve("backend"));
        Files.writeString(codeRoot.resolve("backend/A.java"), "class A {}");
        TestCaseGenerationService service = new TestCaseGenerationService();
        Path testsDir = codeRoot.resolve("tests");

        TestCaseGenerationService.TestCaseGenerationResult result = service.generate(null, codeRoot, testsDir);

        for (TestCaseGenerationService.GeneratedTestFile file : result.files()) {
            assertTrue(file.path().startsWith("java/") || file.path().startsWith("front/")
                    || "README.md".equals(file.path()));
            assertTrue(Files.isRegularFile(testsDir.resolve(file.path())));
        }
        assertEquals(testsDir.toAbsolutePath().normalize(), result.testsDir().toAbsolutePath().normalize());
    }
}
