package com.aipal.service;

import com.aipal.dto.ModelTrainingRequest;
import com.aipal.dto.ModelTrainingDatasetImportRequest;
import com.aipal.dto.ModelTrainingDatasetMockRequest;
import com.aipal.dto.ModelTrainingDatasetSaveRequest;
import com.aipal.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelTrainingServiceTest {

    @BeforeEach
    void setTenantContext() {
        TenantContext.set(new TenantContext.Context(
                1L, "tester", 1L, "tenant-1", false, java.util.Set.of(), java.util.Set.of()));
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void rejectsTrainDataOutsideProjectRoot() {
        ModelTrainingService service = new ModelTrainingService();
        ModelTrainingRequest request = new ModelTrainingRequest();
        request.setTrainData("../data/train.jsonl");

        assertThrows(IllegalArgumentException.class, () -> service.createJob(request));
    }

    @Test
    void rejectsNonPositiveEpochs() {
        ModelTrainingService service = new ModelTrainingService();
        ModelTrainingRequest request = new ModelTrainingRequest();
        request.setTrainData("bge-m3-training/data/sample_train.jsonl");
        request.setEpochs(0);

        assertThrows(IllegalArgumentException.class, () -> service.createJob(request));
    }

    @Test
    void acceptsDryRunRequestWithDefaults() {
        ModelTrainingService service = new ModelTrainingService();
        ModelTrainingRequest request = new ModelTrainingRequest();
        request.setTrainData("bge-m3-training/data/sample_train.jsonl");
        request.setDryRun(true);
        request.setLearningRate(new BigDecimal("0.00001"));

        var job = service.createJob(request);

        assertEquals(true, job.getId().startsWith("MT_"));
        assertEquals("BAAI/bge-m3", job.getModelPath());
        assertEquals("bge-m3-training/output/bge-m3-ft", job.getOutputDir());
    }

    @Test
    void listsTrainingJsonlDatasets() {
        ModelTrainingService service = new ModelTrainingService();

        var datasets = service.listDatasets();

        assertTrue(datasets.stream().anyMatch(dataset -> dataset.getPath().equals("bge-m3-training/data/sample_train.jsonl")));
    }

    @Test
    void importsValidJsonlTrainingDataset() {
        ModelTrainingService service = new ModelTrainingService();
        ModelTrainingDatasetImportRequest request = new ModelTrainingDatasetImportRequest();
        request.setFileName("unit-import.jsonl");
        request.setContent("""
                {"query":"q1","pos":["p1"],"neg":["n1"]}
                {"query":"q2","pos":["p2"],"neg":["n2"]}
                """);

        var dataset = service.importDataset(request);

        assertEquals("unit-import.jsonl", dataset.getName());
        assertEquals(2, dataset.getRecords());
        assertEquals("imported", dataset.getSource());
    }

    @Test
    void previewMockRequiresEnabledModel() {
        ModelTrainingService service = new ModelTrainingService();
        ModelTrainingDatasetMockRequest request = new ModelTrainingDatasetMockRequest();
        request.setFileName("unit-mock.jsonl");
        request.setTopic("BGEM3");
        request.setCount(3);

        assertThrows(IllegalStateException.class, () -> service.previewMockDataset(request));
    }

    @Test
    void savesPreviewedTrainingDataset() {
        ModelTrainingService service = new ModelTrainingService();
        ModelTrainingDatasetSaveRequest saveRequest = new ModelTrainingDatasetSaveRequest();
        saveRequest.setFileName("unit-mock.jsonl");
        saveRequest.setDatasetName("unit-mock");
        saveRequest.setSource("mock");
        saveRequest.setContent("""
                {"query":"Java 代码规范中如何命名类？","pos":["Java 类名应使用 UpperCamelCase，例如 OrderService。错误示例是 order_service 或 orderservice。"],"neg":["变量名应该全部使用大写字母，例如 USERNAME。"]}
                {"query":"Java 方法命名有哪些正确和错误示例？","pos":["Java 方法名应使用 lowerCamelCase，例如 calculateTotalAmount。错误示例是 CalculateTotalAmount 或 calculate_total_amount。"],"neg":["SQL 表名必须使用 lowerCamelCase。"]}
                {"query":"Java 常量命名规范是什么？","pos":["Java 常量应使用全大写下划线，例如 MAX_RETRY_COUNT。错误示例是 maxRetryCount 作为 static final 常量。"],"neg":["类名应该使用全大写下划线，例如 USER_SERVICE。"]}
                """);
        var dataset = service.saveDataset(saveRequest);

        assertEquals("unit-mock.jsonl", dataset.getName());
        assertEquals(3, dataset.getRecords());
        assertEquals("mock", dataset.getSource());
    }
}
