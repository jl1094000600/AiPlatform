package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.dto.BatchEvaluationRequest;
import com.aipal.dto.CriteriaConfigRequest;
import com.aipal.dto.DatasetImportRequest;
import com.aipal.dto.EvaluationRequest;
import com.aipal.entity.AiDataset;
import com.aipal.entity.AiEvaluation;
import com.aipal.entity.AiEvaluationCriteria;
import com.aipal.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/benchmark")
@RequiredArgsConstructor
public class BenchmarkController {

    private final DatasetService datasetService;
    private final DataGeneratorService dataGeneratorService;
    private final EvaluationService evaluationService;
    private final EvaluationStatisticsService statisticsService;
    private final CriteriaEngineService criteriaEngineService;

    /**
     * POST /benchmark/dataset/upload → /api/v1/datasets/import
     */
    @PostMapping("/dataset/upload")
    public Result<AiDataset> uploadDataset(
            @RequestParam String datasetName,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String fields,
            @RequestParam(required = false) MultipartFile file) {
        try {
            DatasetImportRequest request = buildImportRequest(datasetName, description, category, format, fields);
            return Result.success(datasetService.importDataset(request, file));
        } catch (Exception e) {
            return Result.error("Upload failed: " + e.getMessage());
        }
    }

    /**
     * POST /benchmark/simdata/generate → /api/v1/data-generator/generate
     */
    @PostMapping("/simdata/generate")
    public Result<String> generateSimData(@RequestBody Map<String, Object> request) {
        try {
            String template = extractTemplate(request);
            Integer count = request.get("count") != null ? Integer.parseInt(request.get("count").toString()) : 100;
            DataGeneratorService.TemplateType templateType = DataGeneratorService.TemplateType.valueOf(template.toUpperCase());
            String data = dataGeneratorService.generateDataByTemplate(templateType, count);
            return Result.success(data);
        } catch (Exception e) {
            return Result.error("Generation failed: " + e.getMessage());
        }
    }

    private String extractTemplate(Map<String, Object> request) {
        if (request.containsKey("template") && request.get("template") != null) {
            return request.get("template").toString();
        }
        if (request.containsKey("templateId") && request.get("templateId") != null) {
            Object templateId = request.get("templateId");
            if (templateId instanceof Number) {
                int id = ((Number) templateId).intValue();
                return getTemplateById(id);
            }
            return getTemplateById(Integer.parseInt(templateId.toString()));
        }
        throw new IllegalArgumentException("template or templateId is required");
    }

    private String getTemplateById(int id) {
        List<String> templates = dataGeneratorService.getTemplateNames();
        if (id > 0 && id <= templates.size()) {
            return templates.get(id - 1);
        }
        throw new IllegalArgumentException("Invalid templateId: " + id);
    }

    /**
     * GET /benchmark/history
     */
    @GetMapping("/history")
    public Result<?> getHistory(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long datasetId,
            @RequestParam(required = false) Long agentId) {
        return Result.success(evaluationService.listEvaluations(pageNum, pageSize, null, datasetId, agentId));
    }

    /**
     * POST /benchmark/start
     */
    @PostMapping("/start")
    public Result<String> startBenchmark(@RequestBody EvaluationRequest request) {
        return Result.success(evaluationService.startEvaluation(request));
    }

    /**
     * POST /benchmark/start/batch
     */
    @PostMapping("/start/batch")
    public Result<String> startBatchBenchmark(@RequestBody BatchEvaluationRequest request) {
        return Result.success(evaluationService.startBatchEvaluation(request));
    }

    /**
     * GET /benchmark/progress/{id}
     */
    @GetMapping("/progress/{id}")
    public Result<String> getProgress(@PathVariable String id) {
        return Result.success(evaluationService.getEvaluationStatus(id));
    }

    /**
     * GET /benchmark/result/{id}
     */
    @GetMapping("/result/{id}")
    public Result<AiEvaluation> getResult(@PathVariable Long id) {
        return Result.success(evaluationService.getEvaluationById(id));
    }

    /**
     * POST /benchmark/standards
     * 支持两种格式：
     * 1. 单个对象: {criteriaCode: "xxx", criteriaName: "xxx", ...}
     * 2. 数组格式: {standards: [{...}, {...}], rules: [{...}, {...}]}
     */
    @PostMapping("/standards")
    public Result<?> createStandard(@RequestBody Map<String, Object> request) {
        try {
            if (request.containsKey("standards") && request.get("standards") instanceof List) {
                List<?> standards = (List<?>) request.get("standards");
                int created = 0;
                for (Object item : standards) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> standard = (Map<String, Object>) item;
                        CriteriaConfigRequest criteriaRequest = mapToCriteriaRequest(standard);
                        criteriaEngineService.createCriteria(criteriaRequest);
                        created++;
                    }
                }
                return Result.success("Created " + created + " standards");
            } else {
                CriteriaConfigRequest criteriaRequest = mapToCriteriaRequest(request);
                return Result.success(criteriaEngineService.createCriteria(criteriaRequest));
            }
        } catch (Exception e) {
            return Result.error("Failed to create standard: " + e.getMessage());
        }
    }

    private CriteriaConfigRequest mapToCriteriaRequest(Map<String, Object> map) {
        CriteriaConfigRequest request = new CriteriaConfigRequest();
        request.setCriteriaCode(map.get("criteriaCode") != null ? map.get("criteriaCode").toString() : null);
        request.setCriteriaName(map.get("criteriaName") != null ? map.get("criteriaName").toString() : null);
        request.setDescription(map.get("description") != null ? map.get("description").toString() : null);
        request.setFormula(map.get("formula") != null ? map.get("formula").toString() : null);
        if (map.get("weight") != null) {
            request.setWeight(Double.parseDouble(map.get("weight").toString()));
        }
        request.setThresholds(map.get("thresholds") != null ? map.get("thresholds").toString() : null);
        return request;
    }

    /**
     * GET /benchmark/standards
     */
    @GetMapping("/standards")
    public Result<List<AiEvaluationCriteria>> getStandards() {
        return Result.success(criteriaEngineService.getAllCriteria());
    }

    /**
     * PUT /benchmark/standards/{id}
     */
    @PutMapping("/standards/{id}")
    public Result<Boolean> updateStandard(@PathVariable Long id, @RequestBody CriteriaConfigRequest request) {
        return Result.success(criteriaEngineService.updateCriteria(request, id));
    }

    /**
     * DELETE /benchmark/standards/{id}
     */
    @DeleteMapping("/standards/{id}")
    public Result<Boolean> deleteStandard(@PathVariable Long id) {
        return Result.success(criteriaEngineService.deleteCriteria(id));
    }

    /**
     * GET /benchmark/export/{id}
     */
    @GetMapping("/export/{id}")
    public Result<String> exportResult(@PathVariable Long id) {
        String report = statisticsService.generateEvaluationReport(id);
        if (report == null) {
            return Result.error("Evaluation not found");
        }
        return Result.success(report);
    }

    /**
     * GET /benchmark/statistics
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics(
            @RequestParam(required = false) Long datasetId,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) String timeRange) {
        return Result.success(statisticsService.getEvaluationStatistics(datasetId, agentId, timeRange));
    }

    /**
     * GET /benchmark/leaderboard
     */
    @GetMapping("/leaderboard")
    public Result<Map<String, Object>> getLeaderboard(@RequestParam(defaultValue = "10") int topN) {
        return Result.success(statisticsService.getAgentLeaderboard(topN));
    }

    private DatasetImportRequest buildImportRequest(String name, String desc, String category, String format, String fieldsJson) {
        DatasetImportRequest request = new DatasetImportRequest();
        request.setDatasetName(name);
        request.setDescription(desc);
        request.setCategory(category);
        request.setFormat(format != null ? format : "json");
        return request;
    }
}